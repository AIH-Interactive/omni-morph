package top.aihmc.omnimorph.lua.api.particle;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.LuaTypeManager;
import top.aihmc.omnimorph.mixin.particle.ParticleLightRegionAccessor;
import top.aihmc.omnimorph.mixin.particle.ParticleWorldLightMixin;
import top.aihmc.omnimorph.mixin.particle.ParticleEntityLightMixin;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Production light registry and hook body, native particle lifecycle and recorded native section invalidation. */
final class ParticleLightNativeSmokeTest {
    static void run(Unsafe unsafe, Avatar first, Avatar second, Runnable drain) throws Exception {
        var client = Minecraft.getInstance();
        var rendererField = Minecraft.class.getDeclaredField("levelRenderer");
        rendererField.setAccessible(true);
        Object previousRenderer = rendererField.get(client);
        var renderer = (RecordingRenderer) unsafe.allocateInstance(RecordingRenderer.class);
        renderer.dirty = new HashSet<>();
        var texture = new FiguraTexture(first, "particle-light", new NativeImage(1, 1, true));
        var other = new FiguraTexture(second, "particle-light", new NativeImage(1, 1, true));
        try {
            rendererField.set(client, renderer);
            luaWorldAndMovement(unsafe, first, texture, renderer);
            sharedPolicy(first, second, texture, other, renderer);
        } finally {
            CustomParticleRuntime.clearOwner(first.owner);
            CustomParticleRuntime.clearOwner(second.owner);
            CustomParticleRuntime.finishFrame();
            texture.close();
            other.close();
            drain.run();
            rendererField.set(client, previousRenderer);
        }
        System.out.println("PASS native particle lights: Lua settings, production world hook/region identity, "
                + "section invalidation, interpolated entities, shared count/settings policy and cleanup; GPU/game MANUAL/PENDING");
    }

    private static void luaWorldAndMovement(Unsafe unsafe, Avatar owner, FiguraTexture texture,
                                            RecordingRenderer renderer) throws Exception {
        var world = Minecraft.getInstance().level;
        var particle = new CustomParticle(owner, world, texture, new Vec3(.5, .5, .5), new Vec3(2, 0, 0));
        try {
            require(particle.activate(), "ordinary light fixture activates");
            var types = new LuaTypeManager();
            types.generateMetatableFor(LuaParticle.class);
            var wrapper = types.javaToLua(new LuaParticle("dynamic light", particle, owner)).arg1();
            wrapper.get("setDynamicLight").call(wrapper, LuaValue.TRUE);
            require(wrapper.get("isDynamicLight").call(wrapper).toboolean()
                            && wrapper.get("getLightLevel").call(wrapper).toint() == 15
                            && wrapper.get("getLightRadius").call(wrapper).todouble() == 7.5 && !particle.emissive(),
                    "public Lua switch and defaults illuminate independently of emissive");
            int sky = 8 << 20;
            require(worldHook(world, BlockPos.ZERO, sky) == (sky | 240) && !renderer.dirty.isEmpty(),
                    "production world hook adds actual block light and schedules terrain recompilation");
            var entity = (ProbeEntity) unsafe.allocateInstance(ProbeEntity.class);
            entity.world = world;
            entity.probe = new Vec3(.5, .5, .5);
            require(entityHook(entity, sky) == (sky | 240), "production entity hook applies the same source at the native probe");
            entity.world = null;
            require(entityHook(entity, sky) == sky, "entity in another/retired world retains its native lighting");
            entity.world = world;
            var region = (BlockAndLightGetter) Proxy.newProxyInstance(ParticleLightNativeSmokeTest.class.getClassLoader(),
                    new Class<?>[]{BlockAndLightGetter.class, ParticleLightRegionAccessor.class}, (proxy, method, args) -> {
                        if (method.getName().equals("figura$lightLevel")) {
                            return world;
                        }
                        throw new AssertionError("Render-light lookup must not modify/query the server light engine");
                    });
            require(worldHook(region, BlockPos.ZERO, sky) == (sky | 240),
                    "chunk compilation region resolves the owning world through the production accessor boundary");
            reject(() -> wrapper.get("setLightLevel").call(wrapper, LuaValue.valueOf(Double.NaN)));
            reject(() -> wrapper.get("setLightLevel").call(wrapper, LuaValue.valueOf(2.5)));
            reject(() -> wrapper.get("setLightRadius").call(wrapper, LuaValue.valueOf(Double.POSITIVE_INFINITY)));
            CustomParticleRuntime.beginTick();
            particle.tick();
            ParticleDynamicLights.updateFrame(.5f);
            require(ParticleDynamicLights.apply(world, 1.5, .5, .5, sky) == (sky | 240)
                            && worldHook(world, new BlockPos(2, 0, 0), sky) == (sky | 240),
                    "entity lighting follows interpolated head while terrain uses stable tick positions");
            renderer.dirty.clear();
            ParticleDynamicLights.updateFrame(.75f);
            require(renderer.dirty.isEmpty() && ParticleDynamicLights.apply(world, 2, .5, .5, sky) == (sky | 240),
                    "extra render frames interpolate without re-dirtying terrain every frame");
            particle.setPos(64.5, .5, .5);
            CustomParticleRuntime.beginTick();
            ParticleDynamicLights.updateFrame(1);
            require(renderer.dirty.contains(new ParticleLightField.Section(0, 0, 0))
                            && renderer.dirty.contains(new ParticleLightField.Section(4, 0, 0)),
                    "teleport invalidates old and new affected sections");
            require(ParticleDynamicLights.apply(world, 2.5, .5, .5, sky) == sky
                            && ParticleDynamicLights.apply(world, 64.5, .5, .5, sky) == (sky | 240),
                    "teleport leaves no light at the former source");
            particle.setEmissive(true);
            wrapper.get("setDynamicLight").call(wrapper, LuaValue.FALSE);
            require(particle.emissive() && ParticleDynamicLights.liveCount(owner.owner) == 0
                            && worldHook(world, new BlockPos(64, 0, 0), sky) == sky,
                    "disable restores world lighting while emissive remains independently enabled");
            var light = new LuaTable();
            light.set("level", 10);
            light.set("radius", 4);
            var ribbon = new LuaTable();
            ribbon.set("light", light);
            wrapper.get("setRibbon").call(wrapper, ribbon);
            var layers = new LuaTable();
            layers.set(1, new LuaTable());
            layers.set(2, new LuaTable());
            particle.setLayers(layers);
            particle.setRenderMode("RIBBON");
            require(particle.dynamicLight() && particle.lightLevel() == 10 && ParticleDynamicLights.liveCount(owner.owner) == 1,
                    "Ribbon alias and multiple texture layers reuse exactly one source");
            particle.setLifetime(1);
            particle.tick();
            require(!particle.active() && ParticleDynamicLights.liveCount(owner.owner) == 0,
                    "ordinary native lifetime expiry releases the source immediately");
            var reload = new CustomParticle(owner, world, texture, new Vec3(.5, .5, .5), Vec3.ZERO);
            require(reload.activate(), "reload cleanup fixture activates");
            reload.setDynamicLight(true);
            owner.resourceReloadEvent();
            require(!reload.active() && ParticleDynamicLights.liveCount(owner.owner) == 0
                            && worldHook(world, BlockPos.ZERO, sky) == sky,
                    "actual resource reload releases light ownership and restores former terrain probes");
        } finally {
            particle.remove();
        }
    }

    private static void sharedPolicy(Avatar first, Avatar second, FiguraTexture texture, FiguraTexture other,
                                     RecordingRenderer renderer) throws Exception {
        var beforeFirst = first.cosmeticContext;
        var beforeSecond = second.cosmeticContext;
        UUID wearer = UUID.randomUUID();
        var limits = policy(true, 1, 12, 4);
        context(first, wearer, limits);
        context(second, wearer, limits);
        var a = new CustomParticle(first, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
        var b = new CustomParticle(second, Minecraft.getInstance().level, other, Vec3.ZERO, Vec3.ZERO);
        try {
            for (var particle : List.of(a, b)) {
                particle.setLightLevel(12);
                particle.setLightRadius(4);
                particle.setDynamicLight(true);
            }
            require(a.activate() && !b.activate() && ParticleDynamicLights.liveCount(wearer) == 1,
                    "pending sources share the negotiated wearer capacity before activation");
            reject(() -> a.setLightLevel(13));
            reject(() -> a.setLightRadius(5));
            require(a.lightLevel() == 12 && a.lightRadius() == 4, "rejected settings preserve the active source");
            b.setDynamicLight(false);
            require(b.activate(), "another execution can remain alive without a light");
            reject(() -> b.setDynamicLight(true));
            require(!b.dynamicLight() && ParticleDynamicLights.liveCount(wearer) == 1, "failed enable is atomic");
            a.remove();
            b.setDynamicLight(true);
            CustomParticleRuntime.clearOwner(first.owner);
            require(b.dynamicLight() && ParticleDynamicLights.liveCount(wearer) == 1,
                    "cleanup of one execution does not clear a sibling's light");
            context(first, wearer, policy(true, 2, 12, 4));
            context(second, wearer, policy(true, 2, 12, 4));
            var extra = new CustomParticle(first, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
            try {
                extra.setLightLevel(12);
                extra.setLightRadius(4);
                extra.setDynamicLight(true);
                require(extra.activate() && ParticleDynamicLights.liveCount(wearer) == 2,
                        "negotiated increased capacity can retain two independent sources");
                context(first, wearer, limits);
                context(second, wearer, limits);
                ParticleDynamicLights.updateFrame(1);
                require(ParticleDynamicLights.liveCount(wearer) == 1
                                && (b.dynamicLight() ? 1 : 0) + (extra.dynamicLight() ? 1 : 0) == 1,
                        "lowering the negotiated count retires excess actual sources");
            } finally {
                extra.remove();
            }
            b.setDynamicLight(false);
            b.setDynamicLight(true);
            context(second, wearer, policy(true, 1, 8, 4));
            ParticleDynamicLights.updateFrame(1);
            require(!b.dynamicLight() && ParticleDynamicLights.liveCount(wearer) == 0,
                    "a reduced brightness ceiling retires the former higher-level source");
            context(second, wearer, limits);
            b.setDynamicLight(true);
            renderer.dirty.clear();
            context(second, wearer, policy(false, 1, 12, 4));
            ParticleDynamicLights.updateFrame(1);
            require(!b.dynamicLight() && b.active() && ParticleDynamicLights.liveCount(wearer) == 0 && !renderer.dirty.isEmpty(),
                    "backend disable retires illumination and rebuilds former sections without killing the particle");
            reject(() -> b.setDynamicLight(true));
            context(second, wearer, policy(true, 0, 12, 4));
            reject(() -> b.setDynamicLight(true));
            context(second, wearer, limits);
            b.setDynamicLight(true);
            var world = Minecraft.getInstance().level;
            try {
                Minecraft.getInstance().level = null;
                ParticleDynamicLights.updateFrame(1);
                require(!b.dynamicLight() && ParticleDynamicLights.liveCount(wearer) == 0
                                && ParticleDynamicLights.apply(world, 0, 0, 0, 0) == 0,
                        "world disconnect releases counts and atomically drops the former world snapshot");
            } finally {
                Minecraft.getInstance().level = world;
            }
        } finally {
            a.remove();
            b.remove();
            setContext(first, beforeFirst);
            setContext(second, beforeSecond);
        }
    }

    private static int worldHook(BlockAndLightGetter world, BlockPos position, int original) throws Exception {
        var hook = ParticleWorldLightMixin.class.getDeclaredMethod("figura$particleWorldLight", int.class,
                LevelRenderer.BrightnessGetter.class, BlockAndLightGetter.class, BlockState.class, BlockPos.class);
        hook.setAccessible(true);
        return (int) hook.invoke(null, original, null, world, null, position);
    }

    private static int entityHook(Entity entity, int original) throws Exception {
        var hook = ParticleEntityLightMixin.class.getDeclaredMethod("figura$particleEntityLight", int.class,
                Entity.class, float.class);
        hook.setAccessible(true);
        return (int) hook.invoke(new ParticleEntityLightMixin() {}, original, entity, 1f);
    }

    private static OutfitLimits policy(boolean enabled, int count, int level, double radius) {
        var gson = new Gson();
        var json = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("dynamicLightingEnabled", enabled);
        json.addProperty("maxDynamicLights", count);
        json.addProperty("maxDynamicLightLevel", level);
        json.addProperty("maxDynamicLightRadius", radius);
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

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected light policy/setting rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingRenderer extends LevelRenderer {
        private Set<ParticleLightField.Section> dirty;

        private RecordingRenderer() {
            super(null, null, null, null, null, null);
        }

        @Override
        public void setSectionDirty(int x, int y, int z) {
            dirty.add(new ParticleLightField.Section(x, y, z));
        }
    }

    private static final class ProbeEntity extends ArmorStand {
        private ClientLevel world;
        private Vec3 probe;

        private ProbeEntity() {
            super(null, 0, 0, 0);
        }

        @Override
        public Level level() {
            return world;
        }

        @Override
        public Vec3 getLightProbePosition(float partialTick) {
            return probe;
        }
    }
}
