package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** One immutable upload attempt with stable reservation/image/commit identities across explicit retries. */
public final class LibraryUploadService implements AutoCloseable {
    private static final String ROOT = "/api/library/v1/";
    private final LibraryClient client;
    private Operation active;

    public LibraryUploadService(LibraryClient client) {
        this.client = client;
    }

    public record Image(byte[] bytes, String contentType) {
        public Image {
            if (bytes.length < 1 || bytes.length > 2 * 1024 * 1024
                    || !java.util.Set.of("image/png", "image/jpeg").contains(contentType)) {
                throw new IllegalArgumentException("INVALID_IMAGE");
            }
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        public int byteLength() {
            return bytes.length;
        }
    }

    public record Grant(UUID modelId, UUID operationId, String relativePath, long expiresAt,
                        long maxBytes, long policyRevision) {
        public Grant {
            if (modelId == null || operationId == null || expiresAt <= 0 || maxBytes <= 0 || policyRevision < 1
                    || !(ROOT + "uploads/" + operationId + "/body").equals(relativePath)) {
                throw new IllegalArgumentException("INVALID_UPLOAD_GRANT");
            }
        }
    }

    public record Published(UUID modelId, boolean published) {}
    public record ImageResult(UUID modelId, long imageRevision, long policyRevision) {}

    public Operation upload(LibraryUploadDraft.Snapshot draft, Image image) {
        if (active != null) {
            active.close();
        }
        active = new Operation(draft, image);
        active.start();
        return active;
    }

    @Override
    public void close() {
        if (active != null) {
            active.close();
        }
    }

    public final class Operation implements AutoCloseable {
        private final LibraryUploadDraft.Snapshot draft;
        private final Image image;
        private final UUID operation = UUID.randomUUID();
        private final UUID commitOperation = UUID.randomUUID();
        private final UUID imageOperation = UUID.randomUUID();
        private final UUID cancelOperation = UUID.randomUUID();
        private final AvatarLoadProgress progress = new AvatarLoadProgress();
        private volatile boolean closed;
        private volatile long attempt;
        private volatile boolean commitAttempted;
        private Grant grant;
        private long imageExpectedRevision;
        private long commitExpectedRevision;
        private LibraryUploadIdentity identity;
        private int imageWidth;
        private int imageHeight;
        private LibraryClient.Request<Published> request;
        private CompletableFuture<?> preparing;
        private CompletableFuture<Published> future;
        private long progressGeneration;

        private Operation(LibraryUploadDraft.Snapshot draft, Image image) {
            this.draft = draft;
            this.image = image;
        }

        public CompletableFuture<Published> future() {
            return future;
        }

        public AvatarLoadProgress.Snapshot progress() {
            return progress.snapshot();
        }

        public UUID operationId() {
            return operation;
        }

        public CompletableFuture<Published> retry() {
            if (closed || !future.isCompletedExceptionally()) {
                throw new IllegalStateException("UPLOAD_RETRY_UNAVAILABLE");
            }
            start();
            return future;
        }

        private boolean current(long generation) {
            return !closed && generation == attempt && client.current();
        }

        private void start() {
            long generation = ++attempt;
            future = new CompletableFuture<>();
            progressGeneration = progress.begin(AvatarLoadProgress.Stage.PREPARING);
            CompletableFuture<Published> completion = future;
            completion.whenComplete((result, failure) -> {
                if (completion.isCancelled() && generation == attempt) {
                    close();
                }
            });
            if (identity != null) {
                send(generation);
                return;
            }
            var preparation = AvatarLoadExecutor.submit(() -> {
                try {
                    if (image != null) {
                        var pixels = LibraryThumbnailService.decode(image.bytes);
                        imageWidth = pixels.sourceWidth();
                        imageHeight = pixels.sourceHeight();
                    }
                    return LibraryUploadIdentity.prepare(draft.prepared(), () -> !current(generation));
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            });
            preparing = preparation;
            preparation.whenComplete((prepared, failure) -> Minecraft.getInstance().execute(() -> {
                if (!current(generation)) {
                    completion.cancel(false);
                } else if (failure != null) {
                    progress.finish(progressGeneration);
                    completion.completeExceptionally(new LibraryClient.Failure(0, "UPLOAD_PREPARATION_FAILED"));
                } else {
                    identity = prepared;
                    send(generation);
                }
            }));
        }

        private void send(long generation) {
            long progressId = progressGeneration;
            request = client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
                var limits = exchange.json("GET", ROOT + "capabilities", null, LibraryModels.Capabilities.class);
                if (!limits.enabled() || draft.prepared().byteLength() > limits.maxUploadBytes()
                        || !limits.categories().contains(draft.metadata().get("category_id"))
                        || !limits.modelTypes().contains(draft.metadata().get("model_type"))
                        || image != null && (image.byteLength() > limits.maxImageBytes()
                        || imageWidth > limits.maxImageWidth() || imageHeight > limits.maxImageHeight())) {
                    throw new LibraryClient.Failure(413, "UPLOAD_LIMIT");
                }
                if (commitAttempted && grant != null) {
                    try {
                        return commit(exchange);
                    } catch (LibraryClient.Failure failure) {
                        if (failure.status != 404 && failure.status != 401) {
                            throw failure;
                        }
                        // A server restart can lose the temporary file grant. Reserve the same draft again.
                    }
                }
                var input = new HashMap<String, Object>();
                input.put("operation_id", operation.toString());
                input.put("metadata", draft.metadata());
                input.put("raw_sha256", draft.prepared().sha256());
                input.put("byte_length", draft.prepared().byteLength());
                input.put("source_filename", draft.source().getFileName().toString());
                input.put("metadata_source", draft.metadataSource());
                if (!identity.contentHash().isEmpty()) {
                    input.put("content_sha256", identity.contentHash());
                    input.put("schema_sha256", identity.schemaHash());
                }
                progress.stage(progressGeneration, AvatarLoadProgress.Stage.PROFILE);
                Grant reservation = exchange.json("POST", ROOT + "uploads", input, Grant.class);
                if (!reservation.operationId().equals(operation) || reservation.maxBytes() < draft.prepared().byteLength()
                        || grant != null && !grant.modelId().equals(reservation.modelId())) {
                    throw new LibraryClient.Failure(0, "INVALID_UPLOAD_GRANT");
                }
                if (grant == null) {
                    imageExpectedRevision = reservation.policyRevision();
                    commitExpectedRevision = reservation.policyRevision();
                }
                grant = reservation;
                byte[] bytes = draft.prepared().compressed();
                progress.stage(progressGeneration, AvatarLoadProgress.Stage.UPLOADING);
                try {
                    exchange.binary("PUT", grant.relativePath(), CloudHttp.uploadBody(bytes,
                            sent -> {
                                if (current(generation)) {
                                    progress.uploaded(progressId, sent, bytes.length);
                                }
                            }),
                            "application/octet-stream", 204);
                } catch (LibraryClient.Failure failure) {
                    if (failure.status != 409) {
                        throw failure;
                    }
                    // The body acknowledgement may have been lost; commit still verifies the exact immutable hash.
                }
                if (image != null) {
                    var result = exchange.binaryJson("PUT", ROOT + "models/" + grant.modelId() + "/image",
                            image.bytes, image.contentType(), Map.of("x-omnimorph-operation", imageOperation.toString(),
                                    "x-omnimorph-expected-revision", Long.toString(imageExpectedRevision)), ImageResult.class);
                    if (!grant.modelId().equals(result.modelId()) || result.policyRevision() != imageExpectedRevision + 1
                            || result.imageRevision() != result.policyRevision()) {
                        throw new LibraryClient.Failure(409, "IMAGE_CHANGED");
                    }
                    commitExpectedRevision = result.policyRevision();
                }
                return commit(exchange);
            });
            var completion = future;
            request.future().whenComplete((published, failure) -> {
                if (generation != attempt) {
                    return;
                }
                progress.finish(progressGeneration);
                if (failure != null) {
                    completion.completeExceptionally(failure);
                } else {
                    completion.complete(published);
                }
            });
        }

        private Published commit(LibraryClient.Exchange exchange) throws Exception {
            exchange.requireCurrent();
            progress.stage(progressGeneration, AvatarLoadProgress.Stage.APPLYING);
            commitAttempted = true;
            var result = exchange.json("POST", ROOT + "uploads/" + operation + "/commit",
                    Map.of("operation_id", commitOperation.toString(), "expected_revision", commitExpectedRevision),
                    Published.class);
            if (!result.published() || !grant.modelId().equals(result.modelId())) {
                throw new LibraryClient.Failure(0, "INVALID_UPLOAD_COMMIT");
            }
            return result;
        }

        public CompletableFuture<?> cancel() {
            if (closed) {
                return CompletableFuture.completedFuture(null);
            }
            boolean completed = future != null && !future.isCompletedExceptionally() && future.isDone();
            closed = true;
            attempt++;
            if (preparing != null) {
                preparing.cancel(false);
            }
            if (request != null) {
                request.cancel();
            }
            if (future != null) {
                future.cancel(false);
            }
            progress.clear();
            if (completed || !client.current()) {
                return CompletableFuture.completedFuture(null);
            }
            return client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("DELETE",
                    ROOT + "uploads/" + operation, Map.of("operation_id", cancelOperation.toString()), Map.class)).future();
        }

        @Override
        public void close() {
            cancel();
        }
    }
}
