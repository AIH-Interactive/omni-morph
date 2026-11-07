package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omm.preview.data.PreviewTags.CompoundTag;

/** Original animation library text with real in-memory animation objects; no model asset parsing. */
public final class PreviewAnimationLibrariesTest {
    public static void main(String[] args) throws Exception {
        var folder = Path.of(args[0]);
        var sources = new LinkedHashMap<String, String>();
        for (String name : List.of("GSAnimBlend", "EZAnims")) {
            sources.put(name, Files.readString(folder.resolve(name + ".lua")));
        }
        var states =
            JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject().getAsJsonObject("states");
        var definition = new PreviewFiguraParts();
        definition.nodes.add(new PreviewFiguraParts.Node(
            "main", "main", -1, new Vector3f(), new Vector3f(), false, true, new Matrix4f(), new Matrix4f()));
        var animation = new CompoundTag();
        animation.putString("mdl", "main");
        animation.putString("name", "idle");
        animation.putString("loop", "loop");
        animation.putFloat("len", 1);
        definition.animations.add(animation);
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(sources, List.of("EZAnims")), states, 42, new PreviewMolang(states, 42))) {
            lua.attachFigura(definition);
            lua.initialize();
            for (int frame = 0; frame < 60; frame++) {
                lua.frame(frame / 60f);
                lua.postFrame(frame / 60f);
            }
            lua.evaluate("assert(animations.main.idle:isPlaying())");
        }
        System.out.println("Animation libraries PASS: original EZAnims/GSAnimBlend initialize + 60 frame tick/render "
                           + "and actual idle playing; no model loads");
    }
}
