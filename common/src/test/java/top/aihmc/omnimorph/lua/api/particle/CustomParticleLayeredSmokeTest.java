package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaTypeManager;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.figuramc.figura.lua.api.particle.LuaParticle;
public final class CustomParticleLayeredSmokeTest {
    public static void run(Avatar owner, FiguraTexture foreign, Runnable drain) throws Exception {
        var base = new FiguraTexture(owner, "layer-base", new NativeImage(16, 8, true));
        var overlay = new FiguraTexture(owner, "layer-overlay", new NativeImage(32, 16, true));
        var candidate = new FiguraTexture(owner, "layer-candidate", new NativeImage(1, 1, true));
        var retired = new FiguraTexture(owner, "layer-retired", new NativeImage(1, 1, true));
        var particle = new CustomParticle(owner, Minecraft.getInstance().level, base, Vec3.ZERO, Vec3.ZERO);
        try {
            require(particle.activate(), "layer fixture activates one particle");
            var types = new LuaTypeManager();
            types.generateMetatableFor(LuaParticle.class);
            types.generateMetatableFor(FiguraTexture.class);
            types.generateMetatableFor(FiguraVec4.class);
            LuaValue wrapper = types.javaToLua(new LuaParticle("layer-test", particle, owner)).arg1();
            particle.setColor(.8f, .6f, .4f);
            particle.setEmissive(true);
            particle.setSpriteSheet(options("columns", 2, "initialFrame", 1, "paused", true));
            var tint = FiguraVec4.of(.5, 1, .25, .5);
            LuaTable first = options("texture", base, "blend", "alpha");
            LuaTable second = options("texture", overlay, "color", tint, "alpha", .4,
                    "blend", "additive", "emissive", false);
            second.set("spriteSheet", options("columns", 4, "rows", 2, "initialFrame", 5, "paused", true));
            second.set("uv", array(0.25, 0.75, 0.5, 1.0));
            LuaTable layers = array(first, second);
            wrapper.get("setLayers").call(wrapper, layers);
            require(wrapper.get("getLayerCount").call(wrapper).toint() == 2
                            && wrapper.get("getRenderMode").call(wrapper).tojstring().equals("LAYERED"),
                    "public Lua layer configuration selects Layered mode");
            tint.x = 0;
            second.set("alpha", 0);
            var state = new RecordingQuads();
            particle.extract(state, new FixtureCamera(), .5f);
            require(state.quads.size() == 2 && CustomParticleRuntime.liveCount(owner.owner) == 1,
                    "two layers emit two native quads but consume one particle slot");
            Quad a = state.quads.get(0);
            Quad b = state.quads.get(1);
            require(a.layer.textureAtlasLocation().equals(base.getLocation())
                            && b.layer.textureAtlasLocation().equals(overlay.getLocation()),
                    "native quad extraction carries each authorized texture");
            near(a.u0, .5);
            near(a.u1, 1);
            near(b.u0, .3125);
            near(b.u1, .4375);
            near(b.v0, .75);
            near(b.v1, 1);
            require(a.light == 0xF000F0 && b.light == 0xF00000,
                    "layer emissive override uses native no-chunk sky light instead of full-bright block light");
            channel(b.color >>> 24, 51);
            channel(b.color >>> 16 & 255, 102);
            channel(b.color >>> 8 & 255, 153);
            channel(b.color & 255, 25);
            var repeated = new RecordingQuads();
            particle.extract(repeated, new FixtureCamera(), .5f);
            require(state.quads.equals(repeated.quads), "layer tint does not accumulate or mutate authored particle color");
            checkPipelinesAndOrder(a.layer, b.layer);
            testRejectedChanges(particle, first, base, foreign, retired);
            // Retaining the first candidate must be rolled back when the second texture cannot be acquired.
            var candidateGpu = candidate.getTexture();
            reject(() -> particle.setLayers(array(options("texture", candidate), options("texture", retired))));
            candidate.close();
            drain.run();
            require(candidateGpu.isClosed() && particle.layerCount() == 2 && particle.active(),
                    "failed multi-texture replacement releases temporary leases and preserves the old layers");
            var baseGpu = base.getTexture();
            var overlayGpu = overlay.getTexture();
            base.close();
            overlay.close();
            particle.setLayers(array(first));
            require(!overlayGpu.isClosed(), "removed layer stays alive for an already extracted frame");
            CustomParticleRuntime.finishFrame();
            require(overlayGpu.isClosed() && !baseGpu.isClosed(),
                    "frame retirement releases removed layer while the retained layer remains alive");
            particle.setRenderMode("Billboard");
            particle.setBlendMode("opaque");
            require(particle.renderMode().equals("BILLBOARD") && particle.layerCount() == 1
                            && particle.blendMode().equals("opaque"), "Billboard mode and blend controls remain available");
            particle.remove();
            require(baseGpu.isClosed() && CustomParticleRuntime.liveCount(owner.owner) == 0,
                    "final removal releases all layer textures and the one shared lifetime slot");
        } finally {
            particle.remove();
            CustomParticleRuntime.finishFrame();
            base.close();
            overlay.close();
            candidate.close();
            retired.close();
            drain.run();
        }
        System.out.println("PASS Layered particles: native UV/tint/light extraction, blend/order hook, atomic leases, "
                + "layer bounds and frame cleanup; real GPU/game MANUAL/PENDING");
    }

    private static void testRejectedChanges(CustomParticle particle, LuaTable first, FiguraTexture base,
                                             FiguraTexture foreign, FiguraTexture retired) {
        reject(() -> particle.setLayers(array(first, first, first, first, first)));
        reject(() -> particle.setLayers(new LuaTable()));
        var sparse = new LuaTable();
        sparse.set(1000000, first);
        reject(() -> particle.setLayers(sparse));
        reject(() -> particle.setLayers(array(options("texture", foreign))));
        reject(() -> particle.setLayers(array(options("texture", base, "unexpected", 1))));
        reject(() -> particle.setLayers(array(options("texture", base, "blend", "unknown"))));
        reject(() -> particle.setLayers(array(options("texture", base, "alpha", Double.NaN))));
        var uv = options("texture", base);
        uv.set("uv", array(.5, .25, 0, 1));
        reject(() -> particle.setLayers(array(uv)));
        reject(() -> particle.setRenderMode("unknown"));
        retired.close();
        require(particle.layerCount() == 2 && particle.active(), "rejected declarations do not partially publish");
    }

    private static void checkPipelinesAndOrder(SingleQuadParticle.Layer first, SingleQuadParticle.Layer second)
            throws Exception {
        require(first.pipeline().getColorTargetState().blendFunction().orElseThrow().equals(BlendFunction.TRANSLUCENT),
                "alpha layer uses source alpha compositing");
        var additive = second.pipeline().getColorTargetState().blendFunction().orElseThrow();
        require(additive.sourceColor() == SourceFactor.SRC_ALPHA && additive.destColor() == DestFactor.ONE,
                "additive layer still respects source opacity");
        require(first.translucent() && second.translucent()
                        && !first.pipeline().getDepthStencilState().writeDepth()
                        && !second.pipeline().getDepthStencilState().writeDepth(),
                "mixed blends share a pass without writing depth over another layer");
        var opaque = CustomParticlePipelines.pipeline(2, CustomParticlePipelines.Blend.OPAQUE);
        require(opaque.getColorTargetState().blendFunction().isEmpty(), "opaque blend disables compositing");
        var third = new SingleQuadParticle.Layer(true, first.textureAtlasLocation(), opaque);
        var nativeA = SingleQuadParticle.Layer.TRANSLUCENT;
        var nativeB = SingleQuadParticle.Layer.OPAQUE;
        Map<SingleQuadParticle.Layer, Integer> batches = new LinkedHashMap<>();
        batches.put(nativeA, 0);
        batches.put(third, 1);
        batches.put(nativeB, 2);
        batches.put(second, 3);
        batches.put(first, 4);
        var hook = new top.aihmc.omnimorph.mixin.particle.QuadParticleRenderStateMixin();
        var method = hook.getClass().getDeclaredMethod("figura$orderCustomLayers", Map.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
        method.setAccessible(true);
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> original = arguments ->
                ((Map<?, ?>) arguments[0]).entrySet();
        var result = (java.util.Set<?>) method.invoke(hook, batches, original);
        var ordered = new ArrayList<>(result);
        require(((Map.Entry<?, ?>) ordered.get(0)).getKey() == nativeA
                        && ((Map.Entry<?, ?>) ordered.get(2)).getKey() == nativeB
                        && ((Map.Entry<?, ?>) ordered.get(1)).getKey() == first
                        && ((Map.Entry<?, ?>) ordered.get(3)).getKey() == second
                        && ((Map.Entry<?, ?>) ordered.get(4)).getKey() == third,
                "production render hook orders custom slots while leaving vanilla slots in place");
        var vanilla = new LinkedHashMap<SingleQuadParticle.Layer, Integer>();
        vanilla.put(nativeA, 0);
        vanilla.put(nativeB, 1);
        var entries = vanilla.entrySet();
        require(CustomParticlePipelines.orderedEntries(entries) == entries, "vanilla-only batches are untouched");
    }

    private record Quad(SingleQuadParticle.Layer layer, float u0, float u1, float v0, float v1, int color, int light) {
    }

    private static final class RecordingQuads extends QuadParticleRenderState {
        private final List<Quad> quads = new ArrayList<>();

        @Override
        public void add(SingleQuadParticle.Layer layer, float x, float y, float z, float qx, float qy,
                        float qz, float qw, float size, float u0, float u1, float v0, float v1, int color, int light) {
            super.add(layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
            quads.add(new Quad(layer, u0, u1, v0, v1, color, light));
        }
    }

    private static final class FixtureCamera extends Camera {
        @Override
        public Quaternionf rotation() {
            return new Quaternionf();
        }

        @Override
        public Vec3 position() {
            return Vec3.ZERO;
        }
    }

    private static LuaTable options(Object... pairs) {
        var table = new LuaTable();
        for (int index = 0; index < pairs.length; index += 2) {
            table.set((String) pairs[index], lua(pairs[index + 1]));
        }
        return table;
    }

    private static LuaTable array(Object... values) {
        var table = new LuaTable();
        for (int index = 0; index < values.length; index++) {
            table.set(index + 1, lua(values[index]));
        }
        return table;
    }

    private static LuaValue lua(Object value) {
        return switch (value) {
            case LuaValue lua -> lua;
            case Number number -> LuaValue.valueOf(number.doubleValue());
            case Boolean flag -> LuaValue.valueOf(flag);
            case String text -> LuaValue.valueOf(text);
            default -> new org.luaj.vm2.LuaUserdata(value);
        };
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid layer declaration was accepted");
    }

    private static void channel(int actual, int expected) {
        require(Math.abs(actual - expected) <= 1, "Packed layer color " + actual + " != " + expected);
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < .000001, "Layer UV " + actual + " != " + expected);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
