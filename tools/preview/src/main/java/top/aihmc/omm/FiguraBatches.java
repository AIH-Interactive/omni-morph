package top.aihmc.omm;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adapts the original Figura mesh cache and customization stack for rest-pose backend checks. */
final class FiguraBatches implements AutoCloseable {
    private static final String ACCELERATION = "top.aihmc.omnimorph.render.acceleration.";
    private final Object owner;
    private final Object lease;
    private final Object cache;
    private final Object stack;
    private final Class<?> customizationType;
    private final Method meshLookup;
    private final List<Object> batches = new ArrayList<>();
    private final List<Object> textures = new ArrayList<>();
    private final Object root;
    private final FiguraMaterials materials;
    private final Object renderer;
    private final Method skinDeltas;
    private final Method skinnedMesh;
    private final Map<Boolean, Object> backends = new java.util.HashMap<>();
    private SceneLayout.Placement placement;

    FiguraBatches(Object renderer) throws Exception {
        this.renderer = renderer;
        materials = new FiguraMaterials(renderer);
        Class<?> partType = Class.forName("org.figuramc.figura.model.FiguraModelPart");
        skinDeltas = renderer.getClass().getDeclaredMethod("collectBoneSkinningDeltas", partType);
        skinDeltas.setAccessible(true);
        skinnedMesh = renderer.getClass().getDeclaredMethod("skinnedMesh", List.class, int.class,
            Class.forName("org.figuramc.figura.math.vector.FiguraVec3"), partType);
        skinnedMesh.setAccessible(true);
        Class<?> ownerType = Class.forName(ACCELERATION + "AccelerationOwner");
        owner = ownerType.getConstructor().newInstance();
        Class<?> serviceType = Class.forName(ACCELERATION + "AccelerationService");
        Object service = serviceType.getConstructor().newInstance();
        lease = serviceType.getMethod("capture", ownerType, String.class).invoke(service, owner, "figura-preview");
        var cacheField = renderer.getClass().getDeclaredField("staticMeshes");
        cacheField.setAccessible(true);
        cache = cacheField.get(renderer);
        meshLookup = cache.getClass().getDeclaredMethod("mesh", List.class, int.class, double.class, double.class);
        meshLookup.setAccessible(true);
        customizationType = Class.forName("org.figuramc.figura.model.PartCustomization");
        stack = Class.forName("org.figuramc.figura.model.PartCustomization$PartCustomizationStack")
            .getConstructor().newInstance();
        root = renderer.getClass().getField("root").get(renderer);
        refresh();
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("Original Figura model contains no visible rest-pose meshes");
        }
    }

    private void collect(Object part, boolean parentVisible) throws Exception {
        Class<?> partType = part.getClass();
        Object customization = partType.getField("customization").get(part);
        boolean visible = parentVisible && customizationType.getField("visible").getBoolean(customization);
        customizationType.getMethod("recalculate").invoke(customization);
        stack.getClass().getMethod("push", customizationType).invoke(stack, customization);
        try {
            Object combined = stack.getClass().getMethod("peek").invoke(stack);
            Class<?> matrix4 = Class.forName("org.figuramc.figura.math.matrix.FiguraMat4");
            Class<?> matrix3 = Class.forName("org.figuramc.figura.math.matrix.FiguraMat3");
            Class<?> poseType = Class.forName(ACCELERATION + "PoseSnapshot");
            Object deltas = null;
            String[] boneNames = (String[]) partType.getField("skinBoneNames").get(part);
            if (boneNames != null && boneNames.length > 0 && partType.getField("parent").get(part) != null) {
                deltas = skinDeltas.invoke(renderer, part);
            }
            boolean skinned = deltas != null && Array.getLength(deltas) > 0
                && partType.getField("skinBoneIndices").get(part) != null
                && partType.getField("skinBoneWeights").get(part) != null;
            Object position = matrix4.getMethod("copy").invoke(customizationType.getField("positionMatrix").get(combined));
            if (placement != null) {
                matrix4.getMethod("translate", double.class, double.class, double.class)
                    .invoke(position, (double) placement.x(), (double) placement.y(), (double) placement.z());
            }
            Object pose = poseType.getConstructor(matrix4, matrix3, matrix3, Array.newInstance(matrix4, 0).getClass())
                .newInstance(position,
                    customizationType.getField("normalMatrix").get(combined),
                    customizationType.getField("uvMatrix").get(combined), deltas);
            Map<?, ?> vertices = (Map<?, ?>) partType.getField("vertices").get(part);
            for (var entry : vertices.entrySet()) {
                List<?> source = (List<?>) entry.getValue();
                if (source.isEmpty()) {
                    continue;
                }
                Object textureSet = ((List<?>) partType.getField("textures").get(part)).get((Integer) entry.getKey());
                double width = ((Number) textureSet.getClass().getMethod("getWidth").invoke(textureSet)).doubleValue();
                double height = ((Number) textureSet.getClass().getMethod("getHeight").invoke(textureSet)).doubleValue();
                Object mesh;
                if (skinned) {
                    Object uvSize = Class.forName("org.figuramc.figura.math.vector.FiguraVec3")
                        .getMethod("of", double.class, double.class, double.class).invoke(null, width, height, 1d);
                    mesh = skinnedMesh.invoke(null, source, source.size(), uvSize, part);
                } else {
                    mesh = meshLookup.invoke(cache, source, source.size(), width, height);
                }
                Class<?> batchType = Class.forName(ACCELERATION + "DrawBatch");
                for (boolean primary : new boolean[] {true, false}) {
                    var material = materials.resolve(combined, textureSet, primary, source.size() / 4, visible);
                    if (material != null) {
                        batches.add(batchType.getConstructor(lease.getClass(),
                            Class.forName(ACCELERATION + "MeshHandle"), poseType, material.semantics().getClass())
                            .newInstance(lease, mesh, pose, material.semantics()));
                        textures.add(material.texture());
                    }
                }
            }
            for (Object child : (List<?>) partType.getField("children").get(part)) {
                collect(child, visible);
            }
        } finally {
            stack.getClass().getMethod("pop").invoke(stack);
        }
    }

    List<double[]> transform(boolean nativeSimd) throws Exception {
        Object backend = backends.get(nativeSimd);
        if (backend == null) {
            backend = OmniMorphBatches.backend(nativeSimd);
            backends.put(nativeSimd, backend);
        }
        return OmniMorphBatches.transform(batches, backend);
    }

    void placement(SceneLayout.Placement value) throws Exception {
        placement = value;
        refresh();
    }

    void refresh() throws Exception {
        batches.clear();
        textures.clear();
        stack.getClass().getMethod("push", customizationType).invoke(stack, materials.defaults());
        try {
            collect(root, true);
        } finally {
            stack.getClass().getMethod("pop").invoke(stack);
        }
    }

    List<Object> drawBatches() {
        return List.copyOf(batches);
    }

    List<int[]> triangleIndices() throws Exception {
        List<int[]> result = new ArrayList<>();
        for (Object batch : batches) {
            Object mesh = batch.getClass().getMethod("mesh").invoke(batch);
            result.add((int[]) mesh.getClass().getMethod("copyTriangleIndices").invoke(mesh));
        }
        return result;
    }

    List<byte[]> textureBytes() throws Exception {
        var directory = java.nio.file.Path.of(System.getProperty("omm.preview.output"), "figura-textures");
        java.nio.file.Files.createDirectories(directory);
        Map<Object, byte[]> encoded = new java.util.IdentityHashMap<>();
        List<byte[]> result = new ArrayList<>();
        for (Object texture : textures) {
            if (texture == null) {
                throw new UnsupportedOperationException("Figura texture set has no primary texture");
            }
            byte[] bytes = encoded.get(texture);
            if (bytes == null) {
                var file = directory.resolve("texture-" + encoded.size() + ".png");
                texture.getClass().getMethod("writeTexture", java.nio.file.Path.class).invoke(texture, file);
                bytes = java.nio.file.Files.readAllBytes(file);
                encoded.put(texture, bytes);
            }
            result.add(bytes);
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        owner.getClass().getMethod("retire").invoke(owner);
    }
}
