package top.aihmc.omnimorph.model.ysm.sync;

import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Stable per-model inventory and the owner/observer runtime boundary. No resource names leave this adapter. */
public final class YsmSyncModel {
    private final YsmModelRuntime runtime;
    private final List<String> textures;
    private final List<AvatarControlDefinition> controls;
    private final List<String> actions;
    private final List<String> controllers;
    private final List<List<String>> states;
    private final List<YsmAnimationClip> clips;
    private final String schemaHash;

    public YsmSyncModel(YsmModelRuntime runtime) {
        this.runtime = runtime;
        textures = runtime.textureIds();
        controls = runtime.owner().controls.all().stream()
                .filter(control -> switch (control.type()) {
                    case TOGGLE, SLIDER, NUMBER, ENUM, COLOR -> true;
                    default -> false;
                })
                .sorted(Comparator.comparing(AvatarControlDefinition::id)).toList();
        actions = runtime.actions().asMap().keySet().stream().sorted().toList();
        controllers = runtime.animations().getControllers().keySet().stream().sorted().toList();
        states = controllers.stream().map(name -> runtime.animations().getControllers().get(name)
                .states().keySet().stream().sorted().toList()).toList();
        clips = runtime.animations().getClips().values().stream().distinct()
                .sorted(Comparator.comparing(clip -> clip.name)).toList();
        if (textures.size() > 65536 || clips.size() > 65536 || controls.size() > YsmSyncMessage.MAX_ENTRIES
                || actions.size() > YsmSyncMessage.MAX_ENTRIES || controllers.size() > YsmSyncMessage.MAX_ENTRIES
                || states.stream().anyMatch(values -> values.size() > 65536)) {
            throw new IllegalArgumentException("YSM synchronization inventory exceeds budget");
        }
        schemaHash = hashInventory();
    }

    public String contentHash() {
        return runtime.contentHash();
    }

    public String schemaHash() {
        return schemaHash;
    }

    public YsmSyncMessage.State capture() {
        requireOpen();
        runtime.owner().controls.hydrateLiveValues(runtime.owner());
        List<YsmSyncMessage.Control> controlValues = new ArrayList<>();
        for (int index = 0; index < controls.size(); index++) {
            AvatarControlDefinition control = controls.get(index);
            Object value = control.getValue();
            var type = controlType(control);
            double number = switch (type) {
                case BOOLEAN -> Boolean.TRUE.equals(value) ? 1 : 0;
                case NUMBER -> value instanceof Number numeric ? numeric.doubleValue() : control.min();
                case OPTION -> optionIndex(control, value);
                case COLOR -> colorValue(value);
            };
            controlValues.add(new YsmSyncMessage.Control(index, type, number));
        }
        List<YsmSyncMessage.Action> actionValues = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            var action = runtime.actions().get(actions.get(index));
            var playback = runtime.animations().getActiveAnimation(action.getAnimation());
            actionValues.add(new YsmSyncMessage.Action(index, runtime.actions().isActive(action.getId()),
                    playback == null ? 0 : playback.time, playback == null ? 1 : playback.speed,
                    playback != null && playback.loop));
        }
        List<YsmSyncMessage.Controller> controllerValues = new ArrayList<>();
        for (int index = 0; index < controllers.size(); index++) {
            String name = controllers.get(index);
            String current = runtime.animations().controllerRuntime().currentState(name);
            if (current != null) {
                controllerValues.add(new YsmSyncMessage.Controller(index, states.get(index).indexOf(current),
                        runtime.animations().controllerRuntime().stateTime(name)));
            }
        }
        return new YsmSyncMessage.State(textures.indexOf(runtime.textureId()), controlValues, actionValues,
                controllerValues, runtime.animations().captureRemotePlaybacks(clips, controllers));
    }

    /** Validate every model-local reference before making any observable change. */
    public void validate(YsmSyncMessage.State state) {
        requireOpen();
        if (runtime.owner().isHost || !state.complete() || state.texture() >= textures.size()) {
            throw new IllegalArgumentException("Incomplete or incompatible YSM state");
        }
        for (var value : state.controls()) {
            if (value.index() >= controls.size()) {
                throw new IllegalArgumentException("Invalid YSM control index");
            }
            var definition = controls.get(value.index());
            if (value.type() != controlType(definition)
                    || value.type() == YsmSyncMessage.ControlType.OPTION && value.value() >= definition.options().size()
                    || value.type() == YsmSyncMessage.ControlType.NUMBER
                    && (value.value() < definition.min() || value.value() > definition.max())) {
                throw new IllegalArgumentException("Incompatible YSM control value");
            }
        }
        for (var value : state.actions()) {
            if (value.index() >= actions.size()) {
                throw new IllegalArgumentException("Invalid YSM action index");
            }
        }
        for (var value : state.controllers()) {
            if (value.index() >= controllers.size() || value.state() >= states.get(value.index()).size()) {
                throw new IllegalArgumentException("Invalid YSM controller index");
            }
        }
        runtime.animations().validateRemotePlaybacks(state.playbacks(), clips, controllers);
    }

    public void apply(YsmSyncMessage.State state) {
        apply(state, List.of());
    }

    public void apply(YsmSyncMessage.State state, List<YsmSyncMessage.Event> events) {
        validate(state);
        events.forEach(this::validateEvent);
        Map<String, String> controllerStates = new HashMap<>();
        Map<String, Float> controllerTimes = new HashMap<>();
        for (var value : state.controllers()) {
            String name = controllers.get(value.index());
            String target = states.get(value.index()).get(value.state());
            if (!target.equals(runtime.animations().controllerRuntime().currentState(name))) {
                runtime.animations().stopControllerSounds(name);
            }
            controllerStates.put(name, target);
            controllerTimes.put(name, value.time());
        }
        try (var effects = runtime.functions().beginRemoteState()) {
            for (var value : state.controls()) {
                var definition = controls.get(value.index());
                Object controlValue = switch (value.type()) {
                    case BOOLEAN -> value.value() != 0;
                    case NUMBER -> value.value();
                    case OPTION -> definition.options().get((int) value.value());
                    case COLOR -> (long) value.value();
                };
                if (!java.util.Objects.equals(definition.getValue(), controlValue)) {
                    runtime.owner().controls.applyRemoteValue(runtime.owner(), definition.id(), controlValue);
                }
            }
            if (state.texture() >= 0 && !textures.get(state.texture()).equals(runtime.textureId())) {
                runtime.setTexture(textures.get(state.texture()));
            }
        }
        events.forEach(this::replay);
        runtime.animations().controllerRuntime().applyRemoteState(controllerStates, controllerTimes);
        runtime.animations().applyRemotePlaybacks(state.playbacks(), clips, controllers);
    }

    public void validateEvent(YsmSyncMessage.Event event) {
        var values = event.arguments();
        switch (event.kind()) {
            case SYNC -> { }
            case PLAY -> {
                if (values.size() != 4 || !isIndex(values.get(0), clips.size()) || !isIndex(values.get(1), 3)
                        || values.get(3) < 0) {
                    throw new IllegalArgumentException("Invalid remote play event");
                }
            }
            case STOP -> {
                if (values.size() != 1 || !isIndex(values.getFirst(), clips.size())) {
                    throw new IllegalArgumentException("Invalid remote stop event");
                }
            }
            case ACTION -> {
                if (values.size() != 1 || !isIndex(values.getFirst(), actions.size())) {
                    throw new IllegalArgumentException("Invalid remote action event");
                }
            }
        }
    }

    public void replay(YsmSyncMessage.Event event) {
        requireOpen();
        validateEvent(event);
        if (runtime.owner().isHost) {
            return;
        }
        if (event.kind() == YsmSyncMessage.EventKind.SYNC) {
            runtime.functions().replaySync(event);
            return;
        }
        try (var effects = runtime.functions().beginRemoteState()) {
            var values = event.arguments();
            switch (event.kind()) {
                case PLAY -> {
                    String clip = clips.get(values.getFirst().intValue()).name;
                    runtime.animations().stop(clip);
                    runtime.animations().play(clip, YsmAnimationClip.LoopMode.values()[values.get(1).intValue()],
                            values.get(2), values.get(3));
                }
                case STOP -> runtime.animations().stop(clips.get(values.getFirst().intValue()).name);
                case ACTION -> runtime.actions().applyRemotePulse(actions.get(values.getFirst().intValue()));
                default -> throw new IllegalArgumentException("Unsupported remote event");
            }
        }
    }

    private static boolean isIndex(float value, int size) {
        return value >= 0 && value < size && value == Math.rint(value);
    }

    private void requireOpen() {
        if (runtime.isClosed()) {
            throw new IllegalStateException("Retired YSM synchronization model");
        }
    }

    private String hashInventory() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                writeNames(out, textures);
                writeNames(out, controls.stream().map(AvatarControlDefinition::id).toList());
                for (var control : controls) {
                    out.writeUTF(control.type().name());
                    out.writeDouble(control.min());
                    out.writeDouble(control.max());
                    writeNames(out, control.options());
                }
                writeNames(out, actions);
                writeNames(out, controllers);
                for (var names : states) {
                    writeNames(out, names);
                }
                writeNames(out, clips.stream().map(clip -> clip.name).toList());
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("Cannot build YSM synchronization inventory");
        }
    }

    private static void writeNames(DataOutputStream out, List<String> names) throws IOException {
        out.writeInt(names.size());
        for (String name : names) {
            out.writeUTF(name);
        }
    }

    private static YsmSyncMessage.ControlType controlType(AvatarControlDefinition control) {
        return switch (control.type()) {
            case TOGGLE -> YsmSyncMessage.ControlType.BOOLEAN;
            case ENUM -> YsmSyncMessage.ControlType.OPTION;
            case COLOR -> YsmSyncMessage.ControlType.COLOR;
            default -> YsmSyncMessage.ControlType.NUMBER;
        };
    }

    private static int optionIndex(AvatarControlDefinition control, Object value) {
        int index = control.options().indexOf(String.valueOf(value));
        if (index >= 0) {
            return index;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Unrepresentable YSM control option");
        }
    }

    private static long colorValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue() & 0xffffffffL;
        }
        String text = String.valueOf(value);
        return Long.parseUnsignedLong(text.startsWith("#") ? text.substring(1)
                : text.startsWith("0x") ? text.substring(2) : text, 16);
    }
}
