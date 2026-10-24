package top.aihmc.omnimorph.model.library;

import com.google.gson.Gson;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/** Device-local references only. Model files and cloud membership are never changed here. */
public final class LocalOutfitCandidates {
    private static final java.util.Map<Path, LocalOutfitCandidates> STORES = new java.util.HashMap<>();
    private final Set<String> paths = new HashSet<>();
    private final Path file;
    private volatile long generation;

    public static synchronized LocalOutfitCandidates forDirectory(Path root) {
        return STORES.computeIfAbsent(root.toAbsolutePath().normalize(), LocalOutfitCandidates::new);
    }

    private LocalOutfitCandidates(Path root) {
        file = root.resolve("wardrobe-local-outfit.json");
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 1024 * 1024) {
                String[] values = new Gson().fromJson(Files.readString(file), String[].class);
                if (values != null) {
                    paths.addAll(java.util.List.of(values));
                }
            }
        } catch (IOException | RuntimeException invalid) {
            FiguraMod.LOGGER.warn("Could not read local outfit references", invalid);
        }
    }

    public boolean contains(Path path) {
        return path != null && paths.contains(path.toAbsolutePath().normalize().toUri().toString());
    }

    public java.util.concurrent.CompletableFuture<Boolean> set(Path path, boolean included) {
        String value = path.toAbsolutePath().normalize().toUri().toString();
        if (included) {
            paths.add(value);
        } else {
            paths.remove(value);
        }
        String json = new Gson().toJson(paths);
        long expected = ++generation;
        return AvatarLoadExecutor.submit(() -> {
            synchronized (this) {
                if (expected != generation) {
                    return true;
                }
                Path partial = null;
                try {
                    Files.createDirectories(file.getParent());
                    partial = Files.createTempFile(file.getParent(), "outfit-", ".part");
                    Files.writeString(partial, json);
                    Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } catch (IOException failure) {
                    FiguraMod.LOGGER.warn("Could not save local outfit references", failure);
                    return false;
                } finally {
                    if (partial != null) {
                        try {
                            Files.deleteIfExists(partial);
                        } catch (IOException failure) {
                            FiguraMod.debug("Could not remove outfit reference temporary file");
                        }
                    }
                }
            }
        });
    }
}
