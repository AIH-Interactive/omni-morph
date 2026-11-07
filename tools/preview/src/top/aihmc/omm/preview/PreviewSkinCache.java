package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Persistent URL-keyed PNG cache and last successful skin selection beside the preview JAR. */
final class PreviewSkinCache {
    record Candidate(byte[] bytes, String url, Path directory, Path file, boolean cached) {}

    private PreviewSkinCache() {}

    static Candidate load(PreviewConfig config) throws IOException {
        Path directory = directory(config);
        String url = config.vanilla.skinUrl;
        Path selection = directory.resolve("last-skin.json");
        if (url.isBlank() && Files.exists(selection)) {
            url = JsonParser.parseString(Files.readString(selection)).getAsJsonObject().get("url").getAsString();
        }
        if (url.isBlank())
            return null;
        URI uri = URI.create(url);
        if (!List.of("http", "https").contains(uri.getScheme()))
            throw new IOException("vanilla.skinUrl must use HTTP or HTTPS");
        String key = PreviewTextures.sha256(url.getBytes(StandardCharsets.UTF_8));
        Path file = directory.resolve(key + ".png");
        if (Files.exists(file))
            return new Candidate(Files.readAllBytes(file), url, directory, file, true);
        try (var client = HttpClient.newBuilder()
                 .connectTimeout(Duration.ofSeconds(15))
                 .followRedirects(HttpClient.Redirect.NORMAL)
                 .build()) {
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200)
                    throw new IOException("Skin URL returned HTTP " + response.statusCode());
                byte[] bytes = input.readNBytes(4 * 1024 * 1024 + 1);
                if (bytes.length > 4 * 1024 * 1024)
                    throw new IOException("Skin PNG exceeds 4 MiB");
                return new Candidate(bytes, url, directory, file, false);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Skin download interrupted", interrupted);
        }
    }

    static void remember(Candidate skin) throws IOException {
        Files.createDirectories(skin.directory());
        if (!skin.cached())
            Files.write(skin.file(), skin.bytes());
        JsonObject selection = new JsonObject();
        selection.addProperty("url", skin.url());
        selection.addProperty("sha256", PreviewTextures.sha256(skin.bytes()));
        selection.addProperty("file", skin.file().getFileName().toString());
        Files.writeString(
            skin.directory().resolve("last-skin.json"), PreviewConfig.JSON.toJson(selection), StandardCharsets.UTF_8);
    }

    private static Path directory(PreviewConfig config) throws IOException {
        if (!config.vanilla.skinCacheDirectory.isBlank()) {
            return config.configFile.getParent().resolve(config.vanilla.skinCacheDirectory).normalize();
        }
        try {
            Path location = Path.of(PreviewSkinCache.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.getParent().resolve("skin-cache");
        } catch (java.net.URISyntaxException invalid) {
            throw new IOException("Unable to locate preview skin cache directory", invalid);
        }
    }
}
