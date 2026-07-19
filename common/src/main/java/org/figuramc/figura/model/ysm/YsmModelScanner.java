package org.figuramc.figura.model.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.ysm.YsmAvatarDetector;
import org.figuramc.figura.avatar.ysm.YsmAvatarKind;
import org.figuramc.figura.avatar.ysm.YsmManifest;
import org.figuramc.figura.avatar.ysm.YsmManifestReader;
import org.figuramc.figura.avatar.ysm.YsmPackage;
import org.figuramc.figura.avatar.ysm.YsmResourceIndex;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmAnimationControllerParser;
import org.figuramc.figura.model.ysm.controller.YsmControllerAnimationRef;
import org.figuramc.figura.model.ysm.controller.YsmControllerState;
import org.figuramc.figura.model.ysm.controller.YsmControllerTransition;
import org.figuramc.figura.model.ysm.animation.YsmAnimationParser;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.binding.MolangBindings;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class YsmModelScanner {
    private static MolangEngine scannerMolangEngine;

    private YsmModelScanner() {
    }

    public record ScanReport(Path path, YsmAvatarKind kind, String name, YsmResourceIndex resources,
                             YsmModelValidator.ValidationResult mainValidation,
                             YsmModelValidator.ValidationResult armValidation,
                             int animationClipCount, List<String> animationNames, List<String> nativeStateHits,
                             List<String> controllerNames, List<String> functionNames, List<String> eventNames,
                             List<String> actionNames, List<String> controlNames, List<String> subEntities,
                             List<String> unsupportedBindings, List<String> bones, List<String> locators,
                             List<String> attachments, List<String> firstPersonArmBones, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty()
                    && mainValidation != null && mainValidation.valid()
                    && (armValidation == null || armValidation.valid());
        }
    }

    public record ScanSummary(List<ScanReport> reports) {
        public long validCount() {
            return reports.stream().filter(ScanReport::valid).count();
        }

        public long invalidCount() {
            return reports.size() - validCount();
        }
    }

    public static ScanSummary scan(Path root) throws IOException {
        List<ScanReport> reports = new ArrayList<>();
        if (root == null || !Files.exists(root))
            return new ScanSummary(reports);

        if (YsmAvatarDetector.isYsmAvatar(root)) {
            reports.add(scanOne(root));
            return new ScanSummary(reports);
        }

        try (Stream<Path> stream = Files.walk(root, FileVisitOption.FOLLOW_LINKS)) {
            List<Path> candidates = stream
                    .filter(YsmModelScanner::couldBeYsmAvatar)
                    .sorted(Comparator.comparingInt(Path::getNameCount).thenComparing(Path::toString))
                    .toList();
            List<Path> accepted = new ArrayList<>();
            for (Path candidate : candidates) {
                if (isInsideAcceptedPackage(candidate, accepted))
                    continue;
                if (YsmAvatarDetector.isYsmAvatar(candidate)) {
                    reports.add(scanOne(candidate));
                    accepted.add(candidate);
                }
            }
        }
        return new ScanSummary(reports);
    }

    public static ScanReport scanOne(Path path) {
        List<String> errors = new ArrayList<>();
        YsmAvatarKind kind = YsmAvatarDetector.kind(path);
        YsmManifest manifest = null;
        YsmModelValidator.ValidationResult mainValidation = null;
        YsmModelValidator.ValidationResult armValidation = null;
        List<String> locators = List.of();
        List<String> bones = List.of();
        List<String> attachments = List.of();
        List<String> firstPersonArmBones = List.of();
        List<String> animationNames = new ArrayList<>();
        List<String> controllerNames = new ArrayList<>();
        List<String> functionNames = new ArrayList<>();
        List<String> eventNames = new ArrayList<>();
        List<String> actionNames = new ArrayList<>();
        List<String> controlNames = new ArrayList<>();
        List<String> subEntities = new ArrayList<>();
        LinkedHashSet<String> unsupportedBindings = new LinkedHashSet<>();
        int animationClipCount = 0;

        try {
            manifest = YsmManifestReader.read(path);
            YsmResourceIndex index = manifest.resourceIndex();
            try (YsmPackage ysmPackage = YsmPackage.open(path)) {
                if (!index.hasMainModel()) {
                    errors.add("missing main model");
                } else {
                    YsmGeometry mainGeometry = YsmGeometryParser.parse(ysmPackage.readString(index.mainModelPath()));
                    mainValidation = YsmModelValidator.validate(index.mainModelPath(), mainGeometry);
                    bones = describeBones(mainGeometry);
                    locators = describeLocators(mainGeometry);
                    attachments = describeAttachments(mainGeometry);
                }

                if (index.hasArmModel()) {
                    try {
                        YsmGeometry armGeometry = YsmGeometryParser.parse(ysmPackage.readString(index.armModelPath()));
                        armValidation = YsmModelValidator.validate(index.armModelPath(), armGeometry);
                        firstPersonArmBones = describeFirstPersonArmBones(armGeometry);
                    } catch (Throwable e) {
                        errors.add("failed to parse arm model '" + index.armModelPath() + "': " + e.getMessage());
                    }
                }

                for (String animationPath : index.animationPaths()) {
                    try {
                        String animationJson = ysmPackage.readString(animationPath);
                        Map<String, ?> clips = YsmAnimationParser.parse(animationJson, false, null);
                        animationClipCount += clips.size();
                        for (String clipName : clips.keySet())
                            animationNames.add(clipName);
                        collectUnsupportedBindings(unsupportedBindings, animationJson);
                    } catch (Throwable e) {
                        errors.add("failed to parse animation '" + animationPath + "': " + e.getMessage());
                    }
                }

                for (String controllerPath : index.animationControllerPaths()) {
                    try {
                        String controllerJson = ysmPackage.readString(controllerPath);
                        Map<String, YsmAnimationController> controllers = YsmAnimationControllerParser.parse(controllerJson, scannerMolangEngine());
                        for (YsmAnimationController controller : controllers.values()) {
                            controllerNames.add(describeController(controller));
                            validateControllerReferences(controller, animationNames, controllerPath, errors);
                        }
                        collectUnsupportedBindings(unsupportedBindings, controllerJson);
                    } catch (Throwable e) {
                        errors.add("failed to parse controller '" + controllerPath + "': " + e.getMessage());
                    }
                }

                for (String functionPath : index.functionPaths()) {
                    try {
                        String functionJson = ysmPackage.readString(functionPath);
                        YsmMolangFunctionName function = YsmMolangFunctionName.parse(functionPath);
                        if (!function.functionName().isBlank())
                            functionNames.add(function.functionName() + " path=" + functionPath);
                        if (!function.eventName().isBlank())
                            eventNames.add(function.eventName() + " path=" + functionPath);
                        collectUnsupportedBindings(unsupportedBindings, functionJson);
                    } catch (Throwable e) {
                        errors.add("failed to parse function '" + functionPath + "': " + e.getMessage());
                    }
                }

                scanActionSchemas(ysmPackage, index, actionNames, controlNames, unsupportedBindings, errors);
                scanSubEntities(ysmPackage, subEntities, unsupportedBindings, errors);
            }
        } catch (Throwable e) {
            errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        YsmResourceIndex resources = manifest == null ? YsmResourceIndex.fromManifest("", "", List.of(), List.of(), kind) : manifest.resourceIndex();
        String name = manifest == null ? "" : manifest.name();
        animationNames.sort(String::compareToIgnoreCase);
        controllerNames.sort(String::compareToIgnoreCase);
        functionNames.sort(String::compareToIgnoreCase);
        eventNames.sort(String::compareToIgnoreCase);
        actionNames.sort(String::compareToIgnoreCase);
        controlNames.sort(String::compareToIgnoreCase);
        subEntities.sort(String::compareToIgnoreCase);
        return new ScanReport(path, kind, name, resources, mainValidation, armValidation, animationClipCount,
                List.copyOf(animationNames), describeNativeStateHits(animationNames), List.copyOf(controllerNames),
                List.copyOf(functionNames), List.copyOf(eventNames), List.copyOf(actionNames), List.copyOf(controlNames),
                List.copyOf(subEntities), List.copyOf(unsupportedBindings), bones, locators, attachments, firstPersonArmBones, List.copyOf(errors));
    }

    public static void main(String[] args) throws Exception {
        boolean dump = false;
        String rootArg = "refs";
        for (String arg : args) {
            if ("--dump".equalsIgnoreCase(arg)) {
                dump = true;
            } else {
                rootArg = arg;
            }
        }
        Path root = Path.of(rootArg);
        ScanSummary summary = scan(root);
        for (ScanReport report : summary.reports()) {
            String status = report.valid() ? "OK" : "FAIL";
            YsmModelValidator.ModelStats stats = report.mainValidation() == null ? YsmModelValidator.ModelStats.empty() : report.mainValidation().stats();
            System.out.printf("%s %s kind=%s bones=%d roots=%d cubes=%d quads=%d locators=%d animations=%d textures=%d%n",
                    status,
                    report.path(),
                    report.kind(),
                    stats.boneCount(),
                    stats.rootCount(),
                    stats.cubeCount(),
                    stats.quadCount(),
                    stats.locatorCount(),
                    report.animationClipCount(),
                    report.resources().texturePaths().size());
            if (dump)
                dumpReport(report, stats);
            for (String error : report.errors())
                System.out.println("  error: " + error);
        }
        System.out.printf("YSM scan complete: %d total, %d valid, %d invalid%n",
                summary.reports().size(), summary.validCount(), summary.invalidCount());
    }

    private static boolean couldBeYsmAvatar(Path path) {
        if (Files.isDirectory(path))
            return Files.exists(path.resolve("ysm.json")) || Files.exists(path.resolve("main.json"));
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.US);
        return name.endsWith(".zip") || name.endsWith(".ysm");
    }

    private static MolangEngine scannerMolangEngine() {
        if (scannerMolangEngine == null)
            scannerMolangEngine = MolangEngine.fromCustomBinding(new MolangBindings());
        return scannerMolangEngine;
    }

    private static List<String> describeLocators(YsmGeometry geometry) {
        List<String> result = new ArrayList<>();
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmLocator locator = YsmBoneMapper.locatorOf(bone);
            if (locator == null)
                continue;
            result.add(locator.name() + " bone=" + locator.boneName()
                    + " role=" + locator.role()
                    + " left=" + locator.leftSide()
                    + " transform=" + locator.defaultItemTransform());
        }
        return List.copyOf(result);
    }

    private static List<String> describeBones(YsmGeometry geometry) {
        List<String> result = new ArrayList<>();
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmBoneRole role = YsmBoneMapper.roleOf(bone);
            result.add(bone.name
                    + " parent=" + (bone.parentName == null || bone.parentName.isBlank() ? "<root>" : bone.parentName)
                    + " role=" + role
                    + " pivot=" + formatVec(bone.pivot)
                    + " rotation=" + formatVec(bone.rotation)
                    + " cubes=" + bone.cubes.size());
        }
        return List.copyOf(result);
    }

    private static String formatVec(float[] values) {
        if (values == null || values.length < 3)
            return "[]";
        return "[" + trimFloat(values[0]) + ", " + trimFloat(values[1]) + ", " + trimFloat(values[2]) + "]";
    }

    private static String trimFloat(float value) {
        if (value == (long) value)
            return Long.toString((long) value);
        return Float.toString(value);
    }

    private static List<String> describeAttachments(YsmGeometry geometry) {
        List<String> result = new ArrayList<>();
        List<YsmAttachmentType> seenTypes = new ArrayList<>();
        List<YsmBoneRole> seenRoles = new ArrayList<>();
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmLocator locator = YsmBoneMapper.locatorOf(bone);
            if (locator == null)
                continue;
            YsmAttachmentType type = YsmAttachmentType.fromRole(locator.role(), locator.leftSide());
            if (type == YsmAttachmentType.UNKNOWN)
                continue;
            result.add(type + " locator=" + locator.name() + " bone=" + locator.boneName() + " role=" + locator.role() + " left=" + locator.leftSide());
            if (!seenTypes.contains(type))
                seenTypes.add(type);
            if (!seenRoles.contains(locator.role()))
                seenRoles.add(locator.role());
        }
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmBoneRole role = YsmBoneMapper.roleOf(bone);
            boolean left = role == YsmBoneRole.LEFT_HAND || YsmBoneMapper.isLeftSideName(bone.name);
            YsmAttachmentType type = YsmAttachmentType.fromRole(role, left);
            if (type == YsmAttachmentType.UNKNOWN || seenTypes.contains(type) || seenRoles.contains(role))
                continue;
            result.add(type + " fallback=" + bone.name + " role=" + role + " left=" + left);
            seenTypes.add(type);
            seenRoles.add(role);
        }
        return List.copyOf(result);
    }

    private static void dumpReport(ScanReport report, YsmModelValidator.ModelStats stats) {
        if (!stats.roles().isEmpty()) {
            EnumMap<YsmBoneRole, Integer> roles = new EnumMap<>(YsmBoneRole.class);
            roles.putAll(stats.roles());
            System.out.println("  roles: " + roles);
        }
        if (!report.locators().isEmpty()) {
            System.out.println("  locators:");
            for (String locator : report.locators())
                System.out.println("    " + locator);
        }
        if (!report.bones().isEmpty()) {
            System.out.println("  bones:");
            for (String bone : report.bones())
                System.out.println("    " + bone);
        }
        if (!report.attachments().isEmpty()) {
            System.out.println("  attachments:");
            for (String attachment : report.attachments())
                System.out.println("    " + attachment);
        }
        if (!report.nativeStateHits().isEmpty()) {
            System.out.println("  native animation states:");
            for (String hit : report.nativeStateHits())
                System.out.println("    " + hit);
        }
        if (!report.firstPersonArmBones().isEmpty()) {
            System.out.println("  first person arm bones:");
            for (String armBone : report.firstPersonArmBones())
                System.out.println("    " + armBone);
        }
        if (!report.animationNames().isEmpty()) {
            System.out.println("  animation names:");
            for (String name : report.animationNames())
                System.out.println("    " + name);
        }
        if (!report.controllerNames().isEmpty()) {
            System.out.println("  controllers:");
            for (String name : report.controllerNames())
                System.out.println("    " + name);
        }
        if (!report.functionNames().isEmpty()) {
            System.out.println("  functions:");
            for (String name : report.functionNames())
                System.out.println("    " + name);
        }
        if (!report.eventNames().isEmpty()) {
            System.out.println("  function events:");
            for (String name : report.eventNames())
                System.out.println("    " + name);
        }
        if (!report.actionNames().isEmpty()) {
            System.out.println("  actions:");
            for (String name : report.actionNames())
                System.out.println("    " + name);
        }
        if (!report.controlNames().isEmpty()) {
            System.out.println("  controls:");
            for (String name : report.controlNames())
                System.out.println("    " + name);
        }
        if (!report.subEntities().isEmpty()) {
            System.out.println("  sub entities:");
            for (String name : report.subEntities())
                System.out.println("    " + name);
        }
        if (!report.unsupportedBindings().isEmpty()) {
            System.out.println("  unsupported bindings:");
            for (String name : report.unsupportedBindings())
                System.out.println("    " + name);
        }
    }

    private static String describeController(YsmAnimationController controller) {
        int animationRefs = 0;
        int transitions = 0;
        for (YsmControllerState state : controller.states().values()) {
            animationRefs += state.animations().size();
            transitions += state.transitions().size();
        }
        return controller.name()
                + " slot=" + controller.slot()
                + " initial=" + controller.initialState()
                + " states=" + controller.states().size()
                + " animations=" + animationRefs
                + " transitions=" + transitions;
    }

    private static void validateControllerReferences(YsmAnimationController controller, List<String> animationNames, String source, List<String> errors) {
        Set<String> normalizedAnimations = new LinkedHashSet<>();
        for (String name : animationNames)
            normalizedAnimations.add(simpleAnimationName(name));
        for (YsmControllerState state : controller.states().values()) {
            for (YsmControllerAnimationRef ref : state.animations()) {
                String animation = ref.animation();
                if (animation == null || animation.isBlank() || "empty".equals(animation))
                    continue;
                if (!normalizedAnimations.contains(simpleAnimationName(animation)))
                    errors.add("controller '" + controller.name() + "' in '" + source + "' references missing animation '" + animation + "'");
            }
            for (YsmControllerTransition transition : state.transitions()) {
                if (!controller.states().containsKey(transition.targetState()))
                    errors.add("controller '" + controller.name() + "' in '" + source + "' state '" + state.name() + "' references missing transition target '" + transition.targetState() + "'");
            }
        }
    }

    private static void scanActionSchemas(YsmPackage ysmPackage, YsmResourceIndex index, List<String> actions, List<String> controls,
                                          Set<String> unsupportedBindings, List<String> errors) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        paths.addAll(List.of("ysm.json", "ysm.actions.json", "action_wheel.json", "ysm.controls.json", "ysm_controls.json", "controls.json"));
        if (index != null)
            paths.addAll(index.metadataPaths());
        for (String path : paths) {
            if (path == null || path.isBlank() || !ysmPackage.exists(path))
                continue;
            try {
                String json = ysmPackage.readString(path);
                collectUnsupportedBindings(unsupportedBindings, json);
                JsonElement root = parseJson(json);
                if (root == null || root.isJsonNull())
                    continue;
                if (root.isJsonArray()) {
                    scanControls(path, root.getAsJsonArray(), controls);
                    continue;
                }
                if (!root.isJsonObject())
                    continue;
                JsonObject object = root.getAsJsonObject();
                scanControls(path, array(object, "controls"), controls);
                scanControls(path, array(object, "control"), controls);
                scanActions(path, array(object, "actions"), actions);
                scanActions(path, array(object, "action"), actions);
                scanYsmProperties(path, object, actions, controls);
                scanLegacyExtraInfo(path, object, actions);
            } catch (Throwable e) {
                errors.add("failed to scan action schema '" + path + "': " + e.getMessage());
            }
        }
    }

    private static void scanYsmProperties(String path, JsonObject root, List<String> actions, List<String> controls) {
        JsonObject properties = object(root, "properties");
        if (properties == null)
            return;
        if (properties.has("width_scale"))
            controls.add("ysm.width_scale type=slider path=" + path);
        if (properties.has("height_scale"))
            controls.add("ysm.height_scale type=slider path=" + path);
        scanExtraAnimations(path, object(properties, "extra_animation"), "extra_animation", actions);
        scanExtraAnimationClassify(path, array(properties, "extra_animation_classify"), actions);
        scanExtraAnimationClassify(path, object(properties, "extra_animation_classify"), actions);
        scanExtraAnimationButtons(path, array(properties, "extra_animation_buttons"), controls);
    }

    private static void scanLegacyExtraInfo(String path, JsonObject root, List<String> actions) {
        JsonArray geometries = array(root, "minecraft:geometry");
        if (geometries == null)
            return;
        for (JsonElement geometryElement : geometries) {
            if (geometryElement == null || !geometryElement.isJsonObject())
                continue;
            JsonObject description = object(geometryElement.getAsJsonObject(), "description");
            JsonObject extra = object(description, "ysm_extra_info");
            JsonArray names = array(extra, "extra_animation_names");
            if (names == null)
                continue;
            for (int i = 0; i < names.size(); i++) {
                String title = string(names.get(i), "");
                if (!title.isBlank())
                    actions.add("extra_animation.extra" + i + " title=" + title + " page=extra_animation path=" + path);
            }
        }
    }

    private static void scanExtraAnimations(String path, JsonObject object, String page, List<String> actions) {
        if (object == null)
            return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            String label = string(entry.getValue(), id);
            if (id == null || id.isBlank() || label == null || label.isBlank() || id.startsWith("#"))
                continue;
            String animation = label.startsWith("#") ? label : id;
            String title = label.startsWith("#") ? id : label;
            actions.add("extra_animation." + id + " title=" + title + " animation=" + animation + " page=" + page + " path=" + path);
        }
    }

    private static void scanExtraAnimationClassify(String path, JsonArray array, List<String> actions) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", string(object, "name", ""));
            if (id.isBlank())
                continue;
            JsonObject extras = object(object, "extra_animation");
            if (extras == null)
                extras = object(object, "extras");
            scanExtraAnimations(path, extras, id, actions);
        }
    }

    private static void scanExtraAnimationClassify(String path, JsonObject object, List<String> actions) {
        if (object == null)
            return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String id = entry.getKey();
            JsonElement value = entry.getValue();
            if (id == null || id.isBlank() || value == null || !value.isJsonObject())
                continue;
            JsonObject child = value.getAsJsonObject();
            JsonObject extras = object(child, "extra_animation");
            if (extras == null)
                extras = object(child, "extras");
            scanExtraAnimations(path, extras == null ? child : extras, id, actions);
        }
    }

    private static void scanExtraAnimationButtons(String path, JsonArray array, List<String> controls) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject button = element.getAsJsonObject();
            String page = string(button, "id", "");
            if (page.isBlank())
                continue;
            controls.add("ysm.page_link." + page + " type=button target=" + page + " path=" + path);
            JsonArray forms = array(button, "config_forms");
            if (forms == null)
                continue;
            int index = 0;
            for (JsonElement formElement : forms) {
                if (formElement != null && formElement.isJsonObject()) {
                    JsonObject form = formElement.getAsJsonObject();
                    String binding = string(form, "value", "");
                    String id = !binding.isBlank() ? "ysm." + binding.replace('.', '_') : "ysm." + page + "." + index;
                    controls.add(id + " type=" + string(form, "type", "toggle") + " binding=" + binding + " page=" + page + " path=" + path);
                }
                index++;
            }
        }
    }

    private static void scanActions(String path, JsonArray array, List<String> actions) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", "");
            if (id.isBlank())
                continue;
            String animation = string(object, "animation", string(object, "anim", id));
            actions.add(id + " animation=" + animation + " page=" + string(object, "page", "root") + " path=" + path);
        }
    }

    private static void scanControls(String path, JsonArray array, List<String> controls) {
        if (array == null)
            return;
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject())
                continue;
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "id", "");
            if (id.isBlank())
                continue;
            controls.add(id + " type=" + string(object, "type", "toggle")
                    + " binding=" + string(first(object, "binding", "variable", "value"), id)
                    + " page=" + string(object, "page", "root") + " path=" + path);
        }
    }

    private static void scanSubEntities(YsmPackage ysmPackage, List<String> subEntities, Set<String> unsupportedBindings, List<String> errors) {
        try {
            if (ysmPackage.exists("ysm.json")) {
                JsonElement rootElement = parseJson(ysmPackage.readString("ysm.json"));
                JsonObject root = rootElement != null && rootElement.isJsonObject() ? rootElement.getAsJsonObject() : new JsonObject();
                JsonObject files = object(root, "files");
                scanSubEntitySection(ysmPackage, subEntities, unsupportedBindings, "projectile", files.get("projectiles"));
                scanSubEntitySection(ysmPackage, subEntities, unsupportedBindings, "vehicle", files.get("vehicles"));
            }
            if (ysmPackage.exists("arrow.json"))
                subEntities.add("projectile:arrow model=arrow.json texture=" + (ysmPackage.exists("arrow.png") ? "arrow.png" : "<fallback>")
                        + " animation=" + (ysmPackage.exists("arrow.animation.json") ? "arrow.animation.json" : "<none>"));
        } catch (Throwable e) {
            errors.add("failed to scan sub entities: " + e.getMessage());
        }
    }

    private static void scanSubEntitySection(YsmPackage ysmPackage, List<String> subEntities, Set<String> unsupportedBindings, String kind, JsonElement section) throws IOException {
        if (section == null || section.isJsonNull())
            return;
        if (section.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject().entrySet())
                scanSubEntity(ysmPackage, subEntities, unsupportedBindings, kind, entry.getKey(), entry.getValue());
        } else if (section.isJsonArray()) {
            int index = 0;
            for (JsonElement element : section.getAsJsonArray())
                scanSubEntity(ysmPackage, subEntities, unsupportedBindings, kind, kind + index++, element);
        }
    }

    private static void scanSubEntity(YsmPackage ysmPackage, List<String> subEntities, Set<String> unsupportedBindings, String kind, String fallbackId, JsonElement element) throws IOException {
        if (element == null || element.isJsonNull())
            return;
        JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        String id = firstString(object, fallbackId, "identifier", "id", "name");
        String model = element.isJsonPrimitive() ? element.getAsString() : firstString(object, "", "model", "model_path");
        String texture = texturePath(object);
        String animation = firstString(object, "", "animation", "animations", "animation_path");
        String controller = firstString(object, "", "controller", "animation_controller", "controller_path");
        subEntities.add(kind + ":" + (id == null || id.isBlank() ? fallbackId : id)
                + " model=" + valueOrMissing(ysmPackage, model)
                + " texture=" + valueOrMissing(ysmPackage, texture)
                + " animation=" + valueOrMissing(ysmPackage, animation)
                + " controller=" + valueOrMissing(ysmPackage, controller));
        if (!animation.isBlank() && ysmPackage.exists(animation))
            collectUnsupportedBindings(unsupportedBindings, ysmPackage.readString(animation));
        if (!controller.isBlank() && ysmPackage.exists(controller))
            collectUnsupportedBindings(unsupportedBindings, ysmPackage.readString(controller));
    }

    private static String valueOrMissing(YsmPackage ysmPackage, String path) {
        if (path == null || path.isBlank())
            return "<none>";
        return ysmPackage.exists(path) ? YsmPackage.normalize(path) : "<missing:" + YsmPackage.normalize(path) + ">";
    }

    private static final Pattern BINDING_PATTERN = Pattern.compile("\\b(ctrl|ysm|q|query)\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Set<String> SUPPORTED_CTRL = Set.of(
            "death", "sleep", "swim", "swim_stand", "jump", "fall", "fly", "elytra_fly",
            "sneak", "sneaking", "run", "walk", "idle", "attacked", "ride", "riding",
            "hold", "use", "swing", "armor", "attack", "playing_extra_animation",
            "state_continue", "state_break", "state_stop", "state_pause", "state_bypass",
            "loop", "play_once", "hold_on_last_frame", "set_beginning_transition_length",
            "set_animation", "reset", "indicate_reload", "im_delta", "im_pitch", "im_time"
    );
    private static final Set<String> SUPPORTED_YSM = Set.of(
            "rendering_in_inventory", "rendering_in_paperdoll", "is_first_person", "first_person",
            "weather", "dimension_name", "fps", "time_delta", "head_yaw", "head_pitch",
            "ground_speed2", "is_open_air", "block_light", "sky_light", "is_passenger",
            "is_sleep", "is_sneak", "eye_in_water", "frozen_ticks", "air_supply",
            "input_vertical", "input_horizontal", "xxa", "yya", "zza", "keyboard", "mouse",
            "sync", "event", "run_event", "trigger_event", "play_sound", "stop_sound", "stop_all_sounds", "first_order", "second_order",
            "food_level", "is_local_player", "local_player", "person_view", "main_hand",
            "off_hand", "equipped_item", "has_mainhand", "has_offhand", "has_helmet",
            "has_chest_plate", "has_leggings", "has_boots", "armor_value", "hurt_time",
            "swinging", "swing_time", "attack_time", "entity_type", "is_player",
            "elytra_rot_z", "boat_left_paddle", "boat_right_paddle", "boat_left_rowing_time",
            "boat_right_rowing_time", "boat_paddle_scale", "boat_body_offset_y",
            "boat_body_offset_z", "shoot_item_id",
            "dump_mods", "dump_effects", "dump_biome", "has_any_curios",
            "has_any_curios_with_any_tag", "has_any_curios_with_all_tags", "particle",
            "abs_particle", "perlin_noise", "bone_param", "bone_rot", "bone_pos",
            "bone_scale", "bone_pivot_abs"
    );
    private static final Set<String> SUPPORTED_QUERY = Set.of(
            "anim_time", "life_time", "delta_time", "time_of_day", "time_stamp", "moon_phase",
            "frame_count", "actor_count", "health", "max_health", "hurt_time", "position",
            "position_x", "position_y", "position_z", "position_delta", "position_delta_x",
            "position_delta_y", "position_delta_z", "distance_from_camera", "rotation_to_camera",
            "ground_speed", "vertical_speed", "yaw_speed", "walk_distance",
            "modified_distance_moved", "body_x_rotation", "body_y_rotation", "head_x_rotation",
            "head_y_rotation", "eye_target_x_rotation", "eye_target_y_rotation", "cardinal_facing_2d",
            "is_on_ground", "is_jumping", "is_sneaking", "is_sprinting", "is_swimming",
            "is_in_water", "is_in_water_or_rain", "is_on_fire", "is_riding", "has_rider",
            "is_sleeping", "is_spectator", "is_first_person", "rendering_in_paperdoll",
            "rendering_in_inventory", "is_using_item", "is_swinging", "is_eating",
            "is_playing_dead", "has_cape", "all_animations_finished", "any_animation_finished",
            "swing_time", "attack_time", "control", "avatar_control", "ysm_control",
            "ysm_control_bool", "ysm_control_number", "ysm_control_enum", "ysm_action_active",
            "ysm_action_time", "item_in_use_duration", "item_max_use_duration",
            "item_remaining_use_duration", "equipment_count", "max_durability",
            "remaining_durability", "biome_has_all_tags", "biome_has_any_tag",
            "relative_block_has_all_tags", "relative_block_has_any_tag", "is_item_name_any",
            "equipped_item_all_tags", "equipped_item_any_tag", "cape_flap_amount",
            "player_level", "geometry_is_model", "geometry_is_block", "geometry_is_entity",
            "geometry_is_flat", "debug_output"
    );

    private static void collectUnsupportedBindings(Set<String> unsupported, String source) {
        if (source == null || source.isBlank())
            return;
        Matcher matcher = BINDING_PATTERN.matcher(source);
        while (matcher.find()) {
            String namespace = matcher.group(1).toLowerCase(java.util.Locale.US);
            String name = matcher.group(2).toLowerCase(java.util.Locale.US);
            Set<String> supported = switch (namespace) {
                case "ctrl" -> SUPPORTED_CTRL;
                case "ysm" -> SUPPORTED_YSM;
                default -> SUPPORTED_QUERY;
            };
            if (!supported.contains(name))
                unsupported.add(namespace + "." + name);
        }
    }

    private static JsonElement parseJson(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonElement first(JsonObject object, String... keys) {
        if (object == null)
            return null;
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element != null && !element.isJsonNull())
                return element;
        }
        return null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object == null ? null : object.get(key);
        return string(element, fallback);
    }

    private static String string(JsonElement element, String fallback) {
        try {
            return element != null && !element.isJsonNull() ? element.getAsString() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String texturePath(JsonObject object) {
        if (object == null)
            return "";
        JsonElement texture = object.get("texture");
        if (texture == null)
            texture = object.get("textures");
        if (texture == null)
            return "";
        if (texture.isJsonPrimitive())
            return texture.getAsString();
        if (texture.isJsonObject())
            return firstString(texture.getAsJsonObject(), "", "uv", "path", "default", "normal");
        return "";
    }

    private static String firstString(JsonObject object, String fallback, String... keys) {
        if (object == null)
            return fallback == null ? "" : fallback;
        for (String key : keys) {
            String value = stringValue(object.get(key));
            if (!value.isBlank())
                return value;
        }
        return fallback == null ? "" : fallback;
    }

    private static String stringValue(JsonElement element) {
        if (element == null || element.isJsonNull())
            return "";
        if (element.isJsonPrimitive())
            return element.getAsString();
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String value = stringValue(item);
                if (!value.isBlank())
                    return value;
            }
        }
        return "";
    }

    private static List<String> describeNativeStateHits(List<String> animationNames) {
        List<String> result = new ArrayList<>();
        for (List<String> aliases : List.of(
                List.of("idle"),
                List.of("walk"),
                List.of("run"),
                List.of("sneak"),
                List.of("sneaking"),
                List.of("jump"),
                List.of("fall", "fly", "jump"),
                List.of("swim"),
                List.of("swim_stand"),
                List.of("fly"),
                List.of("elytra_fly", "fly"),
                List.of("sit", "ride"),
                List.of("ride", "sit"),
                List.of("sleep"),
                List.of("hurt", "attacked"))) {
            String hit = findFirstAnimationAlias(animationNames, aliases);
            result.add(aliases.get(0) + "=" + (hit == null ? "missing" : hit));
        }
        return result;
    }

    private static String findFirstAnimationAlias(List<String> animationNames, List<String> aliases) {
        for (String alias : aliases) {
            String hit = findAnimationAlias(animationNames, alias);
            if (hit != null)
                return hit;
        }
        return null;
    }

    private static String findAnimationAlias(List<String> animationNames, String state) {
        for (String name : animationNames) {
            if (state.equals(name.toLowerCase(java.util.Locale.US)))
                return name;
        }
        for (String name : animationNames) {
            if (state.equals(simpleAnimationName(name)))
                return name;
        }
        return null;
    }

    private static String simpleAnimationName(String name) {
        if (name == null)
            return "";
        String normalized = name.toLowerCase(java.util.Locale.US);
        if (normalized.startsWith("animation."))
            normalized = normalized.substring("animation.".length());
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length())
            normalized = normalized.substring(colon + 1);
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < normalized.length())
            normalized = normalized.substring(slash + 1);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length())
            normalized = normalized.substring(dot + 1);
        return normalized;
    }

    private static List<String> describeFirstPersonArmBones(YsmGeometry geometry) {
        List<String> result = new ArrayList<>();
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmBoneRole role = YsmBoneMapper.roleOf(bone);
            if (role == YsmBoneRole.LEFT_HAND || role == YsmBoneRole.RIGHT_HAND || role == YsmBoneRole.FIRST_PERSON_ARM)
                result.add(bone.name + " role=" + role + " cubes=" + bone.cubes.size());
        }
        return List.copyOf(result);
    }

    private static boolean isInsideAcceptedPackage(Path candidate, List<Path> accepted) {
        for (Path path : accepted) {
            if (!candidate.equals(path) && candidate.startsWith(path))
                return true;
        }
        return false;
    }
}
