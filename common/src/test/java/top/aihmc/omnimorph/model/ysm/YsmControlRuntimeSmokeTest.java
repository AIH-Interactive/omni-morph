package top.aihmc.omnimorph.model.ysm;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.Bootstrap;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlRuntime;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.avatar.control.AvatarControlValueStore;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.action.YsmActionSchemaParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/** Exercises the real schema, Molang interpreter and disk store, without a GUI fixture. */
public final class YsmControlRuntimeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("figura-control-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            testExpressions();
            testPersistence();
            System.out.println("M5 control runtime: complete expressions, live forms, reset and model-scoped disk restore PASS; GUI pending.");
        } finally {
            clearStore();
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected control fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void testExpressions() throws Exception {
        Fixture fixture = new Fixture("model-a");
        ListTag functions = new ListTag();
        CompoundTag function = new CompoundTag();
        function.putString("path", "functions/double.molang");
        function.putByteArray("data", "return args[0]*2;".getBytes(StandardCharsets.UTF_8));
        functions.add(function);
        fixture.runtime.functions().load(functions);
        AvatarControlDefinition radio = YsmActionSchemaParser.readConfigForm("style", 0,
                JsonParser.parseString("""
                        {"type":"radio","value":"v.style","labels":{
                         "Original":"v.result=0;",
                         "Changed":"v.result=0; loop(3,{v.result=v.result+1;}); v.style=1; v.equal=(v.result==3); v.after=fn.double(v.result);"}}
                        """).getAsJsonObject());
        fixture.owner.controls.register(radio);
        require(fixture.owner.controls.setValue(fixture.owner, radio.id(), "Changed"), "radio change accepted");
        number(3, fixture.eval("v.result"), "full loop command executes once");
        require(Boolean.TRUE.equals(fixture.eval("v.equal")), "equality in assignment does not split the command");
        number(6, fixture.eval("v.after"), "model function receives the command result");
        fixture.owner.controls.hydrateLiveValues(fixture.owner);
        require("1".equals(radio.getValue()), "radio stores the evaluated option index as its existing string type");

        AvatarControlDefinition readExpression = YsmActionSchemaParser.readConfigForm("style", 1,
                JsonParser.parseString("""
                        {"type":"radio","value":"v.style==1","labels":{"Original":"v.style=0;","Changed":"v.style=1;"}}
                        """).getAsJsonObject());
        fixture.owner.controls.register(readExpression);
        fixture.owner.controls.hydrateLiveValues(fixture.owner);
        require("1".equals(readExpression.getValue()), "radio value expression is evaluated rather than used as a variable name");
        fixture.owner.controls.setValue(fixture.owner, readExpression.id(), "Original");
        number(0, fixture.eval("v.style"), "option write command is distinct from read expression");

        AvatarControlDefinition checkbox = YsmActionSchemaParser.readConfigForm("style", 2,
                JsonParser.parseString("{\"type\":\"checkbox\",\"value\":\"v.enabled\"}").getAsJsonObject());
        fixture.owner.controls.register(checkbox);
        fixture.owner.controls.setValue(fixture.owner, checkbox.id(), true);
        number(1, fixture.eval("v.enabled"), "checkbox writes numeric Molang true");
        fixture.eval("v.enabled=0;");
        fixture.owner.controls.hydrateLiveValues(fixture.owner);
        require(Boolean.FALSE.equals(checkbox.getValue()), "checkbox reads live model changes");
        fixture.eval("v.enabled=-1;");
        fixture.owner.controls.hydrateLiveValues(fixture.owner);
        require(Boolean.FALSE.equals(checkbox.getValue()), "reference checkbox truth requires a positive number");

        AvatarControlDefinition slider = YsmActionSchemaParser.readConfigForm("style", 3,
                JsonParser.parseString("""
                        {"type":"slider","value":"v.amount","min":0.1,"max":1,"step":0.05,"default":0.75}
                        """).getAsJsonObject());
        fixture.owner.controls.register(slider);
        number(0.75f, slider.getDefault(), "explicit slider default retained");
        fixture.owner.controls.setValue(fixture.owner, slider.id(), 0.55);
        number(0.55f, fixture.eval("v.amount"), "slider writes bound variable");
        fixture.owner.controls.reset(fixture.owner, slider.id());
        number(0.75f, fixture.eval("v.amount"), "reset executes the original default");
        fixture.owner.controls.setValue(fixture.owner, slider.id(), 0.4);
        fixture.owner.controls.resetAll(fixture.owner);
        number(0.75f, fixture.eval("v.amount"), "reset all includes live forms");

        AvatarControlDefinition earlyReturn = new AvatarControlDefinition("return", AvatarControlType.ENUM)
                .addOption("run").addOptionCommand("run", "v.before=1; return 4; v.unreachable=1;")
                .setPersistent(false);
        fixture.eval("v.unreachable=0;");
        fixture.owner.controls.register(earlyReturn);
        fixture.owner.controls.setValue(fixture.owner, earlyReturn.id(), "run");
        number(1, fixture.eval("v.before"), "commands before return execute");
        number(0, fixture.eval("v.unreachable"), "return terminates command list");

        AvatarControlDefinition budget = new AvatarControlDefinition("budget", AvatarControlType.ENUM)
                .addOption("run").addOptionCommand("run", "loop(1024,{loop(1024,{v.step=v.step+1;});});")
                .setPersistent(false);
        fixture.owner.controls.register(budget);
        fixture.owner.controls.setValue(fixture.owner, budget.id(), "run");
        float steps = ((Number) fixture.eval("v.step")).floatValue();
        require(steps > 0 && steps < 1024 * 1024, "command budget actually interrupts the nested loop");
        number(7, fixture.eval("v.recovery=7; v.recovery;"), "bounded failure does not poison the next expression");
        fixture.owner.getAvatarBindings().register("inspect_control_effects", (Function) (context, arguments) ->
                fixture.runtime.functions().clientEffectsEnabled());
        AvatarControlDefinition effects = new AvatarControlDefinition("effects", AvatarControlType.ENUM)
                .addOption("run").addOptionCommand("run", "v.control_effects=inspect_control_effects();")
                .setPersistent(false);
        fixture.owner.controls.register(effects);
        fixture.owner.controls.setValue(fixture.owner, effects.id(), "run");
        require(Boolean.TRUE.equals(fixture.eval("v.control_effects")), "user option commands enable client effects");
        require(!fixture.runtime.functions().clientEffectsEnabled(), "client effect scope is restored after command execution");
    }

    private static void testPersistence() throws Exception {
        clearStore();
        Fixture existingFigura = new Fixture("ordinary-seed");
        existingFigura.owner.controls.register(new AvatarControlDefinition("ysm.v_amount", AvatarControlType.SLIDER)
                .setDefault(0.2).setBinding("v.amount"));
        existingFigura.owner.controls.setValue(existingFigura.owner, "ysm.v_amount", 0.2);
        Fixture first = new Fixture("model-a");
        first.addForms();
        first.eval("v.amount=0.6; v.enabled=1; v.style=1; v.side_effect=4;");
        first.runtime.restoreControls();
        number(0.6f, first.eval("v.amount"), "unsaved implicit default reads initial model expression");
        number(0.75f, first.eval("v.explicit"), "explicit schema default overrides initial model value");
        number(4, first.eval("v.side_effect"), "reading an unsaved radio does not execute its write command");
        first.owner.controls.setValue(first.owner, "ysm.v_amount", 0.85);
        first.owner.controls.setValue(first.owner, "ysm.v_enabled", false);
        first.owner.controls.setValue(first.owner, "ysm.v_style", "Long");
        require(Files.isRegularFile(top.aihmc.omnimorph.FiguraMod.getCacheDirectory().resolve("avatar_controls.nbt")),
                "actual persistent NBT file written");
        try (var input = Files.newInputStream(top.aihmc.omnimorph.FiguraMod.getCacheDirectory().resolve("avatar_controls.nbt"))) {
            CompoundTag saved = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            int modelValues = 0;
            for (var entry : saved.getListOrEmpty("avatars")) {
                if (!(entry instanceof CompoundTag avatar) || !avatar.getStringOr("avatar", "").contains(":ysm:")) {
                    continue;
                }
                for (var value : avatar.getListOrEmpty("controls")) {
                    CompoundTag control = (CompoundTag) value;
                    require(control.getStringOr("id", "").matches("control:[0-9a-f]{64}"),
                            "model control identity hides source binding expressions");
                    require(!control.getStringOr("type", "").equals("string"),
                            "model radio selection is numeric rather than the original label");
                    modelValues++;
                }
            }
            require(modelValues == 3, "all three model preferences inspected in the actual disk cache");
        }

        Fixture second = new Fixture("model-b");
        second.addForms();
        second.eval("v.amount=0.3; v.enabled=1;");
        second.runtime.restoreControls();
        number(0.3f, second.eval("v.amount"), "same player and control ID use a separate model cache");
        number(1, second.eval("v.enabled"), "same-ID model checkbox remains isolated");
        second.owner.controls.setValue(second.owner, "ysm.v_amount", 0.4);

        clearStore();
        Fixture restored = new Fixture("model-a");
        restored.addForms();
        restored.eval("v.amount=0.1; v.enabled=1;");
        restored.runtime.restoreControls();
        number(0.85f, restored.eval("v.amount"), "disk-reloaded saved slider wins over initial model value");
        number(0, restored.eval("v.enabled"), "disk-reloaded checkbox restores false");
        number(1, restored.eval("v.style"), "disk-reloaded radio executes its option command");
        number(9, restored.eval("v.side_effect"), "full option command runs during restore");
        number(1, restored.eval("v.restore_calls"), "restored function command runs once after model attachment");
        restored.eval("v.amount=0.5;");
        restored.owner.controls.hydrateLiveValues(restored.owner);
        number(0.5f, restored.owner.controls.getValue("ysm.v_amount"), "persistent form still reads live script state");

        first.owner.controls.clear();
        install(first.owner, "ysmRuntime", null);
        require(first.owner.controls.all().isEmpty(), "unloading clears prior model definitions");
        AvatarControlDefinition switched = new AvatarControlDefinition("ysm.v_amount", AvatarControlType.SLIDER)
                .setDefault(0.2).setBinding("v.amount");
        first.owner.controls.register(switched);
        first.owner.controls.loadSavedValues(first.owner);
        first.owner.controls.syncAll(first.owner);
        number(0.2f, first.eval("v.amount"), "same Avatar unload clears the old YSM persistence scope");
        AvatarControlDefinition ordinaryToggle = new AvatarControlDefinition("ordinary.toggle", AvatarControlType.TOGGLE)
                .setBinding("v.negative").setPersistent(false);
        first.owner.controls.register(ordinaryToggle);
        first.eval("v.negative=-1;");
        first.owner.controls.hydrateLiveValues(first.owner);
        require(Boolean.TRUE.equals(ordinaryToggle.getValue()), "ordinary Figura preserves nonzero numeric toggle truth");

        // Ordinary Figura controls retain the existing owner-only key and are isolated from YSM model keys.
        Fixture figura = new Fixture("ordinary-figura");
        AvatarControlDefinition ordinary = new AvatarControlDefinition("ysm.v_amount", AvatarControlType.SLIDER)
                .setDefault(0.2).setBinding("v.amount");
        figura.owner.controls.register(ordinary);
        figura.owner.controls.loadSavedValues(figura.owner);
        figura.owner.controls.syncAll(figura.owner);
        number(0.2f, figura.eval("v.amount"), "ordinary Figura does not inherit YSM settings");
        figura.owner.controls.setValue(figura.owner, ordinary.id(), 0.7);
        clearStore();
        ordinary.setValue(0.2);
        figura.owner.controls.loadSavedValues(figura.owner);
        number(0.7f, ordinary.getValue(), "ordinary Figura disk restore remains compatible");
    }

    private static void clearStore() throws Exception {
        Field values = AvatarControlValueStore.class.getDeclaredField("VALUES");
        values.setAccessible(true);
        ((Map<?, ?>) values.get(null)).clear();
        Field loaded = AvatarControlValueStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
    }

    private static final class Fixture {
        private final Avatar owner;
        private final YsmModelRuntime runtime;
        private final MolangEngine engine;

        private Fixture(String key) throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            owner = (Avatar) ((Unsafe) field.get(null)).allocateInstance(Avatar.class);
            install(owner, "owner", new UUID(0, 5));
            install(owner, "controls", new AvatarControlRuntime());
            install(owner, "molangContext", new Avatar.MolangContext(owner));
            Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            runtime = constructor.newInstance(owner, new YsmGeometry(), null, null, "fixture", false,
                    "NEW", key, Map.of(), 1f, 1f, false);
            install(owner, "ysmRuntime", runtime);
            engine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
            CompoundTag function = new CompoundTag();
            function.putString("path", "functions/restore_value.molang");
            function.putByteArray("data", "v.restore_calls=v.restore_calls+1; return 9;".getBytes(StandardCharsets.UTF_8));
            ListTag functions = new ListTag();
            functions.add(function);
            runtime.functions().load(functions);
        }

        private Object eval(String source) throws Exception {
            return ExpressionEvaluator.evaluator(owner.getMolangContext()).evalAll(engine.parse(source), true);
        }

        private void addForms() {
            YsmActionSchemaParser.apply(runtime, "ysm.json", """
                    {"properties":{"extra_animation_buttons":[{"id":"style","config_forms":[
                     {"type":"checkbox","value":"v.enabled"},
                     {"type":"range","value":"v.amount","min":0.1,"max":1,"step":0.05},
                     {"type":"slider","value":"v.explicit","min":0.1,"max":1,"default":0.75},
                     {"type":"radio","value":"v.style","labels":{
                      "Short":"v.style=0;","Long":"v.style=1; v.side_effect=fn.restore_value();"}}]}]}}
                    """);
        }
    }

    private static void install(Avatar owner, String name, Object value) throws Exception {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void number(float expected, Object value, String message) {
        require(value instanceof Number number && Math.abs(number.floatValue() - expected) < 0.0001f,
                message + "; expected=" + expected + " actual=" + value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
