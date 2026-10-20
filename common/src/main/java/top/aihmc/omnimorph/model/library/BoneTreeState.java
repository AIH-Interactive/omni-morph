package top.aihmc.omnimorph.model.library;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure UI state. Traversal happens only on snapshot/search/expansion changes, never each frame. */
public final class BoneTreeState {
    public record Row(PreviewHierarchy.Node node, int depth, boolean expandable, boolean expanded, boolean matched) {}
    private record Pending(int id, int depth) {}

    private PreviewHierarchy hierarchy = PreviewHierarchy.EMPTY;
    private final Set<Integer> expanded = new HashSet<>();
    private Set<Integer> beforeSearch;
    private final Set<Integer> matches = new HashSet<>();
    private final Set<Integer> searchPaths = new HashSet<>();
    private List<Row> rows = List.of();
    private String query = "";
    private int selected = -1;
    private long modelVersion;
    private double scroll;
    private double horizontalScroll;

    public void replace(PreviewHierarchy hierarchy) {
        this.hierarchy = hierarchy;
        expanded.clear();
        beforeSearch = null;
        matches.clear();
        searchPaths.clear();
        query = "";
        selected = -1;
        scroll = 0;
        horizontalScroll = 0;
        modelVersion++;
        arrange();
    }

    public void search(String value) {
        if (value.equals(query)) {
            return;
        }
        if (query.isEmpty() && !value.isEmpty()) {
            beforeSearch = new HashSet<>(expanded);
        }
        query = value;
        matches.clear();
        searchPaths.clear();
        if (value.isEmpty()) {
            if (beforeSearch != null) {
                expanded.clear();
                expanded.addAll(beforeSearch);
            }
            beforeSearch = null;
        } else {
            expanded.clear();
            if (beforeSearch != null) {
                expanded.addAll(beforeSearch);
            }
            String term = value.toLowerCase(Locale.ROOT);
            for (var node : hierarchy.nodes()) {
                if (node.name().toLowerCase(Locale.ROOT).contains(term)) {
                    matches.add(node.id());
                    int current = node.id();
                    while (current >= 0 && searchPaths.add(current)) {
                        int parent = hierarchy.nodes().get(current).parent();
                        if (parent >= 0) {
                            expanded.add(parent);
                        }
                        current = parent;
                    }
                }
            }
        }
        scroll = 0;
        arrange();
    }

    public void toggle(int id) {
        if (id < 0 || id >= hierarchy.nodes().size()) {
            return;
        }
        if (!expanded.remove(id)) {
            expanded.add(id);
        }
        arrange();
    }

    public void select(int id) {
        selected = id >= 0 && id < hierarchy.nodes().size() ? id : -1;
    }

    private void arrange() {
        var result = new ArrayList<Row>();
        var pending = new ArrayDeque<Pending>();
        for (int index = hierarchy.roots().size() - 1; index >= 0; index--) {
            pending.push(new Pending(hierarchy.roots().get(index), 0));
        }
        while (!pending.isEmpty()) {
            var entry = pending.pop();
            if (!query.isEmpty() && !searchPaths.contains(entry.id())) {
                continue;
            }
            var node = hierarchy.nodes().get(entry.id());
            boolean canExpand = node.children().stream().anyMatch(id -> query.isEmpty() || searchPaths.contains(id));
            result.add(new Row(node, entry.depth(), canExpand, expanded.contains(entry.id()), matches.contains(entry.id())));
            if (expanded.contains(entry.id())) {
                for (int index = node.children().size() - 1; index >= 0; index--) {
                    pending.push(new Pending(node.children().get(index), entry.depth() + 1));
                }
            }
        }
        rows = List.copyOf(result);
    }

    public List<Row> rows() {
        return rows;
    }

    public String query() {
        return query;
    }

    public int selected() {
        return selected;
    }

    public Object selectedPart() {
        return hierarchy.part(selected);
    }

    public long modelVersion() {
        return modelVersion;
    }

    public double scroll() {
        return scroll;
    }

    public void scroll(double value) {
        scroll = value;
    }

    public double horizontalScroll() {
        return horizontalScroll;
    }

    public void horizontalScroll(double value) {
        horizontalScroll = Math.clamp(value, 0, 1);
    }

    public String selectedName() {
        return selected < 0 ? "" : hierarchy.nodes().get(selected).name();
    }
}
