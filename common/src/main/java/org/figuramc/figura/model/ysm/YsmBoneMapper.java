package org.figuramc.figura.model.ysm;

import java.util.Locale;

public final class YsmBoneMapper {
    private YsmBoneMapper() {
    }

    public static YsmBoneRole roleOf(YsmGeometry.Bone bone) {
        if (bone == null)
            return YsmBoneRole.UNKNOWN;

        String name = normalize(bone.name);
        if (name.isBlank())
            return YsmBoneRole.UNKNOWN;

        if (name.equals("gui") || name.contains("gui"))
            return YsmBoneRole.GUI;
        if (name.equals("background") || name.startsWith("background") || name.contains("preview") || name.contains("display"))
            return YsmBoneRole.BACKGROUND;
        if (name.contains("firstperson") || name.contains("fparm"))
            return YsmBoneRole.FIRST_PERSON_ARM;
        if (name.contains("backpack"))
            return YsmBoneRole.BACKPACK;
        if (name.contains("elytra") || name.contains("wing"))
            return YsmBoneRole.ELYTRA;
        if (name.contains("sheath") || name.contains("scabbard"))
            return YsmBoneRole.SHEATH;
        if (name.contains("blade") || name.contains("sword") || name.contains("katana"))
            return YsmBoneRole.BLADE;
        if (name.contains("helmet") || name.contains("hat"))
            return YsmBoneRole.HELMET;
        if (name.equals("head") || name.contains("head"))
            return YsmBoneRole.HEAD;
        if (isLeftHandName(name))
            return YsmBoneRole.LEFT_HAND;
        if (isRightHandName(name))
            return YsmBoneRole.RIGHT_HAND;
        if (name.equals("camera") || name.equals("hitbox") || name.equals("item"))
            return YsmBoneRole.BACKGROUND;
        if (name.equals("fox") || name.equals("car") || name.equals("vehicle") || name.equals("mount") || name.equals("boat") || name.equals("doll"))
            return YsmBoneRole.BACKGROUND;
        if (name.contains("thirdperson"))
            return YsmBoneRole.BACKGROUND;

        return YsmBoneRole.BODY;
    }

    public static boolean isLocator(YsmGeometry.Bone bone) {
        return bone != null && normalize(bone.name).contains("locator");
    }

    public static YsmLocator locatorOf(YsmGeometry.Bone bone) {
        if (!isLocator(bone))
            return null;

        YsmBoneRole role = roleOf(bone);
        boolean left = role == YsmBoneRole.LEFT_HAND || normalize(bone.name).startsWith("left");
        return new YsmLocator(bone.name, bone.name, role, left, defaultItemTransform(role));
    }

    public static boolean isBodyVisibleByDefault(YsmGeometry.Bone bone) {
        if (bone == null)
            return true;
        if (isLocator(bone))
            return false;
        return YsmPartMask.PLAYER_BODY.allows(roleOf(bone));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replace("_", "").replace("-", "");
    }

    private static boolean isLeftHandName(String name) {
        return name.equals("lefthand") || name.equals("leftitem") || name.equals("leftpalm") || name.equals("leftwrist")
                || name.equals("leftforearm") || name.equals("leftlowerarm") || name.equals("leftarm")
                || name.equals("lhand") || name.equals("larm") || name.startsWith("lefthandlocator");
    }

    private static boolean isRightHandName(String name) {
        return name.equals("righthand") || name.equals("rightitem") || name.equals("rightpalm") || name.equals("rightwrist")
                || name.equals("rightforearm") || name.equals("rightlowerarm") || name.equals("rightarm")
                || name.equals("rhand") || name.equals("rarm") || name.startsWith("righthandlocator");
    }

    private static String defaultItemTransform(YsmBoneRole role) {
        return switch (role) {
            case LEFT_HAND -> "third_person_left_hand";
            case RIGHT_HAND -> "third_person_right_hand";
            case BACKPACK -> "backpack";
            case BLADE -> "blade";
            case SHEATH -> "sheath";
            case ELYTRA -> "elytra";
            default -> "";
        };
    }
}
