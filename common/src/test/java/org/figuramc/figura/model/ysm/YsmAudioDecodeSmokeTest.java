package org.figuramc.figura.model.ysm;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import org.figuramc.figura.avatar.ysm.YsmAvatarDetector;
import org.figuramc.figura.avatar.ysm.YsmModelData;
import org.figuramc.figura.avatar.ysm.YsmResourceLimits;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;

/** Actual model audio bytes are decoded to PCM in memory; never opens an audio device or writes PCM. */
public final class YsmAudioDecodeSmokeTest {
    public static void main(String[] arguments) throws Exception {
        Path directory = Files.createTempDirectory("figura-audio-decode-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        try {
            for (int channels : new int[]{1, 2}) {
                byte[] encoded = opus(channels);
                var decoded = YsmAudioDecoder.decode(encoded, 32768);
                require(decoded.codec() == YsmAudioDecoder.Codec.OPUS, "actual Opus codec");
                require(decoded.samples().remaining() == 5360, "pre-skip120 and end trim80 leave2680 mono frames");
                require(decoded.samples().isDirect() && decoded.format().getChannels() == 1
                                && decoded.format().getSampleRate() == 48000, "native mono PCM format");
                expectFailure(() -> YsmAudioDecoder.decode(encoded, 16), "bounded PCM expansion");
                expectFailure(() -> YsmAudioDecoder.decode(Arrays.copyOf(encoded, 24), 32768), "truncated Ogg header");
            }
            expectFailure(() -> YsmAudioDecoder.decode(new byte[]{1, 2, 3}, 32768), "invalid audio input");
            if (arguments.length != 1) {
                throw new IllegalArgumentException("Provide the model corpus directory");
            }
            var counts = new EnumMap<YsmAudioDecoder.Codec, Integer>(YsmAudioDecoder.Codec.class);
            int models = 0;
            int encrypted = 0;
            long totalPcm = 0;
            try (var files = Files.list(Path.of(arguments[0]))) {
                for (Path model : files.sorted().toList()) {
                    if (!YsmAvatarDetector.isYsmAvatar(model)) {
                        continue;
                    }
                    try (YsmModelData data = YsmModelData.read(model, () -> false)) {
                        models++;
                        if (data.encrypted()) {
                            encrypted++;
                        }
                        for (String sound : data.manifest().resourceIndex().soundPaths()) {
                            var decoded = YsmAudioDecoder.decode(data.resources().readBytes(sound),
                                    (int) YsmResourceLimits.DEFAULT.singleResourceBytes());
                            require(decoded.samples().remaining() > 0 && decoded.samples().remaining() % 2 == 0,
                                    "real model audio produces complete PCM samples");
                            counts.merge(decoded.codec(), 1, Integer::sum);
                            totalPcm += decoded.samples().remaining();
                        }
                    }
                }
            }
            require(models == 40 && encrypted == 5, "all plain and encrypted YSM models audited");
            require(counts.getOrDefault(YsmAudioDecoder.Codec.VORBIS, 0) >= 50
                            && counts.getOrDefault(YsmAudioDecoder.Codec.OPUS, 0) >= 4,
                    "both known corpus codecs decoded, including encrypted resources");
            System.out.println("M5 audio PCM decode: models=" + models + " encrypted=" + encrypted
                    + " codecs=" + counts + " pcmBytes=" + totalPcm + "; no audible claim.");
        } finally {
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected audio fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    static byte[] opus(int channels) throws Exception {
        var encoder = new OpusEncoder(48000, channels, OpusApplication.OPUS_APPLICATION_AUDIO);
        var info = new OpusInfo();
        info.setNumChannels(channels);
        info.setSampleRate(48000);
        info.setPreSkip(120);
        var output = new ByteArrayOutputStream();
        try (var stream = new OpusFile(output, info, new OpusTags())) {
            for (int packet = 0; packet < 3; packet++) {
                short[] input = new short[960 * channels];
                for (int frame = 0; frame < 960; frame++) {
                    short sample = (short) (12000 * Math.sin((packet * 960 + frame) * 440 * 2 * Math.PI / 48000));
                    for (int channel = 0; channel < channels; channel++) {
                        input[frame * channels + channel] = sample;
                    }
                }
                byte[] encoded = new byte[4096];
                int length = encoder.encode(input, 0, 960, encoded, 0, encoded.length);
                var audio = new OpusAudioData(Arrays.copyOf(encoded, length));
                audio.setGranulePosition((packet + 1) * 960L - (packet == 2 ? 80 : 0));
                stream.writeAudioData(audio);
            }
        }
        return output.toByteArray();
    }

    private static void expectFailure(CheckedAction action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IOException expected) {
            // Controlled decoding failure is required; silence is not a substitute for invalid data.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
