package top.aihmc.omnimorph.model.ysm.animation;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.RandomAccess;

/** Mutable import list with a validated, stable time index rebuilt only after edits. */
final class YsmAnimationEventList extends AbstractList<YsmAnimationEvent> implements RandomAccess {
    private final List<YsmAnimationEvent> values = new ArrayList<>();
    private List<YsmAnimationEvent> ordered;

    @Override
    public YsmAnimationEvent get(int index) {
        return values.get(index);
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public void add(int index, YsmAnimationEvent event) {
        values.add(index, event);
        ordered = null;
        modCount++;
    }

    @Override
    public YsmAnimationEvent set(int index, YsmAnimationEvent event) {
        var previous = values.set(index, event);
        ordered = null;
        return previous;
    }

    @Override
    public YsmAnimationEvent remove(int index) {
        var previous = values.remove(index);
        ordered = null;
        modCount++;
        return previous;
    }

    List<YsmAnimationEvent> ordered() {
        if (ordered == null) {
            var sorted = new ArrayList<>(values);
            for (var event : sorted) {
                if (!Float.isFinite(event.time()) || event.time() < 0f) {
                    throw new IllegalArgumentException("Invalid animation event time");
                }
            }
            sorted.sort(Comparator.comparingDouble(YsmAnimationEvent::time));
            ordered = List.copyOf(sorted);
        }
        return ordered;
    }
}
