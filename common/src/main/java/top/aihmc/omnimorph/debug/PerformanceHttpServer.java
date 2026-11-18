package top.aihmc.omnimorph.debug;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** A single HTTP worker serializes requests; only the client executor reads model state. */
public final class PerformanceHttpServer implements AutoCloseable {
    public static final String PATH = "/debug/performance";
    private final Executor clientExecutor;
    private final Supplier<JsonObject> capture;
    private HttpServer server;
    private ExecutorService worker;
    private volatile CompletableFuture<JsonObject> pending;
    private volatile long generation;
    private long requestId;
    private boolean enabled;
    private int configuredPort = -1;
    private int boundPort;
    private String error;

    public PerformanceHttpServer(Executor clientExecutor, Supplier<JsonObject> capture) {
        this.clientExecutor = clientExecutor;
        this.capture = capture;
    }

    /** Called on the client thread. Failed binds retry only after a setting changes. */
    public void configure(boolean debug, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Performance HTTP port must be 1..65535");
        }
        if (enabled == debug && configuredPort == port) {
            return;
        }
        close();
        enabled = debug;
        configuredPort = port;
        error = null;
        if (!debug) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
            worker = Executors.newSingleThreadExecutor(task -> {
                var thread = new Thread(task, "OMM performance HTTP");
                thread.setDaemon(true);
                return thread;
            });
            long session = generation;
            server.createContext(PATH, exchange -> handle(exchange, session));
            server.setExecutor(worker);
            server.start();
            boundPort = server.getAddress().getPort();
        } catch (IOException failure) {
            close();
            // Keep the attempted settings so a busy port does not cause a bind loop every tick.
            enabled = debug;
            configuredPort = port;
            error = "HTTP_BIND_FAILED";
        }
    }

    public int boundPort() {
        return boundPort;
    }

    public String error() {
        return error;
    }

    private void handle(HttpExchange exchange, long session) throws IOException {
        try (exchange) {
            if (!exchange.getRequestURI().getPath().equals(PATH)) {
                respond(exchange, 404, failure("NOT_FOUND"));
                return;
            }
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                respond(exchange, 405, failure("READ_ONLY"));
                return;
            }
            long queued = System.nanoTime();
            long id = ++requestId;
            var result = new CompletableFuture<JsonObject>();
            pending = result;
            clientExecutor.execute(() -> {
                if (session != generation || result.isDone()) {
                    result.cancel(false);
                    return;
                }
                try {
                    long started = System.nanoTime();
                    JsonObject snapshot = capture.get();
                    snapshot.addProperty("schemaVersion", 1);
                    snapshot.addProperty("requestId", id);
                    snapshot.addProperty("capturedAt", Instant.now().toString());
                    var request = new JsonObject();
                    request.addProperty("queueMs", (started - queued) / 1_000_000d);
                    request.addProperty("captureMs", (System.nanoTime() - started) / 1_000_000d);
                    snapshot.add("request", request);
                    result.complete(snapshot);
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
            try {
                respond(exchange, 200, result.get(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                respond(exchange, 503, failure("CAPTURE_CANCELLED"));
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
                     | java.util.concurrent.CancellationException unavailable) {
                respond(exchange, 503, failure("CAPTURE_UNAVAILABLE"));
            } finally {
                result.cancel(false);
                pending = null;
            }
        }
    }

    private static JsonObject failure(String code) {
        var result = new JsonObject();
        result.addProperty("error", code);
        return result;
    }

    private static void respond(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        generation++;
        if (pending != null) {
            pending.cancel(false);
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        boundPort = 0;
        enabled = false;
        configuredPort = -1;
    }
}
