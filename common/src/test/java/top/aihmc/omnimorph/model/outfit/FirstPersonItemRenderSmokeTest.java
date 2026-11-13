package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.rendering.FirstPersonItemRender;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Formal Figura loading, Lua ITEM_RENDER and native vertex preparation; no pixel claims. */
public final class FirstPersonItemRenderSmokeTest {
    public static void main(String[] args) throws Exception {
        YsmMaterialNativeSmokeTest.replayFiguraModel(Path.of(args[0]), avatar -> check(avatar, null));
        System.out.println("PASS first-person item: formal Lua event, direct replacement and native vertices; game pending");
    }

    static void check(org.figuramc.figura.avatar.Avatar avatar,
                      org.figuramc.figura.model.rendering.texture.FiguraTextureSet material) {
        var setting = org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT;
        boolean previous = setting.value;
        require(!setting.defaultValue && !setting.hidden
                        && setting.parent == org.figuramc.figura.config.Configs.RENDERING,
                "first-person item replacement is a visible rendering setting, disabled by default");
        setting.setValue("false");
        try {
            checkReplacement(avatar, material);
        } finally {
            setting.setValue(Boolean.toString(previous));
        }
    }

    private static void checkReplacement(org.figuramc.figura.avatar.Avatar avatar,
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
            var part = com.google.common.collect.Iterables.getLast(avatar.renderer.root.children);
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
            var positions = new ArrayList<float[]>();
            var sink = OutfitNativeRenderSmokeTest.vertexSink(positions);
            net.minecraft.client.renderer.MultiBufferSource collector = type -> {
                require(!FirstPersonItemRender.submit(ItemStack.EMPTY,
                        ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemTransform.NO_TRANSFORM,
                        new PoseStack(), ignored -> sink, 0, 0),
                        "nested item drawing cannot re-enter ITEM_RENDER");
                return sink;
            };
            var stick = new ItemStack(Items.STICK);
            var pose = new PoseStack();
            FirstPersonItemRender.withAvatar(avatar, () -> {
                require(!FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0xf000f0, 0),
                        "disabled setting preserves vanilla item and skips ITEM_RENDER");
                require(positions.isEmpty(), "disabled setting emits no Figura replacement");
                org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT.setValue("true");
                require(FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemTransform.NO_TRANSFORM, pose, collector, 0xf000f0, 0),
                        "right-hand ITEM_RENDER replaces a valid Item part");
            });
            require(positions.size() == 4, "the native replacement emits the authored quad");
            org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT.setValue("false");
            FirstPersonItemRender.withAvatar(avatar, () -> require(
                    !FirstPersonItemRender.submit(stick, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            ItemTransform.NO_TRANSFORM, pose, collector, 0, 0),
                    "turning replacement off restores vanilla without reloading the avatar"));
            org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT.setValue("true");
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
            require(positions.size() == 8, "only valid left/right replacements emit geometry");
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

    static final class RegistryLevel extends net.minecraft.client.multiplayer.ClientLevel {
        private RegistryLevel() {
            super(null, null, null, null, 0, 0, null, null, false, 0L);
        }

        @Override
        public net.minecraft.core.RegistryAccess registryAccess() {
            return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        }
    }
}
