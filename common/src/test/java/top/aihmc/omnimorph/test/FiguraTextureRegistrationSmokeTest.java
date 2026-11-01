package top.aihmc.omnimorph.test;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.mixin.render.TextureManagerAccessor;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.lwjgl.opengl.GL11;
import sun.misc.Unsafe;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Exercises the native texture manager callback using a real visible GL context. */
public final class FiguraTextureRegistrationSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var renderThread = RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true);
        var previousThread = renderThread.get(null);
        if (previousThread == null) {
            RenderSystem.initRenderThread();
        } else {
            RenderSystem.assertOnRenderThread();
        }
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        var previous = instance.get(null);
        var client = (Client) unsafe.allocateInstance(Client.class);
        client.manager = new Textures();
        client.queue = new ConcurrentLinkedQueue<>();
        var avatar = (Avatar) unsafe.allocateInstance(Avatar.class);
        var owner = Avatar.class.getDeclaredField("owner");
        owner.setAccessible(true);
        owner.set(avatar, new UUID(0, 56));
        try (var window = NativeGlWindow.open()) {
            instance.set(null, client);
            var pixels = new NativeImage(1, 1, true);
            pixels.setPixelRGBA(0, 0, 0x44332211);
            var texture = new FiguraTexture(avatar, "registration", pixels);
            int id;
            try {
                texture.uploadIfDirty(false, false);
                id = texture.getId();
                require(client.manager.getTexture(texture.getLocation()) == texture,
                        "native register/load terminates and publishes original texture");
                require(GL11.glIsTexture(id), "published texture owns a real GL allocation");
                var actual = org.lwjgl.BufferUtils.createByteBuffer(4);
                texture.bind();
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, actual);
                require((actual.get(0) & 255) == 0x11 && (actual.get(1) & 255) == 0x22
                                && (actual.get(2) & 255) == 0x33 && (actual.get(3) & 255) == 0x44,
                        "published native pixels remain readable");
                texture.load(null);
                require(texture.getId() == id, "resource reload preserves the native texture identity");
                pixels.setPixelRGBA(0, 0, 0x88776655);
                reloadFromWorker(texture);
                RenderSystem.replayQueue();
                texture.bind();
                actual.clear();
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, actual);
                require((actual.get(0) & 255) == 0x55 && (actual.get(1) & 255) == 0x66
                                && (actual.get(2) & 255) == 0x77 && (actual.get(3) & 255) == 0x88,
                        "background reload uploads changed pixels on the render queue");
                var lease = texture.retain();
                texture.close();
                require(GL11.glIsTexture(id) && !NativeImageState.isClosed(pixels),
                        "retirement keeps leased resources alive");
                reloadFromWorker(texture);
                lease.close();
                RenderSystem.replayQueue();
                require(!GL11.glIsTexture(id) && NativeImageState.isClosed(pixels),
                        "last lease releases resources before a queued reload can reuse them");
            } finally {
                texture.close();
                Runnable task;
                while ((task = client.queue.poll()) != null) {
                    task.run();
                }
            }
            require(!client.manager.getByPath().containsKey(texture.getLocation()), "queued removal completes");
            Runnable drain = () -> {
                Runnable task;
                while ((task = client.queue.poll()) != null) {
                    task.run();
                }
            };
            testPublication(avatar, client.manager, drain);
            testLifetime(avatar, client.manager, drain);
            testLateBindings(avatar, client.manager, drain);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL error");
        } finally {
            instance.set(null, previous);
            renderThread.set(null, previousThread);
        }
        System.out.println("PASS actual FiguraTexture native register/load, GL pixel readback, "
                + "reload identity, failure/retry, delayed fallback, 64 replacements, 64 dirty updates, "
                + "lease retirement and queued removal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void testPublication(Avatar owner, TextureManager manager, Runnable drain) throws Exception {
        var pixels = new NativeImage(1, 1, true);
        pixels.setPixelRGBA(0, 0, 0x44332211);
        var texture = new FiguraTexture(owner, "publication", pixels);
        try {
            texture.uploadIfDirty(false, false);
            int id = texture.getId();
            require(manager.getTexture(texture.getLocation()) == texture && GL11.glIsTexture(id),
                    "registration publishes an initialized native binding");
            withUnavailablePixels(pixels, () -> texture.uploadIfDirty(false, false));
            require(texture.getId() == id, "clean textures reuse the uploaded image without reading source pixels");
            texture.releaseId();
            texture.update();
            texture.uploadIfDirty(false, false);
            require(GL11.glIsTexture(texture.getId()), "an explicitly released native binding can be rebuilt");
            requirePixel(texture, 0x44332211);
            texture.close();
            texture.uploadIfDirty(false, false);
            drain.run();
            require(!textures(manager).containsKey(texture.getLocation()), "late uploads cannot republish a closed texture");
        } finally {
            texture.close();
            drain.run();
        }

        var retryPixels = new NativeImage(1, 1, true);
        retryPixels.setPixelRGBA(0, 0, 0x88776655);
        var retry = new FiguraTexture(owner, "retry", retryPixels);
        int[] failedId = {0};
        try {
            withUnavailablePixels(retryPixels, () -> {
                try {
                    retry.uploadIfDirty(false, false);
                    throw new AssertionError("unavailable native source must fail upload");
                } catch (IllegalStateException expected) {
                    failedId[0] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                    require(failedId[0] != 0 && GL11.glIsTexture(failedId[0]),
                            "native upload failed after allocating its unpublished destination");
                    require(!textures(manager).containsKey(retry.getLocation()),
                            "failed native initialization does not publish a partial texture");
                }
            });
            retry.uploadIfDirty(false, false);
            require(manager.getTexture(retry.getLocation()) == retry, "failed uploads remain retryable");
            requirePixel(retry, 0x88776655);
        } finally {
            retry.close();
            drain.run();
        }
        require(NativeImageState.isClosed(retryPixels) && !GL11.glIsTexture(failedId[0]),
                "closing a retried texture releases the allocation retained after upload failure");
    }

    public static void testLateBindings(Avatar owner, TextureManager manager, Runnable drain) throws Exception {
        var fallback = new FiguraTexture(owner, "fallback", new NativeImage(1, 1, true));
        var pixels = new NativeImage(1, 1, true);
        var texture = new FiguraTexture(owner, "late-binding", pixels);
        var missing = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        try {
            fallback.uploadIfDirty(false, false);
            textures(manager).put(missing, fallback);
            texture.uploadIfDirty(false, false);
            java.util.function.IntSupplier delayedBinding = () -> manager.getTexture(texture.getLocation()).getId();
            pixels.setPixelRGBA(0, 0, 0x44332211);
            texture.update();
            require(GL11.glIsTexture(delayedBinding.getAsInt()), "delayed native lookup uploads pending source pixels");
            requirePixel(texture, 0x44332211);
            texture.load(null);
            require(!NativeImageState.isClosed(pixels), "native reload retains its owned source image");
            texture.close();
            require(delayedBinding.getAsInt() == fallback.getId(), "retired bindings resolve before queued removal");
            manager.register(texture.getLocation(), texture);
            require(delayedBinding.getAsInt() == fallback.getId(), "late native registration resolves to the live fallback");
            texture.bind();
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == fallback.getId(),
                    "retired getId and bind use the same fallback without reading released pixels");
            drain.run();
            require(!textures(manager).containsKey(texture.getLocation()), "late registration is removed on the queue");

            for (int iteration = 0; iteration < 64; iteration++) {
                var old = new FiguraTexture(owner, "old", new NativeImage(1, 1, true));
                var replacement = new FiguraTexture(owner, "replacement", new NativeImage(1, 1, true));
                try {
                    old.uploadIfDirty(false, false);
                    replacement.uploadIfDirty(false, false);
                    manager.register(old.getLocation(), replacement);
                    drain.run();
                    require(manager.getTexture(old.getLocation()) == replacement,
                            "old cleanup cannot remove a replacement at the same location");
                } finally {
                    old.close();
                    replacement.close();
                    textures(manager).remove(old.getLocation(), replacement);
                    drain.run();
                }
            }
            manager.close();
            drain.run();
            require(textures(manager).isEmpty(), "manager shutdown permits deferred map cleanup");
        } finally {
            texture.close();
            textures(manager).remove(texture.getLocation());
            textures(manager).remove(missing, fallback);
            fallback.close();
            drain.run();
        }
    }

    public static void testLifetime(Avatar owner, TextureManager manager, Runnable drain) throws Exception {
        var pixels = new NativeImage(1, 1, true);
        var texture = new FiguraTexture(owner, "lifetime", pixels);
        int id = 0;
        try {
            texture.uploadIfDirty(true, true);
            id = texture.getId();
            require(manager.getTexture(texture.getLocation()) == texture, "lifetime texture is registered");
            for (int iteration = 0; iteration < 64; iteration++) {
                pixels.setPixelRGBA(0, 0, 0xff000000 | iteration);
                texture.update();
                require(texture.getId() == id && GL11.glIsTexture(id), "dirty updates preserve the native allocation");
                requirePixel(texture, 0xff000000 | iteration);
                require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S)
                                == org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
                                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T)
                                == org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
                                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) == GL11.GL_LINEAR,
                        "dirty updates preserve clamp-to-edge and linear filtering");
            }
            try (var particleLease = texture.retain(); var secondLease = particleLease.retain()) {
                texture.close();
                require(texture.getId() == id && GL11.glIsTexture(id), "retirement preserves existing particle bindings");
                try {
                    texture.retain();
                    throw new AssertionError("new particles cannot retain retired textures");
                } catch (IllegalStateException expected) {
                    require("TEXTURE_RETIRED".equals(expected.getMessage()), "explicit retired texture rejection");
                }
                try (var frameLease = secondLease.retain()) {
                    particleLease.close();
                    particleLease.close();
                    secondLease.close();
                    require(GL11.glIsTexture(id) && !NativeImageState.isClosed(pixels),
                            "extracted frames outlive both particles and repeated lease release");
                }
                require(!GL11.glIsTexture(id) && NativeImageState.isClosed(pixels),
                        "final extracted frame release closes native and GL resources");
            }
        } finally {
            texture.close();
            drain.run();
        }
        require(!GL11.glIsTexture(id) && NativeImageState.isClosed(pixels), "lifetime fixture releases all owned resources");
    }

    private static Map<ResourceLocation, AbstractTexture> textures(TextureManager manager) {
        return ((TextureManagerAccessor) manager).getByPath();
    }

    private static void requirePixel(FiguraTexture texture, int expectedAbgr) {
        texture.bind();
        var actual = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, actual);
        for (int channel = 0; channel < 4; channel++) {
            require((actual.get(channel) & 255) == ((expectedAbgr >>> (channel * 8)) & 255), "native pixel readback");
        }
    }

    /** Exercise NativeImage's real allocation check; restore ownership before any close or retry. */
    private static void withUnavailablePixels(NativeImage image, Runnable action) throws Exception {
        var pixels = NativeImage.class.getDeclaredField("pixels");
        pixels.setAccessible(true);
        long pointer = pixels.getLong(image);
        require(pointer != 0L, "failure fixture owns allocated source pixels");
        try {
            pixels.setLong(image, 0L);
            action.run();
        } finally {
            pixels.setLong(image, pointer);
        }
    }

    private static void reloadFromWorker(FiguraTexture texture) throws Exception {
        var reload = new java.util.concurrent.FutureTask<Void>(() -> {
            texture.load(null);
            return null;
        });
        new Thread(reload, "native-texture-reload").start();
        reload.get();
    }

    private static final class Client extends Minecraft {
        private Textures manager;
        private ConcurrentLinkedQueue<Runnable> queue;

        private Client() {
            super(null);
        }

        @Override
        public TextureManager getTextureManager() {
            return manager;
        }

        @Override
        public void tell(Runnable task) {
            queue.add(task);
        }
    }

    private static final class Textures extends TextureManager implements TextureManagerAccessor {
        private final Map<ResourceLocation, AbstractTexture> textures;

        @SuppressWarnings("unchecked")
        private Textures() throws Exception {
            super(null);
            var byPath = TextureManager.class.getDeclaredField("byPath");
            byPath.setAccessible(true);
            textures = (Map<ResourceLocation, AbstractTexture>) byPath.get(this);
        }

        @Override
        public Map<ResourceLocation, AbstractTexture> getByPath() {
            return textures;
        }
    }
}
