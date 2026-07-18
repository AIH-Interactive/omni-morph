package org.figuramc.figura.model.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExecutionContext;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.ExpressionEvaluatorImpl;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Struct;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlot;

import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class YsmMolangFunctionRuntime {
    private static final int MAX_CALL_DEPTH = 32;

    private final YsmModelRuntime runtime;
    private final Map<String, YsmFunction> functions = new LinkedHashMap<>();
    private final Map<String, List<YsmFunction>> events = new LinkedHashMap<>();
    private final ArrayDeque<ArgsValue> argumentStack = new ArrayDeque<>();
    private final ArrayDeque<CallFrame> callStack = new ArrayDeque<>();
    private final YsmFunctionEventBus eventBus = new YsmFunctionEventBus();
    private final Map<String, Object> syncValues = new LinkedHashMap<>();
    private final ArrayDeque<String> recentSyncKeys = new ArrayDeque<>();

    public YsmMolangFunctionRuntime(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public void load(Iterable<Tag> tags) {
        if (tags == null)
            return;
        int loaded = 0;
        for (Tag tag : tags) {
            if (!(tag instanceof CompoundTag functionTag))
                continue;
            String path = functionTag.getStringOr("path", "");
            String source = new String(functionTag.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            if (register(path, source))
                loaded++;
        }
        for (List<YsmFunction> values : events.values())
            values.sort(Comparator.comparingInt(YsmFunction::order));
        FiguraMod.debug("YSM functions loaded for {}: functions={}, events={}, files={}",
                runtime.getModelKey(), functions.size(), events.size(), loaded);
        if (FiguraMod.debugModeEnabled()) {
            FiguraMod.debug("YSM function names for {}: {}", runtime.getModelKey(), functionDebugNames());
            FiguraMod.debug("YSM event bindings for {}: {}", runtime.getModelKey(), eventDebugNames());
        }
    }

    public List<Object> currentArguments() {
        ArgsValue args = argumentStack.peek();
        return args == null ? ArgsValue.EMPTY : args;
    }

    public int functionCount() {
        return functions.size();
    }

    public int eventCount() {
        return events.size();
    }

    public List<String> functionDebugNames() {
        return functions.values().stream()
                .map(function -> function.displayName().isBlank() ? function.fileName() : function.displayName())
                .toList();
    }

    public Map<String, List<String>> eventDebugNames() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<YsmFunction>> entry : events.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                    .map(function -> function.displayName().isBlank() ? function.fileName() : function.displayName())
                    .toList());
        }
        return result;
    }

    public List<String> recentEvents() {
        return eventBus.recentEvents();
    }

    public List<String> recentSyncKeys() {
        return List.copyOf(recentSyncKeys);
    }

    public List<String> callStackDebugNames() {
        return callStack.stream()
                .map(frame -> {
                    String name = frame.functionName() == null || frame.functionName().isBlank() ? "<event>" : frame.functionName();
                    String event = frame.eventName() == null || frame.eventName().isBlank() ? "" : " event=" + frame.eventName();
                    String slot = frame.slot() == null || frame.slot().isBlank() ? "" : " slot=" + frame.slot();
                    return name + event + slot;
                })
                .toList();
    }

    public Object recordSync(ExecutionContext<?> context, Function.ArgumentCollection args) {
        if (args == null || args.size() == 0)
            return 0f;
        String key = args.getAsString(context, 0);
        if (key == null || key.isBlank())
            return 0f;
        String normalized = normalizeName(key);
        if (args.size() == 1)
            return syncValues.getOrDefault(normalized, 0f);
        Object value = args.size() > 1 ? args.getValue(context, 1) : 1f;
        syncValues.put(normalized, value);
        String display = key + "=" + value;
        recentSyncKeys.removeIf(entry -> entry.equals(key) || entry.startsWith(key + "="));
        recentSyncKeys.addFirst(display);
        while (recentSyncKeys.size() > 16)
            recentSyncKeys.removeLast();
        return value;
    }

    public Object call(String name, ExecutionContext<?> context, Function.ArgumentCollection args) {
        String normalized = normalizeName(name);
        YsmFunction function = functions.get(normalized);
        if (function == null) {
            FiguraMod.debug("Unknown YSM Molang function {} in {}", name, runtime.getModelKey());
            return 0f;
        }
        if (callStack.size() >= MAX_CALL_DEPTH) {
            FiguraMod.LOGGER.warn("YSM Molang function recursion limit reached in {} while calling {}", runtime.getModelKey(), function.displayName());
            return 0f;
        }
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < args.size(); i++)
            values.add(args.getValue(context, i));
        return execute(function, evaluator(context), new ArgsValue(values), null);
    }

    public void beginControllerFrame() {
        eventBus.beginFrame();
    }

    public void runControllerSlotEvents(String controllerName, YsmControllerSlot slot, ExpressionEvaluator<?> evaluator) {
        for (String event : eventBus.eventsForController(controllerName, slot, events.keySet()))
            runEvent(evaluator, event, controllerName);
    }

    public void runUnboundControllerEvents(ExpressionEvaluator<?> evaluator) {
        for (String event : eventBus.unboundEvents(events.keySet()))
            runEvent(evaluator, event, event);
    }

    public Object runEvent(String event, ExecutionContext<?> context, Function.ArgumentCollection args) {
        String normalized = normalizeName(event);
        List<YsmFunction> values = events.get(normalized);
        if (values == null || values.isEmpty())
            return 0f;
        List<Object> arguments = new ArrayList<>();
        for (int i = 0; i < args.size(); i++)
            arguments.add(args.getValue(context, i));
        eventBus.markExecuted(normalized, "manual", values.size());
        Object result = 0f;
        ExpressionEvaluator<?> evaluator = evaluator(context);
        ArgsValue eventArgs = new ArgsValue(arguments);
        for (YsmFunction function : values)
            result = execute(function, evaluator, eventArgs, "manual");
        return result;
    }

    private void runEvent(ExpressionEvaluator<?> evaluator, String event, String slot) {
        String normalized = normalizeName(event);
        List<YsmFunction> values = events.get(normalized);
        if (values == null || values.isEmpty())
            return;
        eventBus.markExecuted(normalized, slot, values.size());
        Avatar.MolangContext context = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext molangContext ? molangContext : null;
        Object previousController = context == null ? null : context.controller.getPath("controller");
        boolean changedController = context != null && slot != null && !slot.isBlank();
        if (changedController)
            context.controller.setPath("controller", slot);
        try {
            for (YsmFunction function : values)
                execute(function, evaluator, ArgsValue.EMPTY, slot);
        } finally {
            if (changedController) {
                if (previousController == null)
                    context.controller.setPath("controller", 0f);
                else
                    context.controller.setPath("controller", previousController);
            }
        }
    }

    private Object execute(YsmFunction function, ExpressionEvaluator<?> evaluator, ArgsValue args, String slot) {
        if (function.expressions().isEmpty() || evaluator == null)
            return 0f;
        Avatar.MolangContext molangContext = evaluator.entity() instanceof Avatar.MolangContext context ? context : null;
        boolean tempScope = molangContext != null && molangContext.temp.pushScope(args == null ? ArgsValue.EMPTY : args);
        argumentStack.push(args == null ? ArgsValue.EMPTY : args);
        callStack.push(new CallFrame(function.displayName(), function.eventName(), slot));
        try {
            if (evaluator instanceof ExpressionEvaluatorImpl<?> impl)
                return impl.evalAll(function.expressions(), true);
            Object result = 0f;
            for (Expression expression : function.expressions())
                result = evaluator.eval(expression);
            return result;
        } catch (Exception e) {
            FiguraMod.LOGGER.debug("Failed to execute YSM Molang function {} path={} event={} model={}",
                    function.displayName(), function.path(), function.eventName(), runtime.getModelKey(), e);
            return 0f;
        } finally {
            callStack.pop();
            argumentStack.pop();
            if (tempScope)
                molangContext.temp.popScope();
        }
    }

    private ExpressionEvaluator<?> evaluator(ExecutionContext<?> context) {
        if (context instanceof ExpressionEvaluator<?> evaluator)
            return evaluator;
        Avatar.MolangContext molangContext = runtime.owner().getMolangContext();
        return molangContext != null ? ExpressionEvaluator.evaluator(molangContext) : ExpressionEvaluator.evaluator(runtime.owner());
    }

    private boolean register(String path, String source) {
        if (source == null || source.isBlank()) {
            FiguraMod.LOGGER.debug("Skipping empty YSM Molang function {}", path);
            return false;
        }
        YsmMolangFunctionName name = YsmMolangFunctionName.parse(path);
        try {
            YsmMolangFunctionParser.ParsedFunction parsed = YsmMolangFunctionParser.parse(path, source, runtime.owner().getAvatarBindings());
            List<Expression> expressions = parsed.expressions();
            name = parsed.name();
            YsmFunction function = new YsmFunction(path, name.fileName(), name.functionName(), normalizeName(name.functionName()),
                    name.eventName(), normalizeName(name.eventName()), expressions, functions.size() + events.values().stream().mapToInt(List::size).sum());
            if (!function.name().isBlank()) {
                YsmFunction previous = functions.put(function.name(), function);
                if (previous != null)
                    FiguraMod.LOGGER.debug("YSM Molang function {} from {} overrides {}", function.displayName(), path, previous.path());
            }
            if (!function.event().isBlank())
                events.computeIfAbsent(function.event(), ignored -> new ArrayList<>()).add(function);
            if (name.malformed())
                FiguraMod.LOGGER.debug("Loaded malformed YSM Molang function name {} as function={} event={}", path, name.functionName(), name.eventName());
            return true;
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to parse YSM Molang function {} function={} event={} model={}",
                    path, name.functionName(), name.eventName(), runtime.getModelKey(), e);
            return false;
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.US);
    }

    private record YsmFunction(
            String path,
            String fileName,
            String displayName,
            String name,
            String eventName,
            String event,
            List<Expression> expressions,
            int order
    ) {
    }

    private record CallFrame(String functionName, String eventName, String slot) {
    }

    private static class ArgsValue extends AbstractList<Object> implements Struct {
        private static final ArgsValue EMPTY = new ArgsValue(List.of());
        private final List<Object> values;

        private ArgsValue(List<Object> values) {
            this.values = values == null ? List.of() : List.copyOf(values);
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
        public void putProperty(int name, Object value) {
        }

        @Override
        public Struct copy() {
            return new ArgsValue(values);
        }
    }
}
