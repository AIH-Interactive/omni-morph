package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import net.minecraft.util.RandomSource;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class YsmParticleDispatchSmokeTest {
    public static void main(String[] args) throws Exception {
        ArrayDeque<Runnable> executor = new ArrayDeque<>();
        List<String> trace = new ArrayList<>();
        var first = new YsmParticleRuntime.DispatchQueue(2);
        var second = new YsmParticleRuntime.DispatchQueue(2);
        AtomicBoolean worldCurrent = new AtomicBoolean(true);
        first.submit(executor::add, worldCurrent::get, () -> trace.add("first"));
        second.submit(executor::add, () -> true, () -> trace.add("second"));
        first.close();
        drain(executor);
        require(trace.equals(List.of("second")), "unload cancels only the owning model's queued particles");
        require(!first.active() && second.active(), "model lifecycle isolation");
        first.submit(executor::add, () -> true, () -> trace.add("closed"));
        require(executor.isEmpty(), "closed runtime cannot enqueue more particles");
        second.submit(executor::add, worldCurrent::get, () -> trace.add("old-world"));
        worldCurrent.set(false);
        drain(executor);
        require(trace.equals(List.of("second")), "world/model validity is checked when the client task runs");
        worldCurrent.set(true);
        second.submit(executor::add, () -> true, () -> trace.add("old-generation"));
        second.discardPending();
        second.submit(executor::add, () -> true, () -> trace.add("new-generation"));
        drain(executor);
        require(trace.equals(List.of("second", "new-generation")), "failure discard permits later valid work");

        second.submit(executor::add, () -> true, () -> trace.add("a"));
        second.submit(executor::add, () -> true, () -> trace.add("b"));
        try {
            second.submit(executor::add, () -> true, () -> trace.add("overflow"));
            throw new AssertionError("pending task budget must be enforced");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("budget"), "pending budget diagnosis");
        }
        drain(executor);
        require(trace.subList(2, trace.size()).equals(List.of("a", "b")), "accepted tasks retain FIFO executor order");
        try {
            second.submit(command -> { throw new IllegalStateException("executor rejected"); }, () -> true, () -> {});
            throw new AssertionError("executor failure must propagate");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("executor rejected"), "executor failure preserved");
        }
        second.submit(Runnable::run, () -> true, () -> trace.add("inline"));
        second.submit(executor::add, () -> true, () -> { throw new IllegalArgumentException("failed action"); });
        try {
            drain(executor);
            throw new AssertionError("task failure must propagate");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().equals("failed action"), "action failure preserved");
        }
        second.submit(executor::add, () -> true, () -> trace.add("recovered"));
        drain(executor);
        require(trace.getLast().equals("recovered"), "task failure releases its pending allowance");

        require(YsmParticleRuntime.isPreview(EntityRenderMode.FIGURA_GUI)
                && YsmParticleRuntime.isPreview(EntityRenderMode.PAPERDOLL)
                && YsmParticleRuntime.isPreview(EntityRenderMode.MINECRAFT_GUI), "actual GUI modes suppress particles");
        require(!YsmParticleRuntime.isPreview(EntityRenderMode.RENDER)
                && !YsmParticleRuntime.isPreview(EntityRenderMode.FIRST_PERSON)
                && !YsmParticleRuntime.isPreview(null), "ordinary body/arm rendering is not mistaken for preview");
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        MolangBindings bindings = new MolangBindings();
        bindings.register("v", context.variables);
        MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
        for (String name : List.of("particle", "abs_particle")) {
            Function function = (Function) new MolangBindings.YsmBinding().getProperty(name);
            require(!function.validateArgumentSize(0) && function.validateArgumentSize(1)
                    && function.validateArgumentSize(10) && function.validateArgumentSize(11), "reference particle signature");
            Object result = ExpressionEvaluator.evaluator(context).eval(engine.parse("ysm." + name + "(v.denied=1);").getFirst());
            require(result == null && context.variables.getPath("denied") == null,
                    "formal binding rejects missing owner before argument evaluation");
        }
        testRandomAndPrivateFailures();
        System.out.println("M4 particle dispatch lifecycle, queue budgets, world/model guards, failure recovery and formal binding gates passed.");
    }

    private static void testRandomAndPrivateFailures() throws Exception {
        Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                String.class, String.class, Map.class, float.class, float.class, boolean.class);
        constructor.setAccessible(true);
        YsmModelRuntime first = constructor.newInstance(null, new YsmGeometry(), null, null,
                "fixture", false, "NEW", "particle-fixture", Map.of(), 1f, 1f, true);
        YsmModelRuntime second = constructor.newInstance(null, new YsmGeometry(), null, null,
                "fixture", false, "NEW", "particle-fixture", Map.of(), 1f, 1f, true);
        Field randomField = YsmParticleRuntime.class.getDeclaredField("random");
        randomField.setAccessible(true);
        RandomSource firstRandom = (RandomSource) randomField.get(first.particles());
        RandomSource secondRandom = (RandomSource) randomField.get(second.particles());
        require(firstRandom != secondRandom, "same-resource model instances own distinct particle random sources");
        firstRandom.setSeed(20261013L);
        secondRandom.setSeed(20261013L);
        double expectedFirst = firstRandom.nextGaussian();
        firstRandom.nextGaussian();
        require(Double.doubleToLongBits(expectedFirst) == Double.doubleToLongBits(secondRandom.nextGaussian()),
                "one model's emission does not advance the other's random source");
        Method sanitize = YsmParticleRuntime.class.getDeclaredMethod("sanitizeFailure", RuntimeException.class);
        sanitize.setAccessible(true);
        RuntimeException privateFailure = (RuntimeException) sanitize.invoke(first.particles(),
                new IllegalArgumentException("fixture decoded resource ID"));
        require(privateFailure.getMessage().equals("Encrypted YSM operation failed: runtime/particle")
                && privateFailure.getCause() == null, "encrypted engine error does not expose decoded cause/message");
        MolangExecutionBudget.Exceeded exhausted = new MolangExecutionBudget.Exceeded();
        require(sanitize.invoke(first.particles(), exhausted) == exhausted,
                "privacy sanitation cannot swallow or replace shared-budget exhaustion");
        require(!first.functions().clientEffectsEnabled(), "sampling begins without client-side effects");
        try (var effects = first.functions().beginClientEffects(new Object())) {
            require(first.functions().clientEffectsEnabled() && !second.functions().clientEffectsEnabled(),
                    "client-side event gate is per model");
        }
        require(!first.functions().clientEffectsEnabled(), "client event scope restores sampling gate");
        first.particles().close();
        second.particles().close();
    }

    private static void drain(ArrayDeque<Runnable> executor) {
        while (!executor.isEmpty()) {
            executor.removeFirst().run();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
