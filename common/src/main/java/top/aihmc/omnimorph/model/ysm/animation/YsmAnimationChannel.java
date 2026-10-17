package top.aihmc.omnimorph.model.ysm.animation;

import java.util.ArrayList;
import java.util.List;

public class YsmAnimationChannel {
    public final String type; // "position", "rotation", "scale"
    public final List<YsmKeyframe> keyframes = new ArrayList<>();
    
    public final float[] staticValue;
    public final top.aihmc.omnimorph.molang.parser.ast.Expression[] staticExpressions;

    public YsmAnimationChannel(String type, float[] staticValue, top.aihmc.omnimorph.molang.parser.ast.Expression[] staticExpressions) {
        this.type = type;
        this.staticValue = staticValue;
        this.staticExpressions = staticExpressions;
    }
}
