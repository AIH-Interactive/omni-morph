package top.aihmc.omnimorph.model.outfit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Immutable structural definition; runtime parts and GUI snapshot IDs never enter persistent references. */
public final class SkeletonIndex {
    public record Node(int parent, String name, List<OutfitConfig.BoneSegment> path, List<Integer> children) {
        public Node {
            path = List.copyOf(path);
            children = List.copyOf(children);
        }
    }

    public record Binding<T>(SkeletonIndex definition, UUID instanceId, List<T> parts) {
        public Binding {
            parts = List.copyOf(parts);
            OutfitConfig.require(parts.size() == definition.nodes.size());
        }

        public Optional<T> resolve(OutfitConfig.BoneRef reference) {
            if (!java.util.Objects.equals(instanceId, reference.instanceId())) {
                return Optional.empty();
            }
            return definition.resolve(reference).map(parts::get);
        }
    }

    /** Owned by one account/session; shared definitions cannot hold another instance's mutable parts. */
    public static final class Cache {
        private final Map<OutfitConfig.ResourceRef, SkeletonIndex> definitions = new HashMap<>();
        private int parses;

        public synchronized <T> Binding<T> bind(OutfitConfig.ResourceRef resource, UUID instanceId, List<T> roots,
                                    Function<T, String> name, Function<T, List<T>> children,
                                    OutfitLimits limits) {
            SkeletonIndex definition = definitions.get(resource);
            if (definition == null) {
                Binding<T> captured = capture(instanceId, roots, name, children, limits);
                definitions.put(resource, captured.definition());
                parses++;
                return captured;
            }
            definition.checkLimits(limits);
            return new Binding<>(definition, instanceId, definition.bindParts(roots, name, children));
        }

        public synchronized int parseCount() {
            return parses;
        }

        public synchronized void clear() {
            definitions.clear();
        }
    }

    private record Pending<T>(T part, int parent, List<OutfitConfig.BoneSegment> path) {}
    private record Bound<T>(T part, int index) {}

    private final List<Node> nodes;
    private final List<Integer> roots;
    private final Map<List<OutfitConfig.BoneSegment>, Integer> paths;
    private final String hash;
    private final int depth;

    private SkeletonIndex(List<Node> nodes, List<Integer> roots, String hash) {
        this.nodes = List.copyOf(nodes);
        this.roots = List.copyOf(roots);
        this.hash = hash;
        var paths = new HashMap<List<OutfitConfig.BoneSegment>, Integer>();
        int depth = 0;
        for (int index = 0; index < nodes.size(); index++) {
            paths.put(nodes.get(index).path(), index);
            depth = Math.max(depth, nodes.get(index).path().size());
        }
        this.paths = Map.copyOf(paths);
        this.depth = depth;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Integer> roots() {
        return roots;
    }

    public String hash() {
        return hash;
    }

    public OutfitConfig.BoneRef reference(UUID instanceId, int index) {
        return new OutfitConfig.BoneRef(instanceId, nodes.get(index).path(), hash);
    }

    public Optional<Integer> resolve(OutfitConfig.BoneRef reference) {
        return hash.equals(reference.skeletonHash()) ? Optional.ofNullable(paths.get(reference.path())) : Optional.empty();
    }

    /** A duplicate standard name must be resolved by its full path, never by the first traversal hit. */
    public Optional<Integer> uniqueNamed(String name) {
        Integer found = null;
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).name().equals(name)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = index;
            }
        }
        return Optional.ofNullable(found);
    }

    private void checkLimits(OutfitLimits limits) {
        OutfitConfig.require(nodes.size() <= limits.maxResourceNodes() && depth <= limits.maxDepth());
    }

    public static <T> Binding<T> capture(UUID instanceId, List<T> roots, Function<T, String> name,
                                         Function<T, List<T>> children, OutfitLimits limits) {
        var pending = new ArrayDeque<Pending<T>>();
        push(pending, roots, -1, List.of(), name, limits);
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var parts = new ArrayList<T>();
        var names = new ArrayList<String>();
        var parents = new ArrayList<Integer>();
        var paths = new ArrayList<List<OutfitConfig.BoneSegment>>();
        var childIds = new ArrayList<List<Integer>>();
        var rootIds = new ArrayList<Integer>();
        while (!pending.isEmpty()) {
            Pending<T> entry = pending.pop();
            // A native hand/arm pass can expose the same part through two paths. Keep the first path
            // instead of failing the whole frame; its children are already represented by that path.
            if (!seen.add(entry.part())) {
                continue;
            }
            OutfitConfig.require(parts.size() < limits.maxResourceNodes());
            int id = parts.size();
            parts.add(entry.part());
            names.add(name.apply(entry.part()));
            parents.add(entry.parent());
            paths.add(entry.path());
            childIds.add(new ArrayList<>());
            if (entry.parent() < 0) {
                rootIds.add(id);
            } else {
                childIds.get(entry.parent()).add(id);
            }
            push(pending, children.apply(entry.part()), id, entry.path(), name, limits);
        }
        var nodes = new ArrayList<Node>();
        for (int index = 0; index < parts.size(); index++) {
            nodes.add(new Node(parents.get(index), names.get(index), paths.get(index), childIds.get(index)));
        }
        return new Binding<>(new SkeletonIndex(nodes, rootIds, structuralHash(nodes)), instanceId, parts);
    }

    private static <T> void push(ArrayDeque<Pending<T>> pending, List<T> children, int parent,
                                  List<OutfitConfig.BoneSegment> path, Function<T, String> name,
                                  OutfitLimits limits) {
        OutfitConfig.require(children.size() <= limits.maxResourceNodes());
        OutfitConfig.require(pending.size() + children.size() <= limits.maxResourceNodes());
        if (!children.isEmpty()) {
            OutfitConfig.require(path.size() < limits.maxDepth());
        }
        var ordinals = new HashMap<String, Integer>();
        var entries = new ArrayList<Pending<T>>();
        for (T child : children) {
            String childName = name.apply(child);
            int ordinal = ordinals.merge(childName, 1, Integer::sum) - 1;
            var childPath = new ArrayList<>(path);
            childPath.add(new OutfitConfig.BoneSegment(childName, ordinal));
            entries.add(new Pending<>(child, parent, List.copyOf(childPath)));
        }
        for (int index = entries.size() - 1; index >= 0; index--) {
            pending.push(entries.get(index));
        }
    }

    private <T> List<T> bindParts(List<T> parts, Function<T, String> name, Function<T, List<T>> children) {
        OutfitConfig.require(parts.size() == roots.size());
        var pending = new ArrayDeque<Bound<T>>();
        for (int index = 0; index < roots.size(); index++) {
            pending.push(new Bound<>(parts.get(index), roots.get(index)));
        }
        var bound = new ArrayList<T>(Collections.nCopies(nodes.size(), null));
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!pending.isEmpty()) {
            Bound<T> entry = pending.pop();
            Node expected = nodes.get(entry.index());
            List<T> values = children.apply(entry.part());
            OutfitConfig.require(seen.add(entry.part()) && expected.name().equals(name.apply(entry.part()))
                    && expected.children().size() == values.size());
            bound.set(entry.index(), entry.part());
            for (int index = 0; index < values.size(); index++) {
                pending.push(new Bound<>(values.get(index), expected.children().get(index)));
            }
        }
        return List.copyOf(bound);
    }

    private static String structuralHash(List<Node> nodes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Node node : nodes) {
                byte[] name = node.name().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(8).putInt(node.parent()).putInt(name.length).array());
                digest.update(name);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
