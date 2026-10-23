package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** JSON configuration preparation, independent of Figura/YSM model distribution. */
public final class OutfitUploadDraft {
    public record Prepared(String name, OutfitConfig configuration) {}

    public record Snapshot(OutfitConfig configuration, Map<String, Object> metadata) {
        public Snapshot {
            metadata = Map.copyOf(metadata);
        }

        public void validate(LibraryModels.Capabilities capabilities, String origin) {
            if (!capabilities.enabled() || capabilities.outfitsV1() == null
                    || !capabilities.outfitsV1().publication()
                    || !capabilities.categories().contains(metadata.get("category_id"))) {
                throw new IllegalArgumentException("OUTFIT_PUBLICATION_UNAVAILABLE");
            }
            OutfitCodec.encode(configuration, capabilities.outfitsV1().limits());
            if (configuration.base() != null) {
                cloudDependency(configuration.base(), origin);
            }
            for (var instance : configuration.instances()) {
                cloudDependency(instance.resource(), origin);
            }
        }

        public JsonObject input(java.util.UUID operation, OutfitLimits limits) {
            var value = new JsonObject();
            value.addProperty("operation_id", operation.toString());
            value.add("metadata", new com.google.gson.Gson().toJsonTree(metadata));
            value.add("configuration", JsonParser.parseString(OutfitCodec.encode(configuration, limits)));
            return value;
        }
    }

    private OutfitUploadDraft() {}

    public static Prepared prepare(Path source, OutfitLimits limits) throws IOException {
        try (var stream = Files.newInputStream(source)) {
            byte[] bytes = CloudHttp.readBounded(stream, OutfitConfig.MAX_JSON_BYTES + 2048);
            var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            var configuration = root.has("configuration") ? root.get("configuration") : root;
            String filename = source.getFileName().toString();
            String name = root.has("configuration") && root.has("name") ? root.get("name").getAsString()
                    : filename.substring(0, filename.length() - 5);
            LibraryUploadDraft.validateEditable(name, "", List.of());
            return new Prepared(name, OutfitCodec.decode(configuration.toString(), limits));
        } catch (RuntimeException invalid) {
            throw new IOException("INVALID_OUTFIT_CONFIGURATION", invalid);
        }
    }

    public static Snapshot snapshot(Prepared source, String name, String description, String category,
                                    List<String> tags, boolean visible, boolean authorization,
                                    LibraryModels.Capabilities capabilities, String origin) {
        LibraryUploadDraft.validateEditable(name, description, tags);
        var metadata = Map.<String, Object>of("resource_kind", "OUTFIT", "display_name", name,
                "internal_name", source.name(), "authors", List.of(), "description", description,
                "category_id", category, "tags", List.copyOf(tags), "visibility", visible ? "PUBLIC" : "PRIVATE",
                "requires_authorization", authorization);
        var snapshot = new Snapshot(source.configuration(), metadata);
        snapshot.validate(capabilities, origin);
        return snapshot;
    }

    private static void cloudDependency(OutfitConfig.ResourceRef reference, String origin) {
        if (reference.source() != OutfitConfig.Source.CLOUD || !origin.equals(reference.origin())) {
            throw new IllegalArgumentException("OUTFIT_DEPENDENCY_NOT_DISTRIBUTABLE");
        }
    }
}
