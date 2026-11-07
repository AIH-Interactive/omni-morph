package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.figuramc.figura.lua.LuaTypeManager;
import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.figuramc.figura.lua.api.action_wheel.Action;
import org.figuramc.figura.lua.api.action_wheel.ActionWheelAPI;
import org.figuramc.figura.lua.api.action_wheel.Page;
import org.figuramc.figura.lua.api.event.EventsAPI;
import org.figuramc.figura.lua.api.event.LuaEvent;
import org.figuramc.figura.utils.PathUtils;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.*;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.avatar.control.PreviewControlRuntime;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.lua.api.avatar_controls.AvatarControlsAPI;

/** Actual Figura LuaJ host: per-instance globals/modules/events, original whitelisted APIs and bounded calls. */
public final class PreviewLua implements AutoCloseable {
    record Program(Map<String, String> sources, List<String> entries, Map<String, byte[]> sounds) {
        Program(Map<String, String> sources, List<String> entries) {
            this(sources, entries, Map.of());
        }

        Program {
            sources = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sources));
            entries = List.copyOf(entries);
            sounds = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sounds));
        }

        static Program load(Path source) throws IOException {
            try (YsmPackage resources = YsmPackage.open(source)) {
                var scripts = new LinkedHashMap<String, String>();
                var sounds = new LinkedHashMap<String, byte[]>();
                var manifests = resources.resourcePaths()
                                    .stream()
                                    .filter(name
                                        -> name.equalsIgnoreCase("avatar.json")
                                            || name.toLowerCase(java.util.Locale.ROOT).endsWith("/avatar.json"))
                                    .sorted(java.util.Comparator.comparingInt(name -> name.split("/").length))
                                    .toList();
                String metadataPath = manifests.isEmpty() ? null : manifests.get(0);
                if (metadataPath != null
                    && manifests.stream()
                            .filter(name -> name.split("/").length == metadataPath.split("/").length)
                            .count()
                        != 1) {
                    throw new IOException("Ambiguous Figura ZIP: multiple avatar roots");
                }
                String prefix = metadataPath == null
                    ? ""
                    : metadataPath.substring(0, metadataPath.length() - "avatar.json".length());
                for (String name : resources.resourcePaths().stream().sorted().toList()) {
                    if (name.startsWith(prefix) && name.endsWith(".lua")) {
                        scripts.put(name.substring(prefix.length(), name.length() - 4), resources.readString(name));
                    }
                    if (name.startsWith(prefix) && name.endsWith(".ogg")) {
                        String id = name.substring(prefix.length(), name.length() - 4).replace('/', '.');
                        sounds.put(id, resources.readBytes(name));
                    }
                }
                List<String> entries = new ArrayList<>(scripts.keySet());
                if (metadataPath != null) {
                    JsonObject metadata = JsonParser.parseString(resources.readString(metadataPath)).getAsJsonObject();
                    if (metadata.has("autoScripts") && !metadata.get("autoScripts").isJsonNull()) {
                        entries.clear();
                        for (JsonElement entry : metadata.getAsJsonArray("autoScripts")) {
                            entries.add(PathUtils.computeSafeString(entry.getAsString().replaceAll("\\.lua$", "")));
                        }
                    }
                }
                return new Program(scripts, entries, sounds);
            }
        }
    }

    private static final int INSTRUCTION_LIMIT = 500_000;
    private final Globals globals = new Globals();
    private final LuaTypeManager types = new LuaTypeManager();
    private final EventsAPI events = new EventsAPI();
    private final Program program;
    private final Map<String, Varargs> loaded = new LinkedHashMap<>();
    private final Deque<String> loading = new ArrayDeque<>();
    private final LuaFunction setHook;
    private final LuaFunction getInfo;
    private int callDepth;
    private int tick;
    final PreviewParticles particles;
    final PreviewSounds sounds;
    private final PreviewAudio audio;
    private final boolean ownsAudio;
    private String controlsPage = "root";
    private long controlsPageRequest;
    private boolean wheelEnabled;
    private int selectedWheelSlot = -1;
    public final ActionWheelAPI actionWheel = new ActionWheelAPI(this);
    public final PreviewMolang molang;
    public final PreviewControlRuntime controls;
    public final org.figuramc.figura.lua.api.keybind.KeybindAPI keybinds =
        new org.figuramc.figura.lua.api.keybind.KeybindAPI(this);
    private final JsonObject configuredInput;
    private PreviewYsmRuntime ysm;
    private final top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime.Press wheelPress =
        new top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime.Press();

    public PreviewLua(Program program, JsonObject states, long seed, PreviewMolang molang) throws IOException {
        this(program, states, seed, molang, new PreviewAudio(), true, null);
    }

    PreviewLua(Program program, JsonObject states, long seed, PreviewMolang molang, PreviewAudio audio)
        throws IOException {
        this(program, states, seed, molang, audio, false, null);
    }

    PreviewLua(Program program, JsonObject states, long seed, PreviewMolang molang, PreviewAudio audio,
        PreviewSettings settings) throws IOException {
        this(program, states, seed, molang, audio, false, settings);
    }

    private PreviewLua(Program program, JsonObject states, long seed, PreviewMolang molang, PreviewAudio audio,
        boolean ownsAudio, PreviewSettings settings) throws IOException {
        this.program = program;
        this.audio = audio;
        this.ownsAudio = ownsAudio;
        controls = new PreviewControlRuntime(settings);
        sounds = new PreviewSounds(audio, this, program.sounds(), seed);
        configuredInput = states.deepCopy();
        this.molang = molang;
        boolean water = false;
        if (states.has("particles") && states.getAsJsonObject("particles").has("water")) {
            var value = states.getAsJsonObject("particles").get("water");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new LuaError("states.particles.water must be boolean");
            }
            water = value.getAsBoolean();
        }
        particles = molang.effects == null ? new PreviewParticles(seed, water) : molang.effects.particles.particles;
        // Same library set and compiler as FiguraLuaRuntime, without OS/file/package/Java bridges.
        globals.load(new JseBaseLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new JseStringLib());
        globals.load(new JseMathLib());
        LuaC.install(globals);
        globals.load(new DebugLib());
        LuaTable debug = globals.get("debug").checktable();
        setHook = debug.get("sethook").checkfunction();
        getInfo = debug.get("getinfo").checkfunction();
        try (var resource = PreviewLua.class.getResourceAsStream("/assets/omni-morph/scripts/sandbox.lua")) {
            if (resource == null) {
                throw new IOException("Missing original Figura sandbox.lua resource");
            }
            globals.load(new String(resource.readAllBytes(), StandardCharsets.UTF_8), "sandbox").call();
        }
        LuaString.s_metatable = new ReadOnlyLuaTable(LuaString.s_metatable);
        for (Class<?> type : List.of(LuaEvent.class, EventsAPI.class, AvatarControlDefinition.class,
                 AvatarControlType.class, AvatarControlsAPI.class, Action.class, Page.class, ActionWheelAPI.class)) {
            types.generateMetatableFor(type);
        }
        globals.set("events", types.javaToLua(events).arg1());
        globals.set("avatar_controls", types.javaToLua(new AvatarControlsAPI(this)).arg1());
        globals.set("action_wheel", types.javaToLua(actionWheel).arg1());
        globals.set("preview", new ReadOnlyLuaTable(stateTable(states)));
        for (Class<?> type : List.of(org.figuramc.figura.math.vector.FiguraVec2.class,
                 org.figuramc.figura.math.vector.FiguraVec3.class, org.figuramc.figura.math.vector.FiguraVec4.class,
                 org.figuramc.figura.math.matrix.FiguraMat2.class, org.figuramc.figura.math.matrix.FiguraMat3.class,
                 org.figuramc.figura.math.matrix.FiguraMat4.class, org.figuramc.figura.lua.api.math.VectorsAPI.class,
                 org.figuramc.figura.lua.api.math.MatricesAPI.class)) {
            types.generateMetatableFor(type);
        }
        globals.set("vectors", types.javaToLua(org.figuramc.figura.lua.api.math.VectorsAPI.INSTANCE).arg1());
        globals.set("matrices", types.javaToLua(org.figuramc.figura.lua.api.math.MatricesAPI.INSTANCE).arg1());
        try (var resource = PreviewLua.class.getResourceAsStream("/assets/omni-morph/scripts/math.lua")) {
            if (resource == null) {
                throw new IOException("Missing original Figura math.lua resource");
            }
            globals.load(new String(resource.readAllBytes(), StandardCharsets.UTF_8), "math").call();
        }
        globals.set("pings", new LuaTable());
        globals.set("client", PreviewClient.create(states));
        globals.set("player", PreviewSimulatedApi.player(states, types));
        globals.set("host", PreviewConfiguredQueries.create(states, "host"));
        globals.set("world", PreviewWorldClock.create(states, () -> tick));
        types.generateMetatableFor(org.figuramc.figura.lua.api.particle.ParticleAPI.class);
        types.generateMetatableFor(org.figuramc.figura.lua.api.particle.LuaParticle.class);
        globals.set(
            "particles", types.javaToLua(new org.figuramc.figura.lua.api.particle.ParticleAPI(particles)).arg1());
        types.generateMetatableFor(org.figuramc.figura.lua.api.sound.SoundAPI.class);
        types.generateMetatableFor(org.figuramc.figura.lua.api.sound.LuaSound.class);
        globals.set("sounds", types.javaToLua(new org.figuramc.figura.lua.api.sound.SoundAPI(sounds)).arg1());
        types.generateMetatableFor(org.figuramc.figura.lua.api.keybind.FiguraKeybind.class);
        types.generateMetatableFor(org.figuramc.figura.lua.api.keybind.KeybindAPI.class);
        globals.set("keybinds", types.javaToLua(keybinds).arg1());
        globals.get("math").get("randomseed").call(LuaValue.valueOf(seed));
        globals.set("require", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Path path = PathUtils.getPath(args.checkstring(1));
                Path directory = PathUtils.getWorkingDirectory(getInfo);
                return module(PathUtils.computeSafeString(PathUtils.isAbsolute(path) ? path : directory.resolve(path)));
            }
        });
        // OMP FiguraLuaRuntime.listFiles: enumerate the actual loaded model script inventory.
        globals.set("listFiles", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue folderPath, LuaValue includeSubfolders) {
                Path path = PathUtils.getPath(folderPath);
                Path directory = PathUtils.getWorkingDirectory(getInfo);
                Path target = (PathUtils.isAbsolute(path) ? path : directory.resolve(path)).normalize();
                boolean recursive = !includeSubfolders.isnil() && includeSubfolders.checkboolean();
                int index = 1;
                var result = new LuaTable();
                for (String name : program.sources().keySet()) {
                    Path script = PathUtils.getPath(name);
                    if (!script.startsWith(target)) {
                        continue;
                    }
                    if (!recursive && target.relativize(script).getNameCount() != 1) {
                        continue;
                    }
                    result.set(index++, LuaValue.valueOf(name.replace('/', '.')));
                }
                return result;
            }
        });
        installBaseFunctions();
        exportMetatables();
    }

    private void installBaseFunctions() {
        // OMP FiguraLuaRuntime.loadExtraLibraries: named userdata and custom table types.
        globals.set("type", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                if (value.type() == LuaValue.TUSERDATA) {
                    return LuaValue.valueOf(types.getTypeName(value.checkuserdata().getClass()));
                }
                if (value.type() == LuaValue.TTABLE && value.getmetatable() != null) {
                    LuaValue named = value.getmetatable().rawget("__type");
                    if (!named.isnil()) {
                        return named;
                    }
                }
                return LuaValue.valueOf(value.typename());
            }
        });
        // Both OMP iterator wrappers dispatch a supplied metamethod, then use the original library.
        for (String functionName : List.of("pairs", "ipairs")) {
            LuaFunction original = globals.get(functionName).checkfunction();
            globals.set(functionName, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs arguments) {
                    LuaValue value = arguments.arg1();
                    if ((value.type() == LuaValue.TTABLE || value.type() == LuaValue.TUSERDATA)
                        && value.getmetatable() != null) {
                        LuaValue iterator = value.getmetatable().rawget("__" + functionName);
                        if (iterator.isfunction()) {
                            return iterator.invoke(arguments);
                        }
                    }
                    return original.invoke(arguments);
                }
            });
        }
    }

    private void exportMetatables() {
        var exported = new LuaTable();
        types.dumpMetatables(exported);
        globals.set("figuraMetatables", exported);
    }

    private PreviewFiguraParts.State figura;
    private PreviewVanillaModel.State vanilla;

    void attachVanilla(PreviewVanillaModel definition) {
        vanilla = definition.newState();
        vanilla.install(types, globals);
        exportMetatables();
    }

    void updateVanilla(org.joml.Matrix4f[] palette) {
        updateVanilla(palette, null);
    }

    void updateVanilla(org.joml.Matrix4f[] palette, PreviewNativeVertices nativeVertices) {
        if (vanilla != null)
            vanilla.update(palette, nativeVertices);
    }

    boolean vanillaVisible() {
        return vanilla != null && vanilla.visible();
    }

    Map<String, Boolean> vanillaVisibility() {
        return vanilla == null ? Map.of() : vanilla.visibility();
    }
    public final java.util.Map<Integer, org.figuramc.figura.animation.Animation> animations =
        new java.util.LinkedHashMap<>();
    private float animationClock;

    public float animationClock() {
        return animationClock;
    }

    void animationClock(float time) {
        animationClock = time;
    }

    public LuaValue loadScript(String name, String source) {
        return globals.load(source, name);
    }

    public Varargs callAnimation(LuaValue function, Object... arguments) {
        return bounded(() -> function.invoke(arguments(arguments)));
    }

    void attachFigura(PreviewFiguraParts definition) {
        figura = definition.newState();
        globals.set("avatar", PreviewAvatarData.create(definition));
        figura.install(types, globals);
        figura.initializeAnimations(this);
        types.generateMetatableFor(org.figuramc.figura.animation.Animation.class);
        types.generateMetatableFor(org.figuramc.figura.lua.api.AnimationAPI.class);
        globals.set("animations", types.javaToLua(new org.figuramc.figura.lua.api.AnimationAPI(this)).arg1());
        exportMetatables();
    }

    PreviewFiguraParts.State figura() {
        return figura;
    }

    public void initialize() {
        bounded(() -> {
            for (String entry : program.entries()) {
                module(entry);
            }
            controls.hydrateInitial(this);
            controls.syncAll(this);
            if (configuredInput.has("keybinds")) {
                for (var entry : configuredInput.getAsJsonObject("keybinds").entrySet()) {
                    if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean()) {
                        throw new LuaError("states.keybinds." + entry.getKey() + " must be boolean");
                    }
                    var binding = keybinds.getKeybinds().get(entry.getKey());
                    if (binding == null)
                        throw new LuaError("Unknown configured keybind: " + entry.getKey());
                    binding.setDown(entry.getValue().getAsBoolean(), 0);
                }
            }
            events.ENTITY_INIT.call(LuaValue.NONE);
            return LuaValue.NONE;
        });
    }

    public Varargs event(String name, Object... arguments) {
        LuaEvent event = events.__index(name);
        if (event == null) {
            throw new IllegalArgumentException("Unknown Lua event: " + name);
        }
        return bounded(() -> event.call(arguments(arguments)));
    }

    public boolean keybindGui() {
        if (!configuredInput.has("guiOpen"))
            return false;
        var value = configuredInput.get("guiOpen");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new LuaError("states.guiOpen must be boolean");
        }
        return value.getAsBoolean();
    }

    public String vanillaKey(String id) {
        var keys = configuredInput.has("vanillaKeybinds") ? configuredInput.getAsJsonObject("vanillaKeybinds") : null;
        if (keys == null || !keys.has(id))
            throw new LuaError("Missing simulated vanilla key: states.vanillaKeybinds." + id);
        var value = keys.get(id);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new LuaError("states.vanillaKeybinds." + id + " must be a key identifier");
        }
        PreviewKeys.parse(value.getAsString());
        return value.getAsString();
    }

    public Varargs run(LuaFunction function, Object... arguments) {
        return bounded(() -> function.invoke(arguments(arguments)));
    }

    public Varargs evaluate(String source) {
        return bounded(() -> globals.load(source, "preview-evaluation").invoke());
    }

    void applyControls(JsonObject values) {
        for (var entry : values.entrySet()) {
            if (!controls.setValue(this, entry.getKey(), types.luaToJava(json(entry.getValue())))) {
                throw new IllegalArgumentException("Configured model control does not exist: " + entry.getKey());
            }
        }
    }

    void frame(float time) {
        advanceTicks(time);
        render(time);
    }

    @Override
    public void close() {
        sounds.close();
        if (ownsAudio)
            audio.close();
    }

    void advanceTicks(float time) {
        int targetTick = (int) Math.floor(time * 20.0);
        while (tick < targetTick) {
            tick++;
            animationClock = tick / 20f;
            molang.stepClock(tick / 20f, 1f / 20);
            particles.tick();
            event("WORLD_TICK");
            event("TICK");
        }
    }

    void render(float time) {
        sounds.update();
        animationClock = time;
        molang.clock(time, 0);
        int targetTick = (int) Math.floor(time * 20.0);
        float partialTick = (float) (time * 20.0 - targetTick);
        event("WORLD_RENDER", partialTick);
        event("RENDER", partialTick, "RENDER");
        if (figura != null) {
            figura.animate(this);
        }
    }

    void postFrame(float time) {
        float partialTick = (float) (time * 20.0 - Math.floor(time * 20.0));
        event("POST_RENDER", partialTick, "RENDER");
        event("POST_WORLD_RENDER", partialTick);
    }

    public void openControlsPage(String page) {
        controlsPage = page == null ? "root" : page;
        controlsPageRequest++;
    }

    public String controlsPage() {
        return controlsPage;
    }

    public long controlsPageRequest() {
        return controlsPageRequest;
    }

    public void wheelState(boolean enabled, int selected) {
        wheelEnabled = enabled;
        selectedWheelSlot = selected;
    }

    public boolean wheelEnabled() {
        return wheelEnabled;
    }

    public int selectedWheelSlot() {
        return selectedWheelSlot;
    }

    public void executeWheel(int slot, boolean left) {
        bounded(() -> {
            if (!wheelEnabled) {
                selectedWheelSlot = -1;
                return LuaValue.NONE;
            }
            if (actionWheel.execute(this, left)) {
                return LuaValue.NONE;
            }
            Page page = actionWheel.currentPage;
            if (slot >= 0 && slot < 8 && page != null) {
                Action action = page.slots()[slot];
                if (action != null) {
                    action.execute(this, left, wheelPress);
                }
            }
            selectedWheelSlot = -1;
            return LuaValue.NONE;
        });
    }

    public void scrollWheel(double delta) {
        bounded(() -> {
            if (!wheelEnabled || actionWheel.mouseScroll(this, delta)) {
                return LuaValue.NONE;
            }
            Page page = actionWheel.currentPage;
            if (page == null) {
                return LuaValue.NONE;
            }
            if (selectedWheelSlot >= 0 && selectedWheelSlot < 8) {
                Action action = page.slots()[selectedWheelSlot];
                if (action != null && action.scroll != null) {
                    action.mouseScroll(this, delta);
                    return LuaValue.NONE;
                }
            }
            page.setSlotsShift(page.getSlotsShift() - (int) Math.signum(delta));
            return LuaValue.NONE;
        });
    }

    public void triggerYsmAction(String id) {
        if (ysm == null) {
            throw new IllegalStateException("Enable animation.actions for YSM declarative actions");
        }
        ysm.actions().trigger(id);
    }

    public void pressYsmAction(top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime.Press input, String id) {
        if (ysm == null) {
            throw new IllegalStateException("Enable animation.actions for YSM declarative actions");
        }
        input.press(ysm.actions(), id);
    }

    public void releaseWheelInput() {
        wheelPress.release();
    }

    void attachYsm(PreviewActionPlayer.Definition definition, PreviewMesh mesh, String language) {
        ysm = new PreviewYsmRuntime(this, definition, mesh, language);
    }

    PreviewYsmRuntime ysm() {
        return ysm;
    }

    void inputTime(float time) {
        animationClock = time;
        if (ysm != null) {
            ysm.clock(time);
        }
    }

    private Varargs module(String name) {
        Varargs cached = loaded.get(name);
        if (cached != null) {
            return cached;
        }
        if (loading.contains(name)) {
            throw new LuaError("Detected circular dependency in script " + loading.peek());
        }
        String source = program.sources().get(name);
        if (source == null) {
            throw new LuaError("Tried to require nonexistent script \"" + name + "\"!");
        }
        loading.push(name);
        try {
            Path path = Path.of(name);
            Varargs result =
                globals.load(source, name)
                    .invoke(LuaValue.varargsOf(LuaValue.valueOf(PathUtils.computeSafeString(path.getParent())),
                        LuaValue.valueOf(PathUtils.computeSafeString(path.getFileName()))));
            if (result == LuaValue.NIL) {
                result = LuaValue.TRUE;
            }
            loaded.put(name, result);
            return result;
        } finally {
            loading.pop();
        }
    }

    private Varargs arguments(Object[] values) {
        LuaValue[] converted = new LuaValue[values.length];
        for (int index = 0; index < values.length; index++) {
            converted[index] = types.javaToLua(values[index]).arg1();
        }
        return LuaValue.varargsOf(converted);
    }

    private Varargs bounded(Supplier<Varargs> action) {
        boolean outer = callDepth++ == 0;
        if (outer) {
            globals.running.state.bytecodes = 0;
            setHook.invoke(LuaValue.varargsOf(new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    // An Error crosses Lua pcall as well; model scripts cannot swallow the host budget.
                    throw new BudgetSignal();
                }
            }, LuaValue.EMPTYSTRING, LuaValue.valueOf(INSTRUCTION_LIMIT)));
        }
        try {
            return action.get();
        } catch (BudgetSignal exhausted) {
            if (!outer) {
                throw exhausted;
            }
            throw new LuaError("Preview Lua instruction budget exceeded");
        } finally {
            callDepth--;
        }
    }

    private static LuaTable stateTable(JsonObject states) {
        LuaTable table = new LuaTable();
        for (var entry : states.entrySet()) {
            table.set(entry.getKey(), json(entry.getValue()));
        }
        return table;
    }

    private static LuaValue json(JsonElement value) {
        if (value.isJsonObject()) {
            return stateTable(value.getAsJsonObject());
        }
        if (value.isJsonArray()) {
            LuaTable table = new LuaTable();
            for (JsonElement element : value.getAsJsonArray()) {
                table.insert(0, json(element));
            }
            return table;
        }
        if (value.isJsonNull()) {
            return LuaValue.NIL;
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return LuaValue.valueOf(primitive.getAsBoolean());
        }
        return primitive.isNumber() ? LuaValue.valueOf(primitive.getAsDouble())
                                    : LuaValue.valueOf(primitive.getAsString());
    }

    private static final class BudgetSignal extends Error {
        private static final long serialVersionUID = 1;
    }
}
