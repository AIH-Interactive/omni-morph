package top.aihmc.omnimorph.test;

import com.mojang.blaze3d.audio.SoundBuffer;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Inspects decoded PCM before OpenAL upload; these fixtures never open an audio device. */
public final class NativeSoundBufferState {
    private NativeSoundBufferState() {
    }

    public static int pcmBytes(SoundBuffer buffer) {
        try {
            Field data = SoundBuffer.class.getDeclaredField("data");
            data.setAccessible(true);
            ByteBuffer samples = (ByteBuffer) data.get(buffer);
            if (samples == null) {
                throw new IllegalStateException("Decoded PCM is unavailable after upload or on an uninitialized buffer");
            }
            return samples.remaining();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect native 1.21.1 PCM buffer", failure);
        }
    }
}
