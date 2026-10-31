package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import sun.misc.Unsafe;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.PartCustomization;
import top.aihmc.omnimorph.model.rendering.FiguraRenderer;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import org.figuramc.figura.model.rendering.Vertex;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Exercises the production deferred Figura emitter, without a graphics device. */
public final class FiguraAccelerationReplay {
    public static void main(String[] args) throws Exception {
        replay(null);
    }

    public static void replay(Function<VertexConsumer, MultiBufferSource> sourceFactory) throws Exception {
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
        // allocateInstance skips field initializers; install the renderer-owned static mesh cache.
        var cacheType = Class.forName("top.aihmc.omnimorph.model.rendering.FiguraStaticMeshCache");
        var cacheConstructor = cacheType.getDeclaredConstructor();
        cacheConstructor.setAccessible(true);
        Field cacheField = ImmediateFiguraRenderer.class.getDeclaredField("staticMeshes");
        cacheField.setAccessible(true);
        cacheField.set(renderer, cacheConstructor.newInstance());

        Class<?> dataType = Class.forName(ImmediateFiguraRenderer.class.getName() + "$VertexData");
        var constructor = dataType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object data = constructor.newInstance();
        set(data, "renderType", RenderTypes.entityCutout(ResourceLocation.parse("omni-morph:test")));
        set(data, "texture", ResourceLocation.parse("omni-morph:test"));
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
        List<float[]> colors = new ArrayList<>();
        VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    List<float[]> target = switch (method.getName()) {
                        case "addVertex" -> positions;
                        case "setNormal" -> normals;
                        case "setUv" -> uvs;
                        case "setColor" -> colors;
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
        MultiBufferSource destination = sourceFactory == null ? type -> sink : sourceFactory.apply(sink);
        push.invoke(renderer, 1, data, customization, textures, vertices, null, null);
        consume.invoke(buffer, true, destination);
        flush(destination);
        require(positions.size() == 4 && positions.getFirst()[0] == 4 && positions.getFirst()[2] == 8,
                "actual Figura draw consumes transformed batch positions");
        require(normals.size() == 4 && normals.getFirst()[1] == 0.5f,
                "actual Figura draw preserves separate normal matrix");
        require(uvs.size() == 4 && uvs.getFirst()[0] == 0.5f && uvs.getFirst()[1] == 0.25f,
                "actual Figura UV channel remains unchanged");

        // Lua edits vertices in place; the next submission of the same list must show them immediately.
        vertices.getFirst().x = 2;
        vertices.getFirst().u = 0;
        positions.clear();
        uvs.clear();
        push.invoke(renderer, 1, data, customization, textures, vertices, null, null);
        consume.invoke(buffer, true, destination);
        flush(destination);
        require(positions.size() == 4 && positions.getFirst()[0] == 5 && uvs.getFirst()[0] == 0,
                "in-place vertex position and UV edits take effect on the next draw");
        vertices.getFirst().x = 1;
        vertices.getFirst().u = 8;
        positions.clear();
        uvs.clear();
        push.invoke(renderer, 1, data, customization, textures, vertices, null, null);
        consume.invoke(buffer, true, destination);
        flush(destination);
        require(positions.size() == 4 && positions.getFirst()[0] == 4 && uvs.getFirst()[0] == 0.5f,
                "restoring the original vertex values restores the original output");

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
        flush(destination);
        require(positions.size() == 4 && positions.getFirst()[0] == 4.5f && positions.getFirst()[1] == 9,
                "actual Figura draw consumes the complete normalized skinning batch");
        if (sourceFactory == null) {
            var selected = (FiguraModelPart) unsafe.allocateInstance(FiguraModelPart.class);
            var child = (FiguraModelPart) unsafe.allocateInstance(FiguraModelPart.class);
            child.parent = selected;
            boolean paperdoll = org.figuramc.figura.utils.ui.UIHelper.paperdoll;
            var materials = new ArrayList<net.minecraft.client.renderer.rendertype.RenderType>();
            try {
                org.figuramc.figura.utils.ui.UIHelper.paperdoll = true;
                top.aihmc.omnimorph.model.library.PreviewBoneHighlight.capture(owner, selected);
                colors.clear();
                push.invoke(renderer, 1, data, customization, textures, vertices, part, null);
                // The renderer must retain captured alpha even after GUI frame references are released.
                top.aihmc.omnimorph.model.library.PreviewBoneHighlight.finishFrame();
                consume.invoke(buffer, true, (MultiBufferSource) type -> {
                    materials.add(type);
                    return sink;
                });
                require(colors.size() == 4 && colors.getFirst()[3] == 38 && materials.getFirst().hasBlending(),
                        "unselected Figura geometry emits authored alpha times 0.3 with a blending material");
                colors.clear();
                top.aihmc.omnimorph.model.library.PreviewBoneHighlight.capture(owner, selected);
                push.invoke(renderer, 1, data, customization, textures, vertices, child, null);
                consume.invoke(buffer, true, (MultiBufferSource) type -> sink);
                require(colors.size() == 4 && colors.getFirst()[3] == 127 && customization.alpha == 0.5f,
                        "selected descendants retain authored alpha without modifying model customization");
            } finally {
                top.aihmc.omnimorph.model.library.PreviewBoneHighlight.finishFrame();
                org.figuramc.figura.utils.ui.UIHelper.paperdoll = paperdoll;
            }
        }
        positions.clear();
        push.invoke(renderer, 1, data, customization, textures, vertices, null, null);
        owner.accelerationOwner().retire();
        consume.invoke(buffer, true, destination);
        flush(destination);
        require(positions.isEmpty(), "retired Avatar cannot emit a deferred Figura draw");
        System.out.println("Figura production emitter batch positions/normals/UV and retired deferred draw PASS.");
    }

    private static void flush(MultiBufferSource destination) {
        if (destination instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
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
