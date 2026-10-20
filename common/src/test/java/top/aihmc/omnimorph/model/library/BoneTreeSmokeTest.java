package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.model.FiguraModelPart;
import top.aihmc.omnimorph.model.PartCustomization;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BoneTreeSmokeTest {
    public static void main(String[] args) {
        var head = figura("Head", List.of(figura("眼睛", List.of())));
        var hand = figura("Hand", List.of());
        var otherHand = figura("Hand", List.of());
        var body = figura("Body", List.of(head, hand));
        var container = figura("models", List.of(body, otherHand));
        var hierarchy = PreviewHierarchy.fromFigura(container);
        require(hierarchy.roots().size() == 2 && hierarchy.nodes().size() == 5,
                "Figura synthetic root is hidden and real part hierarchy is preserved");
        require(hierarchy.nodes().get(3).name().equals("Hand") && hierarchy.nodes().get(4).name().equals("Hand")
                && hierarchy.nodes().get(3).id() != hierarchy.nodes().get(4).id(), "same-name siblings/branches retain unique IDs");
        var state = new BoneTreeState();
        state.replace(hierarchy);
        require(names(state).equals(List.of("Body", "Hand")), "initial state displays collapsed roots only");
        state.toggle(0);
        require(names(state).equals(List.of("Body", "Head", "Hand", "Hand")), "one-level expansion");
        var before = state.rows();
        state.search("眼睛");
        require(names(state).equals(List.of("Body", "Head", "眼睛")), "CJK search expands every matching ancestor");
        require(state.rows().get(2).matched() && state.rows().get(1).expanded(), "matches and expanded ancestors are marked");
        state.select(2);
        require(state.selectedName().equals("眼睛"), "selection reads the correct node");
        state.search("hAnD");
        require(state.rows().stream().filter(BoneTreeState.Row::matched).count() == 2, "search ignores case and finds duplicate names");
        state.search("missing");
        require(state.rows().isEmpty(), "no matches is an empty result");
        state.search("");
        require(state.rows().equals(before), "clearing search restores the original expansion state after multiple query changes");
        require(container.children.size() == 2 && head.children.size() == 1 && head.customization.visible,
                "tree operations preserve the model's hierarchy and customization");

        var root = new YsmModelPart("root", null, new float[3], new float[3]);
        var arm = new YsmModelPart("arm", root, new float[3], new float[3]);
        new YsmModelPart("finger", arm, new float[3], new float[3]);
        var ysm = PreviewHierarchy.fromYsm(List.of(root, root));
        require(ysm.nodes().size() == 3 && ysm.roots().size() == 1, "YSM roots use real children and ignore runtime aliases");
        state.replace(ysm);
        require(names(state).equals(List.of("root")) && state.selected() == -1 && state.query().isEmpty(),
                "model switch clears old nodes, search, selection and expansion");
        state.search("finger");
        require(names(state).equals(List.of("root", "arm", "finger")), "YSM search follows the true parent chain");
        state.replace(PreviewHierarchy.EMPTY);
        require(state.rows().isEmpty() && state.selectedName().isEmpty(), "loading/failure clears old model nodes");

        var deepRoot = new YsmModelPart("start", null, new float[3], new float[3]);
        var current = deepRoot;
        for (int index = 0; index < 5000; index++) {
            current = new YsmModelPart("node-" + index, current, new float[3], new float[3]);
        }
        state.replace(PreviewHierarchy.fromYsm(List.of(deepRoot)));
        state.search("node-4999");
        require(state.rows().size() == 5001, "deep model capture and ancestor expansion do not overflow the Java stack");
        System.out.println("PASS bone tree: actual Figura/YSM parts, collapsed roots, unique IDs, search/restore, switch cleanup, read-only and deep hierarchies; GUI MANUAL/PENDING");
    }

    private static FiguraModelPart figura(String name, List<FiguraModelPart> children) {
        return new FiguraModelPart(null, name, new PartCustomization(), Map.of(), new ArrayList<>(children));
    }

    private static List<String> names(BoneTreeState state) {
        return state.rows().stream().map(row -> row.node().name()).toList();
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
