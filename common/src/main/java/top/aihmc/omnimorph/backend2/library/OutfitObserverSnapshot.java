package top.aihmc.omnimorph.backend2.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authorized render projection only: these sources do not confer catalog acquisition or inventory rights. */
public record OutfitObserverSnapshot(UUID owner, long revision, long equipmentRevision,
                                     OutfitConfig configuration, List<Resource> resources, String distributionCode) {
    public OutfitObserverSnapshot(UUID owner, long revision, long equipmentRevision,
                                  OutfitConfig configuration, List<Resource> resources) {
        this(owner, revision, equipmentRevision, configuration, resources, null);
    }
    private record SourceKey(OutfitConfig.ResourceKind kind, OutfitConfig.ResourceRef reference) {}
    public static final int JSON_BYTES = OutfitConfig.MAX_JSON_BYTES + 8192
            + 32 * (OutfitConfig.MAX_JSON_BYTES + 4096);

    public record Resource(String id, OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind,
                           CosmeticMetadata accessoryMetadata, int distributionFormat, long byteLength,
                           String rawSha256, String contentSha256, String schemaSha256, String relativePath) {
        public boolean sameSource(Resource other) {
            return other != null && id.equals(other.id) && reference.equals(other.reference) && kind == other.kind
                    && distributionFormat == other.distributionFormat && byteLength == other.byteLength
                    && rawSha256.equals(other.rawSha256) && contentSha256.equals(other.contentSha256)
                    && schemaSha256.equals(other.schemaSha256) && relativePath.equals(other.relativePath)
                    && Objects.equals(accessoryMetadata == null ? null : OutfitCodec.encodeAccessoryMetadata(accessoryMetadata),
                    other.accessoryMetadata == null ? null : OutfitCodec.encodeAccessoryMetadata(other.accessoryMetadata));
        }
    }

    public OutfitObserverSnapshot {
        Objects.requireNonNull(owner);
        require(revision >= 0 && equipmentRevision >= 0);
        resources = List.copyOf(resources);
    }

    /** Modern writes advance revision; a legacy account must still use its original profile base. */
    public boolean legacyFallback() {
        return revision == 0;
    }

    public boolean sameProjection(OutfitObserverSnapshot other) {
        return other != null && owner.equals(other.owner) && revision == other.revision
                && Objects.equals(distributionCode, other.distributionCode)
                && equipmentRevision == other.equipmentRevision && Objects.equals(configuration, other.configuration)
                && resources.size() == other.resources.size() && resources.stream().allMatch(resource ->
                other.resources.stream().anyMatch(resource::sameSource));
    }

    public static String resourcePath(UUID owner, long revision, String id) {
        require(revision >= 0 && id != null && id.matches("[a-f0-9]{64}"));
        return "/api/library/v1/outfits/players/" + owner + "/resources/" + id + "?revision=" + revision;
    }

    static OutfitObserverSnapshot decode(String json, UUID requestedOwner, String origin, OutfitLimits limits) {
        require(json.getBytes(StandardCharsets.UTF_8).length <= JSON_BYTES);
        JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        require(integer(body, "schema_version") == 1);
        String ownerText = text(body, "owner");
        UUID owner = UUID.fromString(ownerText);
        require(owner.toString().equals(ownerText) && owner.equals(requestedOwner));
        long revision = integer(body, "revision");
        long equipment = integer(body, "equipment_revision");
        require(body.has("configuration") && body.has("resources") && body.get("resources").isJsonArray());
        var configuration = body.get("configuration").isJsonNull() ? null
                : OutfitCodec.decode(body.getAsJsonObject("configuration").toString(), limits);
        require(configuration == null || configuration.revision() == revision
                && integer(body.getAsJsonObject("configuration"), "revision") == revision);
        var encoded = body.getAsJsonArray("resources");
        require(encoded.size() <= limits.maxInstances() + 1 && (configuration != null || encoded.isEmpty()));
        var resources = new ArrayList<Resource>();
        var ids = new HashSet<String>();
        var references = new HashSet<SourceKey>();
        for (var value : encoded) {
            JsonObject item = value.getAsJsonObject();
            String id = digest(item, "id");
            require(ids.add(id));
            // Reuse the existing tagged resource decoder rather than introducing a second reference format.
            JsonObject wrapper = JsonParser.parseString(OutfitCodec.encode(OutfitConfig.empty())).getAsJsonObject();
            wrapper.add("base", item.getAsJsonObject("reference"));
            var reference = OutfitCodec.decode(wrapper.toString()).base();
            var kind = OutfitConfig.ResourceKind.valueOf(text(item, "resource_kind"));
            require(reference != null && kind != OutfitConfig.ResourceKind.OUTFIT
                    && references.add(new SourceKey(kind, reference)));
            require(reference.source() == OutfitConfig.Source.LOCAL || reference.origin().equals(origin));
            long format = integer(item, "distribution_format");
            require(format == (reference.format() == OutfitConfig.Format.FIGURA ? 1 : 2));
            long length = integer(item, "byte_length");
            require(length > 0 && length <= CloudHttp.FILE_BYTES && item.has("accessory_metadata"));
            CosmeticMetadata metadata = null;
            if (kind == OutfitConfig.ResourceKind.ACCESSORY) {
                require(integer(item.getAsJsonObject("accessory_metadata"), "schema_version") == 1
                        && text(item.getAsJsonObject("accessory_metadata"), "validation_source").equals("CLIENT_EXTRACTED"));
                String declaration = item.getAsJsonObject("accessory_metadata").toString();
                require(declaration.getBytes(StandardCharsets.UTF_8).length <= 16384);
                metadata = CosmeticMetadata.read(declaration);
                metadata.validateLimits(limits);
            } else {
                require(item.get("accessory_metadata").isJsonNull());
            }
            String path = text(item, "relative_path");
            require(path.equals(resourcePath(owner, revision, id)));
            resources.add(new Resource(id, reference, kind, metadata, (int) format, length,
                    digest(item, "raw_sha256"), digest(item, "content_sha256"), digest(item, "schema_sha256"), path));
        }
        if (configuration != null) {
            var expected = new HashSet<SourceKey>();
            if (configuration.base() != null) {
                expected.add(new SourceKey(OutfitConfig.ResourceKind.AVATAR, configuration.base()));
                require(resources.stream().anyMatch(resource -> resource.kind() == OutfitConfig.ResourceKind.AVATAR
                        && resource.reference().equals(configuration.base())));
            }
            for (var instance : configuration.instances()) {
                require(instance.enabled());
                expected.add(new SourceKey(OutfitConfig.ResourceKind.ACCESSORY, instance.resource()));
                require(resources.stream().anyMatch(resource -> resource.kind() == OutfitConfig.ResourceKind.ACCESSORY
                        && resource.reference().equals(instance.resource())));
            }
            require(expected.equals(references));
        }
        String distributionCode = body.has("distribution_code") ? text(body, "distribution_code") : null;
        require(distributionCode == null || distributionCode.equals("NOT_DISTRIBUTABLE"));
        return new OutfitObserverSnapshot(owner, revision, equipment, configuration, resources, distributionCode);
    }

    private static long integer(JsonObject body, String field) {
        var value = body.getAsJsonPrimitive(field);
        require(value != null && value.isNumber());
        long result = value.getAsBigDecimal().longValueExact();
        require(result >= 0);
        return result;
    }

    private static String text(JsonObject body, String field) {
        var value = body.getAsJsonPrimitive(field);
        require(value != null && value.isString());
        return value.getAsString();
    }

    private static String digest(JsonObject body, String field) {
        String value = text(body, field);
        require(value.matches("[a-f0-9]{64}"));
        return value;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("INVALID_OUTFIT_OBSERVER_SNAPSHOT");
        }
    }
}
