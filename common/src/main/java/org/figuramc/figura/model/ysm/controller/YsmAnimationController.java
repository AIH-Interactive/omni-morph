package org.figuramc.figura.model.ysm.controller;

import java.util.LinkedHashMap;
import java.util.Map;

public record YsmAnimationController(
        String name,
        String initialState,
        YsmControllerSlot slot,
        LinkedHashMap<String, YsmControllerState> states
) {
    public YsmAnimationController {
        initialState = initialState == null || initialState.isBlank() ? "default" : initialState;
        slot = slot == null ? YsmControllerSlot.fromName(name) : slot;
        states = states == null ? new LinkedHashMap<>() : new LinkedHashMap<>(states);
    }

    public YsmControllerState initialStateDefinition() {
        YsmControllerState state = states.get(initialState);
        if (state != null)
            return state;
        state = states.get("default");
        if (state != null)
            return state;
        for (Map.Entry<String, YsmControllerState> entry : states.entrySet())
            return entry.getValue();
        return null;
    }
}
