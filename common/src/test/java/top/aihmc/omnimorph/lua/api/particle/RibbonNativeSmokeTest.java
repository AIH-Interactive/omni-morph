package top.aihmc.omnimorph.lua.api.particle;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaTypeManager;
import org.figuramc.figura.mixin.particle.ParticleEngineMixin;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitTrailBudget;
import top.aihmc.omnimorph.model.outfit.TrailBuffer;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import org.figuramc.figura.permissions.Permissions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.figuramc.figura.lua.api.particle.LuaParticle;
/** Actual Lua settings, production extraction hook, native node submission and native PARTICLE vertex bytes. */
final class RibbonNativeSmokeTest {
    static void run(Avatar first, Avatar second, Runnable drain) throws Exception {
        luaGeometryAndLifetime(first, drain);
        sharedVerticesAndTrailSlots(first, second, drain);
        System.out.println("PASS native Ribbon: Lua mode/settings, extraction hook, actual particle vertex bytes, "
                + "native submit nodes, frozen replay, shared vertices/trail slots and cleanup; GPU/game MANUAL/PENDING");
    }

    private static void luaGeometryAndLifetime(Avatar owner, Runnable drain) throws Exception {
        var texture = new FiguraTexture(owner, "ribbon-base", new NativeImage(16, 8, true));
        var overlay = new FiguraTexture(owner, "ribbon-overlay", new NativeImage(32, 16, true));
        var particle = new CustomParticle(owner, Minecraft.getInstance().level, texture,
                new Vec3(10, 3, 4), new Vec3(1, 0, 0));
        try {
            var types = new LuaTypeManager();
            types.generateMetatableFor(LuaParticle.class);
            var wrapper = types.javaToLua(new LuaParticle("ribbon", particle, owner)).arg1();
            var settings = options("width", .4, "length", 4, "lifetime", 12, "emissive", true, "light", false);
            wrapper.get("setRibbon").call(wrapper, settings);
            wrapper.get("setRenderMode").call(wrapper, LuaValue.valueOf("Ribbon"));
            require(particle.activate(), "configured Ribbon activates through the existing lifecycle");
            particle.setSpriteSheet(options("columns", 2, "initialFrame", 1, "paused", true));
            require(particle.emissive(), "Ribbon emissive aliases the same independent full-bright setting");
            var layers = new LuaTable();
            layers.set(1, new LuaTable());
            LuaTable second = options("emissive", false, "blend", "additive");
            second.set("texture", LuaValue.userdataOf(overlay));
            layers.set(2, second);
            wrapper.get("setLayers").call(wrapper, layers);
            require(wrapper.get("getRenderMode").call(wrapper).tojstring().equals("RIBBON")
                            && wrapper.get("getLayerCount").call(wrapper).toint() == 2,
                    "adding layers preserves Ribbon mode and one shared trajectory");
            reject(() -> particle.setRibbon(options("width", .9, "emissive", 1)));
            reject(() -> particle.setRibbon(options("width", .9, "light", "invalid")));
            near(particle.getRibbon().get("width").todouble(), .4);
            settings.set("width", 999);
            var returned = wrapper.get("getRibbon").call(wrapper).checktable();
            near(returned.get("width").todouble(), .4);
            returned.set("width", 999);
            near(wrapper.get("getRibbon").call(wrapper).get("width").todouble(), .4);
            YsmRenderFrameClock.beginFrame();
            require(extract(new FixtureCamera(), 1).particles.isEmpty(), "first history point emits no invented segment");
            particle.tick();
            YsmRenderFrameClock.beginFrame();
            var frustum = new RecordingFrustum(false);
            int beforeCull = CustomParticleRuntime.availableRibbonVertices(particle);
            require(extract(new FixtureCamera(), frustum, 1).particles.isEmpty()
                            && CustomParticleRuntime.availableRibbonVertices(particle) == beforeCull,
                    "culled history consumes no submitted vertex budget");
            require(frustum.bounds.minX <= 9.8 && frustum.bounds.maxX >= 11.2,
                    "culling considers the tail and head with half-width padding, not just the current head");
            owner.permissions.insert(Permissions.COMPLEXITY, 1, org.figuramc.figura.FiguraMod.MOD_ID);
            try {
                require(extract(new FixtureCamera(), 1).particles.isEmpty()
                                && "RIBBON_FACE_LIMIT".equals(particle.getRenderError())
                                && CustomParticleRuntime.availableRibbonVertices(particle) == beforeCull,
                        "incomplete layered face allowance rejects before spending any vertices");
            } finally {
                owner.permissions.insert(Permissions.COMPLEXITY, 10000, org.figuramc.figura.FiguraMod.MOD_ID);
            }
            YsmRenderFrameClock.beginFrame();
            var state = extract(new FixtureCamera(), 1);
            require(state.particles.size() == 1 && state.particles.getFirst() instanceof RibbonRenderState,
                    "production extraction hook adds an arbitrary-quad particle group");
            var ribbon = (RibbonRenderState) state.particles.getFirst();
            var ordinary = new QuadParticleRenderState();
            particle.extract(ordinary, new FixtureCamera(), 1);
            require(ordinary.isEmpty(), "Ribbon does not also emit a Billboard square");
            var nodes = new SubmitNodeStorage();
            state.submit(nodes, new CameraRenderState());
            require(nodes.getSubmitsPerOrder().get(0).getParticleGroupRenderers().getFirst() == ribbon,
                    "extracted Ribbon reaches the native particle feature queue");
            require(ribbon.prepare(null, false) == null, "translucent Ribbon does not allocate in the solid pass");
            byte[] frozen = vertices(ribbon, 8);
            ByteBuffer data = ByteBuffer.wrap(frozen).order(java.nio.ByteOrder.nativeOrder());
            var format = DefaultVertexFormat.PARTICLE;
            int stride = format.getVertexSize();
            int uv = format.getOffset(VertexFormatElement.UV0);
            int light = format.getOffset(VertexFormatElement.UV2);
            near(data.getFloat(0), 0);
            near(data.getFloat(4), -.2);
            near(data.getFloat(8), -10);
            near(data.getFloat(stride * 2), 1);
            near(data.getFloat(stride + 4), .2);
            near(data.getFloat(uv), .5);
            near(data.getFloat(stride * 2 + uv), 1);
            require(data.getInt(light) == 0xF000F0 && data.getInt(stride * 4 + light) == 0xF00000,
                    "per-layer full-bright and ordinary native light enter actual vertex bytes");
            wrapper.get("setRibbon").call(wrapper, options("width", .8, "uvOffset", -.25));
            require(Arrays.equals(frozen, vertices(ribbon, 8)), "settings changes do not mutate extracted geometry");
            wrapper.get("setRotation").call(wrapper, LuaValue.valueOf(90));
            // Keep one atlas interval so rotation can be inspected without extra UV seam subdivisions.
            wrapper.get("setRibbon").call(wrapper, options("uvOffset", 0));
            YsmRenderFrameClock.beginFrame();
            var rotated = (RibbonRenderState) extract(new FixtureCamera(), 1).particles.getFirst();
            var rotatedBytes = ByteBuffer.wrap(vertices(rotated, 8)).order(java.nio.ByteOrder.nativeOrder());
            near(rotatedBytes.getFloat(4), 0);
            near(rotatedBytes.getFloat(8), -10.4);
            // This JVM does not transform vanilla Particle with the existing Lua setPos accessor mixin.
            // Invoke native position update at partialTick=1; no production behavior is changed for this fixture.
            particle.setPos(100, 3, 4);
            YsmRenderFrameClock.beginFrame();
            require(extract(new FixtureCamera(), 1).particles.isEmpty(), "teleport clears the entire old trajectory");
            require(wrapper.get("getRenderError").call(wrapper).isnil(), "normal empty history is not a render error");
            var gpu = texture.getTexture();
            var overlayGpu = overlay.getTexture();
            texture.close();
            overlay.close();
            wrapper.get("remove").call(wrapper);
            require(!gpu.isClosed() && !overlayGpu.isClosed(), "submitted frame retains both retired layer images");
            require(Arrays.equals(frozen, vertices(ribbon, 8)), "replay after removal does not sample or mutate history");
            CustomParticleRuntime.finishFrame();
            drain.run();
            require(gpu.isClosed() && overlayGpu.isClosed() && CustomParticleRuntime.liveCount(owner.owner) == 0,
                    "frame completion releases textures and execution removal returns capacity");
        } finally {
            particle.remove();
            CustomParticleRuntime.finishFrame();
            texture.close();
            overlay.close();
            drain.run();
        }
    }

    private static void sharedVerticesAndTrailSlots(Avatar first, Avatar second, Runnable drain) throws Exception {
        var firstContext = first.cosmeticContext;
        var secondContext = second.cosmeticContext;
        var aTexture = new FiguraTexture(first, "ribbon-cap-a", new NativeImage(1, 1, true));
        var bTexture = new FiguraTexture(second, "ribbon-cap-b", new NativeImage(1, 1, true));
        var a = new CustomParticle(first, Minecraft.getInstance().level, aTexture, Vec3.ZERO, new Vec3(1, 0, 0));
        var b = new CustomParticle(second, Minecraft.getInstance().level, bTexture, Vec3.ZERO, new Vec3(1, 0, 0));
        UUID wearer = UUID.randomUUID();
        var limits = limits(2, 8);
        var controllerTrail = new TrailBuffer(limits);
        try {
            context(first, wearer, limits);
            context(second, wearer, limits);
            a.setRenderMode("RIBBON");
            var layers = new LuaTable();
            layers.set(1, new LuaTable());
            layers.set(2, new LuaTable());
            a.setLayers(layers);
            b.setRenderMode("RIBBON");
            require(OutfitTrailBudget.acquire(controllerTrail, wearer, limits.maxTrails()),
                    "controller history reserves one shared slot");
            require(a.activate() && !b.activate() && !b.active(),
                    "a controller history and actual particle Ribbon fill one shared quota");
            OutfitTrailBudget.release(controllerTrail);
            require(b.activate(), "releasing controller history permits the waiting particle Ribbon");
            YsmRenderFrameClock.beginFrame();
            extract(new FixtureCamera(), 1);
            a.tick();
            b.tick();
            YsmRenderFrameClock.beginFrame();
            OutfitTrailBudget.chargeVertices(wearer, YsmRenderFrameClock.currentFrame(), 8, 4);
            require(extract(new FixtureCamera(), 1).particles.size() == 1
                            && "RIBBON_VERTEX_LIMIT".equals(a.getRenderError()) && b.getRenderError() == null,
                    "external controller submission and native Ribbon use the same frame vertex allowance");
            YsmRenderFrameClock.beginFrame();
            var accepted = extract(new FixtureCamera(), 1);
            require(accepted.particles.size() == 1 && a.getRenderError() == null
                            && "RIBBON_VERTEX_LIMIT".equals(b.getRenderError()) && b.active(),
                    "first complete two-layer Ribbon spends eight vertices; another execution gets no new quota");
            a.remove();
            require(extract(new FixtureCamera(), 1).particles.isEmpty()
                            && "RIBBON_VERTEX_LIMIT".equals(b.getRenderError()),
                    "removal cannot refund vertices already submitted in the same frame");
            YsmRenderFrameClock.beginFrame();
            require(extract(new FixtureCamera(), 1).particles.size() == 1 && b.getRenderError() == null,
                    "a later frame replenishes the shared vertex allowance");
            b.remove();
            context(first, wearer, limits(1, 8));
            context(second, wearer, limits(1, 8));
            a = new CustomParticle(first, Minecraft.getInstance().level, aTexture, Vec3.ZERO, Vec3.ZERO);
            b = new CustomParticle(second, Minecraft.getInstance().level, bTexture, Vec3.ZERO, Vec3.ZERO);
            a.setRenderMode("RIBBON");
            require(a.activate() && b.activate(), "one Ribbon and one Billboard fit the configured trail limit");
            CustomParticle blocked = b;
            reject(() -> blocked.setRenderMode("RIBBON"));
            require(b.active() && b.renderMode().equals("BILLBOARD"), "failed mode change preserves old material/lifetime");
            a.setRenderMode("BILLBOARD");
            b.setRenderMode("RIBBON");
            require(b.renderMode().equals("RIBBON"), "mode exit releases exactly one trail slot");
            var world = Minecraft.getInstance().level;
            try {
                Minecraft.getInstance().level = null;
                extract(new FixtureCamera(), 1);
                require(!b.active(), "extraction retires a Ribbon whose captured world has been disconnected");
            } finally {
                Minecraft.getInstance().level = world;
            }
        } finally {
            OutfitTrailBudget.release(controllerTrail);
            a.remove();
            b.remove();
            setContext(first, firstContext);
            setContext(second, secondContext);
            CustomParticleRuntime.finishFrame();
            aTexture.close();
            bTexture.close();
            drain.run();
        }
    }

    private static ParticlesRenderState extract(Camera camera, float partialTick) throws Exception {
        return extract(camera, null, partialTick);
    }

    private static ParticlesRenderState extract(Camera camera, Frustum frustum, float partialTick) throws Exception {
        var state = new ParticlesRenderState();
        var hook = ParticleEngineMixin.class.getDeclaredMethod("figura$extractRibbons", ParticlesRenderState.class,
                net.minecraft.client.renderer.culling.Frustum.class, Camera.class, float.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        hook.setAccessible(true);
        hook.invoke(new EngineHook(), state, frustum, camera, partialTick,
                new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("extract", false));
        return state;
    }

    private static byte[] vertices(RibbonRenderState ribbon, int count) {
        try (var storage = ByteBufferBuilder.exactlySized(count * DefaultVertexFormat.PARTICLE.getVertexSize())) {
            var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            ribbon.writeVertices(builder);
            try (var mesh = builder.build()) {
                require(mesh != null && mesh.drawState().vertexCount() == count, "native quad buffer has expected vertices");
                ByteBuffer buffer = mesh.vertexBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return bytes;
            }
        }
    }

    private static OutfitLimits limits(int trails, int vertices) {
        var gson = new Gson();
        var json = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxTrails", trails);
        json.addProperty("maxRibbonVertices", vertices);
        return gson.fromJson(json, OutfitLimits.class);
    }

    private static void context(Avatar owner, UUID wearer, OutfitLimits limits) throws Exception {
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "0".repeat(64), OutfitConfig.Format.FIGURA);
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("back_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        setContext(owner, new CosmeticInstanceContext(wearer, UUID.randomUUID(), wearer, instance, limits,
                CosmeticInstanceContext.Scope.OWNER));
    }

    private static void setContext(Avatar owner, CosmeticInstanceContext context) throws Exception {
        var field = Avatar.class.getDeclaredField("cosmeticContext");
        field.setAccessible(true);
        field.set(owner, context);
    }

    private static LuaTable options(Object... pairs) {
        var table = new LuaTable();
        for (int index = 0; index < pairs.length; index += 2) {
            Object value = pairs[index + 1];
            table.set((String) pairs[index], value instanceof Boolean flag ? LuaValue.valueOf(flag)
                    : value instanceof Number number ? LuaValue.valueOf(number.doubleValue())
                    : LuaValue.valueOf((String) value));
        }
        return table;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected Ribbon capacity rejection");
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 1e-5, "Expected " + expected + ", actual " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FixtureCamera extends Camera {
        @Override
        public Quaternionf rotation() {
            return new Quaternionf();
        }

        @Override
        public Vec3 position() {
            return new Vec3(10, 3, 14);
        }
    }

    private static final class RecordingFrustum extends Frustum {
        private final boolean visible;
        private AABB bounds;

        private RecordingFrustum(boolean visible) {
            super(new Matrix4f(), new Matrix4f());
            this.visible = visible;
        }

        @Override
        public boolean isVisible(AABB bounds) {
            this.bounds = bounds;
            return visible;
        }
    }

    private static final class EngineHook extends ParticleEngineMixin {
        @Override
        protected <T extends ParticleOptions> Particle makeParticle(T options, double x, double y, double z,
                                                                    double vx, double vy, double vz) {
            return null;
        }

        @Override
        public void add(Particle particle) {
        }

        @Override
        public SpriteSet figura$getParticleSprite(ResourceLocation id) {
            return null;
        }
    }
}
