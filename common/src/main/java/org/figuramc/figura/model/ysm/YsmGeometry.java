package org.figuramc.figura.model.ysm;

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

    public record Quad(float[] positions, float[] uvs, float[] normal) {
    }
}
