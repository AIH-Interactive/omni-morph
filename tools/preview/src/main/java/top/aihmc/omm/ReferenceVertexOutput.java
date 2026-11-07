package top.aihmc.omm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Receives original MOD renderer output through its Minecraft VertexConsumer contract. */
final class ReferenceVertexOutput {
    private final List<float[]> positions = new ArrayList<>();
    private final boolean spm;

    ReferenceVertexOutput(String backend) {
        spm = backend.equals("spm");
    }

    List<float[]> render(Object model, boolean nativeSimd) throws Exception {
        positions.clear();
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
        float[] parameters = (float[]) model.getClass().getMethod("getBoneTransformData").invoke(model);
        Class<?> renderer = Class.forName("com.elfmcys.yesstevemodel.geckolib3.geo."
            + (spm ? "ModelRendererBridge" : "NativeModelRenderer"));
        var method = renderer.getMethod(nativeSimd ? "nativeRenderModel" : "renderModel",
            consumerType, pose.getClass(), matrixType, boolean.class, model.getClass(), float[].class,
            float[].class, int.class, int.class, int.class, int.class, float.class, float.class,
            float.class, float.class, boolean.class);
        Object accepted = method.invoke(null, consumer, pose, matrix, false, model, parameters,
            new float[parameters.length / 3], 0, 0, 0x00F000F0, 0, 1f, 1f, 1f, 1f, true);
        if (Boolean.FALSE.equals(accepted)) {
            throw new IllegalStateException("Original native renderer declined the request");
        }
        if (positions.isEmpty()) {
            throw new AssertionError("Original renderer submitted no vertices; parameters="
                + java.util.Arrays.toString(parameters) + "; bakedBones="
                + model.getClass().getField("bakedBones").get(model));
        }
        return List.copyOf(positions);
    }
}
