package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.FiguraModelPart;
import top.aihmc.omnimorph.model.PartCustomization;
import top.aihmc.omnimorph.model.rendering.FiguraRenderer;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.rendering.Vertex;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTextureSet;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Exercises the production deferred Figura emitter, without a graphics device. */
public final class FiguraAccelerationReplay {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Avatar owner = (Avatar) unsafe.allocateInstance(Avatar.class);
        ImmediateFiguraRenderer renderer = (ImmediateFiguraRenderer) unsafe.allocateInstance(ImmediateFiguraRenderer.class);
        Field avatarField = FiguraRenderer.class.getDeclaredField("avatar");
        avatarField.setAccessible(true);
        avatarField.set(renderer, owner);

        Class<?> dataType = Class.forName(ImmediateFiguraRenderer.class.getName() + "$VertexData");
        var constructor = dataType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object data = constructor.newInstance();
        set(data, "renderType", RenderTypes.entityCutout(Identifier.parse("omni-morph:test")));
        set(data, "primary", true);
        set(data, "color", FiguraVec3.of(0.25, 0.5, 0.75));
        PartCustomization customization = new PartCustomization();
        customization.positionMatrix.translate(3, 4, 5);
        customization.normalMatrix.v22 = 0.5;
        customization.alpha = 0.5f;
        customization.light = 0x00f000a0;
        customization.overlay = 0x00020001;
        FiguraTextureSet textures = new FiguraTextureSet("test", null, null, null, null) {
            @Override
            public int getWidth() {
                return 16;
            }

            @Override
            public int getHeight() {
                return 16;
            }
        };
        List<Vertex> vertices = List.of(new Vertex(1, 2, 3, 8, 4, 0, 1, 0),
                new Vertex(2, 2, 3, 16, 4, 0, 1, 0), new Vertex(2, 3, 3, 16, 8, 0, 1, 0),
                new Vertex(1, 3, 3, 8, 8, 0, 1, 0));
        var push = ImmediateFiguraRenderer.class.getDeclaredMethod("pushToBuffer", int.class, dataType,
                PartCustomization.class, FiguraTextureSet.class, List.class, FiguraModelPart.class, FiguraMat4[].class);
        push.setAccessible(true);
        Field bufferField = ImmediateFiguraRenderer.class.getDeclaredField("VERTEX_BUFFER");
        bufferField.setAccessible(true);
        Object buffer = bufferField.get(null);
        var consume = buffer.getClass().getDeclaredMethod("consume", boolean.class, MultiBufferSource.class);
        consume.setAccessible(true);
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    List<float[]> target = switch (method.getName()) {
                        case "addVertex" -> positions;
                        case "setNormal" -> normals;
                        case "setUv" -> uvs;
                        default -> null;
                    };
                    if (target != null) {
                        float[] values = new float[arguments.length];
                        for (int index = 0; index < arguments.length; index++) {
                            values[index] = ((Number) arguments[index]).floatValue();
                        }
                        target.add(values);
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        MultiBufferSource destination = type -> sink;
        push.invoke(renderer, 1, data, customization, textures, vertices, null, null);
        consume.invoke(buffer, true, destination);
        require(positions.size() == 4 && positions.getFirst()[0] == 4 && positions.getFirst()[2] == 8,
                "actual Figura draw consumes transformed batch positions");
        require(normals.size() == 4 && normals.getFirst()[1] == 0.5f,
                "actual Figura draw preserves separate normal matrix");
        require(uvs.size() == 4 && uvs.getFirst()[0] == 0.5f && uvs.getFirst()[1] == 0.25f,
                "actual Figura UV channel remains unchanged");

        positions.clear();
        FiguraModelPart part = (FiguraModelPart) unsafe.allocateInstance(FiguraModelPart.class);
        part.skinBoneIndices = new int[][]{{0, 1}};
        part.skinBoneWeights = new float[][]{{1, 3}};
        for (Vertex vertex : vertices) {
            vertex.origIdx = 0;
        }
        FiguraMat4[] deltas = {FiguraMat4.of().translate(2, 0, 0), FiguraMat4.of().translate(0, 4, 0)};
        push.invoke(renderer, 1, data, customization, textures, vertices, part, deltas);
        consume.invoke(buffer, true, destination);
        require(positions.size() == 4 && positions.getFirst()[0] == 4.5f && positions.getFirst()[1] == 9,
                "actual Figura draw consumes the complete normalized skinning batch");
        positions.clear();
        push.invoke(renderer, 1, data, customization, textures, vertices, null, null);
        owner.accelerationOwner().retire();
        consume.invoke(buffer, true, destination);
        require(positions.isEmpty(), "retired Avatar cannot emit a deferred Figura draw");
        System.out.println("Figura production emitter batch positions/normals/UV and retired deferred draw PASS.");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
