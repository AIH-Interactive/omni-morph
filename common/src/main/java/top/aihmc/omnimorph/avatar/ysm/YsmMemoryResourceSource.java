package top.aihmc.omnimorph.avatar.ysm;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Owns its bytes. Closing releases and clears only this source's buffers. */
public final class YsmMemoryResourceSource implements YsmResourceSource {
    private final String displayName;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private boolean closed;

    public YsmMemoryResourceSource(String displayName, Map<String, byte[]> input) throws IOException {
        this(displayName, input, YsmResourceLimits.DEFAULT);
    }

    public YsmMemoryResourceSource(String displayName, Map<String, byte[]> input,
                                   YsmResourceLimits limits) throws IOException {
        this.displayName = displayName;
        long total = 0;
        try {
            for (var entry : input.entrySet()) {
                YsmResourceLimits.checkCancelled(() -> false);
                String path = requirePath(entry.getKey());
                String lower = path.toLowerCase(Locale.ROOT);
                if (names.containsKey(lower)) {
                    throw new IOException("PATH_CONFLICT: duplicate YSM resource " + path);
                }
                byte[] bytes = entry.getValue();
                if (bytes == null) {
                    throw new IOException("MISSING_RESOURCE: null resource " + path);
                }
                total += bytes.length;
                limits.checkResource(bytes.length, total, files.size() + 1, path);
                files.put(path, bytes.clone());
                names.put(lower, path);
            }
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    public static YsmMemoryResourceSource snapshot(YsmResourceSource source, YsmResourceLimits limits,
                                                   BooleanSupplier cancelled) throws IOException {
        Map<String, byte[]> snapshot = new LinkedHashMap<>();
        long total = 0;
        try {
            List<String> paths = source.resourcePaths();
            limits.checkResource(0, 0, paths.size(), "<inventory>");
            for (String path : paths) {
                YsmResourceLimits.checkCancelled(cancelled);
                byte[] bytes = source.readBytes(path);
                snapshot.put(path, bytes);
                total += bytes.length;
                limits.checkResource(bytes.length, total, snapshot.size(), path);
            }
            YsmResourceLimits.checkCancelled(cancelled);
            return new YsmMemoryResourceSource(source.displayName(), snapshot, limits);
        } finally {
            snapshot.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        }
    }

    private static String requirePath(String path) throws IOException {
        String normalized = YsmPackage.normalize(path);
        if (normalized.isBlank()) {
            throw new IOException("INVALID_PATH: invalid YSM resource path");
        }
        return normalized;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean exists(String path) {
        return !closed && names.containsKey(YsmPackage.normalize(path).toLowerCase(Locale.ROOT));
    }

    @Override
    public byte[] readBytes(String path) throws IOException {
        if (closed) {
            throw new IOException("CLOSED_SOURCE: YSM resource source is closed");
        }
        String name = names.get(requirePath(path).toLowerCase(Locale.ROOT));
        if (name == null) {
            throw new IOException("MISSING_RESOURCE: " + path);
        }
        return files.get(name).clone();
    }

    @Override
    public List<String> resourcePaths() throws IOException {
        if (closed) {
            throw new IOException("CLOSED_SOURCE: YSM resource source is closed");
        }
        return files.keySet().stream().sorted().toList();
    }

    @Override
    public void close() {
        files.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        files.clear();
        names.clear();
        closed = true;
    }
}
