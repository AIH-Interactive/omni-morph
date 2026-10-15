package org.figuramc.figura.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.parser.ParseException;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.binding.MolangBindings;

public final class YsmUnavailableBindingSmokeTest {
    public static void main(String[] args) throws Exception {
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        MolangBindings bindings = new MolangBindings();
        bindings.register("v", context.variables);
        MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
        var collect = YsmModelScanner.class.getDeclaredMethod("collectUnsupportedBindings", java.util.Set.class, String.class);
        collect.setAccessible(true);
        var reason = YsmModelScanner.class.getDeclaredMethod("optionalBindingReason", String.class);
        reason.setAccessible(true);
        for (String name : new String[]{"has_any_curios", "has_any_curios_with_any_tag", "has_any_curios_with_all_tags"}) {
            Function function = (Function) new MolangBindings.YsmBinding().getProperty(name);
            for (int size = 0; size <= 4; size++) {
                if (!function.validateArgumentSize(size)) {
                    throw new AssertionError("reference Fabric Curios NOOP accepts any argument count");
                }
            }
            for (String call : new String[]{"ysm." + name + ";", "ysm." + name + "();",
                    "ysm." + name + "(v.curios_argument=1,'invalid id');"}) {
                if (ExpressionEvaluator.evaluator(context).eval(engine.parse(call).getFirst()) != null) {
                    throw new AssertionError("reference Fabric Curios NOOP returns raw null");
                }
            }
            var gaps = new java.util.TreeSet<String>();
            collect.invoke(null, gaps, "ysm." + name + "('fixture:item');");
            String bindingName = "ysm." + name;
            String capability = MolangBindings.YsmBinding.optionalBindingUnavailableReason(name);
            if (!gaps.contains(bindingName) || capability == null || !capability.startsWith("CAPABILITY_UNAVAILABLE:")
                    || !capability.equals(reason.invoke(null, bindingName))) {
                throw new AssertionError("Curios placeholder must be an explicit optional capability gap");
            }
        }
        if (context.variables.getPath("curios_argument") != null
                || MolangBindings.YsmBinding.optionalBindingUnavailableReason("weather") != null) {
            throw new AssertionError("Curios NOOP evaluates no arguments and does not classify native bindings as optional");
        }
        Function version = (Function) new MolangBindings.YsmBinding().getProperty("mod_version");
        if (version.validateArgumentSize(0) || !version.validateArgumentSize(1) || version.validateArgumentSize(2)) {
            throw new AssertionError("mod_version requires exactly one argument");
        }
        for (String source : new String[]{"ysm.mod_version();", "ysm.mod_version('a','b');"}) {
            try {
                engine.parse(source);
                throw new AssertionError("invalid mod_version arity accepted");
            } catch (ParseException expected) {
                // Validate the production parser/function signature, before argument evaluation.
            }
        }
        for (String source : new String[]{"ysm.mod_version(1);", "ysm.mod_version('unknown-mod');"}) {
            if (ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst()) != null) {
                throw new AssertionError("invalid/unloaded mod must return null");
            }
        }
        Object loadedVersion = ExpressionEvaluator.evaluator(context).eval(engine.parse("ysm.mod_version('figura-test-version');").getFirst());
        if (!"1.2.3-test+cpu".equals(loadedVersion)) {
            throw new AssertionError("loaded platform version must preserve its friendly string");
        }
        context.variables.setPath("raw", "模型文字");
        for (String source : new String[]{"v.raw=='模型文字';", "'模型文字'==v.raw;", "v.raw!='other';", "'other'!=v.raw;"}) {
            if (!Boolean.TRUE.equals(ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst()))) {
                throw new AssertionError("raw/literal string comparison must be symmetric: " + source);
            }
        }
        for (String source : new String[]{"v.raw!='模型文字';", "'模型文字'!=v.raw;", "v.raw=='other';", "'other'==v.raw;"}) {
            if (!Boolean.FALSE.equals(ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst()))) {
                throw new AssertionError("raw/literal string negative comparison: " + source);
            }
        }
        reject(engine, context, "ysm.bone_param(v.side_effect=1);", "function");
        reject(engine, context, "ysm.bone_param;", "variable");
        for (String source : new String[]{"ysm.not_registered(v.side_effect=1);", "ysm.not_registered;"}) {
            try {
                ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst());
                throw new AssertionError("unknown binding must not return a fake zero");
            } catch (UnsupportedOperationException expected) {
                if (!expected.getMessage().contains("ysm.not_registered")
                        || !expected.getMessage().contains("unknown binding")) {
                    throw new AssertionError("unknown binding diagnosis is incomplete");
                }
            }
        }
        if (context.variables.getPath("side_effect") != null) {
            throw new AssertionError("unavailable contract must not evaluate guessed arguments");
        }
        Object result = ExpressionEvaluator.evaluator(context).evalAll(engine.parse("v.next=7; return v.next;"), true);
        if (!(result instanceof Number number) || number.floatValue() != 7f) {
            throw new AssertionError("unavailable binding failure corrupted the next evaluation");
        }
        System.out.println("M4 unresolved bone_param is explicit for function/value access, has no argument effects and permits subsequent evaluation.");
    }

    private static void reject(MolangEngine engine, Avatar.MolangContext context, String source, String kind) throws Exception {
        try {
            ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst());
            throw new AssertionError("unavailable " + kind + " binding returned a fake supported value");
        } catch (UnsupportedOperationException expected) {
            if (!expected.getMessage().contains("ysm.bone_param")
                    || !expected.getMessage().contains("reference contract unavailable")) {
                throw new AssertionError("unavailable binding diagnosis is incomplete");
            }
        }
    }
}
