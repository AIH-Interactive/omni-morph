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

import java.lang.reflect.Method;
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
