package top.aihmc.omm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import javax.sound.sampled.AudioFormat;

/** Local-only probe against the selected original OMP JAR; no game or audio device starts. */
public final class AvatarCleanupProbe {
    private static final class TrackedInput extends ByteArrayInputStream {
        boolean closed;

        TrackedInput() {
            super(new byte[]{7});
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackedOutput extends ByteArrayOutputStream {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        FabricRuntimeInputs.configure(Path.of(args[0], "figura-config"), Path.of(System.getProperty("omm.preview.modJar")));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Class<?> avatarType = Class.forName("org.figuramc.figura.avatar.Avatar");
        Object avatar = avatarType.getMethod("createLibraryPreview", UUID.class, String.class)
            .invoke(null, UUID.randomUUID(), "resource-cleanup-probe");
        Object buffer;
        TrackedInput input = new TrackedInput();
        TrackedOutput output = new TrackedOutput();
        Map<String, Object> sounds = (Map<String, Object>) avatarType.getField("customSounds").get(avatar);
        try (var resources = new PreviewAvatarResources(avatar)) {
            Class<?> bufferType = Class.forName("org.figuramc.figura.lua.api.data.FiguraBuffer");
            buffer = bufferType.getConstructor(avatarType).newInstance(avatar);
            bufferType.getMethod("write", int.class).invoke(buffer, 23);
            Object originalInput = Class.forName("org.figuramc.figura.lua.api.data.FiguraInputStream")
                .getConstructor(avatarType, InputStream.class).newInstance(avatar, input);
            Object originalOutput = Class.forName("org.figuramc.figura.lua.api.data.FiguraOutputStream")
                .getConstructor(avatarType, OutputStream.class).newInstance(avatar, output);
            if ((int) originalInput.getClass().getMethod("read").invoke(originalInput) != 7) {
                throw new AssertionError("Original input stream did not read its data");
            }
            originalOutput.getClass().getMethod("write", int.class).invoke(originalOutput, 31);
            Object sound = Class.forName("com.mojang.blaze3d.audio.SoundBuffer")
                .getConstructor(ByteBuffer.class, AudioFormat.class)
                .newInstance(ByteBuffer.allocateDirect(8), new AudioFormat(8000, 16, 1, true, false));
            sounds.put("unplayed", sound);
            for (String field : new String[]{"openBuffers", "openInputStreams", "openOutputStreams"}) {
                if (((Collection<?>) avatarType.getField(field).get(avatar)).size() != 1) {
                    throw new AssertionError("Probe did not register one original resource in " + field);
                }
            }
        }
        if (!(boolean) buffer.getClass().getMethod("isClosed").invoke(buffer)
            || !input.closed || !output.closed || !sounds.isEmpty()) {
            throw new AssertionError("Original Avatar cleanup left a registered resource open");
        }
        System.out.println("NONEMPTY AVATAR CLEANUP PASS originalBuffer=closed input=closed output=closed unplayedSounds=0");
    }
}
