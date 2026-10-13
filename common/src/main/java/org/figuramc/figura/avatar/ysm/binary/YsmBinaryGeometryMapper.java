package org.figuramc.figura.avatar.ysm.binary;

import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;
import org.figuramc.figura.model.ysm.YsmGeometry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Copies baked faces directly; only the raw bone rotation unit differs from JSON geometry. */
public final class YsmBinaryGeometryMapper {
    private YsmBinaryGeometryMapper() {
    }

    public static YsmGeometry map(YsmBinaryModel.RawGeometry raw, YsmDecodeBudget budget) throws IOException {
        if (raw == null) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "geometry/missing", 0);
        }
        budget.length(raw.bones.size(), 4096, "geometry/bones", 0);
        YsmGeometry geometry = new YsmGeometry();
        geometry.textureWidth = textureDimension(raw.textureWidth);
        geometry.textureHeight = textureDimension(raw.textureHeight);
        int faces = 0;
        for (YsmBinaryModel.RawBone bone : raw.bones) {
            budget.check("geometry/bone", 0);
            if (bone.name == null || bone.name.isBlank() || geometry.bones.containsKey(bone.name)) {
                throw invalid("geometry/bone-name");
            }
            float[] pivot = vector(bone.pivot);
            float[] rotation = vector(bone.rotation);
            for (int axis = 0; axis < rotation.length; axis++) {
                rotation[axis] = (float) Math.toDegrees(rotation[axis]);
                if (!Float.isFinite(rotation[axis])) {
                    throw invalid("geometry/rotation");
                }
            }
            YsmGeometry.Bone target = new YsmGeometry.Bone(bone.name,
                    bone.parentName == null ? "" : bone.parentName, pivot, rotation);
            for (YsmBinaryModel.RawCube cube : bone.cubes) {
                faces = budget.length((long) faces + cube.faces.size(), 262144, "geometry/faces", 0);
                List<YsmGeometry.Quad> quads = new ArrayList<>(cube.faces.size());
                for (YsmBinaryModel.RawFace face : cube.faces) {
                    budget.check("geometry/face", 0);
                    if (face.positions.length != 4 || face.u.length != 4 || face.v.length != 4) {
                        throw invalid("geometry/face-size");
                    }
                    float[] positions = new float[12];
                    float[] uvs = new float[8];
                    for (int vertex = 0; vertex < 4; vertex++) {
                        System.arraycopy(vector(face.positions[vertex]), 0, positions, vertex * 3, 3);
                        if (!Float.isFinite(face.u[vertex]) || !Float.isFinite(face.v[vertex])) {
                            throw invalid("geometry/uv");
                        }
                        uvs[vertex * 2] = face.u[vertex];
                        uvs[vertex * 2 + 1] = face.v[vertex];
                    }
                    quads.add(new YsmGeometry.Quad(positions, uvs, vector(face.normal)));
                }
                target.cubes.add(new YsmGeometry.Cube(quads));
            }
            geometry.bones.put(target.name, target);
        }
        validateParents(geometry, budget);
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmGeometry.Bone parent = geometry.bones.get(bone.parentName);
            if (parent == null) {
                geometry.roots.add(bone);
            } else {
                parent.children.add(bone);
            }
        }
        return geometry;
    }

    private static void validateParents(YsmGeometry geometry, YsmDecodeBudget budget) throws IOException {
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            Set<String> seen = new HashSet<>();
            YsmGeometry.Bone cursor = bone;
            while (cursor != null) {
                budget.check("geometry/parents", 0);
                if (!seen.add(cursor.name)) {
                    throw invalid("geometry/parent-cycle");
                }
                budget.length(seen.size(), 128, "geometry/parent-depth", 0);
                if (!cursor.parentName.isBlank() && !geometry.bones.containsKey(cursor.parentName)) {
                    throw invalid("geometry/missing-parent");
                }
                cursor = geometry.bones.get(cursor.parentName);
            }
        }
    }

    private static float[] vector(float[] values) throws IOException {
        if (values == null || values.length != 3) {
            throw invalid("geometry/vector-size");
        }
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw invalid("geometry/vector-value");
            }
        }
        return values.clone();
    }

    private static int textureDimension(float value) throws IOException {
        if (!Float.isFinite(value) || value <= 0 || value > YsmDecodeBudget.MAX_TEXTURE_PIXELS || value != Math.rint(value)) {
            throw invalid("geometry/texture-size");
        }
        return (int) value;
    }

    private static YsmDecodeException invalid(String stage) {
        return new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage, 0);
    }
}
