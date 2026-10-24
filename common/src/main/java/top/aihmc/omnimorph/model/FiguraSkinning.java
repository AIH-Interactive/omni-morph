package top.aihmc.omnimorph.model;

import org.joml.Matrix4f;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.rendering.Vertex;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Blockbench armature skinning.
 * <p>
 * Blockbench binds a mesh to armature bones with per-vertex weights. The deformation of a vertex is
 * {@code sum(weight * boneWorld * inverseBindWorld) * restPosition}. The previous implementation only
 * composed animation deltas, which is wrong as soon as a bone has a non-identity rest rotation/offset.
 * <p>
 * The mesh's own rest transform is baked into its vertices on setup, so all skinning math happens in
 * the armature's coordinate space (the mesh becomes an identity part).
 */
public final class FiguraSkinning {

    private FiguraSkinning() {}

    /**
     * Resolves all skinned meshes of a freshly loaded model and captures the armature rest pose.
     */
    public static void setup(FiguraModelPart root) {
        for (FiguraModelPart part : traverse(root)) {
            if (part.skinBoneNames == null || part.skinBoneNames.length == 0)
                continue;
            if (part.skinBoneIndices == null || part.skinBoneWeights == null)
                continue;
            resolve(part);
        }
    }

    /**
     * Computes the current skinning matrices of a mesh, aligned with {@link FiguraModelPart#skinBoneNames}.
     * Entries without a resolved bone are identity matrices.
     *
     * @param mesh      skinned mesh part
     * @param overrides optional outfit bone overrides (same semantics as the renderer's animation deltas)
     * @return matrices, or null when the mesh is not skinned
     */
    public static @Nullable FiguraMat4[] deltas(FiguraModelPart mesh, @Nullable Map<FiguraModelPart, Matrix4f> overrides) {
        // Models normally resolve at load time. Resolve lazily as well so skinning data attached after
        // loading (tests and dynamically built parts) still works instead of silently rendering unskinned.
        if (mesh.skinBoneParts == null && mesh.skinBoneNames != null && mesh.skinBoneNames.length > 0
                && mesh.skinBoneIndices != null && mesh.skinBoneWeights != null) {
            resolve(mesh);
        }
        FiguraModelPart[] bones = mesh.skinBoneParts;
        FiguraMat4[] inverseBind = mesh.skinBindInverse;
        if (bones == null || inverseBind == null || bones.length == 0)
            return null;

        FiguraMat4[] deltas = new FiguraMat4[bones.length];
        boolean skinned = false;
        FiguraModelPart armature = mesh.skinArmature != null ? mesh.skinArmature : mesh.parent;

        for (int i = 0; i < bones.length; i++) {
            FiguraModelPart bone = bones[i];
            if (bone == null || inverseBind[i] == null) {
                deltas[i] = FiguraMat4.of();
                continue;
            }

            FiguraMat4 world = worldMatrix(bone, armature, true, overrides);
            // boneWorld * inverse(bindWorld)
            deltas[i] = world.copy().rightMultiply(inverseBind[i]);
            skinned = true;
        }

        return skinned ? deltas : null;
    }

    /**
     * Computes the world transform of a bone relative to an armature part.
     */
    public static FiguraMat4 worldMatrix(FiguraModelPart bone, @Nullable FiguraModelPart armature,
                                         boolean includeAnimation, @Nullable Map<FiguraModelPart, Matrix4f> overrides) {
        List<FiguraModelPart> chain = new ArrayList<>();
        FiguraModelPart current = bone;
        while (current != null && current != armature) {
            chain.add(current);
            current = current.parent;
        }
        Collections.reverse(chain);

        FiguraMat4 world = FiguraMat4.of();
        for (FiguraModelPart node : chain) {
            world.rightMultiply(localMatrix(node, includeAnimation, true));
            if (overrides != null) {
                Matrix4f override = overrides.get(node);
                if (override != null) {
                    // Outfit overrides are authored in armature space around the part pivot, so they apply
                    // after the part's own world transform rather than inside its local animation.
                    world.multiply(pivotedOverride(node, override));
                }
            }
        }
        return world;
    }

    private static void resolve(FiguraModelPart mesh) {
        if (mesh.skinBoneParts != null || mesh.skinBoneNames == null || mesh.skinBoneNames.length == 0
                || mesh.skinBoneIndices == null || mesh.skinBoneWeights == null) {
            return;
        }
        FiguraModelPart armature = findArmature(mesh);
        if (armature == null) {
            FiguraMod.LOGGER.warn("Could not find the armature for skinned mesh '{}'", mesh.name);
            return;
        }

        int count = mesh.skinBoneNames.length;
        FiguraModelPart[] boneParts = new FiguraModelPart[count];
        FiguraMat4[] inverseBind = new FiguraMat4[count];
        int resolved = 0;

        for (int i = 0; i < count; i++) {
            String boneName = mesh.skinBoneNames[i];
            if (boneName == null || boneName.isEmpty())
                continue;

            FiguraModelPart bone = findBone(armature, boneName);
            if (bone == null)
                continue;

            boneParts[i] = bone;
            inverseBind[i] = worldMatrix(bone, armature, false, null).copy().invert();
            resolved++;
        }

        if (resolved == 0) {
            FiguraMod.LOGGER.warn("Skinned mesh '{}' has no resolvable bones", mesh.name);
            return;
        }

        bakeMeshTransform(mesh, armature);

        mesh.skinBoneParts = boneParts;
        mesh.skinBindInverse = inverseBind;
        mesh.skinArmature = armature;
    }

    /**
     * Bakes the mesh's rest transform into its vertices. After this the mesh part is an identity part
     * and a vertex position is expressed directly in armature space.
     */
    private static void bakeMeshTransform(FiguraModelPart mesh, @Nullable FiguraModelPart armature) {
        FiguraMat4 rest = restTransform(mesh, armature);

        for (List<Vertex> vertices : mesh.vertices.values()) {
            for (Vertex vertex : vertices) {
                FiguraVec3 position = rest.apply((double) vertex.x, (double) vertex.y, (double) vertex.z);
                FiguraVec3 normal = rest.applyDir((double) vertex.nx, (double) vertex.ny, (double) vertex.nz);

                vertex.x = (float) position.x;
                vertex.y = (float) position.y;
                vertex.z = (float) position.z;

                if (normal.length() > 1e-6) {
                    normal.normalize();
                    vertex.nx = (float) normal.x;
                    vertex.ny = (float) normal.y;
                    vertex.nz = (float) normal.z;
                }
            }
        }

        PartCustomization customization = mesh.customization;
        customization.setPos(0, 0, 0);
        customization.setRot(0, 0, 0);
        customization.setScale(1, 1, 1);
        customization.setPivot(0, 0, 0);
        customization.offsetPos(0, 0, 0);
        customization.offsetRot(0, 0, 0);
        customization.offsetScale(1, 1, 1);
        customization.offsetPivot(0, 0, 0);
        customization.needsMatrixRecalculation = true;
    }

    /**
     * Composes the part matrices from the mesh up to the armature (exclusive).
     */
    private static FiguraMat4 restTransform(FiguraModelPart mesh, @Nullable FiguraModelPart armature) {
        List<FiguraModelPart> chain = new ArrayList<>();
        FiguraModelPart current = mesh;
        while (current != null && current != armature) {
            chain.add(current);
            current = current.parent;
        }

        FiguraMat4 result = FiguraMat4.of();
        for (FiguraModelPart node : chain) {
            // multiply() pre-multiplies, so walking bottom-up yields parent * child
            result.multiply(localMatrix(node, false, false));
        }
        return result;
    }

    /**
     * Mirrors {@link PartCustomization#recalculate()} and returns the local matrix of a part.
     *
     * @param asBone if true, converts the part matrix to Blockbench's bone matrix
     *               {@code translate(origin) * rotate(rotation)}
     */
    public static FiguraMat4 localMatrix(FiguraModelPart part, boolean includeAnimation, boolean asBone) {
        PartCustomization customization = part.customization;

        FiguraVec3 pivot = customization.getPivot();
        FiguraVec3 offsetPivot = customization.getOffsetPivot();
        FiguraVec3 offsetPos = customization.getOffsetPos();
        FiguraVec3 position = customization.getPos();
        FiguraVec3 rotation = customization.getRot().add(customization.getOffsetRot());
        FiguraVec3 scale = customization.getScale().multiply(customization.getOffsetScale());

        if (includeAnimation) {
            position.add(customization.getAnimPos());
            rotation.add(customization.getAnimRot());
            scale.multiply(customization.getAnimScale());
        }

        FiguraMat4 matrix = FiguraMat4.of();
        matrix.translate(
                offsetPos.x - pivot.x - offsetPivot.x,
                offsetPos.y - pivot.y - offsetPivot.y,
                offsetPos.z - pivot.z - offsetPivot.z
        );
        matrix.scale(scale.x, scale.y, scale.z);

        if (customization.partType == PartCustomization.PartType.MESH) {
            matrix.rotateZ(rotation.z);
            matrix.rotateY(rotation.y);
            matrix.rotateX(rotation.x);
        } else {
            matrix.rotateZYX(rotation.x, rotation.y, rotation.z);
        }

        matrix.translate(
                position.x + pivot.x + offsetPivot.x,
                position.y + pivot.y + offsetPivot.y,
                position.z + pivot.z + offsetPivot.z
        );

        if (asBone) {
            // Part matrices rotate around the pivot; bones are translate(origin) * rotate(rotation)
            matrix.translateFirst(pivot.x + offsetPivot.x, pivot.y + offsetPivot.y, pivot.z + offsetPivot.z);
        }

        return matrix;
    }

    private static FiguraMat4 pivotedOverride(FiguraModelPart part, Matrix4f override) {
        FiguraVec3 pivot = part.customization.getPivot();
        return FiguraMat4.of().set(new Matrix4f()
                .translation((float) pivot.x, (float) pivot.y, (float) pivot.z)
                .mul(override)
                .translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z));
    }

    private static @Nullable FiguraModelPart findArmature(FiguraModelPart mesh) {
        FiguraModelPart part = mesh.parent;
        while (part != null) {
            for (String boneName : mesh.skinBoneNames) {
                if (boneName != null && findBone(part, boneName) != null)
                    return part;
            }
            part = part.parent;
        }
        return mesh.parent;
    }

    private static @Nullable FiguraModelPart findBone(FiguraModelPart root, String name) {
        FiguraModelPart fallback = null;
        Deque<FiguraModelPart> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            FiguraModelPart part = queue.poll();
            if (name.equals(part.name)) {
                if (part.isBone)
                    return part;
                if (fallback == null)
                    fallback = part;
            }
            queue.addAll(part.children);
        }
        return fallback;
    }

    private static List<FiguraModelPart> traverse(FiguraModelPart root) {
        List<FiguraModelPart> parts = new ArrayList<>();
        Deque<FiguraModelPart> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            FiguraModelPart part = queue.poll();
            parts.add(part);
            queue.addAll(part.children);
        }
        return parts;
    }
}
