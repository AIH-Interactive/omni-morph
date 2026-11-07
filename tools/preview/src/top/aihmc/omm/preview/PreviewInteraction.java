package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.figuramc.figura.lua.api.action_wheel.Action;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;

/** Render-thread command queue and reproducible input log shared by the real UI and config replay. */
final class PreviewInteraction implements AutoCloseable {
    static final class Operation {
        long frame;
        String type;
        int slot = 1;
        boolean right;
        double delta;
        String id;
        String page;
        JsonElement value;
        int modifiers;
        boolean repeat;
        int scancode = -1;
    }

    record Slot(String title, String item, boolean present, int rgb, int hoverRgb) {}
    record Control(String id, String title, String description, String page, String targetPage, AvatarControlType type,
        Object value, double min, double max, double step, List<String> options, List<String> optionLabels) {}
    record Snapshot(String wheelPage, int group, int groups, List<Slot> slots, String settingsPage, long pageRequest,
        long revision, List<Control> controls, java.util.Map<String, PreviewYsmRuntime.ActionState> actions,
        java.util.Map<String, AnimationState> animations, List<KeyState> keybinds,
        java.util.Map<String, Boolean> vanilla) {}
    record KeyState(String name, String key, boolean pressed, boolean enabled, boolean gui) {}
    record AnimationState(String state, float time, float speed, float blend, int priority, String loop) {}

    private final ConcurrentLinkedQueue<Operation> commands = new ConcurrentLinkedQueue<>();
    private final List<Operation> replay;
    private final BufferedWriter output;
    private final java.nio.file.Path finalState;
    private final PreviewInteractionWindow window;
    private int next;
    private long lastSnapshot;
    private PreviewLua lastOwner;
    private PreviewYsmInput lastInput;
    private boolean wheelWasEnabled;

    PreviewInteraction(PreviewConfig config) throws Exception {
        var actions = new ArrayList<>(config.interaction.actions);
        if (!config.interaction.replayFile.isBlank()) {
            var file = config.configFile.getParent().resolve(config.interaction.replayFile).normalize();
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    actions.add(PreviewConfig.JSON.fromJson(line, Operation.class));
                }
            }
        }
        for (Operation operation : actions) {
            validate(operation);
        }
        replay = actions.stream().sorted(Comparator.comparingLong(operation -> operation.frame)).toList();
        output = Files.newBufferedWriter(config.outputDirectory.resolve("interaction.jsonl"));
        finalState = config.outputDirectory.resolve("interaction-state.json");
        window = config.interaction.enabled ? new PreviewInteractionWindow(config.window.title, commands::add) : null;
    }

    void update(PreviewLua lua, long frame, float time) throws IOException {
        update(lua, lua == null ? null : lua.molang, frame, time);
    }

    void update(PreviewLua lua, PreviewMolang host, long frame, float time) throws IOException {
        if (host == null)
            return;
        lastOwner = lua;
        lastInput = host.input;
        if (lua != null)
            lua.inputTime(time);
        if (window != null && lua != null) {
            if (wheelWasEnabled && !window.wheelEnabled()) {
                lua.releaseWheelInput();
            }
            wheelWasEnabled = window.wheelEnabled();
            lua.wheelState(window.wheelEnabled(), window.selectedSlot());
        }
        while (next < replay.size() && replay.get(next).frame <= frame) {
            apply(lua, host.input, replay.get(next++), frame, time, "replay");
        }
        Operation command;
        while ((command = commands.poll()) != null) {
            apply(lua, host.input, command, frame, time, "window");
        }
        if (window != null && lua != null && System.nanoTime() - lastSnapshot >= 100_000_000L) {
            lastSnapshot = System.nanoTime();
            window.showSnapshot(snapshot(lua));
        }
    }

    private void apply(PreviewLua lua, PreviewYsmInput input, Operation operation, long frame, float time,
        String source) throws IOException {
        validate(operation);
        if (lua == null && !operation.type.startsWith("key-") && !operation.type.equals("input-ready"))
            throw new UnsupportedOperationException(
                "Interaction " + operation.type + " requires a model Lua/control host");
        boolean enabled = lua != null && lua.wheelEnabled();
        int selected = lua == null ? -1 : lua.selectedWheelSlot();
        if (operation.type.startsWith("wheel-")) {
            // A replay is explicit wheel input and uses the same enabled-wheel dispatch as the UI.
            lua.wheelState(true, operation.slot - 1);
        }
        try {
            switch (operation.type) {
                case "wheel-click" -> lua.executeWheel(operation.slot - 1, !operation.right);
                case "wheel-release" -> lua.releaseWheelInput();
                case "wheel-scroll" -> lua.scrollWheel(operation.delta);
                case "set-control" -> {
                    JsonObject values = new JsonObject();
                    values.add(operation.id, operation.value);
                    lua.applyControls(values);
                }
                case "press-control" -> {
                    if (!lua.controls.press(lua, operation.id)) {
                        throw new IllegalArgumentException("Unknown model control: " + operation.id);
                    }
                }
                case "reset-control" -> {
                    if (!lua.controls.reset(lua, operation.id)) {
                        throw new IllegalArgumentException("Unknown model control: " + operation.id);
                    }
                }
                case "open-page" -> lua.openControlsPage(operation.page);
                case "key-down", "key-up" -> {
                    var key = PreviewKeys.parse(operation.id);
                    if (!operation.repeat)
                        input.event(key, operation.type.equals("key-down"));
                    if (lua != null)
                        org.figuramc.figura.lua.api.keybind.FiguraKeybind.set(
                            lua.keybinds.keyBindings, key, operation.type.equals("key-down"), operation.modifiers);
                    if (lua != null && operation.scancode >= 0 && key.type() == PreviewKeys.Type.KEYBOARD) {
                        org.figuramc.figura.lua.api.keybind.FiguraKeybind.set(lua.keybinds.keyBindings,
                            PreviewKeys.parse("scancode." + operation.scancode), operation.type.equals("key-down"),
                            operation.modifiers);
                    }
                }
                case "key-release-all" -> {
                    input.release();
                    if (lua != null)
                        org.figuramc.figura.lua.api.keybind.FiguraKeybind.releaseAll(lua.keybinds.keyBindings);
                }
                case "input-ready" -> input.setReady(operation.value.getAsBoolean());
                default -> throw new IllegalArgumentException("Unknown input: " + operation.type);
            }
            JsonObject entry = PreviewConfig.JSON.toJsonTree(operation).getAsJsonObject();
            entry.addProperty("frame", frame);
            entry.addProperty("animationTime", time);
            entry.addProperty("source", source);
            if (lua != null)
                entry.add("state", PreviewConfig.JSON.toJsonTree(snapshot(lua)));
            entry.add("ysmInput", PreviewConfig.JSON.toJsonTree(input.snapshot()));
            output.write(new com.google.gson.Gson().toJson(entry));
            output.newLine();
            output.flush();
        } finally {
            if (lua != null)
                lua.wheelState(enabled, selected);
        }
    }

    private static void validate(Operation operation) {
        PreviewConfig.require(operation != null && operation.frame >= 0 && operation.type != null,
            "interaction action needs a nonnegative frame and type");
        PreviewConfig.require(
            List.of("wheel-click", "wheel-release", "wheel-scroll", "set-control", "press-control", "reset-control",
                    "open-page", "key-down", "key-up", "key-release-all", "input-ready")
                .contains(operation.type),
            "unknown interaction action type");
        PreviewConfig.require(
            operation.slot >= 0 && operation.slot <= 8, "interaction slot must be 0..8 (0=no selection)");
        PreviewConfig.require(
            !operation.type.equals("wheel-click") || operation.slot > 0, "wheel-click slot must be 1..8");
        PreviewConfig.require(Double.isFinite(operation.delta), "interaction scroll delta must be finite");
        if (operation.type.equals("set-control") || operation.type.equals("press-control")
            || operation.type.equals("reset-control")) {
            PreviewConfig.require(
                operation.id != null && !operation.id.isBlank(), "interaction control id is required");
        }
        PreviewConfig.require(
            !operation.type.equals("set-control") || operation.value != null, "set-control requires a JSON value");
        PreviewConfig.require(
            !operation.type.equals("open-page") || operation.page != null, "open-page requires a page");
        if (operation.type.equals("input-ready")) {
            PreviewConfig.require(operation.value != null && operation.value.isJsonPrimitive()
                    && operation.value.getAsJsonPrimitive().isBoolean(),
                "input-ready requires a boolean value");
        }
        if (operation.type.equals("key-down") || operation.type.equals("key-up")) {
            PreviewConfig.require(operation.id != null, "key input requires an id");
            PreviewKeys.parse(operation.id);
        }
    }

    static Snapshot snapshot(PreviewLua lua) {
        var page = lua.actionWheel.currentPage;
        List<Slot> slots = new ArrayList<>();
        Action[] actions = page == null ? new Action[8] : page.slots();
        for (Action action : actions) {
            slots.add(action == null
                    ? new Slot("", "", false, 0x303943, 0x404b56)
                    : new Slot(action.getTitle() == null ? "" : action.getTitle(), action.getItem(false), true,
                          rgb(action.getColor(false), 0x3a536b), rgb(action.getColor(true), 0xcccccc)));
        }
        List<Control> controls = new ArrayList<>();
        for (var control : lua.controls.all()) {
            Object value = control.getValue();
            if (value != null && !(value instanceof Number) && !(value instanceof Boolean)
                && !(value instanceof String)) {
                value = value.toString();
            }
            controls.add(new Control(control.id(), control.getTitle(), control.getDescription(), control.page(),
                control.targetPage(), control.type(), value, control.min(), control.max(), control.step(),
                List.copyOf(control.options()), control.options().stream().map(control::optionLabel).toList()));
        }
        var animationStates = new java.util.LinkedHashMap<String, AnimationState>();
        for (var animation : lua.animations.values()) {
            animationStates.put(animation.modelName + "." + animation.name,
                new AnimationState(animation.getPlayState(), animation.getTime(), animation.getSpeed(),
                    animation.getBlend(), animation.getPriority(), animation.getLoop()));
        }
        return new Snapshot(page == null ? "No wheel page" : page.getTitle(), page == null ? 1 : page.getSlotsShift(),
            page == null ? 1 : page.getGroupCount(), List.copyOf(slots), lua.controlsPage(), lua.controlsPageRequest(),
            lua.controls.revision(), List.copyOf(controls),
            lua.ysm() == null ? java.util.Map.of() : lua.ysm().actionStates(), java.util.Map.copyOf(animationStates),
            lua.keybinds.keyBindings.stream()
                .map(binding
                    -> new KeyState(binding.getName(), binding.getKey(), binding.isPressed(), binding.isEnabled(),
                        binding.isGuiEnabled()))
                .toList(),
            lua.vanillaVisibility());
    }

    void keyInput(String key, int scancode, boolean pressed, int modifiers) {
        keyInput(key, scancode, pressed, modifiers, false);
    }

    void keyInput(String key, int scancode, boolean pressed, int modifiers, boolean repeat) {
        var operation = new Operation();
        operation.type = pressed ? "key-down" : "key-up";
        operation.id = key;
        operation.scancode = scancode;
        operation.modifiers = modifiers;
        operation.repeat = repeat;
        commands.add(operation);
    }

    void inputReady(boolean ready) {
        var operation = new Operation();
        operation.type = "input-ready";
        operation.value = new com.google.gson.JsonPrimitive(ready);
        commands.add(operation);
    }

    void releaseKeys() {
        var operation = new Operation();
        operation.type = "key-release-all";
        commands.add(operation);
    }

    void toggleWindow() {
        if (window != null) {
            window.toggle();
        }
    }

    private static int rgb(org.figuramc.figura.math.vector.FiguraVec3 color, int fallback) {
        if (color == null) {
            return fallback;
        }
        int red = (int) Math.round(Math.clamp(color.x, 0, 1) * 255);
        int green = (int) Math.round(Math.clamp(color.y, 0, 1) * 255);
        int blue = (int) Math.round(Math.clamp(color.z, 0, 1) * 255);
        return red << 16 | green << 8 | blue;
    }

    @Override
    public void close() throws Exception {
        if (window != null) {
            window.close();
        }
        if (lastOwner != null) {
            lastOwner.releaseWheelInput();
            org.figuramc.figura.lua.api.keybind.FiguraKeybind.releaseAll(lastOwner.keybinds.keyBindings);
        }
        if (lastInput != null) {
            lastInput.release();
            var state = lastOwner == null ? new JsonObject()
                                          : PreviewConfig.JSON.toJsonTree(snapshot(lastOwner)).getAsJsonObject();
            state.add("ysmInput", PreviewConfig.JSON.toJsonTree(lastInput.snapshot()));
            Files.writeString(finalState, PreviewConfig.JSON.toJson(state));
        }
        output.close();
    }
}
