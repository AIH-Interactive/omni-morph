package top.aihmc.omnimorph.wizards;

import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.FiguraSkeletonAdapter;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.figuramc.figura.wizards.WizardEntry;
/** Uses the existing native source harness; actual windows and rendered particles remain pending. */
public final class CosmeticCreationSmokeTest {
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("cosmetic-creation-");
        try {
            Path seed = Files.createDirectories(data.resolve("seed/model"));
            try (var input = CosmeticCreationSmokeTest.class.getResourceAsStream(
                    "/assets/omni-morph/cosmetic_templates/accessories/flexible-pennant/model/model.bbmodel")) {
                Files.copy(java.util.Objects.requireNonNull(input), seed.resolve("model.bbmodel"));
            }
            Files.writeString(seed.getParent().resolve("avatar.json"), "{\"name\":\"Creation test seed\"}");
            top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.replayFiguraModel(
                    seed.getParent(), ignored -> {
                        try {
                            run(data);
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    });
        } finally {
            try (var files = Files.walk(data)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    public static void run(Path data) throws Exception {
        require(CosmeticTemplate.values().length == 16
                        && java.util.Arrays.stream(CosmeticTemplate.values())
                        .filter(value -> value.resourceId() != null).count() == 7,
                "The existing list retains nine presets and adds seven distinct bundled presets");
        var toggle = new top.aihmc.omnimorph.avatar.control.AvatarControlDefinition("default-test",
                top.aihmc.omnimorph.avatar.control.AvatarControlType.TOGGLE);
        toggle.setDefault(false).setDefault(true);
        require(Boolean.TRUE.equals(toggle.getValue()), "Authored default replaces the factory default");
        toggle.setValue(false).setDefault(true);
        require(Boolean.FALSE.equals(toggle.getValue()), "Stored/explicit false overrides an authored default");
        Path root = data.resolve("created-cosmetics");
        for (var template : CosmeticTemplate.values()) {
            String anchor = template == CosmeticTemplate.FEET ? "right_foot_anchor" : template.anchor();
            var transform = new OutfitConfig.Transform(new OutfitConfig.Vector(1, 2, 3),
                    new OutfitConfig.Vector(0, 15, 0), new OutfitConfig.Vector(1, 1, 1));
            String name = template.resourceId() == null ? template.category() : template.resourceId();
            Path created = CosmeticCreator.create(root, name, "作者", "模板描述", template, anchor, transform);
            var description = LocalCosmeticSource.describe(created, () -> false);
            var metadata = description.metadata();
            require(metadata.category().equals(template.category()) && metadata.cosmeticAnchor().equals(anchor)
                            && metadata.defaultTransform().equals(transform), "Creation metadata: " + template);
            var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                    null, description.contentHash(), OutfitConfig.Format.FIGURA);
            var prepared = LocalCosmeticSource.prepare(created, UUID.randomUUID(), description.contentHash(), () -> false);
            UUID wearer = UUID.randomUUID();
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), reference, 1, true, metadata.defaultAnchor(),
                    transform, List.of(), Map.of(), Map.of());
            var permissions = new PermissionPack.PlayerPermissionPack(
                    new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.toString());
            var context = new CosmeticInstanceContext(UUID.randomUUID(), UUID.randomUUID(), wearer, instance,
                    OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
            Avatar avatar = Avatar.createCosmetic(context, permissions, "created template");
            try {
                avatar.load(prepared.nbt());
                avatar.initializePreviewPlayer(null);
                require(avatar.loaded && avatar.luaRuntime != null && !avatar.scriptError,
                        "Actual template Lua initialization: " + template + " / " + avatar.errorText);
                var adapter = new FiguraSkeletonAdapter(
                        (top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer) avatar.renderer,
                        reference, instance.instanceId(),
                        new SkeletonIndex.Cache(), OutfitLimits.defaults());
                require(adapter.binding().definition().hash().equals(metadata.skeletonHash()),
                        "Declared skeleton matches actual renderer: " + template);
                if (switch (template) {
                    case ORBITAL_CHARMS, PRISMATIC_BADGE, VIEW_SIGIL, PAINTED_RIBBON, SPRINT_LIGHT -> true;
                    default -> false;
                }) {
                    int rootIndex = -1;
                    for (int index = 0; index < adapter.binding().parts().size(); index++) {
                        if (adapter.binding().parts().get(index).name.equals("Cosmetic")) {
                            rootIndex = index;
                            break;
                        }
                    }
                    require(rootIndex >= 0, "Bindable Cosmetic origin exists: " + template);
                    int expectedRoot = rootIndex;
                    require(metadata.editableBones().stream().anyMatch(bone ->
                                    adapter.binding().definition().resolve(bone).orElse(-1) == expectedRoot),
                            "Cosmetic origin is declared editable: " + template);
                    require(adapter.binding().parts().get(rootIndex).children.size() == 1,
                            "Authored mesh remains below the Cosmetic origin: " + template);
                }
                for (int tick = 0; tick < 3; tick++) {
                    avatar.tick.reset(65536);
                    avatar.run("TICK", avatar.tick);
                }
                require(!avatar.scriptError, "Created template lightweight tick: " + template + " / " + avatar.errorText);
                String assertion;
                if (template.resourceId() != null) {
                    assertion = "assert(accessory ~= nil and accessory.options ~= nil)";
                    if (template == CosmeticTemplate.PAINTED_RIBBON) {
                        assertion += "; assert(cosmetic_controls:get('lifetime') == 20); "
                                + "cosmetic_controls:set('lifetime', 300); "
                                + "assert(cosmetic_controls:get('lifetime') == 300); "
                                + "assert(cosmetic_controls:get('emissive') and not cosmetic_controls:get('light')); "
                                + "assert(cosmetic_controls:get('light_interval') == 1); "
                                + "assert(cosmetic_controls:get('light_level') == 15); "
                                + "assert(cosmetic_controls:get('light_radius') == 7.5)";
                    }
                } else if (template == CosmeticTemplate.TRAIL) {
                    assertion = "assert(cosmetic_controls:get('interval') == 4); cosmetic_controls:set('star', true); "
                            + "assert(cosmetic_controls:get('emissive') and not cosmetic_controls:get('light'))";
                } else {
                    assertion = "assert(cosmetic_controls:get('visible')); cosmetic_controls:set('visible', false)";
                }
                avatar.tick.reset(65536);
                avatar.luaRuntime.run(avatar.luaRuntime.load("created-template-test", assertion), avatar.tick);
                require(!avatar.scriptError, "Created instance controls: " + template + " / " + avatar.errorText);
                if (template.resourceId() != null) {
                    for (String relative : List.of("model/model.bbmodel", "script.lua",
                            "textures/art.png", "textures/art.svg")) {
                        require(Files.isRegularFile(created.resolve(relative)), "Bundled editable asset: " + relative);
                    }
                }
                if (template == CosmeticTemplate.TAIL) {
                    require(metadata.exportedBones().size() == 1
                                    && adapter.binding().parts().get(adapter.binding().definition()
                                    .resolve(metadata.exportedBones().getFirst()).orElseThrow()).children.isEmpty(),
                            "Tailtip is an exported real empty group");
                }
                if (template == CosmeticTemplate.TRAIL) {
                    avatar.tick.reset(65536);
                    avatar.luaRuntime.run(avatar.luaRuntime.load("disable-template-emission",
                            "cosmetic_controls:set('enabled', false)"), avatar.tick);
                    avatar.render.reset(65536);
                    avatar.run("POST_RENDER", avatar.render, 0f, "FIGURA_GUI");
                    require(!avatar.scriptError, "Trail callback resolves its own native world matrix without placeholder APIs");
                    var model = JsonParser.parseString(Files.readString(created.resolve("model.bbmodel"))).getAsJsonObject();
                    require(model.getAsJsonArray("elements").isEmpty()
                                    && model.getAsJsonArray("outliner").size() == 1
                                    && model.getAsJsonArray("outliner").get(0).getAsJsonObject()
                                    .getAsJsonArray("children").isEmpty(), "Trail is exactly one empty Cosmetic root");
                    image(created.resolve("textures/sakura_petal.png"), 32, 32, 1);
                    image(created.resolve("textures/twinkle_star_sheet.png"), 128, 64, 8);
                    require(avatar.renderer.getTexture("model.sakura_petal") != null
                                    && avatar.renderer.getTexture("model.twinkle_star_sheet") != null,
                            "Actual renderer owns both names used by trail Lua");
                }
            } finally {
                avatar.clean();
            }
        }
        Path duplicate = CosmeticCreator.create(root, "Head", "", "", CosmeticTemplate.HEAD,
                "custom_socket", OutfitConfig.Transform.identity());
        require(duplicate.getFileName().toString().equals("Head_1"), "Unique accessory directory suffix");
        var wizard = new CosmeticWizard(root);
        wizard.changeEntry(WizardEntry.NAME, "向导创建");
        wizard.template(CosmeticTemplate.FLEXIBLE_PENNANT);
        wizard.anchor("explicit_socket");
        require(wizard.canBuild() && wizard.targetDirectory().equals(root.resolve("向导创建")),
                "Accessory wizard validates the displayed final target");
        wizard.buildAsync().get(20, java.util.concurrent.TimeUnit.SECONDS);
        var authored = LocalCosmeticSource.describe(root.resolve("向导创建"), () -> false).metadata();
        require(authored.category().equals("Back") && authored.cosmeticAnchor().equals("explicit_socket"),
                "Actual queued accessory wizard creates the selected template and named target");
        System.out.println("PASS created cosmetic templates: all sixteen native packages, exact runtime skeleton hashes, "
                + "authored Lua init/tick/controls, empty tailtip and Trail, owned textures and RGBA margins; "
                + "GUI/Blockbench/world particle rendering MANUAL/PENDING");
    }

    private static void image(Path path, int width, int height, int frames) throws Exception {
        var image = ImageIO.read(path.toFile());
        require(image != null && image.getWidth() == width && image.getHeight() == height
                && image.getColorModel().hasAlpha(), "RGBA dimensions: " + path);
        for (int frame = 0; frame < frames; frame++) {
            int x = frame % (width / 32) * 32;
            int y = frame / (width / 32) * 32;
            int opaque = 0;
            for (int row = 0; row < 32; row++) {
                for (int column = 0; column < 32; column++) {
                    int alpha = image.getRGB(x + column, y + row) >>> 24;
                    if (column < 2 || column >= 30 || row < 2 || row >= 30) {
                        require(alpha == 0, "Transparent cell border");
                    }
                    opaque += alpha > 0 ? 1 : 0;
                }
            }
            require(opaque > 0, "Every authored frame has visible content");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
