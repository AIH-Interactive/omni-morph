package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class YsmSyncDeferSmokeTest {
    private YsmSyncDeferSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path environment = Files.createTempDirectory("figura-events-environment-").toAbsolutePath().normalize();
        String previousDirectory = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", environment.toString());
        try {
            runTests();
        } finally {
            if (previousDirectory == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previousDirectory);
            }
            try (var files = Files.walk(environment)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(environment)) {
                        throw new IllegalStateException("Unexpected fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void runTests() throws Exception {
        Fixture fixture = new Fixture();
        Object owner = new Object();
        fixture.context.variables.setPath("label", "first");
        fixture.eval("sync(1,2); queue('layer','ignored',0);");
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        require(fixture.trace.isEmpty(), "ordinary pose evaluation cannot emit client events");
        try (var scope = fixture.functions.beginClientEffects(owner)) {
            fixture.eval("queue('layer',v.label,1); sync(3,2); queue('layer','second',2);");
        }
        require(fixture.trace.equals(List.of("sync:3.0:2.0")),
                "sync immediately invokes @sync with numeric arguments: " + fixture.trace);
        fixture.context.variables.setPath("label", "changed");
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        require(fixture.trace.equals(List.of("sync:3.0:2.0", "defer:second:2.0", "defer:first:1.0")),
                "@defer consumes evaluated arguments in reverse capture order exactly once: " + fixture.trace);
        try (var scope = fixture.functions.beginClientEffects(owner)) {
            fixture.eval("queue(1,'ignored',0); queue('','ignored',0);");
        }
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        require(fixture.trace.size() == 3, "non-string and empty defer addresses do not capture");
        require(fixture.functions.currentArguments().isEmpty() && fixture.functions.callStackDebugNames().isEmpty(),
                "special events release their argument and call scopes");
        Object secondOwner = new Object();
        try (var outer = fixture.functions.beginClientEffects(owner)) {
            fixture.eval("queue('layer','outer',3);");
            try (var inner = fixture.functions.beginClientEffects(secondOwner)) {
                fixture.eval("queue('layer','inner',4);");
            }
            fixture.eval("queue('layer','outer.last',5);");
        }
        int count = fixture.trace.size();
        fixture.functions.flushDeferred(secondOwner, fixture.evaluator);
        require(fixture.trace.size() == count + 1 && fixture.trace.getLast().equals("defer:inner:4.0"),
                "one animation owner cannot consume another owner's queue");
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        require(fixture.trace.subList(fixture.trace.size() - 2, fixture.trace.size())
                .equals(List.of("defer:outer.last:5.0", "defer:outer:3.0")), "nested effect scope restores outer owner");
        Fixture other = new Fixture();
        try (var scope = other.functions.beginClientEffects(owner)) {
            other.eval("queue('layer','other.model',6);");
        }
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        require(other.trace.isEmpty(), "different model runtimes do not share event storage");
        other.functions.flushDeferred(owner, other.evaluator);
        require(other.trace.equals(List.of("defer:other.model:6.0")), "second model retains its own captured arguments");
        try (var scope = other.functions.beginClientEffects(owner)) {
            other.eval("queue('layer','failed.frame',7);");
        }
        other.functions.discardDeferredCalls();
        other.functions.flushDeferred(owner, other.evaluator);
        require(other.trace.size() == 1, "failed-frame cleanup prevents stale deferred replay");
        testBudgetsAndUnload();
        Function syncBinding = (Function) new MolangBindings.YsmBinding().getProperty("sync");
        require(syncBinding.validateArgumentSize(0) && syncBinding.validateArgumentSize(16)
                && !syncBinding.validateArgumentSize(17), "production sync binding validates the reference 16-argument limit");
        require(new MolangBindings.YsmBinding().getProperty("defer") instanceof Function, "production defer binding is registered");
        System.out.println("M4 local sync/defer ordering, typed capture, owners, replay cleanup and bounded events passed.");
    }

    private static void testBudgetsAndUnload() throws Exception {
        Fixture fixture = new Fixture();
        Object owner = new Object();
        Function.ArgumentCollection arguments = new Function.ArgumentCollection(fixture.engine.parse("'layer';1;"));
        try (var scope = fixture.functions.beginClientEffects(owner)) {
            for (int index = 0; index < 4096; index++) {
                fixture.functions.captureDefer(ExpressionEvaluator.evaluator(fixture.context), arguments);
            }
            boolean rejected = false;
            try {
                fixture.functions.captureDefer(fixture.evaluator, arguments);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            require(rejected, "deferred capture count is bounded");
            fixture.functions.clearEventState();
        }
        fixture.functions.flushDeferred(owner, fixture.evaluator);
        fixture.eval("sync(8,9);");
        require(fixture.trace.isEmpty(), "unload clears queued arguments and invalidates surrounding effect scopes");
        fixture.install("sync", "sync(arg(0));");
        try (var scope = fixture.functions.beginClientEffects(owner)) {
            fixture.eval("sync(1);");
        }
        require(fixture.functions.callStackDebugNames().isEmpty() && fixture.functions.currentArguments().isEmpty(),
                "recursive special event is bounded and restores stacks");
        fixture.install("sync", "mark('sync',arg(0),arg(1));");
        try (var scope = fixture.functions.beginClientEffects(owner)) {
            fixture.eval("sync(4,5);");
        }
        require(fixture.trace.getLast().equals("sync:4.0:5.0"), "recursion failure does not damage the next event");
    }

    private static final class Fixture {
        private final Avatar.MolangContext context = new Avatar.MolangContext(null);
        private final ExpressionEvaluator<?> evaluator = ExpressionEvaluator.evaluator(context);
        private final List<String> trace = new ArrayList<>();
        private final YsmMolangFunctionRuntime functions;
        private final MolangEngine engine;
        private final ObjectBinding bindings;

        private Fixture() throws Exception {
            Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            YsmModelRuntime runtime = constructor.newInstance(null, new YsmGeometry(), null, null,
                    "fixture", false, "NEW", "events-fixture", Map.of(), 1f, 1f, true);
            functions = runtime.functions();
            Function sync = functions::recordSync;
            Function queue = functions::captureDefer;
            Function argument = (execution, values) -> {
                int index = values.getAsInt(execution, 0);
                return functions.currentArguments().get(index);
            };
            Function mark = (execution, values) -> {
                trace.add(values.getAsString(execution, 0) + ":" + display(values.getValue(execution, 1))
                        + ":" + display(values.getValue(execution, 2)));
                return 0f;
            };
            bindings = name -> switch (name) {
                case "v" -> context.variables;
                case "sync" -> sync;
                case "queue" -> queue;
                case "arg" -> argument;
                case "mark" -> mark;
                default -> null;
            };
            engine = MolangEngine.fromCustomBinding(bindings);
            install("sync", "mark('sync',arg(0),arg(1));");
            install("defer", "mark('defer',arg(0),arg(1)); queue('layer','discarded',9);");
        }

        private void eval(String source) throws Exception {
            for (var expression : engine.parse(source)) {
                evaluator.eval(expression);
            }
        }

        @SuppressWarnings("unchecked")
        private void install(String event, String source) throws Exception {
            // Assemble parser-produced DTOs into the real runtime without a Minecraft Avatar binding registry.
            Field field = YsmMolangFunctionRuntime.class.getDeclaredField("events");
            field.setAccessible(true);
            Map<String, List<Object>> events = (Map<String, List<Object>>) field.get(functions);
            Class<?> type = Class.forName(YsmMolangFunctionRuntime.class.getName() + "$YsmFunction");
            Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class, String.class,
                    String.class, String.class, String.class, List.class, int.class);
            constructor.setAccessible(true);
            String path = "functions/@" + event + ".molang";
            var parsed = YsmMolangFunctionParser.parse(path, source, bindings);
            events.put(event, List.of(constructor.newInstance(path, parsed.name().fileName(), "", "", event, event,
                    parsed.expressions(), 0)));
        }
    }

    private static String display(Object value) {
        String string = ValueConversions.asString(value);
        return string == null ? String.valueOf(value) : string;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
