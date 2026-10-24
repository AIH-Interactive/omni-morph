package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.ducks.ParticleEngineAccessor;
import org.figuramc.figura.lua.LuaTypeManager;
import org.figuramc.figura.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.utils.RefilledNumber;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.figuramc.figura.lua.api.particle.LuaParticle;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
/** Uses the existing native material recording device; native extraction runs, no game pixels are asserted. */
public final class CustomParticleNativeSmokeTest {
    public static void run(Unsafe unsafe, Avatar first, Avatar second, Runnable drain) throws Exception {
        Minecraft client = Minecraft.getInstance();
        var previousLevel = client.level;
        var engineField = Minecraft.class.getDeclaredField("particleEngine");
        engineField.setAccessible(true);
        Object previousEngine = engineField.get(client);
        var engine = (RecordingEngine) unsafe.allocateInstance(RecordingEngine.class);
        engine.particles = new ArrayList<>();
        var texture = new FiguraTexture(first, "custom-particle-grid", new NativeImage(16, 8, true));
        var secondTexture = new FiguraTexture(second, "custom-particle-grid", new NativeImage(16, 8, true));
        try {
            client.level = (ClientLevel) unsafe.allocateInstance(FixtureLevel.class);
            engineField.set(client, engine);
            initialize(first);
            initialize(second);
            CustomParticleLayeredSmokeTest.run(first, secondTexture, drain);
            RibbonNativeSmokeTest.run(first, second, drain);
            ParticleMotionNativeSmokeTest.run(unsafe, first, drain);
            ParticleLightNativeSmokeTest.run(unsafe, first, second, drain);
            try (var sounds = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingSoundManager(unsafe)) {
                AnchorTrailNativeSmokeTest.run(unsafe, drain);
                PreviewParticleNativeSmokeTest.run(first, drain, () -> engine.particles.size());
                CosmeticParticleEntrySmokeTest.run(drain, () -> engine.particles.size());
                CreatedTrailParticleSmokeTest.run(drain, () -> List.copyOf(engine.particles));
                // The recording list is a submission log, not the native engine's live queue.
                engine.particles.clear();
            }
            top.aihmc.omnimorph.model.outfit.OutfitParticleCallbackSmokeTest.run(unsafe);
            testLuaAndExtraction(first, second, texture, secondTexture, engine);
            testSharedLimits(first, second, texture, secondTexture);
            testLifecycleHooks(first, second, secondTexture, engine);
        } finally {
            CustomParticleRuntime.clearOwner(first.owner);
            CustomParticleRuntime.clearOwner(second.owner);
            CustomParticleRuntime.finishFrame();
            texture.close();
            secondTexture.close();
            drain.run();
            client.level = previousLevel;
            engineField.set(client, previousEngine);
        }
        System.out.println("PASS custom Billboard: actual Lua wrappers/native quad extraction, UV/full-bright, "
                + "shared capacity, execution cleanup and extracted texture lifetime; GPU/game MANUAL/PENDING");
    }

    private static void initialize(Avatar owner) throws Exception {
        set(owner, "particlesRemaining", new RefilledNumber(16));
        set(owner, "noPermissions", new HashSet<>());
        var category = new org.figuramc.figura.permissions.PermissionPack.CategoryPermissionPack(
                org.figuramc.figura.permissions.Permissions.Category.DEFAULT);
        var permissions = new org.figuramc.figura.permissions.PermissionPack.PlayerPermissionPack(
                category, owner.owner.toString());
        permissions.insert(org.figuramc.figura.permissions.Permissions.COMPLEXITY, 10000,
                org.figuramc.figura.FiguraMod.MOD_ID);
        set(owner, "permissions", permissions);
        set(owner, "complexity", new Avatar.Instructions(10000));
    }

    private static void testLuaAndExtraction(Avatar first, Avatar second, FiguraTexture texture,
                                              FiguraTexture secondTexture, RecordingEngine engine) throws Exception {
        var types = new LuaTypeManager();
        types.generateMetatableFor(ParticleAPI.class);
        types.generateMetatableFor(LuaParticle.class);
        types.generateMetatableFor(FiguraTexture.class);
        types.generateMetatableFor(FiguraVec3.class);
        LuaValue api = types.javaToLua(new ParticleAPI(first)).arg1();
        LuaValue handle = types.javaToLua(texture).arg1();
        LuaValue position = types.javaToLua(FiguraVec3.of(2, 3, 4)).arg1();
        LuaValue result = api.get("newCustomParticle").invoke(LuaValue.varargsOf(
                new LuaValue[]{api, handle, position})).arg1();
        require(engine.particles.size() == 1 && CustomParticleRuntime.liveCount(first.owner) == 1,
                "public Lua constructor reaches the engine and consumes live capacity");
        var particle = (CustomParticle) engine.particles.getFirst();
        reject(() -> result.get("setAnchorTrail").call(result, LuaValue.TRUE));
        require(!result.get("isAnchorTrail").call(result).toboolean(),
                "ordinary avatars cannot borrow a cosmetic controller's private trail source");
        require(first.particlesRemaining.peek() == 15, "original Figura rate permission still applies");
        result.get("spawn").call(result);
        require(engine.particles.size() == 1 && first.particlesRemaining.peek() == 15,
                "repeated custom spawn neither duplicates nor consumes rate quota");
        var sheet = new LuaTable();
        sheet.set("columns", 2);
        sheet.set("frameTimeTicks", 2);
        result.get("setSpriteSheet").call(result, sheet);
        result.get("setFrame").call(result, LuaValue.valueOf(1));
        result.get("setPaused").call(result, LuaValue.TRUE);
        result.get("setEmissive").call(result, LuaValue.TRUE);
        require(result.get("getFrame").call(result).toint() == 1
                        && result.get("isPaused").call(result).toboolean()
                        && result.get("isEmissive").call(result).toboolean(),
                "new settings are reachable through production Lua type conversion");
        particle.setParticleSpeed(1, 0, -2);
        particle.tick();
        var state = new RecordingQuads();
        particle.extract(state, new FixtureCamera(), .5f);
        require(state.count == 1 && !state.isEmpty(), "native SingleQuadParticle writes its actual quad state");
        near(state.x, 2.5);
        near(state.y, 3);
        near(state.z, 3);
        near(state.u0, .5);
        near(state.u1, 1);
        near(state.v0, 0);
        near(state.v1, 1);
        require(state.light == 0xF000F0 && state.layer.textureAtlasLocation().equals(texture.getLocation()),
                "quad uses author texture identifier and independent full-bright light coordinates");
        var gpu = texture.getTexture();
        texture.close();
        result.get("remove").call(result);
        require(!gpu.isClosed() && CustomParticleRuntime.liveCount(first.owner) == 0,
                "expired particle returns capacity while the extracted frame retains the retired texture");
        CustomParticleRuntime.finishFrame();
        require(gpu.isClosed(), "frame retirement releases the final native texture binding");
        reject(() -> api.get("newCustomParticle").invoke(LuaValue.varargsOf(new LuaValue[]{api,
                types.javaToLua(secondTexture).arg1(), position})));
        require(CustomParticleRuntime.liveCount(second.owner) == 0, "foreign texture rejection allocates no particle");
        var secondApi = types.javaToLua(new ParticleAPI(second)).arg1();
        secondApi.get("newTexturedParticle").invoke(LuaValue.varargsOf(new LuaValue[]{secondApi,
                types.javaToLua(secondTexture).arg1(), position}));
        require(engine.particles.size() == 2 && engine.particles.getLast() instanceof CustomParticle,
                "textured convenience entry uses the same native implementation");
        var nativeSecond = (CustomParticle) engine.particles.getLast();
        nativeSecond.setLifetime(1);
        for (int tick = 0; tick < 3; tick++) {
            nativeSecond.tick();
        }
        require(!nativeSecond.isAlive() && CustomParticleRuntime.liveCount(second.owner) == 0,
                "native tick lifetime returns capacity without requiring another render");
        int submitted = engine.particles.size();
        var direct = new ParticleAPI(second);
        set(second, "particlesRemaining", new RefilledNumber(0));
        require(!direct.newCustomParticle(secondTexture, FiguraVec3.of(0, 0, 0), null).isAlive()
                        && engine.particles.size() == submitted,
                "permission-denied custom particle is not alive or submitted");
        set(second, "particlesRemaining", new RefilledNumber(16));
        var pause = Minecraft.class.getDeclaredField("pause");
        pause.setAccessible(true);
        boolean previousPause = pause.getBoolean(Minecraft.getInstance());
        try {
            pause.setBoolean(Minecraft.getInstance(), true);
            var pending = direct.newCustomParticle(secondTexture, FiguraVec3.of(0, 0, 0), null);
            require(!pending.isAlive() && second.particlesRemaining.peek() == 16,
                    "paused creation keeps capacity and original rate budget untouched");
            pause.setBoolean(Minecraft.getInstance(), false);
            var currentWorld = Minecraft.getInstance().level;
            try {
                Minecraft.getInstance().level = null;
                pending.spawn();
                require(!pending.isAlive() && engine.particles.size() == submitted,
                        "pending particle from a retired world cannot enter a disconnected engine");
                reject(() -> direct.newCustomParticle(secondTexture, FiguraVec3.of(0, 0, 0), null));
            } finally {
                Minecraft.getInstance().level = currentWorld;
                pending.remove();
            }
        } finally {
            pause.setBoolean(Minecraft.getInstance(), previousPause);
        }
        engine.failNext = true;
        reject(() -> direct.newCustomParticle(secondTexture, FiguraVec3.of(0, 0, 0), null));
        require(CustomParticleRuntime.liveCount(second.owner) == 0 && engine.particles.size() == submitted,
                "engine submission failure returns capacity and texture lease");
        var controls = direct.newCustomParticle(secondTexture, FiguraVec3.of(0, 0, 0), null);
        reject(() -> controls.setLifetime(0));
        reject(() -> controls.setGravity(Float.NaN));
        reject(() -> controls.setScale(0));
        reject(() -> controls.setColor(Double.NaN, 1.0, 1.0, 1.0));
        reject(() -> controls.setPos(Double.POSITIVE_INFINITY, 0.0, 0.0));
        second.resourceReloadEvent();
        require(!controls.isAlive() && CustomParticleRuntime.liveCount(second.owner) == 0,
                "resource reload invalidates the actual returned Lua particle");
    }

    private static void testSharedLimits(Avatar first, Avatar second, FiguraTexture retired,
                                          FiguraTexture secondTexture) throws Exception {
        var wearer = UUID.randomUUID();
        var limits = new OutfitLimits(32, 128, 64, 8192, 32768, 1024, 65536, 8, 256,
                128, 128, 512, 64, 256, 128, 4096, 36000, .001, 64, true, 8, 15, 16,
                1, 4, 4096, 64, 32);
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "0".repeat(64), OutfitConfig.Format.FIGURA);
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("back_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        var firstContext = new CosmeticInstanceContext(wearer, UUID.randomUUID(), wearer, instance,
                limits, CosmeticInstanceContext.Scope.OWNER);
        var secondInstance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 2, true,
                instance.anchor(), instance.transform(), List.of(), Map.of(), Map.of());
        var secondContext = new CosmeticInstanceContext(wearer, firstContext.outfitId(), wearer, secondInstance,
                limits, CosmeticInstanceContext.Scope.OWNER);
        set(first, "cosmeticContext", firstContext);
        set(second, "cosmeticContext", secondContext);
        var firstTexture = new FiguraTexture(first, "capacity", new NativeImage(1, 1, true));
        var a = new CustomParticle(first, Minecraft.getInstance().level, firstTexture, Vec3.ZERO, Vec3.ZERO);
        var b = new CustomParticle(second, Minecraft.getInstance().level, secondTexture, Vec3.ZERO, Vec3.ZERO);
        try {
            require(a.activate() && !b.activate() && CustomParticleRuntime.liveCount(wearer) == 1,
                    "different execution instances share the negotiated wearer live limit");
            CustomParticleRuntime.clearOwner(first.owner);
            require(!a.isAlive() && b.activate() && CustomParticleRuntime.liveCount(wearer) == 1,
                    "clearing one execution returns capacity and leaves another instance eligible");
            CustomParticleRuntime.clearOwner(first.owner);
            require(b.isAlive(), "cleanup of an already removed duplicate does not kill another execution");
            b.remove();
            b.remove();
            require(CustomParticleRuntime.liveCount(wearer) == 0, "capacity release is idempotent");
            reject(() -> new CustomParticle(first, Minecraft.getInstance().level, retired,
                    Vec3.ZERO, Vec3.ZERO).activate());
            require(CustomParticleRuntime.liveCount(wearer) == 0, "retired texture failure cannot leak capacity");
            testNegotiatedLayerLimits(second, secondTexture, secondContext, secondInstance, limits);
            var gson = new com.google.gson.Gson();
            var zeroJson = gson.toJsonTree(limits).getAsJsonObject();
            zeroJson.addProperty("maxCustomParticles", 0);
            var zero = gson.fromJson(zeroJson, OutfitLimits.class);
            set(second, "cosmeticContext", new CosmeticInstanceContext(wearer, firstContext.outfitId(), wearer,
                    secondInstance, zero, CosmeticInstanceContext.Scope.OWNER));
            require(!new CustomParticle(second, Minecraft.getInstance().level, secondTexture,
                    Vec3.ZERO, Vec3.ZERO).activate(), "zero negotiated live capacity disables activation");
            zeroJson = gson.toJsonTree(limits).getAsJsonObject();
            zeroJson.addProperty("maxParticleLayers", 0);
            zero = gson.fromJson(zeroJson, OutfitLimits.class);
            set(second, "cosmeticContext", new CosmeticInstanceContext(wearer, firstContext.outfitId(), wearer,
                    secondInstance, zero, CosmeticInstanceContext.Scope.OWNER));
            require(!new CustomParticle(second, Minecraft.getInstance().level, secondTexture,
                    Vec3.ZERO, Vec3.ZERO).activate(), "zero negotiated texture layers disables custom draws");
            set(second, "cosmeticContext", new CosmeticInstanceContext(wearer, firstContext.outfitId(), wearer,
                    secondInstance, limits, CosmeticInstanceContext.Scope.DRAFT));
            require(!new CustomParticle(second, Minecraft.getInstance().level, secondTexture,
                    Vec3.ZERO, Vec3.ZERO).activate(), "draft effects cannot enter the live world engine");
        } finally {
            a.remove();
            b.remove();
            firstTexture.close();
            set(first, "cosmeticContext", null);
            set(second, "cosmeticContext", null);
        }
    }

    private static void testNegotiatedLayerLimits(Avatar owner, FiguraTexture texture,
                                                  CosmeticInstanceContext context, OutfitConfig.Instance instance,
                                                  OutfitLimits limits) throws Exception {
        var gson = new com.google.gson.Gson();
        for (int maximum : new int[]{1, 8}) {
            var json = gson.toJsonTree(limits).getAsJsonObject();
            json.addProperty("maxParticleLayers", maximum);
            var negotiated = gson.fromJson(json, OutfitLimits.class);
            set(owner, "cosmeticContext", new CosmeticInstanceContext(context.accountId(), context.outfitId(),
                    context.wearerId(), instance, negotiated, CosmeticInstanceContext.Scope.OWNER));
            var particle = new CustomParticle(owner, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
            try {
                require(particle.activate(), "negotiated layer fixture activates");
                var layers = new LuaTable();
                for (int index = 1; index <= maximum; index++) {
                    layers.set(index, new LuaTable());
                }
                particle.setLayers(layers);
                particle.setEmissive(true);
                var nativeState = new RecordingQuads();
                particle.extract(nativeState, new FixtureCamera(), 0);
                require(nativeState.count == maximum, "negotiated layer limit reaches actual native extraction");
                layers.set(maximum + 1, new LuaTable());
                reject(() -> particle.setLayers(layers));
                require(particle.layerCount() == maximum && particle.active(),
                        "over-limit layer update preserves the previously accepted definition");
            } finally {
                particle.remove();
                CustomParticleRuntime.finishFrame();
            }
        }
    }

    private static void testLifecycleHooks(Avatar first, Avatar second, FiguraTexture texture,
                                             RecordingEngine engine) throws Exception {
        var firstTexture = new FiguraTexture(first, "eviction", new NativeImage(1, 1, true));
        var a = new CustomParticle(first, Minecraft.getInstance().level, firstTexture, Vec3.ZERO, Vec3.ZERO);
        var b = new CustomParticle(second, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
        try {
            require(a.activate() && b.activate(), "two wearer fixture particles activate");
            engine.particles.add(a);
            var queue = com.google.common.collect.EvictingQueue.<Particle>create(1);
            queue.add(a);
            var hook = new top.aihmc.omnimorph.mixin.particle.ParticleGroupMixin();
            var queueField = hook.getClass().getDeclaredField("particles");
            queueField.setAccessible(true);
            queueField.set(hook, queue);
            var engineField = hook.getClass().getDeclaredField("engine");
            engineField.setAccessible(true);
            engineField.set(hook, engine);
            var evict = hook.getClass().getDeclaredMethod("figura$releaseEvictedCustomParticle",
                    Particle.class, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
            evict.setAccessible(true);
            evict.invoke(hook, b, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("add", false));
            queue.add(b);
            require(!a.isAlive() && b.isAlive() && !engine.particles.contains(a)
                            && CustomParticleRuntime.liveCount(first.owner) == 0,
                    "production eviction hook releases the displaced custom particle and owner tracking");
            second.resourceReloadEvent();
            require(!b.isAlive() && CustomParticleRuntime.liveCount(second.owner) == 0,
                    "author resource reload clears only its execution's custom particles");
            var c = new CustomParticle(second, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
            require(c.activate(), "capacity is usable after reload");
            var engineHook = new org.figuramc.figura.mixin.particle.ParticleEngineMixin() {
                @Override
                protected <T extends ParticleOptions> Particle makeParticle(T options, double x, double y,
                        double z, double vx, double vy, double vz) {
                    return null;
                }

                @Override
                public void add(Particle particle) {
                }

                @Override
                public SpriteSet figura$getParticleSprite(Identifier id) {
                    return null;
                }
            };
            engineHook.figura$spawnParticle(c, second.owner);
            var clear = org.figuramc.figura.mixin.particle.ParticleEngineMixin.class.getDeclaredMethod(
                    "figura$clearCustomParticles", org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
            clear.setAccessible(true);
            clear.invoke(engineHook, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("clearParticles", false));
            require(!c.isAlive() && CustomParticleRuntime.liveCount(second.owner) == 0,
                    "production engine clear hook releases pending custom particles on world reset");
        } finally {
            a.remove();
            b.remove();
            firstTexture.close();
        }
    }

    private static final class FixtureLevel extends ClientLevel implements org.figuramc.figura.mixin.ClientLevelInvoker {
        private FixtureLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public boolean hasChunkAt(net.minecraft.core.BlockPos pos) {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity> getEntityGetter() {
            return (net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity>)
                    java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{net.minecraft.world.level.entity.LevelEntityGetter.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("get")) {
                                    return null;
                                }
                                throw new UnsupportedOperationException("No entity lookup traversal in this fixture");
                            });
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

    private static final class RecordingQuads extends QuadParticleRenderState {
        private int count;
        private float x, y, z, u0, u1, v0, v1;
        private int light;
        private SingleQuadParticle.Layer layer;

        @Override
        public void add(SingleQuadParticle.Layer layer, float x, float y, float z,
                        float qx, float qy, float qz, float qw, float size,
                        float u0, float u1, float v0, float v1, int color, int light) {
            super.add(layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
            count++;
            this.x = x;
            this.y = y;
            this.z = z;
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
            this.light = light;
            this.layer = layer;
        }
    }

    private static final class RecordingEngine extends ParticleEngine implements ParticleEngineAccessor {
        private List<Particle> particles;
        private boolean failNext;

        private RecordingEngine() {
            super(null, null);
        }

        @Override
        public void figura$spawnParticle(Particle particle, UUID owner) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("fixture particle submission failure");
            }
            particles.add(particle);
        }

        @Override
        public <T extends ParticleOptions> Particle figura$makeParticle(T options, double x, double y, double z,
                                                                       double vx, double vy, double vz) {
            throw new UnsupportedOperationException("No vanilla factory used for custom particles");
        }

        @Override
        public void figura$clearParticles(UUID owner) {
            CustomParticleRuntime.clearOwner(owner);
        }

        @Override
        public SpriteSet figura$getParticleSprite(Identifier id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void figura$removeParticle(Particle particle) {
            particles.remove(particle);
        }

        @Override
        public ParticleResources figura$getParticleResources() {
            throw new UnsupportedOperationException();
        }
    }

    private static void set(Avatar target, String fieldName, Object value) throws Exception {
        var field = Avatar.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid custom particle operation was accepted");
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < .000001, "Native quad value " + actual + " != " + expected);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
