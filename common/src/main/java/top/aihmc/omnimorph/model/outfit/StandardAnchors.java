package top.aihmc.omnimorph.model.outfit;

import java.util.ArrayList;
import java.util.List;

/** Author groups keep their exact pivots; vanilla derives points from native part-local model units. */
public final class StandardAnchors {
    public record Target(OutfitConfig.BoneRef bone, OutfitConfig.Transform offset) {}

    private StandardAnchors() {
    }

    public static List<Target> resolve(SkeletonIndex skeleton, boolean vanilla, String name) {
        OutfitConfig.require(OutfitConfig.STANDARD_ANCHORS.contains(name));
        var targets = new ArrayList<Target>();
        for (int index = 0; index < skeleton.nodes().size(); index++) {
            if (skeleton.nodes().get(index).name().equals(name)) {
                targets.add(new Target(skeleton.reference(null, index), OutfitConfig.Transform.identity()));
            }
        }
        if (!targets.isEmpty()) {
            return List.copyOf(targets);
        }
        String parent;
        OutfitConfig.Vector position;
        switch (name) {
            case "head_anchor", "hair_anchor" -> {
                parent = "Head";
                position = new OutfitConfig.Vector(0, -8, 0);
            }
            case "face_anchor" -> {
                parent = "Head";
                position = new OutfitConfig.Vector(0, -4, -4);
            }
            case "back_anchor", "wing_anchor" -> {
                parent = "Body";
                position = new OutfitConfig.Vector(0, name.equals("wing_anchor") ? 3 : 6, 2);
            }
            case "hip" -> {
                parent = "Body";
                position = new OutfitConfig.Vector(0, 12, 0);
            }
            case "left_leg_anchor", "right_leg_anchor", "left_foot_anchor", "right_foot_anchor" -> {
                parent = name.startsWith("left_") ? "LeftLeg" : "RightLeg";
                position = new OutfitConfig.Vector(0, name.contains("_foot_") ? 12 : 6, 0);
            }
            default -> {
                // A vanilla player has no natural tail tip.
                return List.of();
            }
        }
        var index = skeleton.uniqueNamed(parent);
        if (index.isPresent()) {
            return List.of(new Target(skeleton.reference(null, index.get()), new OutfitConfig.Transform(position,
                    new OutfitConfig.Vector(0, 0, 0), new OutfitConfig.Vector(1, 1, 1))));
        }
        if (vanilla) {
            return List.of();
        }
        // Custom Figura/YSM rigs often have a localized or aliased head/body bone instead of the vanilla name.
        // Resolve it by a case-insensitive alias before falling back to the model's first rig node.
        int alias = aliasIndex(skeleton, parent);
        if (alias >= 0) {
            return List.of(new Target(skeleton.reference(null, alias), new OutfitConfig.Transform(position,
                    new OutfitConfig.Vector(0, 0, 0), new OutfitConfig.Vector(1, 1, 1))));
        }
        boolean headLike = name.equals("head_anchor") || name.equals("hair_anchor") || name.equals("face_anchor");
        if (headLike && skeleton.nodes().size() > 1) {
            // No named head at all: the first authored rig node is the closest stable attachment.
            return List.of(new Target(skeleton.reference(null, 1), OutfitConfig.Transform.identity()));
        }
        return List.of();
    }

    private static int aliasIndex(SkeletonIndex skeleton, String canonical) {
        for (int index = 0; index < skeleton.nodes().size(); index++) {
            String name = skeleton.nodes().get(index).name();
            if (name.equalsIgnoreCase(canonical)) {
                return index;
            }
        }
        for (int index = 0; index < skeleton.nodes().size(); index++) {
            String name = skeleton.nodes().get(index).name().toLowerCase(java.util.Locale.ROOT);
            String target = canonical.toLowerCase(java.util.Locale.ROOT);
            if (name.contains(target) || (target.contains(name) && name.length() > 2)) {
                return index;
            }
        }
        return -1;
    }
}
