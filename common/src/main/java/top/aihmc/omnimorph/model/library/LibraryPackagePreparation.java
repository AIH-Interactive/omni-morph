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
            return switch (metadata.modelType()) {
                case "FIGURA" -> "avatar.json";
                case "YSM_ENCRYPTED" -> "encrypted_manifest";
                default -> nbt.getCompoundOrEmpty("metadata").getStringOr("ysm_kind", "").equals("old")
                        ? "main.json" : "ysm.json";
            };
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
                Path directory = Files.createDirectory(staging.resolve("figura"));
                try (var archive = YsmPackage.open(source)) {
                    String root = LibraryMetadataReader.figuraRoot(archive, cancelled);
                    long total = 0;
                    int count = 0;
                    for (String name : archive.resourcePaths()) {
                        YsmResourceLimits.checkCancelled(cancelled);
                        if (!name.startsWith(root)) {
                            continue;
                        }
                        String relative = name.substring(root.length());
                        Path target = directory.resolve(relative).normalize();
                        if (!target.startsWith(directory)) {
                            throw new IOException("UNSAFE_RESOURCE_PATH");
                        }
                        byte[] bytes = archive.readBytes(name);
                        total += bytes.length;
                        YsmResourceLimits.DEFAULT.checkResource(bytes.length, total, ++count, name);
                        Files.createDirectories(target.getParent());
                        Files.write(target, bytes);
                    }
                }
                nbt = LocalAvatarLoader.prepareFiguraAvatar(directory, owner);
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
            // Preserve declared authors; do not serialize the wardrobe's display-only '?' fallback.
            nbt.getCompoundOrEmpty("metadata").putString("name", metadata.internalName());
            nbt.getCompoundOrEmpty("metadata").putString("authors", String.join("\n", metadata.authors()));
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
        } finally {
            try (var paths = Files.walk(staging)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
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
