package org.figuramc.figura.model.ysm.controller;

import org.figuramc.figura.molang.parser.ast.Expression;

public record YsmControllerAnimationRef(
        String animation,
        Expression condition
) {
}
