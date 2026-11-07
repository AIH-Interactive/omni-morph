package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Explicit null means a configured nil result, which must survive multi-model configuration copies. */
public final class PreviewConfigNullTest {
    public static void main(String[] args) throws Exception {
        var config = new PreviewConfig();
        config.states = JsonParser.parseString("{\"player\":{\"getVehicle\":null}}").getAsJsonObject();
        var method = PreviewMultiWindow.class.getDeclaredMethod(
            "sourceConfig", PreviewConfig.class, String.class, JsonObject.class);
        method.setAccessible(true);
        var selected = (PreviewConfig) method.invoke(null, config, "builtin:cube", new JsonObject());
        if (!selected.states.getAsJsonObject("player").has("getVehicle")
            || !selected.states.getAsJsonObject("player").get("getVehicle").isJsonNull()) {
            throw new AssertionError("sourceConfig lost the explicitly configured null query");
        }
        selected.states.getAsJsonObject("player").addProperty("getVehicle", "changed");
        if (!config.states.getAsJsonObject("player").get("getVehicle").isJsonNull()) {
            throw new AssertionError("Source state configuration is shared");
        }
        System.out.println("Config null PASS: explicit nil query retained and state copies isolated; no model loads");
    }
}
