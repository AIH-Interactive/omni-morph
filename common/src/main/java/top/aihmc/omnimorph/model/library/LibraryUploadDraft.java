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
    }

    public void tags(String commaSeparated) {
        var normalized = new ArrayList<String>();
        var identities = new java.util.HashSet<String>();
        for (String raw : commaSeparated.split(",")) {
            String value = raw.strip();
            if (!value.isEmpty() && identities.add(value.toLowerCase(Locale.ROOT))) {
                normalized.add(value);
            }
        }
        tags = List.copyOf(normalized);
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
        validate(displayName, 96, false);
        validate(metadata.internalName(), 96, false);
        validate(description, 4096, true);
        validate(source.getFileName().toString(), 128, false);
        if (metadata.authors().size() > 4 || tags.size() > 8) {
            throw new IllegalArgumentException("METADATA_LIMIT");
        }
        metadata.authors().forEach(author -> validate(author, 48, false));
        tags.forEach(tag -> validate(tag, 32, false));
        return new Snapshot(source, prepared, Map.of("display_name", displayName,
                "internal_name", metadata.internalName(), "authors", metadata.authors(),
                "model_type", metadata.modelType(), "category_id", category, "description", description,
                "tags", tags, "visibility", isPublic ? "PUBLIC" : "PRIVATE",
                "requires_authorization", requiresAuthorization), prepared.metadataSource());
    }

    private static void validate(String value, int maximumBytes, boolean emptyAllowed) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > maximumBytes
                || !emptyAllowed && value.isBlank()
                || value.codePoints().anyMatch(character -> character == 0 || character == '§'
                || Character.isISOControl(character) && character != '\n' && character != '\t')) {
            throw new IllegalArgumentException("METADATA_LIMIT");
        }
    }
}
