package org.figuramc.figura.model.ysm;

import org.figuramc.figura.avatar.ysm.YsmAvatarDetector;
import org.figuramc.figura.avatar.ysm.YsmAvatarKind;
import org.figuramc.figura.avatar.ysm.YsmManifest;
import org.figuramc.figura.avatar.ysm.YsmManifestReader;
import org.figuramc.figura.avatar.ysm.YsmPackage;
import org.figuramc.figura.avatar.ysm.YsmResourceIndex;
import org.figuramc.figura.model.ysm.animation.YsmAnimationParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class YsmModelScanner {
    private YsmModelScanner() {
    }

    public record ScanReport(Path path, YsmAvatarKind kind, String name, YsmResourceIndex resources,
                             YsmModelValidator.ValidationResult mainValidation,
                             YsmModelValidator.ValidationResult armValidation,
                             int animationClipCount, List<String> animationNames, List<String> nativeStateHits,
                             List<String> bones, List<String> locators, List<String> attachments, List<String> firstPersonArmBones, List<String> errors) {
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

        try (Stream<Path> stream = Files.walk(root)) {
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
                    } catch (Exception e) {
                        errors.add("failed to parse arm model '" + index.armModelPath() + "': " + e.getMessage());
                    }
                }

                for (String animationPath : index.animationPaths()) {
                    try {
                        Map<String, ?> clips = YsmAnimationParser.parse(ysmPackage.readString(animationPath), false);
                        animationClipCount += clips.size();
                        for (String clipName : clips.keySet())
                            animationNames.add(clipName);
                    } catch (Exception e) {
                        errors.add("failed to parse animation '" + animationPath + "': " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        YsmResourceIndex resources = manifest == null ? YsmResourceIndex.fromManifest("", "", List.of(), List.of(), kind) : manifest.resourceIndex();
        String name = manifest == null ? "" : manifest.name();
        animationNames.sort(String::compareToIgnoreCase);
        return new ScanReport(path, kind, name, resources, mainValidation, armValidation, animationClipCount,
                List.copyOf(animationNames), describeNativeStateHits(animationNames), bones, locators, attachments, firstPersonArmBones, List.copyOf(errors));
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
            boolean left = role == YsmBoneRole.LEFT_HAND || YsmBoneMapper.normalize(bone.name).startsWith("left");
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
