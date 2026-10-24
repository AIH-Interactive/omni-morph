package top.aihmc.omnimorph.backend2.library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Real local HTTP and production wire-adapter auth; no game window or rendering assertions. */
public final class LibraryClientSmokeTest {
    private static final String CAPABILITIES = """
            {"enabled":true,"version":1,"max_upload_bytes":67108864,"max_page_size":8,
             "max_image_bytes":2097152,"max_image_width":2048,"max_image_height":2048,
             "formats":["zip","ysm"],"model_types":["FIGURA","YSM_PLAIN","YSM_ENCRYPTED"],
             "categories":["default"],"admin_login_available":false}
            """;

    public static void main(String[] args) throws Exception {
        var gson = new com.google.gson.GsonBuilder()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        var missing = gson.fromJson("{\"available\":false}", LibraryModels.WardrobeState.class);
        require(!missing.showing() && !missing.canChangeVisibility(), "missing wardrobe is disabled and hidden");
        for (boolean blocked : new boolean[]{false, true}) {
            for (boolean show : new boolean[]{false, true}) {
                var value = gson.fromJson("{\"available\":true,\"asset_id\":\"synthetic\",\"asset_revision\":2,"
                        + "\"equipment_revision\":3,\"show_equipment\":" + show + ",\"blocked\":" + blocked + "}",
                        LibraryModels.WardrobeState.class);
                require(value.showing() == (show && !blocked) && value.canChangeVisibility() == !blocked,
                        "blocked wardrobe cannot be shown even if a stale response retained the show intent");
            }
        }
        for (boolean protobuf : new boolean[]{false, true}) {
            try (var server = new Server(protobuf)) {
                testAdapterAndClient(server, protobuf);
            }
        }
        System.out.println("PASS library client: both adapter credentials, bounded queues, independent transfer lane, cancellation, stale delivery and HTTP/JSON boundaries");
    }

    private static void testAdapterAndClient(Server server, boolean protobuf) throws Exception {
        var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
        var http = HttpClient.newHttpClient();
        CloudProtocol protocol = protobuf
                ? new ProtobufCloudProtocol(endpoint, "synthetic-original-token", "test", 77, http)
                : new FiguraCloudProtocol(endpoint, "synthetic-original-token", http);
        // These tests exercise actual adapter HTTP authentication, independently of WS discovery.
        set(protocol, "ready", true);
        if (protobuf) {
            set(protocol, "session", "synthetic-scoped-session");
        }
        var live = new AtomicBoolean(true);
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        var session = new LibrarySession(server.origin(), UUID.fromString("00000000-0000-0000-0000-000000000001"),
                77, () -> live.get() && protocol.connected());
        try (protocol; var client = new LibraryClient(protocol.libraryAccess(), session, callbacks::add)) {
            var request = client.capabilities();
            waitFor(() -> !callbacks.isEmpty());
            require(!request.future().isDone(), "worker completion waits for the client-thread mailbox");
            var capabilities = await(request.future(), callbacks);
            require(capabilities.enabled() && capabilities.maxPageSize() == 8, "capabilities DTO maps snake_case fields");
            var page = await(client.list(Map.of("q", "中文 & +", "page", "1", "page_size", "8")).future(), callbacks);
            require(page.total() == 0 && page.items().isEmpty(), "list pagination parses on a worker");
            require(server.encodedQuery.get(), "search terms are encoded rather than becoming query parameters");

            server.capabilityStatus.set(404);
            require(!await(client.capabilities().future(), callbacks).enabled(), "old backend is unsupported without protocol fallback");
            server.capabilityStatus.set(401);
            failure(client.capabilities().future(), callbacks, 401, "UNAUTHENTICATED");
            server.capabilityStatus.set(200);

            var retired = client.capabilities();
            waitFor(() -> !callbacks.isEmpty());
            live.set(false);
            drain(callbacks);
            require(retired.future().isCancelled(), "old server/player context cannot publish a queued completion");
            live.set(true);

            var stalled = client.submit(LibraryClient.Lane.CONTROL,
                    exchange -> exchange.json("GET", "/api/library/v1/stall", null, LibraryModels.Capabilities.class));
            require(server.stallStarted.await(5, TimeUnit.SECONDS), "real HTTP response is waiting for its body");
            stalled.cancel();
            require(stalled.future().isCancelled(), "cancelling a live response closes its call");
            require(await(client.capabilities().future(), callbacks).enabled(), "cancelling one call keeps other calls and wardrobe auth alive");
            server.stallRelease.countDown();

            var release = new CountDownLatch(1);
            var started = new CountDownLatch(2);
            var transfers = new ArrayList<LibraryClient.Request<Integer>>();
            try {
                for (int index = 0; index < 6; index++) {
                    transfers.add(client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
                        started.countDown();
                        require(release.await(5, TimeUnit.SECONDS), "bounded transfer batch releases");
                        return 1;
                    }));
                }
                require(started.await(5, TimeUnit.SECONDS), "two transfer workers are occupied");
                failure(client.submit(LibraryClient.Lane.TRANSFER, exchange -> 2).future(), callbacks, 0, "LIBRARY_BUSY");
                require(await(client.capabilities().future(), callbacks).enabled(), "occupied file workers do not block control requests");
            } finally {
                release.countDown();
                transfers.forEach(LibraryClient.Request::cancel);
            }

            failure(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                    "/api/library/v1/large", null, LibraryModels.Capabilities.class)).future(), callbacks, 0, "LIBRARY_REQUEST_FAILED");
            failure(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                    "/api/library/v1/redirect", null, LibraryModels.Capabilities.class)).future(), callbacks, 0, "LIBRARY_REQUEST_FAILED");
            failure(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                    "https://example.invalid/api/library/v1/capabilities", null, LibraryModels.Capabilities.class)).future(), callbacks, 0, "LIBRARY_REQUEST_FAILED");
            require(server.redirectTarget.get() == 0 && server.authErrors.get() == 0,
                    "redirects never receive credentials; adapter credentials and origin stay bound to the endpoint");
            require(await(client.capabilities().future(), callbacks).enabled(), "HTTP boundary failures do not retire the shared adapter");

            var queued = client.capabilities();
            waitFor(() -> !callbacks.isEmpty());
            protocol.close();
            drain(callbacks);
            require(queued.future().isCancelled(), "adapter retirement invalidates pending library publication");
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        waitFor(() -> {
            drain(callbacks);
            return future.isDone();
        });
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks,
                                int status, String code) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("Expected a bounded library failure");
        } catch (ExecutionException error) {
            require(error.getCause() instanceof LibraryClient.Failure failure
                            && failure.status == status && failure.code.equals(code),
                    "failure retains only the expected status/code: " + error.getCause());
        }
    }

    private static void drain(ConcurrentLinkedQueue<Runnable> callbacks) {
        Runnable action;
        while ((action = callbacks.poll()) != null) {
            action.run();
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        require(condition.getAsBoolean(), "bounded async condition completes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(4);
        private final AtomicInteger capabilityStatus = new AtomicInteger(200);
        private final AtomicInteger authErrors = new AtomicInteger();
        private final AtomicInteger redirectTarget = new AtomicInteger();
        private final AtomicBoolean encodedQuery = new AtomicBoolean();
        private final CountDownLatch stallStarted = new CountDownLatch(1);
        private final CountDownLatch stallRelease = new CountDownLatch(1);

        private Server(boolean protobuf) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", request -> handle(request, protobuf));
            server.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange request, boolean protobuf) throws java.io.IOException {
            try (request) {
                var headers = request.getRequestHeaders();
                boolean valid = protobuf
                        ? "Bearer synthetic-scoped-session".equals(headers.getFirst("Authorization"))
                            && "77".equals(headers.getFirst("x-omnimorph-epoch"))
                            && headers.getFirst("x-omnimorph-auth-mode") == null
                        : "Bearer synthetic-original-token".equals(headers.getFirst("Authorization"))
                            && "figura".equals(headers.getFirst("x-omnimorph-auth-mode"))
                            && headers.getFirst("x-omnimorph-epoch") == null;
                boolean validOrigin = origin().equals(headers.getFirst("x-omnimorph-origin"))
                        && headers.get("x-omnimorph-origin").size() == 1;
                if (!valid || !validOrigin) {
                    authErrors.incrementAndGet();
                }
                int status = 200;
                String body = CAPABILITIES;
                switch (request.getRequestURI().getPath()) {
                    case "/api/library/v1/capabilities" -> {
                        status = capabilityStatus.get();
                        if (status != 200) {
                            body = "{\"code\":\"UNAUTHENTICATED\",\"private_detail\":\"never surface this\"}";
                        }
                    }
                    case "/api/library/v1/models" -> {
                        String query = request.getRequestURI().getRawQuery();
                        encodedQuery.set(query.contains("q=%E4%B8%AD%E6%96%87+%26+%2B"));
                        body = "{\"items\":[],\"total\":0,\"page\":1,\"page_size\":8,\"has_next\":false}";
                    }
                    case "/api/library/v1/large" -> body = "x".repeat(65537);
                    case "/api/library/v1/redirect" -> {
                        request.getResponseHeaders().add("Location", origin() + "/sensitive");
                        status = 302;
                        body = "";
                    }
                    case "/sensitive" -> redirectTarget.incrementAndGet();
                    default -> {
                    }
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                request.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (request.getRequestURI().getPath().equals("/api/library/v1/stall")) {
                    stallStarted.countDown();
                    try {
                        require(stallRelease.await(5, TimeUnit.SECONDS), "stalled response is bounded");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                request.getResponseBody().write(bytes);
            }
        }

        @Override
        public void close() {
            stallRelease.countDown();
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
