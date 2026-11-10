package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonObject;
import top.aihmc.omnimorph.backend2.library.LibraryClient;

import java.util.Map;
import java.util.UUID;

/** Keeps metadata and cover mutation identities stable across explicit save retries. */
public final class LibraryModelEditOperation {
    private final UUID modelId;
    private final Map<String, Object> metadata;
    private final LibraryUploadService.Image image;
    private final boolean clearImage;
    private final UUID imageOperation = UUID.randomUUID();
    private Long metadataRevision;

    public LibraryModelEditOperation(UUID modelId, Map<String, Object> metadata,
                                     LibraryUploadService.Image image, boolean clearImage) {
        this.modelId = modelId;
        this.metadata = Map.copyOf(metadata);
        this.image = image;
        this.clearImage = clearImage;
    }

    public void execute(LibraryClient.Exchange exchange) throws Exception {
        String path = "/api/library/v1/models/" + modelId;
        if (metadataRevision == null) {
            var result = exchange.json("PATCH", path, metadata, JsonObject.class);
            long expected = ((Number) metadata.get("expected_revision")).longValue() + 1;
            checkResult(result, expected);
            metadataRevision = expected;
        }
        if (image != null) {
            var result = exchange.binaryJson("PUT", path + "/image", image.bytes(), image.contentType(),
                    Map.of("x-omnimorph-operation", imageOperation.toString(),
                            "x-omnimorph-expected-revision", metadataRevision.toString()), JsonObject.class);
            checkResult(result, metadataRevision + 1);
        } else if (clearImage) {
            var result = exchange.json("DELETE", path + "/image",
                    Map.of("operation_id", imageOperation.toString(), "expected_revision", metadataRevision),
                    JsonObject.class);
            checkResult(result, metadataRevision + 1);
        }
    }

    private void checkResult(JsonObject result, long expectedRevision) throws LibraryClient.Failure {
        try {
            if (modelId.toString().equals(result.get("model_id").getAsString())
                    && result.get("policy_revision").getAsLong() == expectedRevision) {
                return;
            }
        } catch (RuntimeException malformed) {
            throw new LibraryClient.Failure(0, "INVALID_LIBRARY_RESPONSE");
        }
        throw new LibraryClient.Failure(0, "INVALID_LIBRARY_RESPONSE");
    }
}
