package top.aihmc.omnimorph.config;

import com.google.gson.JsonParser;
import top.aihmc.omnimorph.FiguraMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Exercises real legacy/new configuration and local-data selection after the mod ID change. */
public final class OmniMorphIdentitySmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("omnimorph-identity-");
        System.setProperty("figura.test.directory", directory.toString());
        try {
            Path legacyDirectory = Files.createDirectory(directory.resolve("figura"));
            Path model = Files.createDirectory(legacyDirectory.resolve("avatars")).resolve("local-model-marker");
            Files.writeString(model, "synthetic-local-model");
            String legacyConfig = "{\"CONFIG_VERSION\":1,\"cloud_protocol\":\"FIGURA_CLOUD\","
                    + "\"server_ip\":\"https://preserved.test:7443\"}";
            Path oldFile = directory.resolve("figura.json");
            Path newFile = directory.resolve("omni-morph.json");
            Files.writeString(oldFile, legacyConfig);

            Path currentDirectory = directory.resolve("omnimorph");
            require(FiguraMod.getFiguraDirectory().equals(currentDirectory), "Default mod root uses omnimorph");
            require(!Files.exists(legacyDirectory), "Legacy root is renamed to the default directory");
            require(Files.readString(currentDirectory.resolve("avatars/local-model-marker")).equals("synthetic-local-model"),
                    "Renamed avatar directory preserves model bytes");
            require(top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.getLocalAvatarDirectory()
                    .equals(currentDirectory.resolve("avatars")), "Actual wardrobe resolves omnimorph/avatars");
            ConfigManager.loadConfig();
            require(Configs.CLOUD_PROTOCOL.value == 0 && Configs.SERVER_IP.value.equals("https://preserved.test:7443"),
                    "Actual old config retains explicit protocol and server origin");
            ConfigManager.saveConfig();
            require(Files.isRegularFile(newFile) && Files.readString(oldFile).equals(legacyConfig),
                    "Save writes the new mod config and preserves the original backup");
            var saved = JsonParser.parseString(Files.readString(newFile)).getAsJsonObject();
            require(saved.get("cloud_protocol").getAsString().equals("FIGURA_CLOUD"), "Explicit old protocol survives migration");
            saved.addProperty("cloud_protocol", "PROTOBUF");
            saved.addProperty("server_ip", "https://new-choice.test");
            Files.writeString(newFile, saved.toString());
            ConfigManager.loadConfig();
            require(Configs.CLOUD_PROTOCOL.value == 1 && Configs.SERVER_IP.value.equals("https://new-choice.test"),
                    "New configuration takes precedence over retained legacy config");
            require(Files.readString(oldFile).equals(legacyConfig), "Reload does not mutate the legacy backup");

            Path previousDirectory = Files.createDirectory(directory.resolve("omni-morph"));
            Files.writeString(previousDirectory.resolve("previous-marker"), "previous-root");
            require(FiguraMod.getFiguraDirectory().equals(currentDirectory), "Existing canonical root takes precedence");
            require(Files.exists(previousDirectory.resolve("previous-marker")), "Other roots are never merged or overwritten");
            Files.move(currentDirectory, directory.resolve("retained-models"));
            Files.createDirectory(legacyDirectory);
            Files.writeString(legacyDirectory.resolve("figura-marker"), "oldest-root");
            require(FiguraMod.getFiguraDirectory().equals(currentDirectory), "Previous branded root migrates to omnimorph");
            require(Files.readString(currentDirectory.resolve("previous-marker")).equals("previous-root")
                            && Files.exists(legacyDirectory.resolve("figura-marker")),
                    "omni-morph migration takes precedence over figura and preserves the other tree");
            Configs.MAIN_DIR.value = directory.resolve("explicit-models").toString();
            require(FiguraMod.getFiguraDirectory().equals(directory.resolve("explicit-models")),
                    "User-selected local model directory remains authoritative");
            Files.move(currentDirectory, directory.resolve("retained-previous-root"));
            require(FiguraMod.getFiguraDirectory().equals(directory.resolve("explicit-models"))
                            && Files.exists(legacyDirectory) && !Files.exists(currentDirectory),
                    "Explicit directory prevents automatic migration of default roots");
            Configs.MAIN_DIR.value = "";

            for (String language : new String[] {"en_us", "zh_cn"}) {
                try (var stream = OmniMorphIdentitySmokeTest.class.getClassLoader()
                        .getResourceAsStream("assets/" + FiguraMod.MOD_ID + "/lang/" + language + ".json")) {
                    require(stream != null, "Language is packaged in the active mod namespace");
                    var text = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                    require(text.has(FiguraMod.MOD_ID) && text.has(FiguraMod.MOD_ID + ".gui.help.omnimorph_version"),
                            "Real translated project and version fields resolve");
                    require(text.get(FiguraMod.MOD_ID + ".config.cloud_protocol.1").getAsString().equals("Figura Cloud"),
                            "Technical protocol identity remains Figura Cloud");
                }
            }
            var remap = top.aihmc.omnimorph.resources.FiguraRuntimeResources.class
                    .getDeclaredMethod("remapPublicResource", String.class, byte[].class);
            remap.setAccessible(true);
            String upstreamLanguage = "{\"figura\":\"Figura\",\"figura.gui.help.ingame_docs\":\"Figura Docs\","
                    + "\"figura.gui.synthetic\":\"Figura settings\",\"figura.docs.synthetic\":\"Figura Lua API\","
                    + "\"figura.config.cloud_protocol.1\":\"Figura Cloud\"}";
            byte[] mappedBytes = (byte[]) remap.invoke(null, "lang/zh_cn.json", upstreamLanguage.getBytes(StandardCharsets.UTF_8));
            var mapped = JsonParser.parseString(new String(mappedBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            require(!mapped.has("omni-morph.gui.help.ingame_docs") && !mapped.has("figura"),
                    "Remote upstream dictionaries do not override bundled Help/project identity");
            require(mapped.get("omni-morph.gui.synthetic").getAsString().equals("OmniMorph settings")
                            && mapped.get("omni-morph.docs.synthetic").getAsString().equals("Figura Lua API")
                            && mapped.get("omni-morph.config.cloud_protocol.1").getAsString().equals("Figura Cloud"),
                    "Public resource translation migration preserves technical names");
            byte[] font = (byte[]) remap.invoke(null, "font/ui.json", "{\"file\":\"figura:font/icons.png\"}".getBytes(StandardCharsets.UTF_8));
            require(JsonParser.parseString(new String(font, StandardCharsets.UTF_8)).getAsJsonObject()
                    .get("file").getAsString().equals("omni-morph:font/icons.png"), "Remote font identifiers mount in the active namespace");
            System.out.println("PASS OmniMorph identity: actual config import/precedence, retained local models and packaged localization");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
            System.clearProperty("figura.test.directory");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
