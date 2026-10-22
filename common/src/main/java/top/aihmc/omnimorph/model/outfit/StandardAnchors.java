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
        if (!targets.isEmpty() || !vanilla) {
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
        if (index.isEmpty()) {
            return List.of();
        }
        return List.of(new Target(skeleton.reference(null, index.get()), new OutfitConfig.Transform(position,
                new OutfitConfig.Vector(0, 0, 0), new OutfitConfig.Vector(1, 1, 1))));
    }
}
