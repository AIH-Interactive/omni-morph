package top.aihmc.omm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Local installation settings; no Minecraft singleton or game startup is needed. */
final class RuntimeConfiguration {
    private static final String[] DIRECTORIES = {
        "minecraftDirectory", "fabricVersionDirectory", "forgeVersionDirectory", "librariesDirectory"
    };

    private RuntimeConfiguration() {
    }

    static JsonObject read(Path path) throws Exception {
        return read(path, java.util.Map.of());
    }

    static JsonObject read(Path path, java.util.Map<String, String> overrides) throws Exception {
        JsonObject settings = Files.exists(path) ? json(path) : new JsonObject();
        defaultValue(settings, "librariesDirectory", "D:/Program Files/AIHMC/.minecraft/libraries");
        defaultValue(settings, "skinPath", "D:/UserData/Desktop/Project/OMP/OMP_26.1.2/tools/preview/skin.png");
        defaultValue(settings, "skinModel", "auto");
        defaultValue(settings, "modDirectory", path.getParent().resolve("libs").toString());
        overrides.forEach(settings::addProperty);
        Path skinPath = Path.of(text(settings, "skinPath"));
        if (!skinPath.isAbsolute()) {
            settings.addProperty("skinPath", path.getParent().resolve(skinPath).normalize().toString());
        }
        if (!valid(settings)) {
            SwingUtilities.invokeAndWait(() -> configure(settings));
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(settings));
        }
        if (!java.util.List.of("auto", "alex", "steve").contains(text(settings, "skinModel"))) {
            throw new IllegalArgumentException("skinModel must be auto, alex or steve");
        }
        return settings;
    }

    static JsonObject json(Path path) throws Exception {
        String contents = Files.readString(path);
        if (contents.startsWith("\uFEFF")) {
            contents = contents.substring(1);
        }
        return JsonParser.parseString(contents).getAsJsonObject();
    }

    static String text(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static void defaultValue(JsonObject settings, String key, String value) {
        if (!settings.has(key)) {
            settings.addProperty(key, value);
        }
    }

    private static boolean valid(JsonObject settings) {
        for (String key : DIRECTORIES) {
            String value = text(settings, key);
            if (value.isBlank() || !Files.isDirectory(Path.of(value))) {
                return false;
            }
        }
        return true;
    }

    private static void configure(JsonObject settings) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel("配置本机 Minecraft 依赖目录；不会启动游戏。"));
        var fields = new LinkedHashMap<String, JTextField>();
        String[] labels = {"Minecraft 根目录", "Fabric 26.1.2 版本目录", "Forge 1.20.1 版本目录", "共享 libraries 目录"};
        for (int index = 0; index < DIRECTORIES.length; index++) {
            String key = DIRECTORIES[index];
            JTextField field = new JTextField(text(settings, key), 55);
            fields.put(key, field);
            panel.add(new JLabel(labels[index]));
            panel.add(field);
        }
        while (true) {
            int answer = JOptionPane.showConfirmDialog(null, panel, "预览器 · Minecraft 配置",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                throw new IllegalStateException("Minecraft configuration cancelled");
            }
            fields.forEach((key, field) -> settings.addProperty(key, field.getText().strip()));
            if (valid(settings)) {
                return;
            }
            JOptionPane.showMessageDialog(null, "请填写四个有效目录。", "路径无效", JOptionPane.ERROR_MESSAGE);
        }
    }
}
