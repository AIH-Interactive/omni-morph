package top.aihmc.omnimorph.model.outfit;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.molang.storage.VariableStorage;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Shared rules for the existing animation APIs when bound to a cosmetic instance. */
public final class CosmeticMolangVariables {
    private CosmeticMolangVariables() {
    }

    public static Float get(VariableStorage variables, String name) {
        Object value = variables.getPath(normalize(name));
        return value instanceof Number number ? number.floatValue() : null;
    }

    public static void set(VariableStorage variables, String name, float value) {
        String path = normalize(name);
        finite(value);
        variables.setPath(path, value);
    }

    public static void setAll(VariableStorage variables, LuaValue table) {
        Map<String, Float> values = new LinkedHashMap<>();
        for (LuaValue key : table.checktable().keys()) {
            String path = normalize(key.checkjstring());
            float value = table.get(key).checknumber().tofloat();
            finite(value);
            values.put(path, value);
        }
        // Reject the entire update before exposing a partially applied batch to animation evaluation.
        values.forEach(variables::setPath);
    }

    private static String normalize(String name) {
        String path = name.trim().toLowerCase(Locale.ROOT);
        if (path.startsWith("variable.")) {
            path = path.substring("variable.".length());
        } else if (path.startsWith("v.")) {
            path = path.substring(2);
        } else if (path.startsWith("query.") || path.startsWith("q.") || path.startsWith("context.")
                || path.startsWith("c.") || path.startsWith("temp.") || path.startsWith("t.")) {
            throw new LuaError("COSMETIC_VARIABLE_SCOPE");
        }
        if (!path.matches("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*")) {
            throw new LuaError("COSMETIC_VARIABLE_NAME");
        }
        return path;
    }

    private static void finite(float value) {
        if (!Float.isFinite(value)) {
            throw new LuaError("COSMETIC_VARIABLE_VALUE");
        }
    }
}
