package top.aihmc.omm.preview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import top.aihmc.omnimorph.model.ysm.YsmAudioDecoder;

/** Uses the original decoder verification corpus; never starts Minecraft. */
public final class PreviewModelAudioTest {
    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of(args[0]);
        try (var audio = new PreviewAudio()) {
            for (String name : new String[] {
                     "vorbis-mono.ogg", "vorbis-exact.ogg", "opus-exact.ogg", "opus-input-rate.ogg", "opus-gain.ogg"}) {
                byte[] bytes = Files.readAllBytes(fixtures.resolve(name));
                var decoded = YsmAudioDecoder.decode(bytes, 16 * 1024 * 1024);
                var expected = name.startsWith("opus") ? YsmAudioDecoder.Codec.OPUS : YsmAudioDecoder.Codec.VORBIS;
                if (decoded.codec() != expected || decoded.format().getChannels() != 1
                    || decoded.format().getSampleSizeInBits() != 16 || !decoded.samples().hasRemaining()) {
                    throw new AssertionError("Invalid decoded format: " + name);
                }
                if (expected == YsmAudioDecoder.Codec.OPUS && decoded.format().getSampleRate() != 48000) {
                    throw new AssertionError("Opus must use its 48 kHz decoding clock");
                }
                var buffer = audio.modelBuffer(bytes);
                if (buffer != audio.modelBuffer(bytes) || buffer.channels() != 1
                    || buffer.frames() != decoded.samples().remaining() / 2) {
                    throw new AssertionError("Actual OpenAL upload/cache mismatch: " + name);
                }
                try {
                    YsmAudioDecoder.decode(bytes, 2);
                    throw new AssertionError("PCM budget was ignored: " + name);
                } catch (IOException expectedLimit) {
                    if (!expectedLimit.getMessage().startsWith("RESOURCE_LIMIT:"))
                        throw expectedLimit;
                }
                System.out.println(name + ": " + decoded.codec() + ", mono frames=" + buffer.frames());
            }
        }
        System.out.println(
            "Model audio PASS: original Opus/Vorbis, mono PCM, bounded decoding and shared OpenAL upload");
    }
}
