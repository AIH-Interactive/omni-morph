package top.aihmc.omnimorph.model.outfit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.parsers.AvatarMetadataParser;

import java.util.List;

/** Shared avatar credits plus accessory declarations from the resource-root cosmetic.json. */
public record CosmeticMetadata(AvatarMetadataParser.Metadata common, String category,
                               String cosmeticAnchor, boolean hasBones, boolean displayBones,
                               boolean editBones, String skeletonRevision, String skeletonHash,
                               List<String> supportedBases, String validationSource) {
    public static final String DIRECTORY = "cosmetics";
    public static final String FILE = "cosmetic.json";
    public static final List<String> CATEGORIES = List.of(
            "Head", "Back", "Feet", "Legs", "Face", "Hair", "Tail", "Trail", "Wings");

    public CosmeticMetadata {
        OutfitConfig.text(category, 64, false);
        OutfitConfig.text(cosmeticAnchor, 128, true);
        OutfitConfig.text(skeletonRevision, 128, true);
        supportedBases = List.copyOf(supportedBases);
        OutfitConfig.require(supportedBases.size() <= 3
                && supportedBases.stream().distinct().count() == supportedBases.size()
                && List.of("VANILLA", "FIGURA", "YSM").containsAll(supportedBases));
        OutfitConfig.require(hasBones || (!displayBones && !editBones));
        OutfitConfig.require(List.of("CLIENT_EXTRACTED", "SERVER_VERIFIED").contains(validationSource));
        if (hasBones) {
            OutfitConfig.hash(skeletonHash);
        } else {
            OutfitConfig.require(skeletonHash.isEmpty());
        }
    }

    /** Caller resolves the explicit resource purpose before selecting one of two manifests. */
    public static CosmeticMetadata read(String cosmeticJson) {
        JsonObject root = JsonParser.parseString(cosmeticJson).getAsJsonObject();
        if (root.has("schema_version")) {
            OutfitConfig.require(root.get("schema_version").getAsInt() == 1);
        }
        return new CosmeticMetadata(AvatarMetadataParser.read(cosmeticJson),
                string(root, "category", "Unknown"), string(root, "cosmetic_anchor", ""),
                flag(root, "has_bones"), flag(root, "display_bones"), flag(root, "edit_bones"),
                string(root, "skeleton_revision", ""), string(root, "skeleton_hash", ""),
                root.has("supported_bases")
                        ? root.getAsJsonArray("supported_bases").asList().stream().map(v -> v.getAsString()).toList()
                        : List.of("VANILLA", "FIGURA", "YSM"),
                "CLIENT_EXTRACTED");
    }

    public OutfitConfig.AnchorRef defaultAnchor() {
        return OutfitConfig.AnchorRef.authorDefault(cosmeticAnchor);
    }

    public static String selectManifest(boolean avatarPresent, boolean cosmeticPresent,
                                        OutfitConfig.ResourceKind declaredKind) {
        if (declaredKind == OutfitConfig.ResourceKind.OUTFIT
                || (!avatarPresent && !cosmeticPresent)
                || (avatarPresent && cosmeticPresent && declaredKind == null)) {
            throw new IllegalArgumentException("AMBIGUOUS_RESOURCE_METADATA");
        }
        if (declaredKind == OutfitConfig.ResourceKind.ACCESSORY || (!avatarPresent && declaredKind == null)) {
            OutfitConfig.require(cosmeticPresent);
            return FILE;
        }
        OutfitConfig.require(avatarPresent);
        return "avatar.json";
    }

    private static boolean flag(JsonObject root, String key) {
        return root.has(key) && root.get(key).getAsBoolean();
    }

    private static String string(JsonObject root, String key, String fallback) {
        return root.has(key) ? root.get(key).getAsString() : fallback;
    }
}
