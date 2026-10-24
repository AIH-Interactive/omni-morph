package top.aihmc.omnimorph.model.library;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.FiguraModelPart;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Immutable read-only snapshot of one loaded preview. IDs are unique within this snapshot. */
public record PreviewHierarchy(List<Node> nodes, List<Integer> roots, List<Object> parts) {
    public static final PreviewHierarchy EMPTY = new PreviewHierarchy(List.of(), List.of());

    public record Node(int id, int parent, String name, List<Integer> children) {
        public Node {
            children = List.copyOf(children);
        }
    }

    private record Pending<T>(T part, int parent) {}

    public PreviewHierarchy {
        nodes = List.copyOf(nodes);
        roots = List.copyOf(roots);
        parts = List.copyOf(parts);
    }

    public PreviewHierarchy(List<Node> nodes, List<Integer> roots) {
        this(nodes, roots, List.of());
    }

    /** Identity is used only for GUI selection feedback; no model edits are exposed by the tree. */
    public Object part(int id) {
        return id < 0 || id >= parts.size() ? null : parts.get(id);
    }

    public static PreviewHierarchy fromAvatar(Avatar avatar) {
        if (avatar.getYsmRuntime() != null) {
            return fromYsm(avatar.getYsmRuntime().rootParts());
        }
        return avatar.renderer == null ? EMPTY : fromFigura(avatar.renderer.root);
    }

    public static PreviewHierarchy fromFigura(FiguraModelPart root) {
        if (root == null) {
            return EMPTY;
        }
        boolean container = (root.name.isEmpty() || root.name.equals("models"))
                && (root.vertices == null || root.vertices.isEmpty());
        return capture(container ? List.copyOf(root.children) : List.of(root), part -> part.name,
                part -> List.copyOf(part.children));
    }

    public static PreviewHierarchy fromYsm(List<YsmModelPart> roots) {
        return capture(roots, YsmModelPart::getName, part -> List.copyOf(part.getChildren()));
    }

    private static <T> PreviewHierarchy capture(List<T> roots, Function<T, String> name,
                                                Function<T, List<T>> children) {
        var pending = new ArrayDeque<Pending<T>>();
        for (int index = roots.size() - 1; index >= 0; index--) {
            pending.push(new Pending<>(roots.get(index), -1));
        }
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var names = new ArrayList<String>();
        var parents = new ArrayList<Integer>();
        var childIds = new ArrayList<List<Integer>>();
        var rootIds = new ArrayList<Integer>();
        var references = new ArrayList<Object>();
        while (!pending.isEmpty()) {
            var entry = pending.pop();
            if (!seen.add(entry.part())) {
                continue; // Runtime aliases or a malformed cycle must not duplicate or loop the GUI tree.
            }
            int id = names.size();
            references.add(entry.part());
            names.add(name.apply(entry.part()));
            parents.add(entry.parent());
            childIds.add(new ArrayList<>());
            if (entry.parent() < 0) {
                rootIds.add(id);
            } else {
                childIds.get(entry.parent()).add(id);
            }
            var parts = children.apply(entry.part());
            for (int index = parts.size() - 1; index >= 0; index--) {
                pending.push(new Pending<>(parts.get(index), id));
            }
        }
        var nodes = new ArrayList<Node>();
        for (int id = 0; id < names.size(); id++) {
            nodes.add(new Node(id, parents.get(id), names.get(id), childIds.get(id)));
        }
        return new PreviewHierarchy(nodes, rootIds, references);
    }
}
