package top.aihmc.omnimorph.model.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class YsmControllerDiagnosticSmokeTest {
    public static void main(String[] args) throws Exception {
        MolangEngine engine = MolangEngine.fromCustomBinding(new MolangBindings());
        String source = """
                {"animation_controllers":{"player_ctrl_main":{"initial_state":"walking","states":{
                  "walking":{"animations":[{"walk":"q.missing_weight"}],
                    "transitions":[{"idle":"q.missing_transition"}],
                    "on_entry":["q.missing_entry;"],"on_exit":["1;"]},
                  "idle":{"on_entry":["1;"]}
                }}}}
                """;
        List<YsmAnimationControllerParser.Diagnostic> diagnostics = new ArrayList<>();
        Map<String, YsmAnimationController> parsed = YsmAnimationControllerParser.parse(source, engine, diagnostics::add);
        require(parsed.size() == 1 && parsed.get("player_ctrl_main").states().size() == 2,
                "diagnosis preserves controller/state loading and existing fallback policy");
        require(diagnostics.size() == 3, "weight, transition and entry compilation all report failures");
        for (var diagnostic : diagnostics) {
            require(diagnostic.controller().equals("player_ctrl_main") && diagnostic.state().equals("walking"),
                    "every expression failure identifies its controller and state");
            require(diagnostic.message().contains("missing_"), "parser diagnosis retains the missing binding");
        }
        require(!parsed.get("player_ctrl_main").states().get("walking").onExit().isEmpty(),
                "valid statements after failed expressions still compile");

        CompoundTag controller = new CompoundTag();
        controller.putString("path", "controllers/player.json");
        controller.putByteArray("data", source.getBytes(StandardCharsets.UTF_8));
        ListTag files = new ListTag();
        files.add(controller);
        CompoundTag model = new CompoundTag();
        model.put("animation_controllers", files);
        List<String> messages = new ArrayList<>();
        BiConsumer<String, YsmAnimationControllerParser.Diagnostic> receiver = (path, diagnostic) ->
                messages.add(YsmModelRuntime.controllerDiagnostic("fixture-model", false, path, diagnostic));
        Method reader = YsmModelRuntime.class.getDeclaredMethod("readAnimationControllers",
                CompoundTag.class, MolangEngine.class, BiConsumer.class);
        reader.setAccessible(true);
        Object loaded = reader.invoke(null, model, engine, receiver);
        require(loaded instanceof Map<?, ?> controllers && controllers.size() == 1 && messages.size() == 3,
                "formal NBT controller reader forwards actual resource diagnostics");
        for (String message : messages) {
            require(message.contains("model=fixture-model") && message.contains("resource=controllers/player.json")
                            && message.contains("controller=player_ctrl_main") && message.contains("state=walking")
                            && message.contains("missing_"),
                    "plain runtime message supplies model/resource/controller/state/binding");
        }
        var privateFailure = new YsmAnimationControllerParser.Diagnostic("private-controller", "private-state", "private-source");
        require(YsmModelRuntime.controllerDiagnostic("private-model", true, "private-resource", privateFailure)
                        .equals("Encrypted YSM operation failed: runtime/controller-parse"),
                "encrypted runtime message exposes only a fixed category");
        diagnostics.clear();
        require(YsmAnimationControllerParser.parse("{broken", engine, diagnostics::add).isEmpty()
                        && diagnostics.size() == 1,
                "invalid JSON reports a document failure");
        diagnostics.clear();
        String array = """
                {"animation_controllers":[{"name":"array_controller","states":[
                  {"name":"array_state","on_entry":"q.array_missing;"}
                ]}]}
                """;
        YsmAnimationControllerParser.parse(array, engine, diagnostics::add);
        require(diagnostics.size() == 1 && diagnostics.getFirst().controller().equals("array_controller")
                        && diagnostics.getFirst().state().equals("array_state"),
                "array declarations preserve controller/state locations");
        System.out.println("M4 controller diagnostics: formal NBT resource, controller/state/binding location, recovery and encrypted privacy passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
