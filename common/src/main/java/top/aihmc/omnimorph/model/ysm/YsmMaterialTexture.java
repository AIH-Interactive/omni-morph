package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

import java.util.Arrays;

/** A selected base image and its owned PBR channels; the ordinary renderer uses the base image. */
public final class YsmMaterialTexture extends FiguraTexture {
    private final Avatar channelOwner;
    private final byte[] normalData;
    private final byte[] specularData;
    private ChannelTexture normal;
    private ChannelTexture specular;
    private boolean closed;

    public YsmMaterialTexture(Avatar owner, String name, byte[] base, byte[] normal, byte[] specular) {
        super(owner, name, base);
        channelOwner = owner;
        normalData = normal.clone();
        specularData = specular.clone();
    }

    public synchronized FiguraTexture normalTexture() {
        if (closed || normalData.length == 0) {
            return null;
        }
        if (normal == null || normal.released) {
            normal = new ChannelTexture(channelOwner, getName() + "_n", normalData);
        }
        return normal;
    }

    public synchronized FiguraTexture specularTexture() {
        if (closed || specularData.length == 0) {
            return null;
        }
        if (specular == null || specular.released) {
            specular = new ChannelTexture(channelOwner, getName() + "_s", specularData);
        }
        return specular;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (normal != null) {
            normal.close();
        }
        if (specular != null) {
            specular.close();
        }
        Arrays.fill(normalData, (byte) 0);
        Arrays.fill(specularData, (byte) 0);
        super.close();
    }

    /** Iris closes accepted channels when clearing its holders; recreate them on the next PBR load. */
    private static final class ChannelTexture extends FiguraTexture {
        private volatile boolean released;

        private ChannelTexture(Avatar owner, String name, byte[] data) {
            super(owner, name, data);
        }

        @Override
        public void close() {
            released = true;
            super.close();
        }
    }
}
