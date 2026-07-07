package org.figuramc.figura.model.ysm;

import java.util.Locale;

public final class YsmBoneClassifier {
    private YsmBoneClassifier() {
    }

    public static boolean isNonBodyBranch(YsmGeometry.Bone bone) {
        String name = normalize(bone.name);
        if (name.isBlank())
            return false;

        if (name.equals("gui") || name.equals("background") || name.startsWith("background"))
            return true;
        if (name.contains("locator"))
            return true;
        if (name.equals("camera") || name.equals("hitbox") || name.equals("item"))
            return true;
        if (name.equals("fox") || name.equals("car") || name.equals("vehicle") || name.equals("mount") || name.equals("boat") || name.equals("doll"))
            return true;
        if (name.contains("backpack") || name.contains("sheath") || name.contains("blade") || name.contains("elytra"))
            return true;
        if (name.contains("preview") || name.contains("display") || name.contains("thirdperson") || name.contains("firstperson"))
            return true;
        if (bone.parentName == null || bone.parentName.isBlank())
            return name.contains("vehicle") || name.contains("mount") || name.contains("car") || name.contains("boat");
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replace("_", "").replace("-", "");
    }
}
