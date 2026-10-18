package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.backend2.library.LibraryModels;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Client-thread form state. Source callbacks cannot overwrite values explicitly edited by the player. */
public final class LibraryUploadDraft {
    private final UUID uploader;
    private long generation;
    private Path source;
    private LibraryMetadataReader.Metadata metadata;
    private LibraryPackagePreparation.Prepared prepared;
    private String displayName = "";
    private String description = "";
    private String category = "";
    private List<String> tags = List.of();
    private boolean isPublic = true;
    private boolean requiresAuthorization = true;
    private boolean nameEdited;
    private boolean descriptionEdited;
    private boolean authorizationEdited;
    private boolean categoryEdited;
    private List<String> categories = List.of();

    public LibraryUploadDraft(UUID uploader) {
        this.uploader = java.util.Objects.requireNonNull(uploader);
    }

    public UUID uploader() {
        return uploader;
    }

    public long beginSource(Path path) {
        generation++;
        source = path;
        metadata = null;
        prepared = null;
        displayName = "";
        description = "";
        requiresAuthorization = true;
        nameEdited = false;
        descriptionEdited = false;
        authorizationEdited = false;
        categoryEdited = false;
        defaultCategory(false);
        return generation;
    }

    public boolean metadata(long expectedGeneration, LibraryMetadataReader.Metadata value) {
        if (expectedGeneration != generation || source == null || prepared != null) {
            return false;
        }
        applyMetadata(value);
        return true;
    }

    private void applyMetadata(LibraryMetadataReader.Metadata value) {
        metadata = value;
        if (!nameEdited) {
            displayName = value.internalName();
        }
        if (!descriptionEdited) {
            description = value.description();
        }
        if (!authorizationEdited) {
            requiresAuthorization = value.requiresAuthorizationDefault();
        }
    }

    public boolean prepared(long expectedGeneration, LibraryPackagePreparation.Prepared value) {
        if (expectedGeneration != generation || source == null) {
            return false;
        }
        applyMetadata(value.metadata());
        prepared = value;
        return true;
    }

    public void displayName(String value) {
        displayName = value;
        nameEdited = true;
    }

    public String displayName() {
        return displayName;
    }

    public void description(String value) {
        description = value;
        descriptionEdited = true;
    }

    public String description() {
        return description;
    }

    public void requiresAuthorization(boolean value) {
        requiresAuthorization = value;
        authorizationEdited = true;
    }

    public boolean requiresAuthorization() {
        return requiresAuthorization;
    }

    public void isPublic(boolean value) {
        isPublic = value;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void category(String value) {
        category = value;
        categoryEdited = true;
    }

    public String category() {
        return category;
    }

    public void categories(List<String> available) {
        categories = List.copyOf(available);
        defaultCategory(false);
    }

    public void defaultCategory(boolean decorative) {
        if (categoryEdited || categories.isEmpty()) {
            return;
        }
        String preferred = decorative ? "decorative_parts" : "full_model";
        category = categories.contains(preferred) ? preferred : categories.getFirst();
    }

    public void tags(String commaSeparated) {
        tags = normalizeTags(commaSeparated);
    }

    static List<String> normalizeTags(String commaSeparated) {
        var normalized = new ArrayList<String>();
        var identities = new java.util.HashSet<String>();
        for (String raw : commaSeparated.split(",")) {
            String value = raw.strip();
            if (!value.isEmpty() && identities.add(value.toLowerCase(Locale.ROOT))) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    public record Snapshot(Path source, LibraryPackagePreparation.Prepared prepared,
                           Map<String, Object> metadata, String metadataSource) {
        public Snapshot {
            metadata = Map.copyOf(metadata);
        }
    }

    /** Revalidates current backend limits at submit, rather than trusting limits captured at file selection. */
    public Snapshot snapshot(LibraryModels.Capabilities limits) {
        if (!limits.enabled() || prepared == null || metadata == null || source == null
                || prepared.byteLength() > limits.maxUploadBytes()
                || !limits.categories().contains(category) || !limits.modelTypes().contains(metadata.modelType())) {
            throw new IllegalArgumentException("UPLOAD_NOT_READY");
        }
        validateEditable(displayName, description, tags);
        validate(metadata.internalName(), 96, false, "internal_name");
        validate(source.getFileName().toString(), 128, false, "source");
        metadata.authors().forEach(author -> validate(author, 0, false, "authors"));
        return new Snapshot(source, prepared, Map.of("display_name", displayName,
                "internal_name", metadata.internalName(), "authors", metadata.authors(),
                "model_type", metadata.modelType(), "category_id", category, "description", description,
                "tags", tags, "visibility", isPublic ? "PUBLIC" : "PRIVATE",
                "requires_authorization", requiresAuthorization), prepared.metadataSource());
    }

    static void validateEditable(String name, String description, List<String> tags) {
        validate(name, 96, false, "display_name");
        validate(description, 4096, true, "description");
        if (tags.size() > 8) {
            throw new InvalidCount("tags", 8);
        }
        tags.forEach(tag -> validate(tag, 32, false, "tags"));
    }

    public static final class InvalidCount extends IllegalArgumentException {
        private final String field;
        private final int maximumEntries;

        private InvalidCount(String field, int maximumEntries) {
            super("METADATA_LIMIT");
            this.field = field;
            this.maximumEntries = maximumEntries;
        }

        public String field() {
            return field;
        }

        public int maximumEntries() {
            return maximumEntries;
        }
    }

    public static final class InvalidField extends IllegalArgumentException {
        private final String field;
        private final int maximumBytes;

        private InvalidField(String field, int maximumBytes) {
            super("METADATA_LIMIT");
            this.field = field;
            this.maximumBytes = maximumBytes;
        }

        public String field() {
            return field;
        }

        public int maximumBytes() {
            return maximumBytes;
        }
    }

    private static void validate(String value, int maximumBytes, boolean emptyAllowed, String field) {
        // A zero byte budget means that the source-defined author text has no field length limit.
        if (value == null || maximumBytes > 0 && value.getBytes(StandardCharsets.UTF_8).length > maximumBytes
                || !emptyAllowed && value.isBlank()
                || value.codePoints().anyMatch(character -> character == 0 || character == '§'
                || Character.isISOControl(character) && character != '\n' && character != '\t')) {
            throw new InvalidField(field, maximumBytes);
        }
    }
}
