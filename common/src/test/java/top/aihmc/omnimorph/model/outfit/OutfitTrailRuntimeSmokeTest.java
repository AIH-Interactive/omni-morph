package top.aihmc.omnimorph.model.outfit;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Production composition/Lua sampling with a controlled entity clock, not a rendered world. */
public final class OutfitTrailRuntimeSmokeTest {
    private static final class World extends ClientLevel {
        long clock;

        private World() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public long getGameTime() {
            return clock;
        }
    }

    private static final class Wearer extends RemotePlayer implements top.aihmc.omnimorph.mixin.EntityAccessor {
        World world;

        private Wearer() {
            super(null, new GameProfile(UUID.randomUUID(), "trail-world-clock"));
        }

        @Override
        public Level level() {
            return world;
        }

        @Override
        public Level getLevel() {
            return world;
        }

        private void position(Vec3 previous, Vec3 current) throws Exception {
            xo = previous.x;
            yo = previous.y;
            zo = previous.z;
            var field = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
            field.setAccessible(true);
            field.set(this, current);
        }
    }

    public static void run() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (Unsafe) field.get(null);
        World world = (World) unsafe.allocateInstance(World.class);
        Wearer wearer = (Wearer) unsafe.allocateInstance(Wearer.class);
        wearer.setUUID(UUID.randomUUID());
        wearer.world = world;
        wearer.position(new Vec3(100, 200, 300), new Vec3(102, 204, 306));
        world.clock = 10;
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "9".repeat(64), OutfitConfig.Format.FIGURA);
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null),
                OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(wearer.getUUID(), UUID.randomUUID(), wearer.getUUID(), instance,
                OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.getUUID().toString());
        Avatar avatar = Avatar.createCosmetic(context, permissions, "world trail sampling");
        var data = new CompoundTag();
        var metadata = new CompoundTag();
        metadata.putString("cosmetic_json", "{\"category\":\"Trail\"}");
        data.put("metadata", metadata);
        avatar.load(data);
        var config = new OutfitConfig(1, 0, null, List.of(instance), List.of(), Map.of(resource.identity(), 1), false);
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, OutfitLimits.defaults());
        var pose = base.capture(new Matrix4f());
        var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
        var disabled = new com.google.gson.Gson().toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        disabled.addProperty("maxTrails", 0);
        try {
            new OutfitComposition(config, new com.google.gson.Gson().fromJson(disabled, OutfitLimits.class), wearer,
                    Map.of(instance.instanceId(), avatar), new SkeletonIndex.Cache());
            throw new AssertionError("negotiated disabled trail capacity was ignored");
        } catch (IllegalArgumentException expected) {
            require(avatar.luaRuntime == null, "trail capacity is checked before author initialization");
        }
        try (var composition = new OutfitComposition(config, OutfitLimits.defaults(), wearer,
                Map.of(instance.instanceId(), avatar), new SkeletonIndex.Cache())) {
            var occupied = new TrailBuffer[OutfitLimits.defaults().maxTrails()];
            try {
                for (int index = 0; index < occupied.length; index++) {
                    occupied[index] = new TrailBuffer(OutfitLimits.defaults());
                    require(OutfitTrailBudget.acquire(occupied[index], wearer.getUUID(), occupied.length),
                            "other trail sources fill the wearer's negotiated capacity");
                }
                try (var denied = composition.retain()) {
                    denied.draw(pose, state, .5f, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).isEmpty()
                                    && OutfitTrailBudget.count(wearer.getUUID()) == occupied.length,
                            "production controller sampling cannot obtain a separate trail quota");
                }
            } finally {
                for (var trail : occupied) {
                    OutfitTrailBudget.release(trail);
                }
            }
            try (var first = composition.retain()) {
                wearer.position(new Vec3(999, 999, 999), new Vec3(999, 999, 999));
                world.clock = 11;
                try {
                    first.draw(pose, state, .5f, EntityRenderMode.RENDER, ignored -> {});
                } catch (RuntimeException failure) {
                    var ownerField = OutfitComposition.class.getDeclaredField("controllerOwner");
                    ownerField.setAccessible(true);
                    Avatar controller = (Avatar) ownerField.get(composition);
                    throw new AssertionError("world trail fixture controller: " + controller.errorText, failure);
                }
                var points = composition.trailSamples(instance.instanceId());
                require(points.size() == 1, "actual built-in Lua appends one world sample");
                require(OutfitTrailBudget.count(wearer.getUUID()) == 1,
                        "successful controller sampling owns exactly one shared slot");
                position(points.getFirst(), 101, 202.75, 303, 10.5);
                first.draw(pose, state, .5f, EntityRenderMode.RENDER, ignored -> {});
                require(composition.trailSamples(instance.instanceId()).size() == 1, "retained replay does not sample twice");
                world.clock = 11;
                wearer.position(new Vec3(101.2, 202, 303), new Vec3(101.2, 202, 303));
                try (var next = composition.retain()) {
                    next.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).size() == 2, "new frame samples current normalized pose");
                }
                try (var hand = composition.retain()) {
                    hand.draw(pose.restrict(index -> false), state, 0, EntityRenderMode.FIRST_PERSON, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).size() == 2,
                            "hand-only or GUI work cannot erase the world trajectory");
                }
                try (var invalid = composition.retain()) {
                    invalid.draw(pose.restrict(index -> false), state, 0, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).isEmpty(), "unavailable world target clears dependent history");
                    require(OutfitTrailBudget.count(wearer.getUUID()) == 0, "target loss returns the shared slot");
                }
                world.clock = 12;
                try (var restored = composition.retain()) {
                    restored.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                }
                wearer.position(new Vec3(10000, 200, 300), new Vec3(10000, 200, 300));
                world.clock = 13;
                try (var teleported = composition.retain()) {
                    teleported.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                    var jumped = composition.trailSamples(instance.instanceId());
                    require(jumped.size() == 1 && jumped.getFirst().x() == 10000, "world teleport starts a fresh trajectory");
                }
                var replacementWorld = (World) unsafe.allocateInstance(World.class);
                replacementWorld.clock = 14;
                wearer.world = replacementWorld;
                composition.tick();
                require(composition.trailSamples(instance.instanceId()).isEmpty(), "dimension/session switch clears without drawing");
                require(OutfitTrailBudget.count(wearer.getUUID()) == 0, "world change returns controller capacity");
                try (var changed = composition.retain()) {
                    changed.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).size() == 1, "new world may sample again");
                }
                try (var failed = composition.retain()) {
                    try {
                        failed.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {
                            throw new IllegalStateException("world trail draw failure");
                        });
                        throw new AssertionError("native world drawing failure was swallowed");
                    } catch (IllegalStateException expected) {
                        require("world trail draw failure".equals(expected.getMessage()), "native drawing failure remains unchanged");
                    }
                    require(composition.trailSamples(instance.instanceId()).isEmpty(), "failed world draw clears its transient history");
                }
                try (var resumed = composition.retain()) {
                    resumed.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).size() == 1, "healthy frames resume after native draw failure");
                }
                replacementWorld.clock = 30;
                composition.tick();
                require(composition.trailSamples(instance.instanceId()).isEmpty(), "lifetime expires even when entity is not rendered");
                require(OutfitTrailBudget.count(wearer.getUUID()) == 0, "unrendered expiry releases controller capacity");
                try (var stale = composition.retain()) {
                    replacementWorld.clock = 50;
                    stale.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).isEmpty(), "an expired queued frame cannot recreate an old trail");
                }
                try (var pending = composition.retain()) {
                    composition.close();
                    pending.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                    require(composition.trailSamples(instance.instanceId()).isEmpty(), "retired queued frames cannot resurrect effects");
                    require(OutfitTrailBudget.count(wearer.getUUID()) == 0, "retirement returns capacity despite queued frames");
                }
            }
        } finally {
            avatar.clean();
        }
        previewTrails(wearer, config, context, data, pose, state);
        System.out.println("PASS world trail sampling: actual Lua/frame bridge, interpolated extraction pose, no replay/GUI effects, target/teleport/world/expiry/retirement cleanup; pixels MANUAL/PENDING");
    }

    private static void previewTrails(Wearer wearer, OutfitConfig config, CosmeticInstanceContext context,
                                      CompoundTag data, SkeletonPose pose,
                                      net.minecraft.client.renderer.entity.state.AvatarRenderState state) throws Exception {
        var client = net.minecraft.client.Minecraft.getInstance();
        var previousWorld = client.level;
        client.level = wearer.world;
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.getUUID().toString());
        Avatar preview = Avatar.createCosmetic(context, permissions, "preview trail sampling");
        preview.load(data.copy());
        UUID instance = context.instanceId();
        Object worldSlot = new Object();
        try (var scope = new top.aihmc.omnimorph.lua.api.particle.PreviewParticleScope(wearer.world, OutfitLimits.defaults());
             var composition = new OutfitComposition(config, OutfitLimits.defaults(), wearer,
                     Map.of(instance, preview), new SkeletonIndex.Cache())) {
            composition.bindPreviewParticles(scope);
            require(OutfitTrailBudget.acquire(worldSlot, wearer.getUUID(), 1), "world fixture retains its own trail slot");
            try (var rejectedWorld = composition.retain()) {
                rejectedWorld.draw(pose, state, 0, EntityRenderMode.RENDER, ignored -> {});
                require(composition.trailSamples(instance).isEmpty(), "bound preview trails cannot sample a world draw");
            }
            wearer.world.clock++;
            wearer.position(new Vec3(10, 20, 30), new Vec3(10, 20, 30));
            try (var first = composition.retain()) {
                first.draw(pose, state, 0, EntityRenderMode.FIGURA_GUI, ignored -> {});
                require(composition.trailSamples(instance).size() == 1
                                && OutfitTrailBudget.count(scope.budgetId()) == 1
                                && OutfitTrailBudget.count(wearer.getUUID()) == 1,
                        "GUI controller samples under the private preview slot budget beside the world slot");
                first.draw(pose, state, 0, EntityRenderMode.FIGURA_GUI, ignored -> {});
                require(composition.trailSamples(instance).size() == 1, "retained GUI replay cannot append history again");
            }
            wearer.world.clock++;
            wearer.position(new Vec3(10.2, 20, 30), new Vec3(10.2, 20, 30));
            try (var moved = composition.retain()) {
                moved.draw(pose, state, 0, EntityRenderMode.FIGURA_GUI, ignored -> {});
                var snapshot = preview.cosmeticContext.trailSnapshot(0);
                require(snapshot != null && snapshot.path().size() == 2,
                        "GUI controller exposes the same two-point readonly source consumed by linked Ribbon particles");
            }
            try (var retained = composition.retain()) {
                composition.close();
                retained.draw(pose, state, 0, EntityRenderMode.FIGURA_GUI, ignored -> {});
                require(composition.trailSamples(instance).isEmpty() && OutfitTrailBudget.count(scope.budgetId()) == 0
                                && OutfitTrailBudget.count(wearer.getUUID()) == 1,
                        "retired GUI frame cannot recreate history or release the world's capacity");
            }
        } finally {
            OutfitTrailBudget.release(worldSlot);
            preview.clean();
            client.level = previousWorld;
        }
        System.out.println("PASS preview controller trails: native GUI frame/Lua sampling, private slot budget, readonly linked source, replay and retirement; pixels MANUAL/PENDING");
    }

    private static void position(TrailBuffer.Sample value, double x, double y, double z, double time) {
        require(Math.abs(value.x() - x) < .0001 && Math.abs(value.y() - y) < .0001
                        && Math.abs(value.z() - z) < .0001 && Math.abs(value.timeTicks() - time) < .0001,
                "world-space sample retains the extraction origin and native model-unit conversion: " + value);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
