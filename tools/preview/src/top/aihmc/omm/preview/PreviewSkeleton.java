package top.aihmc.omm.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omnimorph.model.ysm.YsmGeometry;

/** Bind metadata shared by instances; each instance owns its animated matrices and sampled poses. */
final class PreviewSkeleton {
    final String[] names;
    final int[] parents;
    final int[] order;
    final Vector3f[] pivots;
    final Vector3f[] rotations;
    final Matrix4f[] inverseBindWorld;
    final boolean[] defaultVisible;
    final boolean[] restVisible;

    PreviewSkeleton(PreviewSkeleton base, PreviewSkeleton extra) {
        int offset = base.names.length, count = offset + extra.names.length;
        names = java.util.Arrays.copyOf(base.names, count);
        parents = java.util.Arrays.copyOf(base.parents, count);
        order = java.util.Arrays.copyOf(base.order, count);
        pivots = java.util.Arrays.copyOf(base.pivots, count);
        rotations = java.util.Arrays.copyOf(base.rotations, count);
        inverseBindWorld = java.util.Arrays.copyOf(base.inverseBindWorld, count);
        defaultVisible = java.util.Arrays.copyOf(base.defaultVisible, count);
        restVisible = java.util.Arrays.copyOf(base.restVisible, count);
        for (int index = 0; index < extra.names.length; index++) {
            int target = offset + index;
            names[target] = extra.names[index];
            parents[target] = extra.parents[index] < 0 ? -1 : offset + extra.parents[index];
            order[target] = offset + extra.order[index];
            pivots[target] = extra.pivots[index];
            rotations[target] = extra.rotations[index];
            inverseBindWorld[target] = extra.inverseBindWorld[index];
            defaultVisible[target] = extra.defaultVisible[index];
            restVisible[target] = extra.restVisible[index];
        }
    }

    PreviewSkeleton(PreviewFiguraParts parts) {
        int count = parts.nodes.size();
        names = new String[count];
        parents = new int[count];
        order = new int[count];
        pivots = new Vector3f[count];
        rotations = new Vector3f[count];
        inverseBindWorld = new Matrix4f[count];
        defaultVisible = new boolean[count];
        restVisible = new boolean[count];
        for (int index = 0; index < count; index++) {
            var node = parts.nodes.get(index);
            names[index] = node.path();
            parents[index] = node.parent();
            order[index] = index;
            pivots[index] = new Vector3f(node.pivot());
            rotations[index] = new Vector3f(node.rotation());
            inverseBindWorld[index] = new Matrix4f(node.bind()).invert();
            defaultVisible[index] = node.visible();
            restVisible[index] = node.visible() && (node.parent() < 0 || restVisible[node.parent()]);
        }
    }

    PreviewSkeleton(YsmGeometry geometry, Map<String, Matrix4f> bindWorld, java.util.Set<String> hidden) {
        names = geometry.bones.keySet().toArray(String[] ::new);
        parents = new int[names.length];
        pivots = new Vector3f[names.length];
        rotations = new Vector3f[names.length];
        inverseBindWorld = new Matrix4f[names.length];
        defaultVisible = new boolean[names.length];
        restVisible = new boolean[names.length];
        Map<String, Integer> indices = new HashMap<>();
        for (int index = 0; index < names.length; index++) {
            indices.put(names[index], index);
        }
        for (int index = 0; index < names.length; index++) {
            var bone = geometry.bones.get(names[index]);
            parents[index] = bone.parentName == null || bone.parentName.isBlank() ? -1 : indices.get(bone.parentName);
            pivots[index] = new Vector3f(bone.pivot[0], bone.pivot[1], bone.pivot[2]).div(16);
            rotations[index] = new Vector3f(bone.rotation[0], bone.rotation[1], bone.rotation[2]);
            inverseBindWorld[index] = new Matrix4f(bindWorld.get(names[index])).invert();
            defaultVisible[index] = !hidden.contains(names[index].toLowerCase(java.util.Locale.ROOT));
        }
        var sorted = new ArrayList<Integer>();
        boolean[] added = new boolean[names.length];
        for (int index = 0; index < names.length; index++) {
            append(index, added, sorted);
        }
        order = sorted.stream().mapToInt(Integer::intValue).toArray();
        for (int index : order) {
            restVisible[index] = defaultVisible[index] && (parents[index] < 0 || restVisible[parents[index]]);
        }
    }

    private void append(int index, boolean[] added, ArrayList<Integer> sorted) {
        if (added[index]) {
            return;
        }
        if (parents[index] >= 0) {
            append(parents[index], added, sorted);
        }
        sorted.add(index);
        added[index] = true;
    }
}
