package top.aihmc.omnimorph.model.outfit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Client-thread edits of one committed snapshot. No operation publishes equipment or changes model files. */
public final class OutfitDraft {
    private final OutfitConfig committed;
    private final OutfitLimits limits;
    private OutfitConfig configuration;
    private UUID selectedInstance;
    private long generation;

    public OutfitDraft(OutfitConfig committed, OutfitLimits limits) {
        this.committed = Objects.requireNonNull(committed);
        this.limits = Objects.requireNonNull(limits);
        OutfitCodec.encode(committed, limits);
        configuration = committed;
    }

    public OutfitConfig committed() {
        return committed;
    }

    public OutfitConfig configuration() {
        return configuration;
    }

    public long generation() {
        return generation;
    }

    public boolean dirty() {
        return !configuration.equals(committed);
    }

    public UUID selectedInstance() {
        return selectedInstance;
    }

    public void select(UUID id) {
        if (id != null) {
            instance(id);
        }
        selectedInstance = id;
    }

    public OutfitConfig.Instance instance(UUID id) {
        return configuration.instances().stream().filter(instance -> instance.instanceId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OUTFIT_INSTANCE_MISSING"));
    }

    public void setBase(OutfitConfig.ResourceRef base) {
        accept(copy(base, configuration.instances(), configuration.virtualAnchors(), configuration.displayOrdinals()));
        selectedInstance = null;
    }

    public UUID add(OutfitConfig.ResourceRef resource, OutfitConfig.AnchorRef anchor, OutfitConfig.Transform transform) {
        Objects.requireNonNull(resource);
        var watermarks = new LinkedHashMap<>(configuration.displayOrdinals());
        int ordinal = nextOrdinal(watermarks, resource);
        UUID id = UUID.randomUUID();
        var instances = new ArrayList<>(configuration.instances());
        instances.add(new OutfitConfig.Instance(id, resource, ordinal, true, anchor, transform,
                List.of(), Map.of(), Map.of()));
        accept(copy(configuration.base(), instances, configuration.virtualAnchors(), watermarks));
        selectedInstance = id;
        return id;
    }

    public void setEnabled(UUID id, boolean enabled) {
        edit(id, current -> changed(current, enabled, current.anchor(), current.transform(), current.boneOverrides(),
                current.controls(), current.luaState()));
    }

    public void setAnchor(UUID id, OutfitConfig.AnchorRef anchor) {
        edit(id, current -> changed(current, current.enabled(), anchor, current.transform(), current.boneOverrides(),
                current.controls(), current.luaState()));
    }

    public void setTransform(UUID id, OutfitConfig.Transform transform) {
        edit(id, current -> changed(current, current.enabled(), current.anchor(), transform, current.boneOverrides(),
                current.controls(), current.luaState()));
    }

    /** The caller supplies the resource's authored defaults; this does not reset the whole outfit. */
    public void resetInstance(UUID id, OutfitConfig.AnchorRef anchor, OutfitConfig.Transform transform) {
        edit(id, current -> changed(current, current.enabled(), anchor, transform, List.of(), Map.of(), Map.of()));
    }

    public void setControl(UUID id, String key, Object value) {
        edit(id, current -> {
            var controls = new LinkedHashMap<>(current.controls());
            controls.put(key, value);
            return changed(current, current.enabled(), current.anchor(), current.transform(), current.boneOverrides(),
                    controls, current.luaState());
        });
    }

    public void setControls(UUID id, Map<String, Object> values) {
        edit(id, current -> changed(current, current.enabled(), current.anchor(), current.transform(),
                current.boneOverrides(), Map.copyOf(values), current.luaState()));
    }

    public void validateControls(UUID id, Map<String, Object> values) {
        var current = instance(id);
        var replacement = changed(current, current.enabled(), current.anchor(), current.transform(),
                current.boneOverrides(), Map.copyOf(values), current.luaState());
        var instances = configuration.instances().stream()
                .map(instance -> instance == current ? replacement : instance).toList();
        OutfitCodec.encode(copy(configuration.base(), instances, configuration.virtualAnchors(),
                configuration.displayOrdinals()), limits);
    }

    public void setBoneOverride(UUID id, OutfitConfig.BoneRef bone, OutfitConfig.Transform transform) {
        OutfitConfig.require(id.equals(bone.instanceId()));
        edit(id, current -> {
            var overrides = new ArrayList<>(current.boneOverrides());
            overrides.removeIf(override -> override.bone().equals(bone));
            overrides.add(new OutfitConfig.BoneOverride(bone, transform));
            return changed(current, current.enabled(), current.anchor(), current.transform(), overrides,
                    current.controls(), current.luaState());
        });
    }

    public void clearBoneOverride(UUID id, OutfitConfig.BoneRef bone) {
        OutfitConfig.require(id.equals(bone.instanceId()));
        edit(id, current -> changed(current, current.enabled(), current.anchor(), current.transform(),
                current.boneOverrides().stream().filter(override -> !override.bone().equals(bone)).toList(),
                current.controls(), current.luaState()));
    }

    /** For removal confirmation: includes descendants, without mutating the draft or author metadata. */
    public List<UUID> dependents(UUID id) {
        Objects.requireNonNull(id);
        var affected = new HashSet<UUID>();
        affected.add(id);
        boolean changed;
        do {
            changed = false;
            for (var instance : configuration.instances()) {
                if (!affected.contains(instance.instanceId()) && dependsOn(instance.anchor(), affected)) {
                    changed |= affected.add(instance.instanceId());
                }
            }
        } while (changed);
        return configuration.instances().stream().map(OutfitConfig.Instance::instanceId)
                .filter(affected::contains).filter(instance -> !instance.equals(id)).toList();
    }

    private boolean dependsOn(OutfitConfig.AnchorRef anchor, Set<UUID> affected) {
        return switch (anchor.type()) {
            case ACCESSORY_BONE -> affected.contains(anchor.bone().instanceId());
            case AUTO_TRAIL -> affected.contains(anchor.preferredInstanceId());
            case VIRTUAL -> affected.contains(anchor.anchorId()) || configuration.virtualAnchors().stream()
                    .filter(value -> value.id().equals(anchor.anchorId()))
                    .anyMatch(value -> affected.contains(value.parent().instanceId()));
            default -> false;
        };
    }

    public void remove(UUID id) {
        instance(id);
        var removedAnchors = new HashSet<UUID>();
        var anchors = configuration.virtualAnchors().stream().filter(anchor -> {
            if (id.equals(anchor.parent().instanceId())) {
                removedAnchors.add(anchor.id());
                return false;
            }
            return true;
        }).toList();
        var instances = configuration.instances().stream().filter(instance -> !id.equals(instance.instanceId()))
                .map(instance -> detach(instance, id, removedAnchors)).toList();
        accept(copy(configuration.base(), instances, anchors, configuration.displayOrdinals()));
        if (id.equals(selectedInstance)) {
            selectedInstance = null;
        }
    }

    private static OutfitConfig.Instance detach(OutfitConfig.Instance instance, UUID removed, Set<UUID> anchors) {
        var anchor = instance.anchor();
        if (removed != null && anchor.type() == OutfitConfig.AnchorType.ACCESSORY_BONE && removed.equals(anchor.bone().instanceId())) {
            anchor = OutfitConfig.AnchorRef.authorDefault("missing-instance:" + removed);
        } else if (anchor.type() == OutfitConfig.AnchorType.VIRTUAL && anchors.contains(anchor.anchorId())) {
            anchor = OutfitConfig.AnchorRef.authorDefault("missing-anchor:" + anchor.anchorId());
        } else if (removed != null && anchor.type() == OutfitConfig.AnchorType.AUTO_TRAIL && removed.equals(anchor.preferredInstanceId())) {
            anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null);
        }
        return changed(instance, instance.enabled(), anchor, instance.transform(), instance.boneOverrides(),
                instance.controls(), instance.luaState());
    }

    public UUID addVirtualAnchor(String name, OutfitConfig.BoneRef parent, OutfitConfig.Transform transform) {
        UUID id = UUID.randomUUID();
        var anchors = new ArrayList<>(configuration.virtualAnchors());
        anchors.add(new OutfitConfig.VirtualAnchor(id, name, parent, transform));
        accept(copy(configuration.base(), configuration.instances(), anchors, configuration.displayOrdinals()));
        return id;
    }

    public void updateVirtualAnchor(UUID id, String name, OutfitConfig.BoneRef parent, OutfitConfig.Transform transform) {
        OutfitConfig.require(configuration.virtualAnchors().stream().anyMatch(anchor -> anchor.id().equals(id)));
        var anchors = configuration.virtualAnchors().stream().map(anchor -> anchor.id().equals(id)
                ? new OutfitConfig.VirtualAnchor(id, name, parent, transform) : anchor).toList();
        accept(copy(configuration.base(), configuration.instances(), anchors, configuration.displayOrdinals()));
    }

    public void removeVirtualAnchor(UUID id) {
        OutfitConfig.require(configuration.virtualAnchors().stream().anyMatch(anchor -> anchor.id().equals(id)));
        var anchors = configuration.virtualAnchors().stream().filter(anchor -> !anchor.id().equals(id)).toList();
        var instances = configuration.instances().stream().map(instance -> detach(instance, null, Set.of(id))).toList();
        accept(copy(configuration.base(), instances, anchors, configuration.displayOrdinals()));
    }

    public void cancel() {
        accept(committed);
        selectedInstance = null;
    }

    /** Explicit whole-draft reset selects vanilla and preserves numbering tombstones and equipment visibility. */
    public void reset() {
        accept(copy(null, List.of(), List.of(), configuration.displayOrdinals()));
        selectedInstance = null;
    }

    /** Presets are copied into this outfit, with fresh execution/virtual IDs and rewritten internal references. */
    public Map<UUID, UUID> importPreset(OutfitConfig preset) {
        OutfitCodec.encode(preset, limits);
        var identities = new HashMap<UUID, UUID>();
        preset.instances().forEach(instance -> identities.put(instance.instanceId(), UUID.randomUUID()));
        preset.virtualAnchors().forEach(anchor -> identities.put(anchor.id(), UUID.randomUUID()));
        var watermarks = new LinkedHashMap<>(configuration.displayOrdinals());
        preset.displayOrdinals().forEach((identity, ordinal) -> watermarks.merge(identity, ordinal, Math::max));
        var instances = new ArrayList<OutfitConfig.Instance>();
        for (var instance : preset.instances()) {
            var overrides = instance.boneOverrides().stream().map(override -> new OutfitConfig.BoneOverride(
                    rewrite(override.bone(), identities), override.transform())).toList();
            instances.add(new OutfitConfig.Instance(identities.get(instance.instanceId()), instance.resource(),
                    configuration.displayOrdinals().containsKey(instance.resource().identity())
                            ? nextOrdinal(watermarks, instance.resource()) : instance.displayOrdinal(),
                    instance.enabled(), rewrite(instance.anchor(), identities),
                    instance.transform(), overrides, instance.controls(), instance.luaState()));
        }
        var anchors = preset.virtualAnchors().stream().map(anchor -> new OutfitConfig.VirtualAnchor(
                identities.get(anchor.id()), anchor.name(), rewrite(anchor.parent(), identities), anchor.transform())).toList();
        accept(copy(preset.base(), instances, anchors, watermarks));
        selectedInstance = null;
        return Map.copyOf(identities);
    }

    private static OutfitConfig.AnchorRef rewrite(OutfitConfig.AnchorRef anchor, Map<UUID, UUID> identities) {
        return new OutfitConfig.AnchorRef(anchor.type(), anchor.name(),
                anchor.bone() == null ? null : rewrite(anchor.bone(), identities),
                anchor.anchorId() == null ? null : identities.get(anchor.anchorId()),
                anchor.preferredInstanceId() == null ? null : identities.get(anchor.preferredInstanceId()), anchor.symbol());
    }

    private static OutfitConfig.BoneRef rewrite(OutfitConfig.BoneRef bone, Map<UUID, UUID> identities) {
        return new OutfitConfig.BoneRef(bone.instanceId() == null ? null : identities.get(bone.instanceId()),
                bone.path(), bone.skeletonHash());
    }

    private static int nextOrdinal(Map<String, Integer> watermarks, OutfitConfig.ResourceRef resource) {
        int previous = watermarks.getOrDefault(resource.identity(), 0);
        OutfitConfig.require(previous < Integer.MAX_VALUE);
        watermarks.put(resource.identity(), previous + 1);
        return previous + 1;
    }

    private void edit(UUID id, UnaryOperator<OutfitConfig.Instance> edit) {
        var current = instance(id);
        var replacement = edit.apply(current);
        var instances = configuration.instances().stream().map(instance -> instance == current ? replacement : instance).toList();
        accept(copy(configuration.base(), instances, configuration.virtualAnchors(), configuration.displayOrdinals()));
    }

    private static OutfitConfig.Instance changed(OutfitConfig.Instance current, boolean enabled,
                                                 OutfitConfig.AnchorRef anchor, OutfitConfig.Transform transform,
                                                 List<OutfitConfig.BoneOverride> overrides, Map<String, Object> controls,
                                                 Map<String, Object> luaState) {
        return new OutfitConfig.Instance(current.instanceId(), current.resource(), current.displayOrdinal(), enabled,
                anchor, transform, overrides, controls, luaState);
    }

    private OutfitConfig copy(OutfitConfig.ResourceRef base, List<OutfitConfig.Instance> instances,
                               List<OutfitConfig.VirtualAnchor> anchors, Map<String, Integer> watermarks) {
        return new OutfitConfig(1, committed.revision(), base, instances, anchors, watermarks, committed.showEquipment());
    }

    private void accept(OutfitConfig next) {
        OutfitCodec.encode(next, limits);
        if (!next.equals(configuration)) {
            configuration = next;
            generation++;
        }
    }
}
