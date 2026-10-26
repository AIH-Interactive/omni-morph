package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import sun.misc.Unsafe;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Exercises native hand selection with explicit entity inputs, without rendering a world. */
public final class YsmHandAnimationSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.BOW.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.DIAMOND_SPEAR.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.TRIDENT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Method update = YsmAnimationPlayer.class.getDeclaredMethod("updateNativeHandAnimations", LivingEntity.class);
        update.setAccessible(true);
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var entity = (HandEntity) ((Unsafe) unsafeField.get(null)).allocateInstance(HandEntity.class);
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.mainArm = HumanoidArm.RIGHT;
        entity.main = new ItemStack(Items.BOW);

        if (args.length > 0) {
            YsmMaterialNativeSmokeTest.replayAcceleration(Path.of(args[0]), runtime -> {
                try {
                    verifyReferenceBow(runtime, entity, update);
                    verifyReferenceSpear(runtime, entity, update);
                } catch (ReflectiveOperationException failure) {
                    throw new RuntimeException(failure);
                } finally {
                    entity.using = false;
                }
            });
        }

        if (args.length > 1) {
            YsmMaterialNativeSmokeTest.replayAcceleration(Path.of(args[1]), runtime -> {
                try {
                    verifyLanceChargeSelection(runtime, entity, update);
                } catch (ReflectiveOperationException failure) {
                    throw new RuntimeException(failure);
                } finally {
                    entity.using = false;
                    entity.main = new ItemStack(Items.BOW);
                }
            });
        }

        YsmAnimationPlayer player = player("use_mainhand:bow");
        update.invoke(player, entity);
        require(!targeted(player, "use_mainhand:bow"), "holding a bow must not start its drawing animation");
        entity.using = true;
        update.invoke(player, entity);
        require(targeted(player, "use_mainhand:bow"), "using a bow starts its drawing animation");
        entity.using = false;
        update.invoke(player, entity);
        require(!targeted(player, "use_mainhand:bow"), "releasing a bow retires its drawing animation");

        player = player("use_mainhand$minecraft:bow");
        update.invoke(player, entity);
        require(!targeted(player, "use_mainhand$minecraft:bow"),
                "an item-ID drawing condition must not match holding the same bow");
        entity.using = true;
        update.invoke(player, entity);
        require(targeted(player, "use_mainhand$minecraft:bow"), "using selects the exact item-ID drawing clip");
        entity.using = false;

        verifyBowHands(entity, update);
        verifyHoldPause(entity, update);
        verifyLegacyLoopOverride(entity, update);
        verifyLocalInputPulse(entity, update);
        verifyControllerStageInterleaving(update);
        verifyHandStageOwnership(entity, update);
        verifyEmptyHandFallback(entity, update);
        entity.mainArm = HumanoidArm.RIGHT;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.offhand = ItemStack.EMPTY;
        entity.main = new ItemStack(Items.DIAMOND_SPEAR);
        entity.main.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 10,
                Optional.empty(), Optional.empty(), Optional.empty(), 1f, 1f,
                Optional.empty(), Optional.empty()));
        player = player("hold_mainhand:spear", "use_mainhand:spear", "use_mainhand");
        update.invoke(player, entity);
        require(!targeted(player, "use_mainhand:spear"), "holding a spear must not borrow its use pose");
        require(targeted(player, "hold_mainhand:spear"), "legacy spear hold remains available");
        entity.using = true;
        for (int useTicks : new int[]{1, 10, 40, 100}) {
            entity.useTicks = useTicks;
            update.invoke(player, entity);
            require(!targeted(player, "use_mainhand:spear"),
                    "native spear must not select legacy trident use at tick " + useTicks);
            require(!targeted(player, "use_mainhand"), "specialized spear use precedes the generic action");

            require(targeted(player, "hold_mainhand:spear"), "missing kinetic charge clip retains the authored grip");
        }

        player = player("lance_charge", "use_mainhand:spear");
        update.invoke(player, entity);
        require(targeted(player, "lance_charge"), "dedicated kinetic charge clip takes precedence");
        require(Float.isFinite(player.getActiveAnimation("lance_charge").sampledTime),
                "dedicated kinetic charge uses the weapon timeline");

        player = player("use_mainhand:lance");
        update.invoke(player, entity);
        require(targeted(player, "use_mainhand:lance"), "declared lance use clip remains supported");

        entity.using = false;
        player = player("hold_mainhand:spear");
        update.invoke(player, entity);
        var missingUseHold = player.getActiveAnimation("hold_mainhand:spear");
        entity.using = true;
        update.invoke(player, entity);
        require(missingUseHold != null && !missingUseHold.paused, "unresolved spear use preserves its grip");
        entity.main = new ItemStack(Items.BOW);
        entity.using = false;
        player = player("hold_mainhand:bow");
        update.invoke(player, entity);
        var bowHold = player.getActiveAnimation("hold_mainhand:bow");
        entity.using = true;
        update.invoke(player, entity);
        require(bowHold != null && !bowHold.paused, "unresolved non-kinetic use also preserves its grip");

        verifySpearClassification(entity, update);
        entity.main = new ItemStack(Items.TRIDENT);
        player = player("hold_mainhand:spear", "use_mainhand:spear");
        update.invoke(player, entity);
        require(targeted(player, "use_mainhand:spear"), "trident still uses the legacy throwing pose");
        require(Float.isNaN(player.getActiveAnimation("use_mainhand:spear").sampledTime),
                "trident must not inherit kinetic weapon sampling");
        entity.using = false;
        update.invoke(player, entity);
        require(!targeted(player, "use_mainhand:spear") && targeted(player, "hold_mainhand:spear"),
                "releasing the trident returns to its authored grip");

        entity.main = ItemStack.EMPTY;
        entity.offhand = new ItemStack(Items.TRIDENT);
        entity.usedHand = InteractionHand.OFF_HAND;
        player = player("hold_offhand:spear", "use_offhand:spear");
        update.invoke(player, entity);
        require(targeted(player, "hold_offhand:spear"), "offhand trident uses its authored grip");
        entity.using = true;
        update.invoke(player, entity);
        require(targeted(player, "use_offhand:spear"), "offhand trident retains its throwing animation");
        entity.using = false;
        update.invoke(player, entity);
        require(!targeted(player, "use_offhand:spear") && targeted(player, "hold_offhand:spear"),
                "offhand trident returns to its grip after release");
        verifyUseTransforms(entity);
        System.out.println("PASS native hand state: holding, using and release preserve conditional clip ownership");
    }

    private static void verifyLanceChargeSelection(YsmModelRuntime runtime, HandEntity entity, Method update)
            throws ReflectiveOperationException {
        entity.main = new ItemStack(Items.DIAMOND_SPEAR);
        entity.main.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 10,
                Optional.empty(), Optional.empty(), Optional.empty(), 1f, 1f, Optional.empty(), Optional.empty()));
        entity.using = true;
        entity.useTicks = 10;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.mainArm = HumanoidArm.RIGHT;
        var player = runtime.animations();
        update.invoke(player, entity);
        var uses = player.getActiveAnimations().values().stream()
                .filter(playing -> playing.poseOrder == YsmControllerSlot.executionOrder("use") && playing.targetWeight > 0f)
                .toList();
        require(uses.isEmpty() || uses.stream().allMatch(value -> value.clip.name.contains("lance")),
                "missing charge either stops use or selects an available lance resource");
        require(player.getActiveAnimation("hold_mainhand:spear") != null
                        && !player.getActiveAnimation("hold_mainhand:spear").paused,
                "without charge resources the reported model retains its authored grip");
        require(uses.stream().noneMatch(value -> value.clip.name.endsWith(":spear")),
                "native SPEAR must not select the legacy trident throw when resolving lance charge");
    }

    private static void verifyEmptyHandFallback(HandEntity entity, Method update) throws ReflectiveOperationException {
        entity.main = new ItemStack(Items.BOW);
        entity.using = true;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.mainArm = HumanoidArm.RIGHT;
        var animations = player("hold_mainhand:bow");
        String id = "use_mainhand$minecraft:bow";
        animations.registerAnimations(Map.of(id, new YsmAnimationClip(id, 1f, true)));
        var defaults = clips("use_mainhand:bow");
        animations.registerDefaultAnimations(defaults, defaults);
        update.invoke(animations, entity);
        var selected = animations.getActiveAnimation("use_mainhand:bow");
        require(selected != null && selected.targetWeight > 0f && !selected.clip.boneAnimations.isEmpty(),
                "an empty ID candidate does not block later default hand candidates");
        animations.registerAnimations(clips(id));
        update.invoke(animations, entity);
        require(targeted(animations, id), "nonempty author-specific clips still precede defaults");
        animations.setNativeAnimationEnabled("use_mainhand:bow", false);
        animations.registerAnimations(Map.of(id, new YsmAnimationClip(id, 1f, true)));
        update.invoke(animations, entity);
        require(animations.getActiveAnimation("use_mainhand:bow") == null
                        || animations.getActiveAnimation("use_mainhand:bow").targetWeight == 0f,
                "empty placeholder fallback still respects explicit action disable");
        entity.using = false;
    }

    static void verifyTacticsSustainedUse(YsmModelRuntime runtime, java.util.List<String> failures)
            throws ReflectiveOperationException {
        Items.BOW.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.TRIDENT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        var input = YsmModelRuntime.class.getDeclaredField("previewInput");
        input.setAccessible(true);
        var defaults = YsmModelRuntime.class.getDeclaredMethod("loadDefaultAnimations",
                net.minecraft.nbt.CompoundTag.class, MolangEngine.class);
        defaults.setAccessible(true);
        defaults.invoke(runtime, input.get(runtime), MolangEngine.fromCustomBinding(runtime.owner().getAvatarBindings()));
        var player = runtime.animations();
        var evaluator = ExpressionEvaluator.evaluator(runtime.owner().getMolangContext());
        Method update = YsmAnimationPlayer.class.getDeclaredMethod("updateNativeHandAnimations", LivingEntity.class);
        Method advance = YsmAnimationPlayer.class.getDeclaredMethod("advanceAnimationTimeline", float.class, ExpressionEvaluator.class);
        Method reconcile = YsmAnimationPlayer.class.getDeclaredMethod("reconcileBuiltinSources");
        update.setAccessible(true);
        advance.setAccessible(true);
        reconcile.setAccessible(true);
        HandEntity entity = newHandEntity();
        entity.mainArm = HumanoidArm.RIGHT;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.offhand = ItemStack.EMPTY;
        for (var item : new net.minecraft.world.item.Item[]{Items.BOW, Items.TRIDENT}) {
            String name = item == Items.BOW ? "use_mainhand:bow" : "use_mainhand:spear";
            entity.main = new ItemStack(item);
            entity.using = true;
            update.invoke(player, entity);
            var draw = player.getActiveAnimation(name);
            if (draw == null) {
                failures.add("specific default " + name + " must beat author's generic use");
            } else {
                for (int frame = 0; frame < 40; frame++) {
                    reconcile.invoke(player);
                    update.invoke(player, entity);
                    advance.invoke(player, 0.05f, evaluator);
                }
                if (player.getActiveAnimation(name) != draw || Math.abs(draw.time - 2f) > 0.001f
                        || draw.loopMode != YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME) {
                    failures.add(name + " must keep one continuous held draw");
                }
            }
            entity.using = false;
            update.invoke(player, entity);
            advance.invoke(player, 0.25f, evaluator);
        }
        // Without default resources, a genuine authored ONCE use still must not retrigger
        // until the continuous use request ends, matching reference lastRequestedAnimation.
        player.setNativeStateMachineEnabled(false);
        entity.main = new ItemStack(Items.BOW);
        entity.using = true;
        update.invoke(player, entity);
        var once = player.getActiveAnimation("use_mainhand");
        for (int frame = 0; frame < 60; frame++) {
            reconcile.invoke(player);
            update.invoke(player, entity);
            advance.invoke(player, 0.05f, evaluator);
        }
        if (once == null || player.getActiveAnimation("use_mainhand") != once || once.weight != 0f) {
            failures.add("completed one-shot use must remain stopped while right click stays held");
        }
        entity.using = false;
        update.invoke(player, entity);
        advance.invoke(player, 0.25f, evaluator);
        entity.using = true;
        update.invoke(player, entity);
        var restarted = player.getActiveAnimation("use_mainhand");
        if (restarted == null || restarted == once || restarted.time != 0f) {
            failures.add("release and reuse must start a fresh authored one-shot");
        }
        entity.using = false;
        update.invoke(player, entity);
        player.setNativeStateMachineEnabled(true);
    }

    private static void verifyReferenceSpear(YsmModelRuntime runtime, HandEntity entity, Method update)
            throws ReflectiveOperationException {
        // This native replay fixture has no Avatar NBT, so explicitly run the same default
        // preparation used by formal Avatar assembly before checking fallback playback.
        var input = YsmModelRuntime.class.getDeclaredField("previewInput");
        input.setAccessible(true);
        var defaults = YsmModelRuntime.class.getDeclaredMethod("loadDefaultAnimations",
                net.minecraft.nbt.CompoundTag.class, MolangEngine.class);
        defaults.setAccessible(true);
        defaults.invoke(runtime, input.get(runtime), MolangEngine.fromCustomBinding(runtime.owner().getAvatarBindings()));
        var player = runtime.animations();
        entity.main = new ItemStack(Items.DIAMOND_SPEAR);
        entity.main.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 10,
                Optional.empty(), Optional.empty(), Optional.empty(), 1f, 1f, Optional.empty(), Optional.empty()));
        entity.useTicks = 2;
        entity.using = false;
        update.invoke(player, entity);
        entity.using = true;
        update.invoke(player, entity);
        var using = player.getActiveAnimation("use_mainhand:lance");
        require(using != null && !using.clip.boneAnimations.isEmpty() && Float.isFinite(using.sampledTime),
                "real model uses lance charge rather than legacy trident use");
        var evaluator = ExpressionEvaluator.evaluator(runtime.owner().getMolangContext());
        Method advance = YsmAnimationPlayer.class.getDeclaredMethod("advanceAnimationTimeline",
                float.class, ExpressionEvaluator.class);
        advance.setAccessible(true);
        advance.invoke(player, .5f, evaluator);
        float expectedTime = Math.clamp(2f / Math.max(1f,
                entity.main.get(DataComponents.KINETIC_WEAPON).computeDamageUseDuration()), 0f, 1f) * using.clip.length;
        require(Math.abs(using.time - expectedTime) < .0001f, "real model spear follows reference charge progress");
        sample(player, evaluator);
        double usedPose = runtime.getPart("RightArm").animRotRaw().x;
        using.weight = 0f;
        sample(player, evaluator);
        require(Math.abs(usedPose - runtime.getPart("RightArm").animRotRaw().x) > .01,
                "real model spear clip changes the arm pose");
        using.weight = 1f;
        update.invoke(player, entity);
        require(player.getActiveAnimation("use_mainhand:lance") == using && Math.abs(using.time - expectedTime) < .0001f,
                "holding spear use retains its instance and authored clock");
        entity.using = false;
        update.invoke(player, entity);
        require(player.getActiveAnimation("use_mainhand:lance").targetWeight == 0f, "real model retires spear use on release");
        entity.main = new ItemStack(Items.BOW);
        System.out.println("PASS real model spear: lance charge selection, reference progress, arm pose, hold and release");
    }

    private static void verifyHandStageOwnership(HandEntity entity, Method update) throws ReflectiveOperationException {
        entity.using = true;
        entity.swinging = false;
        entity.mainArm = HumanoidArm.RIGHT;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.main = new ItemStack(Items.BOW);
        entity.offhand = ItemStack.EMPTY;
        var evaluator = ExpressionEvaluator.evaluator(new org.figuramc.figura.avatar.Avatar.MolangContext(null));
        var owned = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(
                    top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController controller,
                    ExpressionEvaluator<?> evaluation) {
                // This fixture has no function program; state ownership uses the production runtime.
            }
        };
        owned.registerAnimations(clips("use_mainhand:bow", "hold_mainhand:bow"));
        owned.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "controller.animation.player.use_0":{"initial_state":"ysm-builtin","states":{"ysm-builtin":{}}},
                  "player.use_1":{"states":{"default":{}}},
                  "player.post_use":{"states":{"default":{}}}}}
                """, MolangEngine.fromCustomBinding(key -> null)));
        owned.controllerRuntime().update(evaluator, .1f, false);
        update.invoke(owned, entity);
        require(!targeted(owned, "use_mainhand:bow"), "numbered custom use controller owns the native use stage");
        require(owned.usesBuiltinController("player.swing") && owned.usesBuiltinController("player.hold_mainhand"),
                "use ownership leaves other core stages independent");
        var missing = player("hold_mainhand:bow");
        for (int frame = 0; frame < 3; frame++) {
            update.invoke(missing, entity);
        }
        @SuppressWarnings("unchecked")
        var diagnostics = (java.util.List<String>) missing.getClass().getMethod("handAnimationDiagnostics").invoke(missing);
        require(diagnostics.size() == 1 && diagnostics.getFirst().contains("player.use.mainhand")
                        && diagnostics.getFirst().contains("minecraft:bow"),
                "unresolved use is diagnosed once with action and item");
        var separate = player("hold_mainhand:bow");
        update.invoke(separate, entity);
        require(((java.util.List<?>) separate.getClass().getMethod("handAnimationDiagnostics").invoke(separate)).size() == 1,
                "diagnostic deduplication is per player instance");
        var success = player("use_mainhand:bow");
        update.invoke(success, entity);
        require(((java.util.List<?>) success.getClass().getMethod("handAnimationDiagnostics").invoke(success)).isEmpty(),
                "resolved actions are not reported as missing");
        entity.using = false;
    }

    private static void verifySpearClassification(HandEntity entity, Method update) throws ReflectiveOperationException {
        entity.using = true;
        entity.main = new ItemStack(Items.DIAMOND_SPEAR);
        entity.main.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 10,
                Optional.empty(), Optional.empty(), Optional.empty(), 1f, 1f, Optional.empty(), Optional.empty()));
        var generic = player("use_mainhand");
        update.invoke(generic, entity);
        require(!targeted(generic, "use_mainhand"),
                "missing native spear charge must not loop an unrelated short generic action");
        entity.main = new ItemStack(Items.BOW);
        entity.main.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 10,
                Optional.empty(), Optional.empty(), Optional.empty(), 1f, 1f, Optional.empty(), Optional.empty()));
        var arbitrary = player("lance_charge", "use_mainhand$literal", "use_mainhand$minecraft:bow");
        update.invoke(arbitrary, entity);
        require(targeted(arbitrary, "use_mainhand$minecraft:bow") && !targeted(arbitrary, "lance_charge"),
                "a component alone does not classify arbitrary items as a lance");
        entity.main = new ItemStack(Items.DIAMOND_SPEAR);
        entity.main.set(DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumable.builder()
                .animation(net.minecraft.world.item.ItemUseAnimation.SPEAR).build());
        var noKinetic = player("lance_charge");
        update.invoke(noKinetic, entity);
        require(targeted(noKinetic, "lance_charge") && Float.isNaN(noKinetic.getActiveAnimation("lance_charge").sampledTime),
                "native spear classification does not require a kinetic component for selection");
        Items.STICK.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        var holder = Items.STICK.builtInRegistryHolder();
        var previous = holder.tags().toList();
        Method bindTags = holder.getClass().getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        try {
            bindTags.invoke(holder, java.util.List.of(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.parse("sparkle_morpher:pike"))));
            entity.main = new ItemStack(Items.STICK);
            entity.main.set(DataComponents.CONSUMABLE, net.minecraft.world.item.component.Consumable.builder()
                    .animation(net.minecraft.world.item.ItemUseAnimation.SPEAR).build());
            var pike = player("lance_charge");
            update.invoke(pike, entity);
            require(targeted(pike, "lance_charge"), "pike tags select lance actions without a kinetic component");
        } finally {
            bindTags.invoke(holder, previous);
        }
    }

    private static void verifyReferenceBow(YsmModelRuntime runtime, HandEntity entity, Method update)
            throws ReflectiveOperationException {
        var animations = runtime.animations();
        var evaluator = ExpressionEvaluator.evaluator(runtime.owner().getMolangContext());
        Method advance = YsmAnimationPlayer.class.getDeclaredMethod("advanceAnimationTimeline",
                float.class, ExpressionEvaluator.class);
        Method sample = YsmAnimationPlayer.class.getDeclaredMethod("sampleAnimations",
                ExpressionEvaluator.class, boolean.class);
        advance.setAccessible(true);
        sample.setAccessible(true);

        entity.using = true;
        update.invoke(animations, entity);
        var drawing = animations.getActiveAnimation("use_mainhand:bow");
        require(drawing != null && !drawing.clip.boneAnimations.isEmpty(),
                "reference model must select its authored bow drawing clip");
        advance.invoke(animations, 0.5f, evaluator);
        sample.invoke(animations, evaluator, false);
        double drawnPitch = runtime.getPart("RightArm").animRotRaw().x;
        drawing.weight = 0f;
        sample.invoke(animations, evaluator, false);
        require(Math.abs(drawnPitch - runtime.getPart("RightArm").animRotRaw().x) > 45f,
                "reference bow clip must change the rendered arm pose");
        drawing.weight = 1f;

        entity.swinging = true;
        entity.swingingArm = InteractionHand.MAIN_HAND;
        update.invoke(animations, entity);
        require(targeted(animations, "use_mainhand:bow"),
                "simultaneous swing must not replace the active bow draw");
        entity.swinging = false;
        update.invoke(animations, entity);
        require(animations.getActiveAnimation("use_mainhand:bow") == drawing && drawing.time == 0.5f,
                "holding use must preserve the current drawing clock");

        entity.using = false;
        update.invoke(animations, entity);
        advance.invoke(animations, 0.025f, evaluator);
        require(!targeted(animations, "use_mainhand:bow"), "release must retire the bow draw");
        entity.using = true;
        update.invoke(animations, entity);
        var nextDraw = animations.getActiveAnimation("use_mainhand:bow");
        require(nextDraw != drawing && nextDraw.time == 0f,
                "redraw during release fade must restart instead of flashing to the previous draw");
        advance.invoke(animations, 0.5f, evaluator);
        require(nextDraw.time == 0.5f && nextDraw.weight == 1f,
                "the second draw must reach the same time and weight as the first");
        sample.invoke(animations, evaluator, false);
        require(Math.abs(runtime.getPart("RightArm").animRotRaw().x - drawnPitch) < 0.001f,
                "the second draw must reproduce the first draw's arm pose");
        System.out.println("PASS reference YSM bow: arm pose, swing priority, held clock, release and redraw");
    }

    private static void verifyBowHands(HandEntity entity, Method update) throws ReflectiveOperationException {
        for (HumanoidArm mainArm : HumanoidArm.values()) {
            for (InteractionHand hand : InteractionHand.values()) {
                entity.mainArm = mainArm;
                entity.usedHand = hand;
                entity.main = hand == InteractionHand.MAIN_HAND ? new ItemStack(Items.BOW) : ItemStack.EMPTY;
                entity.offhand = hand == InteractionHand.OFF_HAND ? new ItemStack(Items.BOW) : ItemStack.EMPTY;
                boolean left = hand == InteractionHand.MAIN_HAND
                        ? mainArm == HumanoidArm.LEFT : mainArm == HumanoidArm.RIGHT;
                String name = left ? "use_offhand:bow" : "use_mainhand:bow";
                var animations = player("use_mainhand:bow", "use_offhand:bow", "swing:bow", "swing_offhand:bow");
                entity.using = true;
                entity.swinging = true;
                entity.swingingArm = hand;
                update.invoke(animations, entity);
                require(targeted(animations, name), "first bow draw must survive a swing: " + mainArm + "/" + hand);
                entity.swinging = false;
                entity.using = false;
                update.invoke(animations, entity);
                entity.using = true;
                update.invoke(animations, entity);
                require(targeted(animations, name) && animations.getActiveAnimation(name).time == 0f,
                        "bow redraw must restart for " + mainArm + "/" + hand);
                entity.using = false;
            }
        }
    }

    /**
     * Sparkle registers hold_offhand and hold_mainhand as separate controllers; each returns PAUSE only
     * while its own hand swings or uses an item. PAUSE keeps the clock running and resumes the same clip.
     */
    private static void verifyHoldPause(HandEntity entity, Method update) throws ReflectiveOperationException {
        Method advance = YsmAnimationPlayer.class.getDeclaredMethod("advanceAnimationTimeline",
                float.class, ExpressionEvaluator.class);
        advance.setAccessible(true);
        var evaluator = ExpressionEvaluator.evaluator(new org.figuramc.figura.avatar.Avatar.MolangContext(null));
        entity.mainArm = HumanoidArm.RIGHT;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.main = new ItemStack(Items.BOW);
        entity.offhand = ItemStack.EMPTY;
        entity.using = false;
        entity.swinging = false;
        var animations = player("use_mainhand:bow", "hold_mainhand$minecraft:bow", "hold_offhand:empty");
        update.invoke(animations, entity);
        var mainHold = animations.getActiveAnimation("hold_mainhand$minecraft:bow");
        var offHold = animations.getActiveAnimation("hold_offhand:empty");
        require(mainHold != null && offHold != null, "both hands start their own hold");
        require(mainHold.poseOrder == YsmControllerSlot.executionOrder("hold_mainhand")
                        && offHold.poseOrder == YsmControllerSlot.executionOrder("hold_offhand"),
                "each hold belongs to its reference hold stage");

        entity.using = true;
        update.invoke(animations, entity);
        require(targeted(animations, "use_mainhand:bow"), "main-hand use starts its action");
        require(animations.getActiveAnimation("hold_offhand:empty") == offHold && targeted(animations, "hold_offhand:empty")
                        && !offHold.paused,
                "main-hand use must not fade or pause the off-hand hold");
        require(animations.getActiveAnimation("hold_mainhand$minecraft:bow") == mainHold && mainHold.paused,
                "main-hand use pauses its own hold instead of replacing it");
        float heldTime = mainHold.time;
        advance.invoke(animations, 0.25f, evaluator);
        require(mainHold.time > heldTime, "a paused hold keeps advancing its clock");

        entity.using = false;
        update.invoke(animations, entity);
        require(animations.getActiveAnimation("hold_mainhand$minecraft:bow") == mainHold && !mainHold.paused
                        && targeted(animations, "hold_mainhand$minecraft:bow"),
                "releasing use resumes the same hold clip and clock");

        entity.swinging = true;
        entity.swingingArm = InteractionHand.OFF_HAND;
        update.invoke(animations, entity);
        require(offHold.paused && !mainHold.paused, "an off-hand swing pauses only the off-hand hold");
        entity.swinging = false;
        update.invoke(animations, entity);
        require(!offHold.paused && animations.getActiveAnimation("hold_offhand:empty") == offHold,
                "the off-hand hold resumes after its swing");
        System.out.println("PASS hand holds: independent per hand, paused only by their own swing/use");
    }

    /**
     * Sparkle AnimationFormatValidator: format version >= 19 (plaintext folders report 65535) or a clip from
     * the built-in default model keeps the authored loop; older binary models use the predicate's loop
     * (LOOP for hold/use, HOLD_ON_LAST_FRAME for a *:lance hold).
     */
    private static void verifyLegacyLoopOverride(HandEntity entity, Method update) throws ReflectiveOperationException {
        entity.mainArm = HumanoidArm.RIGHT;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.main = new ItemStack(Items.BOW);
        entity.offhand = ItemStack.EMPTY;
        entity.swinging = false;
        String hold = "hold_mainhand$minecraft:bow";
        String use = "use_mainhand:bow";
        for (int version : new int[]{18, 19, 65535}) {
            var animations = onceClips(hold, use);
            if (version != 65535) {
                animations.setModelFormatVersion(version);
            }
            YsmAnimationClip.LoopMode expected = version < 19
                    ? YsmAnimationClip.LoopMode.LOOP : YsmAnimationClip.LoopMode.ONCE;
            entity.using = false;
            update.invoke(animations, entity);
            require(animations.getActiveAnimation(hold).loopMode == expected,
                    "hold loop for format " + version + " must be " + expected);
            entity.using = true;
            update.invoke(animations, entity);
            require(animations.getActiveAnimation(use).loopMode == expected,
                    "use loop for format " + version + " must be " + expected);
        }
        entity.using = false;
        System.out.println("PASS hand loop override: legacy binary formats use predicate loops, modern keep authored");
    }

    /**
     * Sparkle's use and swing predicates read the local input pulse through InputStateKey, so a
     * pending click starts the authored action before vanilla mirrors the same state. OmniMorph's
     * Molang queries already work that way; native hand selection must agree with them.
     */
    private static void verifyLocalInputPulse(HandEntity entity, Method update) throws ReflectiveOperationException {
        YsmHandActionState hands = YsmInputState.device().hands();
        entity.mainArm = HumanoidArm.RIGHT;
        entity.usedHand = InteractionHand.MAIN_HAND;
        entity.main = new ItemStack(Items.BOW);
        entity.offhand = ItemStack.EMPTY;
        entity.using = false;
        entity.swinging = false;
        hands.prepare(entity, true);

        var animations = localPlayer("hold_mainhand:bow", "use_mainhand:bow", "swing:bow");
        update.invoke(animations, entity);
        var hold = animations.getActiveAnimation("hold_mainhand:bow");
        require(targeted(animations, "hold_mainhand:bow"), "the holding hand starts its authored grip");

        hands.mouseEvent(1, 1);
        require(hands.using(entity, InteractionHand.MAIN_HAND, true), "the fixture starts a pending use pulse");
        update.invoke(animations, entity);
        require(targeted(animations, "use_mainhand:bow"), "a pending use pulse alone starts the drawing animation");
        require(animations.getActiveAnimation("use_mainhand:bow").poseOrder
                        == YsmControllerSlot.executionOrder("use"),
                "a use clip belongs to the reference use stage");
        require(hold.paused, "a pending use pulse pauses its own hold like a native use");

        hands.mouseEvent(1, 0);
        require(!hands.using(entity, InteractionHand.MAIN_HAND, true), "releasing the button clears the use pulse");
        update.invoke(animations, entity);
        require(!targeted(animations, "use_mainhand:bow") && targeted(animations, "hold_mainhand:bow") && !hold.paused,
                "ending the use pulse resumes the same authored grip");

        hands.mouseEvent(0, 1);
        require(hands.swinging(entity, InteractionHand.MAIN_HAND, true), "the fixture starts a pending swing pulse");
        update.invoke(animations, entity);
        require(targeted(animations, "swing:bow"), "a pending swing pulse alone starts the swing animation");
        require(animations.getActiveAnimation("swing:bow").poseOrder
                        == YsmControllerSlot.executionOrder("swing"),
                "a swing clip belongs to the reference swing stage");

        var observer = player("hold_mainhand:bow", "use_mainhand:bow");
        // A fresh device state keeps the pulse sequence apart from the swing above.
        hands.prepare(entity, false);
        hands.prepare(entity, true);
        hands.mouseEvent(1, 1);
        update.invoke(observer, entity);
        require(!targeted(observer, "use_mainhand:bow"),
                "a pending local pulse never selects an action for a model without device input");

        hands.prepare(entity, false);
        update.invoke(animations, entity);
        require(!targeted(animations, "swing:bow"), "clearing the device state retires the pulse swing");
        System.out.println("PASS local input pulses: native hand selection shares the query layer's pending use and swing");
    }

    /**
     * The reference registers post_main (30) before use (41), so an author post_main idle pose must
     * not overwrite the arms a later native use stage writes. Both write RightArm here.
     */
    private static void verifyControllerStageInterleaving(Method update) throws ReflectiveOperationException {
        YsmModelRuntime runtime = stageRuntime();
        YsmAnimationPlayer player = runtime.animations();
        player.registerAnimations(YsmAnimationParser.parse("""
                {"animations":{
                  "post_idle":{"loop":true,"animation_length":1,
                    "bones":{"RightArm":{"rotation":[-90,0,0]}}},
                  "use_mainhand:bow":{"loop":true,"animation_length":1,
                    "bones":{"RightArm":{"rotation":[45,0,0]}}}
                }}
                """, MolangEngine.fromCustomBinding(key -> null)));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "player.post_main":{"initial_state":"active","states":{
                    "active":{"animations":["post_idle"]}}}
                }}
                """, MolangEngine.fromCustomBinding(key -> null)));
        var evaluator = ExpressionEvaluator.evaluator(new org.figuramc.figura.avatar.Avatar.MolangContext(null));
        player.controllerRuntime().update(evaluator, 0f, false);

        HandEntity draw = newHandEntity();
        draw.usedHand = InteractionHand.MAIN_HAND;
        draw.mainArm = HumanoidArm.RIGHT;
        draw.main = new ItemStack(Items.BOW);
        draw.using = true;
        update.invoke(player, draw);
        var use = player.getActiveAnimation("use_mainhand:bow");
        require(use != null && use.poseOrder > YsmControllerSlot.executionOrder("player.post_main"),
                "the use stage follows post_main");
        use.weight = 1f;
        use.targetWeight = 1f;
        sample(player, evaluator);
        require(Math.abs(runtime.getPart("RightArm").animRotRaw().x - 45f) < 0.001f,
                "post_main must not overwrite the later native use arm pose: "
                        + runtime.getPart("RightArm").animRotRaw().x);
        require(Math.abs(player.getControllerAnimation("player.post_main", "post_idle").weight - 1f) < 0.001f,
                "the declared post_main clip still runs");
        System.out.println("PASS hand stages: the native use arm pose survives the post_main layer");
    }

    private static YsmModelRuntime stageRuntime() throws ReflectiveOperationException {
        var constructor = YsmModelRuntime.class.getDeclaredConstructor(org.figuramc.figura.avatar.Avatar.class,
                YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                String.class, String.class, Map.class, float.class, float.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, stageGeometry(), stageGeometry(), null, "fixture", false,
                "NEW", "hand-stage-fixture", Map.of(), 1f, 1f, true);
    }

    private static YsmGeometry stageGeometry() {
        YsmGeometry geometry = new YsmGeometry();
        var root = new YsmGeometry.Bone("root", null, new float[3], new float[3]);
        var arm = new YsmGeometry.Bone("RightArm", "root", new float[3], new float[3]);
        geometry.bones.put(root.name, root);
        geometry.bones.put(arm.name, arm);
        geometry.roots.add(root);
        return geometry;
    }

    private static HandEntity newHandEntity() throws ReflectiveOperationException {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (HandEntity) ((Unsafe) unsafeField.get(null)).allocateInstance(HandEntity.class);
    }

    private static void sample(YsmAnimationPlayer player, ExpressionEvaluator<?> evaluator)
            throws ReflectiveOperationException {
        Method sample = YsmAnimationPlayer.class.getDeclaredMethod("sampleAnimations",
                ExpressionEvaluator.class, boolean.class);
        sample.setAccessible(true);
        sample.invoke(player, evaluator, false);
    }

    private static YsmAnimationPlayer onceClips(String... names) {
        String definitions = java.util.Arrays.stream(names)
                .map(name -> "\"" + name + "\":{\"loop\":false,\"animation_length\":1,"
                        + "\"bones\":{\"RightArm\":{\"rotation\":[-90,0,0]}}}")
                .collect(java.util.stream.Collectors.joining(","));
        Map<String, YsmAnimationClip> clips = YsmAnimationParser.parse("{\"animations\":{" + definitions + "}}",
                MolangEngine.fromCustomBinding(key -> null));
        var player = new YsmAnimationPlayer(null);
        player.registerAnimations(clips);
        return player;
    }

    private static void verifyUseTransforms(HandEntity entity) {
        ItemStack spear = new ItemStack(Items.DIAMOND_SPEAR);
        spear.set(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 10,
                Optional.of(new KineticWeapon.Condition(30, 0f, 0f)), Optional.empty(),
                Optional.of(new KineticWeapon.Condition(60, 0f, 0f)), 0.5f, 1f,
                Optional.empty(), Optional.empty()));
        // Independent reference golden angles: raising, tired and disengaged phase boundaries/midpoints.
        int[] ticks = {0, 1, 5, 10, 25, 40, 55, 70, 100};
        float[] compensation = {60f, 58.32f, 30f, 0f, 0f, 0f, 0f, 0f, 0f};
        float[] forward = {90f, 90f, 90f, 90f, 135f, 180f, 180f, 180f, 180f};
        float[] down = {0f, 0f, 0f, 0f, 0f, 0f, 3f, 6f, 6f};
        for (HumanoidArm mainArm : HumanoidArm.values()) {
            entity.mainArm = mainArm;
            for (InteractionHand hand : InteractionHand.values()) {
                entity.usedHand = hand;
                boolean activeLeft = hand == InteractionHand.MAIN_HAND
                        ? mainArm == HumanoidArm.LEFT : mainArm == HumanoidArm.RIGHT;
                for (boolean left : new boolean[]{false, true}) {
                    entity.using = false;
                    var held = new PoseStack();
                    YsmHeldItemTransforms.apply(held, entity, spear, left, false);
                    entity.using = true;
                    for (int index = 0; index < ticks.length; index++) {
                        entity.useTicks = ticks[index];
                        var expected = new PoseStack();
                        expected.last().pose().set(held.last().pose());
                        if (left == activeLeft) {
                            expected.rotateAround(com.mojang.math.Axis.XP.rotationDegrees(compensation[index]), 0f, .125f, .125f);
                            expected.rotateAround(com.mojang.math.Axis.YP.rotationDegrees((left ? -1f : 1f) * forward[index]),
                                    0f, .125f, .125f);
                            expected.rotateAround(com.mojang.math.Axis.XP.rotationDegrees(down[index]), 0f, .125f, .125f);
                        }
                        var actual = new PoseStack();
                        YsmHeldItemTransforms.apply(actual, entity, spear, left, false);
                        require(actual.last().pose().equals(expected.last().pose(), 0.00001f),
                                "spear orientation/grip must match reference YSM use at tick " + ticks[index]
                                        + ", mainArm=" + mainArm + ", hand=" + hand + ", left=" + left);
                    }
                    ItemStack trident = new ItemStack(Items.TRIDENT);
                    entity.using = false;
                    var restingTrident = new PoseStack();
                    YsmHeldItemTransforms.apply(restingTrident, entity, trident, left, false);
                    entity.using = true;
                    var chargingTrident = new PoseStack();
                    YsmHeldItemTransforms.apply(chargingTrident, entity, trident, left, false);
                    require(chargingTrident.last().pose().equals(restingTrident.last().pose(), 0.00001f),
                            "trident animation must not receive a second kinetic item rotation");
                }
            }
        }
        System.out.println("PASS spear/trident transforms: both main arms, both hands, charge phases and inactive hand");
    }

    private static YsmAnimationPlayer player(String... names) {
        var player = new YsmAnimationPlayer(null);
        player.registerAnimations(clips(names));
        return player;
    }

    /** A player that owns the client's device input, as the rendered local model does. */
    private static YsmAnimationPlayer localPlayer(String... names) {
        var player = new YsmAnimationPlayer(null) {
            @Override
            protected boolean ownsDeviceHandInput(LivingEntity entity) {
                return true;
            }
        };
        player.registerAnimations(clips(names));
        return player;
    }

    private static Map<String, YsmAnimationClip> clips(String... names) {
        String definitions = java.util.Arrays.stream(names)
                .map(name -> "\"" + name + "\":{\"loop\":true,\"animation_length\":1,"
                        + "\"bones\":{\"RightArm\":{\"rotation\":[-90,0,0]}}}")
                .collect(java.util.stream.Collectors.joining(","));
        String json = "{\"animations\":{" + definitions + "}}";
        return YsmAnimationParser.parse(json, MolangEngine.fromCustomBinding(key -> null));
    }

    private static boolean targeted(YsmAnimationPlayer player, String name) {
        var expectedClip = player.getClips().get(name);
        return player.getClips().keySet().stream().map(player::getActiveAnimation)
                .anyMatch(playing -> playing != null && playing.clip == expectedClip && playing.targetWeight > 0f);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class HandEntity extends LivingEntity {
        private ItemStack main;
        private ItemStack offhand;
        private boolean using;
        private InteractionHand usedHand;
        private int useTicks = 10;
        private HumanoidArm mainArm;

        private HandEntity() {
            super(EntityType.ARMOR_STAND, null);
        }

        @Override
        public HumanoidArm getMainArm() {
            return mainArm;
        }

        @Override
        public Pose getPose() {
            return Pose.STANDING;
        }

        @Override
        public boolean isSleeping() {
            return false;
        }

        @Override
        public ItemStack getItemInHand(InteractionHand hand) {
            if (hand == InteractionHand.MAIN_HAND) {
                return main;
            }
            return offhand == null ? ItemStack.EMPTY : offhand;
        }

        @Override
        public ItemStack getMainHandItem() {
            return getItemInHand(InteractionHand.MAIN_HAND);
        }

        @Override
        public ItemStack getOffhandItem() {
            return getItemInHand(InteractionHand.OFF_HAND);
        }

        @Override
        public boolean isUsingItem() {
            return using;
        }

        @Override
        public InteractionHand getUsedItemHand() {
            return usedHand;
        }

        @Override
        public ItemStack getUseItem() {
            return getItemInHand(usedHand);
        }

        @Override
        public boolean isFallFlying() {
            return false;
        }

        @Override
        public int getTicksUsingItem() {
            return using ? useTicks : 0;
        }

        @Override
        public float getTicksSinceLastKineticHitFeedback(float partialTick) {
            return Float.MAX_VALUE;
        }
    }
}
