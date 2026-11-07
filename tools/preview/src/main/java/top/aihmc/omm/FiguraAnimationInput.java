package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.util.Map;

/** Supplies an explicit sample time to the original animation player, without a game clock. */
final class FiguraAnimationInput {
    private final Object animation;
    private final Class<?> player;
    private final Class<?> animationType;

    FiguraAnimationInput(Object avatar, JsonObject configuration) throws Exception {
        Map<?, ?> animations = (Map<?, ?>) avatar.getClass().getField("animations").get(avatar);
        String selected = configuration.get("name").getAsString();
        Object match = null;
        for (Object candidate : animations.values()) {
            String name = candidate.getClass().getField("modelName").get(candidate)
                + "/" + candidate.getClass().getField("name").get(candidate);
            if (selected.equals(name)) {
                if (match != null) {
                    throw new IllegalArgumentException("Ambiguous animation: " + selected);
                }
                match = candidate;
            }
        }
        if (match == null) {
            throw new IllegalArgumentException("Animation not found: " + selected);
        }
        animation = match;
        animationType = animation.getClass();
        player = Class.forName("org.figuramc.figura.animation.AnimationPlayer");
        animationType.getMethod("pause").invoke(animation);
    }

    void sample(float seconds) throws Exception {
        if (!Float.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Animation time must be finite and nonnegative");
        }
        player.getMethod("clear", animationType).invoke(null, animation);
        animationType.getMethod("setTime", float.class).invoke(animation, seconds);
        int remaining = (int) player.getMethod("tick", animationType, int.class)
            .invoke(null, animation, Integer.MAX_VALUE);
        System.out.println("FIGURA ANIMATION SAMPLE time=" + seconds
            + " cost=" + (Integer.MAX_VALUE - remaining));
    }

    static void report(Object avatar) throws Exception {
        Map<?, ?> animations = (Map<?, ?>) avatar.getClass().getField("animations").get(avatar);
        for (Object animation : animations.values()) {
            System.out.println("FIGURA CLIP " + animation.getClass().getField("modelName").get(animation)
                + "/" + animation.getClass().getField("name").get(animation)
                + " length=" + animation.getClass().getMethod("getLength").invoke(animation));
        }
    }
}
