package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

public final class YsmInputSmokeTest {
    public static void main(String[] args) throws Exception {
        YsmInputState state = new YsmInputState();
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        boolean[] owner = {true};
        MolangBindings bindings = new MolangBindings();
        bindings.register("v", context.variables);
        bindings.register("input_test", (ObjectBinding) name -> switch (name) {
            // These call the exact production argument/state reader with an explicit ownership fixture.
            case "keyboard" -> (Function) (ctx, arguments) -> state.keyboard(owner[0], ctx, arguments);
            case "mouse" -> (Function) (ctx, arguments) -> state.mouse(owner[0], ctx, arguments);
            default -> null;
        });
        MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
        state.setReady(true);
        state.keyEvent(65, 2);
        require(!evaluate(engine, context, "input_test.keyboard(65);"), "repeat cannot create a press");
        state.keyEvent(65, 1);
        state.keyEvent(65, 2);
        require(evaluate(engine, context, "input_test.keyboard(64,65,66);"), "reference any-key semantics");
        require(evaluate(engine, context, "input_test.keyboard(65,v.touched=66);"), "first down key short-circuits");
        require(context.variables.getPath("touched") == null, "short-circuit does not evaluate later arguments");
        state.keyEvent(65, 0);
        require(!evaluate(engine, context, "input_test.keyboard(65);"), "release clears held key");
        state.keyEvent(66, 1);
        require(evaluate(engine, context, "input_test.keyboard(65,v.touched=66);"), "later held key is evaluated");
        require(((Number) context.variables.getPath("touched")).intValue() == 66, "later argument effect is preserved");
        state.keyEvent(32, 1);
        state.keyEvent(348, 1);
        state.keyEvent(31, 1);
        state.keyEvent(349, 1);
        require(evaluate(engine, context, "input_test.keyboard(32);")
                && evaluate(engine, context, "input_test.keyboard(348);"), "inclusive key boundaries");
        require(!evaluate(engine, context, "input_test.keyboard(-1,31,349);"), "invalid keys cannot access device arrays");

        state.mouseEvent(0, 1);
        state.mouseEvent(7, 1);
        require(evaluate(engine, context, "input_test.mouse(0);")
                && evaluate(engine, context, "input_test.mouse(7);"), "inclusive mouse boundaries");
        state.mouseEvent(0, 0);
        require(!evaluate(engine, context, "input_test.mouse(0);"), "mouse release clears held button");
        require(!evaluate(engine, context, "input_test.mouse(-1);")
                && !evaluate(engine, context, "input_test.mouse(8);"), "invalid mouse buttons rejected");
        state.mouseEvent(1, 1);
        require(evaluate(engine, context, "input_test.mouse(1.9);"), "reference integer conversion");

        owner[0] = false;
        require(!evaluate(engine, context, "input_test.keyboard(v.denied=66);"), "observer keyboard access denied");
        require(!evaluate(engine, context, "input_test.mouse(v.denied=1);"), "observer mouse access denied");
        require(context.variables.getPath("denied") == null, "observer does not evaluate input arguments");
        owner[0] = true;
        require(evaluate(engine, context, "input_test.keyboard(66);"), "observer cannot clear owner's input");
        state.setReady(false);
        state.keyEvent(65, 1);
        state.mouseEvent(1, 1);
        require(!evaluate(engine, context, "input_test.keyboard(v.inactive=66);"), "unready access denied");
        require(context.variables.getPath("inactive") == null, "unready query does not evaluate input arguments");
        state.setReady(true);
        require(!evaluate(engine, context, "input_test.keyboard(65,66,32,348);"), "focus/screen reset removes stale keyboard state");
        require(!evaluate(engine, context, "input_test.mouse(1);")
                && !evaluate(engine, context, "input_test.mouse(7);"), "focus/screen reset removes stale mouse state");

        Object local = new Object();
        require(YsmInputState.ownsInput(true, local, local), "host controlling local entity owns input");
        require(!YsmInputState.ownsInput(false, local, local)
                && !YsmInputState.ownsInput(true, new Object(), local)
                && !YsmInputState.ownsInput(true, null, null), "observer/other entity/missing player gates");
        require(!evaluate(engine, context, "ysm.keyboard(v.unowned=65);")
                && !evaluate(engine, context, "ysm.mouse(v.unowned=1);"), "actual bindings reject an unowned context before game access");
        require(context.variables.getPath("unowned") == null, "actual unowned binding has no argument effects");
        require(ExpressionEvaluator.evaluator(context).eval(engine.parse("ysm.mouse(0);").getFirst()) instanceof Boolean,
                "actual input binding preserves the reference Boolean return type");
        state.keyEvent(65, 1);
        state.mouseEvent(1, 1);
        state.updateReadiness(null);
        state.setReady(true);
        require(!evaluate(engine, context, "input_test.keyboard(65);")
                && !evaluate(engine, context, "input_test.mouse(1);"), "missing client clears device readiness/state");
        Function keyboard = (Function) new MolangBindings.YsmBinding().getProperty("keyboard");
        Function mouse = (Function) new MolangBindings.YsmBinding().getProperty("mouse");
        require(!keyboard.validateArgumentSize(0) && keyboard.validateArgumentSize(1)
                && keyboard.validateArgumentSize(3), "keyboard signature allows one or more keys");
        require(!mouse.validateArgumentSize(0) && mouse.validateArgumentSize(1)
                && !mouse.validateArgumentSize(2), "mouse signature is exactly one button");
        System.out.println("M4 input readers: press/release/ranges, any-key short circuit, ownership and readiness cleanup passed.");
    }

    private static boolean evaluate(MolangEngine engine, Avatar.MolangContext context, String source) throws Exception {
        return ExpressionEvaluator.evaluator(context).evalAsBoolean(engine.parse(source).getFirst());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
