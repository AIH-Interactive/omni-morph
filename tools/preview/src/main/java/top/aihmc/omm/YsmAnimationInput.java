package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

/** Samples original YSM animation channels at configured times without advancing a game state machine. */
final class YsmAnimationInput {
    private final Object player;
    private final Object playing;
    private final Object evaluator;
    private final Method sample;
    private final String name;

    YsmAnimationInput(Object runtime, JsonObject configuration) throws Exception {
        name = configuration.get("name").getAsString();
        player = runtime.getClass().getMethod("animations").invoke(runtime);
        Map<?, ?> clips = (Map<?, ?>) player.getClass().getMethod("getClips").invoke(player);
        if (!clips.containsKey(name)) {
            throw new IllegalArgumentException("Original YSM animation not found: " + name);
        }
        Map<?, ?> active = (Map<?, ?>) player.getClass().getMethod("getActiveAnimations").invoke(player);
        for (Object key : new ArrayList<>(active.keySet())) {
            player.getClass().getMethod("stop", String.class).invoke(player, key.toString());
        }
        playing = player.getClass().getMethod("play", String.class, boolean.class, float.class)
            .invoke(player, name, true, 1f);
        Method getEvaluator = player.getClass().getDeclaredMethod("evaluatorForEvents");
        getEvaluator.setAccessible(true);
        evaluator = getEvaluator.invoke(player);
        Object context = Class.forName("top.aihmc.omnimorph.molang.runtime.ExecutionContext")
            .getMethod("entity").invoke(evaluator);
        ConfiguredMolangQueries.configure(context, configuration);
        sample = player.getClass().getDeclaredMethod("sampleAnimations",
            Class.forName("top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator"), boolean.class);
        sample.setAccessible(true);
    }

    void sample(float time) throws Exception {
        if (!Float.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("YSM animation time must be finite and nonnegative");
        }
        playing.getClass().getField("time").setFloat(playing, time);
        playing.getClass().getField("previousTime").setFloat(playing, time);
        sample.invoke(player, evaluator, false);
        System.out.println("OMP ORIGINAL YSM SAMPLE name=" + name + " time=" + time);
    }
}
