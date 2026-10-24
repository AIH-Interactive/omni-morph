package top.aihmc.omnimorph.model.outfit;

import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Native source preparation; no extraction, changes to user files, or wardrobe publication. */
public final class LocalCosmeticSource {
    public record Description(CosmeticMetadata metadata, String contentHash, OutfitConfig.Format format) {}

    public record Prepared(Description description, CompoundTag nbt) {
        public Prepared {
            nbt = nbt.copy();
        }

        @Override
        public CompoundTag nbt() {
            return nbt.copy();
        }
    }

    private LocalCosmeticSource() {
    }

    public static Description describe(Path path, BooleanSupplier cancelled) throws IOException {
        try (YsmPackage source = YsmPackage.open(path)) {
            return describe(source, cancelled);
        }
    }

    private static Description describe(YsmPackage source, BooleanSupplier cancelled) throws IOException {
        if (!source.exists(CosmeticMetadata.FILE)) {
            throw new IOException("MISSING_COSMETIC_METADATA");
        }
        try {
            CosmeticMetadata metadata = CosmeticMetadata.read(source.readString(CosmeticMetadata.FILE));
            Path payload = ysmPayload(source);
            OutfitConfig.Format format = payload == null ? OutfitConfig.Format.FIGURA
                    : Files.isRegularFile(payload) && YsmEncryptedSource.hasMagic(payload)
                    ? OutfitConfig.Format.YSM_ENCRYPTED : OutfitConfig.Format.YSM_PLAIN;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            int count = 0;
            for (String name : source.resourcePaths()) {
                YsmResourceLimits.checkCancelled(cancelled);
                byte[] bytes = source.readBytes(name);
                total += bytes.length;
                YsmResourceLimits.DEFAULT.checkResource(bytes.length, total, ++count, name);
                byte[] key = name.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(key.length).array());
                digest.update(key);
                digest.update(ByteBuffer.allocate(8).putLong(bytes.length).array());
                digest.update(bytes);
            }
            return new Description(metadata, HexFormat.of().formatHex(digest.digest()), format);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        } catch (RuntimeException invalid) {
            throw new IOException("INVALID_COSMETIC_METADATA", invalid);
        }
    }

    public static Prepared prepare(Path path, UUID executionOwner, String expectedHash,
                                   BooleanSupplier cancelled) throws Exception {
        try (YsmPackage source = YsmPackage.open(path)) {
            Description description = describe(source, cancelled);
            if (!description.contentHash().equals(expectedHash)) {
                throw new IOException("COSMETIC_SOURCE_CHANGED");
            }
            Path root = source.resolve(CosmeticMetadata.FILE).getParent();
            CompoundTag wrapper = LocalAvatarLoader.prepareCosmeticAvatar(root, executionOwner);
            CompoundTag nbt = wrapper;
            Path payload = ysmPayload(source);
            if (payload != null) {
                try (YsmModelData data = YsmModelData.read(payload, cancelled)) {
                    var manifest = data.manifest();
                    var selected = manifest.textures().stream()
                            .filter(texture -> texture.id().equals(manifest.defaultTexture()))
                            .findFirst().orElse(manifest.textures().isEmpty() ? null : manifest.textures().getFirst());
                    nbt = YsmAvatarLoader.toAvatarNbt(data, selected, executionOwner, null);
                }
                nbt.getCompoundOrEmpty("metadata").merge(wrapper.getCompoundOrEmpty("metadata"));
                for (String key : List.of("scripts", "sounds", "resources")) {
                    if (wrapper.contains(key)) {
                        nbt.put(key, wrapper.get(key).copy());
                    }
                }
            }
            YsmResourceLimits.checkCancelled(cancelled);
            // Detect an edit while native parsing ran; never cache bytes under an obsolete revision.
            if (!describe(source, cancelled).contentHash().equals(expectedHash)) {
                throw new IOException("COSMETIC_SOURCE_CHANGED");
            }
            return new Prepared(description, nbt);
        }
    }

    private static Path ysmPayload(YsmPackage source) throws IOException {
        if (source.exists("ysm.json") || source.exists("main.json")) {
            return source.resolve(CosmeticMetadata.FILE).getParent();
        }
        List<String> candidates = source.resourcePaths().stream()
                .filter(name -> !name.contains("/") && name.toLowerCase(java.util.Locale.ROOT).endsWith(".ysm"))
                .toList();
        boolean figura = source.resourcePaths().stream().anyMatch(name -> name.endsWith(".bbmodel"));
        if (candidates.size() > 1 || (!candidates.isEmpty() && figura)) {
            throw new IOException("AMBIGUOUS_COSMETIC_PAYLOAD");
        }
        return candidates.isEmpty() ? null : source.resolve(candidates.getFirst());
    }
}
