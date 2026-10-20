package top.aihmc.omnimorph.backend2.library;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Actual session read and existing equip CAS requests; no game activation or GUI substitute. */
public final class LibraryEquipmentVisibilitySmokeTest {
    public static void main(String[] args) throws Exception {
        UUID player = UUID.randomUUID();
        UUID model = UUID.randomUUID();
        var showing = new AtomicBoolean();
        var publicModel = new AtomicBoolean();
        var blocked = new AtomicBoolean();
        var switched = new AtomicBoolean();
        var loseResponse = new AtomicBoolean();
        var revision = new AtomicLong(7);
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/library/v1/", exchange -> {
            int status = 200;
            String response;
            try {
                require("Bearer synthetic-player".equals(exchange.getRequestHeaders().getFirst("Authorization")),
                        "visibility retains the authenticated player credential");
                if (exchange.getRequestURI().getPath().endsWith("/session")) {
                    response = "{\"uuid\":\"" + player + "\",\"name\":\"Player\",\"admin\":false,"
                            + "\"equipment_revision\":" + revision.get() + ",\"equipment_model_id\":"
                            + (switched.get() ? "null" : "\"" + model + "\"")
                            + ",\"equipment_model_revision\":1,\"show_private_equipment\":"
                            + (!publicModel.get() && showing.get() && !blocked.get())
                            + ",\"show_equipment\":" + (showing.get() && !blocked.get()) + "}";
                } else {
                    require(exchange.getRequestURI().getPath().equals("/api/library/v1/models/" + model + "/equip")
                            && exchange.getRequestMethod().equals("POST"), "only existing equip metadata is mutated");
                    var input = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    if (blocked.get() || switched.get()) {
                        status = 403;
                        response = "{\"code\":\"FORBIDDEN\"}";
                    } else if (input.get("expected_equipment_revision").getAsLong() != revision.get()) {
                        status = 409;
                        response = "{\"code\":\"CONFLICT\"}";
                    } else {
                        require(input.has("show_equipment"), "general visibility is explicitly sent for public as well as private equipment");
                        showing.set(input.get("show_equipment").getAsBoolean());
                        long saved = revision.incrementAndGet();
                        if (loseResponse.getAndSet(false)) {
                            return;
                        }
                        response = "{\"model_id\":\"" + model + "\",\"model_revision\":1,"
                                + "\"equipment_revision\":" + saved + ",\"owner\":\"" + player + "\","
                                + "\"asset_id\":\"lib_" + model.toString().replace("-", "")
                                + "_1\",\"asset_revision\":1,\"show_private_equipment\":" + showing.get() + "}";
                    }
                }
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                request -> request.header("Authorization", "Bearer synthetic-player"));
             var client = new LibraryClient(access, new LibrarySession(origin, player, 1, () -> true), callbacks::add)) {
            var service = new LibraryEquipmentVisibility(client);
            var initial = await(service.read(model).future(), callbacks);
            require(initial.equipped() && !initial.showing(), "default private appearance is hidden");
            var saved = await(service.save(initial, true).future(), callbacks);
            require(saved.showing() && saved.equipmentRevision() == 8, "save is confirmed by fresh server state");
            var reopened = new LibraryEquipmentVisibility(client);
            require(await(reopened.read(model).future(), callbacks).showing(), "reopening reads the persisted choice");
            failure(service.save(initial, false).future(), callbacks);
            require(await(service.read(model).future(), callbacks).showing(), "stale CAS cannot overwrite the current choice");
            loseResponse.set(true);
            failure(service.save(saved, false).future(), callbacks);
            require(!await(service.read(model).future(), callbacks).showing(), "lost acknowledgement is resolved from authoritative state");
            publicModel.set(true);
            showing.set(true);
            var publicState = await(service.read(model).future(), callbacks);
            require(publicState.showing(), "public equipment uses general visibility even when the legacy private flag is false");
            var hiddenPublic = await(service.save(publicState, false).future(), callbacks);
            require(!hiddenPublic.showing(), "public equipment can be hidden without changing its source");
            require(await(service.save(hiddenPublic, true).future(), callbacks).showing(), "public equipment can be shown again");
            blocked.set(true);
            failure(service.save(await(service.read(model).future(), callbacks), true).future(), callbacks);
            require(!await(service.read(model).future(), callbacks).showing(), "blocking cannot be overridden by the switch");
            switched.set(true);
            require(!await(service.read(model).future(), callbacks).equipped(), "a different current source cannot be mutated");
        } finally {
            server.stop(0);
        }
        System.out.println("PASS public/private equipment visibility: persisted readback, CAS, lost response, blocking and source isolation");
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) {
                callback.run();
            }
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("stale or forbidden visibility update was accepted");
        } catch (ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure, "failure remains a typed client error");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
