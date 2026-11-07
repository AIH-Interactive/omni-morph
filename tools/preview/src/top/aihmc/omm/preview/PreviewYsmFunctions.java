package top.aihmc.omm.preview;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import top.aihmc.omnimorph.model.ysm.YsmFunctionEventBus;
import top.aihmc.omnimorph.model.ysm.YsmMolangFunctionName;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlotBinder;
import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluatorImpl;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Struct;
import top.aihmc.omnimorph.molang.storage.StringPool;

/** OMP function/event execution adapted to an instance-owned standalone Molang host. */
public final class PreviewYsmFunctions {
    private static final int MAX_CALL_DEPTH = 32;
    private final PreviewMolang host;
    private final Map<String, YsmFunction> functions = new LinkedHashMap<>();
    private final Map<String, List<YsmFunction>> events = new LinkedHashMap<>();
    private final ArrayDeque<ArgsValue> argumentStack = new ArrayDeque<>();
    private final ArrayDeque<CallFrame> callStack = new ArrayDeque<>();
    private final YsmFunctionEventBus eventBus = new YsmFunctionEventBus();
    private Object eventOwner;
    private boolean clientEffects;
    private final ArrayDeque<String> recentSyncKeys = new ArrayDeque<>();

    /** Executes the original local @sync contract without a Minecraft network transport. */
    Object recordSync(ExecutionContext<?> context, Function.ArgumentCollection args) {
        if (!clientEffectsEnabled() || args.size() > 16)
            return null;
        List<Object> values = new ArrayList<>(args.size());
        for (int index = 0; index < args.size(); index++) {
            float value = args.getAsFloat(context, index);
            if (!Float.isFinite(value))
                return null;
            values.add(value);
        }
        recentSyncKeys.addFirst("sync " + values);
        while (recentSyncKeys.size() > 16) recentSyncKeys.removeLast();
        List<YsmFunction> handlers = events.get("sync");
        if (handlers != null) {
            ArgsValue arguments = new ArgsValue(values);
            for (YsmFunction function : handlers) execute(function, host.evaluator(), arguments, "sync");
        }
        return null;
    }

    List<String> recentSyncKeys() {
        return List.copyOf(recentSyncKeys);
    }

    public EffectScope beginClientEffects(Object owner) {
        return new EffectScope(owner);
    }

    Object clientEffectOwner() {
        return eventOwner;
    }

    boolean clientEffectsEnabled() {
        return clientEffects;
    }

    public final class EffectScope implements AutoCloseable {
        private final Object previousOwner = eventOwner;
        private final boolean previousClientEffects = clientEffects;
        private boolean closed;

        private EffectScope(Object owner) {
            eventOwner = owner;
            clientEffects = true;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                eventOwner = previousOwner;
                clientEffects = previousClientEffects;
            }
        }
    }

    PreviewYsmFunctions(PreviewMolang host, Map<String, String> sources) {
        this.host = host;
        for (var source : sources.entrySet()) {
            try {
                var name = YsmMolangFunctionName.parse(source.getKey());
                var expressions = host.compile(source.getValue());
                var function = new YsmFunction(source.getKey(), name.fileName(), name.functionName(),
                    normalizeName(name.functionName()), name.eventName(), normalizeName(name.eventName()), expressions,
                    functions.size() + events.values().stream().mapToInt(List::size).sum());
                if (!function.name().isBlank())
                    functions.put(function.name(), function);
                if (!function.event().isBlank())
                    events.computeIfAbsent(function.event(), ignored -> new ArrayList<>()).add(function);
            } catch (Exception invalid) {
                throw new IllegalArgumentException("Invalid YSM function: " + source.getKey(), invalid);
            }
        }
        for (var handlers : events.values()) handlers.sort(Comparator.comparingInt(YsmFunction::order));
    }

    Set<String> controllerEventNames() {
        return events.keySet();
    }
    public List<Object> currentArguments() {
        ArgsValue args = argumentStack.peek();
        return args == null ? ArgsValue.EMPTY : args;
    }

    public List<String> recentEvents() {
        return eventBus.recentEvents();
    }

    public void beginControllerFrame() {
        eventBus.beginFrame();
    }

    public void runControllerSlotEvents(
        String controllerName, YsmControllerSlot slot, ExpressionEvaluator<?> evaluator) {
        for (String event : eventBus.eventsForController(controllerName, slot, controllerEventNames()))
            runEvent(evaluator, event, controllerName);
    }

    public void runUnboundControllerEvents(
        ExpressionEvaluator<?> evaluator, Collection<YsmAnimationController> controllers) {
        Set<String> owned = new HashSet<>();
        for (YsmAnimationController controller : controllers) {
            owned.addAll(YsmControllerSlotBinder.eventNamesForController(controller.name(), controller.slot()));
        }
        for (String event : eventBus.unboundEvents(controllerEventNames())) {
            if (!owned.contains(event)) {
                runEvent(evaluator, event, event);
            }
        }
    }

    public Object runEvent(
        String event, ExecutionContext<?> context, Function.ArgumentCollection args, int argumentOffset) {
        String normalized = normalizeName(event);
        List<YsmFunction> values = events.get(normalized);
        if (values == null || values.isEmpty())
            return 0f;
        List<Object> arguments = new ArrayList<>();
        int start = Math.max(0, argumentOffset);
        for (int i = start; i < args.size(); i++) arguments.add(args.getValue(context, i));
        eventBus.markExecuted(normalized, "manual", values.size());
        Object result = 0f;
        ExpressionEvaluator<?> evaluator = host.evaluator();
        ArgsValue eventArgs = new ArgsValue(arguments);
        for (YsmFunction function : values) result = execute(function, evaluator, eventArgs, "manual");
        return result;
    }

    private void runEvent(ExpressionEvaluator<?> evaluator, String event, String slot) {
        String normalized = normalizeName(event);
        List<YsmFunction> values = events.get(normalized);
        if (values == null || values.isEmpty())
            return;
        eventBus.markExecuted(normalized, slot, values.size());
        PreviewMolang context = host;
        boolean changedController = context != null && slot != null && !slot.isBlank();
        var controllerScope = changedController
            ? context.controller.preserveScopedValues(Set.of(StringPool.computeIfAbsent("controller")))
            : null;
        try (controllerScope) {
            if (changedController)
                context.controller.setPath("controller", slot);
            for (YsmFunction function : values) execute(function, evaluator, ArgsValue.EMPTY, slot);
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.US);
    }

    private record YsmFunction(String path, String fileName, String displayName, String name, String eventName,
        String event, List<Expression> expressions, int order) {}

    private record CallFrame(String functionName, String eventName, String slot, String path) {}

    private static class ArgsValue extends AbstractList<Object> implements Struct {
        private static final ArgsValue EMPTY = new ArgsValue(List.of());
        private final List<Object> values;

        private ArgsValue(List<Object> values) {
            this.values = values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
        }

        @Override
        public Object get(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : 0f;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public Object getProperty(int name) {
            String key = StringPool.getString(name);
            if ("size".equals(key) || "length".equals(key))
                return (float) values.size();
            try {
                return get(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
                return 0f;
            }
        }

        @Override
        public void putProperty(int name, Object value) {}

        @Override
        public Struct copy() {
            return new ArgsValue(values);
        }
    }

    public Object call(String name, ExecutionContext<?> context, Function.ArgumentCollection args) {
        String normalized = normalizeName(name);
        YsmFunction function = functions.get(normalized);
        if (function == null)
            throw new IllegalArgumentException("Unknown YSM function: " + name);
        if (callStack.size() >= MAX_CALL_DEPTH)
            throw new IllegalStateException("YSM function recursion limit exceeded");
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) values.add(args.getValue(context, i));
        return execute(function, host.evaluator(), new ArgsValue(values), null);
    }

    private Object execute(YsmFunction function, ExpressionEvaluator<?> evaluator, ArgsValue args, String slot) {
        if (callStack.size() >= MAX_CALL_DEPTH) {
            throw new IllegalStateException("YSM event recursion limit exceeded");
        }
        if (function.expressions().isEmpty() || evaluator == null)
            return 0f;
        boolean tempScope = host.temp.pushScope(args == null ? ArgsValue.EMPTY : args);
        if (!tempScope)
            throw new IllegalStateException("YSM temporary scope limit exceeded");
        argumentStack.push(args == null ? ArgsValue.EMPTY : args);
        callStack.push(new CallFrame(function.displayName(), function.eventName(), slot, function.path()));
        try {
            if (evaluator instanceof ExpressionEvaluatorImpl<?> impl)
                return impl.evalAll(function.expressions(), true);
            Object result = 0f;
            for (Expression expression : function.expressions()) result = evaluator.eval(expression);
            return result;
        } finally {
            callStack.pop();
            argumentStack.pop();
            if (tempScope)
                host.temp.popScope();
        }
    }
}
