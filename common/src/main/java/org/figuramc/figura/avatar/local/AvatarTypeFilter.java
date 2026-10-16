package org.figuramc.figura.avatar.local;

/** Shared wardrobe type filter; operates only on already discovered list metadata. */
public enum AvatarTypeFilter {
    ALL("all"),
    FIGURA("figura"),
    YSM_PLAIN("ysm_plain"),
    YSM_ENCRYPTED("ysm_encrypted");

    private final String id;

    AvatarTypeFilter(String id) {
        this.id = id;
    }

    public String translationKey() {
        return "gui.wardrobe.filter." + id;
    }

    public AvatarTypeFilter next() {
        AvatarTypeFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean matches(LocalAvatarFetcher.AvatarPath avatar, String query) {
        if (avatar instanceof LocalAvatarFetcher.FolderPath folder) {
            return folder.getChildren().stream().anyMatch(child -> matches(child, query));
        }
        boolean typeMatches = switch (this) {
            case ALL -> true;
            case FIGURA -> !avatar.isYsm();
            case YSM_PLAIN -> avatar.isYsm() && !avatar.isEncryptedYsm();
            case YSM_ENCRYPTED -> avatar.isEncryptedYsm();
        };
        return typeMatches && avatar.search(query);
    }
}
