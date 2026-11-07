package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Avatar metadata comes from the same original parser records used by actual animation playback. */
public final class PreviewAvatarDataTest {
    public static void main(String[] args) throws Exception {
        var config =
            PreviewConfig.load(new String[] {"--config", Path.of(args[0], "figura-keyframes.json").toString()});
        var mesh = PreviewMesh.load(config);
        var states = new JsonObject();
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42))) {
            lua.attachFigura(mesh.figuraParts);
            lua.initialize();
            int count = mesh.figuraParts.animations.size();
            if (!lua.evaluate("local data = avatar:getNBT(); assert(#data.animations == " + count + "); "
                        + "local codes = 0; for _, def in ipairs(data.animations) do "
                        + "assert(animations[def.mdl][def.name]:getLength() == (def.len or 0)); "
                        + "codes = codes + #(def.code or {}) end; assert(codes > 0); "
                        + "assert(#data.models.chld > 0); "
                        + "data.animations[1].name = 'changed'; data.models.chld[1].name = 'changed'; "
                        + "local fresh = avatar:getNBT(); return fresh.animations[1].name ~= 'changed' "
                        + "and fresh.models.chld[1].name ~= 'changed'")
                    .arg1()
                    .checkboolean()) {
                throw new AssertionError("Parser metadata, live animation lookup or fresh-table isolation differs");
            }
        }
        System.out.println(
            "Avatar data PASS: actual models/animations/timeline metadata, animation lookup and fresh snapshots");
    }
}
