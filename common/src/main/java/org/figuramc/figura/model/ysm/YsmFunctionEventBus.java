package org.figuramc.figura.model.ysm;

import org.figuramc.figura.model.ysm.controller.YsmControllerSlot;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlotBinder;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YsmFunctionEventBus {
    private final Set<String> executedEventsThisFrame = new HashSet<>();
    private final ArrayDeque<String> recentEvents = new ArrayDeque<>();

    public void beginFrame() {
        executedEventsThisFrame.clear();
    }

    public Set<String> eventsForController(String controllerName, YsmControllerSlot slot) {
        return YsmControllerSlotBinder.eventNamesForController(controllerName, slot);
    }

    public List<String> unboundEvents(Collection<String> events) {
        return YsmControllerSlotBinder.unboundEvents(events, executedEventsThisFrame);
    }

    public void markExecuted(String event, String slot, int functionCount) {
        if (event == null || event.isBlank())
            return;
        executedEventsThisFrame.add(event);
        String value = slot == null || slot.isBlank() ? event + " x" + functionCount : event + " @" + slot + " x" + functionCount;
        recentEvents.addFirst(value);
        while (recentEvents.size() > 16)
            recentEvents.removeLast();
    }

    public List<String> recentEvents() {
        return List.copyOf(recentEvents);
    }
}
