package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real HTTP checks for cover revision sequencing, scoped admin authority and immutable retries. */
public final class LibraryModelEditSmokeTest {
    private static final UUID MODEL = new UUID(0, 9);
    private static final String ADMIN = "a".repeat(64);

    public static void main(String[] args) throws Exception {
        var patches = new AtomicInteger();
        var puts = new AtomicInteger();
        var deletes = new AtomicInteger();
        var operation = new AtomicReference<String>();
        var failure = new AtomicReference<Throwable>();
        byte[] image = {1, 2, 3};
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/library/v1/models/" + MODEL, exchange -> {
            try {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                String token = exchange.getRequestHeaders().getFirst("x-omnimorph-admin");
                switch (exchange.getRequestMethod()) {
                    case "PATCH" -> {
                        patches.incrementAndGet();
                        var body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                        long revision = body.get("expected_revision").getAsLong();
                        require((revision == 10) == ADMIN.equals(token), "admin token stays scoped to admin edit");
                        respond(exchange, 200, receipt(revision + 1));
                    }
                    case "PUT" -> {
                        require(token == null, "ordinary edits do not carry admin authority");
                        require(Arrays.equals(bytes, image), "selected cover bytes are uploaded unchanged");
                        require("2".equals(exchange.getRequestHeaders().getFirst("x-omnimorph-expected-revision")),
                                "cover compares against the metadata receipt, not the original revision");
                        String id = exchange.getRequestHeaders().getFirst("x-omnimorph-operation");
                        if (puts.incrementAndGet() == 1) {
                            operation.set(id);
                            respond(exchange, 503, "{\"code\":\"LIBRARY_UNAVAILABLE\"}");
                        } else {
                            require(id.equals(operation.get()), "cover retry uses the same operation identity");
                            respond(exchange, 200, receipt(3));
                        }
                    }
                    case "DELETE" -> {
                        deletes.incrementAndGet();
                        require(ADMIN.equals(token), "administrator cover deletion carries scoped authority");
                        var body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                        require(body.get("expected_revision").getAsLong() == 11, "delete follows metadata revision");
                        require(body.has("operation_id"), "cover deletion has an idempotency identity");
                        respond(exchange, 200, receipt(12));
                    }
                    default -> throw new AssertionError("unexpected request");
                }
            } catch (Throwable problem) {
                failure.set(problem);
                respond(exchange, 500, "{\"code\":\"TEST_FAILURE\"}");
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                builder -> builder.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(origin, new UUID(0, 1), 1, () -> true), Runnable::run)) {
            var edit = new LibraryModelEditOperation(MODEL, metadata(1),
                    new LibraryUploadService.Image(image, "image/png"), false);
            try {
                execute(client, edit, false);
                throw new AssertionError("first cover response must fail");
            } catch (ExecutionException expected) {
                require(expected.getCause() instanceof LibraryClient.Failure, "typed failure remains retryable");
            }
            execute(client, edit, false);
            require(patches.get() == 1 && puts.get() == 2, "retry resumes cover without resubmitting metadata");
            execute(client, new LibraryModelEditOperation(MODEL, metadata(10), null, true), true);
            require(deletes.get() == 1, "explicit clear deletes the submitted cover");
            execute(client, new LibraryModelEditOperation(MODEL, metadata(20), null, false), false);
            require(puts.get() == 2 && deletes.get() == 1, "unchanged cover is neither replaced nor deleted");
            if (failure.get() != null) {
                throw new AssertionError("HTTP contract", failure.get());
            }
        } finally {
            server.stop(0);
        }
        System.out.println("LibraryModelEditSmokeTest passed");
    }

    private static Map<String, Object> metadata(long revision) {
        return Map.of("operation_id", UUID.randomUUID().toString(), "expected_revision", revision,
                "display_name", "Edited model");
    }

    private static void execute(LibraryClient client, LibraryModelEditOperation operation, boolean admin) throws Exception {
        client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (admin) {
                exchange.adminToken(ADMIN);
            }
            operation.execute(exchange);
            return true;
        }).future().get(10, TimeUnit.SECONDS);
    }

    private static String receipt(long revision) {
        var result = new JsonObject();
        result.addProperty("model_id", MODEL.toString());
        result.addProperty("policy_revision", revision);
        return result.toString();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
