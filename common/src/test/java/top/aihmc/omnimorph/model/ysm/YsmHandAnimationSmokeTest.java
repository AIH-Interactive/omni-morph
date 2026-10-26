package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
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
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
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
                } catch (ReflectiveOperationException failure) {
                    throw new RuntimeException(failure);
                } finally {
                    entity.using = false;
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
                    "kinetic spear must not use the legacy trident throwing pose at tick " + useTicks);
            require(!targeted(player, "use_mainhand"), "kinetic spear must not use an unrelated generic action");
            require(targeted(player, "hold_mainhand:spear"), "missing kinetic charge clip retains the authored grip");
        }

        player = player("lance_charge", "use_mainhand:spear");
        update.invoke(player, entity);
        require(targeted(player, "lance_charge"), "dedicated kinetic charge clip takes precedence");
        require(Float.isFinite(player.getActiveAnimation("lance_charge").sampledTime),
                "dedicated kinetic charge uses the weapon timeline");

        player = player("use_mainhand:lance", "use_mainhand:spear");
        update.invoke(player, entity);
        require(targeted(player, "use_mainhand:lance"), "declared lance use clip remains supported");

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
        var state = new ArmedEntityRenderState();
        state.ticksSinceKineticHitFeedback = Float.MAX_VALUE;
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
                    for (int ticks : new int[]{0, 1, 10, 25, 40, 70, 100}) {
                        entity.useTicks = ticks;
                        var expected = new PoseStack();
                        expected.last().pose().set(held.last().pose());
                        if (left == activeLeft) {
                            SpearAnimations.thirdPersonUseItem(state, expected, ticks,
                                    left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, spear);
                        }
                        var actual = new PoseStack();
                        YsmHeldItemTransforms.apply(actual, entity, spear, left, false);
                        require(actual.last().pose().equals(expected.last().pose(), 0.00001f),
                                "spear orientation/grip must match native use at tick " + ticks
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
        String definitions = java.util.Arrays.stream(names)
                .map(name -> "\"" + name + "\":{\"loop\":true,\"animation_length\":1,"
                        + "\"bones\":{\"RightArm\":{\"rotation\":[-90,0,0]}}}")
                .collect(java.util.stream.Collectors.joining(","));
        String json = "{\"animations\":{" + definitions + "}}";
        Map<String, YsmAnimationClip> clips = YsmAnimationParser.parse(json, MolangEngine.fromCustomBinding(key -> null));
        var player = new YsmAnimationPlayer(null);
        player.registerAnimations(clips);
        return player;
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
        public ItemStack getItemInHand(InteractionHand hand) {
            if (hand == InteractionHand.MAIN_HAND) {
                return main;
            }
            return offhand == null ? ItemStack.EMPTY : offhand;
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
