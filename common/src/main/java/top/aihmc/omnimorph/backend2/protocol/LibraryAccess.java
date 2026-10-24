package top.aihmc.omnimorph.backend2.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Library HTTP and outfit-event access. Credentials stay inside the selected wire adapter. */
public final class LibraryAccess implements AutoCloseable {
    private static final ScheduledThreadPoolExecutor DEADLINES = deadlines();

    private static ScheduledThreadPoolExecutor deadlines() {
        var executor = new ScheduledThreadPoolExecutor(1, action -> {
            Thread thread = new Thread(action, "OmniMorph library deadlines");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    @FunctionalInterface
    public interface Authorizer {
        void authorize(HttpRequest.Builder request) throws IOException;
    }

    private final CloudEndpoint endpoint;
    private final HttpClient client;
    private final Authorizer authorizer;
    private final Set<Call> calls = new HashSet<>();
    private final Set<OutfitConnection> outfitConnections = new HashSet<>();
    private boolean closed;

    public LibraryAccess(CloudEndpoint endpoint, HttpClient client, Authorizer authorizer) {
        this.endpoint = endpoint;
        this.client = client;
        this.authorizer = authorizer;
    }

    public synchronized Call open() throws IOException {
        endpoint.requireAllowed();
        if (closed) {
            throw new IOException("LIBRARY_RETIRED");
        }
        if (calls.size() >= 8) {
            throw new IOException("LIBRARY_BUSY");
        }
        Call call = new Call(new CloudHttp(client, endpoint));
        calls.add(call);
        return call;
    }

    public String origin() {
        return endpoint.origin().toString();
    }

    /** Credentials are copied only to this same-origin, fixed-purpose WebSocket handshake. */
    public synchronized OutfitConnection openOutfitEvents(Consumer<byte[]> receive,
                                                          Consumer<String> failure,
                                                          Runnable ready) throws IOException {
        endpoint.requireAllowed();
        if (closed || !outfitConnections.isEmpty()) {
            throw new IOException(closed ? "LIBRARY_RETIRED" : "LIBRARY_BUSY");
        }
        var request = HttpRequest.newBuilder(endpoint.http("/api/library/v1/outfit-events"));
        authorizer.authorize(request);
        request.header("x-omnimorph-origin", origin());
        var builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .subprotocols("omnimorph.outfit.v1");
        request.build().headers().map().forEach((name, values) -> {
            for (String value : values) {
                builder.header(name, value);
            }
        });
        URI target = endpoint.websocket("/api/library/v1/outfit-events");
        var connection = new OutfitConnection(receive, failure);
        outfitConnections.add(connection);
        try {
            connection.opening = builder.buildAsync(target, connection.socket);
        } catch (RuntimeException rejected) {
            connection.close();
            throw new IOException("OUTFIT_EVENTS_UNAVAILABLE");
        }
        connection.opening.whenComplete((socket, error) -> {
            try {
                if (error != null) {
                    connection.failed("Outfit socket handshake failed");
                } else {
                    connection.socket.attach(socket);
                    if (connection.finished) {
                        socket.abort();
                    } else {
                        ready.run();
                    }
                }
            } catch (Exception rejected) {
                connection.failed("Outfit socket retired");
            }
        });
        return connection;
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (Call call : Set.copyOf(calls)) {
            call.close();
        }
        for (OutfitConnection connection : Set.copyOf(outfitConnections)) {
            connection.close();
        }
    }

    public final class OutfitConnection implements AutoCloseable {
        private final CloudSocket socket;
        private final Consumer<String> failure;
        private volatile CompletableFuture<java.net.http.WebSocket> opening;
        private volatile boolean finished;

        private OutfitConnection(Consumer<byte[]> receive, Consumer<String> failure) {
            this.failure = failure;
            socket = new CloudSocket(16_384, receive, this::failed, endpoint, true);
        }

        public void send(String json) throws IOException {
            if (finished) {
                throw new IOException("LIBRARY_RETIRED");
            }
            socket.sendText(json);
        }

        private void failed(String reason) {
            if (!finished) {
                close();
                failure.accept(reason);
            }
        }

        @Override
        public void close() {
            finished = true;
            socket.close();
            var pending = opening;
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
            synchronized (LibraryAccess.this) {
                outfitConnections.remove(this);
            }
        }
    }

    public final class Call implements AutoCloseable {
        private final CloudHttp http;
        private ScheduledFuture<?> deadline;
        private boolean finished;

        private Call(CloudHttp http) {
            this.http = http;
        }

        public HttpResponse<InputStream> send(String method, String path, HttpRequest.BodyPublisher body,
                                              String contentType, String adminToken) throws Exception {
            return send(method, path, body, contentType, adminToken, java.util.Map.of());
        }

        public HttpResponse<InputStream> send(String method, String path, HttpRequest.BodyPublisher body,
                                              String contentType, String adminToken,
                                              java.util.Map<String, String> mutationHeaders) throws Exception {
            URI relative = URI.create(path);
            if (path.length() > 8192 || relative.isAbsolute() || relative.getRawAuthority() != null
                    || relative.getRawFragment() != null || relative.getRawPath() == null
                    || !relative.getRawPath().matches("/api/library/v1/[A-Za-z0-9/_-]+")
                    || !Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                throw new IOException("INVALID_LIBRARY_ROUTE");
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint.http(path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .method(method, body);
            authorizer.authorize(request);
            for (var entry : mutationHeaders.entrySet()) {
                boolean valid = switch (entry.getKey()) {
                    case "x-omnimorph-operation" -> entry.getValue().matches(
                            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
                    case "x-omnimorph-expected-revision" -> entry.getValue().matches("[1-9][0-9]{0,18}");
                    default -> false;
                };
                if (!valid) {
                    throw new IOException("INVALID_LIBRARY_HEADER");
                }
                request.header(entry.getKey(), entry.getValue());
            }
            if (contentType != null) {
                request.header("Content-Type", contentType);
            }
            request.header("x-omnimorph-origin", endpoint.origin().toString());
            if (adminToken != null && !adminToken.isEmpty()) {
                if (adminToken.length() > 1024) {
                    throw new IOException("INVALID_ADMIN_SESSION");
                }
                request.header("x-omnimorph-admin", adminToken);
            }
            synchronized (this) {
                if (finished) {
                    throw new IOException("LIBRARY_RETIRED");
                }
                if (deadline != null) {
                    deadline.cancel(false);
                }
                // HttpRequest timeout alone ends at response headers with an InputStream body.
                // This closes stalled bodies too, releasing the worker and download slot.
                deadline = DEADLINES.schedule(this::close, 60, TimeUnit.SECONDS);
            }
            return http.send(request.build());
        }

        @Override
        public void close() {
            synchronized (this) {
                if (finished) {
                    return;
                }
                finished = true;
                if (deadline != null) {
                    deadline.cancel(false);
                }
            }
            http.close();
            synchronized (LibraryAccess.this) {
                calls.remove(this);
            }
        }
    }
}
