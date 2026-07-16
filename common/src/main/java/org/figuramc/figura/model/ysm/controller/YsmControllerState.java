package org.figuramc.figura.model.ysm.controller;

import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.List;

public record YsmControllerState(
        String name,
        List<YsmControllerAnimationRef> animations,
        List<YsmControllerTransition> transitions,
        List<Expression> onEntry,
        List<Expression> onExit,
        float blendTransition,
        boolean blendViaShortestPath
) {
    public YsmControllerState {
        animations = animations == null ? List.of() : List.copyOf(animations);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        onEntry = onEntry == null ? List.of() : List.copyOf(onEntry);
        onExit = onExit == null ? List.of() : List.copyOf(onExit);
    }
}
