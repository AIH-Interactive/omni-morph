package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Bounded worker-only distribution preparation. No Avatar, equipment or preview-cache publication. */
public final class LibraryPackagePreparation {
    public enum Phase {
        READING, PREPARING, COMPRESSING
    }
    private LibraryPackagePreparation() {
    }

    public record Prepared(LibraryMetadataReader.Metadata metadata, CompoundTag nbt, byte[] compressed,
                           String sha256) {
        public Prepared {
            nbt = nbt.copy();
            compressed = compressed.clone();
        }

        @Override
        public CompoundTag nbt() {
            return nbt.copy();
        }

        @Override
        public byte[] compressed() {
            return compressed.clone();
        }

        public int byteLength() {
            return compressed.length;
        }

        public String metadataSource() {
            if (resourceKind().equals("ACCESSORY") && metadata.modelType().equals("FIGURA")) {
                return "cosmetic.json";
            }
            return switch (metadata.modelType()) {
                case "FIGURA" -> "avatar.json";
                case "YSM_ENCRYPTED" -> "encrypted_manifest";
                default -> nbt.getCompoundOrEmpty("metadata").getStringOr("ysm_kind", "").equals("old")
                        ? "main.json" : "ysm.json";
            };
        }

        public String resourceKind() {
            return nbt.getCompoundOrEmpty("metadata").getStringOr("resource_kind", "AVATAR");
        }

        public JsonObject accessoryMetadata() {
            return resourceKind().equals("ACCESSORY") ? JsonParser.parseString(
                    nbt.getCompoundOrEmpty("metadata").getStringOr("cosmetic_json", "")).getAsJsonObject() : null;
        }
    }

    public static Prepared prepare(Path input, UUID owner, long uploadLimit,
                                   BooleanSupplier cancelled) throws Exception {
        return prepare(input, owner, uploadLimit, cancelled, ignored -> {});
    }

    public static Prepared prepare(Path input, UUID owner, long uploadLimit,
                                   BooleanSupplier cancelled, java.util.function.Consumer<Phase> phase) throws Exception {
        phase.accept(Phase.READING);
        String filename = input.getFileName().toString();
        String lower = filename.toLowerCase(Locale.ROOT);
        if ((!lower.endsWith(".zip") && !lower.endsWith(".ysm")) || uploadLimit < 1) {
            throw new IOException("UNSUPPORTED_FORMAT");
        }
        YsmResourceLimits.checkCancelled(cancelled);
        Path staging = Files.createTempDirectory("omnimorph-library-source-");
        try {
            // Parse metadata and runtime data from the same bounded snapshot if the original changes.
            Path source = staging.resolve(filename);
            try (var stream = Files.newInputStream(input); var output = Files.newOutputStream(source)) {
                copy(stream, output, YsmResourceLimits.DEFAULT.fileBytes(), cancelled);
            }
            var metadata = LibraryMetadataReader.read(source, cancelled);
            phase.accept(Phase.PREPARING);
            CompoundTag nbt;
            if (metadata.modelType().equals("FIGURA")) {
                try (var archive = YsmPackage.open(source)) {
                    String root = LibraryMetadataReader.figuraRoot(archive, cancelled);
                    long total = 0;
                    int count = 0;
                    for (String name : archive.resourcePaths()) {
                        YsmResourceLimits.checkCancelled(cancelled);
                        if (!name.startsWith(root)) {
                            continue;
                        }
                        byte[] bytes = archive.readBytes(name);
                        total += bytes.length;
                        YsmResourceLimits.DEFAULT.checkResource(bytes.length, total, ++count, name);
                    }
                    // ZIP names need not be valid Windows paths (for example LICENSE folders with newlines).
                    // Compile inside the validated archive instead of extracting its entries onto the host.
                    Path directory = archive.resolve(root + "avatar.json").getParent();
                    nbt = LocalAvatarLoader.prepareFiguraAvatar(directory, owner);
                }
            } else {
                try (var data = YsmModelData.read(source, cancelled)) {
                    var manifest = data.manifest();
                    var texture = manifest.textures().stream()
                            .filter(option -> option.id().equals(manifest.defaultTexture()))
                            .findFirst().orElse(manifest.textures().isEmpty() ? null : manifest.textures().getFirst());
                    nbt = YsmAvatarLoader.toAvatarNbt(data, texture, owner, null);
                }
            }
            YsmResourceLimits.checkCancelled(cancelled);
            return finish(metadata, nbt, uploadLimit, cancelled, phase);
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /** A cosmetic source stays a native accessory, including the encrypted payload inside an author wrapper. */
    public static Prepared prepareAccessory(Path input, UUID owner, long uploadLimit,
                                             BooleanSupplier cancelled, java.util.function.Consumer<Phase> phase) throws Exception {
        phase.accept(Phase.READING);
        YsmResourceLimits.checkCancelled(cancelled);
        if (uploadLimit < 1) {
            throw new IOException("UPLOAD_LIMIT");
        }
        try (var source = YsmPackage.open(input)) {
            String root = cosmeticRoot(source, cancelled);
            Path directory = source.resolve(root + CosmeticMetadata.FILE).getParent();
            var description = LocalCosmeticSource.describe(directory, cancelled);
            phase.accept(Phase.PREPARING);
            var prepared = LocalCosmeticSource.prepare(directory, owner, description.contentHash(), cancelled);
            var common = LibraryMetadataReader.fromFigura(description.metadata().common(), input.getFileName().toString());
            var metadata = new LibraryMetadataReader.Metadata(description.format().name(), common.internalName(),
                    common.authors(), common.description(), common.requiresAuthorizationDefault());
            return finish(metadata, prepared.nbt(), uploadLimit, cancelled, phase);
        }
    }

    private static String cosmeticRoot(YsmPackage source, BooleanSupplier cancelled) throws IOException {
        if (source.exists(CosmeticMetadata.FILE)) {
            return "";
        }
        String root = null;
        for (String name : source.resourcePaths()) {
            YsmResourceLimits.checkCancelled(cancelled);
            if (name.endsWith("/" + CosmeticMetadata.FILE)) {
                if (root != null) {
                    throw new IOException("AMBIGUOUS_COSMETIC_SOURCE");
                }
                root = name.substring(0, name.length() - CosmeticMetadata.FILE.length());
            }
        }
        if (root == null) {
            throw new IOException("MISSING_COSMETIC_METADATA");
        }
        return root;
    }

    /** Edit only placement declarations on an immutable distribution copy; author capabilities and source files stay intact. */
    public static Prepared withAccessoryMetadata(Prepared source, JsonObject declaration, long uploadLimit,
                                                  BooleanSupplier cancelled) throws IOException {
        YsmResourceLimits.checkCancelled(cancelled);
        if (!source.resourceKind().equals("ACCESSORY") || declaration == null) {
            throw new IOException("COSMETIC_RESOURCE_KIND_REQUIRED");
        }
        CosmeticMetadata.read(declaration.toString());
        JsonObject authored = source.accessoryMetadata();
        JsonObject immutableSource = authored.deepCopy();
        JsonObject immutableEdited = declaration.deepCopy();
        for (String key : java.util.List.of("category", "cosmetic_anchor", "default_transform")) {
            immutableSource.remove(key);
            immutableEdited.remove(key);
        }
        if (!immutableSource.equals(immutableEdited)) {
            throw new IOException("COSMETIC_AUTHOR_DECLARATION_CHANGED");
        }
        if (authored.equals(declaration)) {
            return source;
        }
        CompoundTag nbt = source.nbt();
        nbt.getCompoundOrEmpty("metadata").putString("cosmetic_json", declaration.toString());
        if (nbt.contains("ysm") && nbt.getCompoundOrEmpty("ysm").contains("cosmetic_json")) {
            nbt.getCompoundOrEmpty("ysm").putString("cosmetic_json", declaration.toString());
        }
        return finish(source.metadata(), nbt, uploadLimit, cancelled, ignored -> {});
    }

    private static Prepared finish(LibraryMetadataReader.Metadata metadata, CompoundTag nbt, long uploadLimit,
                                     BooleanSupplier cancelled, java.util.function.Consumer<Phase> phase) throws IOException {
        // Preserve declared authors; do not serialize the wardrobe's display-only '?' fallback.
        nbt.getCompoundOrEmpty("metadata").putString("name", metadata.internalName());
        nbt.getCompoundOrEmpty("metadata").putString("authors", String.join("\n", metadata.authors()));
        return compress(metadata, nbt, uploadLimit, cancelled, phase);
    }

    /** Fixed local inputs have already been parsed; preserve their native metadata, including formatting. */
    static Prepared fromNative(CompoundTag source, OutfitConfig.Format format,
                               long uploadLimit, BooleanSupplier cancelled) throws IOException {
        YsmResourceLimits.checkCancelled(cancelled);
        CompoundTag nbt = source.copy();
        var tag = nbt.getCompoundOrEmpty("metadata");
        var metadata = new LibraryMetadataReader.Metadata(format.name(), tag.getStringOr("name", ""),
                java.util.List.of(tag.getStringOr("authors", "").split("\n")),
                tag.getStringOr("description", ""), false);
        return compress(metadata, nbt, uploadLimit, cancelled, ignored -> {});
    }

    private static Prepared compress(LibraryMetadataReader.Metadata metadata, CompoundTag nbt, long uploadLimit,
                                     BooleanSupplier cancelled, java.util.function.Consumer<Phase> phase) throws IOException {
        nbt = YsmSourcePayload.forOutput(nbt);
        phase.accept(Phase.COMPRESSING);
        var bytes = new ByteArrayOutputStream();
        long limit = Math.min(uploadLimit, CloudHttp.FILE_BYTES);
        try (var output = new OutputStream() {
            private long written;

            @Override
            public void write(int value) throws IOException {
                reserve(1);
                bytes.write(value);
            }

            @Override
            public void write(byte[] values, int offset, int length) throws IOException {
                reserve(length);
                bytes.write(values, offset, length);
            }

            private void reserve(int count) throws IOException {
                YsmResourceLimits.checkCancelled(cancelled);
                written += count;
                if (written > limit) {
                    throw new IOException("UPLOAD_LIMIT");
                }
            }
        }) {
            NbtIo.writeCompressed(nbt, output);
        }
        byte[] distribution = bytes.toByteArray();
        return new Prepared(metadata, nbt, distribution, CloudHttp.sha256(distribution));
    }

    private static void copy(java.io.InputStream input, OutputStream output, long limit,
                             BooleanSupplier cancelled) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            YsmResourceLimits.checkCancelled(cancelled);
            total += count;
            if (total > limit) {
                throw new IOException("RESOURCE_LIMIT");
            }
            output.write(buffer, 0, count);
        }
    }
}
