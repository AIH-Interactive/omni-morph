package top.aihmc.omnimorph.backend2.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded worker operations with completion delivery on the client thread. */
public final class LibraryClient implements AutoCloseable {
    public enum Lane {
        CONTROL, TRANSFER, THUMBNAIL
    }

    @FunctionalInterface
    public interface Task<T> {
        T run(Exchange exchange) throws Exception;
    }

    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private static final String ROOT = "/api/library/v1/";
    private final LibraryAccess access;
    private final LibrarySession session;
    private final Executor clientThread;
    private final Map<Lane, ThreadPoolExecutor> executors;
    private final Set<Request<?>> requests = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public LibraryClient(LibraryAccess access, LibrarySession session, Executor clientThread) {
        this.access = access;
        this.session = session;
        this.clientThread = clientThread;
        executors = Map.of(Lane.CONTROL, executor("control", 2, 16),
                Lane.TRANSFER, executor("transfer", 2, 4),
                Lane.THUMBNAIL, executor("thumbnail", 4, 16));
    }

    private static ThreadPoolExecutor executor(String lane, int workers, int queue) {
        var sequence = new AtomicInteger();
        return new ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), action -> {
                    Thread thread = new Thread(action, "OmniMorph library " + lane + " " + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public LibrarySession session() {
        return session;
    }

    public boolean current() {
        return !closed && session.current();
    }

    public Request<LibraryModels.Capabilities> capabilities() {
        return submit(Lane.CONTROL, exchange -> {
            try {
                return exchange.json("GET", ROOT + "capabilities", null, LibraryModels.Capabilities.class);
            } catch (Failure failure) {
                if (failure.status == 404 || failure.status == 405) {
                    return LibraryModels.Capabilities.unsupported();
                }
                throw failure;
            }
        });
    }

    public Request<LibraryModels.Page> list(Map<String, String> query) {
        StringBuilder path = new StringBuilder(ROOT + "models?");
        for (var entry : Map.copyOf(query).entrySet()) {
            if (!Set.of("scope", "q", "category", "tags", "type", "authorization", "status", "sort", "page", "page_size")
                    .contains(entry.getKey())) {
                throw new IllegalArgumentException("INVALID_LIST_QUERY");
            }
            path.append(encode(entry.getKey())).append('=').append(encode(entry.getValue())).append('&');
        }
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", path.toString(), null, LibraryModels.Page.class));
    }

    public Request<LibraryModels.Model> detail(UUID model) {
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", ROOT + "models/" + model,
                null, LibraryModels.Model.class));
    }

    public Request<LibraryModels.SessionInfo> sessionInfo() {
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", ROOT + "session",
                null, LibraryModels.SessionInfo.class));
    }

    public <T> Request<T> submit(Lane lane, Task<T> action) {
        var request = new Request<T>(executors.get(lane));
        synchronized (requests) {
            if (closed || !session.current() || requests.size() >= 64) {
                request.future.completeExceptionally(new Failure(0, "LIBRARY_UNAVAILABLE"));
                return request;
            }
            requests.add(request);
        }
        request.future.whenComplete((value, error) -> {
            if (request.future.isCancelled()) {
                request.cancel();
            }
            requests.remove(request);
        });
        request.work = new FutureTask<>(() -> {
            T result = null;
            Throwable failure = null;
            try (LibraryAccess.Call call = access.open()) {
                request.call = call;
                request.requireCurrent();
                result = action.run(new Exchange(request, call));
                request.requireCurrent();
            } catch (Exception error) {
                failure = error instanceof Failure || error instanceof CancellationException
                        ? error : new Failure(0, "LIBRARY_REQUEST_FAILED");
            } finally {
                request.call = null;
            }
            T completed = result;
            Throwable error = failure;
            try {
                clientThread.execute(() -> {
                    try {
                        request.requireCurrent();
                        if (error != null) {
                            request.future.completeExceptionally(error);
                        } else {
                            request.future.complete(completed);
                        }
                    } catch (CancellationException retired) {
                        request.future.cancel(false);
                    }
                });
            } catch (RejectedExecutionException unavailable) {
                request.future.cancel(false);
            }
            return null;
        });
        try {
            request.executor.execute(request.work);
        } catch (RejectedExecutionException full) {
            request.future.completeExceptionally(new Failure(0, "LIBRARY_BUSY"));
        }
        return request;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        synchronized (requests) {
            closed = true;
        }
        for (Request<?> request : Set.copyOf(requests)) {
            request.cancel();
        }
        executors.values().forEach(ThreadPoolExecutor::shutdownNow);
    }

    public final class Request<T> implements AutoCloseable {
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ThreadPoolExecutor executor;
        private volatile LibraryAccess.Call call;
        private volatile FutureTask<Void> work;

        private Request(ThreadPoolExecutor executor) {
            this.executor = executor;
        }

        public CompletableFuture<T> future() {
            return future;
        }

        public void requireCurrent() {
            if (cancelled.get() || !current()) {
                throw new CancellationException("LIBRARY_RETIRED");
            }
        }

        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            LibraryAccess.Call active = call;
            if (active != null) {
                active.close();
            }
            FutureTask<Void> task = work;
            if (task != null) {
                task.cancel(true);
                executor.remove(task);
            }
            future.cancel(false);
        }

        @Override
        public void close() {
            cancel();
        }
    }

    public final class Exchange {
        private final Request<?> request;
        private final LibraryAccess.Call call;
        private String adminToken = "";

        private Exchange(Request<?> request, LibraryAccess.Call call) {
            this.request = request;
            this.call = call;
        }

        /** Set only for an explicit admin operation, never for ordinary observer downloads. */
        public void adminToken(String token) {
            adminToken = token == null ? "" : token;
        }

        public void requireCurrent() {
            request.requireCurrent();
        }

        public HttpResponse<InputStream> send(String method, String path, HttpRequest.BodyPublisher body,
                                              String contentType) throws Exception {
            request.requireCurrent();
            return call.send(method, path, body, contentType, adminToken);
        }

        public <T> T json(String method, String path, Object body, Class<T> type) throws Exception {
            byte[] encoded = body == null ? new byte[0] : JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            if (encoded.length > CloudHttp.CONTROL_BYTES) {
                throw new Failure(0, "CONTROL_LIMIT");
            }
            var response = send(method, path, HttpRequest.BodyPublishers.ofByteArray(encoded), "application/json");
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = CloudHttp.readBounded(input, CloudHttp.CONTROL_BYTES);
            }
            request.requireCurrent();
            if (response.statusCode() != 200) {
                throw responseFailure(response.statusCode(), bytes);
            }
            try {
                T value = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
                if (value == null) {
                    throw new IllegalArgumentException();
                }
                return value;
            } catch (RuntimeException malformed) {
                throw new Failure(0, "INVALID_LIBRARY_RESPONSE");
            }
        }
    }

    private static Failure responseFailure(int status, byte[] bytes) {
        String code = "LIBRARY_HTTP_ERROR";
        UUID requestId = null;
        boolean retryable = status == 429 || status == 503;
        try {
            JsonObject object = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            String candidate = object.get("code").getAsString();
            if (candidate.matches("[A-Z_]{1,64}")) {
                code = candidate;
            }
            if (object.has("request_id")) {
                requestId = UUID.fromString(object.get("request_id").getAsString());
            }
            if (object.has("retryable") && object.get("retryable").isJsonPrimitive()
                    && object.getAsJsonPrimitive("retryable").isBoolean()) {
                retryable = object.get("retryable").getAsBoolean();
            }
        } catch (RuntimeException ignored) {
            // Do not surface HTML, private model text, credentials or arbitrary server errors.
        }
        return new Failure(status, code, requestId, retryable);
    }

    public static final class Failure extends IOException {
        public final int status;
        public final String code;
        public final UUID requestId;
        public final boolean retryable;

        public Failure(int status, String code) {
            this(status, code, null, false);
        }

        private Failure(int status, String code, UUID requestId, boolean retryable) {
            super(code);
            this.status = status;
            this.code = code;
            this.requestId = requestId;
            this.retryable = retryable;
        }
    }
}
