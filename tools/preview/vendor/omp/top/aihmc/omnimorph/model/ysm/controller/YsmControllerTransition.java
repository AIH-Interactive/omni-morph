package top.aihmc.omnimorph.model.ysm.controller;

import top.aihmc.omnimorph.molang.parser.ast.Expression;

public record YsmControllerTransition(
        String targetState,
        Expression condition
) {
}
