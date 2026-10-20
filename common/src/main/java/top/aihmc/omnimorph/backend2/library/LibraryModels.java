package top.aihmc.omnimorph.backend2.library;

import java.util.List;
import java.util.UUID;

/** Immutable JSON DTOs for the existing library v1 extension. */
public final class LibraryModels {
    private LibraryModels() {
    }

    public record Capabilities(boolean enabled, int version, long maxUploadBytes, int maxPageSize,
                               long maxImageBytes, int maxImageWidth, int maxImageHeight,
                               List<String> formats, List<String> modelTypes, List<String> categories,
                               boolean adminLoginAvailable) {
        public Capabilities {
            formats = immutable(formats);
            modelTypes = immutable(modelTypes);
            categories = immutable(categories);
            if (enabled && (version != 1 || maxUploadBytes <= 0 || maxPageSize < 1 || maxPageSize > 64
                    || maxImageBytes <= 0 || maxImageBytes > 2 * 1024 * 1024
                    || maxImageWidth < 1 || maxImageWidth > 2048 || maxImageHeight < 1 || maxImageHeight > 2048)) {
                throw new IllegalArgumentException("INVALID_CAPABILITIES");
            }
        }

        public static Capabilities unsupported() {
            return new Capabilities(false, 1, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), false);
        }
    }

    public record Uploader(UUID uuid, String name) {
        public Uploader {
            if (uuid == null || name == null) {
                throw new IllegalArgumentException("INVALID_UPLOADER");
            }
        }
    }

    public record SessionInfo(UUID uuid, String name, boolean admin, Long equipmentRevision,
                              UUID equipmentModelId, Long equipmentModelRevision, boolean showPrivateEquipment,
                              Boolean showEquipment) {
        public SessionInfo(UUID uuid, String name, boolean admin, Long equipmentRevision,
                           UUID equipmentModelId, Long equipmentModelRevision, boolean showPrivateEquipment) {
            this(uuid, name, admin, equipmentRevision, equipmentModelId, equipmentModelRevision, showPrivateEquipment, null);
        }

        public boolean showing() {
            return showEquipment != null ? showEquipment : showPrivateEquipment;
        }

        public SessionInfo(UUID uuid, String name, boolean admin, Long equipmentRevision) {
            this(uuid, name, admin, equipmentRevision, null, null, false);
        }

        public SessionInfo {
            if (uuid == null || name == null || equipmentRevision == null || equipmentRevision < 0) {
                throw new IllegalArgumentException("INVALID_LIBRARY_SESSION");
            }
        }
    }

    public record WardrobeState(boolean available, String assetId, long assetRevision, long equipmentRevision,
                                boolean showEquipment, boolean blocked) {
        public WardrobeState {
            if (available && (assetId == null || assetRevision < 1 || equipmentRevision < 1)) {
                throw new IllegalArgumentException("INVALID_WARDROBE_STATE");
            }
        }

        public boolean canChangeVisibility() {
            return available && !blocked;
        }

        public boolean showing() {
            return available && showEquipment && !blocked;
        }
    }

    /** Detail responses contain the same summary fields followed by optional detail metadata. */
    public record Model(UUID modelId, long revision, long policyRevision, String displayName,
                        String internalName, List<String> authors, Uploader uploader, String modelType,
                        long imageRevision, boolean imageAvailable, long downloads, long favorites,
                        boolean favorited, String visibility, boolean requiresAuthorization,
                        List<String> allowedActions, String description, String categoryId,
                        List<String> tags, String moderationStatus, long byteLength, long resourceRevision,
                        String metadataSource, String metadataValidation) {
        public Model {
            authors = immutable(authors);
            allowedActions = immutable(allowedActions);
            tags = immutable(tags);
            if (modelId == null || revision < 1 || policyRevision < 1 || displayName == null
                    || internalName == null || uploader == null
                    || !List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED").contains(modelType)
                    || !List.of("PRIVATE", "PUBLIC").contains(visibility)
                    || imageRevision < 0 || downloads < 0 || favorites < 0 || byteLength < 0) {
                throw new IllegalArgumentException("INVALID_MODEL");
            }
            description = description == null ? "" : description;
            categoryId = categoryId == null ? "" : categoryId;
            moderationStatus = moderationStatus == null ? "ACTIVE" : moderationStatus;
        }

        public boolean allows(String action) {
            return allowedActions.contains(action);
        }
    }

    public record Page(List<Model> items, long total, int page, int pageSize, boolean hasNext) {
        public Page {
            items = immutable(items);
            if (total < 0 || page < 1 || pageSize < 1 || pageSize > 64 || items.size() > pageSize
                    || items.size() > total) {
                throw new IllegalArgumentException("INVALID_PAGE");
            }
        }
    }

    public record CollectionEntry(UUID modelId, long joinedAt, String joinSource, boolean inOutfit, Model model) {}

    public record CollectionPage(List<CollectionEntry> items, long total, int page, int pageSize, boolean hasNext) {
        public CollectionPage {
            items = immutable(items);
            if (total < 0 || page < 1 || pageSize < 1 || pageSize > 64 || items.size() > pageSize) {
                throw new IllegalArgumentException("INVALID_COLLECTION_PAGE");
            }
        }
    }

    public record Transfer(UUID transferId, String purpose, long modelRevision, long policyRevision,
                           String relativePath, long expiresAt) {
        public Transfer {
            if (transferId == null || !List.of("preview", "download").contains(purpose)
                    || modelRevision < 1 || policyRevision < 1 || expiresAt <= 0
                    || !("/api/library/v1/transfers/" + transferId).equals(relativePath)) {
                throw new IllegalArgumentException("INVALID_TRANSFER");
            }
        }
    }

    public record Equipment(UUID modelId, long modelRevision, long equipmentRevision, UUID owner,
                            String assetId, long assetRevision, boolean showPrivateEquipment) {
        public Equipment {
            if (modelId == null || modelRevision < 1 || equipmentRevision < 1 || owner == null
                    || assetId == null || !assetId.matches("lib_[a-f0-9]{32}_[0-9]+") || assetRevision < 1) {
                throw new IllegalArgumentException("INVALID_EQUIPMENT");
            }
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
