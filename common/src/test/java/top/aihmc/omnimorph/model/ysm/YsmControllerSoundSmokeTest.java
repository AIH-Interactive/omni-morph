package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.animation.YsmControllerSoundBank;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerState;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import java.util.ArrayList;
import java.util.List;

/** Records the production controller's audio requests without a device or a game window. */
public final class YsmControllerSoundSmokeTest {
    private YsmControllerSoundSmokeTest() {
    }

    public static void main(String[] args) {
        List<String> sounds = new ArrayList<>();
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                // Function execution is independently validated; this fixture isolates state audio.
            }

            @Override
            public void playControllerSounds(String controllerName, YsmControllerState state) {
                sounds.addAll(state.soundEffects());
            }
        };
        Variable trigger = context -> ((float[]) context.entity())[0];
        ObjectBinding query = name -> name.equals("trigger") ? trigger : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("q") ? query : null);
        var controllers = YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"fixture":{"states":{
                  "default":{"sound_effects":["initial",{"effect":"initial.object"}],
                    "transitions":[{"next":"q.trigger"}]},
                  "next":{"sound_effects":[{"effect":"next.sound"}],
                    "transitions":[{"default":"1"}]}
                }}}}
                """, engine);
        require(controllers.get("fixture").states().get("default").soundEffects()
                .equals(List.of("initial", "initial.object")), "primitive and object sound declarations retained");
        player.registerControllers(controllers);
        float[] context = {0f};
        var evaluator = ExpressionEvaluator.evaluator(context);
        player.controllerRuntime().update(evaluator, 0.1f);
        player.controllerRuntime().update(evaluator, 0.1f);
        require(sounds.equals(List.of("initial", "initial.object")), "initial sounds fire once while staying in a state");
        context[0] = 1f;
        player.controllerRuntime().update(evaluator, 0.1f);
        require(sounds.equals(List.of("initial", "initial.object", "next.sound", "initial", "initial.object")),
                "empty-state chain enters the target and re-enters the original state in the same update");
        player.controllerRuntime().update(evaluator, 0.1f);
        require(player.controllerRuntime().currentState("fixture").equals("default"), "return transition happened");
        int count = sounds.size();
        player.controllerRuntime().update(evaluator, 0.1f);
        player.controllerRuntime().update(evaluator, 0.1f);
        require(sounds.size() == count && player.controllerRuntime().currentState("fixture").equals("default"),
                "held true sound transition cannot chatter");
        context[0] = 0f;
        player.controllerRuntime().update(evaluator, 0.1f);
        context[0] = 1f;
        player.controllerRuntime().update(evaluator, 0.1f);
        require(sounds.size() == count + 1 && sounds.getLast().equals("next.sound"), "false condition rearms sound transition");
        testEntryExitContext();
        testSoundOwnership();
        System.out.println("M3 controller entry sound requests and held-condition transition gating passed; audible playback is manual.");
    }

    private static void testEntryExitContext() {
        List<String> trace = new ArrayList<>();
        Function mark = (context, arguments) -> {
            trace.add(arguments.getAsString(context, 0) + ":" + arguments.getAsString(context, 1));
            return 0f;
        };
        Variable state = context -> ((Avatar.MolangContext) context.entity()).controller.getPath("current_state");
        ObjectBinding controller = name -> name.equals("current_state") ? state : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> switch (name) {
            case "mark" -> mark;
            case "c" -> controller;
            default -> null;
        });
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                // The explicit on_entry/on_exit expressions below provide the trace.
            }

            @Override
            public void stopControllerSounds(String controllerName) {
                trace.add("stop:" + controllerName);
                super.stopControllerSounds(controllerName);
            }
        };
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"fixture":{"states":{
                  "default":{"on_entry":["mark('initial',c.current_state);"],
                    "on_exit":["mark('exit',c.current_state);"],"transitions":[{"next":"1"}]},
                  "next":{"on_entry":["mark('entry',c.current_state);"]}
                }}}}
                """, engine));
        trace.clear(); // Registration also releases sounds owned by a replaced definition.
        player.controllerRuntime().update(ExpressionEvaluator.evaluator(new Avatar.MolangContext(null)), 0.1f);
        require(trace.equals(List.of("initial:default", "stop:fixture", "exit:default", "entry:next")),
                "entry/exit expressions see their own state context: " + trace);
    }

    private static void testSoundOwnership() {
        YsmControllerSoundBank bank = new YsmControllerSoundBank();
        List<String> stops = new ArrayList<>();
        boolean[] finished = {false};
        bank.add("main", () -> false, () -> stops.add("main.loading"));
        bank.add("main", () -> finished[0], () -> stops.add("main.finished"));
        bank.add("parallel", () -> false, () -> stops.add("parallel.loop"));
        bank.prune();
        require(stops.isEmpty(), "pruning keeps pending buffers and active channels");
        finished[0] = true;
        bank.prune();
        bank.stop("main");
        bank.stop("main");
        require(stops.equals(List.of("main.loading")),
                "controller exit stops only its live handles once; completed audio is released");
        bank.close();
        bank.close();
        require(stops.equals(List.of("main.loading", "parallel.loop")),
                "unload stops remaining owned channels once");
        YsmControllerSoundBank other = new YsmControllerSoundBank();
        other.add("main", () -> false, () -> stops.add("other.main"));
        bank.stop("main");
        require(stops.size() == 2, "different model instances do not share ownership");
        other.close();
        require(stops.getLast().equals("other.main"), "second instance retains its own sound");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
