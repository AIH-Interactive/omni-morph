package org.figuramc.figura.model.ysm.animation;

import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.List;

public record YsmAnimationEvent(float time, String type, String value, List<Expression> expressions) {
    public YsmAnimationEvent(float time, String type, String value) {
        this(time, type, value, List.of());
    }

    public YsmAnimationEvent {
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
    }
}
