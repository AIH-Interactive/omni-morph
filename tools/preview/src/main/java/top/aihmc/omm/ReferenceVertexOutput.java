package top.aihmc.omm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Receives original MOD renderer output through its Minecraft VertexConsumer contract. */
final class ReferenceVertexOutput {
    private final List<float[]> positions = new ArrayList<>();
    private final List<double[]> vertices = new ArrayList<>();
    private final List<float[]> colors = new ArrayList<>();
    private final List<int[]> lightingCoordinates = new ArrayList<>();
    private final boolean spm;
    private final RenderInputs inputs;
    private boolean lightingReported;
    private org.joml.Matrix4f windowCamera;
    private final org.joml.Vector3f translation = new org.joml.Vector3f();

    ReferenceVertexOutput(String backend) throws Exception {
        spm = backend.equals("spm");
        inputs = RenderInputs.read();
    }

    void camera(org.joml.Matrix4f camera) {
        windowCamera = new org.joml.Matrix4f(camera);
    }

    void placement(SceneLayout.Placement placement) {
        translation.set(placement.x(), placement.y(), placement.z());
    }

    List<float[]> render(Object model, boolean nativeSimd) throws Exception {
        return render(model, nativeSimd, (float[]) model.getClass().getMethod("getBoneTransformData").invoke(model));
    }

    List<float[]> render(Object model, boolean nativeSimd, float[] parameters) throws Exception {
        positions.clear();
        vertices.clear();
        colors.clear();
        lightingCoordinates.clear();
        Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
        if (!(boolean) renderSystem.getMethod("isOnRenderThread").invoke(null)) {
            renderSystem.getMethod("initRenderThread").invoke(null);
        }
        Class<?> consumerType = Class.forName("com.mojang.blaze3d.vertex.VertexConsumer");
        Object consumer = Proxy.newProxyInstance(consumerType.getClassLoader(), new Class<?>[]{consumerType},
            (proxy, method, args) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                if ((spm && method.getName().equals("addVertex") && args.length == 3)
                    || (!spm && method.getName().equals("m_5483_"))) {
                    float[] position = new float[]{((Number) args[0]).floatValue(),
                        ((Number) args[1]).floatValue(), ((Number) args[2]).floatValue()};
                    for (float component : position) {
                        if (!Float.isFinite(component)) {
                            throw new AssertionError("Non-finite original renderer output");
                        }
                    }
                    positions.add(position);
                    vertices.add(new double[]{position[0], position[1], position[2], 1, 0, 0, 0, 0, 0});
                    colors.add(new float[]{1, 1, 1, 1});
                    lightingCoordinates.add(new int[]{inputs.packedLight() & 65535, inputs.packedLight() >>> 16,
                        inputs.packedOverlay() & 65535, inputs.packedOverlay() >>> 16});
                } else if (method.getName().equals(spm ? "setUv1" : "m_7122_")) {
                    int[] coordinates = lightingCoordinates.getLast();
                    coordinates[2] = ((Number) args[0]).intValue();
                    coordinates[3] = ((Number) args[1]).intValue();
                } else if (method.getName().equals(spm ? "setUv2" : "m_7120_")) {
                    int[] coordinates = lightingCoordinates.getLast();
                    coordinates[0] = ((Number) args[0]).intValue();
                    coordinates[1] = ((Number) args[1]).intValue();
                } else if (method.getName().equals(spm ? "setColor" : "m_6122_")) {
                    float[] color = colors.getLast();
                    if (args.length == 1) {
                        int packed = ((Number) args[0]).intValue();
                        color[0] = ((packed >>> 16) & 255) / 255f;
                        color[1] = ((packed >>> 8) & 255) / 255f;
                        color[2] = (packed & 255) / 255f;
                        color[3] = ((packed >>> 24) & 255) / 255f;
                    } else {
                        for (int channel = 0; channel < 4; channel++) {
                            color[channel] = ((Number) args[channel]).floatValue() / 255f;
                        }
                    }
                } else if (method.getName().equals(spm ? "setUv" : "m_7421_")) {
                    double[] vertex = vertices.getLast();
                    vertex[7] = ((Number) args[0]).doubleValue();
                    vertex[8] = ((Number) args[1]).doubleValue();
                } else if (method.getName().equals(spm ? "setNormal" : "m_5601_")) {
                    double[] vertex = vertices.getLast();
                    for (int axis = 0; axis < 3; axis++) {
                        vertex[4 + axis] = ((Number) args[axis]).doubleValue();
                    }
                }
                if (method.getReturnType() == consumerType) {
                    return proxy;
                }
                if (method.getReturnType() == void.class) {
                    return null;
                }
                throw new UnsupportedOperationException(method.toString());
            });
        Class<?> stackType = Class.forName("com.mojang.blaze3d.vertex.PoseStack");
        Object stack = stackType.getConstructor().newInstance();
        Object pose = stackType.getMethod(spm ? "last" : "m_85850_").invoke(stack);
        Class<?> matrixType = Class.forName("org.joml.Matrix4f");
        Object matrix = matrixType.getConstructor().newInstance();
        org.joml.Matrix4f savedProjection = null;
        org.joml.Matrix4f projection = null;
        var config = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            java.nio.file.Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
        if (windowCamera != null) {
            ((org.joml.Matrix4f) matrix).set(windowCamera).translate(translation);
            ((org.joml.Matrix4f) pose.getClass().getMethod(spm ? "pose" : "m_252922_").invoke(pose))
                .translation(translation);
            if (!spm) {
                // The native path reads RenderSystem projection; the Java path receives matrix.
                // Pose contains only instance placement; the window applies the camera once.
                projection = (org.joml.Matrix4f) renderSystem.getMethod("getProjectionMatrix").invoke(null);
                savedProjection = new org.joml.Matrix4f(projection);
                projection.set(windowCamera);
            }
        } else if (!spm && config.has("camera")) {
            var camera = config.getAsJsonObject("camera");
            var eye = vector(camera, "eye");
            var target = vector(camera, "target");
            float fov = camera.get("fovDegrees").getAsFloat();
            if (!Float.isFinite(fov) || fov <= 0 || fov >= 180 || eye.equals(target)
                || new org.joml.Vector3f(target).sub(eye).cross(0, 1, 0).lengthSquared() == 0) {
                throw new IllegalArgumentException("Invalid configured camera");
            }
            projection = (org.joml.Matrix4f) renderSystem.getMethod("getProjectionMatrix").invoke(null);
            savedProjection = new org.joml.Matrix4f(projection);
            projection.setPerspective((float) Math.toRadians(fov), 1.5f, 0.01f, 1000f);
            var view = new org.joml.Matrix4f().lookAt(eye, target, new org.joml.Vector3f(0, 1, 0));
            ((org.joml.Matrix4f) pose.getClass().getMethod("m_252922_").invoke(pose)).set(view);
            view.normal((org.joml.Matrix3f) pose.getClass().getMethod("m_252943_").invoke(pose));
            ((org.joml.Matrix4f) matrix).set(projection).mul(view);
        }
        Class<?> renderer = Class.forName("com.elfmcys.yesstevemodel.geckolib3.geo."
            + (spm ? "ModelRendererBridge" : "NativeModelRenderer"));
        var method = renderer.getMethod(nativeSimd ? "nativeRenderModel" : "renderModel",
            consumerType, pose.getClass(), matrixType, boolean.class, model.getClass(), float[].class,
            float[].class, int.class, int.class, int.class, int.class, float.class, float.class,
            float.class, float.class, boolean.class);
        Object accepted;
        try {
            accepted = method.invoke(null, consumer, pose, matrix, false, model, parameters,
                new float[parameters.length / 3], 0, 0, inputs.packedLight(), inputs.packedOverlay(),
                inputs.tint().x, inputs.tint().y, inputs.tint().z, inputs.tint().w, true);
        } finally {
            if (savedProjection != null) {
                projection.set(savedProjection);
            }
        }
        if (Boolean.FALSE.equals(accepted)) {
            throw new IllegalStateException("Original native renderer declined the request");
        }
        if (positions.isEmpty()) {
            throw new AssertionError("Original renderer submitted no vertices; parameters="
                + java.util.Arrays.toString(parameters) + "; bakedBones="
                + model.getClass().getField("bakedBones").get(model));
        }
        if (!lightingReported) {
            var counts = new java.util.TreeMap<String, Integer>();
            for (int[] coordinates : lightingCoordinates) {
                counts.merge(java.util.Arrays.toString(coordinates), 1, Integer::sum);
            }
            System.out.println("ORIGINAL VERTEX LIGHTING [lightU,lightV,overlayU,overlayV]=" + counts);
            lightingReported = true;
        }
        return List.copyOf(positions);
    }

    double[] vertexData(Object model, boolean nativeSimd, float[] parameters) throws Exception {
        render(model, nativeSimd, parameters);
        double[] result = new double[vertices.size() * 9];
        for (int index = 0; index < vertices.size(); index++) {
            System.arraycopy(vertices.get(index), 0, result, index * 9, 9);
        }
        return result;
    }

    float[] vertexColors() {
        float[] result = new float[colors.size() * 4];
        for (int index = 0; index < colors.size(); index++) {
            System.arraycopy(colors.get(index), 0, result, index * 4, 4);
        }
        return result;
    }

    int[] vertexLightingCoordinates() {
        int[] result = new int[lightingCoordinates.size() * 4];
        for (int index = 0; index < lightingCoordinates.size(); index++) {
            System.arraycopy(lightingCoordinates.get(index), 0, result, index * 4, 4);
        }
        return result;
    }

    static int[] quadIndices(int vertexCount) {
        if (vertexCount % 4 != 0) {
            throw new IllegalArgumentException("Original quad output is incomplete: " + vertexCount);
        }
        int[] indices = new int[vertexCount / 4 * 6];
        for (int quad = 0; quad < vertexCount / 4; quad++) {
            int vertex = quad * 4;
            int offset = quad * 6;
            indices[offset] = vertex;
            indices[offset + 1] = vertex + 1;
            indices[offset + 2] = vertex + 2;
            indices[offset + 3] = vertex + 2;
            indices[offset + 4] = vertex + 3;
            indices[offset + 5] = vertex;
        }
        return indices;
    }

    private static org.joml.Vector3f vector(com.google.gson.JsonObject camera, String name) {
        var array = camera.getAsJsonArray(name);
        if (array.size() != 3) {
            throw new IllegalArgumentException("camera." + name + " requires three coordinates");
        }
        var result = new org.joml.Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
        if (!result.isFinite()) {
            throw new IllegalArgumentException("camera." + name + " must be finite");
        }
        return result;
    }
}
