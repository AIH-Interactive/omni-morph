package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.backend2.library.LibraryModels;

import java.util.Map;
import java.util.UUID;

/** Metadata patches retain the server revision and never include source provenance. */
public final class LibraryModelEdits {
    private LibraryModelEdits() {
    }

    public static boolean available(LibraryModels.Model model, UUID player) {
        return available(model, player, false);
    }

    public static boolean available(LibraryModels.Model model, UUID player, boolean administrator) {
        return model != null && model.allows("patch")
                && (administrator || model.uploader().uuid().equals(player))
                && (model.moderationStatus().equals("ACTIVE")
                    || administrator && model.moderationStatus().equals("BLOCKED"));
    }

    public static Map<String, Object> snapshot(LibraryModels.Model model, UUID player,
                                              LibraryModels.Capabilities capabilities, UUID operation,
                                              String name, String description, String category,
                                              String tags, boolean isPublic, boolean requiresAuthorization) {
        return snapshot(model, player, false, capabilities, operation, name, description, category,
                tags, isPublic, requiresAuthorization);
    }

    public static Map<String, Object> snapshot(LibraryModels.Model model, UUID player, boolean administrator,
                                              LibraryModels.Capabilities capabilities, UUID operation,
                                              String name, String description, String category,
                                              String tags, boolean isPublic, boolean requiresAuthorization) {
        if (!available(model, player, administrator)) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        if (isPublic && model.moderationStatus().equals("BLOCKED")) {
            throw new IllegalArgumentException("FORBIDDEN");
        }
        if (!capabilities.enabled() || !capabilities.categories().contains(category)) {
            throw new IllegalArgumentException("INVALID_CATEGORY");
        }
        var normalizedTags = LibraryUploadDraft.normalizeTags(tags);
        LibraryUploadDraft.validateEditable(name, description, normalizedTags);
        return Map.of("operation_id", operation.toString(), "expected_revision", model.policyRevision(),
                "display_name", name, "description", description, "category_id", category,
                "tags", normalizedTags, "visibility", isPublic ? "PUBLIC" : "PRIVATE",
                "requires_authorization", requiresAuthorization);
    }
}
