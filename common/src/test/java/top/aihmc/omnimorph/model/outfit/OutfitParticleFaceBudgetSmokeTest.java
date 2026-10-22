package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.luaj.vm2.LuaTable;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;
import sun.misc.Unsafe;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/** Native six-face model drawing is supplied by the existing material fixture. */
public final class OutfitParticleFaceBudgetSmokeTest {
    public static void run(Unsafe unsafe, Avatar owner, IntSupplier drawModel, Runnable drain) throws Exception {
        var previousLevel = Minecraft.getInstance().level;
        var previousPermissions = owner.permissions;
        var previousComplexity = owner.complexity;
        var previousDenied = owner.noPermissions;
        var previousMode = owner.renderMode;
        var texture = new FiguraTexture(owner, "face-budget", new NativeImage(1, 1, true));
        try {
            Minecraft.getInstance().level = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
            set(owner, "permissions", permissions(owner.owner));
            set(owner, "complexity", new Avatar.Instructions(8));
            set(owner, "noPermissions", new HashSet<>());
            owner.renderMode = EntityRenderMode.WORLD;
            testStandalone(owner, texture, drawModel);
            testShared(owner, drawModel, drain);
        } finally {
            CustomParticleRuntime.clearOwner(owner.owner);
            CustomParticleRuntime.finishFrame();
            texture.close();
            drain.run();
            Minecraft.getInstance().level = previousLevel;
            set(owner, "permissions", previousPermissions);
            set(owner, "complexity", previousComplexity);
            set(owner, "noPermissions", previousDenied);
            owner.renderMode = previousMode;
        }
        System.out.println("PASS particle/model face budget: actual YSM vertices and layered quads, either order, "
                + "counter reset, exact/insufficient allowance, hidden-owner refill, duplicate instances and frame retirement");
    }

    private static void testStandalone(Avatar owner, FiguraTexture texture, IntSupplier drawModel) {
        var particle = particle(owner, texture);
        try {
            require(OutfitInstructionBudget.current(owner) == null
                            && OutfitInstructionBudget.currentFaces(owner) != null,
                    "particle-only face tracking leaves ordinary Lua instruction scopes unchanged");
            YsmRenderFrameClock.beginFrame();
            require(drawModel.getAsInt() == 24, "six real model faces submit their native vertices first");
            require(extract(particle) == 2 && extract(particle) == 0,
                    "two layers use the final two face units, and a repeated extraction cannot exceed eight");
            YsmRenderFrameClock.beginFrame();
            require(extract(particle) == 2, "a new frame refills an owner even before its model is drawn");
            owner.complexity.reset(8);
            require(drawModel.getAsInt() == 24 && extract(particle) == 0,
                    "a native counter reset between particle and model submissions does not refund faces");
            owner.permissions.insert(Permissions.COMPLEXITY, 7, FiguraMod.MOD_ID);
            YsmRenderFrameClock.beginFrame();
            require(drawModel.getAsInt() == 24 && extract(particle) == 0 && particle.active(),
                    "one spare face cannot partially draw a two-layer particle or destroy its lifetime");
            owner.permissions.insert(Permissions.COMPLEXITY, 8, FiguraMod.MOD_ID);
            YsmRenderFrameClock.beginFrame();
            require(extract(particle) == 2, "hidden model does not leave a particle stuck on the previous allowance");
            particle.remove();
            owner.complexity.reset(8);
            require(drawModel.getAsInt() == 24
                            && OutfitInstructionBudget.currentFaces(owner).allowance(owner, owner.complexity) == 0,
                    "removing the last particle keeps its spent faces until frame retirement");
            CustomParticleRuntime.finishFrame();
            require(OutfitInstructionBudget.currentFaces(owner) == null,
                    "frame retirement releases the inactive face ledger");
        } finally {
            particle.remove();
            CustomParticleRuntime.finishFrame();
        }
    }

    private static void testShared(Avatar base, IntSupplier drawModel, Runnable drain) {
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "0".repeat(64), OutfitConfig.Format.FIGURA);
        var first = cosmetic(base, resource, 1);
        var second = cosmetic(base, resource, 2);
        var budget = new OutfitInstructionBudget(() -> base);
        budget.bindInstances(List.of(first, second));
        first.cosmeticContext.bindInstructionBudget(budget);
        second.cosmeticContext.bindInstructionBudget(budget);
        var firstTexture = new FiguraTexture(first, "shared-face", new NativeImage(1, 1, true));
        var secondTexture = new FiguraTexture(second, "shared-face", new NativeImage(1, 1, true));
        var a = particle(first, firstTexture);
        var b = particle(second, secondTexture);
        try {
            YsmRenderFrameClock.beginFrame();
            try (var ignored = budget.enter()) {
                require(drawModel.getAsInt() == 24, "base model draws against its actual scoped outfit budget");
            }
            require(extract(a) == 2, "first cosmetic's layers consume the remaining shared faces outside base scope");
            base.complexity.reset(8);
            require(extract(b) == 0 && b.active(), "another execution cannot obtain fresh shared faces by splitting instances");
            require(first.complexity.remaining == 6 && second.complexity.remaining == 8,
                    "only accepted geometry is charged to each instance's individual counter");
        } finally {
            a.remove();
            b.remove();
            CustomParticleRuntime.finishFrame();
            firstTexture.close();
            secondTexture.close();
            drain.run();
        }
    }

    private static Avatar cosmetic(Avatar base, OutfitConfig.ResourceRef resource, int ordinal) {
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, ordinal, true,
                OutfitConfig.AnchorRef.authorDefault("back_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(base.owner, base.owner, base.owner, instance,
                OutfitLimits.defaults(), CosmeticInstanceContext.Scope.OWNER);
        return Avatar.createCosmetic(context, base.permissions, "particle-face-budget");
    }

    private static CustomParticle particle(Avatar owner, FiguraTexture texture) {
        var particle = new CustomParticle(owner, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
        particle.setEmissive(true);
        var layers = new LuaTable();
        layers.set(1, new LuaTable());
        layers.set(2, new LuaTable());
        particle.setLayers(layers);
        require(particle.activate(), "face-budget particle activates within its independent live limit");
        return particle;
    }

    private static int extract(CustomParticle particle) {
        var quads = new RecordingQuads();
        particle.extract(quads, new FixtureCamera(), 0);
        return quads.count;
    }

    private static PermissionPack.PlayerPermissionPack permissions(UUID owner) {
        var category = new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT);
        var permissions = new PermissionPack.PlayerPermissionPack(category, owner.toString());
        permissions.insert(Permissions.COMPLEXITY, 8, FiguraMod.MOD_ID);
        return permissions;
    }

    private static final class RecordingQuads extends QuadParticleRenderState {
        private int count;

        @Override
        public void add(SingleQuadParticle.Layer layer, float x, float y, float z, float qx, float qy,
                        float qz, float qw, float size, float u0, float u1, float v0, float v1, int color, int light) {
            super.add(layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
            count++;
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

    private static void set(Avatar owner, String name, Object value) throws Exception {
        var field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
