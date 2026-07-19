package org.figuramc.figura.model.ysm.animation;

import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.List;
import java.util.Map;

public record YsmAnimationEvent(float time, String type, String value, List<Expression> expressions, Map<String, String> params) {
    public YsmAnimationEvent(float time, String type, String value) {
        this(time, type, value, List.of(), Map.of());
    }

    public YsmAnimationEvent(float time, String type, String value, List<Expression> expressions) {
        this(time, type, value, expressions, Map.of());
    }

    public YsmAnimationEvent {
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
