package top.aihmc.omm.preview;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_LINEAR_DISTANCE_CLAMPED;
import static org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_decode_memory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

/** One lazily opened real OpenAL device; decoded immutable buffers are shared across avatar instances. */
public final class PreviewAudio implements AutoCloseable {
    record Buffer(int handle, int channels, int sampleRate, int frames) {}
    private final Map<String, Buffer> buffers = new LinkedHashMap<>();
    private final Set<Voice> voices = new LinkedHashSet<>();
    private long device;
    private long context;
    private ALCapabilities capabilities;
    private boolean closed;
    private final float[] listener = new float[3];
    private final float[] orientation = {0, 0, -1, 0, 1, 0};
    private long starts;
    private int allocations;
    private int decodes;
    private int peakVoices;
    private String deviceName = "not opened; no audio requested";

    private void current() {
        if (closed)
            throw new IllegalStateException("Audio device already closed");
        if (device == 0) {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0)
                throw new IllegalStateException("Could not open the default OpenAL output device");
            try {
                var deviceCapabilities = ALC.createCapabilities(device);
                context = alcCreateContext(device, (IntBuffer) null);
                if (context == 0 || !alcMakeContextCurrent(context)) {
                    throw new IllegalStateException("Could not create the OpenAL playback context");
                }
                capabilities = AL.createCapabilities(deviceCapabilities);
                alDistanceModel(AL_LINEAR_DISTANCE_CLAMPED);
                deviceName = alcGetString(device, ALC_DEVICE_SPECIFIER);
                applyListener();
            } catch (RuntimeException failure) {
                close();
                throw failure;
            }
        } else {
            if (!alcMakeContextCurrent(context))
                throw new IllegalStateException("Could not activate the audio context");
            AL.setCurrentThread(capabilities);
        }
    }

    Buffer buffer(byte[] encoded) {
        String hash = PreviewTextures.sha256(encoded);
        var cached = buffers.get(hash);
        if (cached != null)
            return cached;
        current();
        ByteBuffer input = MemoryUtil.memAlloc(encoded.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(encoded).flip();
            IntBuffer channels = stack.mallocInt(1), rate = stack.mallocInt(1);
            ShortBuffer samples = stb_vorbis_decode_memory(input, channels, rate);
            if (samples == null)
                throw new IllegalArgumentException("Invalid Ogg Vorbis sound data");
            try {
                int channelCount = channels.get(0);
                if (channelCount != 1 && channelCount != 2) {
                    throw new IllegalArgumentException(
                        "Sound must be mono or stereo, found " + channelCount + " channels");
                }
                int handle = alGenBuffers();
                try {
                    alBufferData(
                        handle, channelCount == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, samples, rate.get(0));
                    check("upload decoded sound");
                    var buffer = new Buffer(handle, channelCount, rate.get(0), samples.remaining() / channelCount);
                    buffers.put(hash, buffer);
                    decodes++;
                    return buffer;
                } catch (RuntimeException failure) {
                    alDeleteBuffers(handle);
                    throw failure;
                }
            } finally {
                LibCStdlib.free(samples);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    Buffer modelBuffer(byte[] encoded) {
        String hash = "ysm:" + PreviewTextures.sha256(encoded);
        var cached = buffers.get(hash);
        if (cached != null)
            return cached;
        current();
        try {
            var decoded = top.aihmc.omnimorph.model.ysm.YsmAudioDecoder.decode(encoded,
                Math.toIntExact(top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits.DEFAULT.singleResourceBytes()));
            int handle = alGenBuffers();
            try {
                int rate = Math.round(decoded.format().getSampleRate());
                alBufferData(handle, AL_FORMAT_MONO16, decoded.samples(), rate);
                check("upload decoded model sound");
                var buffer = new Buffer(handle, 1, rate, decoded.samples().remaining() / 2);
                buffers.put(hash, buffer);
                decodes++;
                return buffer;
            } catch (RuntimeException failure) {
                alDeleteBuffers(handle);
                throw failure;
            }
        } catch (java.io.IOException invalid) {
            throw new java.io.UncheckedIOException("Unable to decode YSM model audio", invalid);
        }
    }

    Voice voice(byte[] encoded, boolean modelAudio) {
        Buffer buffer = modelAudio ? modelBuffer(encoded) : buffer(encoded);
        current();
        int source = alGenSources();
        check("allocate playback source");
        alSourcei(source, AL_BUFFER, buffer.handle);
        var voice = new Voice(source, buffer);
        voices.add(voice);
        allocations++;
        peakVoices = Math.max(peakVoices, voices.size());
        return voice;
    }

    Voice voice(byte[] encoded) {
        return voice(encoded, false);
    }

    void listener(float x, float y, float z, float fx, float fy, float fz) {
        listener[0] = x;
        listener[1] = y;
        listener[2] = z;
        orientation[0] = fx;
        orientation[1] = fy;
        orientation[2] = fz;
        if (device != 0) {
            current();
            applyListener();
        }
    }

    private void applyListener() {
        alListener3f(AL_POSITION, listener[0], listener[1], listener[2]);
        alListenerfv(AL_ORIENTATION, orientation);
    }

    void update() {
        if (device == 0)
            return;
        current();
        for (var voice : Set.copyOf(voices)) {
            if (alGetSourcei(voice.handle, AL_SOURCE_STATE) == AL_STOPPED)
                voice.close();
        }
    }

    Map<String, Object> snapshot() {
        var result = new LinkedHashMap<String, Object>();
        result.put("device", deviceName);
        result.put("decodedBuffers", buffers.size());
        result.put("decodedBufferLoads", decodes);
        result.put("playCalls", starts);
        result.put("allocatedSources", allocations);
        result.put("activeSources", voices.size());
        result.put("peakSources", peakVoices);
        result.put("closed", closed);
        return result;
    }

    private static void check(String operation) {
        int error = alGetError();
        if (error != AL_NO_ERROR)
            throw new IllegalStateException("OpenAL failed to " + operation + ": " + error);
    }

    public final class Voice implements AutoCloseable {
        private int handle;
        final Buffer buffer;

        private Voice(int handle, Buffer buffer) {
            this.handle = handle;
            this.buffer = buffer;
        }

        public boolean stopped() {
            if (handle == 0)
                return true;
            current();
            return alGetSourcei(handle, AL_SOURCE_STATE) == AL_STOPPED;
        }

        public boolean playing() {
            if (handle == 0)
                return false;
            current();
            return alGetSourcei(handle, AL_SOURCE_STATE) == AL_PLAYING;
        }

        public void play() {
            current();
            alSourcePlay(handle);
            check("play sound");
            starts++;
        }

        public void pause() {
            if (handle == 0)
                return;
            current();
            alSourcePause(handle);
            check("pause sound");
        }

        public void position(double x, double y, double z) {
            if (handle == 0)
                return;
            current();
            alSource3f(handle, AL_POSITION, (float) x, (float) y, (float) z);
            check("set sound position");
        }

        public void volume(float value) {
            if (handle == 0)
                return;
            current();
            alSourcef(handle, AL_GAIN, Math.max(0, value));
            check("set sound volume");
        }

        public void pitch(float value) {
            if (handle == 0)
                return;
            current();
            alSourcef(handle, AL_PITCH, value);
            check("set sound pitch");
        }

        public void loop(boolean value) {
            if (handle == 0)
                return;
            current();
            alSourcei(handle, AL_LOOPING, value ? AL_TRUE : AL_FALSE);
            check("set sound loop");
        }

        public void attenuation(float value) {
            if (handle == 0)
                return;
            current();
            alSourcef(handle, AL_REFERENCE_DISTANCE, 0);
            alSourcef(handle, AL_MAX_DISTANCE, value * 16);
            alSourcef(handle, AL_ROLLOFF_FACTOR, 1);
            check("set sound attenuation");
        }

        int sampleOffset() {
            if (handle == 0)
                return 0;
            current();
            return alGetSourcei(handle, AL_SAMPLE_OFFSET);
        }

        @Override
        public void close() {
            if (handle == 0)
                return;
            current();
            alSourceStop(handle);
            alDeleteSources(handle);
            handle = 0;
            voices.remove(this);
        }
    }

    @Override
    public void close() {
        if (closed)
            return;
        if (context != 0) {
            alcMakeContextCurrent(context);
            AL.setCurrentThread(capabilities);
            for (var voice : Set.copyOf(voices)) voice.close();
            buffers.values().forEach(buffer -> alDeleteBuffers(buffer.handle));
            buffers.clear();
            alcMakeContextCurrent(0);
            AL.setCurrentThread(null);
            alcDestroyContext(context);
            context = 0;
        }
        if (device != 0) {
            alcCloseDevice(device);
            device = 0;
        }
        closed = true;
    }
}
