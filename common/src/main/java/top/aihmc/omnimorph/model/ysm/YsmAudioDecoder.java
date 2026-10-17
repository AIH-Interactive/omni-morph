package top.aihmc.omnimorph.model.ysm;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import net.minecraft.client.sounds.JOrbisAudioStream;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Bounded mono PCM decoding for positional model audio; no sound device is needed. */
public final class YsmAudioDecoder {
    public enum Codec {
        VORBIS, OPUS
    }

    public record Decoded(ByteBuffer samples, AudioFormat format, Codec codec) {
    }

    private YsmAudioDecoder() {
    }

    public static Decoded decode(byte[] data, int maximumPcmBytes) throws IOException {
        if (data == null || data.length == 0 || data.length > YsmResourceLimits.DEFAULT.singleResourceBytes()
                || maximumPcmBytes <= 0 || maximumPcmBytes > YsmResourceLimits.DEFAULT.singleResourceBytes()) {
            throw new IOException("RESOURCE_LIMIT: invalid model audio size");
        }
        try {
            Codec codec = codec(data);
            return codec == Codec.OPUS ? opus(data, maximumPcmBytes) : vorbis(data, maximumPcmBytes);
        } catch (OpusException | IllegalArgumentException error) {
            throw new IOException("INVALID_DATA: invalid model audio", error);
        }
    }

    private static Codec codec(byte[] data) throws IOException {
        try (var file = new OggFile(new ByteArrayInputStream(data))) {
            var first = file.getPacketReader().getNextPacket();
            if (first == null) {
                throw new IOException("INVALID_DATA: empty Ogg audio");
            }
            byte[] header = first.getData();
            if (startsWith(header, "OpusHead")) {
                return Codec.OPUS;
            }
            if (header.length > 7 && header[0] == 1
                    && startsWith(java.util.Arrays.copyOfRange(header, 1, header.length), "vorbis")) {
                return Codec.VORBIS;
            }
            throw new IOException("UNSUPPORTED_FORMAT: model audio codec");
        }
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (bytes[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static Decoded vorbis(byte[] data, int maximum) throws IOException {
        try (var source = new JOrbisAudioStream(new ByteArrayInputStream(data))) {
            AudioFormat format = source.getFormat();
            int channels = format.getChannels();
            if (channels < 1 || channels > 2 || format.getSampleSizeInBits() != 16) {
                throw new IOException("UNSUPPORTED_FORMAT: model audio channels");
            }
            var output = new ByteArrayOutputStream();
            ByteBuffer chunk;
            while ((chunk = source.read(8192)).hasRemaining()) {
                YsmResourceLimits.checkCancelled(() -> false);
                chunk.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                while (chunk.remaining() >= channels * 2) {
                    int sample = chunk.getShort();
                    if (channels == 2) {
                        sample = (sample + chunk.getShort()) / 2;
                    }
                    writeSample(output, sample, maximum);
                }
            }
            return decoded(output.toByteArray(), format.getSampleRate(), Codec.VORBIS);
        }
    }

    private static Decoded opus(byte[] data, int maximum) throws IOException, OpusException {
        try (var file = new OggFile(new ByteArrayInputStream(data)); var source = new OpusFile(file)) {
            var info = source.getInfo();
            int channels = info.getNumChannels();
            if (channels < 1 || channels > 2 || info.getChannelMappingFamily() != 0) {
                throw new IOException("UNSUPPORTED_FORMAT: model Opus channel mapping");
            }
            var decoder = new OpusDecoder(48000, channels);
            decoder.setGain((short) info.getOutputGain());
            int remainingSkip = info.getPreSkip();
            long lastGranule = -1;
            short[] samples = new short[5760 * channels];
            var output = new ByteArrayOutputStream();
            org.gagravarr.opus.OpusAudioData packet;
            while ((packet = source.getNextAudioPacket()) != null) {
                YsmResourceLimits.checkCancelled(() -> false);
                byte[] bytes = packet.getData();
                int frames = decoder.decode(bytes, 0, bytes.length, samples, 0, 5760, false);
                int skip = Math.min(remainingSkip, frames);
                remainingSkip -= skip;
                for (int frame = skip; frame < frames; frame++) {
                    int sample = samples[frame * channels];
                    if (channels == 2) {
                        sample = (sample + samples[frame * channels + 1]) / 2;
                    }
                    writeSample(output, sample, maximum);
                }
                if (packet.getGranulePosition() >= 0) {
                    lastGranule = packet.getGranulePosition();
                }
            }
            byte[] pcm = output.toByteArray();
            if (lastGranule >= 0) {
                long expectedFrames = Math.max(0, lastGranule - info.getPreSkip());
                if (expectedFrames > pcm.length / 2) {
                    throw new IOException("INVALID_DATA: truncated model Opus audio");
                }
                pcm = java.util.Arrays.copyOf(pcm, (int) expectedFrames * 2);
            }
            return decoded(pcm, 48000, Codec.OPUS);
        }
    }

    private static void writeSample(ByteArrayOutputStream output, int sample, int maximum) throws IOException {
        if (output.size() > maximum - 2) {
            throw new IOException("RESOURCE_LIMIT: decoded model audio exceeds PCM budget");
        }
        output.write(sample & 255);
        output.write((sample >>> 8) & 255);
    }

    private static Decoded decoded(byte[] pcm, float sampleRate, Codec codec) throws IOException {
        if (pcm.length == 0 || !Float.isFinite(sampleRate) || sampleRate <= 0) {
            throw new IOException("INVALID_DATA: model audio has no samples");
        }
        ByteBuffer samples = ByteBuffer.allocateDirect(pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        samples.put(pcm).flip();
        return new Decoded(samples, new AudioFormat(sampleRate, 16, 1, true, false), codec);
    }
}
