package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;
import top.aihmc.omnimorph.molang.parser.ast.FloatExpression;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Controller commands and native resource lifetime, not evidence of complete combination rendering. */
public final class OutfitCompositionSmokeTest {
    public static void run(CompoundTag prepared) throws Exception {
        PlayerModel model = new PlayerModel(LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, OutfitLimits.defaults());
        var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, base.binding().definition().roots().getFirst()),
                null, null, null);
        var goodResource = resource(OutfitConfig.Format.FIGURA);
        var ysmResource = resource(OutfitConfig.Format.YSM_ENCRYPTED);
        var badResource = resource(OutfitConfig.Format.FIGURA);
        var declarations = List.of(
                instance(goodResource, 1, true, anchor, 3),
                instance(goodResource, 2, true, anchor, 6),
                instance(ysmResource, 1, true, anchor, 9),
                instance(badResource, 1, false, anchor, 12),
                instance(badResource, 2, true, anchor, 15),
                instance(goodResource, 3, true, OutfitConfig.AnchorRef.authorDefault("unknown_target"), 18));
        Map<String, Integer> watermarks = new HashMap<>();
        for (var declaration : declarations) {
            watermarks.merge(declaration.resource().identity(), declaration.displayOrdinal(), Math::max);
        }
        var configuration = new OutfitConfig(1, 0, null, declarations, List.of(), watermarks, false);
        Map<UUID, Avatar> instances = new LinkedHashMap<>();
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
        permissions.insert(Permissions.INIT_INST, 1000, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        for (var declaration : declarations) {
            var context = new CosmeticInstanceContext(account, outfit, account, declaration,
                    OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
            Avatar avatar = Avatar.createCosmetic(context, permissions, "composition-fixture");
            CompoundTag nbt;
            if (declaration.resource().equals(ysmResource)) {
                nbt = prepared.copy();
                nbt.remove("scripts");
            } else {
                nbt = new CompoundTag();
                var metadata = new CompoundTag();
                metadata.putString("resource_kind", "ACCESSORY");
                nbt.put("metadata", metadata);
                if (declaration.resource().equals(goodResource)) {
                    var scripts = new CompoundTag();
                    scripts.putByteArray("tick", """
                            tick_count = 0
                            function events.world_tick()
                                tick_count = tick_count + 1
                            end
                            """.getBytes(StandardCharsets.UTF_8));
                    nbt.put("scripts", scripts);
                }
                if (declaration.resource().equals(badResource)) {
                    var scripts = new CompoundTag();
                    scripts.putByteArray("bad", "while true do end".getBytes(StandardCharsets.UTF_8));
                    nbt.put("scripts", scripts);
                }
            }
            avatar.load(nbt);
            instances.put(declaration.instanceId(), avatar);
        }
        OutfitComposition composition = null;
        OutfitComposition.Frame first = null;
        OutfitComposition.Frame second = null;
        try {
            var indexes = new SkeletonIndex.Cache();
            composition = new OutfitComposition(configuration, OutfitLimits.defaults(), null, instances, indexes);
            require(indexes.parseCount() == 3, "one shared index per exact resource, including duplicate instances");
            first = composition.retain();
            var commands = new ArrayList<OutfitComposition.Draw>();
            var pose = base.capture(new Matrix4f());
            first.draw(pose, null, .5f, commands::add);
            require(commands.size() == 3, "built-in Lua mounts static Figura/YSM and skips disabled, unresolved and failed scripts");
            for (int index = 0; index < commands.size(); index++) {
                var command = commands.get(index);
                require(command.instanceId().equals(declarations.get(index).instanceId())
                        && Math.abs(command.matrix().m30() - (index + 1) * 3) < .0001f,
                        "Lua composition preserves stable instance order and each model-unit offset");
                command.matrix().identity();
            }
            require(instances.get(declarations.get(3).instanceId()).luaRuntime == null
                    && !instances.get(declarations.get(3).instanceId()).scriptError,
                    "disabled resources never initialize their author script");
            require(instances.get(declarations.get(4).instanceId()).scriptError,
                    "one author initialization timeout is isolated from the remaining outfit");
            int initializationUsed = instances.values().stream()
                    .mapToInt(avatar -> avatar.init.max - avatar.init.remaining).sum();
            require(initializationUsed == 1000,
                    "successful and timed-out initializers together consume exactly one outfit init allowance");
            require(instances.get(declarations.get(5).instanceId()).luaRuntime == null,
                    "an unresolved attachment does not initialize author effects");
            Avatar good = instances.get(declarations.getFirst().instanceId());
            var assertion = good.luaRuntime.load("bridge isolation", "assert(cosmetic_native == nil)");
            require(good.luaRuntime.run(assertion, new Avatar.Instructions(4096)) != null && !good.scriptError,
                    "an author cannot access the controller's native bridge");
            try (var handOnly = composition.retain()) {
                handOnly.draw(pose.restrict(index -> false), null, .5f, command -> {
                    throw new AssertionError("a hand pass cannot emit a root/body attachment");
                });
            }
            composition.tick();
            var tickAssertion = good.luaRuntime.load("mounted tick across passes", "assert(tick_count == 1)");
            require(good.luaRuntime.run(tickAssertion, new Avatar.Instructions(4096)) != null && !good.scriptError,
                    "valid mounted instances keep ticking after a pass that does not draw their bones");
            require(instances.get(declarations.get(5).instanceId()).luaRuntime == null,
                    "a truly unresolved bone remains uninitialized after the same tick");
            testSharedMolangBudget(first, pose, good, instances.get(declarations.get(2).instanceId()));
            testSharedEffectBudget(composition, good, instances.get(declarations.get(1).instanceId()));
            testSharedTextureBudget(good, instances.get(declarations.get(1).instanceId()));
            testSharedLuaBudget(good, instances.get(declarations.get(1).instanceId()));
            testBaseLuaBudget(good);
            second = composition.retain();
            composition.close();
            first.close();
            Avatar ysm = instances.get(declarations.get(2).instanceId());
            require(ysm.getYsmRuntime() != null && ysm.accelerationOwner().isCurrent(),
                    "retiring a draft preserves resources held by another extracted frame");
            var lastFrame = second;
            var drawn = new int[1];
            lastFrame.draw(pose, null, .5f, command -> {
                drawn[0]++;
                lastFrame.close();
                require(ysm.getYsmRuntime() != null && ysm.accelerationOwner().isCurrent(),
                        "closing during drawing defers native resource cleanup until the callback returns");
            });
            require(drawn[0] == 1 && ysm.getYsmRuntime() == null && !ysm.accelerationOwner().isCurrent(),
                    "last frame retirement stops later commands and releases native resources exactly once");
            lastFrame.close();
            System.out.println("PASS outfit controller: Lua-owned static selection, native instances, timeout isolation and deferred generation cleanup; combined rendering pending");
        } finally {
            if (first != null) {
                first.close();
            }
            if (second != null) {
                second.close();
            }
            if (composition != null) {
                composition.close();
            } else {
                instances.values().forEach(Avatar::clean);
            }
        }
    }

    private static void testSharedTextureBudget(Avatar first, Avatar second) {
        for (int index = 0; index < top.aihmc.omnimorph.lua.api.TextureAPI.TEXTURE_LIMIT; index++) {
            Avatar owner = index % 2 == 0 ? first : second;
            owner.registerTexture("outfit-" + index, new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
        }
        var original = first.renderer.customTextures.get("outfit-0");
        var replacement = first.registerTexture("outfit-0",
                new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
        require(replacement != original && first.renderer.customTextures.size() == 64,
                "replacing one existing texture remains valid at the shared limit");
        try {
            second.registerTexture("extra", new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
            throw new AssertionError("each instance received another custom texture allowance");
        } catch (org.luaj.vm2.LuaError expected) {
            require(expected.getMessage().contains("Outfit maximum"), "shared texture quota reports an explicit error");
        }
        require(first.renderer.customTextures.get("outfit-0") == replacement
                        && !second.renderer.customTextures.containsKey("extra")
                        && !first.scriptError && !second.scriptError,
                "quota rejection preserves existing images and does not clean another author's VM");
        first.renderer.customTextures.remove("outfit-0").close();
        second.registerTexture("released-slot", new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
        require(first.renderer.customTextures.size() + second.renderer.customTextures.size() == 128,
                "closing and removing an instance texture releases a slot in the shared inventory");
    }

    private static void testSharedEffectBudget(OutfitComposition composition, Avatar first, Avatar second) {
        int soundMaximum = first.permissions.get(Permissions.SOUNDS);
        int particleMaximum = first.permissions.get(Permissions.PARTICLES);
        require(soundMaximum > 0 && particleMaximum >= 20, "effect fixture has nonzero existing permissions");
        for (int index = 0; index < soundMaximum; index++) {
            Avatar owner = index % 2 == 0 ? first : second;
            new top.aihmc.omnimorph.lua.api.sound.LuaSound(
                    (com.mojang.blaze3d.audio.SoundBuffer) null, "budget-only", owner).play();
            require(!owner.noPermissions.contains(Permissions.SOUNDS), "real LuaSound guard admits available quota");
        }
        new top.aihmc.omnimorph.lua.api.sound.LuaSound(
                (com.mojang.blaze3d.audio.SoundBuffer) null, "budget-only", first).play();
        require(first.noPermissions.contains(Permissions.SOUNDS) && first.soundsRemaining.peek() > 0
                        && first.remainingSounds() == 0 && second.remainingSounds() == 0,
                "sound playback cannot use a second author's private quota after exhausting the wearer");
        for (int index = 0; index < particleMaximum; index++) {
            require((index % 2 == 0 ? first : second).useParticle(), "shared particle entry point consumes one token");
        }
        new top.aihmc.omnimorph.lua.api.particle.LuaParticle("budget-only", null, first).spawn();
        require(first.noPermissions.contains(Permissions.PARTICLES) && first.particlesRemaining.peek() > 0
                        && first.remainingParticles() == 0 && second.remainingParticles() == 0,
                "real LuaParticle denial guard shares the wearer allowance without constructing a particle");
        composition.tick();
        int refill = particleMaximum / net.minecraft.SharedConstants.TICKS_PER_SECOND;
        require(first.remainingParticles() == refill && second.remainingParticles() == refill,
                "one composition tick refills a single wearer bucket rather than one bucket per instance");
    }

    private static void testSharedLuaBudget(Avatar first, Avatar second) {
        for (Avatar avatar : List.of(first, second)) {
            avatar.permissions.insert(Permissions.RENDER_INST, 64, top.aihmc.omnimorph.FiguraMod.MOD_ID);
            avatar.render.reset(64);
        }
        YsmRenderFrameClock.beginFrame();
        var firstCall = first.luaRuntime.load("shared render first", "local n=0 for i=1,4 do n=n+i end return n");
        require(first.luaRuntime.run(firstCall, first.render) != null,
                "first instance executes within the shared render allowance");
        var runaway = second.luaRuntime.load("shared render runaway", "while true do end");
        require(second.luaRuntime.run(runaway, second.render) == null && second.scriptError && !first.scriptError,
                "the instance that overruns the remaining allowance fails independently");
        require((64 - first.render.remaining) + (64 - second.render.remaining) == 64,
                "failed VM instructions are charged and cannot grant another instance a fresh allowance");
        first.render.reset(64);
        var small = first.luaRuntime.load("shared render next", "return 1");
        require(first.luaRuntime.run(small, first.render) == null && !first.scriptError,
                "resetting one instance's counter cannot refill the shared frame or disable a healthy VM");
        YsmRenderFrameClock.beginFrame();
        require(first.luaRuntime.run(small, first.render) != null && !first.scriptError,
                "a healthy instance resumes on the next render frame");
    }

    private static void testBaseLuaBudget(Avatar accessory) {
        Avatar base = Avatar.createLibraryPreview(UUID.randomUUID(), "base-budget-fixture");
        var previous = accessory.cosmeticContext.instructionBudget();
        var budget = new OutfitInstructionBudget(() -> base);
        budget.bindInstances(List.of(accessory));
        accessory.cosmeticContext.bindInstructionBudget(budget);
        try {
            base.load(new CompoundTag());
            base.permissions.insert(Permissions.RENDER_INST, 64, top.aihmc.omnimorph.FiguraMod.MOD_ID);
            base.render.reset(64);
            base.render.use(20);
            accessory.render.reset(64);
            YsmRenderFrameClock.beginFrame();
            require(budget.allowance(accessory, accessory.render) == 44,
                    "base work before the composed callback reduces the accessory allowance");
            var call = accessory.luaRuntime.load("accessory shares base", "return 1");
            require(accessory.luaRuntime.run(call, accessory.render) != null,
                    "accessory executes using the base's remaining allowance");
            int accessoryUsed = 64 - accessory.render.remaining;
            var runtime = new top.aihmc.omnimorph.lua.FiguraLuaRuntime(base, Map.of());
            require(runtime.init(new net.minecraft.nbt.ListTag()), "fixture base VM initializes normally");
            var baseCall = runtime.load("base shares accessory", "return 2");
            try (var scope = budget.enter()) {
                require(OutfitInstructionBudget.current(base) == budget, "base callback uses the scoped outfit budget");
                require(runtime.run(baseCall, base.render) != null, "base callback executes within its remaining quota");
                int baseUsed = 64 - base.render.remaining;
                base.render.reset(64);
                require(budget.allowance(base, base.render) == 64 - baseUsed - accessoryUsed,
                        "a later base pass cannot refund base or accessory instructions by resetting its counter");
                var texture = base.registerTexture("base-slot",
                        new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
                require(base.renderer.customTextures.get("base-slot") == texture,
                        "base texture registration uses the same scoped inventory as its accessory");
                int baseSlots = top.aihmc.omnimorph.lua.api.TextureAPI.TEXTURE_LIMIT
                        - accessory.renderer.customTextures.size();
                for (int index = 1; index < baseSlots; index++) {
                    base.registerTexture("base-slot-" + index,
                            new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
                }
                try {
                    base.registerTexture("base-extra",
                            new com.mojang.blaze3d.platform.NativeImage(1, 1, true), false);
                    throw new AssertionError("base received an independent custom texture allowance");
                } catch (org.luaj.vm2.LuaError expected) {
                    require(expected.getMessage().contains("Outfit maximum"),
                            "base cannot bypass texture slots already used by the accessory");
                }
            }
            require(OutfitInstructionBudget.current(base) == null,
                    "leaving the composition does not permanently bind the borrowed base VM");
            var oldEffects = accessory.cosmeticContext.effectBudget();
            accessory.cosmeticContext.bindEffectBudget(new OutfitEffectBudget(() -> base, accessory));
            try {
                require(!accessory.useSound(), "the existing detached preview's zero sound permission is retained");
                base.permissions.insert(Permissions.SOUNDS, 5, top.aihmc.omnimorph.FiguraMod.MOD_ID);
                base.soundsRemaining.set(5);
                for (int tick = 0; tick < net.minecraft.SharedConstants.TICKS_PER_SECOND; tick++) {
                    base.soundsRemaining.tick();
                }
                int baseSounds = base.soundsRemaining.peek();
                require(accessory.useSound() && base.soundsRemaining.peek() == baseSounds - 1,
                        "accessory consumes the existing base sound bucket");
                while (base.soundsRemaining.use()) {
                    // Simulate native base emissions consuming the same original bucket.
                }
                require(!accessory.useSound(), "base consumption immediately limits accessory effects");
            } finally {
                accessory.cosmeticContext.bindEffectBudget(oldEffects);
            }
        } finally {
            accessory.cosmeticContext.bindInstructionBudget(previous);
            base.clean();
        }
    }

    private static void testSharedMolangBudget(OutfitComposition.Frame frame, SkeletonPose pose,
                                               Avatar figura, Avatar ysm) {
        var figuraContext = new Avatar.MolangContext(figura);
        var ysmContext = ysm.getMolangContext();
        require(figuraContext.executionBudget() == ysmContext.executionBudget(),
                "Figura and YSM instances resolve the same outfit allowance");
        YsmRenderFrameClock.beginFrame();
        boolean exhausted = false;
        try {
            frame.draw(pose, null, 0, command -> {
                if (command.avatar() == figura) {
                    for (int step = 0; step < MolangExecutionBudget.DEFAULT_FRAME_STEPS; step++) {
                        figuraContext.executionBudget().consume();
                    }
                } else {
                    ExpressionEvaluator.evaluator(ysmContext).eval(FloatExpression.ONE);
                }
            });
        } catch (MolangExecutionBudget.Exceeded expected) {
            exhausted = true;
        }
        require(exhausted, "one instance's workload consumes another instance's remaining frame allowance");
        try {
            frame.draw(pose, null, 0,
                    command -> ExpressionEvaluator.evaluator(ysmContext).eval(FloatExpression.ONE));
            throw new AssertionError("repeated composition pass refilled an exhausted frame");
        } catch (MolangExecutionBudget.Exceeded expected) {
            // The real frame scope, rather than each expression or draw call, owns refill.
        }
        YsmRenderFrameClock.beginFrame();
        frame.draw(pose, null, 0,
                command -> ExpressionEvaluator.evaluator(ysmContext).eval(FloatExpression.ONE));
    }

    private static OutfitConfig.ResourceRef resource(OutfitConfig.Format format) {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "0".repeat(64), format);
    }

    private static OutfitConfig.Instance instance(OutfitConfig.ResourceRef resource, int ordinal, boolean enabled,
                                                  OutfitConfig.AnchorRef anchor, int x) {
        return new OutfitConfig.Instance(UUID.randomUUID(), resource, ordinal, enabled, anchor,
                new OutfitConfig.Transform(new OutfitConfig.Vector(x, 0, 0), new OutfitConfig.Vector(0, 0, 0),
                        new OutfitConfig.Vector(1, 1, 1)), List.of(), Map.of(), Map.of());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
