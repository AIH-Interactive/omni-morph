package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import top.aihmc.omnimorph.model.rendering.FirstPersonItemRender;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FiguraSubmission;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Formal Figura loading, Lua ITEM_RENDER and deferred native vertex consumption; no pixel claims. */
public final class FirstPersonItemRenderSmokeTest {
    public static void main(String[] args) throws Exception {
        YsmMaterialNativeSmokeTest.replayFiguraAcceleration(Path.of(args[0]), avatar -> check(avatar, null));
        System.out.println("PASS first-person item: formal Lua event, queued replacement and native vertices; game pending");
    }

    static void check(org.figuramc.figura.avatar.Avatar avatar,
                      org.figuramc.figura.model.rendering.texture.FiguraTextureSet material) {
        try {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            try {
                net.minecraft.client.Minecraft.getInstance().level =
                        (RegistryLevel) unsafe.allocateInstance(RegistryLevel.class);
            } catch (InstantiationException failure) {
                throw new AssertionError(failure);
            }
            OutfitNativeRenderSmokeTest.addQuad(avatar);
            var part = avatar.renderer.root.children.getLast();
            if (material != null) {
                part.textures = List.of(material);
            }
            part.parentType = ParentType.Item;
            avatar.renderer.sortParts();
            avatar.luaRuntime.setGlobal("test_item", part);
            avatar.loadScript("first_person_test", """
                    calls = 0
                    renderer:setFOV(0.5)
                    function events.item_render(item, mode)
                        calls = calls + 1
                        if item.id == "minecraft:stick" then return test_item end
                    end
                    """).call();
            avatar.renderMode = EntityRenderMode.FIRST_PERSON;
            require(top.aihmc.omnimorph.model.rendering.CameraFov.apply(80, avatar, true) == 40,
                    "item Avatar applies its Lua FOV multiplier");
            List<FiguraSubmission> submissions = new ArrayList<>();
            var collector = (SubmitNodeCollector) Proxy.newProxyInstance(
                    SubmitNodeCollector.class.getClassLoader(),
                    new Class<?>[]{SubmitNodeCollector.class, NodeCollectorExtension.class},
                    (proxy, method, values) -> {
                        if (method.getName().equals("submitFiguraModel")) {
                            require(!FirstPersonItemRender.submit(ItemStack.EMPTY,
                                    ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemTransform.NO_TRANSFORM,
                                    new PoseStack(), (SubmitNodeCollector) proxy, 0, 0),
                                    "nested item submission cannot re-enter ITEM_RENDER");
                            @SuppressWarnings("unchecked")
                            var drawing = (org.apache.commons.lang3.function.TriFunction<
                                    org.figuramc.figura.avatar.Avatar,
                                    net.minecraft.client.renderer.entity.state.EntityRenderState,
                                    MultiBufferSource, Void>) values[2];
                            submissions.add(new FiguraSubmission(avatar, null, drawing));
                        }
                        return method.getName().equals("getFiguraSubmissions") ? submissions : null;
                    });
            Items.STICK.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
            var stick = new ItemStack(Items.STICK);
            var pose = new PoseStack();
            FirstPersonItemRender.withAvatar(avatar, () -> {
                require(FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0xf000f0, 0),
                        "right-hand ITEM_RENDER replaces a valid Item part");
            });
            require(submissions.size() == 1, "one replacement reaches the deferred queue");
            var positions = new ArrayList<float[]>();
            var sink = OutfitNativeRenderSmokeTest.vertexSink(positions);
            submissions.getFirst().renderer().apply(avatar, null, type -> sink);
            require(positions.size() == 4, "the queued replacement emits the authored quad");
            require(top.aihmc.omnimorph.model.rendering.CameraFov.apply(100, avatar, true) == 50,
                    "item consumption preserves the same Avatar FOV multiplier");
            FirstPersonItemRender.withAvatar(avatar, () -> {
                require(FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0xf000f0, 0),
                        "left-hand replacement uses the same public event");
                require(!FirstPersonItemRender.submit(stick, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0, 0),
                        "third-person submissions retain their existing event route");
                part.parentType = ParentType.None;
                require(!FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0, 0),
                        "a non-Item return preserves vanilla rendering");
                part.parentType = ParentType.Item;
                avatar.luaRuntime.setGlobal("test_item", null);
                require(!FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0, 0),
                        "nil return preserves vanilla rendering");
                avatar.luaRuntime.setGlobal("test_item", part);
            });
            require(submissions.size() == 2, "only valid left/right replacements queue geometry");
            avatar.loadScript("item_event_count", "assert(calls == 4)").call();
            try {
                FirstPersonItemRender.withAvatar(avatar, () -> {
                    throw new IllegalStateException("drawing failure");
                });
            } catch (IllegalStateException expected) {
                require(expected.getMessage().equals("drawing failure"), "drawing failure propagates");
            }
            require(!FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemTransform.NO_TRANSFORM, pose, collector, 0, 0), "scope exit restores normal routing");
            var retired = org.figuramc.figura.avatar.Avatar.createLibraryPreview(
                    java.util.UUID.randomUUID(), "retired item Avatar");
            try {
                retired.load(avatar.nbt.copy());
                retired.initializePreviewPlayer(null);
                retired.luaRuntime.renderer.setFOV(0.25f);
                retired.clean();
                FirstPersonItemRender.withAvatar(retired, () -> require(
                        !FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                ItemTransform.NO_TRANSFORM, pose, collector, 0, 0),
                        "retired model cannot replace a newly submitted item"));
                require(top.aihmc.omnimorph.model.rendering.CameraFov.apply(80, retired, true) == 80,
                        "retired model cannot retain its FOV override");
            } finally {
                retired.clean();
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
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
