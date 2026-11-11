package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.mixin.render.renderers.PlayerRendererMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

/** Checks the actual 1.21.1 hand hooks and draft snapshot; no rendered-pixel assertions. */
final class OutfitHandRenderingSmokeTest {
    static void run(Unsafe unsafe, LibraryPreviewSession preview) throws Exception {
        var client = Minecraft.getInstance();
        var timer = field(Minecraft.class, "timer");
        Object previousTimer = timer.get(client);
        timer.set(client, new DeltaTracker.Timer(20, 0, value -> value));
        try {
            var renderer = (HandRenderer) unsafe.allocateInstance(HandRenderer.class);
            renderer.initialize();
            var avatar = field(PlayerRendererMixin.class, "avatar");
            var outfit = field(PlayerRendererMixin.class, "figura$armOutfit");
            var head = PlayerRendererMixin.class.getDeclaredMethod("figura$renderYsmHand", PoseStack.class,
                    MultiBufferSource.class, int.class, AbstractClientPlayer.class, ModelPart.class,
                    ModelPart.class, CallbackInfo.class);
            var post = PlayerRendererMixin.class.getDeclaredMethod("postRenderHand", PoseStack.class,
                    MultiBufferSource.class, int.class, AbstractClientPlayer.class, ModelPart.class,
                    ModelPart.class, CallbackInfo.class);
            head.setAccessible(true);
            post.setAccessible(true);
            var recording = (RecordingAvatar) unsafe.allocateInstance(RecordingAvatar.class);
            for (var arm : List.of(renderer.getModel().leftArm, renderer.getModel().rightArm)) {
                var stack = new PoseStack();
                head.invoke(renderer, stack, null, 0, client.player, arm, arm,
                        new CallbackInfo("renderHand", true));
                if (avatar.get(renderer) != preview.outfitBase() || outfit.get(renderer) == null) {
                    throw new AssertionError("Both hands must capture the local draft base and composition");
                }
                // Record the draw callback without requiring an OpenGL context. The selected draft above
                // is checked separately; the real RETURN hook must invoke one arm and clear its state.
                avatar.set(renderer, recording);
                post.invoke(renderer, stack, null, 0, client.player, arm, arm,
                        new CallbackInfo("renderHand", false));
                if (avatar.get(renderer) != null || outfit.get(renderer) != null) {
                    throw new AssertionError("Hand hooks must clear captured state after drawing");
                }
            }
            if (recording.calls != 2) {
                throw new AssertionError("Both first-person hand callbacks must run once");
            }
            System.out.println("PASS first-person draft hand capture, both draw callbacks and cleanup; pixels unverified");
        } finally {
            timer.set(client, previousTimer);
            top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class RecordingAvatar extends Avatar {
        private int calls;

        private RecordingAvatar() {
            super((java.util.UUID) null);
        }

        @Override
        public void firstPersonRender(PoseStack stack, MultiBufferSource buffers, Player player,
                                      PlayerModel model, ModelPart arm, int light, float delta) {
            calls++;
        }
    }

    private static final class HandRenderer extends PlayerRendererMixin {
        private HandRenderer() {
            super(null, null, 0);
        }

        private void initialize() {
            model = new PlayerModel<>(LayerDefinition.create(
                    PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
            return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
        }
    }
}
