package top.aihmc.omnimorph.model.ysm;

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
        Method update = YsmAnimationPlayer.class.getDeclaredMethod("updateNativeHandAnimations", LivingEntity.class);
        update.setAccessible(true);
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var entity = (HandEntity) ((Unsafe) unsafeField.get(null)).allocateInstance(HandEntity.class);
        entity.usedHand = InteractionHand.MAIN_HAND;
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
        player = player("use_mainhand:spear");
        update.invoke(player, entity);
        require(!targeted(player, "use_mainhand:spear"), "holding a spear must not borrow its use pose");
        entity.using = true;
        update.invoke(player, entity);
        require(targeted(player, "use_mainhand:spear"), "using a spear selects its declared use pose");
        System.out.println("PASS native hand state: holding, using and release preserve conditional clip ownership");
    }

    private static YsmAnimationPlayer player(String name) {
        String json = "{\"animations\":{\"" + name + "\":{\"loop\":true,\"animation_length\":1,"
                + "\"bones\":{\"RightArm\":{\"rotation\":[-90,0,0]}}}}}";
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
        private boolean using;
        private InteractionHand usedHand;

        private HandEntity() {
            super(EntityType.ARMOR_STAND, null);
        }

        @Override
        public HumanoidArm getMainArm() {
            return HumanoidArm.RIGHT;
        }

        @Override
        public Pose getPose() {
            return Pose.STANDING;
        }

        @Override
        public ItemStack getItemInHand(InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND ? main : ItemStack.EMPTY;
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
            return using ? 10 : 0;
        }
    }
}
