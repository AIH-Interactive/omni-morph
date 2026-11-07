package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

/** Verifies the runnable JAR contains the actual game texture bytes under Minecraft asset paths. */
public final class PackagedTexturesTest {
    public static void main(String[] args) throws Exception {
        try (ZipFile jar = new ZipFile(args[0])) {
            var notice = jar.getEntry("assets/omm_preview/texture-sources.json");
            if (notice == null) {
                throw new AssertionError("Texture provenance missing from JAR");
            }
            var metadata =
                JsonParser.parseString(new String(jar.getInputStream(notice).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            int count = 0;
            for (var element : metadata.getAsJsonArray("textures")) {
                var expected = element.getAsJsonObject();
                String name = expected.get("entry").getAsString();
                var entry = jar.getEntry(name);
                if (entry == null) {
                    throw new AssertionError("Packaged texture missing: " + name);
                }
                byte[] bytes;
                try (var input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                if (!PreviewTextures.sha256(bytes).equals(expected.get("sha256").getAsString())) {
                    throw new AssertionError("Packaged texture hash differs from game resource: " + name);
                }
                if (PreviewTextures.image(bytes, name).getWidth() <= 0) {
                    throw new AssertionError("Invalid PNG: " + name);
                }
                count++;
            }
            if (count != 47) {
                throw new AssertionError("Expected six environment/player and 41 particle textures; found " + count);
            }
            if (metadata.getAsJsonArray("particleDefinitions").size() != 6) {
                throw new AssertionError("Six source particle definitions were not packaged");
            }
            for (var element : metadata.getAsJsonArray("particleDefinitions")) {
                var expected = element.getAsJsonObject();
                String name = expected.get("entry").getAsString();
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getEntry(name))) {
                    bytes = input.readAllBytes();
                }
                if (!PreviewTextures.sha256(bytes).equals(expected.get("sha256").getAsString())) {
                    throw new AssertionError("Particle definition differs from game source: " + name);
                }
                var sprites = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                                  .getAsJsonObject()
                                  .getAsJsonArray("textures");
                for (var sprite : sprites) {
                    String path = "assets/" + sprite.getAsString().replace(":", "/textures/particle/") + ".png";
                    if (jar.getEntry(path) == null)
                        throw new AssertionError("Particle sprite missing: " + path);
                }
            }
            var audioNotice = jar.getEntry("assets/omm_preview/audio-sources.json");
            var audioMetadata =
                JsonParser
                    .parseString(new String(jar.getInputStream(audioNotice).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (audioMetadata.getAsJsonArray("audio").isEmpty())
                throw new AssertionError("No original audio packaged");
            for (var value : audioMetadata.getAsJsonArray("audio")) {
                var expected = value.getAsJsonObject();
                String path = expected.get("entry").getAsString();
                byte[] bytes;
                try (var input = jar.getInputStream(jar.getEntry(path))) {
                    bytes = input.readAllBytes();
                }
                if (!PreviewTextures.sha256(bytes).equals(expected.get("sha256").getAsString())) {
                    throw new AssertionError("Packaged sound differs from original asset: " + path);
                }
            }
            try (var input = jar.getInputStream(jar.getEntry("assets/minecraft/sounds.json"))) {
                var events =
                    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (var event : audioMetadata.getAsJsonArray("selectedEvents")) {
                    if (!events.has(event.getAsString().replace("minecraft:", ""))) {
                        throw new AssertionError("Selected sound event missing: " + event);
                    }
                }
            }
        }
        System.out.println("Packaged textures/audio PASS: original PNG/particle/sound assets and source hashes match");
    }
}
