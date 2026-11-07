package top.aihmc.omm.preview;

import com.google.gson.JsonParser;

/** Explicit simulated equipment must preserve the reference ctrl hand matcher contract. */
public final class PreviewYsmEquipmentTest {
    private static void value(PreviewMolang host, String source, float expected) throws Exception {
        float actual = host.evaluate(host.compile(source));
        if (actual != expected)
            throw new AssertionError(source + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) throws Exception {
        var states = JsonParser.parseString("""
            {"equipment":{
              "mainhand":{"id":"minecraft:bow","types":["bow"],"tags":["example:ranged"],"using":true,"swinging":false},
              "offhand":{"id":"minecraft:shield","types":["shield"],"using":false,"swinging":true},
              "head":{"id":"minecraft:iron_helmet","tags":["example:armor"]},
              "feet":{"id":"minecraft:air"}
            }}
            """).getAsJsonObject();
        var host = new PreviewMolang(states, 42);
        value(host, "ctrl.use('mainhand',':bow')", 1);
        value(host, "ctrl.use('right','$minecraft:bow')", 1);
        value(host, "ctrl.use('main','#example:ranged',99)", 1);
        value(host, "ctrl.use('offhand',':shield')", 0);
        value(host, "ctrl.swing('offhand',':shield')", 1);
        value(host, "ctrl.swing('mainhand',':bow')", 0);
        value(host, "ctrl.hold('mainhand',':shield')", 0);
        value(host, "ctrl.hold('off','$minecraft:shield')", 1);
        value(host, "ctrl.use('head',':bow')", 0);
        value(host, "ctrl.armor('helmet','#example:armor')", 1);
        value(host, "ctrl.armor('feet','empty')", 1);
        value(host, "ctrl.armor('head',':helmet')", 0);
        value(host, "ctrl.hold('missing',':bow')", 0);
        var missing = new PreviewMolang(new com.google.gson.JsonObject(), 42);
        try {
            value(missing, "ctrl.use('mainhand',':bow')", 0);
            throw new AssertionError("Missing simulated equipment was silently treated as unused");
        } catch (UnsupportedOperationException expected) {
            if (!expected.getMessage().contains("states.equipment.mainhand"))
                throw expected;
        }
        states.getAsJsonObject("equipment").getAsJsonObject("mainhand").addProperty("using", false);
        value(host, "ctrl.use('mainhand',':bow')", 1);
        var changed = new PreviewMolang(states, 42);
        value(changed, "ctrl.use('mainhand',':bow')", 0);
        System.out.println("YSM equipment PASS: explicit use/swing/hold/armor, hand aliases, exact IDs/tags/types, "
                           + "missing-state diagnostics and isolation");
    }
}
