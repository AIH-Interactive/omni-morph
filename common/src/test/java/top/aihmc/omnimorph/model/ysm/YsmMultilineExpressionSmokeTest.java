package top.aihmc.omnimorph.model.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmMemoryResourceSource;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceSource;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CPU expression execution and loader-to-runtime propagation; no GUI or graphics claims. */
public final class YsmMultilineExpressionSmokeTest {
    private YsmMultilineExpressionSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        List<Float> trace = new ArrayList<>();
        Function mark = (context, arguments) -> {
            trace.add(arguments.getAsFloat(context, 0));
            return 0f;
        };
        MolangBindings bindings = new MolangBindings();
        bindings.register("mark", mark);
        MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
        String animation = """
                {"animations":{"test":{"timeline":{"0":["loop(2, {", "mark(7);", "});"]},
                  "sound_effects":{"0":["a","b"]},"particle_effects":{"0":["c","d"]}}}}
                """;
        var merged = YsmAnimationParser.parse(animation, true, engine, true).get("test");
        require(merged.events.size() == 5, "only timeline arrays merge; sound and particle arrays retain separate events");
        var timeline = merged.events.stream().filter(event -> event.type().equals("timeline")).findFirst().orElseThrow();
        ExpressionEvaluator.evaluator(null).evalAll(timeline.expressions(), true);
        require(trace.equals(List.of(7f, 7f)), "a block split across timeline lines compiles and executes as one unit");
        require(YsmAnimationParser.parse(animation, engine).get("test").events.size() == 7,
                "the existing parser overload retains separate timeline entries");
        String controller = """
                {"animation_controllers":{"test":{"states":{"default":{
                  "on_entry":["loop(2, {","mark(3);","});"],
                  "on_exit":["loop(2, {","mark(9);","});"]}}}}}
                """;
        var diagnostics = new ArrayList<YsmAnimationControllerParser.Diagnostic>();
        var state = YsmAnimationControllerParser.parse(controller, engine, diagnostics::add, true)
                .get("test").states().get("default");
        require(diagnostics.isEmpty(), "merged fragments produce no controller parse errors");
        trace.clear();
        ExpressionEvaluator.evaluator(null).evalAll(state.onEntry(), true);
        ExpressionEvaluator.evaluator(null).evalAll(state.onExit(), true);
        require(trace.equals(List.of(3f, 3f, 9f, 9f)), "entry and exit both execute merged blocks");
        YsmAnimationControllerParser.parse(controller, engine, diagnostics::add);
        require(!diagnostics.isEmpty(), "disabled mode diagnoses incomplete fragments individually");

        var properties = YsmAvatarLoader.class.getDeclaredMethod("addYsmProperties", CompoundTag.class, YsmResourceSource.class);
        properties.setAccessible(true);
        for (boolean enabled : List.of(false, true)) {
            CompoundTag tag = new CompoundTag();
            try (var source = new YsmMemoryResourceSource("multiline-property", Map.of("ysm.json",
                    bytes("{\"properties\":{\"merge_multiline_expr\":" + enabled + "}}")))) {
                properties.invoke(null, tag, source);
            }
            require(tag.getBooleanOr("merge_multiline_expr", !enabled) == enabled, "loader retains the model's merge flag");
            tag.put("animations", entries(animation));
            tag.put("animation_controllers", entries(controller));
            var readAnimations = YsmModelRuntime.class.getDeclaredMethod("readAnimations", CompoundTag.class, MolangEngine.class);
            readAnimations.setAccessible(true);
            @SuppressWarnings("unchecked")
            var clips = (Map<String, YsmAnimationClip>) readAnimations.invoke(null, tag, engine);
            long timelineCount = clips.get("test").events.stream().filter(event -> event.type().equals("timeline")).count();
            require(timelineCount == (enabled ? 1 : 3), "runtime animation assembly consumes the model property");
            var readControllers = YsmModelRuntime.class.getDeclaredMethod("readAnimationControllers", CompoundTag.class, MolangEngine.class);
            readControllers.setAccessible(true);
            @SuppressWarnings("unchecked")
            var controllers = (Map<String, YsmAnimationController>) readControllers.invoke(null, tag, engine);
            trace.clear();
            ExpressionEvaluator.evaluator(null).evalAll(controllers.get("test").states().get("default").onEntry(), true);
            require(trace.size() == (enabled ? 2 : 1), "runtime controller assembly consumes the same property");
        }
        System.out.println("YSM multiline expressions: timeline, controller entry/exit, separate effects and runtime property PASS");
    }

    private static ListTag entries(String json) {
        CompoundTag entry = new CompoundTag();
        entry.putByteArray("data", bytes(json));
        ListTag entries = new ListTag();
        entries.add(entry);
        return entries;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
