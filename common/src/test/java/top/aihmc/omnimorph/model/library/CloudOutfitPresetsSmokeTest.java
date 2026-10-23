package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The actual authenticated client and HTTP responses, including a committed request whose response was lost. */
public final class CloudOutfitPresetsSmokeTest {
    public static void main(String[] args) throws Exception {
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        var live = new AtomicBoolean(true);
        try (var server = new Server();
             var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                     request -> request.header("Authorization", "Bearer synthetic-owner"));
             var client = new LibraryClient(access, new LibrarySession(server.origin(), UUID.randomUUID(), 1, live::get), callbacks::add)) {
            var presets = new CloudOutfitPresets(client, OutfitLimits.defaults());
            var config = new OutfitConfig(1, 7, null, List.of(), List.of(), Map.of(), false);
            UUID id = UUID.randomUUID();
            server.loseNext.set(true);
            try (var save = presets.save(id, 0, "Private 中文 preset", config)) {
                failure(save.future(), callbacks, "BUSY");
                UUID operation = save.operationId();
                var replay = await(save.retry(), callbacks);
                require(replay.id().equals(id) && replay.revision() == 1 && replay.configuration().equals(config)
                                && save.operationId().equals(operation) && server.commits.get() == 1 && server.replays.get() == 1,
                        "a lost response replays the identical operation and CAS input without creating a second preset");
            }
            var items = await(presets.list().future(), callbacks);
            require(items.size() == 1 && items.getFirst().id().equals(id)
                            && await(presets.read(id).future(), callbacks).configuration().equals(config),
                    "owner-only list/read preserve the independent preset and outfit revisions");
            try (var stale = presets.save(id, 0, "stale", config)) {
                failure(stale.future(), callbacks, "REVISION_CONFLICT");
            }
            require(await(presets.read(id).future(), callbacks).name().equals("Private 中文 preset"),
                    "a stale update cannot replace a newer document");
            try (var update = presets.save(id, 1, "Updated", config)) {
                require(await(update.future(), callbacks).revision() == 2, "a matching expected revision explicitly updates the preset");
            }
            server.wrongRead.set(true);
            failure(presets.read(id).future(), callbacks, "INVALID_OUTFIT_PRESET_RESPONSE");
            server.wrongRead.set(false);
            server.fractionalRevision.set(true);
            failure(presets.list().future(), callbacks, "INVALID_OUTFIT_PRESET_RESPONSE");
            server.fractionalRevision.set(false);
            server.badAcknowledgement.set(true);
            try (var update = presets.save(id, 2, "Verified acknowledgement", config)) {
                failure(update.future(), callbacks, "OUTFIT_PRESET_RESPONSE_MISMATCH");
                int committed = server.commits.get();
                server.badAcknowledgement.set(false);
                require(await(update.retry(), callbacks).revision() == 3 && server.commits.get() == committed,
                        "a mismatched acknowledgement is rejected and can recover through the original idempotent operation");
            }
            server.armGate();
            var cancelled = presets.read(id);
            require(server.entered.await(5, TimeUnit.SECONDS), "read reaches the actual HTTP gate");
            cancelled.cancel();
            server.release.countDown();
            require(cancelled.future().isCancelled(), "closing the request cancels the actual in-flight read");
            server.armGate();
            var retired = presets.read(id);
            require(server.entered.await(5, TimeUnit.SECONDS), "retirement read reaches the server");
            live.set(false);
            server.release.countDown();
            drainUntil(retired.future(), callbacks);
            require(retired.future().isCancelled(), "a completed body cannot publish into a retired authenticated session");
            require(server.unexpected.get() == 0, "private presets never call model upload/equip/authorization endpoints");
            System.out.println("PASS cloud personal presets: real authenticated list/read/save, fixed CAS/idempotent retries, response validation, cancellation and session retirement; no publication");
        }
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        drainUntil(future, callbacks);
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void drainUntil(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable action;
            while ((action = callbacks.poll()) != null) {
                action.run();
            }
            Thread.sleep(1);
        }
        require(future.isDone(), "HTTP operation completes within the bounded wait");
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks, String code) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("Expected " + code);
        } catch (ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure failure && failure.code.equals(code),
                    "specific cloud preset failure: " + expected.getCause());
        }
    }

    static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final Map<UUID, JsonObject> documents = new HashMap<>();
        private final Map<UUID, JsonObject> operationInputs = new HashMap<>();
        private final Map<UUID, JsonObject> operationResults = new HashMap<>();
        final AtomicBoolean loseNext = new AtomicBoolean();
        final AtomicBoolean wrongRead = new AtomicBoolean();
        final AtomicBoolean fractionalRevision = new AtomicBoolean();
        final AtomicBoolean badAcknowledgement = new AtomicBoolean();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger replays = new AtomicInteger();
        final AtomicInteger unexpected = new AtomicInteger();
        private final AtomicBoolean gate = new AtomicBoolean();
        volatile CountDownLatch entered = new CountDownLatch(1);
        volatile CountDownLatch release = new CountDownLatch(1);

        Server() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void armGate() {
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            gate.set(true);
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                if (!"Bearer synthetic-owner".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    respond(exchange, 401, "{\"code\":\"UNAUTHORIZED\"}");
                    return;
                }
                if (gate.compareAndSet(true, false)) {
                    CountDownLatch gateRelease = release;
                    entered.countDown();
                    try {
                        if (!gateRelease.await(10, TimeUnit.SECONDS)) {
                            return;
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                String path = exchange.getRequestURI().getPath();
                String root = "/api/library/v1/outfit-presets";
                if (path.equals(root) && exchange.getRequestMethod().equals("POST")) {
                    var input = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    UUID operation = UUID.fromString(input.get("operation_id").getAsString());
                    JsonObject result = operationResults.get(operation);
                    if (result != null) {
                        if (!operationInputs.get(operation).equals(input)) {
                            respond(exchange, 409, "{\"code\":\"OPERATION_ID_REUSED\"}");
                            return;
                        }
                        replays.incrementAndGet();
                    } else {
                        UUID id = UUID.fromString(input.get("preset_id").getAsString());
                        long revision = documents.containsKey(id) ? documents.get(id).get("revision").getAsLong() : 0;
                        if (revision != input.get("expected_revision").getAsLong()) {
                            respond(exchange, 409, "{\"code\":\"REVISION_CONFLICT\"}");
                            return;
                        }
                        result = input.deepCopy();
                        result.remove("operation_id");
                        result.remove("expected_revision");
                        result.addProperty("revision", revision + 1);
                        documents.put(id, result.deepCopy());
                        operationInputs.put(operation, input.deepCopy());
                        operationResults.put(operation, result.deepCopy());
                        commits.incrementAndGet();
                    }
                    if (loseNext.compareAndSet(true, false)) {
                        respond(exchange, 503, "{\"code\":\"BUSY\"}");
                    } else {
                        result = result.deepCopy();
                        if (badAcknowledgement.get()) {
                            result.addProperty("name", "Wrong acknowledgement");
                        }
                        respond(exchange, 200, result.toString());
                    }
                } else if (path.equals(root) && exchange.getRequestMethod().equals("GET")) {
                    var response = new JsonObject();
                    var items = new com.google.gson.JsonArray();
                    for (JsonObject document : documents.values()) {
                        JsonObject item = document.deepCopy();
                        item.remove("configuration");
                        if (fractionalRevision.get()) {
                            item.addProperty("revision", 1.5);
                        }
                        items.add(item);
                    }
                    response.add("items", items);
                    respond(exchange, 200, response.toString());
                } else if (path.startsWith(root + "/") && exchange.getRequestMethod().equals("GET")) {
                    UUID id = UUID.fromString(path.substring(root.length() + 1));
                    JsonObject result = documents.get(id);
                    if (result == null) {
                        respond(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                    } else {
                        result = result.deepCopy();
                        if (wrongRead.get()) {
                            result.addProperty("preset_id", UUID.randomUUID().toString());
                        }
                        respond(exchange, 200, result.toString());
                    }
                } else {
                    unexpected.incrementAndGet();
                    respond(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                }
            }
        }

        private static void respond(HttpExchange exchange, int status, String value) throws java.io.IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
