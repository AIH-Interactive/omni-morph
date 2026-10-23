package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.StandardAnchors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Display IDs never escape this snapshot; persistent edits use typed, instance-owned references. */
public record OutfitHierarchy(PreviewHierarchy hierarchy, Map<Integer, Target> targets, Set<UUID> activeInstances) {
    public static final OutfitHierarchy EMPTY = new OutfitHierarchy(PreviewHierarchy.EMPTY, Map.of(), Set.of());

    public enum Kind {
        BASE, ACCESSORIES, INSTANCE, BASE_BONE, INSTANCE_BONE,
        STANDARD_GROUP, STANDARD, VIRTUAL_GROUP, VIRTUAL, STATUS
    }

    public record AnchorChoice(String label, OutfitConfig.AnchorRef anchor) {}

    public record Target(Kind kind, UUID instanceId, OutfitConfig.BoneRef bone, UUID anchorId,
                          String standard, boolean editable, boolean mountable) {}

    public record Labels(String base, String accessories, String standards, String virtuals,
                          String noBones, String hiddenBones, String disabled) {}

    public OutfitHierarchy {
        targets = Map.copyOf(targets);
        activeInstances = Set.copyOf(activeInstances);
    }

    public Target target(int id) {
        return targets.get(id);
    }

    /** Ordered like the visible tree; display IDs are never persisted as attachment references. */
    public List<AnchorChoice> anchors(UUID attachingInstance) {
        var choices = new ArrayList<AnchorChoice>();
        for (var node : hierarchy.nodes()) {
            var target = targets.get(node.id());
            if (target == null || !target.mountable()
                    || attachingInstance != null && attachingInstance.equals(target.instanceId())) {
                continue;
            }
            var type = switch (target.kind()) {
                case BASE_BONE -> OutfitConfig.AnchorType.BASE_BONE;
                case INSTANCE_BONE -> OutfitConfig.AnchorType.ACCESSORY_BONE;
                case VIRTUAL -> OutfitConfig.AnchorType.VIRTUAL;
                default -> null;
            };
            if (type == null) {
                continue;
            }
            var names = new java.util.ArrayDeque<String>();
            for (var ancestor = node; ; ancestor = hierarchy.nodes().get(ancestor.parent())) {
                names.addFirst(ancestor.name());
                if (ancestor.parent() < 0) {
                    break;
                }
            }
            var anchor = new OutfitConfig.AnchorRef(type, null, target.bone(), target.anchorId(), null, null);
            choices.add(new AnchorChoice(String.join(" / ", names), anchor));
        }
        return List.copyOf(choices);
    }

    public static OutfitHierarchy capture(OutfitComposition.EditorSnapshot snapshot, Labels labels) {
        var builder = new Builder();
        int base = builder.add(-1, labels.base(), target(Kind.BASE, null), null);
        builder.skeleton(base, snapshot.base(), null, null, false, Set.of());
        int standards = builder.add(-1, labels.standards(), target(Kind.STANDARD_GROUP, null), null);
        for (String name : OutfitConfig.STANDARD_ANCHORS.stream().sorted().toList()) {
            boolean available = StandardAnchors.resolve(
                    snapshot.base().definition(), snapshot.configuration().base() == null, name).size() == 1;
            builder.add(standards, name, new Target(Kind.STANDARD, null, null, null, name, false, available), null);
        }
        int accessories = builder.add(-1, labels.accessories(), target(Kind.ACCESSORIES, null), null);
        for (var entry : snapshot.instances()) {
            var declaration = entry.declaration();
            var metadata = entry.metadata();
            String name = metadata == null ? declaration.resource().format().name() : metadata.common().name;
            if (name == null || name.isBlank()) {
                name = declaration.resource().format().name();
            }
            name += " #" + declaration.displayOrdinal();
            if (!declaration.enabled()) {
                name += " · " + labels.disabled();
            }
            int instance = builder.add(accessories, name, target(Kind.INSTANCE, declaration.instanceId()), null);
            if (metadata == null || !metadata.hasBones()) {
                builder.add(instance, labels.noBones(), target(Kind.STATUS, declaration.instanceId()), null);
            } else if (!metadata.displayBones()) {
                builder.add(instance, labels.hiddenBones(), target(Kind.STATUS, declaration.instanceId()), null);
            } else {
                if (declaration.enabled()) {
                    builder.activeInstances.add(declaration.instanceId());
                }
                builder.skeleton(instance, entry.skeleton(), declaration.instanceId(), metadata,
                        entry.mayEdit(), entry.exported());
            }
        }
        int virtuals = builder.add(-1, labels.virtuals(), target(Kind.VIRTUAL_GROUP, null), null);
        for (var anchor : snapshot.configuration().virtualAnchors()) {
            builder.add(virtuals, anchor.name(), new Target(Kind.VIRTUAL, null, null, anchor.id(), null, true, true), null);
        }
        return builder.build();
    }

    private static Target target(Kind kind, UUID instance) {
        return new Target(kind, instance, null, null, null, false, false);
    }

    private static final class Builder {
        private final List<String> names = new ArrayList<>();
        private final List<Integer> parents = new ArrayList<>();
        private final List<List<Integer>> children = new ArrayList<>();
        private final List<Integer> roots = new ArrayList<>();
        private final List<Object> parts = new ArrayList<>();
        private final Map<Integer, Target> targets = new HashMap<>();
        private final Set<UUID> activeInstances = new HashSet<>();

        private int add(int parent, String name, Target target, Object part) {
            int id = names.size();
            names.add(name);
            parents.add(parent);
            children.add(new ArrayList<>());
            parts.add(part == null ? target : part);
            targets.put(id, target);
            if (parent < 0) {
                roots.add(id);
            } else {
                children.get(parent).add(id);
            }
            return id;
        }

        private void skeleton(int parent, SkeletonIndex.Binding<?> binding, UUID instance,
                               CosmeticMetadata metadata,
                               boolean authorized, Set<OutfitConfig.BoneRef> exported) {
            var ids = new ArrayList<Integer>();
            var editable = metadata == null ? Set.<OutfitConfig.BoneRef>of()
                    : new HashSet<>(metadata.editableBones());
            var skeleton = binding.definition();
            boolean allowEdits = authorized && metadata != null && metadata.editBones()
                    && metadata.skeletonHash().equals(skeleton.hash());
            for (int index = 0; index < skeleton.nodes().size(); index++) {
                var node = skeleton.nodes().get(index);
                var reference = skeleton.reference(instance, index);
                boolean mayEdit = allowEdits && editable.contains(skeleton.reference(null, index));
                var target = new Target(instance == null ? Kind.BASE_BONE : Kind.INSTANCE_BONE,
                        instance, reference, null, null, mayEdit, instance == null || exported.contains(reference));
                ids.add(add(node.parent() < 0 ? parent : ids.get(node.parent()), node.name(), target,
                        binding.parts().get(index)));
            }
        }

        private OutfitHierarchy build() {
            var nodes = new ArrayList<PreviewHierarchy.Node>();
            for (int id = 0; id < names.size(); id++) {
                nodes.add(new PreviewHierarchy.Node(id, parents.get(id), names.get(id), children.get(id)));
            }
            return new OutfitHierarchy(new PreviewHierarchy(nodes, roots, parts), targets, activeInstances);
        }
    }
}
