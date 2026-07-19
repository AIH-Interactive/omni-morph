package org.figuramc.figura.model.ysm.controller;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class YsmControllerRuntime {
    private final YsmAnimationPlayer player;
    private final Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
    private final Map<String, String> currentStates = new HashMap<>();
    private final Map<String, Float> stateTimes = new HashMap<>();
    private final Map<String, Set<String>> activeByController = new HashMap<>();
    private final Map<String, Set<String>> completedOnceByController = new HashMap<>();
    private final Set<String> enteredInitialStates = new HashSet<>();

    public YsmControllerRuntime(YsmAnimationPlayer player) {
        this.player = player;
    }

    public void register(Map<String, YsmAnimationController> newControllers) {
        if (newControllers == null || newControllers.isEmpty())
            return;
        controllers.putAll(newControllers);
        for (YsmAnimationController controller : newControllers.values()) {
            YsmControllerState state = controller.initialStateDefinition();
            if (state != null) {
                currentStates.put(controller.name(), state.name());
                stateTimes.put(controller.name(), 0f);
                enteredInitialStates.remove(controller.name());
            }
        }
    }

    public Map<String, YsmAnimationController> controllers() {
        return controllers;
    }

    public String currentState(String controller) {
        return currentStates.get(controller);
    }

    public void update(ExpressionEvaluator<?> evaluator) {
        update(evaluator, 0f);
    }

    public void update(ExpressionEvaluator<?> evaluator, float deltaTime) {
        controllers.values().stream()
                .sorted(Comparator
                        .comparing(YsmAnimationController::slot)
                        .thenComparing(YsmAnimationController::name))
                .forEach(controller -> update(controller, evaluator, deltaTime));
    }

    private void update(YsmAnimationController controller, ExpressionEvaluator<?> evaluator, float deltaTime) {
        YsmControllerState state = state(controller);
        if (state == null)
            return;
        stateTimes.merge(controller.name(), Math.max(0f, deltaTime), Float::sum);

        Avatar.MolangContext context = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
        float oldAnimTime = context == null ? 0f : context.anim_time;
        float oldLifeTime = context == null ? 0f : context.life_time;
        float oldAnyFinished = context == null ? 0f : context.any_animation_finished;
        float oldAllFinished = context == null ? 0f : context.all_animations_finished;
        try {
            applyControllerClock(controller, context);
            applyControllerContext(controller, context);
            player.runFunctionEventsForController(controller, evaluator);
            applyControllerContext(controller, context);
            if (enteredInitialStates.add(controller.name())) {
                execute(state.onEntry(), evaluator);
                applyControllerContext(controller, context);
            }
            YsmControllerTransition transition = firstTransition(state, evaluator);
            if (transition != null) {
                transition(controller, state, transition.targetState(), evaluator);
                state = state(controller);
                if (state == null)
                    return;
            }

            Set<String> desired = new HashSet<>();
            Set<String> completed = completedOnceByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>());
            for (String animation : Set.copyOf(activeByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>()))) {
                if (player.isFinishedOnce(animation))
                    completed.add(normalize(animation));
            }
            for (YsmControllerAnimationRef ref : state.animations()) {
                if (ref.animation() == null || ref.animation().isBlank() || "empty".equals(ref.animation()))
                    continue;
                if (ref.condition() != null && !evalBool(ref.condition(), evaluator))
                    continue;
                YsmAnimationClip clip = clip(ref.animation());
                if (clip == null)
                    continue;
                if (clip.loopMode == YsmAnimationClip.LoopMode.ONCE && completed.contains(normalize(ref.animation())))
                    continue;
                desired.add(ref.animation());
                player.play(ref.animation(), clip.loopMode, 1f, state.blendTransition());
            }

            Set<String> previous = activeByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>());
            for (String animation : Set.copyOf(previous)) {
                if (!desired.contains(animation)) {
                    boolean finishedOnce = player.isFinishedOnce(animation);
                    player.fadeOut(animation, state.blendTransition());
                    if (!finishedOnce)
                        completed.remove(normalize(animation));
                }
            }
            previous.clear();
            previous.addAll(desired);
        } finally {
            if (context != null) {
                context.anim_time = oldAnimTime;
                context.life_time = oldLifeTime;
                context.any_animation_finished = oldAnyFinished;
                context.all_animations_finished = oldAllFinished;
            }
        }
    }

    private YsmControllerState state(YsmAnimationController controller) {
        String stateName = currentStates.get(controller.name());
        YsmControllerState state = controller.states().get(stateName);
        if (state != null)
            return state;
        state = controller.initialStateDefinition();
        if (state != null)
            currentStates.put(controller.name(), state.name());
        return state;
    }

    private YsmControllerTransition firstTransition(YsmControllerState state, ExpressionEvaluator<?> evaluator) {
        for (YsmControllerTransition transition : state.transitions()) {
            if (transition.condition() == null || evalBool(transition.condition(), evaluator))
                return transition;
        }
        return null;
    }

    private void transition(YsmAnimationController controller, YsmControllerState oldState, String newState, ExpressionEvaluator<?> evaluator) {
        YsmControllerState target = controller.states().get(newState);
        if (target == null || target == oldState)
            return;
        execute(oldState.onExit(), evaluator);
        currentStates.put(controller.name(), target.name());
        stateTimes.put(controller.name(), 0f);
        float fadeSeconds = Math.max(oldState.blendTransition(), target.blendTransition());
        for (String animation : activeByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>()))
            player.fadeOut(animation, fadeSeconds);
        activeByController.get(controller.name()).clear();
        completedOnceByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>()).clear();
        execute(target.onEntry(), evaluator);
    }

    private void execute(Iterable<Expression> expressions, ExpressionEvaluator<?> evaluator) {
        if (evaluator == null || expressions == null)
            return;
        for (Expression expression : expressions) {
            try {
                evaluator.eval(expression);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean evalBool(Expression expression, ExpressionEvaluator<?> evaluator) {
        if (expression == null)
            return true;
        if (evaluator == null)
            return false;
        try {
            return evaluator.evalAsBoolean(expression);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void applyControllerClock(YsmAnimationController controller, Avatar.MolangContext context) {
        if (context == null)
            return;
        float maxTime = 0f;
        float maxLength = 0f;
        boolean any = false;
        boolean all = true;
        boolean hasAnimation = false;
        Set<String> animations = activeByController.get(controller.name());
        if (animations != null) {
            for (String animation : animations) {
                YsmAnimationPlayer.PlayingAnimation playing = player.getActiveAnimations().get(normalize(animation));
                if (playing == null)
                    continue;
                hasAnimation = true;
                maxTime = Math.max(maxTime, playing.time);
                maxLength = Math.max(maxLength, playing.clip.length);
                boolean finished = playing.clip.length <= 0f || playing.time >= playing.clip.length;
                any |= finished;
                all &= finished;
            }
        }
        context.anim_time = maxTime;
        context.life_time = maxLength;
        context.any_animation_finished = any ? 1f : 0f;
        context.all_animations_finished = !hasAnimation || all ? 1f : 0f;
    }

    private void applyControllerContext(YsmAnimationController controller, Avatar.MolangContext context) {
        if (context == null)
            return;
        String stateName = currentStates.getOrDefault(controller.name(), "");
        setControllerValue(context, "controller", controller.name());
        setControllerValue(context, "slot", controller.slot().name().toLowerCase(Locale.US));
        setControllerValue(context, "current_state", stateName);
        setControllerValue(context, "state_time", stateTimes.getOrDefault(controller.name(), 0f));
        setControllerValue(context, "anim_time", context.anim_time);
        setControllerValue(context, "life_time", context.life_time);
        setControllerValue(context, "any_animation_finished", context.any_animation_finished);
        setControllerValue(context, "all_animations_finished", context.all_animations_finished);
        for (String knownState : controller.states().keySet())
            setControllerValue(context, normalizeStateFlag(knownState), knownState.equals(stateName) ? 1f : 0f);
    }

    private static void setControllerValue(Avatar.MolangContext context, String name, Object value) {
        context.controller.setScoped(StringPool.computeIfAbsent(name), value);
    }

    private static String normalizeStateFlag(String name) {
        if (name == null || name.isBlank())
            return "state";
        return name.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_').replace('.', '_');
    }

    private static String normalize(String name) {
        if (name == null)
            return "";
        String value = name;
        if (value.startsWith("animation."))
            value = value.substring("animation.".length());
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length())
            value = value.substring(colon + 1);
        int suffix = value.indexOf('#');
        if (suffix >= 0)
            value = value.substring(0, suffix);
        return value.toLowerCase(Locale.US);
    }

    private YsmAnimationClip clip(String name) {
        YsmAnimationClip clip = player.getClips().get(name);
        if (clip != null)
            return clip;
        String normalized = normalize(name);
        clip = player.getClips().get(normalized);
        if (clip != null)
            return clip;
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length()) {
            clip = player.getClips().get(normalized.substring(dot + 1));
            if (clip != null)
                return clip;
        }
        return null;
    }
}
