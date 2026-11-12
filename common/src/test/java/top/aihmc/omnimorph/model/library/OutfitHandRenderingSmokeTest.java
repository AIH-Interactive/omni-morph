package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.mixin.render.renderers.PlayerRendererMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Executes the production hand submission hook; this is not a rendered-pixel test. */
final class OutfitHandRenderingSmokeTest {
    static void run(Unsafe unsafe, LibraryPreviewSession preview) throws Exception {
        var client = Minecraft.getInstance();
        var timerField = Minecraft.class.getDeclaredField("deltaTracker");
        timerField.setAccessible(true);
        Object previousTimer = timerField.get(client);
        timerField.set(client, new DeltaTracker.Timer(20, 0, value -> value));
        try {
            var renderer = (HandRenderer) unsafe.allocateInstance(HandRenderer.class);
            renderer.initialize();
            var submitted = new ArrayList<Avatar>();
            var states = new ArrayList<AvatarRenderState>();
            var collector = (SubmitNodeCollector) Proxy.newProxyInstance(
                    OutfitHandRenderingSmokeTest.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class, NodeCollectorExtension.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("submitFiguraModel")) {
                            submitted.add((Avatar) arguments[0]);
                            states.add((AvatarRenderState) arguments[1]);
                        }
                        return null;
                    });
            var post = PlayerRendererMixin.class.getDeclaredMethod("postRenderHand", PoseStack.class,
                    SubmitNodeCollector.class, int.class, Identifier.class, ModelPart.class,
                    boolean.class, CallbackInfo.class);
            post.setAccessible(true);
            var texture = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
            for (var arm : List.of(renderer.getModel().leftArm, renderer.getModel().rightArm)) {
                post.invoke(renderer, new PoseStack(), collector, 0, texture, arm, false,
                        new CallbackInfo("renderHand", false));
            }
            if (submitted.size() != 2 || submitted.stream().anyMatch(base -> base != preview.outfitBase())) {
                throw new AssertionError("Both first-person hands must submit the local Figura draft "
                        + "even when no resident Avatar is selected");
            }
            for (var state : states) {
                if (OutfitRenderBinding.avatar(state) != preview.outfitBase()
                        || OutfitRenderBinding.composition(state) == null) {
                    throw new AssertionError("Hand base and composition must come from the same draft snapshot");
                }
            }
            var itemRenderer = (HandItemRenderer) unsafe.allocateInstance(HandItemRenderer.class);
            Avatar draftBase = preview.outfitBase();
            top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.addQuad(draftBase);
            var itemPart = draftBase.renderer.root.children.getLast();
            itemPart.parentType = org.figuramc.figura.model.ParentType.Item;
            draftBase.renderer.sortParts();
            draftBase.luaRuntime.setGlobal("draft_item", itemPart);
            draftBase.loadScript("draft_item_regression", """
                    events.item_render:register(function() return draft_item end, 'draftRegression')
                    """).call();
            var wrap = org.figuramc.figura.mixin.render.renderers.ItemInHandRendererMixin.class.getDeclaredMethod(
                    "figura$withFirstPersonItems", float.class, PoseStack.class, SubmitNodeCollector.class,
                    net.minecraft.client.player.LocalPlayer.class, int.class,
                    com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
            wrap.setAccessible(true);
            net.minecraft.world.item.Items.AIR.builtInRegistryHolder().bindComponents(
                    net.minecraft.core.component.DataComponentMap.EMPTY);
            try {
                com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> drawing = arguments -> {
                    if (!top.aihmc.omnimorph.model.rendering.FirstPersonItemRender.submit(
                            net.minecraft.world.item.ItemStack.EMPTY,
                            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            net.minecraft.client.resources.model.cuboid.ItemTransform.NO_TRANSFORM,
                            new PoseStack(), collector, 0, 0)) {
                        throw new AssertionError("First-person item must use the visible local draft, not the resident Avatar");
                    }
                    return null;
                };
                wrap.invoke(itemRenderer, 1f, new PoseStack(), collector, client.player, 0, drawing);
            } finally {
                draftBase.loadScript("remove_draft_item_regression",
                        "events.item_render:remove('draftRegression')").call();
                draftBase.renderer.root.children.remove(itemPart);
                draftBase.renderer.sortParts();
            }
            System.out.println("PASS first-person draft hands: actual Figura submission hook, both arms "
                    + "and matching captured base/composition; real game rendering MANUAL/PENDING");
        } finally {
            timerField.set(client, previousTimer);
            OutfitRenderBinding.finishFrame();
        }
    }

    private static final class HandItemRenderer extends org.figuramc.figura.mixin.render.renderers.ItemInHandRendererMixin {
        @Override
        protected void renderPlayerArm(PoseStack matrices, SubmitNodeCollector collector, int light,
                                       float equipProgress, float swingProgress, net.minecraft.world.entity.HumanoidArm arm) {
        }
    }

    private static final class HandRenderer extends PlayerRendererMixin {
        private HandRenderer() {
            super(null, null, 0);
        }

        private void initialize() {
            model = new PlayerModel(LayerDefinition.create(
                    PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        }

        @Override
        public AvatarRenderState createRenderState() {
            return new AvatarRenderState();
        }

        @Override
        public void extractRenderState(AbstractClientPlayer player, AvatarRenderState state, float delta) {
            // Entity extraction is outside this submission regression; production outfit capture still runs.
        }

        @Override
        protected void finalizeRenderState(AbstractClientPlayer player, AvatarRenderState state) {
            // Shadow extraction needs a live level and is unrelated to hand renderer selection.
        }

        @Override
        public Identifier getTextureLocation(AvatarRenderState state) {
            return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        }
    }
}
