package org.figuramc.figura.model.ysm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YsmModelValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Figura/YSM");

    private YsmModelValidator() {
    }

    public record ModelStats(int boneCount, int rootCount, int cubeCount, int quadCount, int locatorCount,
                             Map<YsmBoneRole, Integer> roles) {
        public static ModelStats empty() {
            return new ModelStats(0, 0, 0, 0, 0, Map.of());
        }
    }

    public record ValidationResult(boolean valid, List<String> warnings, List<String> errors, ModelStats stats) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of(), ModelStats.empty());
        }
    }

    public static ValidationResult validate(String sourceName, YsmGeometry geometry) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int boneCount = geometry.bones.size();
        int rootCount = geometry.roots.size();
        int totalCubes = 0;
        int totalQuads = 0;
        int locatorCount = 0;
        EnumMap<YsmBoneRole, Integer> roles = new EnumMap<>(YsmBoneRole.class);

        if (boneCount == 0) {
            errors.add(sourceName + ": no bones found");
            return new ValidationResult(false, warnings, errors, ModelStats.empty());
        }

        if (rootCount == 0) {
            errors.add(sourceName + ": no root bones (possible cycle or missing parent references)");
        } else if (rootCount > 32) {
            warnings.add(sourceName + ": " + rootCount + " root bones (unusually high)");
        }

        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            if (bone.parentName != null && !bone.parentName.isBlank() && !geometry.bones.containsKey(bone.parentName))
                warnings.add(sourceName + ": bone '" + bone.name + "' references missing parent '" + bone.parentName + "'");

            Set<String> visited = new HashSet<>();
            String current = bone.parentName;
            while (current != null && !current.isBlank()) {
                if (!visited.add(current)) {
                    errors.add(sourceName + ": circular parent reference detected at bone '" + bone.name + "'");
                    break;
                }
                YsmGeometry.Bone parent = geometry.bones.get(current);
                current = parent != null ? parent.parentName : null;
            }
        }

        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmBoneRole role = YsmBoneMapper.roleOf(bone);
            roles.merge(role, 1, Integer::sum);
            if (YsmBoneMapper.isLocator(bone))
                locatorCount++;

            totalCubes += bone.cubes.size();
            for (YsmGeometry.Cube cube : bone.cubes) {
                totalQuads += cube.quads.size();
                for (YsmGeometry.Quad quad : cube.quads)
                    validateQuad(sourceName, bone.name, quad, errors);
            }
        }

        if (boneCount > 256)
            warnings.add(sourceName + ": " + boneCount + " bones (high count may impact performance)");
        if (totalQuads > 16384)
            warnings.add(sourceName + ": " + totalQuads + " quads (high poly count may impact performance)");

        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            validateArray(sourceName, bone.name, "pivot", bone.pivot, 3, errors);
            validateArray(sourceName, bone.name, "rotation", bone.rotation, 3, errors);
        }

        ModelStats stats = new ModelStats(boneCount, rootCount, totalCubes, totalQuads, locatorCount, Map.copyOf(roles));
        boolean valid = errors.isEmpty();

        for (String warning : warnings)
            LOGGER.warn("[YSM Validation] " + warning);
        for (String error : errors)
            LOGGER.error("[YSM Validation] " + error);
        if (valid) {
            LOGGER.info("[YSM Validation] {}: {} bones, {} roots, {} cubes, {} quads, {} locators - OK",
                    sourceName, boneCount, rootCount, totalCubes, totalQuads, locatorCount);
        }

        return new ValidationResult(valid, warnings, errors, stats);
    }

    public static void validateAndLog(String sourceName, YsmGeometry geometry) {
        validate(sourceName, geometry);
    }

    private static void validateQuad(String sourceName, String boneName, YsmGeometry.Quad quad, List<String> errors) {
        validateArray(sourceName, boneName, "position", quad.positions(), 12, errors);
        validateArray(sourceName, boneName, "uv", quad.uvs(), 8, errors);
        validateArray(sourceName, boneName, "normal", quad.normal(), 3, errors);
    }

    private static void validateArray(String sourceName, String boneName, String label, float[] values, int expectedLength, List<String> errors) {
        if (values == null || values.length != expectedLength) {
            errors.add(sourceName + ": bone '" + boneName + "' has invalid " + label + " data");
            return;
        }
        for (float value : values) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                errors.add(sourceName + ": bone '" + boneName + "' has NaN/Infinity " + label + " value");
                return;
            }
        }
    }
}
