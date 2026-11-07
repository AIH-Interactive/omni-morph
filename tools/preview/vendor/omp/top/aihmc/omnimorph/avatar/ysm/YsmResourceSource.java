package top.aihmc.omnimorph.avatar.ysm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Read-only package resources, independent of their container or filesystem. */
public interface YsmResourceSource extends AutoCloseable {
    String displayName();

    boolean exists(String path);

    byte[] readBytes(String path) throws IOException;

    List<String> resourcePaths() throws IOException;

    default String readString(String path) throws IOException {
        String text = new String(readBytes(path), StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    @Override
    void close();
}
