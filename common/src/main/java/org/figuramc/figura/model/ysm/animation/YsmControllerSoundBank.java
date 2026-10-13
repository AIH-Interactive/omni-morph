package org.figuramc.figura.model.ysm.animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Owns controller audio until its channel finishes or its controller exits. */
public final class YsmControllerSoundBank implements AutoCloseable {
    private final Map<String, List<SoundHandle>> sounds = new HashMap<>();

    private record SoundHandle(BooleanSupplier finished, Runnable stop) {
    }

    public void add(String controller, BooleanSupplier finished, Runnable stop) {
        prune();
        sounds.computeIfAbsent(controller, ignored -> new ArrayList<>())
                .add(new SoundHandle(finished, stop));
    }

    public void prune() {
        sounds.values().forEach(handles -> handles.removeIf(handle -> handle.finished().getAsBoolean()));
        sounds.values().removeIf(List::isEmpty);
    }

    public void stop(String controller) {
        List<SoundHandle> handles = sounds.remove(controller);
        if (handles != null) {
            handles.forEach(handle -> handle.stop().run());
        }
    }

    @Override
    public void close() {
        List<SoundHandle> handles = sounds.values().stream().flatMap(List::stream).toList();
        sounds.clear();
        handles.forEach(handle -> handle.stop().run());
    }
}
