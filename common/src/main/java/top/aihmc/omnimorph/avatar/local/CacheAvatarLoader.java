package top.aihmc.omnimorph.avatar.local;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.utils.IOUtils;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import com.google.gson.Gson;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheAvatarLoader {

    public static void init() {
        LocalAvatarLoader.async(() -> {
            Path file = getAvatarCacheDirectory();
            if (!(Files.exists(file) && Files.isDirectory(file)))
                return;

            List<Path> children = IOUtils.listPaths(file);
            if (children == null)
                return;

            for (Path child : children) {
                try {
                    FileTime time = Files.getLastModifiedTime(child);
                    long diff = System.currentTimeMillis() - time.toMillis();
                    long elapsed = TimeUnit.MILLISECONDS.toDays(diff);
                    if (elapsed > 7 || child.getFileName().toString().endsWith(".nbt")) {
                        if (Files.deleteIfExists(child)) {
                            FiguraMod.debug("Successfully deleted cache avatar \"{}\" with \"{}\" days old", IOUtils.getFileNameOrEmpty(child), elapsed);
                        } else {
                            throw new Exception();
                        }
                    }
                } catch (Exception ignored) {
                    FiguraMod.debug("Failed to delete cache avatar \"{}\"", IOUtils.getFileNameOrEmpty(child));
                }
            }
        });
    }

    public static boolean checkAndLoad(String hash, UserData target) {
        return checkAndLoad(hash, target, () -> true);
    }

    public static boolean checkAndLoad(String hash, UserData target, java.util.function.BooleanSupplier current) {
        return checkAndLoad(hash, target, current, () -> {});
    }

    public static boolean checkAndLoad(String hash, UserData target,
                                       java.util.function.BooleanSupplier current, Runnable missing) {
        Path p = getAvatarCacheDirectory();
        p = p.resolve(hash + ".omc");

        if (Files.exists(p)) {
            load(hash, target, current, missing);
            return true;
        }

        return false;
    }

    public static void load(String hash, UserData target) {
        load(hash, target, () -> true);
    }

    private static void load(String hash, UserData target, java.util.function.BooleanSupplier current) {
        load(hash, target, current, () -> {});
    }

    private static void load(String hash, UserData target, java.util.function.BooleanSupplier current,
                             Runnable missing) {
        long progress = target.loading.begin(top.aihmc.omnimorph.avatar.AvatarLoadProgress.Stage.READING);
        LocalAvatarLoader.async(() -> {
            Path path = getAvatarCacheDirectory().resolve(hash + ".omc");
            try (var stream = new java.io.ByteArrayInputStream(
                    new ModelCacheCodec(FiguraMod.getCacheDirectory()).read(path, CloudHttp.FILE_BYTES))) {
                var nbt = NbtIo.readCompressed(stream, NbtAccounter.create(256L * 1024 * 1024));
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (current.getAsBoolean()) {
                        target.loadAvatar(nbt, current);
                    }
                    target.loading.finish(progress);
                });
                FiguraMod.debug("Loaded avatar \"{}\" from cache to \"{}\"", hash, target.id);
            } catch (Exception e) {
                target.loading.finish(progress);
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException cleanupFailure) {
                    FiguraMod.debug("Failed to remove invalid model cache");
                }
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (current.getAsBoolean()) {
                        missing.run();
                    }
                });
                FiguraMod.LOGGER.error("Failed to load cache avatar: " + hash, e);
            }
        });
    }

    public static void save(String hash, CompoundTag nbt) {
        LocalAvatarLoader.async(() -> {
            Path file = getAvatarCacheDirectory().resolve(hash + ".omc");
            try {
                CompoundTag portable = YsmSourcePayload.forOutput(nbt);
                try (var output = new ModelCacheCodec(FiguraMod.getCacheDirectory()).output(file)) {
                    NbtIo.writeCompressed(portable, output);
                }
                FiguraMod.debug("Saved avatar \"{}\" on cache", hash);
            } catch (Exception e) {
                FiguraMod.LOGGER.error("Failed to save avatar on cache: " + hash, e);
            }
        });
    }

    public static void clearCache() {
        LocalAvatarLoader.async(() -> {
            Path file = getAvatarCacheDirectory();

            if (!(Files.exists(file) && Files.isDirectory(file)))
                return;

            List<Path> children = IOUtils.listPaths(file);
            if (children == null)
                return;

            for (Path child : children) {
                try {
                    if (!Files.deleteIfExists(child))
                        throw new Exception();
                } catch (Exception ignored) {
                    FiguraMod.debug("Failed to delete cache avatar \"{}\"", IOUtils.getFileNameOrEmpty(child));
                }
            }

            FiguraMod.debug("Finished clearing avatar cache");
        });
    }

    /** Descriptive provenance only; every new library use must obtain fresh backend permission. */
    public record LibrarySource(String instance, UUID viewer, UUID modelId, long modelRevision,
                                long policyRevision, String assetId, long assetRevision,
                                String visibility, boolean requiresAuthorization, boolean showPrivateEquipment) {}

    /** Worker-only formal cache write. Preview files are never copied or promoted here. */
    public static void saveLibrary(String hash, CompoundTag nbt, LibrarySource source) throws java.io.IOException {
        if (!hash.matches("[a-f0-9]{64}") || source.instance() == null || source.viewer() == null
                || source.modelId() == null || source.modelRevision() < 1 || source.policyRevision() < 1) {
            throw new java.io.IOException("INVALID_LIBRARY_SOURCE");
        }
        Path directory = getAvatarCacheDirectory();
        String scope = CloudHttp.sha256((source.instance() + "\n" + source.viewer()).getBytes(StandardCharsets.UTF_8));
        Path model = Files.createTempFile(directory, "library-model-", ".part");
        Path provenance = null;
        try {
            try (var output = new ModelCacheCodec(FiguraMod.getCacheDirectory()).output(model)) {
                NbtIo.writeCompressed(YsmSourcePayload.forOutput(nbt), output);
            }
            if (Files.size(model) > CloudHttp.FILE_BYTES) {
                throw new java.io.IOException("FORMAL_CACHE_LIMIT");
            }
            provenance = Files.createTempFile(directory, "library-source-", ".part");
            Files.writeString(provenance, new Gson().toJson(source), StandardCharsets.UTF_8);
            Files.move(model, directory.resolve(hash + ".omc"), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            Files.move(provenance, directory.resolve(hash + "." + scope + ".library-source.json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(model);
            if (provenance != null) {
                Files.deleteIfExists(provenance);
            }
        }
    }

    // cache directory
    public static Path getAvatarCacheDirectory() {
        return IOUtils.getOrCreateDir(FiguraMod.getCacheDirectory(), "avatars");
    }
}
