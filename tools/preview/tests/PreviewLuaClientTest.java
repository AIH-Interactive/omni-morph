package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

/** Client state is explicit; version comparison retains OMP's real implementation. */
public final class PreviewLuaClientTest {
    public static void main(String[] args) throws Exception {
        var states = new JsonObject();
        var client = new JsonObject();
        client.addProperty("getFiguraVersion", "0.1.5");
        states.add("client", client);
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42))) {
            if (!lua.evaluate("return client.getFiguraVersion() == '0.1.5' and "
                        + "client.compareVersions('0.1.5', '0.1.5+build') == 0 and "
                        + "client.compareVersions('0.1.0-rc.14', '0.1.5') < 0 and "
                        + "client.compareVersions('0.2', '0.1.9') > 0 and "
                        + "not pcall(client.compareVersions, 'invalid', '0.1.5')")
                    .arg1()
                    .checkboolean()) {
                throw new AssertionError("Configured client version or OMP comparison differs");
            }
        }
        var missing = new JsonObject();
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(Map.of(), List.of()), missing, 42, new PreviewMolang(missing, 42))) {
            try {
                lua.evaluate("return client.getFiguraVersion()");
                throw new AssertionError("Missing simulated client version was invented");
            } catch (LuaError expected) {
                if (!expected.getMessage().contains("states.client.getFiguraVersion")) {
                    throw expected;
                }
            }
        }
        System.out.println(
            "Client API PASS: explicit simulated version, real OMP comparison and missing-value diagnostics");
    }
}
