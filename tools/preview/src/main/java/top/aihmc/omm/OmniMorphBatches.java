package top.aihmc.omm;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Adapts the original bone transforms and mesh cache into original draw batches. */
final class OmniMorphBatches implements AutoCloseable {
    private static final String ACCELERATION = "top.aihmc.omnimorph.render.acceleration.";
    private final Object owner;
    private final Object lease;
    private final Object renderer;
    private final Object meshes;
    private final Object stack;
    private final Class<?> poseType;
    private final Class<?> batchType;
    private final Class<?> meshType;
    private final Method meshLookup;
    private final Method applyBone;
    private final Object geometry;
    private final Object runtime;
    private final RenderInputs inputs;
    private Object semantics;
    private Object hiddenSemantics;
    private SceneLayout.Placement placement;
    private final List<Object> batches = new ArrayList<>();

    OmniMorphBatches(Object geometry) throws Exception {
        this(geometry, null);
    }

    OmniMorphBatches(Object geometry, Object runtime) throws Exception {
        this.geometry = geometry;
        this.runtime = runtime;
        inputs = runtime == null ? null : RenderInputs.read();
        Class<?> ownerType = Class.forName(ACCELERATION + "AccelerationOwner");
        owner = ownerType.getConstructor().newInstance();
        Class<?> serviceType = Class.forName(ACCELERATION + "AccelerationService");
        Object service = serviceType.getConstructor().newInstance();
        lease = serviceType.getMethod("capture", ownerType, String.class).invoke(service, owner, "preview");
        poseType = Class.forName(ACCELERATION + "PoseSnapshot");
        batchType = Class.forName(ACCELERATION + "DrawBatch");
        meshType = Class.forName(ACCELERATION + "MeshHandle");
        Class<?> rendererType = Class.forName("top.aihmc.omnimorph.model.ysm.YsmRenderer");
        Class<?> runtimeType = Class.forName("top.aihmc.omnimorph.model.ysm.YsmModelRuntime");
        // The original transform helper only uses the supplied bone/part/stack.
        renderer = rendererType.getConstructor(runtimeType).newInstance(runtime);
        var meshField = rendererType.getDeclaredField("accelerationMeshes");
        meshField.setAccessible(true);
        meshes = meshField.get(renderer);
        meshLookup = meshes.getClass().getDeclaredMethod("get", List.class);
        meshLookup.setAccessible(true);
        Class<?> stackType = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        stack = stackType.getConstructor().newInstance();
        applyBone = rendererType.getDeclaredMethod("applyBoneTransform",
            Class.forName("top.aihmc.omnimorph.model.ysm.YsmGeometry$Bone"),
            Class.forName("top.aihmc.omnimorph.model.ysm.YsmModelPart"), stackType, boolean.class);
        applyBone.setAccessible(true);
        refresh();
    }

    void refresh() throws Exception {
        batches.clear();
        semantics = runtime == null ? null : materialSemantics(true);
        hiddenSemantics = runtime == null ? null : materialSemantics(false);
        stack.getClass().getMethod("pushPose").invoke(stack);
        try {
            if (placement != null) {
                stack.getClass().getMethod("translate", float.class, float.class, float.class)
                    .invoke(stack, placement.x(), placement.y(), placement.z());
            }
            for (Object root : (List<?>) geometry.getClass().getField("roots").get(geometry)) {
                collect(root, true);
            }
        } finally {
            stack.getClass().getMethod("popPose").invoke(stack);
        }
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("Model contains no drawable original meshes");
        }
    }

    void placement(SceneLayout.Placement placement) throws Exception {
        this.placement = placement;
        refresh();
    }

    private void collect(Object bone, boolean parentVisible) throws Exception {
        if (runtime == null && !bone.getClass().getField("visible").getBoolean(bone)) {
            return;
        }
        stack.getClass().getMethod("pushPose").invoke(stack);
        try {
            Object part = runtime == null ? null : runtime.getClass().getMethod("getPart", String.class)
                .invoke(runtime, bone.getClass().getField("name").get(bone));
            boolean visible = parentVisible && (part == null
                || (boolean) part.getClass().getMethod("visibleRaw").invoke(part)
                && !(boolean) part.getClass().getMethod("hiddenScaleRaw").invoke(part));
            // Keep batch/texture slots stable; hidden branches are never submitted.
            // The original renderer returns before applying a hidden zero-scale transform.
            if (visible) {
                applyBone.invoke(renderer, bone, part, stack, false);
            }
            Object floatPose = stack.getClass().getMethod("last").invoke(stack);
            Object pose = poseType.getConstructor(floatPose.getClass()).newInstance(floatPose);
            List<?> cubes = (List<?>) bone.getClass().getField("cubes").get(bone);
            for (Object mesh : (List<?>) meshLookup.invoke(meshes, cubes)) {
                Object drawSemantics = visible ? semantics : hiddenSemantics;
                Object batch = semantics == null
                    ? batchType.getConstructor(lease.getClass(), meshType, poseType).newInstance(lease, mesh, pose)
                    : batchType.getConstructor(lease.getClass(), meshType, poseType, semantics.getClass())
                        .newInstance(lease, mesh, pose, drawSemantics);
                batches.add(batch);
            }
            for (Object child : (List<?>) bone.getClass().getField("children").get(bone)) {
                collect(child, visible);
            }
        } finally {
            stack.getClass().getMethod("popPose").invoke(stack);
        }
    }

    List<double[]> transform(boolean nativeSimd) throws Exception {
        return transform(batches, nativeSimd);
    }

    private Object materialSemantics(boolean visible) throws Exception {
        Method renderType = renderer.getClass().getDeclaredMethod("renderType");
        renderType.setAccessible(true);
        Object material = renderType.invoke(renderer);
        Object texture = runtime.getClass().getMethod("texture").invoke(runtime);
        Object identifier = texture.getClass().getMethod("getLocation").invoke(texture);
        Class<?> type = Class.forName(ACCELERATION + "DrawSemantics");
        Class<?> layer = Class.forName(ACCELERATION + "DrawSemantics$Layer");
        int order = (int) renderer.getClass().getMethod("bodySubmitOrder").invoke(renderer);
        return type.getConstructor(Class.forName("net.minecraft.client.renderer.rendertype.RenderType"),
            Class.forName("net.minecraft.resources.Identifier"), layer, String.class,
            int.class, int.class, int.class, int.class, float.class, float.class, float.class,
            float.class, double.class, boolean.class).newInstance(
                material, identifier, layer.getField("PRIMARY").get(null), "body", order, 0,
                inputs.packedLight(), inputs.packedOverlay(), inputs.tint().x, inputs.tint().y,
                inputs.tint().z, inputs.tint().w, 0d, visible);
    }

    List<Object> drawBatches() {
        return List.copyOf(batches);
    }

    static List<double[]> transform(List<Object> batches, boolean nativeSimd) throws Exception {
        return transform(batches, backend(nativeSimd));
    }

    static Object backend(boolean nativeSimd) throws Exception {
        if (nativeSimd) {
            Class<?> loader = Class.forName(ACCELERATION + "NativeLibraryLoader");
            Method load = loader.getDeclaredMethod("load", ClassLoader.class, Path.class);
            load.setAccessible(true);
            Object loaded = load.invoke(null, loader.getClassLoader(),
                Path.of("build/preview/jar-host/native/omp").toAbsolutePath());
            System.out.println("OMP ORIGINAL NATIVE " + loaded);
        }
        Class<?> backendType = Class.forName(ACCELERATION
            + (nativeSimd ? "NativeSimdBackend" : "JavaAccelerationBackend"));
        return backendType.getConstructor().newInstance();
    }

    static List<double[]> transform(List<Object> batches, Object backend) throws Exception {
        Class<?> batchType = Class.forName(ACCELERATION + "DrawBatch");
        Class<?> backendType = backend.getClass();
        Method transform = backendType.getMethod("transform", batchType);
        List<double[]> results = new ArrayList<>();
        for (Object batch : batches) {
            double[] vertices = (double[]) transform.invoke(backend, batch);
            for (double value : vertices) {
                if (!Double.isFinite(value)) {
                    throw new AssertionError("Non-finite original backend output");
                }
            }
            results.add(vertices);
        }
        return results;
    }

    List<int[]> triangleIndices() throws Exception {
        List<int[]> indices = new ArrayList<>();
        for (Object batch : batches) {
            Object mesh = batchType.getMethod("mesh").invoke(batch);
            indices.add((int[]) meshType.getMethod("copyTriangleIndices").invoke(mesh));
        }
        return indices;
    }

    @Override
    public void close() throws Exception {
        Method clear = meshes.getClass().getDeclaredMethod("clear");
        clear.setAccessible(true);
        clear.invoke(meshes);
        owner.getClass().getMethod("retire").invoke(owner);
    }
}
