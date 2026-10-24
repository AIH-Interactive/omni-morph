package top.aihmc.omnimorph.backend2.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
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
    private OutfitEvents outfitEvents;

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

    public String origin() {
        return access.origin();
    }

    public boolean current() {
        return !closed && session.current();
    }

    public synchronized OutfitEvents outfitEvents(OutfitEvents.Listener listener) {
        if (!current()) {
            throw new CancellationException("LIBRARY_RETIRED");
        }
        if (outfitEvents == null) {
            outfitEvents = new OutfitEvents(access, session, listener);
        }
        return outfitEvents;
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

    public Request<OutfitConfig> currentOutfit() {
        return submit(Lane.CONTROL, exchange -> OutfitCodec.decode(
                exchange.json("GET", ROOT + "outfits/current", null, JsonObject.class).toString()));
    }

    public Request<OutfitObserverSnapshot> observerOutfit(UUID owner) {
        java.util.Objects.requireNonNull(owner);
        return submit(Lane.CONTROL, exchange -> exchange.observerOutfit(owner));
    }

    public Request<OutfitInstanceState> instanceState(UUID owner, UUID instance) {
        return submit(Lane.CONTROL, exchange -> OutfitInstanceState.decode(exchange.json("GET",
                ROOT + "outfits/players/" + owner + "/instances/" + instance + "/state", null, JsonObject.class)));
    }

    public Request<OutfitInstanceState> publishInstanceState(OutfitInstanceState state) {
        return submit(Lane.CONTROL, exchange -> OutfitInstanceState.decode(exchange.json("POST",
                ROOT + "outfits/instances/" + state.instanceId() + "/state", state.encode(), JsonObject.class)));
    }

    public Request<OutfitApplyResult> applyOutfit(UUID operation, OutfitConfig configuration) {
        java.util.Objects.requireNonNull(operation);
        OutfitConfig prepared = OutfitCodec.decode(OutfitCodec.encode(configuration));
        if (prepared.revision() == Long.MAX_VALUE) {
            throw new IllegalArgumentException("OUTFIT_REVISION_EXHAUSTED");
        }
        return submit(Lane.CONTROL, exchange -> {
            LibraryModels.Capabilities capabilities;
            try {
                capabilities = exchange.json("GET", ROOT + "capabilities", null, LibraryModels.Capabilities.class);
            } catch (Failure failure) {
                if (failure.status == 404 || failure.status == 405) {
                    throw new Failure(415, "UNSUPPORTED_FORMAT");
                }
                throw failure;
            }
            var outfits = capabilities.outfitsV1();
            if (!capabilities.enabled() || outfits == null || !outfits.configuration() || !outfits.apply()) {
                throw new Failure(415, "UNSUPPORTED_FORMAT");
            }
            JsonObject body = OutfitApplyResult.request(operation, prepared, outfits.limits());
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            if (encoded.length > OutfitApplyResult.JSON_BYTES) {
                throw new Failure(0, "CONTROL_LIMIT");
            }
            var response = exchange.send("PUT", ROOT + "outfits/current",
                    HttpRequest.BodyPublishers.ofByteArray(encoded), "application/json");
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = CloudHttp.readBounded(input, OutfitApplyResult.JSON_BYTES);
            }
            exchange.requireCurrent();
            if (response.statusCode() != 200 && response.statusCode() != 409) {
                throw responseFailure(response.statusCode(), bytes);
            }
            try {
                String mediaType = response.headers().firstValue("content-type").orElse("").split(";", 2)[0].trim();
                if (!mediaType.equalsIgnoreCase("application/json")) {
                    throw new IllegalArgumentException("INVALID_OUTFIT_APPLY_MEDIA_TYPE");
                }
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                return OutfitApplyResult.decode(response.statusCode(), json,
                        operation, prepared, outfits.limits());
            } catch (RuntimeException | CharacterCodingException malformed) {
                throw new Failure(0, "INVALID_OUTFIT_APPLY_RESPONSE");
            }
        });
    }

    /** Only a successful explicit null means unbound; HTTP failures must never trigger an upload fallback. */
    public Request<java.util.Optional<OutfitPrivateUpload.Binding>> resolveOutfitSource(
            OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind) {
        OutfitPrivateUpload.lookup(reference, kind);
        return submit(Lane.CONTROL, exchange -> resolveOutfitSource(exchange, reference, kind, outfitSourceLimits(exchange)));
    }

    private java.util.Optional<OutfitPrivateUpload.Binding> resolveOutfitSource(
            Exchange exchange, OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind,
            OutfitLimits limits) throws Exception {
        var response = privateUploadJson(exchange, ROOT + "outfits/resources/local/resolve",
                OutfitPrivateUpload.lookup(reference, kind));
        try {
            if (!response.has("resource")) {
                throw new IllegalArgumentException();
            }
            if (response.get("resource").isJsonNull()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(OutfitPrivateUpload.resolved(response.getAsJsonObject("resource"),
                    reference, kind, limits));
        } catch (RuntimeException malformed) {
            throw new Failure(0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
        }
    }

    /** Explicit private preparation only: does not equip, publish a catalog entry or activate a runtime. */
    public Request<OutfitPrivateUpload.Binding> uploadOutfitSource(OutfitPrivateUpload upload,
                                                                 java.util.function.LongConsumer progress) {
        java.util.Objects.requireNonNull(upload);
        java.util.Objects.requireNonNull(progress);
        return submit(Lane.TRANSFER, exchange -> {
            OutfitLimits limits = outfitSourceLimits(exchange);
            JsonObject input = upload.request(limits);
            var existing = resolveOutfitSource(exchange, upload.reference(), upload.resourceKind(), limits);
            if (existing.isPresent() && upload.matches(existing.get())) {
                return existing.get();
            }
            // A different existing package (for example from an older client parser) is replaced by this upload.
            String path = ROOT + "outfits/resources/uploads";
            JsonObject reservation = privateUploadJson(exchange, path, input);
            try {
                upload.checkReservation(reservation);
                if (reservation.get("completed").getAsBoolean()) {
                    return upload.binding(reservation.getAsJsonObject("resource"));
                }
            } catch (RuntimeException malformed) {
                throw new Failure(0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
            }
            exchange.binary("PUT", upload.bodyPath(), CloudHttp.uploadBody(upload.bytes(), progress),
                    "application/octet-stream", 204);
            var receipt = privateUploadJson(exchange, path + "/" + upload.operation() + "/commit", null);
            try {
                return upload.binding(receipt);
            } catch (RuntimeException malformed) {
                throw new Failure(0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
            }
        });
    }

    private OutfitLimits outfitSourceLimits(Exchange exchange) throws Exception {
        LibraryModels.Capabilities capabilities;
        try {
            capabilities = exchange.json("GET", ROOT + "capabilities", null, LibraryModels.Capabilities.class);
        } catch (Failure failure) {
            if (failure.status == 404 || failure.status == 405) {
                throw new Failure(415, "UNSUPPORTED_FORMAT");
            }
            throw failure;
        }
        var outfits = capabilities.outfitsV1();
        if (!capabilities.enabled() || outfits == null || !outfits.configuration() || !outfits.apply()) {
            throw new Failure(415, "UNSUPPORTED_FORMAT");
        }
        return outfits.limits();
    }

    private JsonObject privateUploadJson(Exchange exchange, String path, JsonObject body) throws Exception {
        byte[] encoded = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > CloudHttp.CONTROL_BYTES) {
            throw new Failure(0, "CONTROL_LIMIT");
        }
        var response = exchange.send("POST", path, HttpRequest.BodyPublishers.ofByteArray(encoded), "application/json");
        byte[] bytes;
        try (InputStream input = response.body()) {
            bytes = CloudHttp.readBounded(input, CloudHttp.CONTROL_BYTES);
        }
        exchange.requireCurrent();
        if (response.statusCode() != 200) {
            throw responseFailure(response.statusCode(), bytes);
        }
        try {
            String media = response.headers().firstValue("content-type").orElse("").split(";", 2)[0].trim();
            if (!media.equalsIgnoreCase("application/json")) {
                throw new IllegalArgumentException();
            }
            return JsonParser.parseString(StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(bytes)).toString()).getAsJsonObject();
        } catch (RuntimeException | CharacterCodingException malformed) {
            throw new Failure(0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
        }
    }

    public Request<JsonObject> outfitPresets() {
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", ROOT + "outfit-presets",
                null, JsonObject.class));
    }

    public Request<JsonObject> outfitPreset(UUID id) {
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", ROOT + "outfit-presets/" + id,
                null, JsonObject.class));
    }

    public Request<JsonObject> saveOutfitPreset(UUID operationId, UUID presetId, long expectedRevision,
                                               String name, OutfitConfig config, OutfitLimits limits) {
        var body = new JsonObject();
        body.addProperty("operation_id", operationId.toString());
        body.addProperty("preset_id", presetId.toString());
        body.addProperty("expected_revision", expectedRevision);
        body.addProperty("name", name);
        body.add("configuration", JsonParser.parseString(OutfitCodec.encode(config, limits)));
        return submit(Lane.CONTROL, exchange -> exchange.json("POST", ROOT + "outfit-presets",
                body, JsonObject.class));
    }

    public Request<LibraryModels.Model> detail(UUID model) {
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", ROOT + "models/" + model,
                null, LibraryModels.Model.class));
    }

    public Request<LibraryModels.SessionInfo> sessionInfo() {
        return submit(Lane.CONTROL, exchange -> exchange.json("GET", ROOT + "session",
                null, LibraryModels.SessionInfo.class));
    }

    public Request<LibraryModels.CollectionPage> collection(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("INVALID_COLLECTION_PAGE");
        }
        return submit(Lane.CONTROL, exchange -> exchange.json("GET",
                ROOT + "collection?page_size=64&page=" + page, null, LibraryModels.CollectionPage.class));
    }

    public Request<JsonObject> collectionOutfit(UUID model, boolean inOutfit) {
        return submit(Lane.CONTROL, exchange -> exchange.json("PUT", ROOT + "collection/" + model + "/outfit",
                Map.of("in_outfit", inOutfit), JsonObject.class));
    }

    public Request<JsonObject> collectionRemove(UUID model) {
        return submit(Lane.CONTROL, exchange -> exchange.json("DELETE", ROOT + "collection/" + model,
                null, JsonObject.class));
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
        synchronized (this) {
            if (outfitEvents != null) {
                outfitEvents.close();
            }
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
        private long previewCacheSeconds = 3600;

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

        public java.util.Optional<OutfitPrivateUpload.Binding> resolveOutfitSource(
                OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind) throws Exception {
            return LibraryClient.this.resolveOutfitSource(this, reference, kind, outfitSourceLimits(this));
        }

        public byte[] outfitSourceBody(OutfitPrivateUpload.Binding binding) throws Exception {
            var query = OutfitPrivateUpload.lookup(binding.reference(), binding.resourceKind());
            query.addProperty("raw_sha256", binding.rawSha256());
            var response = send("POST", ROOT + "outfits/resources/local/body",
                    HttpRequest.BodyPublishers.ofString(query.toString()), "application/json");
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw responseFailure(response.statusCode(), CloudHttp.readBounded(input, CloudHttp.CONTROL_BYTES));
                }
                String media = response.headers().firstValue("content-type").orElse("");
                long length = response.headers().firstValueAsLong("content-length").orElse(-1);
                if (!media.equals("application/octet-stream") || length <= 0 || length > CloudHttp.FILE_BYTES
                        || !response.headers().firstValue("x-omnimorph-sha256").orElse("").equals(binding.rawSha256())) {
                    throw new Failure(0, "INVALID_OUTFIT_SOURCE_BODY");
                }
                byte[] bytes = CloudHttp.readBounded(input, (int) length);
                requireCurrent();
                if (bytes.length != length || !CloudHttp.sha256(bytes).equals(binding.rawSha256())) {
                    throw new Failure(0, "DISTRIBUTION_HASH_MISMATCH");
                }
                return bytes;
            }
        }

        public OutfitObserverSnapshot observerOutfit(UUID owner) throws Exception {
            OutfitLimits limits = outfitSourceLimits(this);
            var response = send("GET", ROOT + "outfits/players/" + owner, HttpRequest.BodyPublishers.noBody(), null);
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = CloudHttp.readBounded(input, response.statusCode() == 200
                        ? OutfitObserverSnapshot.JSON_BYTES : CloudHttp.CONTROL_BYTES);
            }
            requireCurrent();
            if (response.statusCode() != 200) {
                throw responseFailure(response.statusCode(), bytes);
            }
            try {
                String media = response.headers().firstValue("content-type").orElse("").split(";", 2)[0].trim();
                if (!media.equalsIgnoreCase("application/json")) {
                    throw new IllegalArgumentException();
                }
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                return OutfitObserverSnapshot.decode(json, owner, origin(), limits);
            } catch (RuntimeException | CharacterCodingException malformed) {
                throw new Failure(0, "INVALID_OUTFIT_OBSERVER_SNAPSHOT");
            }
        }

        public byte[] observerOutfitBody(OutfitObserverSnapshot snapshot, OutfitObserverSnapshot.Resource resource) throws Exception {
            if (!snapshot.resources().contains(resource) || !resource.relativePath().equals(
                    OutfitObserverSnapshot.resourcePath(snapshot.owner(), snapshot.revision(), resource.id()))) {
                throw new Failure(0, "INVALID_OUTFIT_OBSERVER_SOURCE");
            }
            var response = send("GET", resource.relativePath(), HttpRequest.BodyPublishers.noBody(), null);
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw responseFailure(response.statusCode(), CloudHttp.readBounded(input, CloudHttp.CONTROL_BYTES));
                }
                String media = response.headers().firstValue("content-type").orElse("");
                long length = response.headers().firstValueAsLong("content-length").orElse(-1);
                if (!media.equals("application/octet-stream") || length <= 0 || length > CloudHttp.FILE_BYTES
                        || length != resource.byteLength()
                        || !response.headers().firstValue("x-omnimorph-sha256").orElse("").equals(resource.rawSha256())) {
                    throw new Failure(0, "INVALID_OUTFIT_OBSERVER_BODY");
                }
                byte[] bytes = CloudHttp.readBounded(input, (int) length);
                requireCurrent();
                if (bytes.length != length || !CloudHttp.sha256(bytes).equals(resource.rawSha256())) {
                    throw new Failure(0, "DISTRIBUTION_HASH_MISMATCH");
                }
                return bytes;
            }
        }

        public long previewCacheSeconds() {
            return previewCacheSeconds;
        }

        public HttpResponse<InputStream> send(String method, String path, HttpRequest.BodyPublisher body,
                                              String contentType) throws Exception {
            request.requireCurrent();
            var response = call.send(method, path, body, contentType, adminToken);
            if (response.statusCode() == 200) {
                try {
                    long seconds = response.headers().firstValueAsLong("x-omnimorph-preview-cache-seconds")
                            .orElse(3600);
                    if (seconds < 0 || seconds > 0xffffffffL) {
                        throw new NumberFormatException("Invalid preview cache lifetime");
                    }
                    previewCacheSeconds = seconds;
                } catch (NumberFormatException invalid) {
                    response.body().close();
                    throw new Failure(0, "INVALID_CACHE_LIFETIME");
                }
            }
            return response;
        }

        public <T> T json(String method, String path, Object body, Class<T> type) throws Exception {
            byte[] encoded = body == null ? new byte[0] : JSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            if (encoded.length > CloudHttp.CONTROL_BYTES) {
                throw new Failure(0, "CONTROL_LIMIT");
            }
            var response = send(method, path, HttpRequest.BodyPublishers.ofByteArray(encoded), "application/json");
            return readJson(response, type);
        }

        public <T> T binaryJson(String method, String path, byte[] bytes, String contentType,
                                Map<String, String> headers, Class<T> type) throws Exception {
            request.requireCurrent();
            var response = call.send(method, path, HttpRequest.BodyPublishers.ofByteArray(bytes), contentType,
                    adminToken, headers);
            return readJson(response, type);
        }

        public void binary(String method, String path, HttpRequest.BodyPublisher body,
                           String contentType, int expectedStatus) throws Exception {
            var response = send(method, path, body, contentType);
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = CloudHttp.readBounded(input, CloudHttp.CONTROL_BYTES);
            }
            request.requireCurrent();
            if (response.statusCode() != expectedStatus) {
                throw responseFailure(response.statusCode(), bytes);
            }
        }

        private <T> T readJson(HttpResponse<InputStream> response, Class<T> type) throws Exception {
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
