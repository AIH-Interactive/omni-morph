package org.figuramc.figura.model.ysm.controller;

import org.figuramc.figura.molang.parser.ast.Expression;

public record YsmControllerTransition(
        String targetState,
        Expression condition
) {
}
