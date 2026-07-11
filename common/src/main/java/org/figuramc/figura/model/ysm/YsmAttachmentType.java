package org.figuramc.figura.model.ysm;

public enum YsmAttachmentType {
    LEFT_HAND,
    RIGHT_HAND,
    BACKPACK,
    BLADE,
    SHEATH,
    ELYTRA,
    HEAD,
    HELMET,
    UNKNOWN;

    public static YsmAttachmentType fromRole(YsmBoneRole role, boolean leftSide) {
        if (role == null)
            return UNKNOWN;
        return switch (role) {
            case LEFT_HAND -> LEFT_HAND;
            case RIGHT_HAND -> RIGHT_HAND;
            case BACKPACK -> BACKPACK;
            case BLADE -> BLADE;
            case SHEATH -> SHEATH;
            case ELYTRA -> ELYTRA;
            case HEAD -> HEAD;
            case HELMET -> HELMET;
            default -> UNKNOWN;
        };
    }
}
