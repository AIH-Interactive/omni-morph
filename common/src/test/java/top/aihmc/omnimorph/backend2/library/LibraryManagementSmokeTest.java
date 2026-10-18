package top.aihmc.omnimorph.backend2.library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual HTTP, authorization gates and Windows credential ACLs; no window/rendering assertions. */
public final class LibraryManagementSmokeTest {
    private static final UUID MODEL = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String ACCESS = "a".repeat(64);
    private static final String REFRESH = "b".repeat(64);

    public static void main(String[] args) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newCachedThreadPool();
        server.setExecutor(workers);
        var required = new AtomicBoolean(false);
        var grants = new AtomicInteger();
        var resolved = new AtomicInteger();
        var changes = new AtomicInteger();
        var ordinaryAdminLeaks = new AtomicInteger();
        var logouts = new AtomicInteger();
        var refreshes = new AtomicInteger();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/library/v1/", exchange -> {
            try {
                if (!"Bearer synthetic-player-session".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    throw new AssertionError("missing original player credential");
                }
                String path = exchange.getRequestURI().getPath();
                String token = exchange.getRequestHeaders().getFirst("x-omnimorph-admin");
                if (path.contains("/admin/")) {
                    require(origin.equals(exchange.getRequestHeaders().getFirst("x-omnimorph-origin")),
                            "login/refresh include the adapter-derived origin before an access token exists");
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (path.endsWith("/admin/login") || path.endsWith("/admin/refresh")) {
                    if (path.endsWith("refresh")) {
                        refreshes.incrementAndGet();
                        require(body.contains(REFRESH), "refresh uses the scoped saved credential");
                    }
                    respond(exchange, "{\"access_token\":\"" + ACCESS + "\",\"refresh_token\":\"" + REFRESH
                            + "\",\"expires_at\":" + (System.currentTimeMillis() / 1000 + 600) + "}");
                } else if (path.endsWith("/admin/logout")) {
                    require(ACCESS.equals(token), "logout explicitly attaches admin authority");
                    logouts.incrementAndGet();
                    respond(exchange, "{}");
                } else if (path.endsWith("/session")) {
                    boolean admin = ACCESS.equals(token);
                    respond(exchange, "{\"uuid\":\"" + OWNER + "\",\"name\":\"Uploader\",\"admin\":" + admin + ",\"equipment_revision\":0}");
                } else if (path.endsWith("/models/" + MODEL)) {
                    if (token != null) {
                        ordinaryAdminLeaks.incrementAndGet();
                    }
                    respond(exchange, model(required.get()));
                } else if (path.endsWith("/grants")) {
                    grants.incrementAndGet();
                    respond(exchange, "{\"items\":[{\"uuid\":\"" + OWNER + "\",\"name\":\"Uploader\",\"created_at\":1}],"
                            + "\"total\":1,\"page\":1,\"page_size\":8,\"has_next\":false}");
                } else if (path.endsWith("/players/resolve")) {
                    resolved.incrementAndGet();
                    require(exchange.getRequestURI().getRawQuery().contains("name=Other+%26+Player"), "player name is URL encoded");
                    respond(exchange, "{\"items\":[{\"uuid\":\"" + CANDIDATE + "\",\"name\":\"Other & Player\"}]}");
                } else if (path.contains("/grants/")) {
                    changes.incrementAndGet();
                    require(body.contains("\"expected_revision\":7"), "authorization retry keeps the original CAS");
                    respond(exchange, "{}");
                } else {
                    throw new AssertionError("unexpected route");
                }
            } catch (Throwable failure) {
                byte[] bytes = "{\"code\":\"TEST_CONTRACT_FAILED\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                failure.printStackTrace();
            }
        });
        server.start();
        var directory = Files.createTempDirectory("omnimorph-admin-test-");
        var queue = new ConcurrentLinkedQueue<Runnable>();
        var live = new AtomicBoolean(true);
        var endpoint = CloudEndpoint.parse(origin, () -> true);
        try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(),
                builder -> builder.header("Authorization", "Bearer synthetic-player-session"));
             var client = new LibraryClient(access, new LibrarySession("synthetic-instance", OWNER, 1, live::get), queue::add);
             var admin = new LibraryAdminSession(client, directory)) {
            var authorization = new LibraryAuthorization(client, MODEL, () -> "");
            failure(authorization.list(1).future(), queue, "AUTHORIZATION_NOT_REQUIRED");
            require(grants.get() == 0, "free models do not fetch authorization data");
            required.set(true);
            var listing = await(authorization.list(1).future(), queue);
            require(LibraryAuthorization.available(listing.model()) && listing.grants().total() == 1,
                    "required models show authorization management");
            require(await(authorization.resolve("Other & Player").future(), queue).items().getFirst().uuid().equals(CANDIDATE),
                    "candidate identity comes from verified server UUID");
            failure(authorization.change(new LibraryAuthorization.Change(OWNER, false, UUID.randomUUID(), 7)).future(), queue,
                    "UPLOADER_GRANT_REQUIRED");
            require(changes.get() == 0, "uploader revocation never leaves the client");
            var change = new LibraryAuthorization.Change(CANDIDATE, true, UUID.randomUUID(), 7);
            await(authorization.change(change).future(), queue);
            await(authorization.change(change).future(), queue);
            required.set(false);
            failure(authorization.resolve("Other & Player").future(), queue, "AUTHORIZATION_NOT_REQUIRED");
            failure(authorization.change(change).future(), queue, "AUTHORIZATION_NOT_REQUIRED");
            require(resolved.get() == 1 && changes.get() == 2, "late free-policy change blocks resolve and mutations");

            boolean remember = await(admin.rememberAvailable().future(), queue);
            require(await(admin.login("Uploader", "synthetic-password", remember, UUID.randomUUID()).future(), queue),
                    "login verifies the authenticated player and admin context");
            require(admin.active(), "verified admin session is active");
            await(client.detail(MODEL).future(), queue);
            require(ordinaryAdminLeaks.get() == 0, "ordinary requests never inherit administrator credentials");
            if (remember) {
                var store = new LibraryAdminStore(directory, client);
                require(store.read() != null, "remembered refresh credential is ACL protected");
                try (var restored = new LibraryAdminSession(client, directory)) {
                    require(await(restored.restore().future(), queue) && restored.active(), "refresh restores current authority");
                }
                require(refreshes.get() == 1, "refresh is a single explicit scoped operation");
                // Same directory with another player must not see the credential.
                try (var other = new LibraryClient(access,
                        new LibrarySession("synthetic-instance", CANDIDATE, 1, live::get), queue::add)) {
                    require(new LibraryAdminStore(directory, other).read() == null, "stored credentials are player-scoped");
                }
            }
            await(admin.logout().future(), queue);
            require(!admin.active() && logouts.get() == 1, "logout clears local authority and revokes the server session");
            if (remember) {
                require(new LibraryAdminStore(directory, client).read() == null, "logout removes remembered refresh credentials");
            }
            live.set(false);
            require(!admin.active(), "retired connections cannot retain administrator context");
        } finally {
            server.stop(0);
            workers.shutdownNow();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("PASS management: required-only authorization, fresh policy, uploader grant, UUID resolution, scoped admin authority, ACL remember/refresh/logout");
    }

    private static String model(boolean required) {
        return "{\"model_id\":\"" + MODEL + "\",\"revision\":1,\"policy_revision\":7,\"display_name\":\"Synthetic\","
                + "\"internal_name\":\"Internal\",\"authors\":[],\"uploader\":{\"uuid\":\"" + OWNER + "\",\"name\":\"Uploader\"},"
                + "\"model_type\":\"FIGURA\",\"visibility\":\"PRIVATE\",\"requires_authorization\":" + required
                + ",\"allowed_actions\":[\"grants\"],\"tags\":[]}";
    }

    private static void respond(HttpExchange exchange, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable callback;
            while ((callback = queue.poll()) != null) {
                callback.run();
            }
            Thread.sleep(5);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> queue, String code) throws Exception {
        try {
            await(future, queue);
            throw new AssertionError("expected " + code);
        } catch (ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure typed && typed.code.equals(code), "controlled failure: " + code);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
