package top.aihmc.omnimorph.avatar.local;

import com.google.gson.Gson;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarDetector;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.figuramc.figura.avatar.local.LocalAvatarLoader;
/** Device-only source locations. Portable outfit references contain the ID and content hash, never these URIs. */
public final class LocalAvatarSource {
    private record Index(Map<String, UUID> resources) {}
    private record Content(String hash, OutfitConfig.Format format) {}

    public record OpenSource(Path path, FileSystem ownedFileSystem) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (ownedFileSystem != null) {
                ownedFileSystem.close();
            }
        }
    }

    private static final Gson JSON = new Gson();
    private final Path indexFile;
    private final Map<String, UUID> resources = new HashMap<>();

    public LocalAvatarSource(Path dataRoot) throws IOException {
        indexFile = dataRoot.toAbsolutePath().normalize().resolve("avatar-resource-ids.json");
        if (Files.isSymbolicLink(indexFile)) {
            throw new IOException("INVALID_AVATAR_ID_INDEX");
        }
        if (Files.exists(indexFile)) {
            if (!Files.isRegularFile(indexFile) || Files.size(indexFile) > 1024 * 1024) {
                throw new IOException("AVATAR_ID_INDEX_LIMIT");
            }
            try {
                Index stored = JSON.fromJson(Files.readString(indexFile), Index.class);
                if (stored == null || stored.resources() == null || stored.resources().size() > 4096) {
                    throw new IOException("INVALID_AVATAR_ID_INDEX");
                }
                var ids = new HashSet<UUID>();
                for (var entry : stored.resources().entrySet()) {
                    sourceUri(entry.getKey());
                    if (entry.getValue() == null || !ids.add(entry.getValue())) {
                        throw new IOException("INVALID_AVATAR_ID_INDEX");
                    }
                    resources.put(entry.getKey(), entry.getValue());
                }
            } catch (RuntimeException invalid) {
                throw new IOException("INVALID_AVATAR_ID_INDEX", invalid);
            }
        }
    }

    /** Failure to record a source must not prevent existing local models from loading. Cancellation still propagates. */
    public static synchronized OutfitConfig.ResourceRef capture(Path path, BooleanSupplier cancelled) throws IOException {
        try {
            return new LocalAvatarSource(FiguraMod.getFiguraDirectory()).describe(path, cancelled);
        } catch (InterruptedIOException cancelledRead) {
            throw cancelledRead;
        } catch (IOException | RuntimeException unavailable) {
            FiguraMod.LOGGER.warn("Local avatar provenance unavailable for {}", path, unavailable);
            return null;
        }
    }

    public synchronized OutfitConfig.ResourceRef describe(Path path, BooleanSupplier cancelled) throws IOException {
        Path canonical = path.toRealPath();
        String location = canonical.toUri().toString();
        sourceUri(location);
        Content content = content(canonical, cancelled);
        UUID id = resources.get(location);
        if (id == null) {
            if (resources.size() >= 4096) {
                throw new IOException("AVATAR_ID_INDEX_LIMIT");
            }
            id = UUID.randomUUID();
            resources.put(location, id);
            try {
                save();
            } catch (IOException failure) {
                resources.remove(location);
                throw failure;
            }
        }
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, id, null, null, null, content.hash(), content.format());
    }

    public static void verify(Path path, OutfitConfig.ResourceRef expected, BooleanSupplier cancelled) throws IOException {
        if (expected == null) {
            return;
        }
        Content actual = content(path, cancelled);
        if (expected.source() != OutfitConfig.Source.LOCAL || !expected.contentHash().equals(actual.hash())
                || expected.format() != actual.format()) {
            throw new IOException("OUTFIT_BASE_SOURCE_CHANGED");
        }
    }

    /** Worker-only fixed-version input for private outfit previews; never publishes a player model. */
    public net.minecraft.nbt.CompoundTag prepare(OutfitConfig.ResourceRef reference, UUID owner,
                                                BooleanSupplier cancelled) throws Exception {
        try (var source = open(reference, cancelled)) {
            Path path = source.path();
            net.minecraft.nbt.CompoundTag nbt;
            if (reference.format() == OutfitConfig.Format.FIGURA) {
                if (Files.isDirectory(path)) {
                    nbt = LocalAvatarLoader.prepareFiguraAvatar(path, owner);
                } else {
                    try (var archive = FileSystems.newFileSystem(path, Map.of())) {
                        nbt = LocalAvatarLoader.prepareFiguraAvatar(archive.getPath("/"), owner);
                    }
                }
            } else {
                try (var data = YsmModelData.read(path, cancelled)) {
                    var texture = data.manifest().textures().stream()
                            .filter(option -> option.id().equals(data.manifest().defaultTexture()))
                            .findFirst().orElse(data.manifest().textures().isEmpty() ? null : data.manifest().textures().getFirst());
                    nbt = YsmAvatarLoader.toAvatarNbt(data, texture, owner, path);
                }
            }
            YsmResourceLimits.checkCancelled(cancelled);
            verify(path, reference, cancelled);
            if (!nbt.getCompoundOrEmpty("metadata").getStringOr("resource_kind", "AVATAR").equals("AVATAR")) {
                throw new IOException("OUTFIT_BASE_RESOURCE_KIND_REQUIRED");
            }
            return nbt;
        }
    }

    /** Reopens archive subdirectories after restart; never closes a filesystem borrowed from the wardrobe inventory. */
    public synchronized OpenSource open(OutfitConfig.ResourceRef reference, BooleanSupplier cancelled) throws IOException {
        if (reference.source() != OutfitConfig.Source.LOCAL) {
            throw new IOException("OUTFIT_BASE_SOURCE_MISMATCH");
        }
        String location = resources.entrySet().stream().filter(entry -> entry.getValue().equals(reference.localId()))
                .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IOException("OUTFIT_BASE_SOURCE_MISSING"));
        URI uri = sourceUri(location);
        FileSystem owned = null;
        try {
            Path path;
            try {
                path = Path.of(uri);
            } catch (FileSystemNotFoundException missing) {
                if (!uri.getScheme().equalsIgnoreCase("jar")) {
                    throw missing;
                }
                URI archive = URI.create(location.substring(0, location.indexOf("!/")));
                try {
                    owned = FileSystems.newFileSystem(archive, Map.of());
                } catch (FileSystemAlreadyExistsException borrowed) {
                    // Another inventory may have opened the same archive in the meantime.
                }
                path = Path.of(uri);
            }
            verify(path, reference, cancelled);
            return new OpenSource(path, owned);
        } catch (IOException | RuntimeException failure) {
            if (owned != null) {
                owned.close();
            }
            throw failure;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(indexFile.getParent());
        Path temporary = Files.createTempFile(indexFile.getParent(), "avatar-ids-", ".part");
        try {
            byte[] json = JSON.toJson(new Index(Map.copyOf(resources))).getBytes(StandardCharsets.UTF_8);
            if (json.length > 1024 * 1024) {
                throw new IOException("AVATAR_ID_INDEX_LIMIT");
            }
            Files.write(temporary, json);
            Files.move(temporary, indexFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static URI sourceUri(String text) throws IOException {
        URI uri = URI.create(text);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri;
        }
        int separator = text.indexOf("!/");
        if ("jar".equalsIgnoreCase(uri.getScheme()) && separator > 4
                && "file".equalsIgnoreCase(URI.create(text.substring(4, separator)).getScheme())) {
            return uri;
        }
        throw new IOException("INVALID_AVATAR_SOURCE_URI");
    }

    private static Content content(Path path, BooleanSupplier cancelled) throws IOException {
        YsmResourceLimits.checkCancelled(cancelled);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        if (YsmEncryptedSource.hasMagic(path)) {
            long total = 0;
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    YsmResourceLimits.checkCancelled(cancelled);
                    total += count;
                    if (total > YsmResourceLimits.DEFAULT.fileBytes()) {
                        throw new IOException("RESOURCE_LIMIT: avatar source file exceeds budget");
                    }
                    digest.update(buffer, 0, count);
                }
            }
            return new Content(HexFormat.of().formatHex(digest.digest()), OutfitConfig.Format.YSM_ENCRYPTED);
        }
        OutfitConfig.Format format = YsmAvatarDetector.isYsmAvatar(path)
                ? OutfitConfig.Format.YSM_PLAIN : OutfitConfig.Format.FIGURA;
        try (YsmPackage source = YsmPackage.open(path)) {
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
        }
        return new Content(HexFormat.of().formatHex(digest.digest()), format);
    }
}
