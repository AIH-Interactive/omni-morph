package org.figuramc.figura.model.ysm.animation;

import java.util.ArrayList;
import java.util.List;

public class YsmAnimationChannel {
    public final String type; // "position", "rotation", "scale"
    public final List<YsmKeyframe> keyframes = new ArrayList<>();
    
    public final float[] staticValue;
    public final org.figuramc.figura.molang.parser.ast.Expression[] staticExpressions;

    public YsmAnimationChannel(String type, float[] staticValue, org.figuramc.figura.molang.parser.ast.Expression[] staticExpressions) {
        this.type = type;
        this.staticValue = staticValue;
        this.staticExpressions = staticExpressions;
    }
}
