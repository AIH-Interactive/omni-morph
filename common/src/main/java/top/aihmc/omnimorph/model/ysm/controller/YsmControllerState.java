package top.aihmc.omnimorph.model.ysm.controller;

import top.aihmc.omnimorph.molang.parser.ast.Expression;

import java.util.List;

public record YsmControllerState(
        String name,
        List<YsmControllerAnimationRef> animations,
        List<YsmControllerTransition> transitions,
        List<Expression> onEntry,
        List<Expression> onExit,
        float blendTransition,
        boolean blendViaShortestPath,
        YsmBlendTransition blendCurve,
        List<String> soundEffects
) {
    public YsmControllerState {
        animations = animations == null ? List.of() : List.copyOf(animations);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        onEntry = onEntry == null ? List.of() : List.copyOf(onEntry);
        onExit = onExit == null ? List.of() : List.copyOf(onExit);
        blendCurve = blendCurve == null ? YsmBlendTransition.linear(blendTransition) : blendCurve;
        soundEffects = soundEffects == null ? List.of() : List.copyOf(soundEffects);
    }

    public YsmControllerState(String name, List<YsmControllerAnimationRef> animations,
                              List<YsmControllerTransition> transitions, List<Expression> onEntry,
                              List<Expression> onExit, float blendTransition, boolean blendViaShortestPath) {
        this(name, animations, transitions, onEntry, onExit, blendTransition, blendViaShortestPath, null, List.of());
    }

    public YsmControllerState(String name, List<YsmControllerAnimationRef> animations,
                              List<YsmControllerTransition> transitions, List<Expression> onEntry,
                              List<Expression> onExit, float blendTransition, boolean blendViaShortestPath,
                              YsmBlendTransition blendCurve) {
        this(name, animations, transitions, onEntry, onExit, blendTransition, blendViaShortestPath, blendCurve, List.of());
    }
}
