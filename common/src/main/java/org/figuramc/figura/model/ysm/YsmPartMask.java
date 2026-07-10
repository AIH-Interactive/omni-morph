package org.figuramc.figura.model.ysm;

import java.util.EnumSet;
import java.util.Set;

public class YsmPartMask {
    public static final YsmPartMask PLAYER_BODY = new YsmPartMask(EnumSet.of(
            YsmBoneRole.BODY,
            YsmBoneRole.LEFT_HAND,
            YsmBoneRole.RIGHT_HAND,
            YsmBoneRole.HEAD,
            YsmBoneRole.HELMET,
            YsmBoneRole.UNKNOWN
    ));

    public static final YsmPartMask HELD_ITEM = new YsmPartMask(EnumSet.of(
            YsmBoneRole.LEFT_HAND,
            YsmBoneRole.RIGHT_HAND,
            YsmBoneRole.UNKNOWN
    ));

    public static final YsmPartMask FIRST_PERSON_ARM = new YsmPartMask(EnumSet.of(
            YsmBoneRole.LEFT_HAND,
            YsmBoneRole.RIGHT_HAND,
            YsmBoneRole.FIRST_PERSON_ARM
    ));

    public static final YsmPartMask ATTACHMENTS = new YsmPartMask(EnumSet.of(
            YsmBoneRole.BACKPACK,
            YsmBoneRole.BLADE,
            YsmBoneRole.SHEATH,
            YsmBoneRole.ELYTRA,
            YsmBoneRole.HEAD,
            YsmBoneRole.HELMET,
            YsmBoneRole.LEFT_HAND,
            YsmBoneRole.RIGHT_HAND
    ));

    public static final YsmPartMask DEBUG_ALL = new YsmPartMask(EnumSet.allOf(YsmBoneRole.class));

    private final Set<YsmBoneRole> roles;

    public YsmPartMask(Set<YsmBoneRole> roles) {
        this.roles = roles == null || roles.isEmpty() ? EnumSet.noneOf(YsmBoneRole.class) : EnumSet.copyOf(roles);
    }

    public static YsmPartMask forPass(YsmRenderPass pass) {
        return switch (pass) {
            case HELD_ITEM -> HELD_ITEM;
            case FIRST_PERSON_ARM -> FIRST_PERSON_ARM;
            case ATTACHMENTS -> ATTACHMENTS;
            case DEBUG_ALL, WARDROBE_PREVIEW -> DEBUG_ALL;
            case PLAYER_BODY -> PLAYER_BODY;
        };
    }

    public boolean allows(YsmBoneRole role) {
        return role != null && roles.contains(role);
    }
}
