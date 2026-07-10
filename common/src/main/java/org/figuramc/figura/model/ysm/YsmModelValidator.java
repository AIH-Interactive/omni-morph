package org.figuramc.figura.model.ysm;

import org.figuramc.figura.FiguraMod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class YsmModelValidator {
    private YsmModelValidator() {
    }

    public record ValidationResult(boolean valid, List<String> warnings, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
    }

    public static ValidationResult validate(String sourceName, YsmGeometry geometry) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int boneCount = geometry.bones.size();
        int rootCount = geometry.roots.size();
        int totalCubes = 0;
        int totalQuads = 0;

        // Check for empty geometry
        if (boneCount == 0) {
            errors.add(sourceName + ": no bones found");
            return new ValidationResult(false, warnings, errors);
        }

        // Check root count
        if (rootCount == 0) {
            errors.add(sourceName + ": no root bones (possible cycle or missing parent references)");
        } else if (rootCount > 32) {
            warnings.add(sourceName + ": " + rootCount + " root bones (unusually high)");
        }

        // Check for circular parent references
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
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

        // Check cubes and quads
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            totalCubes += bone.cubes.size();
            for (YsmGeometry.Cube cube : bone.cubes)
                totalQuads += cube.quads.size();
        }

        if (boneCount > 256) {
            warnings.add(sourceName + ": " + boneCount + " bones (high count may impact performance)");
        }
        if (totalQuads > 16384) {
            warnings.add(sourceName + ": " + totalQuads + " quads (high poly count may impact performance)");
        }

        // Check for NaN/Infinity in pivots and rotations
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            for (float v : bone.pivot) {
                if (Float.isNaN(v) || Float.isInfinite(v)) {
                    errors.add(sourceName + ": bone '" + bone.name + "' has NaN/Infinity pivot value");
                    break;
                }
            }
            for (float v : bone.rotation) {
                if (Float.isNaN(v) || Float.isInfinite(v)) {
                    errors.add(sourceName + ": bone '" + bone.name + "' has NaN/Infinity rotation value");
                    break;
                }
            }
        }

        boolean valid = errors.isEmpty();

        if (!warnings.isEmpty()) {
            for (String warning : warnings)
                FiguraMod.LOGGER.warn("[YSM Validation] " + warning);
        }
        if (!errors.isEmpty()) {
            for (String error : errors)
                FiguraMod.LOGGER.error("[YSM Validation] " + error);
        } else {
            FiguraMod.LOGGER.info("[YSM Validation] {}: {} bones, {} roots, {} cubes, {} quads — OK",
                    sourceName, boneCount, rootCount, totalCubes, totalQuads);
        }

        return new ValidationResult(valid, warnings, errors);
    }

    public static void validateAndLog(String sourceName, YsmGeometry geometry) {
        validate(sourceName, geometry);
    }
}
