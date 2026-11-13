package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import top.aihmc.omnimorph.model.rendering.FirstPersonItemRender;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FiguraSubmission;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads the original encrypted author sample; validates submission and native vertices, not pixels. */
public final class YsmFirstPersonWeaponSmokeTest {
    public static void main(String[] args) throws Exception {
        YsmMaterialNativeSmokeTest.replayAcceleration(Path.of(args[0]), YsmFirstPersonWeaponSmokeTest::check);
        System.out.println("PASS YSM first-person weapon: authored hold/swing, deferred geometry and fallback");
    }

    private static void check(YsmModelRuntime runtime) {
        var setting = org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT;
        boolean previous = setting.value;
        require(!setting.defaultValue, "native weapon replacement defaults to disabled");
        setting.setValue("false");
        try {
            checkReplacement(runtime);
        } finally {
            setting.setValue(Boolean.toString(previous));
        }
    }

    private static void checkReplacement(YsmModelRuntime runtime) {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) field.get(null);
            net.minecraft.client.Minecraft.getInstance().level =
                    (RegistryLevel) unsafe.allocateInstance(RegistryLevel.class);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        require(runtime.hasArmModel() && !runtime.getArmGeometry().bones.containsKey("RightSword"),
                "author weapon belongs to body geometry, not the separate arm model");
        var hold = runtime.animations().play("hold_mainhand:sword", true, 1);
        require(hold != null,
                "author sword hold animation loads");
        var state = new LivingEntityRenderState();
        state.ageInTicks = 1;
        runtime.updateAnimations(state, null, true);
        require(hold.clip.boneAnimations.containsKey("RightHandLocator"),
                "author sword hold declares its hand-item visibility track");
        List<FiguraSubmission> queue = new ArrayList<>();
        var collector = collector(runtime, queue);
        FirstPersonItemRender.withAvatar(runtime.owner(), () -> {
            require(!FirstPersonItemRender.submit(sword, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                    "disabled setting preserves the vanilla YSM item");
            require(queue.isEmpty(), "disabled setting queues no YSM replacement");
            org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT.setValue("true");
            runtime.updateAnimations(state, null, true);
            require(FirstPersonItemRender.submit(sword, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                    "YSM authored hidden hand locator replaces the vanilla first-person sword");
            require(!FirstPersonItemRender.submit(sword, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                    "unmodified opposite hand retains the vanilla item");
            require(!FirstPersonItemRender.submit(sword, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                    "third-person route remains independent");
        });
        require(queue.size() == 1, "hidden author item queues one native replacement");
        int idleVertices = vertices(runtime, queue.removeFirst());
        require(idleVertices == 0,
                "sheathed sword remains hidden in the hand during the author's idle pose");

        runtime.animations().controllerRuntime().applyRemoteState(
                java.util.Map.of("player.parallel_6", "剑1"), java.util.Map.of("player.parallel_6", 0.15f));
        var swing = runtime.animations().playControllerAnimation("player.parallel_6", "剑1",
                top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip.LoopMode.LOOP, 1, 0);
        require(swing != null, "author sword swing animation loads");
        swing.time = 0.15f;
        runtime.animations().beginControllerPoseTransition("player.parallel_6",
                top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition.linear(0));
        // Resample this controller-owned author pose without advancing it into a different state.
        runtime.updateAnimations(state, null, true);
        require(runtime.isBoneVisibleInHierarchy("LeftSword"),
                "first-person animation sampling also updates the body weapon pose");
        FirstPersonItemRender.withAvatar(runtime.owner(), () -> require(
                FirstPersonItemRender.submit(sword, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                "swing submits the author's native weapon"));
        require(queue.size() == 1, "swing queues one replacement");
        runtime.getPart("LeftSword").setVisible(false);
        int swingVertices = vertices(runtime, queue.getFirst());
        require(swingVertices > 0,
                "deferred replacement retains its captured animated weapon and glow geometry");
        require(!runtime.getArmPart("RightHandLocator").hiddenScaleRaw(),
                "weapon rendering preserves the independent default first-person arm pose");
        queue.clear();
        require(runtime.animations().play("hold_offhand:sword", true, 1) != null,
                "author offhand hold animation loads");
        runtime.updateAnimations(state, null, true);
        require(swing.time == 0.15f, "resampling hands does not advance the animation timeline twice");
        FirstPersonItemRender.withAvatar(runtime.owner(), () -> {
            for (var context : List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemDisplayContext.FIRST_PERSON_LEFT_HAND)) {
                require(FirstPersonItemRender.submit(sword, context, ItemTransform.NO_TRANSFORM,
                        new PoseStack(), collector, 0xf000f0, 0),
                        "both authored hand locators can replace their corresponding item");
            }
        });
        require(queue.size() == 2, "dual hand replacements queue separately");
        require(vertices(runtime, queue.get(0)) + vertices(runtime, queue.get(1)) == swingVertices,
                "dual hand replacement renders the visible author weapon exactly once");

        runtime.getPart("RightHandLocator").setVisible(true);
        runtime.getPart("RightHandLocator").animScaleRaw().set(1, 1, 1);
        runtime.getArmPart("RightHandLocator").setVisible(true);
        runtime.getArmPart("RightHandLocator").animScaleRaw().set(1, 1, 1);
        FirstPersonItemRender.withAvatar(runtime.owner(), () -> {
            require(!FirstPersonItemRender.submit(sword, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                    "a visible author locator preserves the original vanilla item");
            require(!FirstPersonItemRender.submit(ItemStack.EMPTY, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                    "an empty hand cannot submit an item replacement");
        });
        System.out.println("Author sample vertices: idle=" + idleVertices + ", swing=" + swingVertices);
    }

    private static SubmitNodeCollector collector(YsmModelRuntime runtime, List<FiguraSubmission> queue) {
        return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class, NodeCollectorExtension.class}, (proxy, method, values) -> {
                    if (method.getName().equals("submitFiguraModel")) {
                        @SuppressWarnings("unchecked")
                        var draw = (org.apache.commons.lang3.function.TriFunction<org.figuramc.figura.avatar.Avatar,
                                net.minecraft.client.renderer.entity.state.EntityRenderState,
                                FiguraVertexSource, Void>) values[2];
                        queue.add(new FiguraSubmission(runtime.owner(), null, draw));
                    }
                    return method.getName().equals("getFiguraSubmissions") ? queue : null;
                });
    }

    private static int vertices(YsmModelRuntime runtime, FiguraSubmission submission) {
        int[] count = {0};
        var sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, values) -> {
                    if (method.getName().equals("addVertex")) {
                        count[0]++;
                        for (Object value : values) {
                            if (value instanceof Float coordinate) {
                                require(Float.isFinite(coordinate), "weapon vertex remains finite");
                            }
                        }
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        submission.renderer().apply(runtime.owner(), null, type -> sink);
        return count[0];
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RegistryLevel extends net.minecraft.client.multiplayer.ClientLevel {
        private RegistryLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public net.minecraft.core.RegistryAccess registryAccess() {
            return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        }
    }
}
