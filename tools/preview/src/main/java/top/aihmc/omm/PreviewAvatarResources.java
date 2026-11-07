package top.aihmc.omm;

import java.util.Collection;
import java.util.Map;

/** Owns a detached original Avatar and supplies its absent sound-engine disposal boundary. */
public final class PreviewAvatarResources implements AutoCloseable {
    private final Object avatar;

    PreviewAvatarResources(Object avatar) {
        this.avatar = avatar;
    }

    static void closeYsmRuntime(Object runtime) throws Exception {
        Object owner = runtime.getClass().getMethod("owner").invoke(runtime);
        new PreviewAvatarResources(owner).close();
    }

    public static void clearSounds(Object avatar) throws Exception {
        if (!avatar.getClass().getField("isLibraryPreview").getBoolean(avatar)) {
            throw new IllegalArgumentException("Sound disposal adapter requires a detached library preview");
        }
        // The original preview permission pack disables playback. Dispose the original buffers
        // directly instead of dispatching through Minecraft's unavailable SoundEngine.
        Map<?, ?> sounds = (Map<?, ?>) avatar.getClass().getField("customSounds").get(avatar);
        for (Object sound : sounds.values()) {
            sound.getClass().getMethod("discardAlBuffer").invoke(sound);
        }
        sounds.clear();
    }

    @Override
    public void close() throws Exception {
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        }
        avatar.getClass().getMethod("clean").invoke(avatar);
        for (String name : new String[]{"openBuffers", "openInputStreams", "openOutputStreams"}) {
            if (!((Collection<?>) avatar.getClass().getField(name).get(avatar)).isEmpty()) {
                throw new IllegalStateException("Original Avatar cleanup retained " + name);
            }
        }
        System.out.println("FIGURA ORIGINAL AVATAR CLEAN PASS buffers=0 inputStreams=0 outputStreams=0");
    }
}
