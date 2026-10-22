package top.aihmc.omnimorph.lua.api.particle;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.LuaTypeManager;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitTrailBudget;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import top.aihmc.omnimorph.model.outfit.SkeletonPose;
import top.aihmc.omnimorph.model.outfit.VanillaSkeletonAdapter;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Production controller samples become native particle vertices; no rendered game pixels are asserted. */
final class AnchorTrailNativeSmokeTest {
    static void run(Unsafe unsafe, Runnable drain) throws Exception {
        Minecraft client = Minecraft.getInstance();
        var previousWorld = client.level;
        var world = (World) unsafe.allocateInstance(World.class);
        var wearer = (Wearer) unsafe.allocateInstance(Wearer.class);
        wearer.setUUID(UUID.randomUUID());
        wearer.world = world;
        wearer.position(new Vec3(10, 20, 30));
        world.clock = 10;
        var gson = new Gson();
        var json = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxTrails", 1);
        var limits = gson.fromJson(json, OutfitLimits.class);
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "8".repeat(64), OutfitConfig.Format.FIGURA);
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null),
                OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(wearer.getUUID(), UUID.randomUUID(), wearer.getUUID(),
                instance, limits, CosmeticInstanceContext.Scope.OWNER);
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.getUUID().toString());
        permissions.insert(Permissions.COMPLEXITY, 10000, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        Avatar owner = Avatar.createCosmetic(context, permissions, "anchor ribbon native fixture");
        var data = new CompoundTag();
        var metadata = new CompoundTag();
        metadata.putString("cosmetic_json", "{\"category\":\"Trail\"}");
        data.put("metadata", metadata);
        owner.load(data);
        var config = new OutfitConfig(1, 0, null, List.of(instance), List.of(), Map.of(resource.identity(), 1), false);
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        SkeletonPose pose = new VanillaSkeletonAdapter(model, limits).capture(new Matrix4f());
        var texture = new FiguraTexture(owner, "anchor-trail", new NativeImage(1, 1, true));
        client.level = world;
        try (var composition = new OutfitComposition(config, limits, wearer,
                Map.of(instance.instanceId(), owner), new SkeletonIndex.Cache())) {
            var particle = new CustomParticle(owner, world, texture, new Vec3(-999, -999, -999), Vec3.ZERO);
            var independent = new CustomParticle(owner, world, texture, Vec3.ZERO, Vec3.ZERO);
            try {
                var types = new LuaTypeManager();
                types.generateMetatableFor(LuaParticle.class);
                var wrapper = types.javaToLua(new LuaParticle("anchor-trail", particle, owner)).arg1();
                wrapper.get("setAnchorTrail").call(wrapper, LuaValue.TRUE);
                wrapper.get("setRenderMode").call(wrapper, LuaValue.valueOf("Ribbon"));
                wrapper.get("setEmissive").call(wrapper, LuaValue.TRUE);
                require(wrapper.get("isAnchorTrail").call(wrapper).toboolean() && particle.activate(),
                        "actual Lua settings opt into this instance's controller history");
                require(OutfitTrailBudget.count(wearer.getUUID()) == 0 && extract().particles.isEmpty(),
                        "an attached particle does not create a second history or invent anchor samples");
                particle.setLifetime(100);
                particle.setDynamicLight(true);
                require(ParticleDynamicLights.liveCount(wearer.getUUID()) == 1
                                && ParticleDynamicLights.apply(world, -999, -999, -999, 0) == 0,
                        "waiting attached light keeps its quota without illuminating the unrelated particle position");
                draw(composition, pose);
                require(composition.trailSamples(instance.instanceId()).size() == 1
                                && OutfitTrailBudget.count(wearer.getUUID()) == 1 && extract().particles.isEmpty(),
                        "controller owns one slot and first history point draws no segment");
                world.clock = 11;
                wearer.position(new Vec3(11, 20, 30));
                draw(composition, pose);
                var extracted = extract();
                require(extracted.particles.size() == 1 && extracted.particles.getFirst() instanceof RibbonRenderState,
                        "actual controller/Lua world samples reach the native Ribbon particle group");
                var ribbon = (RibbonRenderState) extracted.particles.getFirst();
                byte[] frozen = vertices(ribbon);
                var buffer = java.nio.ByteBuffer.wrap(frozen).order(ByteOrder.nativeOrder());
                int stride = DefaultVertexFormat.PARTICLE.getVertexSize();
                for (int index = 0; index < 4; index++) {
                    float x = buffer.getFloat(index * stride);
                    require(x >= 9.9 && x <= 11.1, "native vertex follows the world anchor, not particle position: " + x);
                }
                var nodes = new SubmitNodeStorage();
                extracted.submit(nodes, new CameraRenderState());
                require(nodes.getSubmitsPerOrder().get(0).getParticleGroupRenderers().getFirst() == ribbon,
                        "controller Ribbon reaches the same native submit queue as ordinary Ribbon");
                ParticleDynamicLights.updateFrame(1);
                require(ParticleDynamicLights.apply(world, 11, 20.75, 30, 0) > 0,
                        "attached dynamic light follows the controller head");
                independent.setRenderMode("Ribbon");
                require(!independent.activate(), "controller and attached renderers cannot bypass the one-history limit");
                reject(() -> wrapper.get("setAnchorTrail").call(wrapper, LuaValue.FALSE));
                require(particle.anchorTrail(), "failed transition to an independent history preserves the source");
                independent.setAnchorTrail(true);
                independent.setEmissive(true);
                require(independent.activate() && extract().particles.size() == 2
                                && OutfitTrailBudget.count(wearer.getUUID()) == 1,
                        "two bounded particle renderers may share one controller trajectory without resampling it");
                independent.remove();
                require(particle.active() && OutfitTrailBudget.count(wearer.getUUID()) == 1,
                        "removing an attached renderer cannot release the controller's trajectory slot");
                try (var invalid = composition.retain()) {
                    invalid.draw(pose.restrict(index -> false), new AvatarRenderState(), 0,
                            EntityRenderMode.RENDER, ignored -> {});
                }
                require(extract().particles.isEmpty() && OutfitTrailBudget.count(wearer.getUUID()) == 0,
                        "target loss removes controller drawing without recreating private history");
                ParticleDynamicLights.updateFrame(1);
                require(ParticleDynamicLights.apply(world, 11, 20.75, 30, 0) == 0 && particle.dynamicLight(),
                        "missing anchor withdraws illumination while retaining the configured switch");
                world.clock = 12;
                wearer.position(new Vec3(12, 20, 30));
                draw(composition, pose);
                world.clock = 13;
                wearer.position(new Vec3(13, 20, 30));
                draw(composition, pose);
                require(extract().particles.size() == 1, "healthy controller history restores rendering without replacing particle");
                world.clock = 14;
                wearer.position(new Vec3(1000, 20, 30));
                draw(composition, pose);
                require(extract().particles.isEmpty(), "teleport reset cannot produce a cross-map Ribbon");
                texture.close();
                composition.close();
                require(!particle.active() && CustomParticleRuntime.liveCount(wearer.getUUID()) == 0
                                && OutfitTrailBudget.count(wearer.getUUID()) == 0
                                && ParticleDynamicLights.liveCount(wearer.getUUID()) == 0,
                        "composition retirement clears attached particles, sources and the controller slot");
                require(Arrays.equals(frozen, vertices(ribbon)), "native retained geometry survives controller/owner retirement");
            } finally {
                particle.remove();
                independent.remove();
            }
        } finally {
            owner.clean();
            CustomParticleRuntime.finishFrame();
            texture.close();
            drain.run();
            client.level = previousWorld;
        }
        System.out.println("PASS attached controller Ribbon: actual Lua/frame sampling, native vertices/submission, "
                + "one history slot, failed transition, target/teleport/retirement and head light; pixels MANUAL/PENDING");
    }

    private static void draw(OutfitComposition composition, SkeletonPose pose) {
        try (var frame = composition.retain()) {
            frame.draw(pose, new AvatarRenderState(), 0, EntityRenderMode.RENDER, ignored -> {});
        }
    }

    private static ParticlesRenderState extract() {
        YsmRenderFrameClock.beginFrame();
        var state = new ParticlesRenderState();
        CustomParticleRuntime.extractRibbons(state, null, new FixtureCamera(), 0);
        return state;
    }

    private static byte[] vertices(RibbonRenderState ribbon) {
        try (var storage = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.PARTICLE.getVertexSize())) {
            var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            ribbon.writeVertices(builder);
            try (var mesh = builder.build()) {
                require(mesh != null && mesh.drawState().vertexCount() == 4, "one real segment has four native vertices");
                byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                mesh.vertexBuffer().get(bytes);
                return bytes;
            }
        }
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected attached trail capacity rejection");
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
            return Vec3.ZERO;
        }
    }

    private static final class World extends ClientLevel {
        private long clock;

        private World() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public long getGameTime() {
            return clock;
        }
    }

    private static final class Wearer extends RemotePlayer implements top.aihmc.omnimorph.mixin.EntityAccessor {
        private World world;

        private Wearer() {
            super(null, new GameProfile(UUID.randomUUID(), "anchor-trail"));
        }

        @Override
        public Level level() {
            return world;
        }

        @Override
        public Level getLevel() {
            return world;
        }

        private void position(Vec3 position) throws Exception {
            xo = position.x;
            yo = position.y;
            zo = position.z;
            var field = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
            field.setAccessible(true);
            field.set(this, position);
        }
    }
}
