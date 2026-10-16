package org.figuramc.figura.model.ysm.controller;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.molang.storage.VariableStorage;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class YsmControllerRuntime {
    private static final String CHILD_ENTRY_PREFIX = "ysm-entry-";
    private static final int MAX_CONTROLLER_DEPTH = 5;
    private final YsmAnimationPlayer player;
    private final Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
    private final Map<String, String> currentStates = new HashMap<>();
    private final Map<String, Float> stateTimes = new HashMap<>();
    private final Map<String, Set<String>> activeByController = new HashMap<>();
    private final Map<String, Set<String>> completedOnceByController = new HashMap<>();
    private final Set<String> enteredInitialStates = new HashSet<>();
    private final Set<SoundTransitionKey> heldSoundTransitions = new HashSet<>();
    private final Map<String, String> activeChildren = new HashMap<>();

    private record SoundTransitionKey(String controller, String source, String target) {
    }

    public YsmControllerRuntime(YsmAnimationPlayer player) {
        this.player = player;
    }

    public void register(Map<String, YsmAnimationController> newControllers) {
        if (newControllers == null || newControllers.isEmpty())
            return;
        controllers.putAll(newControllers);
        heldSoundTransitions.removeIf(key -> newControllers.containsKey(key.controller()));
        for (YsmAnimationController controller : newControllers.values()) {
            deactivateChild(controller, null);
            player.stopControllerSounds(controller.name());
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

    public float stateTime(String controller) {
        return stateTimes.getOrDefault(controller, 0f);
    }

    /** Imported state does not replay entry/exit effects or infer transitions from observer input. */
    public void applyRemoteState(Map<String, String> states, Map<String, Float> times) {
        for (var entry : states.entrySet()) {
            YsmAnimationController controller = controllers.get(entry.getKey());
            Float time = times.get(entry.getKey());
            if (controller == null || !controller.states().containsKey(entry.getValue())
                    || time == null || !Float.isFinite(time) || time < 0) {
                throw new IllegalArgumentException("Invalid remote controller state");
            }
        }
        currentStates.clear();
        currentStates.putAll(states);
        stateTimes.clear();
        stateTimes.putAll(times);
        enteredInitialStates.clear();
        enteredInitialStates.addAll(states.keySet());
        activeByController.clear();
        completedOnceByController.clear();
        activeChildren.clear();
        heldSoundTransitions.clear();
    }

    public void addRemotePlayback(String controller, String clip) {
        String name = controllers.keySet().stream().filter(key -> key.equalsIgnoreCase(controller)).findFirst().orElse(controller);
        activeByController.computeIfAbsent(name, ignored -> new HashSet<>()).add(clip);
    }

    public void advanceRemoteTime(float deltaSeconds) {
        if (Float.isFinite(deltaSeconds) && deltaSeconds > 0) {
            stateTimes.replaceAll((name, time) -> (float) Math.min(Float.MAX_VALUE, (double) time + deltaSeconds));
        }
    }

    public void update(ExpressionEvaluator<?> evaluator) {
        update(evaluator, 0f);
    }

    public void update(ExpressionEvaluator<?> evaluator, float deltaTime) {
        update(evaluator, deltaTime, true);
    }

    public void update(ExpressionEvaluator<?> evaluator, float deltaTime, boolean firstPersonActive) {
        if (!firstPersonActive) {
            deactivateFirstPersonControllers(evaluator);
        }
        Set<String> childDefinitions = new HashSet<>();
        for (YsmAnimationController controller : controllers.values()) {
            for (YsmControllerState state : controller.states().values()) {
                String child = childName(controller, state);
                if (child != null) {
                    childDefinitions.add(child);
                }
            }
        }
        controllers.values().stream()
                .filter(controller -> !childDefinitions.contains(controller.name()))
                .filter(controller -> firstPersonActive || controller.slot() != YsmControllerSlot.FIRST_PERSON_ARM)
                .sorted(Comparator
                        .comparingInt((YsmAnimationController controller) -> YsmControllerSlot.executionOrder(controller.name()))
                        .thenComparing(YsmAnimationController::name))
                .forEach(controller -> update(controller, evaluator, deltaTime, 1));
    }

    private void update(YsmAnimationController controller, ExpressionEvaluator<?> evaluator, float deltaTime, int depth) {
        YsmControllerState state = state(controller);
        if (state == null)
            return;
        stateTimes.merge(controller.name(), Math.max(0f, deltaTime), Float::sum);

        Avatar.MolangContext context = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
        try (ContextScope contextScope = openControllerContext(controller.name(), evaluator)) {
            player.runFunctionEventsForController(controller, evaluator);
            applyControllerContext(controller, context);
            if (enteredInitialStates.add(controller.name())) {
                player.beginControllerPoseTransition(controller.name(), state.blendCurve(), state.blendViaShortestPath());
                execute(state.onEntry(), evaluator);
                player.playControllerSounds(controller.name(), state);
                applyControllerContext(controller, context);
            }
            Set<String> visitedStates = new HashSet<>();
            visitedStates.add(state.name());
            YsmControllerTransition transition;
            while ((transition = firstTransition(controller, state, evaluator, visitedStates)) != null) {
                if (!visitedStates.add(transition.targetState())) {
                    break;
                }
                transition(controller, state, transition.targetState(), evaluator);
                state = state(controller);
                if (state == null)
                    return;
                if (!state.animations().isEmpty() && childName(controller, state) == null) {
                    break;
                }
            }

            String childName = childName(controller, state);
            if (childName != null) {
                YsmAnimationController child = controllers.get(childName);
                if (child != null && depth < MAX_CONTROLLER_DEPTH) {
                    activeChildren.put(controller.name(), childName);
                    update(child, evaluator, deltaTime, depth + 1);
                }
                return;
            }

            Set<String> desired = new HashSet<>();
            Set<String> completed = completedOnceByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>());
            for (String animation : Set.copyOf(activeByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>()))) {
                if (player.isControllerAnimationFinished(controller.name(), animation))
                    completed.add(normalize(animation));
            }
            for (YsmControllerAnimationRef ref : state.animations()) {
                if (ref.animation() == null || ref.animation().isBlank() || "empty".equals(ref.animation()))
                    continue;
                float weight = animationWeight(ref.condition(), evaluator);
                if (weight <= 0f) {
                    completed.remove(normalize(ref.animation()));
                    continue;
                }
                YsmAnimationClip clip = clip(ref.animation());
                if (clip == null)
                    continue;
                if (clip.loopMode == YsmAnimationClip.LoopMode.ONCE && completed.contains(normalize(ref.animation())))
                    continue;
                desired.add(ref.animation());
                player.playControllerAnimation(controller.name(), ref.animation(), clip.loopMode, weight,
                        state.blendTransition(), evaluator);
            }

            Set<String> previous = activeByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>());
            for (String animation : Set.copyOf(previous)) {
                if (!desired.contains(animation)) {
                    boolean finishedOnce = player.isControllerAnimationFinished(controller.name(), animation);
                    player.fadeOutControllerAnimation(controller.name(), animation, state.blendTransition());
                    if (!finishedOnce)
                        completed.remove(normalize(animation));
                }
            }
            previous.clear();
            previous.addAll(desired);
        }
    }

    private static String childName(YsmAnimationController controller, YsmControllerState state) {
        if (!state.name().startsWith(CHILD_ENTRY_PREFIX)) {
            return null;
        }
        return controller.name() + "." + state.name().substring(CHILD_ENTRY_PREFIX.length());
    }

    private void deactivateChild(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
        String childName = activeChildren.remove(controller.name());
        YsmAnimationController child = controllers.get(childName);
        if (child == null) {
            return;
        }
        deactivateController(child, evaluator);
    }

    private void preserveChildPose(YsmAnimationController controller) {
        String childName = activeChildren.get(controller.name());
        YsmAnimationController child = controllers.get(childName);
        if (child != null) {
            preserveChildPose(child);
            player.inheritControllerPose(controller.name(), child.name());
        }
    }

    public void deactivateFirstPersonControllers(ExpressionEvaluator<?> evaluator) {
        for (YsmAnimationController controller : controllers.values()) {
            if (controller.slot() == YsmControllerSlot.FIRST_PERSON_ARM && enteredInitialStates.contains(controller.name())) {
                deactivateController(controller, evaluator);
            }
        }
    }

    private void deactivateController(YsmAnimationController child, ExpressionEvaluator<?> evaluator) {
        deactivateChild(child, evaluator);
        player.stopControllerSounds(child.name());
        YsmControllerState state = state(child);
        if (state != null && enteredInitialStates.contains(child.name())) {
            try (ContextScope contextScope = openControllerContext(child.name(), evaluator)) {
                execute(state.onExit(), evaluator);
            }
        }
        player.resetControllerAnimations(child.name());
        activeByController.remove(child.name());
        completedOnceByController.remove(child.name());
        enteredInitialStates.remove(child.name());
        heldSoundTransitions.removeIf(key -> key.controller().equals(child.name()));
        currentStates.put(child.name(), child.initialState());
        stateTimes.put(child.name(), 0f);
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

    private YsmControllerTransition firstTransition(YsmAnimationController controller, YsmControllerState state,
                                                     ExpressionEvaluator<?> evaluator, Set<String> visitedStates) {
        for (YsmControllerTransition transition : state.transitions()) {
            boolean condition = transition.condition() == null || evalBool(transition.condition(), evaluator);
            YsmControllerState target = controller.states().get(transition.targetState());
            if (target == null || target == state) {
                continue;
            }
            // A rejected cycle edge must not consume the sound gate for a later frame.
            if (condition && visitedStates.contains(target.name())) {
                continue;
            }
            if (hasSoundTrigger(target)) {
                SoundTransitionKey key = new SoundTransitionKey(controller.name(), state.name(), target.name());
                if (!condition) {
                    heldSoundTransitions.remove(key);
                    continue;
                }
                if (!heldSoundTransitions.add(key)) {
                    continue;
                }
            }
            if (condition) {
                return transition;
            }
        }
        return null;
    }

    private boolean hasSoundTrigger(YsmControllerState state) {
        if (!state.soundEffects().isEmpty()) {
            return true;
        }
        for (YsmControllerAnimationRef reference : state.animations()) {
            YsmAnimationClip clip = clip(reference.animation());
            if (clip != null && clip.events.stream().anyMatch(event -> "sound".equals(event.type()))) {
                return true;
            }
        }
        return false;
    }

    private void transition(YsmAnimationController controller, YsmControllerState oldState, String newState, ExpressionEvaluator<?> evaluator) {
        YsmControllerState target = controller.states().get(newState);
        if (target == null || target == oldState)
            return;
        player.stopControllerSounds(controller.name());
        preserveChildPose(controller);
        deactivateChild(controller, evaluator);
        Avatar.MolangContext exitContext = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext value
                ? value : null;
        applyControllerClock(controller, exitContext);
        applyControllerContext(controller, exitContext);
        execute(oldState.onExit(), evaluator);
        currentStates.put(controller.name(), target.name());
        stateTimes.put(controller.name(), 0f);
        float fadeSeconds = target.blendCurve().duration();
        player.beginControllerPoseTransition(controller.name(), target.blendCurve(), target.blendViaShortestPath());
        for (String animation : activeByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>()))
            player.retireControllerAnimation(controller.name(), animation, fadeSeconds);
        activeByController.get(controller.name()).clear();
        completedOnceByController.computeIfAbsent(controller.name(), ignored -> new HashSet<>()).clear();
        Avatar.MolangContext context = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext value ? value : null;
        applyControllerClock(controller, context);
        applyControllerContext(controller, context);
        execute(target.onEntry(), evaluator);
        player.playControllerSounds(controller.name(), target);
    }

    private void execute(Iterable<Expression> expressions, ExpressionEvaluator<?> evaluator) {
        if (evaluator == null || expressions == null)
            return;
        try (var effectScope = player.beginControllerEffects()) {
            for (Expression expression : expressions) {
                try {
                    evaluator.eval(expression);
                } catch (MolangExecutionBudget.Exceeded exhausted) {
                    throw exhausted;
                } catch (Exception failure) {
                    reportFailure(evaluator, failure);
                }
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
        } catch (MolangExecutionBudget.Exceeded exhausted) {
            throw exhausted;
        } catch (Exception failure) {
            reportFailure(evaluator, failure);
            return false;
        }
    }

    private static float animationWeight(Expression expression, ExpressionEvaluator<?> evaluator) {
        if (expression == null) {
            return 1f;
        }
        if (evaluator == null) {
            return 0f;
        }
        try {
            float value = evaluator.evalAsFloat(expression);
            return Float.isFinite(value) && value > 0f ? value : 0f;
        } catch (MolangExecutionBudget.Exceeded exhausted) {
            throw exhausted;
        } catch (RuntimeException e) {
            reportFailure(evaluator, e);
            return 0f;
        }
    }

    private static void reportFailure(ExpressionEvaluator<?> evaluator, Exception failure) {
        if (evaluator.entity() instanceof Avatar.MolangContext context && context.owner != null
                && context.owner.getYsmRuntime() != null) {
            context.owner.getYsmRuntime().reportControllerExecutionFailure(failure);
        }
    }

    private void applyControllerClock(YsmAnimationController controller, Avatar.MolangContext context) {
        if (context == null)
            return;
        float maxTime = 0f;
        float maxLength = 0f;
        Set<String> completed = completedOnceByController.getOrDefault(controller.name(), Set.of());
        boolean any = !completed.isEmpty();
        boolean all = true;
        boolean hasAnimation = !completed.isEmpty();
        java.util.List<YsmAnimationPlayer.PlayingAnimation> instances = new java.util.ArrayList<>();
        Set<String> animations = activeByController.get(controller.name());
        if (animations != null) {
            for (String animation : animations) {
                YsmAnimationPlayer.PlayingAnimation playing = player.getControllerAnimation(controller.name(), animation);
                if (playing != null) {
                    instances.add(playing);
                }
            }
        }
        YsmAnimationPlayer.PlayingAnimation function = player.getControllerFunctionAnimation(controller.name());
        if (function != null) {
            instances.add(function);
        }
        for (YsmAnimationPlayer.PlayingAnimation playing : instances) {
            hasAnimation = true;
            maxTime = Math.max(maxTime, playing.time);
            maxLength = Math.max(maxLength, playing.clip.length);
            boolean finished = playing.hasCompletedCycle();
            any |= finished;
            all &= finished;
        }
        context.anim_time = maxTime;
        context.life_time = maxLength;
        context.any_animation_finished = !hasAnimation || any ? 1f : 0f;
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

    public ContextScope openControllerContext(String controllerName, ExpressionEvaluator<?> evaluator) {
        Avatar.MolangContext context = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext value
                ? value : null;
        YsmAnimationController controller = controllerName == null ? null : controllers.get(controllerName);
        if (controller == null && controllerName != null) {
            controller = controllers.values().stream()
                    .filter(definition -> definition.name().equalsIgnoreCase(controllerName))
                    .findFirst().orElse(null);
        }
        ContextScope scope = new ContextScope(context, controllerName == null ? Set.of() : controllerContextFields());
        if (context != null && controller != null) {
            for (YsmAnimationController definition : controllers.values()) {
                for (String state : definition.states().keySet()) {
                    setControllerValue(context, normalizeStateFlag(state), 0f);
                }
            }
            applyControllerClock(controller, context);
            applyControllerContext(controller, context);
            context.is_first_person = controller.slot() == YsmControllerSlot.FIRST_PERSON_ARM ? 1f : 0f;
        }
        return scope;
    }

    public ContextScope openAnimationContext(String controllerName, ExpressionEvaluator<?> evaluator,
                                             float animationTime, float animationLength) {
        ContextScope scope = openControllerContext(controllerName, evaluator);
        if (scope.context != null) {
            scope.context.anim_time = animationTime;
            scope.context.life_time = animationLength;
            if (controllerName != null) {
                setControllerValue(scope.context, "anim_time", animationTime);
                setControllerValue(scope.context, "life_time", animationLength);
            }
        }
        return scope;
    }

    public ContextScope openAnimationContext(String controllerName, ExpressionEvaluator<?> evaluator,
                                             float animationTime, float animationLength, boolean firstPerson) {
        ContextScope scope = openAnimationContext(controllerName, evaluator, animationTime, animationLength);
        if (scope.context != null) {
            scope.context.is_first_person = firstPerson ? 1f : 0f;
        }
        return scope;
    }

    private Set<Integer> controllerContextFields() {
        Set<Integer> fields = new HashSet<>();
        for (String field : List.of("controller", "slot", "current_state", "state_time", "anim_time",
                "life_time", "any_animation_finished", "all_animations_finished")) {
            fields.add(StringPool.computeIfAbsent(field));
        }
        for (YsmAnimationController controller : controllers.values()) {
            for (String state : controller.states().keySet()) {
                fields.add(StringPool.computeIfAbsent(normalizeStateFlag(state)));
            }
        }
        return fields;
    }

    public static final class ContextScope implements AutoCloseable {
        private final Avatar.MolangContext context;
        private final VariableStorage.Scope fields;
        private final float animationTime;
        private final float lifeTime;
        private final float anyFinished;
        private final float allFinished;
        private final float firstPerson;
        private boolean closed;

        private ContextScope(Avatar.MolangContext context, Set<Integer> names) {
            this.context = context;
            fields = context == null ? null : context.controller.preserveScopedValues(names);
            animationTime = context == null ? 0f : context.anim_time;
            lifeTime = context == null ? 0f : context.life_time;
            anyFinished = context == null ? 0f : context.any_animation_finished;
            allFinished = context == null ? 0f : context.all_animations_finished;
            firstPerson = context == null ? 0f : context.is_first_person;
        }

        @Override
        public void close() {
            if (closed || context == null) {
                return;
            }
            closed = true;
            fields.close();
            context.anim_time = animationTime;
            context.life_time = lifeTime;
            context.any_animation_finished = anyFinished;
            context.all_animations_finished = allFinished;
            context.is_first_person = firstPerson;
        }
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
