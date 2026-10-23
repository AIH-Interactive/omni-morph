package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure UI state. Traversal happens only on snapshot/search/expansion changes, never each frame. */
public final class BoneTreeState {
    public record Row(PreviewHierarchy.Node node, int depth, boolean expandable, boolean expanded, boolean matched) {}
    private record Pending(int id, int depth) {}
    public record View(OutfitHierarchy.Kind kind, UUID instance) {
        public static final View ALL = new View(null, null);
    }
    public record ViewChoice(View view, String label) {}
    private record TargetKey(OutfitHierarchy.Kind kind, UUID instance, OutfitConfig.BoneRef bone,
                             UUID anchor, String standard) {}
    private record Saved(Set<TargetKey> expanded, Set<TargetKey> beforeSearch, String query,
                         TargetKey selected, double scroll, double horizontalScroll) {}

    private PreviewHierarchy hierarchy = PreviewHierarchy.EMPTY;
    private OutfitHierarchy outfit = OutfitHierarchy.EMPTY;
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
    private View view = View.ALL;
    private final Map<View, Saved> views = new HashMap<>();
    private List<Integer> viewRoots = List.of();
    private final Set<Integer> inView = new HashSet<>();
    private List<ViewChoice> choices = List.of(new ViewChoice(View.ALL, ""));

    public void replace(PreviewHierarchy hierarchy) {
        outfit = OutfitHierarchy.EMPTY;
        view = View.ALL;
        views.clear();
        choices = List.of(new ViewChoice(View.ALL, ""));
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
        updateViewNodes();
        arrange();
    }

    public void replace(OutfitHierarchy hierarchy) {
        saveView();
        this.hierarchy = hierarchy.hierarchy();
        outfit = hierarchy;
        refreshViewChoices();
        var available = viewChoices().stream().map(ViewChoice::view).collect(java.util.stream.Collectors.toSet());
        views.keySet().retainAll(available);
        if (!available.contains(view)) {
            view = View.ALL;
        }
        updateViewNodes();
        restoreView();
    }

    public View view() {
        return view;
    }

    public List<ViewChoice> viewChoices() {
        return choices;
    }

    private void refreshViewChoices() {
        var choices = new ArrayList<ViewChoice>();
        choices.add(new ViewChoice(View.ALL, ""));
        for (var node : hierarchy.nodes()) {
            var target = outfit.target(node.id());
            if (target == null) {
                continue;
            }
            if (target.kind() == OutfitHierarchy.Kind.BASE || target.kind() == OutfitHierarchy.Kind.STANDARD_GROUP
                    || target.kind() == OutfitHierarchy.Kind.VIRTUAL_GROUP
                    || target.kind() == OutfitHierarchy.Kind.INSTANCE && outfit.activeInstances().contains(target.instanceId())) {
                choices.add(new ViewChoice(new View(target.kind(), target.instanceId()), node.name()));
            }
        }
        this.choices = List.copyOf(choices);
    }

    public void view(View next) {
        if (next.equals(view) || viewChoices().stream().noneMatch(choice -> choice.view().equals(next))) {
            return;
        }
        saveView();
        view = next;
        updateViewNodes();
        restoreView();
    }

    private TargetKey key(int id) {
        var target = outfit.target(id);
        return target == null ? null : new TargetKey(target.kind(), target.instanceId(), target.bone(),
                target.anchorId(), target.standard());
    }

    private Set<TargetKey> keys(Set<Integer> ids) {
        if (ids == null) {
            return null;
        }
        var result = new HashSet<TargetKey>();
        for (int id : ids) {
            var key = key(id);
            if (key != null) {
                result.add(key);
            }
        }
        return Set.copyOf(result);
    }

    private void saveView() {
        if (outfit != OutfitHierarchy.EMPTY) {
            views.put(view, new Saved(keys(expanded), keys(beforeSearch), query, key(selected), scroll, horizontalScroll));
        }
    }

    private void restoreView() {
        var saved = views.get(view);
        var ids = new HashMap<TargetKey, Integer>();
        for (int id : inView) {
            ids.put(key(id), id);
        }
        expanded.clear();
        beforeSearch = null;
        matches.clear();
        searchPaths.clear();
        query = "";
        selected = -1;
        scroll = 0;
        horizontalScroll = 0;
        if (saved != null) {
            for (var key : saved.expanded()) {
                if (ids.containsKey(key)) {
                    expanded.add(ids.get(key));
                }
            }
            select(ids.getOrDefault(saved.selected(), -1));
            search(saved.query());
            if (saved.beforeSearch() != null) {
                beforeSearch = new HashSet<>();
                for (var key : saved.beforeSearch()) {
                    if (ids.containsKey(key)) {
                        beforeSearch.add(ids.get(key));
                    }
                }
            }
            scroll = saved.scroll();
            horizontalScroll = saved.horizontalScroll();
        }
        modelVersion++;
        arrange();
    }

    private void updateViewNodes() {
        viewRoots = view.equals(View.ALL) ? hierarchy.roots() : hierarchy.nodes().stream()
                .filter(node -> {
                    var target = outfit.target(node.id());
                    return target != null && target.kind() == view.kind()
                            && java.util.Objects.equals(target.instanceId(), view.instance());
                }).map(PreviewHierarchy.Node::id).toList();
        inView.clear();
        var pending = new ArrayDeque<Integer>(viewRoots);
        while (!pending.isEmpty()) {
            int id = pending.pop();
            if (inView.add(id)) {
                pending.addAll(hierarchy.nodes().get(id).children());
            }
        }
    }

    public OutfitHierarchy.Target selectedTarget() {
        return outfit.target(selected);
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
                if (inView.contains(node.id()) && node.name().toLowerCase(Locale.ROOT).contains(term)) {
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
        var target = outfit.target(id);
        boolean inactiveBone = target != null && target.kind() == OutfitHierarchy.Kind.INSTANCE_BONE
                && !outfit.activeInstances().contains(target.instanceId());
        selected = inView.contains(id) && !inactiveBone ? id : -1;
    }

    private void arrange() {
        var result = new ArrayList<Row>();
        var pending = new ArrayDeque<Pending>();
        for (int index = viewRoots.size() - 1; index >= 0; index--) {
            pending.push(new Pending(viewRoots.get(index), 0));
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
        var target = selectedTarget();
        if (target != null && target.kind() == OutfitHierarchy.Kind.STANDARD) {
            return target.mountable() ? OutfitConfig.AnchorRef.authorDefault(target.standard()) : null;
        }
        if (target != null && target.kind() == OutfitHierarchy.Kind.VIRTUAL) {
            return new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL, null, null, target.anchorId(), null, null);
        }
        Object part = hierarchy.part(selected);
        if (part instanceof OutfitHierarchy.Target) {
            return null;
        }
        if (part instanceof top.aihmc.omnimorph.model.outfit.VanillaSkeletonAdapter.Part vanilla) {
            if (vanilla.nativePart() != null) {
                return vanilla.nativePart();
            }
            return target == null ? null : new OutfitConfig.AnchorRef(
                    OutfitConfig.AnchorType.BASE_BONE, null, target.bone(), null, null, null);
        }
        return part;
    }

    public boolean locate(OutfitConfig.AnchorRef anchor) {
        for (var node : hierarchy.nodes()) {
            var target = outfit.target(node.id());
            if (target == null) {
                continue;
            }
            boolean matches = switch (anchor.type()) {
                case STANDARD -> target.kind() == OutfitHierarchy.Kind.STANDARD && anchor.name().equals(target.standard());
                case BASE_BONE -> target.kind() == OutfitHierarchy.Kind.BASE_BONE && anchor.bone().equals(target.bone());
                case ACCESSORY_BONE -> target.kind() == OutfitHierarchy.Kind.INSTANCE_BONE && anchor.bone().equals(target.bone());
                case VIRTUAL -> target.kind() == OutfitHierarchy.Kind.VIRTUAL && anchor.anchorId().equals(target.anchorId());
                default -> false;
            };
            if (!matches) {
                continue;
            }
            var kind = switch (target.kind()) {
                case STANDARD -> OutfitHierarchy.Kind.STANDARD_GROUP;
                case VIRTUAL -> OutfitHierarchy.Kind.VIRTUAL_GROUP;
                case INSTANCE_BONE -> OutfitHierarchy.Kind.INSTANCE;
                default -> OutfitHierarchy.Kind.BASE;
            };
            view(new View(kind, target.instanceId()));
            search("");
            for (int parent = node.parent(); parent >= 0; parent = hierarchy.nodes().get(parent).parent()) {
                expanded.add(parent);
            }
            select(node.id());
            arrange();
            for (int row = 0; row < rows.size(); row++) {
                if (rows.get(row).node().id() == selected) {
                    scroll = row / (double) Math.max(1, rows.size() - 1);
                    break;
                }
            }
            modelVersion++;
            return selected >= 0;
        }
        select(-1);
        return false;
    }

    public void locateBaseRoot() {
        view(new View(OutfitHierarchy.Kind.BASE, null));
        search("");
        select(rows.isEmpty() ? -1 : rows.getFirst().node().id());
        scroll = 0;
        modelVersion++;
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
