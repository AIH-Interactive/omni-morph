package top.aihmc.omnimorph.wizards;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Creates only a local accessory package, publishing its directory after native source parsing. */
public final class CosmeticCreator {
    private static final String RESOURCES = "/assets/omni-morph/cosmetic_templates/";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private CosmeticCreator() {
    }

    public static Path create(Path root, String name, String authors, String description,
                              CosmeticTemplate template, String anchor, OutfitConfig.Transform transform)
            throws Exception {
        String filename = YsmAvatarCreator.directoryName(name);
        Path directory = root.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path staging = Files.createTempDirectory(directory.getParent(), ".omnimorph-cosmetic-");
        var created = new ArrayList<Path>();
        created.add(staging);
        try {
            byte[] baseTexture = resource("textures/cosmetic_base.png");
            var model = template.model(Base64.getEncoder().encodeToString(baseTexture));
            Files.createDirectory(staging.resolve("textures"));
            created.add(staging.resolve("textures"));
            if (template == CosmeticTemplate.TRAIL) {
                for (String texture : List.of("sakura_petal", "twinkle_star_sheet")) {
                    byte[] bytes = resource("textures/" + texture + ".png");
                    write(staging.resolve("textures/" + texture + ".png"), bytes, created);
                    model.addImage(texture, Base64.getEncoder().encodeToString(bytes),
                            texture.equals("sakura_petal") ? 32 : 128,
                            texture.equals("sakura_petal") ? 32 : 64);
                }
            } else {
                write(staging.resolve("textures/cosmetic_base.png"), baseTexture, created);
            }
            write(staging.resolve("model.bbmodel"), JSON.toJson(model.build()).getBytes(StandardCharsets.UTF_8), created);
            String script = new String(resource(template == CosmeticTemplate.TRAIL ? "trail.lua" : "geometry.lua"),
                    StandardCharsets.UTF_8).replace("@CATEGORY@", template.category());
            write(staging.resolve("script.lua"), script.getBytes(StandardCharsets.UTF_8), created);

            JsonObject metadata = metadata(name, authors, description, template, anchor, transform);
            Path manifest = staging.resolve("cosmetic.json");
            write(manifest, JSON.toJson(metadata).getBytes(StandardCharsets.UTF_8), created);
            CompoundTag prepared = LocalAvatarLoader.prepareCosmeticAvatar(staging, UUID.randomUUID());
            CompoundTag rootModel = prepared.getCompoundOrEmpty("models");
            var binding = SkeletonIndex.capture(null, List.of(rootModel), tag -> tag.getStringOr("name", ""),
                    tag -> tag.getListOrEmpty("chld").stream().map(CompoundTag.class::cast).toList(),
                    OutfitLimits.defaults());
            var skeleton = binding.definition();
            metadata.addProperty("has_bones", true);
            metadata.addProperty("display_bones", true);
            metadata.addProperty("edit_bones", true);
            metadata.addProperty("skeleton_revision", "template-1");
            metadata.addProperty("skeleton_hash", skeleton.hash());
            var editable = new JsonArray();
            var exported = new JsonArray();
            for (int index = 0; index < skeleton.nodes().size(); index++) {
                editable.add(JSON.toJsonTree(skeleton.reference(null, index)));
                if (skeleton.nodes().get(index).name().equals("tailtip")) {
                    exported.add(JSON.toJsonTree(skeleton.reference(null, index)));
                }
            }
            metadata.add("editable_bones", editable);
            metadata.add("exported_bones", exported);
            String json = JSON.toJson(metadata);
            CosmeticMetadata.read(json);
            Files.writeString(manifest, json);

            for (int suffix = 0; ; suffix++) {
                Path target = directory.resolve(filename + (suffix == 0 ? "" : "_" + suffix));
                try {
                    return Files.move(staging, target);
                } catch (FileAlreadyExistsException conflict) {
                    // Keep the validated package private and try the next unused final name.
                }
            }
        } catch (Exception failure) {
            for (int index = created.size() - 1; index >= 0; index--) {
                try {
                    Files.deleteIfExists(created.get(index));
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    private static JsonObject metadata(String name, String authors, String description,
                                       CosmeticTemplate template, String anchor, OutfitConfig.Transform transform) {
        var metadata = new JsonObject();
        metadata.addProperty("schema_version", 1);
        metadata.addProperty("name", name.strip());
        metadata.addProperty("description", description == null ? "" : description);
        metadata.add("authors", JSON.toJsonTree(authors == null ? List.of()
                : Arrays.stream(authors.split(",")).map(String::strip).filter(value -> !value.isEmpty()).toList()));
        metadata.addProperty("category", template.category());
        metadata.addProperty("cosmetic_anchor", anchor);
        metadata.add("supported_bases", JSON.toJsonTree(List.of("VANILLA", "FIGURA", "YSM")));
        var local = new JsonObject();
        local.add("position", vector(transform.position()));
        local.add("rotation", vector(transform.rotation()));
        local.add("scale", vector(transform.scale()));
        metadata.add("default_transform", local);
        return metadata;
    }

    private static JsonArray vector(OutfitConfig.Vector value) {
        var array = new JsonArray();
        array.add(value.x());
        array.add(value.y());
        array.add(value.z());
        return array;
    }

    private static byte[] resource(String relative) throws IOException {
        try (var stream = CosmeticCreator.class.getResourceAsStream(RESOURCES + relative)) {
            if (stream == null) {
                throw new IOException("COSMETIC_TEMPLATE_RESOURCE_MISSING: " + relative);
            }
            return stream.readAllBytes();
        }
    }

    private static void write(Path path, byte[] bytes, List<Path> created) throws IOException {
        Files.createFile(path);
        created.add(path);
        Files.write(path, bytes);
    }
}
