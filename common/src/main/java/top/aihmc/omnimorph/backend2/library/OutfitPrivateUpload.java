package top.aihmc.omnimorph.backend2.library;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Frozen native distribution and private purpose. Retain this object for same-operation retries. */
public final class OutfitPrivateUpload {
    public record Binding(OutfitConfig.ResourceRef reference, String assetId, long assetRevision,
                          String rawSha256, OutfitConfig.ResourceKind resourceKind,
                          CosmeticMetadata accessoryMetadata) {
        public boolean sameSource(Binding other) {
            return other != null && reference.equals(other.reference) && assetId.equals(other.assetId)
                    && assetRevision == other.assetRevision && rawSha256.equals(other.rawSha256)
                    && resourceKind == other.resourceKind
                    && declaration(accessoryMetadata).equals(declaration(other.accessoryMetadata));
        }
    }

    private final UUID operation;
    private final OutfitConfig.ResourceRef reference;
    private final OutfitConfig.ResourceKind kind;
    private final CosmeticMetadata metadata;
    private final byte[] bytes;
    private final String rawHash;
    private final String contentHash;
    private final String schemaHash;

    public OutfitPrivateUpload(UUID operation, OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind,
                               CosmeticMetadata metadata, byte[] bytes, String contentHash, String schemaHash) {
        this.operation = Objects.requireNonNull(operation);
        this.reference = Objects.requireNonNull(reference);
        this.kind = Objects.requireNonNull(kind);
        require(reference.source() == OutfitConfig.Source.LOCAL && kind != OutfitConfig.ResourceKind.OUTFIT);
        require((kind == OutfitConfig.ResourceKind.ACCESSORY) == (metadata != null));
        require(bytes.length > 0 && bytes.length <= CloudHttp.FILE_BYTES);
        this.metadata = metadata;
        this.bytes = bytes.clone();
        this.rawHash = CloudHttp.sha256(this.bytes);
        this.contentHash = digest(contentHash);
        this.schemaHash = digest(schemaHash);
        require(reference.format() == OutfitConfig.Format.FIGURA
                || this.contentHash != null && this.schemaHash != null);
    }

    public UUID operation() {
        return operation;
    }

    public OutfitConfig.ResourceRef reference() {
        return reference;
    }

    public OutfitConfig.ResourceKind resourceKind() {
        return kind;
    }

    public String rawSha256() {
        return rawHash;
    }

    public int byteLength() {
        return bytes.length;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String bodyPath() {
        return "/api/library/v1/outfits/resources/uploads/" + operation + "/body";
    }

    public JsonObject request(OutfitLimits limits) {
        if (metadata != null) {
            metadata.validateLimits(limits);
        }
        var body = new JsonObject();
        body.addProperty("operation_id", operation.toString());
        body.add("reference", referenceJson());
        body.addProperty("resource_kind", kind.name());
        body.add("accessory_metadata", declaration());
        body.addProperty("raw_sha256", rawHash);
        body.addProperty("byte_length", bytes.length);
        nullable(body, "content_sha256", contentHash);
        nullable(body, "schema_sha256", schemaHash);
        require(body.toString().getBytes(StandardCharsets.UTF_8).length <= CloudHttp.CONTROL_BYTES);
        return body;
    }

    /** Check the receipt against the frozen input, including native identity and author declaration. */
    public Binding binding(JsonObject body) {
        require(referenceJson().equals(body.getAsJsonObject("reference")));
        require(kind.name().equals(text(body, "resource_kind")));
        require(rawHash.equals(text(body, "raw_sha256")));
        require(declaration().equals(body.get("accessory_metadata")));
        String asset = "outfit_" + operation.toString().replace("-", "");
        require(asset.equals(text(body, "asset_id")) && integer(body, "asset_revision") == 1);
        return new Binding(reference, asset, 1, rawHash, kind, metadata);
    }

    public static JsonObject lookup(OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind) {
        require(reference.source() == OutfitConfig.Source.LOCAL && kind != OutfitConfig.ResourceKind.OUTFIT);
        var body = new JsonObject();
        body.add("reference", referenceJson(reference));
        body.addProperty("resource_kind", kind.name());
        return body;
    }

    static Binding resolved(JsonObject body, OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind,
                            OutfitLimits limits) {
        require(referenceJson(reference).equals(body.getAsJsonObject("reference")));
        require(kind.name().equals(text(body, "resource_kind")));
        String asset = text(body, "asset_id");
        require(!asset.isBlank() && asset.length() <= 128);
        long revision = integer(body, "asset_revision");
        require(revision > 0);
        String raw = digest(text(body, "raw_sha256"));
        require(raw != null && body.has("accessory_metadata"));
        CosmeticMetadata metadata = null;
        if (kind == OutfitConfig.ResourceKind.ACCESSORY) {
            var declaration = body.getAsJsonObject("accessory_metadata");
            require(integer(declaration, "schema_version") == 1
                    && text(declaration, "validation_source").equals("CLIENT_EXTRACTED"));
            require(declaration.toString().getBytes(StandardCharsets.UTF_8).length <= 16384);
            metadata = CosmeticMetadata.read(declaration.toString());
            metadata.validateLimits(limits);
        } else {
            require(body.get("accessory_metadata").isJsonNull());
        }
        return new Binding(reference, asset, revision, raw, kind, metadata);
    }

    boolean matches(Binding binding) {
        return reference.equals(binding.reference()) && kind == binding.resourceKind()
                && rawHash.equals(binding.rawSha256()) && declaration().equals(declaration(binding.accessoryMetadata()));
    }

    void checkReservation(JsonObject body) {
        require(operation.toString().equals(text(body, "operation_id")));
        require(body.getAsJsonPrimitive("completed").isBoolean());
        if (!body.get("completed").getAsBoolean()) {
            require(bodyPath().equals(text(body, "relative_path")));
            require(integer(body, "max_bytes") >= bytes.length && integer(body, "expires_at") > 0);
        }
    }

    private JsonObject referenceJson() {
        return referenceJson(reference);
    }

    private static JsonObject referenceJson(OutfitConfig.ResourceRef reference) {
        var value = new JsonObject();
        value.addProperty("source", "LOCAL");
        value.addProperty("local_id", reference.localId().toString());
        value.addProperty("content_hash", reference.contentHash());
        value.addProperty("format", reference.format().name());
        return value;
    }

    private com.google.gson.JsonElement declaration() {
        return declaration(metadata);
    }

    private static com.google.gson.JsonElement declaration(CosmeticMetadata metadata) {
        if (metadata == null) {
            return JsonNull.INSTANCE;
        }
        var value = OutfitCodec.encodeAccessoryMetadata(metadata);
        // Rust's BoneRef has a nullable instance owner; only author resource bones belong here.
        for (String field : java.util.List.of("exported_bones", "editable_bones")) {
            for (var bone : value.getAsJsonArray(field)) {
                bone.getAsJsonObject().add("instance_id", JsonNull.INSTANCE);
            }
        }
        require(value.toString().getBytes(StandardCharsets.UTF_8).length <= 16384);
        return value;
    }

    private static String digest(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        require(value.matches("[0-9a-f]{64}"));
        return value;
    }

    private static void nullable(JsonObject object, String field, String value) {
        object.add(field, value == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(value));
    }

    private static String text(JsonObject object, String field) {
        var value = object.getAsJsonPrimitive(field);
        require(value.isString());
        return value.getAsString();
    }

    private static long integer(JsonObject object, String field) {
        var value = object.getAsJsonPrimitive(field);
        require(value.isNumber());
        long result = value.getAsBigDecimal().longValueExact();
        require(result >= 0);
        return result;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("INVALID_OUTFIT_PRIVATE_UPLOAD");
        }
    }
}
