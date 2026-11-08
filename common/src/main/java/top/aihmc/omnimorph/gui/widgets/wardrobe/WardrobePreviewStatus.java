package top.aihmc.omnimorph.gui.widgets.wardrobe;

import top.aihmc.omnimorph.backend2.library.LibraryModels;

/** Moderation, authorization and visibility state of the model shown in a wardrobe preview. */
public record WardrobePreviewStatus(boolean blocked, boolean requiresAuthorization, boolean privateVisibility) {
    /** Returns null when the previewed resource has no library metadata, such as local models and drafts. */
    public static WardrobePreviewStatus of(LibraryModels.Model model) {
        if (model == null) {
            return null;
        }
        return new WardrobePreviewStatus("BLOCKED".equals(model.moderationStatus()), model.requiresAuthorization(),
                "PRIVATE".equals(model.visibility()));
    }
}
