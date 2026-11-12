package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.mixin.render.CameraMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sun.misc.Unsafe;

/** Runs camera/input consumers while the visible draft differs from the resident avatar. */
final class VisibleAvatarRoutingSmokeTest {
    static void run(Unsafe unsafe, LibraryPreviewSession preview, Runnable drain, Object editor) throws Exception {
        var client = Minecraft.getInstance();
        var formal = AvatarManager.getAvatarForPlayer(client.player.getUUID());
        var draft = preview.outfitBase();
        require(formal != draft, "fixture has different visible and resident Avatars");
        draft.loadScript("visible_camera", "renderer:setFOV(0.5)").call();
        var camera = (TestCamera) unsafe.allocateInstance(TestCamera.class);
        var entity = CameraMixin.class.getDeclaredField("entity");
        entity.setAccessible(true);
        entity.set(camera, client.player);
        var fov = CameraMixin.class.getDeclaredMethod("figura$applyFov", float.class, CallbackInfoReturnable.class);
        fov.setAccessible(true);
        var result = new CallbackInfoReturnable<Float>("calculateFov", true, 80f);
        fov.invoke(camera, 1f, result);
        require(result.getReturnValueF() == 40f, "camera consumes visible draft FOV: expected 40, got " + result.getReturnValueF());
        require(AvatarManager.getAvatarForPlayer(client.player.getUUID()) == formal,
                "camera routing does not publish the draft as formal equipment");
        cameraTransforms(camera, draft);
        draft.loadScript("clear_visible_camera", "renderer:setFOV(nil)").call();
        draft.loadScript("visible_input", """
                routing_chars = 0
                events.char_typed:register(function() routing_chars = routing_chars + 1 end, 'routingTest')
                """).call();
        var keyboard = new org.figuramc.figura.mixin.input.KeyboardHandlerMixin();
        var chars = keyboard.getClass().getDeclaredMethod("charTyped", long.class,
                net.minecraft.client.input.CharacterEvent.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        chars.setAccessible(true);
        chars.invoke(keyboard, 0L, new net.minecraft.client.input.CharacterEvent('测'),
                new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("charTyped", false));
        draft.loadScript("assert_visible_input", "assert(routing_chars == 1, 'visible draft must receive typed character')").call();
        require(!draft.scriptError, "character event reaches the visible draft runtime");
        draft.loadScript("clear_visible_input", "events.char_typed:remove('routingTest')").call();
        mouseAndKeybinds(draft);
        var fire = org.figuramc.figura.mixin.render.renderers.ScreenEffectRendererMixin.class.getDeclaredMethod(
                "renderFire", com.mojang.blaze3d.vertex.PoseStack.class,
                net.minecraft.client.renderer.MultiBufferSource.class,
                net.minecraft.client.renderer.texture.TextureAtlasSprite.class,
                org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        fire.setAccessible(true);
        boolean oldFire = draft.luaRuntime.renderer.renderFire;
        draft.luaRuntime.renderer.renderFire = false;
        try (var scope = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingCameraEntity(client.player)) {
            var callback = new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("renderFire", true);
            fire.invoke(null, new com.mojang.blaze3d.vertex.PoseStack(), null, null, callback);
            require(callback.isCancelled(), "visible draft can hide the first-person fire overlay");
        } finally {
            draft.luaRuntime.renderer.renderFire = oldFire;
        }
        try (var vanillaGui = new LibraryPreviewBinding(client.player, null, false)) {
            vanillaGui.withPlayerContext(() -> require(
                    top.aihmc.omnimorph.model.outfit.VisibleAvatar.localPlayer() == null,
                    "explicit vanilla GUI scope takes precedence over the world draft"));
        }
        try (var vanilla = new LibraryPreviewSession()) {
            require(vanilla.selectVanilla(), "vanilla draft selection succeeds");
            var config = new top.aihmc.omnimorph.model.outfit.OutfitConfig(1, 0, null,
                    java.util.List.of(), java.util.List.of(), java.util.Map.of(), false);
            var prepared = vanilla.prepareComposition(config,
                    top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults(), client.player.getUUID(),
                    java.util.UUID.randomUUID(), draft.permissions, (resource, cancelled) -> {
                        throw new AssertionError("vanilla draft has no dependencies");
                    });
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!prepared.isDone() && System.nanoTime() < deadline) {
                drain.run();
                Thread.sleep(5);
            }
            require(prepared.isDone() && prepared.join(), "vanilla composition prepares");
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.show(editor, vanilla);
            require(top.aihmc.omnimorph.model.outfit.VisibleAvatar.localPlayer() == null,
                    "vanilla world draft does not fall through to the formal Avatar");
            require(AvatarManager.getAvatarForPlayer(client.player.getUUID()) == formal,
                    "vanilla world draft does not alter formal identity");
        } finally {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.show(editor, preview);
        }
        boolean panic = AvatarManager.panic;
        try {
            AvatarManager.panic = true;
            require(top.aihmc.omnimorph.model.outfit.VisibleAvatar.localPlayer() == null,
                    "panic still suppresses visible scripts");
        } finally {
            AvatarManager.panic = panic;
        }
        System.out.println("PASS visible draft: camera, input, fire, vanilla scopes, panic and unchanged formal lookup");
    }

    private static void cameraTransforms(TestCamera camera, org.figuramc.figura.avatar.Avatar draft) throws Exception {
        var renderer = draft.luaRuntime.renderer;
        var oldRotation = renderer.cameraRot;
        var oldPivot = renderer.cameraPivot;
        var oldPosition = renderer.cameraPos;
        try {
            renderer.cameraRot = org.figuramc.figura.math.vector.FiguraVec3.of(11, 22, 33);
            renderer.cameraPivot = org.figuramc.figura.math.vector.FiguraVec3.of(1, 2, 3);
            renderer.cameraPos = org.figuramc.figura.math.vector.FiguraVec3.of(3, 4, 5);
            invoke(CameraMixin.class, camera, "setupAvatarVar", new Class<?>[]{float.class,
                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class}, 1f,
                    new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("alignWithEntity", false));
            invoke(CameraMixin.class, camera, "setupRot", new Class<?>[]{float.class,
                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class}, 1f,
                    new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("alignWithEntity", false));
            require(camera.yaw == 22 && camera.pitch == 11, "draft camera rotation reaches the actual consumer");
            require((double) invoke(CameraMixin.class, camera, "setupPivotX", new Class<?>[]{double.class}, 10d) == 1,
                    "draft camera pivot replaces the vanilla input");
            invoke(CameraMixin.class, camera, "setupPos", new Class<?>[]{float.class,
                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class}, 1f,
                    new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("alignWithEntity", false));
            require(camera.x == -5 && camera.y == 4 && camera.z == -3, "draft camera position preserves axis mapping");
        } finally {
            renderer.cameraRot = oldRotation;
            renderer.cameraPivot = oldPivot;
            renderer.cameraPos = oldPosition;
        }
    }

    private static void mouseAndKeybinds(org.figuramc.figura.avatar.Avatar draft) throws Exception {
        var type = org.figuramc.figura.mixin.input.MouseHandlerMixin.class;
        var mouse = new org.figuramc.figura.mixin.input.MouseHandlerMixin();
        var clientField = type.getDeclaredField("minecraft");
        clientField.setAccessible(true);
        clientField.set(mouse, Minecraft.getInstance());
        var grabbed = type.getDeclaredField("mouseGrabbed");
        grabbed.setAccessible(true);
        grabbed.set(mouse, true);
        draft.loadScript("visible_mouse", """
                routing_scroll = 0
                events.mouse_scroll:register(function() routing_scroll = routing_scroll + 1; return true end, 'routingTest')
                """).call();
        try {
            var callback = new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("onScroll", true);
            invoke(type, mouse, "onScroll", new Class<?>[]{long.class, double.class, double.class,
                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class}, 0L, 0d, 1d, callback);
            require(callback.isCancelled(), "draft mouse event preserves cancellation");
            draft.loadScript("assert_visible_mouse", "assert(routing_scroll == 1)").call();
        } finally {
            draft.loadScript("clear_visible_mouse", "events.mouse_scroll:remove('routingTest')").call();
        }
        var key = com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(4);
        var binding = new org.figuramc.figura.lua.api.keybind.FiguraKeybind(draft, "routing test", key);
        binding.press = draft.loadScript("visible_keybind", "return function() return true end").call().checkfunction();
        draft.luaRuntime.keybinds.keyBindings.add(binding);
        try {
            binding.setDown(true, 0);
            var mapping = new org.figuramc.figura.mixin.input.KeyMappingMixin();
            var keyField = mapping.getClass().getDeclaredField("key");
            keyField.setAccessible(true);
            keyField.set(mapping, key);
            require(!(boolean) invoke(mapping.getClass(), mapping, "setDown", new Class<?>[]{boolean.class}, true),
                    "draft Lua keybind overrides the native binding");
            invoke(mapping.getClass(), null, "releaseAll", new Class<?>[]{
                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class},
                    new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("releaseAll", false));
            require(!org.figuramc.figura.lua.api.keybind.FiguraKeybind.overridesKey(
                    draft.luaRuntime.keybinds.keyBindings, key), "releaseAll clears the draft override");
        } finally {
            draft.luaRuntime.keybinds.keyBindings.remove(binding);
        }
    }

    private static Object invoke(Class<?> type, Object instance, String name, Class<?>[] parameters,
                                 Object... arguments) throws Exception {
        var method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(instance, arguments);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static final class TestCamera extends CameraMixin {
        private float yaw, pitch, x, y, z;

        @Override
        protected void setRotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        protected void move(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
