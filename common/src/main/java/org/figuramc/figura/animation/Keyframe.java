package org.figuramc.figura.animation;

import com.mojang.datafixers.util.Pair;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.parsers.BlockbenchCommonTypes;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.List;

public class Keyframe implements Comparable<Keyframe> {

    private final Avatar owner;
    private final Animation animation;
    private final float time;
    private final Interpolation interpolation;
    private final FiguraVec3 targetA, targetB;
    private final String[] aCode, bCode;
    private final String chunkName;
    private final FiguraVec3 bezierLeft, bezierRight;
    private final FiguraVec3 bezierLeftTime, bezierRightTime;

    // Pre-compiled Molang AST cache for each code component
    private final Expression[] aMolangAst;
    private final Expression[] bMolangAst;
    private final boolean[] aIsMolang;
    private final boolean[] bIsMolang;

    public Keyframe(Avatar owner, Animation animation, float time, Interpolation interpolation, Pair<FiguraVec3, String[]> a, Pair<FiguraVec3, String[]> b, FiguraVec3 bezierLeft, FiguraVec3 bezierRight, FiguraVec3 bezierLeftTime, FiguraVec3 bezierRightTime) {
        this.owner = owner;
        this.animation = animation;
        this.time = time;
        this.interpolation = interpolation;
        this.targetA = a.getFirst();
        this.targetB = b.getFirst();
        this.aCode = a.getSecond();
        this.bCode = b.getSecond();
        this.chunkName = animation.getName() + " keyframe (" + time + "s)";
        this.bezierLeft = bezierLeft;
        this.bezierRight = bezierRight;
        this.bezierLeftTime = bezierLeftTime;
        this.bezierRightTime = bezierRightTime;

        // Pre-compile Molang AST for string-based keyframe data
        this.aMolangAst = compileMolangExpressions(aCode);
        this.bMolangAst = compileMolangExpressions(bCode);
        this.aIsMolang = detectMolangExpressions(aCode);
        this.bIsMolang = detectMolangExpressions(bCode);
    }

    /**
     * Pre-compiles array of string expressions into Molang AST.
     * Returns null for pure number or Lua expressions.
     */
    private static Expression[] compileMolangExpressions(String[] code) {
        if (code == null) return null;
        Expression[] ast = new Expression[code.length];
        for (int i = 0; i < code.length; i++) {
            if (code[i] != null && BlockbenchCommonTypes.isMolang(code[i])) {
                try {
                    List<Expression> parsed = Avatar.getMolangEngine().parse(code[i]);
                    ast[i] = parsed.isEmpty() ? null : parsed.get(0);
                } catch (Exception e) {
                    ast[i] = null;
                }
            } else {
                ast[i] = null;
            }
        }
        return ast;
    }

    /**
     * Detects which code entries are Molang expressions.
     */
    private static boolean[] detectMolangExpressions(String[] code) {
        if (code == null) return new boolean[0];
        boolean[] result = new boolean[code.length];
        for (int i = 0; i < code.length; i++) {
            result[i] = code[i] != null && BlockbenchCommonTypes.isMolang(code[i]);
        }
        return result;
    }

    public FiguraVec3 getTargetA(float delta) {
        if (targetA != null) return targetA.copy();
        return FiguraVec3.of(
            evaluateCode(aCode, aMolangAst, aIsMolang, 0, delta),
            evaluateCode(aCode, aMolangAst, aIsMolang, 1, delta),
            evaluateCode(aCode, aMolangAst, aIsMolang, 2, delta)
        );
    }

    public FiguraVec3 getTargetB(float delta) {
        if (targetB != null) return targetB.copy();
        return FiguraVec3.of(
            evaluateCode(bCode, bMolangAst, bIsMolang, 0, delta),
            evaluateCode(bCode, bMolangAst, bIsMolang, 1, delta),
            evaluateCode(bCode, bMolangAst, bIsMolang, 2, delta)
        );
    }

    /**
     * Evaluates a single code component, trying Molang first, then falling back to Lua.
     */
    private float evaluateCode(String[] code, Expression[] molangAst, boolean[] isMolang, int index, float delta) {
        if (code == null || index >= code.length) return 0f;
        String data = code[index];
        if (data == null) return 0f;

        // Fast path: pure number
        try {
            return Float.parseFloat(data);
        } catch (NumberFormatException ignored) {}

        // Molang path: use pre-compiled AST + evaluator
        if (isMolang != null && index < isMolang.length && isMolang[index]) {
            if (molangAst != null && index < molangAst.length && molangAst[index] != null) {
                ExpressionEvaluator<?> evaluator = animation.getMolangEvaluator();
                if (evaluator != null) {
                    return evaluator.evalAsFloat(molangAst[index]);
                }
            }
        }

        // Fallback: original Lua evaluation
        return parseStringData(data, delta);
    }

    private float parseStringData(String data, float delta) {
        FiguraMod.pushProfiler(data);
        try {
            return FiguraMod.popReturnProfiler(Float.parseFloat(data));
        } catch (Exception ignored) {
            if (data == null)
                return FiguraMod.popReturnProfiler(0f);

            try {
                LuaValue val = owner.loadScript(chunkName, "return " + data);
                if (val == null)
                    return FiguraMod.popReturnProfiler(0f);

                Varargs args = owner.run(val, owner.animation, delta, animation);
                if (args.isnumber(1))
                    return FiguraMod.popReturnProfiler(args.tofloat(1));
                else
                    throw new Exception(); // dummy exception
            } catch (Exception ignored2) {
                try {
                    LuaValue val = owner.loadScript(chunkName, data);
                    if (val == null)
                        return FiguraMod.popReturnProfiler(0f);

                    Varargs args = owner.run(val, owner.animation, delta, animation);
                    if (args.isnumber(1))
                        return FiguraMod.popReturnProfiler(args.tofloat(1));
                    else
                        throw new LuaError("Failed to parse data from [" + this.chunkName + "], expected number, but got " + args.arg(1).typename());
                } catch (Exception e) {
                    if (owner.luaRuntime != null)
                        owner.luaRuntime.error(e);
                }
            }
        }

        return FiguraMod.popReturnProfiler(0f);
    }

    public float getTime() {
        return time;
    }

    public Interpolation getInterpolation() {
        return interpolation;
    }

    public FiguraVec3 getBezierLeft() {
        return bezierLeft.copy();
    }

    public FiguraVec3 getBezierRight() {
        return bezierRight.copy();
    }

    public FiguraVec3 getBezierLeftTime() {
        return bezierLeftTime.copy();
    }

    public FiguraVec3 getBezierRightTime() {
        return bezierRightTime.copy();
    }

    @Override
    public int compareTo(Keyframe other) {
        return Float.compare(this.getTime(), other.getTime());
    }
}
