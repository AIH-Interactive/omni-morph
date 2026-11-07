package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.parser.ParseException;
import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.AssignableVariable;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;
import top.aihmc.omnimorph.molang.runtime.binding.PreviewMathBinding;
import top.aihmc.omnimorph.molang.runtime.binding.StandardBindings;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;

/** Real OMP Molang parser/evaluator with per-instance simulated host state and seeded randomness. */
public final class PreviewMolang implements MolangExecutionBudget.Owner {
    public final top.aihmc.omnimorph.model.ysm.YsmFilterRuntime filters =
        new top.aihmc.omnimorph.model.ysm.YsmFilterRuntime();
    PreviewYsmEffects effects;
    PreviewYsmFunctions functions;
    final PreviewYsmEquipment equipment;
    final PreviewBlockQueries blockQueries;
    PreviewControllerPlayer controllers;
    PreviewYsmBones bones;
    final PreviewYsmInput input;
    final top.aihmc.omnimorph.molang.storage.TempVariableStorage temp =
        new top.aihmc.omnimorph.molang.storage.TempVariableStorage();
    private final MolangExecutionBudget budget = new MolangExecutionBudget();
    public final top.aihmc.omnimorph.molang.storage.VariableStorage variables =
        new top.aihmc.omnimorph.molang.storage.VariableStorage();
    private final Map<String, Object> queries = new HashMap<>();
    private final Map<String, Object> ysmValues = new HashMap<>();
    private final Random random;
    private final MolangEngine engine;
    private final ExpressionEvaluator<PreviewMolang> evaluator = ExpressionEvaluator.evaluator(this);
    public final top.aihmc.omnimorph.molang.storage.VariableStorage controller =
        new top.aihmc.omnimorph.molang.storage.VariableStorage();
    public float anim_time;
    public float life_time;
    public float any_animation_finished;
    public float all_animations_finished;
    public float is_first_person;
    private float delta;

    public PreviewMolang(JsonObject states, long seed) {
        input = new PreviewYsmInput(states);
        equipment = new PreviewYsmEquipment(states);
        blockQueries = new PreviewBlockQueries(states);
        random = new Random(seed);
        boolean running = !states.has("running") || states.get("running").getAsBoolean();
        queries.put("is_running", running);
        queries.put("running", running);
        if (states.has("query")) {
            for (var entry : states.getAsJsonObject("query").entrySet()) {
                queries.put(entry.getKey().toLowerCase(Locale.ROOT), primitive(entry.getValue()));
            }
        }
        if (states.has("ysm")) {
            for (var entry : states.getAsJsonObject("ysm").entrySet()) {
                ysmValues.put(entry.getKey().toLowerCase(Locale.ROOT), primitive(entry.getValue()));
            }
        }
        if (states.has("ctrl")) {
            for (var entry : states.getAsJsonObject("ctrl").entrySet()) {
                controller.setScoped(top.aihmc.omnimorph.molang.storage.StringPool.computeIfAbsent(
                                         entry.getKey().toLowerCase(Locale.ROOT)),
                    primitive(entry.getValue()));
            }
        }
        ObjectBinding math = new PreviewMathBinding();
        ObjectBinding query = name
            -> name.equals("relative_block_has_any_tag")
            ? PreviewBlockQueries.relativeBlockHasAnyTag()
            : (Variable) context -> ((PreviewMolang) context.entity()).query(name);
        ObjectBinding ysm = name -> switch (name) {
            case "keyboard" -> PreviewYsmInput.binding(false);
            case "mouse" -> PreviewYsmInput.binding(true);
            case "particle" -> PreviewYsmParticles.binding(false);
            case "abs_particle" -> PreviewYsmParticles.binding(true);
            case "bone_pos" -> PreviewYsmBones.binding(PreviewYsmBones.Channel.POSITION);
            case "bone_rot" -> PreviewYsmBones.binding(PreviewYsmBones.Channel.ROTATION);
            case "bone_scale" -> PreviewYsmBones.binding(PreviewYsmBones.Channel.SCALE);
            case "bone_pivot_abs" -> PreviewYsmBones.binding(PreviewYsmBones.Channel.PIVOT_ABS);
            case "sync" ->
                new Function() {
                    @Override
                    public Object evaluate(ExecutionContext<?> context, ArgumentCollection args) {
                        return ((PreviewMolang) context.entity()).functions.recordSync(context, args);
                    }

                    @Override
                    public boolean validateArgumentSize(int size) {
                        return size <= 16;
                    }
                };
            case "first_order" -> filterFunction(false);
            case "second_order" -> filterFunction(true);
            case "play_sound" -> soundFunction(name, 2, 5);
            case "stop_sound" -> soundFunction(name, 1, 2);
            case "stop_all_sounds" -> soundFunction(name, 0, 1);
            case "event", "run_event", "trigger_event" ->
                (Function) (context, args) -> {
                    if (args.size() == 0)
                        return 0f;
                    String event = args.getAsString(context, 0);
                    if (event == null || event.isBlank())
                        return 0f;
                    return ((PreviewMolang) context.entity()).functions.runEvent(event, context, args, 1);
                };
            default -> (Variable) context -> ((PreviewMolang) context.entity()).ysm(name);
        };
        ObjectBinding functionBinding = name
            -> (Function) (context, args) -> ((PreviewMolang) context.entity()).functions.call(name, context, args);
        ObjectBinding persistent = name -> variable(name, false);
        ObjectBinding transientVariables = name -> variable(name, true);
        engine = MolangEngine.fromCustomBinding(name -> switch (name.toLowerCase(Locale.ROOT)) {
            case "math" -> math;
            case "q", "query" -> query;
            case "ysm" -> ysm;
            case "fn" -> functionBinding;
            case "args" -> (Variable) context -> ((PreviewMolang) context.entity()).functions.currentArguments();
            case "ctrl", "controller", "c", "context" -> controllerBinding();
            case "v", "variable" -> persistent;
            case "t", "temp" -> transientVariables;
            case "loop" -> StandardBindings.LOOP_FUNC;
            case "for_each" -> StandardBindings.FOR_EACH_FUNC;
            default -> null;
        });
        functions = new PreviewYsmFunctions(this, Map.of());
    }

    public List<Expression> compile(String source) throws ParseException {
        return engine.parse(source);
    }

    private static Function soundFunction(String operation, int minimum, int maximum) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection arguments) {
                PreviewMolang host = (PreviewMolang) execution.entity();
                if (host.effects == null) {
                    throw new UnsupportedOperationException("YSM sound functions require a loaded model audio host");
                }
                return host.effects.soundFunction(execution, arguments, operation);
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size >= minimum && size <= maximum;
            }
        };
    }

    private static Function filterFunction(boolean secondOrder) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                PreviewMolang host = (PreviewMolang) execution.entity();
                String name = args.getAsString(execution, 0);
                if (name == null || name.isEmpty())
                    return 0f;
                var domain = top.aihmc.omnimorph.model.ysm.YsmFilterRuntime.Domain.PREVIEW;
                float input = args.getAsFloat(execution, 1);
                float first = args.size() >= 3 ? args.getAsFloat(execution, 2) : 1f;
                if (!secondOrder)
                    return host.filters.firstOrder(domain, name, input, first);
                float damping = args.size() >= 4 ? args.getAsFloat(execution, 3) : 1f;
                float response = args.size() >= 5 ? args.getAsFloat(execution, 4) : 1f;
                return host.filters.secondOrder(domain, name, input, first, damping, response);
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size >= 2;
            }
        };
    }

    public MolangEngine engine() {
        return engine;
    }

    public ExpressionEvaluator<PreviewMolang> evaluator() {
        return evaluator;
    }

    public MolangExecutionBudget.Scope beginFrame(long identity, float time, float delta) {
        clock(time, delta);
        temp.clearFrame();
        return budget.beginFrame(identity);
    }

    public float evaluate(List<Expression> expressions) {
        temp.clearFrame();
        try (var scope = budget.begin()) {
            return ValueConversions.asFloat(evaluator.evalAll(expressions, true));
        }
    }

    public void clock(float time, float delta) {
        this.anim_time = time;
        this.life_time = time;
        this.delta = delta;
    }

    public void lifeTime(float time) {
        life_time = time;
    }

    void stepClock(float time, float delta) {
        clock(time, delta);
        temp.clearFrame();
    }

    public Random random() {
        return random;
    }

    @Override
    public MolangExecutionBudget executionBudget() {
        return budget;
    }

    private Object query(String name) {
        return switch (name) {
            case "anim_time" -> anim_time;
            case "life_time" -> life_time;
            case "any_animation_finished" -> any_animation_finished;
            case "all_animations_finished" -> all_animations_finished;
            case "is_first_person" -> is_first_person;
            case "delta_time" -> delta;
            default -> {
                if (!queries.containsKey(name)) {
                    throw new UnsupportedOperationException(
                        "Molang query." + name + " needs a simulated value in states.query." + name);
                }
                yield queries.get(name);
            }
        };
    }

    private Object ysm(String name) {
        if (!ysmValues.containsKey(name)) {
            throw new UnsupportedOperationException(
                "Molang ysm." + name + " needs a simulated value in states.ysm." + name);
        }
        return ysmValues.get(name);
    }

    private static ObjectBinding controllerBinding() {
        return name -> {
            switch (name) {
                case "loop":
                    return (Variable) context -> 10f;
                case "state_continue":
                    return (Variable) context -> 2f;
                case "state_break", "state_stop":
                    return (Variable) context -> 3f;
                case "state_pause":
                    return (Variable) context -> 4f;
                case "state_bypass":
                    return (Variable) context -> 5f;
                case "play_once":
                    return (Variable) context -> 11f;
                case "hold_on_last_frame":
                    return (Variable) context -> 12f;
                case "set_animation", "reset", "set_beginning_transition_length", "indicate_reload":
                    return PreviewYsmControllerFunctions.binding(name);
                default:
                    break;
            }
            if (java.util.Set.of("use", "hold", "swing", "armor").contains(name)) {
                return PreviewYsmEquipment.binding(name);
            }
            return new AssignableVariable() {
                @Override
                public Object evaluate(ExecutionContext<?> context) {
                    PreviewMolang host = (PreviewMolang) context.entity();
                    return ((Variable) host.controller.getProperty(name)).evaluate(context);
                }

                @Override
                public void assign(ExecutionContext<?> context, Object value) {
                    PreviewMolang host = (PreviewMolang) context.entity();
                    ((AssignableVariable) host.controller.getProperty(name)).assign(context, value);
                }
            };
        };
    }

    private static AssignableVariable variable(String name, boolean temporary) {
        return new AssignableVariable() {
            @Override
            public Object evaluate(ExecutionContext<?> context) {
                PreviewMolang host = (PreviewMolang) context.entity();
                return temporary ? ((Variable) host.temp.getProperty(name)).evaluate(context)
                                 : ((Variable) host.variables.getProperty(name)).evaluate(context);
            }

            @Override
            public void assign(ExecutionContext<?> context, Object value) {
                PreviewMolang host = (PreviewMolang) context.entity();
                if (temporary) {
                    ((AssignableVariable) host.temp.getProperty(name)).assign(context, value);
                } else {
                    ((AssignableVariable) host.variables.getProperty(name)).assign(context, value);
                }
            }
        };
    }

    private static Object primitive(JsonElement value) {
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("states.query values must be booleans, numbers or strings");
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }
}
