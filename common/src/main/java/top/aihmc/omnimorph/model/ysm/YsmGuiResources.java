package top.aihmc.omnimorph.model.ysm;

import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmTextureDecoder;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Model-owned preview images, decoded on load and uploaded only when the existing GUI needs them. */
public final class YsmGuiResources implements AutoCloseable {
    private final Avatar owner;
    private final Map<String, byte[]> images = new LinkedHashMap<>();
    private final Map<String, FiguraTexture> textures = new LinkedHashMap<>();
    private CompoundTag names = new CompoundTag();
    private String foreground = "";
    private String background = "";
    private boolean closed;
    private int invalidImages;

    public YsmGuiResources(Avatar owner) {
        this.owner = owner;
    }

    public synchronized void load(CompoundTag tag) {
        close();
        closed = false;
        invalidImages = 0;
        names = tag.getCompoundOrEmpty("gui_image_names").copy();
        foreground = tag.getStringOr("gui_foreground", "");
        background = tag.getStringOr("gui_background", "");
        YsmDecodeBudget budget = new YsmDecodeBudget();
        for (var value : tag.getListOrEmpty("gui_entries")) {
            if (!(value instanceof CompoundTag entry)) {
                continue;
            }
            String path = entry.getStringOr("path", "");
            if (path.isBlank() || images.containsKey(path)) {
                continue;
            }
            byte[] bytes = entry.getByteArray("data").orElse(new byte[0]).clone();
            try {
                images.put(path, YsmTextureDecoder.toPng(bytes, 0, 0, 0, budget));
            } catch (IOException failure) {
                // Preview assets are optional. Keep diagnostics free of decrypted names or codec messages.
                invalidImages++;
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    public synchronized FiguraTexture foreground() {
        return get(foreground);
    }

    public synchronized FiguraTexture background() {
        return get(background);
    }

    public synchronized FiguraTexture get(String name) {
        if (closed || name == null || name.isBlank()) {
            return null;
        }
        String path = names.getStringOr(name, name);
        byte[] bytes = images.get(path);
        if (bytes == null) {
            return null;
        }
        return textures.computeIfAbsent(path, key -> new FiguraTexture(owner, "YSM GUI", bytes));
    }

    public synchronized int invalidImages() {
        return invalidImages;
    }

    @Override
    public synchronized void close() {
        closed = true;
        textures.values().forEach(FiguraTexture::close);
        textures.clear();
        images.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        images.clear();
        names = new CompoundTag();
        foreground = "";
        background = "";
    }
}
