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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Library-only HTTP access. Credentials stay inside the selected wire adapter. */
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

    @Override
    public synchronized void close() {
        closed = true;
        for (Call call : Set.copyOf(calls)) {
            call.close();
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
            URI relative = URI.create(path);
            if (path.length() > 8192 || relative.isAbsolute() || relative.getRawAuthority() != null
                    || relative.getRawFragment() != null || relative.getRawPath() == null
                    || !relative.getRawPath().matches("/api/library/v1/[A-Za-z0-9/_-]+")
                    || !Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                throw new IOException("INVALID_LIBRARY_ROUTE");
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint.http(path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-store")
                    .method(method, body);
            authorizer.authorize(request);
            if (contentType != null) {
                request.header("Content-Type", contentType);
            }
            if (adminToken != null && !adminToken.isEmpty()) {
                if (adminToken.length() > 1024) {
                    throw new IOException("INVALID_ADMIN_SESSION");
                }
                request.header("x-omnimorph-admin", adminToken);
                request.header("x-omnimorph-origin", endpoint.origin().toString());
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
