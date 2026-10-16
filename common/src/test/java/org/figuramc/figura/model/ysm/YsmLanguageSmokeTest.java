package org.figuramc.figura.model.ysm;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarTranslations;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import org.figuramc.figura.avatar.ysm.YsmAvatarDetector;
import org.figuramc.figura.avatar.ysm.YsmModelData;
import org.figuramc.figura.lua.api.action_wheel.Action;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.ysm.action.YsmActionSchemaParser;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native client identity only: tests text consumers, with no GUI or rendering claim. */
public final class YsmLanguageSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("figura-language-").toAbsolutePath().normalize();
        String previousDirectory = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", temporary.toString());
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousClient = instance.get(null);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            LanguageClient client = (LanguageClient) unsafe.allocateInstance(LanguageClient.class);
            client.languages = new LanguageManager("en_us", ignored -> {});
            instance.set(null, client);
            testDictionaries(client);
            testSharedConsumers(client);
            testYsmSchema(client, unsafe);
            if (args.length != 1) {
                throw new IllegalArgumentException("Provide the model corpus directory");
            }
            testListDiscovery(temporary, Path.of(args[0]));
            testCorpus(Path.of(args[0]), client);
            System.out.println("M5 model language: shared Figura/YSM text consumers, live locale, fallback, raw values and isolation PASS; GUI pending.");
        } finally {
            instance.set(null, previousClient);
            if (previousDirectory == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previousDirectory);
            }
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(temporary)) {
                        throw new IllegalStateException("Unexpected language fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void testDictionaries(LanguageClient client) {
        try (AvatarTranslations translations = dictionaries()) {
            client.languages.setSelected("ZH-cn");
            equal("中文", translations.get("title", "raw"), "current locale normalization");
            equal("raw", translations.get("english_only", "raw"), "present locale missing key keeps original");
            equal("English", translations.get("fr_fr", "title", "raw"), "absent locale uses English dictionary");
            equal("raw", translations.get("fr_fr", "absent", "raw"), "missing keys retain original");
            equal("", translations.get("zh_cn", "empty", "raw"), "empty translation retained");
            equal("12", translations.get("en_us", "number", "raw"), "primitive number accepted");
            equal("true", translations.get("en_us", "boolean", "raw"), "primitive boolean accepted");
            equal("raw", translations.get("en_us", "nested", "raw"), "nested values ignored");
            equal("作者\nraw2", translations.authors("raw1\nraw2"), "author index and original fallback");
            require(translations.invalidResourceCount() == 1, "malformed optional dictionary counted");
            translations.load(Map.of("lang/zh_cn.json", bytes("{}")));
            equal("raw", translations.get("title", "raw"), "reload removes previous dictionaries");
            translations.close();
            equal("raw", translations.get("title", "raw"), "close clears dictionaries");
        }
    }

    private static AvatarTranslations dictionaries() {
        AvatarTranslations translations = new AvatarTranslations();
        translations.load(Map.of(
                "lang/en_us.json", bytes("{\"title\":\"English\",\"english_only\":\"English only\",\"number\":12,\"boolean\":true,\"nested\":{}}"),
                "lang/zh_cn.json", bytes("{\"title\":\"中文\",\"empty\":\"\",\"metadata.authors.0.name\":\"作者\"}"),
                "lang/broken.json", bytes("{")));
        return translations;
    }

    private static void testSharedConsumers(LanguageClient client) {
        try (AvatarTranslations figura = dictionaries(); AvatarTranslations ysm = new AvatarTranslations()) {
            ysm.load(Map.of("en_us.json", bytes("{\"title\":\"YSM title\"}")));
            AvatarControlDefinition control = new AvatarControlDefinition("figura.control", AvatarControlType.ENUM)
                    .setTitle("original").setTitleSupplier(() -> figura.get("title", "original"))
                    .setDescription("description").setDescriptionSupplier(() -> figura.get("title", "description"))
                    .addOption("raw_a").addOption("raw_b").setDefault("raw_b")
                    .setOptionLabelResolver((index, raw) -> figura.get("title", raw));
            Action action = new Action().setTitle("original").setTitleSupplier(control::getTitle);
            client.languages.setSelected("en_us");
            equal("English", control.getTitle(), "shared Figura control title");
            equal("English", action.getTitle(), "shared action wheel title");
            equal("YSM title", ysm.get("title", "raw"), "model dictionaries isolated");
            client.languages.setSelected("zh_cn");
            equal("中文", control.getTitle(), "control switches locale without rebuild");
            equal("中文", control.getDescription(), "description switches locale");
            equal("中文", control.optionLabel("raw_a"), "display label is translated");
            equal("raw_b", control.value(), "stored option remains raw despite equal display labels");
            equal(List.of("raw_a", "raw_b"), control.options(), "raw options retained");
            equal("中文", action.getTitle(), "wheel switches locale without rebuild");
            action.setToggleTitle("explicit toggle").setToggled(true);
            equal("explicit toggle", action.getTitle(), "ordinary Figura toggle title preserved");
            action.setToggled(false).setTitle("Lua override");
            control.setTitle("Lua control override");
            equal("Lua override", action.getTitle(), "explicit wheel setter overrides translation");
            equal("Lua control override", control.getTitle(), "explicit control setter overrides translation");
        }
    }

    private static void testYsmSchema(LanguageClient client, Unsafe unsafe) throws Exception {
        Avatar owner = (Avatar) unsafe.allocateInstance(Avatar.class);
        install(owner, "molangContext", new Avatar.MolangContext(owner));
        install(owner, "controls", new AvatarControlRuntime());
        Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                String.class, String.class, Map.class, float.class, float.class, boolean.class);
        constructor.setAccessible(true);
        YsmModelRuntime runtime = constructor.newInstance(owner, new YsmGeometry(), null, null,
                "fixture", false, "NEW", "language-fixture", Map.of(), 1f, 1f, false);
        install(owner, "ysmRuntime", runtime);
        try {
            runtime.translations().load(Map.of("lang/zh_cn.json", bytes("""
                    {"properties.extra_animation.wave":"招手",
                     "properties.extra_animation_classify.mood.smile":"微笑",
                     "properties.extra_animation_buttons.style.name":"外观",
                     "properties.extra_animation_buttons.style.config_forms.0.title":"发型",
                     "properties.extra_animation_buttons.style.config_forms.0.description":"选择外观",
                     "properties.extra_animation_buttons.style.config_forms.0.labels.0":"短发"}
                    """)));
            YsmActionSchemaParser.apply(runtime, "ysm.json", """
                    {"properties":{"extra_animation":{"wave":"Wave"},
                     "extra_animation_classify":{"mood":{"smile":"Smile"}},
                     "extra_animation_buttons":[{"id":"style","name":"Style","config_forms":[
                     {"type":"enum","title":"Hair","description":"Choose style","value":"v.style",
                      "labels":{"short":"v.style=0;","long":"v.style=1;"},"default":"long"}]}]}}
                    """);
            client.languages.setSelected("zh_cn");
            YsmActionDefinition wave = runtime.actions().get("extra_animation.wave");
            equal("招手", wave.getTitle(), "YSM root action key consumed");
            equal("微笑", runtime.actions().get("extra_animation.smile").getTitle(), "classified action key consumed");
            equal("外观", owner.controls.get("ysm.page_link.style").getTitle(), "page link key consumed");
            AvatarControlDefinition control = owner.controls.get("ysm.v_style");
            equal("发型", control.getTitle(), "form title key consumed");
            equal("选择外观", control.getDescription(), "form description key consumed");
            equal("短发", control.optionLabel("short"), "option labels use reference numeric index");
            equal("long", control.optionLabel("long"), "missing option translation keeps original");
            equal("v.style=0;", control.optionCommands().get("short"), "translation preserves command routing");
            equal("long", control.value(), "translation preserves form default");
            client.languages.setSelected("en_us");
            equal("Wave", wave.getTitle(), "schema original retained without dictionaries");
            equal("Hair", control.getTitle(), "form switches back without parsing schema again");
        } finally {
            // This CPU fixture owns no native texture; clear its model-local dictionaries only.
            runtime.translations().close();
        }
    }

    private static void testListDiscovery(Path temporary, Path corpus) throws Exception {
        Path root = Files.createDirectory(temporary.resolve("listing"));
        Path plain = Files.createDirectory(root.resolve("plain-model"));
        Files.writeString(plain.resolve("ysm.json"),
                "{\"metadata\":{\"name\":\"Deferred Plain\"},\"files\":{\"player\":{\"model\":{\"main\":\"broken.json\"}}}}");
        Files.writeString(plain.resolve("broken.json"), "invalid geometry");
        Path figura = Files.createDirectory(root.resolve("figura-model"));
        Files.writeString(figura.resolve("avatar.json"), "{\"name\":\"Figura Fixture\"}");
        Path encrypted = root.resolve("encrypted-model.ysm");
        try (var sources = Files.list(corpus)) {
            Path original = sources.filter(org.figuramc.figura.avatar.ysm.YsmEncryptedSource::hasMagic)
                    .findFirst().orElseThrow();
            try (var input = Files.newInputStream(original)) {
                Files.write(encrypted, input.readNBytes(7));
            }
        }
        var folder = new LocalAvatarFetcher.FolderPath(root);
        require(folder.fetch() && folder.getChildren().size() == 3,
                "list discovery includes plain invalid geometry, encrypted truncated payload and original Figura without validating models");
        var plainCard = folder.getChildren().stream().filter(card -> card.getPath().equals(plain)).findFirst().orElseThrow();
        var encryptedCard = folder.getChildren().stream().filter(card -> card.getPath().equals(encrypted)).findFirst().orElseThrow();
        equal("Deferred Plain", plainCard.getName(), "plain metadata survives deferred model validation");
        require(encryptedCard.isEncryptedYsm(), "encrypted filter uses the header rather than filename guesses");
        var all = org.figuramc.figura.avatar.local.AvatarTypeFilter.ALL;
        for (var filter : org.figuramc.figura.avatar.local.AvatarTypeFilter.values()) {
            long count = folder.getChildren().stream().filter(card -> filter.matches(card, "")).count();
            require(count == (filter == all ? 3 : 1), "avatar type filter partitions discovered sources");
            require(filter.matches(folder, ""), "matching descendants keep grouping folders visible");
        }
        require(!org.figuramc.figura.avatar.local.AvatarTypeFilter.YSM_ENCRYPTED.matches(folder, "Deferred Plain"),
                "nested type and text filters must match the same descendant");
        require(org.figuramc.figura.avatar.local.AvatarTypeFilter.YSM_PLAIN.matches(folder, "Deferred"),
                "nested plain model is searchable");
        require(all.next().next().next().next() == all, "filter cycles back to all formats");
        for (Path invalid : List.of(plain, encrypted)) {
            boolean rejected = false;
            try (var ignored = YsmModelData.read(invalid, () -> false)) {
                // Activating an invalid list entry must retain the existing validation gate.
            } catch (java.io.IOException | RuntimeException expected) {
                rejected = true;
            }
            require(rejected, "activation still rejects the invalid model that discovery deferred");
        }
        System.out.println("Deferred wardrobe discovery and recursive Figura/plain/encrypted filters PASS; activation validation retained.");
    }

    private static void testCorpus(Path root, LanguageClient client) throws Exception {
        int models = 0;
        int encrypted = 0;
        int dictionaries = 0;
        int keys = 0;
        boolean plainCard = false;
        boolean encryptedCard = false;
        try (var paths = Files.list(root)) {
            for (Path path : paths.sorted().toList()) {
                if (!YsmAvatarDetector.isYsmAvatar(path)) {
                    continue;
                }
                try (YsmModelData data = YsmModelData.read(path, () -> false);
                     AvatarTranslations translations = new AvatarTranslations()) {
                    models++;
                    if (data.encrypted()) {
                        encrypted++;
                    }
                    Map<String, byte[]> resources = new LinkedHashMap<>();
                    ListTag entries = new ListTag();
                    for (String language : data.manifest().resourceIndex().languagePaths()) {
                        byte[] content = data.resources().readBytes(language);
                        resources.put(language, content);
                        CompoundTag entry = new CompoundTag();
                        entry.putString("path", language);
                        entry.putByteArray("data", content);
                        entries.add(entry);
                    }
                    translations.load(entries);
                    try (AvatarTranslations direct = new AvatarTranslations()) {
                        direct.load(resources);
                        for (var resource : resources.entrySet()) {
                            String file = resource.getKey().replace('\\', '/');
                            file = file.substring(file.lastIndexOf('/') + 1);
                            String locale = file.substring(0, file.length() - 5);
                            var object = JsonParser.parseString(new String(resource.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                            dictionaries++;
                            for (var word : object.entrySet()) {
                                if (word.getValue().isJsonPrimitive()) {
                                    String expected = word.getValue().getAsString();
                                    equal(expected, translations.get(locale, word.getKey(), "missing"), "runtime dictionary key consumed");
                                    equal(expected, direct.get(locale, word.getKey(), "missing"), "resource dictionary agrees with runtime");
                                    keys++;
                                }
                            }
                        }
                    }
                    require(translations.invalidResourceCount() == 0, "corpus dictionaries parsed");
                    if (!resources.isEmpty() && (data.encrypted() ? !encryptedCard : !plainCard)) {
                        var card = new LocalAvatarFetcher.AvatarPath(path, path.getParent());
                        for (String locale : List.of("zh_cn", "en_us", "fr_fr")) {
                            client.languages.setSelected(locale);
                            equal(data.encrypted() ? path.getFileName().toString()
                                            : translations.get(locale, "metadata.name", data.manifest().name()),
                                    card.getName(), "plain wardrobe names follow language; encrypted lists retain filenames without decrypting");
                            equal(data.encrypted() ? ""
                                            : translations.get(locale, "metadata.tips", data.manifest().description()),
                                    card.getDescription(), "encrypted private descriptions are deferred until activation");
                        }
                        if (data.encrypted()) {
                            encryptedCard = true;
                        } else {
                            plainCard = true;
                        }
                    }
                }
            }
        }
        require(models == 40 && encrypted == 5 && dictionaries > 0 && keys > 0 && plainCard && encryptedCard, "complete YSM corpus language audit");
        System.out.println("M5 language corpus: models=" + models + " encrypted=" + encrypted
                + " dictionaries=" + dictionaries + " primitiveKeys=" + keys);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void install(Avatar owner, String name, Object value) throws Exception {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void equal(Object expected, Object actual, String message) {
        require(java.util.Objects.equals(expected, actual), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class LanguageClient extends Minecraft {
        private LanguageManager languages;

        private LanguageClient() {
            super(null);
        }

        @Override
        public LanguageManager getLanguageManager() {
            return languages;
        }
    }
}
