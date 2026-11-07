package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;

/** Original YsmInputState keyboard/mouse rules with an explicit offline owner/readiness snapshot. */
final class PreviewYsmInput {
    private final boolean[] keys = new boolean[349];
    private final boolean[] buttons = new boolean[8];
    private final boolean owner;
    private boolean ready;

    PreviewYsmInput(JsonObject states) {
        var input = states.has("input") ? states.getAsJsonObject("input") : new JsonObject();
        owner = flag(input, "owner", true, "states.input.");
        boolean gui = flag(states, "guiOpen", false, "states.");
        setReady(flag(input, "ready", !gui, "states.input."));
        configure(input, "keys", false);
        configure(input, "mouse", true);
    }

    private static boolean flag(JsonObject values, String name, boolean fallback, String path) {
        if (!values.has(name))
            return fallback;
        var value = values.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(path + name + " must be boolean");
        return value.getAsBoolean();
    }

    private void configure(JsonObject input, String name, boolean mouse) {
        if (!input.has(name))
            return;
        if (!input.get(name).isJsonArray())
            throw new IllegalArgumentException("states.input." + name + " must be an array of numeric codes");
        for (var value : input.getAsJsonArray(name)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() != value.getAsInt()
                || value.getAsInt() < (mouse ? 0 : 32) || value.getAsInt() > (mouse ? 7 : 348)) {
                throw new IllegalArgumentException("states.input." + name + " contains an invalid GLFW code");
            }
            event(mouse, value.getAsInt(), true);
        }
    }

    void setReady(boolean value) {
        ready = value;
        if (!ready)
            release();
    }

    void release() {
        Arrays.fill(keys, false);
        Arrays.fill(buttons, false);
    }

    void event(PreviewKeys.Key key, boolean pressed) {
        if (key.type() != PreviewKeys.Type.SCANCODE)
            event(key.type() == PreviewKeys.Type.MOUSE, key.value(), pressed);
    }

    private void event(boolean mouse, int code, boolean pressed) {
        if (!ready || code < (mouse ? 0 : 32) || code > (mouse ? 7 : 348))
            return;
        (mouse ? buttons : keys)[code] = pressed;
    }

    static Function binding(boolean mouse) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                var input = ((PreviewMolang) execution.entity()).input;
                if (!input.owner || !input.ready)
                    return false;
                for (int index = 0; index < args.size(); index++) {
                    int code = args.getAsInt(execution, index);
                    if (code >= (mouse ? 0 : 32) && code <= (mouse ? 7 : 348)
                        && (mouse ? input.buttons : input.keys)[code])
                        return true;
                }
                return false;
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return mouse ? size == 1 : size >= 1;
            }
        };
    }

    Map<String, Object> snapshot() {
        return Map.of("owner", owner, "ready", ready, "keys", held(keys), "mouse", held(buttons));
    }

    private static java.util.List<Integer> held(boolean[] values) {
        var held = new ArrayList<Integer>();
        for (int index = 0; index < values.length; index++) {
            if (values[index])
                held.add(index);
        }
        return held;
    }
}
