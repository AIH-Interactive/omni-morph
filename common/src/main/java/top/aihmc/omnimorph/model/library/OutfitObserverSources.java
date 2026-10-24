package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonArray;
import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.OutfitObserverSnapshot;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.cache.ModelCacheLayout;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Current observer admission surrounds cache reads and native parsing; cached bytes never grant access. */
public final class OutfitObserverSources {
    private final LibraryClient client;
    private final Path root;
    private final ModelCacheCodec codec;

    public OutfitObserverSources(LibraryClient client, Path cacheRoot) {
        this.client = client;
        root = cacheRoot.toAbsolutePath().normalize();
        codec = new ModelCacheCodec(root);
    }

    public LibraryClient.Request<CompoundTag> read(OutfitObserverSnapshot snapshot, OutfitObserverSnapshot.Resource resource) {
        if (snapshot.configuration() == null || !snapshot.resources().contains(resource)) {
            throw new IllegalArgumentException("OUTFIT_OBSERVER_SOURCE_REQUIRED");
        }
        return client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            if (!snapshot.sameProjection(exchange.observerOutfit(snapshot.owner()))) {
                throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
            }
            Path cache = cachePath(snapshot, resource);
            byte[] bytes = cached(cache, resource);
            boolean fetched = bytes == null;
            if (fetched) {
                bytes = exchange.observerOutfitBody(snapshot, resource);
            }
            CompoundTag nbt = OutfitSourceService.parse(bytes, resource.reference(), resource.kind(), resource.accessoryMetadata());
            exchange.requireCurrent();
            if (!snapshot.sameProjection(exchange.observerOutfit(snapshot.owner()))) {
                throw new LibraryClient.Failure(409, "OUTFIT_CHANGED");
            }
            if (fetched) {
                Path temporary = Files.createTempFile(cache.getParent(), "outfit-observer-", ".part");
                try {
                    codec.write(temporary, bytes);
                    exchange.requireCurrent();
                    Files.move(temporary, cache, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            exchange.requireCurrent();
            return nbt;
        });
    }

    private Path cachePath(OutfitObserverSnapshot snapshot, OutfitObserverSnapshot.Resource resource) throws IOException {
        Path directory = root.resolve(resource.kind() == OutfitConfig.ResourceKind.ACCESSORY
                ? ModelCacheLayout.COSMETIC_DIRECTORY : ModelCacheLayout.AVATAR_DIRECTORY);
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("UNSAFE_OUTFIT_OBSERVER_CACHE");
            }
        }
        Files.createDirectories(directory);
        var identity = new JsonArray();
        for (String value : List.of(client.origin(), client.session().instance(), client.session().player().toString(),
                snapshot.owner().toString(), resource.id(), resource.reference().toString(), resource.kind().name(),
                resource.rawSha256(), resource.contentSha256(), resource.schemaSha256())) {
            identity.add(value);
        }
        String key = CloudHttp.sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
        return directory.resolve("outfit-observer-" + key + ModelCacheLayout.extension(resource.kind().name()));
    }

    private byte[] cached(Path path, OutfitObserverSnapshot.Resource resource) {
        try {
            byte[] bytes = codec.read(path, CloudHttp.FILE_BYTES);
            return bytes.length == resource.byteLength() && CloudHttp.sha256(bytes).equals(resource.rawSha256()) ? bytes : null;
        } catch (IOException invalid) {
            return null;
        }
    }
}
