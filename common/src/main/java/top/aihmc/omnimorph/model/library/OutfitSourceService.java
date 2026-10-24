package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.OutfitPrivateUpload;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.cache.ModelCacheLayout;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Fixed owner dependencies. A cache hit never replaces fresh server admission. */
public final class OutfitSourceService {
    public record Prepared(OutfitPrivateUpload.Binding binding, CompoundTag nbt) {
        public Prepared {
            nbt = nbt.copy();
        }

        @Override
        public CompoundTag nbt() {
            return nbt.copy();
        }
    }

    private final LibraryClient client;
    private final Path root;
    private final ModelCacheCodec codec;

    public OutfitSourceService(LibraryClient client, Path cacheRoot) {
        this.client = client;
        this.root = cacheRoot.toAbsolutePath().normalize();
        this.codec = new ModelCacheCodec(root);
    }

    public LibraryClient.Request<Prepared> read(OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind) {
        OutfitPrivateUpload.lookup(reference, kind);
        return client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            var binding = exchange.resolveOutfitSource(reference, kind)
                    .orElseThrow(() -> new LibraryClient.Failure(404, "NOT_FOUND"));
            Path cache = cachePath(binding);
            byte[] bytes = cached(cache, binding.rawSha256());
            boolean fetched = bytes == null;
            if (fetched) {
                bytes = exchange.outfitSourceBody(binding);
            }
            CompoundTag nbt = parse(bytes, binding);
            exchange.requireCurrent();
            var current = exchange.resolveOutfitSource(reference, kind).orElse(null);
            if (!binding.sameSource(current)) {
                throw new LibraryClient.Failure(409, "CONFLICT");
            }
            if (fetched) {
                Path temporary = Files.createTempFile(cache.getParent(), "outfit-source-", ".part");
                try {
                    codec.write(temporary, bytes);
                    exchange.requireCurrent();
                    Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            exchange.requireCurrent();
            return new Prepared(binding, nbt);
        });
    }

    private Path cachePath(OutfitPrivateUpload.Binding binding) throws IOException {
        Path directory = root.resolve(binding.resourceKind() == OutfitConfig.ResourceKind.ACCESSORY
                ? ModelCacheLayout.COSMETIC_DIRECTORY : ModelCacheLayout.AVATAR_DIRECTORY);
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("UNSAFE_OUTFIT_SOURCE_CACHE");
            }
        }
        Files.createDirectories(directory);
        var identity = new com.google.gson.JsonArray();
        for (String value : java.util.List.of(client.origin(), client.session().instance(),
                client.session().player().toString(), binding.reference().localId().toString(),
                binding.reference().contentHash(), binding.reference().format().name(), binding.resourceKind().name(),
                binding.assetId(), Long.toString(binding.assetRevision()), binding.rawSha256())) {
            identity.add(value);
        }
        String key = CloudHttp.sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
        return directory.resolve("outfit-source-" + key + ModelCacheLayout.extension(binding.resourceKind().name()));
    }

    private byte[] cached(Path path, String hash) {
        try {
            byte[] bytes = codec.read(path, CloudHttp.FILE_BYTES);
            return CloudHttp.sha256(bytes).equals(hash) ? bytes : null;
        } catch (IOException invalid) {
            return null;
        }
    }

    private static CompoundTag parse(byte[] bytes, OutfitPrivateUpload.Binding binding) throws IOException {
        return parse(bytes, binding.reference(), binding.resourceKind(), binding.accessoryMetadata());
    }

    static CompoundTag parse(byte[] bytes, OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind,
                             CosmeticMetadata declaration) throws IOException {
        CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(256L * 1024 * 1024));
        var metadata = nbt.getCompoundOrEmpty("metadata");
        boolean ysm = metadata.getStringOr("format", "").equals("ysm-native");
        boolean encrypted = YsmSourcePayload.isEncrypted(nbt.getCompoundOrEmpty("ysm"));
        if (ysm != (reference.format() != OutfitConfig.Format.FIGURA)
                || encrypted != (reference.format() == OutfitConfig.Format.YSM_ENCRYPTED)
                || !metadata.getStringOr("resource_kind", "AVATAR").equals(kind.name())) {
            throw new IOException("DISTRIBUTION_TYPE_MISMATCH");
        }
        if (encrypted && !YsmSourcePayload.forOutput(nbt).equals(nbt)) {
            throw new IOException("NONPORTABLE_ENCRYPTED_DISTRIBUTION");
        }
        if (kind == OutfitConfig.ResourceKind.ACCESSORY) {
            var authored = CosmeticMetadata.read(metadata.getStringOr("cosmetic_json", ""));
            if (!OutfitCodec.encodeAccessoryMetadata(authored)
                    .equals(OutfitCodec.encodeAccessoryMetadata(declaration))) {
                throw new IOException("COSMETIC_AUTHOR_DECLARATION_CHANGED");
            }
        }
        return nbt;
    }
}
