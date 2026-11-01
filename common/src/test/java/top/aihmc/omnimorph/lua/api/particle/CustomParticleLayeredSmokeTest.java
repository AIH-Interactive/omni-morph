package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
import java.util.List;

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
            require(a.light == 0xF000F0 && b.light == 0,
                    "layer emissive override uses native no-chunk darkness instead of full-bright block light");
            channel(b.color >>> 24, 51);
            channel(b.color >>> 16 & 255, 102);
            channel(b.color >>> 8 & 255, 153);
            channel(b.color & 255, 25);
            var repeated = new RecordingQuads();
            particle.extract(repeated, new FixtureCamera(), .5f);
            require(state.quads.equals(repeated.quads), "layer tint does not accumulate or mutate authored particle color");
            checkMaterialsAndOrder(owner, particle, base, a.layer, b.layer);
            testRejectedChanges(particle, first, base, foreign, retired);
            // Retaining the first candidate must be rolled back when the second texture cannot be acquired.
            int candidateGpu = candidate.getId();
            reject(() -> particle.setLayers(array(options("texture", candidate), options("texture", retired))));
            candidate.close();
            drain.run();
            require(!org.lwjgl.opengl.GL11.glIsTexture(candidateGpu) && particle.layerCount() == 2 && particle.active(),
                    "failed multi-texture replacement releases temporary leases and preserves the old layers");
            int baseGpu = base.getId();
            int overlayGpu = overlay.getId();
            base.close();
            overlay.close();
            particle.setLayers(array(first));
            require(org.lwjgl.opengl.GL11.glIsTexture(overlayGpu), "removed layer stays alive for an already extracted frame");
            CustomParticleRuntime.finishFrame();
            require(!org.lwjgl.opengl.GL11.glIsTexture(overlayGpu) && org.lwjgl.opengl.GL11.glIsTexture(baseGpu),
                    "frame retirement releases removed layer while the retained layer remains alive");
            particle.setRenderMode("Billboard");
            particle.setBlendMode("opaque");
            require(particle.renderMode().equals("BILLBOARD") && particle.layerCount() == 1
                            && particle.blendMode().equals("opaque"), "Billboard mode and blend controls remain available");
            particle.remove();
            require(!org.lwjgl.opengl.GL11.glIsTexture(baseGpu) && CustomParticleRuntime.liveCount(owner.owner) == 0,
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
        System.out.println("PASS Layered particles: native UV/tint/light extraction, material selection/native draw order, atomic leases, "
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

    private static void checkMaterialsAndOrder(Avatar owner, CustomParticle particle, FiguraTexture texture,
                                               CustomParticlePipelines.Material first,
                                               CustomParticlePipelines.Material second) throws Exception {
        require(first.pipeline().blend() == CustomParticlePipelines.Blend.ALPHA
                        && second.pipeline().blend() == CustomParticlePipelines.Blend.ADDITIVE,
                "author blend declarations select their production materials");
        for (var material : List.of(first, second, new CustomParticlePipelines.Material(texture.getLocation(),
                CustomParticlePipelines.pipeline(2, CustomParticlePipelines.Blend.OPAQUE)))) {
            var type = material.renderType(false);
            var stateField = type.getClass().getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(type);
            var maskField = state.getClass().getDeclaredField("writeMaskState");
            maskField.setAccessible(true);
            Object mask = maskField.get(state);
            var depthField = mask.getClass().getDeclaredField("writeDepth");
            depthField.setAccessible(true);
            require(!depthField.getBoolean(mask), "every custom blend uses color-only writes");
        }
        // Two particles interleave author indices; draw the actual frozen frame to a native vertex buffer.
        var peer = new CustomParticle(owner, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
        try {
            require(peer.activate(), "cross-particle author order fixture activates");
            peer.setEmissive(true);
            peer.setLayers(array(options("blend", "opaque"), new LuaTable(), options("blend", "opaque")));
            var frame = PreviewParticleFrame.extract(new CustomParticle[]{peer, particle}, Vec3.ZERO,
                    new Quaternionf(), 0);
            var order = new ArrayList<RenderType>();
            var builder = new BufferBuilder(1024);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            frame.draw(new Matrix4f(), type -> {
                order.add(type);
                return builder;
            });
            var mesh = builder.end();
            try {
                require(mesh != null && mesh.drawState().vertexCount() == 20,
                        "five author layers emit five complete native quads");
            } finally {
                mesh.release();
            }
            var peers = peer.currentLayers();
            require(order.equals(List.of(peers.get(0).renderLayer().renderType(false), first.renderType(false),
                            peers.get(1).renderLayer().renderType(false), second.renderType(false),
                            peers.get(2).renderLayer().renderType(false))),
                    "production frame drawing orders all author indices across particles, including opaque layers");
        } finally {
            peer.remove();
        }
    }

    private record Quad(CustomParticlePipelines.Material layer, float u0, float u1, float v0, float v1,
                        int color, int light) {
    }

    private static final class RecordingQuads implements BiConsumer<
            CustomParticlePipelines.Material, Consumer<VertexConsumer>> {
        private final List<Quad> quads = new ArrayList<>();

        @Override
        public void accept(CustomParticlePipelines.Material material,
                           Consumer<VertexConsumer> writer) {
            var capture = new ParticleVertexCapture();
            capture.accept(material, writer);
            var batch = capture.batches.get(0);
            var first = batch.vertices().get(0);
            quads.add(new Quad(material, batch.u0(), batch.u1(), batch.v0(), batch.v1(), first.color(), first.light()));
        }
    }

    private static final class FixtureCamera extends Camera {
        @Override
        public Quaternionf rotation() {
            return new Quaternionf();
        }

        @Override
        public Vec3 getPosition() {
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
        if (value instanceof LuaValue lua) {
            return lua;
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (value instanceof Boolean flag) {
            return LuaValue.valueOf(flag);
        }
        if (value instanceof String text) {
            return LuaValue.valueOf(text);
        }
        return new org.luaj.vm2.LuaUserdata(value);
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
