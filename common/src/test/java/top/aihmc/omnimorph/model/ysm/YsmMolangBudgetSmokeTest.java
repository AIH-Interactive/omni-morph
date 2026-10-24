package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.parser.ast.FloatExpression;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluatorImpl;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.StandardBindings;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;
import top.aihmc.omnimorph.molang.storage.VariableStorage;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class YsmMolangBudgetSmokeTest {
    private YsmMolangBudgetSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        testLoopsAndRecovery();
        testNestedContext();
        testFrameAndAvatarIsolation();
        testDefaultFrameWorkload();
        System.out.println("M4 shared Molang execution budget, loop cleanup, context/frame/Avatar isolation and recovery passed.");
    }

    private static void testLoopsAndRecovery() throws Exception {
        Fixture fixture = new Fixture(128);
        expectExceeded(() -> fixture.evaluate("loop(1024,{loop(1024,{v.count=v.count+1;});});"),
                "nested loops share one total budget");
        fixture.context.variables.setPath("count", 0f);
        fixture.evaluate("loop(3,{v.count=v.count+1; continue; v.count=999;});");
        equal(3f, ValueConversions.asFloat(fixture.context.variables.getPath("count")),
                "loop nesting/continue state is restored after exhaustion");
        equal(7f, ValueConversions.asFloat(fixture.evaluate("loop(8,{return 7;});99;")), "loop return escapes once");
        equal(9f, ValueConversions.asFloat(fixture.evaluate("9;")), "next evaluation is not stuck in return state");
        expectExceeded(() -> fixture.evaluate("for_each(v.item,items,{});"), "empty for_each over unbounded iterable is bounded");
        expectExceeded(() -> fixture.evaluate("loop(1024,{});"), "empty loop still consumes execution steps");
        var expensive = fixture.engine.parse("loop(1024,{});");
        expectExceeded(() -> fixture.evaluator.evalSafe(expensive.getFirst()), "evalSafe cannot hide budget exhaustion");
        expectExceeded(() -> fixture.evaluator.evalAllSafe(expensive, true), "evalAllSafe cannot hide budget exhaustion");
        var arithmetic = fixture.engine.parse(String.join("+", Collections.nCopies(100, "1"))).getFirst();
        expectExceeded(() -> fixture.evaluator.evalAsFloat(arithmetic), "optimized float evaluator consumes the same allowance");
        equal(2f, fixture.evaluator.evalAsFloat(new FloatExpression(2f)), "optimized path recovers for the next evaluation");
    }

    private static void testNestedContext() throws Exception {
        Fixture fixture = new Fixture(128);
        expectExceeded(() -> fixture.evaluate("loop(80,{}); nested();"), "child evaluation cannot reset the parent's allowance");
        equal(1f, ValueConversions.asFloat(fixture.evaluate("1;")), "parent resumes after nested-context exhaustion");
    }

    private static void testFrameAndAvatarIsolation() {
        BudgetContext context = new BudgetContext(64);
        ExpressionEvaluator<?> evaluator = ExpressionEvaluator.evaluator(context);
        List<Expression> twentySteps = Collections.nCopies(20, FloatExpression.ONE);
        for (int pass = 0; pass < 3; pass++) {
            try (var frame = context.executionBudget().beginFrame(1)) {
                evaluator.evalAll(twentySteps, false);
            }
        }
        expectExceeded(() -> {
            try (var frame = context.executionBudget().beginFrame(1)) {
                evaluator.evalAll(twentySteps, false);
            }
        }, "repeated frame passes consume the remaining frame allowance");
        equal(1f, evaluator.evalAsFloat(FloatExpression.ONE), "standalone evaluation has its own bounded transaction");
        expectExceeded(() -> {
            try (var frame = context.executionBudget().beginFrame(1)) {
                evaluator.eval(FloatExpression.ONE);
            }
        }, "standalone evaluation cannot refill a consumed render-frame allowance");
        try (var frame = context.executionBudget().beginFrame(2)) {
            evaluator.evalAll(twentySteps, false);
        }
        Avatar.MolangContext first = new Avatar.MolangContext(null);
        Avatar.MolangContext second = new Avatar.MolangContext(null);
        try (var frame = first.executionBudget().beginFrame(17)) {
            for (int step = 0; step < MolangExecutionBudget.DEFAULT_FRAME_STEPS; step++) {
                first.executionBudget().consume();
            }
            expectExceeded(first.executionBudget()::consume, "first Avatar exhausted its frame allowance");
        }
        try (var frame = second.executionBudget().beginFrame(17)) {
            equal(1f, ExpressionEvaluator.evaluator(second).evalAsFloat(FloatExpression.ONE),
                    "second Avatar's same frame remains independent");
        }
    }

    private static void testDefaultFrameWorkload() {
        var context = new Avatar.MolangContext(null);
        var evaluator = ExpressionEvaluator.evaluator(context);
        // A real complex model needs about 295,000 AST steps for one 20 Hz frame.
        var completeModelFrame = Collections.<Expression>nCopies(300_000, FloatExpression.ONE);
        try (var frame = context.executionBudget().beginFrame(1)) {
            evaluator.evalAll(completeModelFrame, false);
        }
        expectExceeded(() -> {
            try (var repeatedPass = context.executionBudget().beginFrame(1)) {
                evaluator.evalAll(completeModelFrame, false);
            }
        }, "repeated render passes cannot refill the larger frame allowance");
        expectExceeded(() -> evaluator.evalAll(
                Collections.nCopies(MolangExecutionBudget.DEFAULT_STEPS + 1, FloatExpression.ONE), false),
                "standalone evaluations retain their original smaller allowance");
        try (var nextFrame = context.executionBudget().beginFrame(2)) {
            evaluator.evalAll(completeModelFrame, false);
        }
    }

    private static final class BudgetContext implements MolangExecutionBudget.Owner {
        private final MolangExecutionBudget budget;
        private final VariableStorage variables = new VariableStorage();

        private BudgetContext(int maximumSteps) {
            budget = new MolangExecutionBudget(maximumSteps);
        }

        @Override
        public MolangExecutionBudget executionBudget() {
            return budget;
        }
    }

    private static final class Fixture {
        private final BudgetContext context;
        private final ExpressionEvaluatorImpl<BudgetContext> evaluator;
        private final MolangEngine engine;

        private Fixture(int maximumSteps) {
            context = new BudgetContext(maximumSteps);
            evaluator = new ExpressionEvaluatorImpl<>(context);
            Iterable<Float> infinite = () -> new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public Float next() {
                    return 1f;
                }
            };
            Function nested = (execution, arguments) -> {
                var child = ((ExpressionEvaluatorImpl<?>) execution).createChild(new BudgetContext(100_000));
                return child.evalAll(Collections.nCopies(80, FloatExpression.ONE), false);
            };
            engine = MolangEngine.fromCustomBinding(name -> switch (name) {
                case "v" -> context.variables;
                case "loop" -> StandardBindings.LOOP_FUNC;
                case "for_each" -> StandardBindings.FOR_EACH_FUNC;
                case "items" -> (Variable) execution -> infinite;
                case "nested" -> nested;
                default -> null;
            });
        }

        private Object evaluate(String source) throws Exception {
            return evaluator.evalAll(engine.parse(source), true);
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }

    private static void expectExceeded(CheckedAction action, String message) {
        try {
            action.run();
        } catch (MolangExecutionBudget.Exceeded expected) {
            return;
        } catch (Exception unexpected) {
            throw new AssertionError(message, unexpected);
        }
        throw new AssertionError(message);
    }

    private static void equal(float expected, float actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": " + actual);
        }
    }
}
