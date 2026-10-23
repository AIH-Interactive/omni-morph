package top.aihmc.omnimorph.wizards;

import top.aihmc.omnimorph.exporters.BlockBenchModel;
import top.aihmc.omnimorph.exporters.BlockBenchModel.Group;
import top.aihmc.omnimorph.math.vector.FiguraVec3;

/** Small editable geometry in the selected attachment's local coordinates. */
public enum CosmeticTemplate {
    HEAD("Head", "head_anchor"),
    BACK("Back", "back_anchor"),
    FEET("Feet", "left_foot_anchor"),
    LEGS("Legs", "left_leg_anchor"),
    FACE("Face", "face_anchor"),
    HAIR("Hair", "hair_anchor"),
    TAIL("Tail", "hip"),
    TRAIL("Trail", "tailtip"),
    WINGS("Wings", "wing_anchor");

    private final String category;
    private final String anchor;

    CosmeticTemplate(String category, String anchor) {
        this.category = category;
        this.anchor = anchor;
    }

    public String category() {
        return category;
    }

    public String anchor() {
        return anchor;
    }

    public BlockBenchModel model(String baseTexture) {
        var model = new BlockBenchModel("free");
        model.setResolution(32, 32);
        if (this != TRAIL) {
            model.addImage("cosmetic_base", baseTexture, 32, 32);
        }
        Group root = model.addGroup("Cosmetic", FiguraVec3.of());
        switch (this) {
            case HEAD -> {
                Group head = group(model, "Headwear", root);
                box(model, head, "Brim", -4, 0, -3, 8, 1, 6);
                box(model, head, "Crown", -3, 1, -2, 6, 3, 4);
            }
            case BACK -> {
                Group pack = group(model, "Backpack", root);
                box(model, pack, "Bag", -3, -4, -2, 6, 8, 3);
                Group flap = model.addGroup("Flap", FiguraVec3.of(0, 4, -2), pack);
                box(model, flap, "Lid", -3, 3, -2.5, 6, 1, 4);
            }
            case FEET -> box(model, group(model, "Footwear", root), "Shoe", -2, 0, -2.5, 4, 1, 5);
            case LEGS -> box(model, group(model, "Legwear", root), "Cuff", -2, -1.5, -2, 4, 3, 4);
            case FACE -> {
                Group glasses = group(model, "Glasses", root);
                box(model, glasses, "Bridge", -1, .5, -.5, 2, .5, 1);
                box(model, group(model, "LeftLens", glasses), "Lens", -3.5, 0, -.25, 3, 2, .5);
                box(model, group(model, "RightLens", glasses), "Lens", .5, 0, -.25, 3, 2, .5);
            }
            case HAIR -> {
                Group clip = group(model, "HairClip", root);
                box(model, clip, "Clip", -1.5, 0, -.5, 3, 1, 1);
                box(model, group(model, "Ribbon", clip), "Strap", -1, -4, -.25, 2, 4, .5);
            }
            case TAIL -> {
                Group base = group(model, "TailBase", root);
                box(model, base, "Segment", -1, -1, 0, 2, 2, 3);
                Group mid = model.addGroup("TailMid", FiguraVec3.of(0, 0, 3), base);
                box(model, mid, "Segment", -.8, -.8, 3, 1.6, 1.6, 3);
                Group end = model.addGroup("TailEnd", FiguraVec3.of(0, 0, 6), mid);
                box(model, end, "Segment", -.5, -.5, 6, 1, 1, 3);
                model.addGroup("tailtip", FiguraVec3.of(0, 0, 9), end);
            }
            case TRAIL -> {
                // A single real, empty root. Trail sampling uses its resolved mounted pose.
            }
            case WINGS -> {
                Group wings = group(model, "WingRoot", root);
                box(model, group(model, "LeftWing", wings), "Feathers", 0, -2, -.5, 6, 5, 1);
                box(model, group(model, "RightWing", wings), "Feathers", -6, -2, -.5, 6, 5, 1);
            }
        }
        return model;
    }

    private static Group group(BlockBenchModel model, String name, Group parent) {
        return model.addGroup(name, FiguraVec3.of(), parent);
    }

    private static void box(BlockBenchModel model, Group parent, String name,
                            double x, double y, double z, double width, double height, double depth) {
        model.addCube(name, FiguraVec3.of(x, y, z), FiguraVec3.of(width, height, depth), parent)
                .generateBoxFaces(0, 0, 0);
    }
}
