package org.figuramc.figura.model.ysm.animation;

import org.figuramc.figura.molang.parser.ast.Expression;

public class YsmKeyframe {
    public final float time;
    public final float[] value;
    public final Expression[] expressions;
    public String interpolation = "linear";

    public YsmKeyframe(float time, float[] value, Expression[] expressions) {
        this.time = time;
        this.value = value;
        this.expressions = expressions;
    }
}
