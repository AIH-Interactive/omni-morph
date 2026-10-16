package org.figuramc.figura.model.ysm.sync;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Model-local indices keep private resource names and expressions out of state messages. */
public record YsmSyncMessage(UUID owner, UUID session, String contentHash, String schemaHash,
                             long generation, long sequence, long baseSequence, long ownerTick,
                             boolean snapshot, long eventWatermark, State state, List<Event> events) {
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_EVENTS = 64;

    public YsmSyncMessage {
        if (owner == null || session == null || state == null) {
            throw new IllegalArgumentException("Missing sync identity or state");
        }
        requireHash(contentHash);
        requireHash(schemaHash);
        if (generation < 0 || sequence < 0 || ownerTick < 0 || eventWatermark < 0
                || (snapshot ? baseSequence != -1 : baseSequence < 0 || baseSequence >= sequence)) {
            throw new IllegalArgumentException("Invalid sync clock");
        }
        if (snapshot && !state.complete()) {
            throw new IllegalArgumentException("Incomplete snapshot");
        }
        events = List.copyOf(events);
        if (events.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("Invalid sync event batch");
        }
        long previous = -1;
        for (Event event : events) {
            if (event.sequence() <= previous || event.sequence() > eventWatermark) {
                throw new IllegalArgumentException("Unordered sync events");
            }
            previous = event.sequence();
        }
    }

    /** Null sections in a delta mean unchanged; a present empty list clears that section. */
    public record State(Integer texture, List<Control> controls, List<Action> actions,
                        List<Controller> controllers, List<Playback> playbacks) {
        public State {
            if (texture != null) {
                requireIndex(texture, true);
            }
            controls = copySection(controls, Control::index);
            actions = copySection(actions, Action::index);
            controllers = copySection(controllers, Controller::index);
            playbacks = copySection(playbacks, Playback::slot);
        }

        public boolean complete() {
            return texture != null && controls != null && actions != null && controllers != null && playbacks != null;
        }

        public State apply(State delta) {
            return new State(delta.texture != null ? delta.texture : texture,
                    delta.controls != null ? delta.controls : controls,
                    delta.actions != null ? delta.actions : actions,
                    delta.controllers != null ? delta.controllers : controllers,
                    delta.playbacks != null ? delta.playbacks : playbacks);
        }
    }

    public enum ControlType {
        BOOLEAN, NUMBER, OPTION, COLOR
    }

    public record Control(int index, ControlType type, double value) {
        public Control {
            requireIndex(index, false);
            if (type == null || !Double.isFinite(value)
                    || type == ControlType.BOOLEAN && value != 0 && value != 1
                    || type == ControlType.OPTION && (value < 0 || value > 65535 || value != Math.rint(value))
                    || type == ControlType.COLOR && (value < 0 || value > 0xffffffffL || value != Math.rint(value))) {
                throw new IllegalArgumentException("Invalid sync control");
            }
        }
    }

    public record Action(int index, boolean active, float time, float speed, boolean loop) {
        public Action {
            requireIndex(index, false);
            requireTime(time);
            requireFinite(speed);
        }
    }

    public record Controller(int index, int state, float time) {
        public Controller {
            requireIndex(index, false);
            requireIndex(state, false);
            requireTime(time);
        }
    }

    public record Playback(int slot, int clip, int controller, float time, float speed,
                           float weight, float targetWeight, int loopMode, float fadeSpeed, int flags) {
        public Playback(int slot, int clip, int controller, float time, float speed,
                        float weight, float targetWeight, int loopMode) {
            this(slot, clip, controller, time, speed, weight, targetWeight, loopMode, 0, 0);
        }

        public Playback {
            requireIndex(slot, false);
            requireIndex(clip, false);
            requireIndex(controller, true);
            requireTime(time);
            requireFinite(speed);
            requireFinite(weight);
            requireFinite(targetWeight);
            requireFinite(fadeSpeed);
            if (weight < 0 || targetWeight < 0 || fadeSpeed < 0 || loopMode < 0 || loopMode > 2
                    || flags < 0 || flags > 7) {
                throw new IllegalArgumentException("Invalid sync playback");
            }
        }
    }

    public enum EventKind {
        SYNC, PLAY, STOP, ACTION
    }

    public record Event(long sequence, EventKind kind, List<Float> arguments) {
        public Event(long sequence, List<Float> arguments) {
            this(sequence, EventKind.SYNC, arguments);
        }

        public Event {
            arguments = List.copyOf(arguments);
            if (sequence <= 0 || kind == null || arguments.size() > 16) {
                throw new IllegalArgumentException("Invalid sync event");
            }
            arguments.forEach(YsmSyncMessage::requireFinite);
        }
    }

    private static <T> List<T> copySection(List<T> values, ToIntFunction<T> index) {
        if (values == null) {
            return null;
        }
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Sync section exceeds budget");
        }
        List<T> result = List.copyOf(values);
        HashSet<Integer> indices = new HashSet<>();
        for (T value : result) {
            if (!indices.add(index.applyAsInt(value))) {
                throw new IllegalArgumentException("Duplicate sync index");
            }
        }
        return result;
    }

    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid sync digest");
        }
    }

    private static void requireIndex(int index, boolean allowAbsent) {
        if (index < (allowAbsent ? -1 : 0) || index > 65535) {
            throw new IllegalArgumentException("Invalid sync index");
        }
    }

    private static void requireTime(float time) {
        requireFinite(time);
        if (time < 0) {
            throw new IllegalArgumentException("Invalid sync time");
        }
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite sync value");
        }
    }
}
