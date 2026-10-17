package top.aihmc.omnimorph.model.ysm.animation;

import top.aihmc.omnimorph.molang.parser.ast.Expression;

public class YsmKeyframe {
    public final float time;
    public final float[] value;
    public final Expression[] expressions;
    public final float[] preValue;
    public final Expression[] preExpressions;
    public String interpolation = "linear";

    public YsmKeyframe(float time, float[] value, Expression[] expressions) {
        this(time, value, expressions, value, expressions);
    }

    public YsmKeyframe(float time, float[] value, Expression[] expressions, float[] preValue, Expression[] preExpressions) {
        this.time = time;
        this.value = value;
        this.expressions = expressions;
        this.preValue = preValue;
        this.preExpressions = preExpressions;
    }
}
