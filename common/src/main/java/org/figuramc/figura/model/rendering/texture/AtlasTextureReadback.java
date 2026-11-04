package org.figuramc.figura.model.rendering.texture;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.mixin.render.TextureAtlasAccessor;

/** Publishes the two atlas-copy APIs with one asynchronous image/staging lifetime. */
public final class AtlasTextureReadback {
    private AtlasTextureReadback() {
    }

    public static FiguraTexture read(Avatar owner, String name, TextureAtlas atlas) {
        var source = atlas.getTexture();
        var dimensions = (TextureAtlasAccessor) atlas;
        int width = dimensions.figura$getWidth();
        int height = dimensions.figura$getHeight();
        int pixelStride = source.getFormat().blockSize();
        NativeImage image = new NativeImage(width, height, true);
        FiguraTexture texture;
        try {
            texture = owner.registerTexture(name, image, false);
        } catch (RuntimeException | Error failure) {
            image.close();
            throw failure;
        }

        FiguraTexture.Lease pending = texture.retain();
        GpuBuffer staging = null;
        try {
            var device = RenderSystem.getDevice();
            staging = device.createBuffer(() -> "Atlas Read Buffer",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * pixelStride);
            GpuBuffer readback = staging;
            device.createCommandEncoder().copyTextureToBuffer(source, readback, 0, () -> {
                try (pending; readback; GpuBufferSlice.MappedView view = readback.map(true, false)) {
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int pixel = view.data().getInt((x + y * width) * pixelStride);
                            image.setPixelABGR(x, height - y - 1, pixel | 0xFF000000);
                        }
                    }
                    // Consumers may already have uploaded the initial image while this copy waited.
                    texture.update();
                }
            }, 0);
            return texture;
        } catch (RuntimeException | Error failure) {
            if (staging != null) {
                staging.close();
            }
            pending.close();
            texture.close();
            throw failure;
        }
    }
}
