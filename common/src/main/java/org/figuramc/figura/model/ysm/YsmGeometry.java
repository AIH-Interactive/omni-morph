package org.figuramc.figura.model.ysm;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YsmGeometry {
    public int textureWidth = 64;
    public int textureHeight = 64;
    public final Map<String, Bone> bones = new LinkedHashMap<>();
    public final List<Bone> roots = new ArrayList<>();

    public static class Bone {
        public final String name;
        public final String parentName;
        public final float[] pivot;
        public final float[] rotation;
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();
        public boolean visible = true;

        public Bone(String name, String parentName, float[] pivot, float[] rotation) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.rotation = rotation;
        }
    }

    public static class Cube {
        public final List<Quad> quads;

        public Cube(List<Quad> quads) {
            this.quads = quads;
        }
    }

    public boolean applyTextureAlpha(byte[] textureBytes) {
        for (Bone bone : bones.values()) {
            for (Cube cube : bone.cubes) {
                for (Quad quad : cube.quads)
                    quad.setAlphaState(true, false);
            }
        }

        if (textureBytes == null || textureBytes.length == 0)
            return false;

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(textureBytes));
            if (image == null || !image.getColorModel().hasAlpha())
                return false;

            boolean coloredTranslucent = false;
            for (Bone bone : bones.values()) {
                for (Cube cube : bone.cubes) {
                    for (Quad quad : cube.quads)
                        coloredTranslucent |= scanQuadAlpha(quad, image);
                }
            }
            return coloredTranslucent;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean scanQuadAlpha(Quad quad, BufferedImage image) {
        float[] uvs = quad.uvs();
        if (uvs == null || uvs.length < 8)
            return false;

        float minU = uvs[0], maxU = uvs[0];
        float minV = uvs[1], maxV = uvs[1];
        for (int i = 2; i < uvs.length; i += 2) {
            minU = Math.min(minU, uvs[i]);
            maxU = Math.max(maxU, uvs[i]);
            minV = Math.min(minV, uvs[i + 1]);
            maxV = Math.max(maxV, uvs[i + 1]);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0)
            return false;

        int startX = clamp((int) Math.floor(minU * width + 0.01f), 0, width - 1);
        int endX = clamp((int) Math.floor(maxU * width - 0.01f), 0, width - 1);
        int startY = clamp((int) Math.floor(minV * height + 0.01f), 0, height - 1);
        int endY = clamp((int) Math.floor(maxV * height - 0.01f), 0, height - 1);
        if (endX < startX)
            endX = startX;
        if (endY < startY)
            endY = startY;

        boolean visible = false;
        boolean translucent = false;
        boolean coloredTranslucent = false;
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha > 0)
                    visible = true;
                if (alpha < 255)
                    translucent = true;
                if (alpha > 0 && alpha < 255 && (argb & 0x00ffffff) != 0)
                    coloredTranslucent = true;
                if (visible && translucent && coloredTranslucent) {
                    quad.setAlphaState(true, true);
                    return true;
                }
            }
        }
        quad.setAlphaState(visible, visible && translucent);
        return coloredTranslucent;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public static class Quad {
        private final float[] positions;
        private final float[] uvs;
        private final float[] normal;
        private boolean visible = true;
        private boolean translucent;

        public Quad(float[] positions, float[] uvs, float[] normal) {
            this.positions = positions;
            this.uvs = uvs;
            this.normal = normal;
        }

        public float[] positions() {
            return positions;
        }

        public float[] uvs() {
            return uvs;
        }

        public float[] normal() {
            return normal;
        }

        public boolean visible() {
            return visible;
        }

        public boolean translucent() {
            return translucent;
        }

        private void setAlphaState(boolean visible, boolean translucent) {
            this.visible = visible;
            this.translucent = translucent;
        }
    }
}
