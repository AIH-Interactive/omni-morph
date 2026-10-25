package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Public edit operations and immutable snapshots, without a game client or equipment publication. */
public final class OutfitDraftSmokeTest {
    public static void main(String[] args) {
        editsAndCancellation();
        dependenciesAndVirtuals();
        presetCopies();
        rejectedEditsAreAtomic();
        System.out.println("PASS outfit draft: duplicate ordinals, immutable committed state, selection, edits/cancel/reset, "
                + "dependent removal, virtual anchors, preset identity rewrite and atomic limit failures; UI wiring separate");
    }

    private static void editsAndCancellation() {
        var resource = resource();
        var seed = new OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
        UUID first = seed.add(resource, anchor(), OutfitConfig.Transform.identity());
        UUID second = seed.add(resource, anchor(), OutfitConfig.Transform.identity());
        var committed = new OutfitConfig(1, 7, resource(), seed.configuration().instances(), List.of(),
                seed.configuration().displayOrdinals(), true);
        var draft = new OutfitDraft(committed, OutfitLimits.defaults());
        require(!draft.dirty() && draft.generation() == 0, "opening a draft preserves its committed snapshot");
        draft.select(first);
        require(draft.generation() == 0, "selection does not invalidate prepared geometry");
        draft.setBase(null);
        require(draft.selectedInstance() == null && draft.configuration().base() == null, "vanilla base selection is explicit");
        draft.setEnabled(second, false);
        draft.setTransform(second, offset(3));
        draft.setControl(second, "wing", true);
        require(draft.instance(second).resource().equals(resource) && draft.instance(second).displayOrdinal() == 2
                        && draft.instance(second).controls().equals(Map.of("wing", true)),
                "per-instance edits preserve the resource, immutable identity and display suffix");
        long generation = draft.generation();
        draft.setEnabled(second, false);
        require(draft.generation() == generation, "a no-op edit does not request another preview preparation");
        draft.select(first);
        draft.remove(first);
        require(draft.selectedInstance() == null && draft.instance(second).displayOrdinal() == 2,
                "removal clears only the deleted selection and does not renumber its sibling");
        require(draft.findInstance(first) == null,
                "stale UI callbacks can query a removed instance without throwing");
        UUID third = draft.add(resource, anchor(), OutfitConfig.Transform.identity());
        require(draft.instance(third).displayOrdinal() == 3, "new duplicates advance the resource watermark");
        draft.remove(third);
        UUID fourth = draft.add(resource, anchor(), OutfitConfig.Transform.identity());
        require(draft.instance(fourth).displayOrdinal() == 4, "deleted suffixes are not reused within this draft");
        require(committed.instances().size() == 2 && committed.instances().get(1).enabled()
                        && committed.instances().get(1).controls().isEmpty() && committed.base() != null,
                "edits never mutate the committed equipment configuration");
        draft.cancel();
        require(!draft.dirty() && draft.configuration().equals(committed) && draft.selectedInstance() == null,
                "cancel restores the entire committed snapshot and clears transient selection");
        draft.reset();
        require(draft.configuration().instances().isEmpty() && draft.configuration().base() == null
                        && draft.configuration().showEquipment() && draft.configuration().revision() == 7,
                "whole-draft reset does not silently alter equipment visibility or its CAS revision");
        UUID afterReset = draft.add(resource, anchor(), OutfitConfig.Transform.identity());
        require(draft.instance(afterReset).displayOrdinal() == 3, "reset retains committed numbering tombstones");
    }

    private static void dependenciesAndVirtuals() {
        var draft = new OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
        UUID parent = draft.add(resource(), anchor(), OutfitConfig.Transform.identity());
        UUID child = draft.add(resource(), anchor(), OutfitConfig.Transform.identity());
        UUID descendant = draft.add(resource(), anchor(), OutfitConfig.Transform.identity());
        UUID viaVirtual = draft.add(resource(), anchor(), OutfitConfig.Transform.identity());
        UUID automatic = draft.add(resource(), anchor(), OutfitConfig.Transform.identity());
        draft.setAnchor(child, accessory(parent));
        draft.setAnchor(descendant, accessory(child));
        UUID virtual = draft.addVirtualAnchor("尾端", bone(parent), offset(1));
        draft.setAnchor(viaVirtual, virtual(virtual));
        draft.setAnchor(automatic, new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL,
                null, null, null, parent, null));
        require(Set.copyOf(draft.dependents(parent)).equals(Set.of(child, descendant, viaVirtual, automatic)),
                "confirmation can enumerate the direct and transitive affected instances");
        draft.select(parent);
        draft.remove(parent);
        require(draft.selectedInstance() == null && draft.configuration().virtualAnchors().isEmpty(),
                "removing a parent clears its selection and virtual definitions");
        require(draft.instance(child).anchor().type() == OutfitConfig.AnchorType.UNRESOLVED
                        && draft.instance(viaVirtual).anchor().type() == OutfitConfig.AnchorType.UNRESOLVED
                        && draft.instance(descendant).anchor().bone().instanceId().equals(child),
                "manual dependents retain their instances and become unresolved rather than attaching elsewhere");
        require(draft.instance(automatic).anchor().type() == OutfitConfig.AnchorType.AUTO_TRAIL
                        && draft.instance(automatic).anchor().preferredInstanceId() == null,
                "only automatic mode can resume its defined fallback selection");
        UUID baseVirtual = draft.addVirtualAnchor("共享挂点", bone(null), offset(2));
        draft.setAnchor(child, virtual(baseVirtual));
        draft.updateVirtualAnchor(baseVirtual, "重命名", bone(null), offset(4));
        require(draft.instance(child).anchor().anchorId().equals(baseVirtual), "renaming does not break virtual references");
        var beforeCycle = draft.configuration();
        reject(() -> draft.updateVirtualAnchor(baseVirtual, "cycle", bone(child), offset(1)));
        require(draft.configuration() == beforeCycle, "a virtual self-cycle cannot partially replace the current anchor");
        draft.removeVirtualAnchor(baseVirtual);
        require(draft.instance(child).anchor().type() == OutfitConfig.AnchorType.UNRESOLVED
                        && draft.instance(descendant).anchor().type() == OutfitConfig.AnchorType.ACCESSORY_BONE,
                "virtual deletion changes just its bindings and preserves unrelated accessory bone references");
        draft.setControl(child, "show", false);
        draft.setBoneOverride(child, bone(child), offset(5));
        reject(() -> draft.setBoneOverride(child, bone(null), offset(1)));
        draft.resetInstance(child, anchor(), offset(2));
        require(draft.instance(child).anchor().equals(anchor()) && draft.instance(child).transform().equals(offset(2))
                        && draft.instance(child).boneOverrides().isEmpty() && draft.instance(child).controls().isEmpty(),
                "instance reset restores the supplied author defaults without resetting the whole draft");
    }

    private static void presetCopies() {
        var shared = resource();
        var preset = new OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
        UUID parent = preset.add(shared, anchor(), offset(1));
        UUID child = preset.add(shared, accessory(parent), offset(2));
        UUID virtual = preset.addVirtualAnchor("socket", bone(parent), offset(3));
        UUID third = preset.add(resource(), virtual(virtual), offset(4));
        UUID automatic = preset.add(resource(), new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL,
                null, null, null, parent, null), offset(5));
        preset.setBoneOverride(child, bone(child), offset(6));
        preset.setControl(child, "opacity", .5);
        var destination = new OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
        destination.add(shared, anchor(), OutfitConfig.Transform.identity());
        var ids = destination.importPreset(preset.configuration());
        require(ids.size() == 5 && ids.entrySet().stream().noneMatch(entry -> entry.getKey().equals(entry.getValue())),
                "all instance and virtual IDs are fresh when a preset is copied");
        require(destination.instance(ids.get(child)).anchor().bone().instanceId().equals(ids.get(parent))
                        && destination.instance(ids.get(child)).boneOverrides().getFirst().bone().instanceId().equals(ids.get(child))
                        && destination.instance(ids.get(third)).anchor().anchorId().equals(ids.get(virtual))
                        && destination.instance(ids.get(automatic)).anchor().preferredInstanceId().equals(ids.get(parent))
                        && destination.configuration().virtualAnchors().getFirst().parent().instanceId().equals(ids.get(parent)),
                "copy rewrites every supported internal reference, including overrides and automatic preferences");
        require(destination.instance(ids.get(parent)).displayOrdinal() == 3
                        && destination.instance(ids.get(child)).displayOrdinal() == 4
                        && destination.instance(ids.get(third)).displayOrdinal() == 1,
                "conflicting resource suffixes advance while a newly imported resource retains its suffix");
        require(destination.instance(ids.get(child)).controls().equals(Map.of("opacity", .5))
                        && preset.instance(child).displayOrdinal() == 2
                        && preset.instance(child).anchor().bone().instanceId().equals(parent),
                "copy preserves authored scalar state and never edits the preset itself");
    }

    private static void rejectedEditsAreAtomic() {
        var json = new Gson().toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxInstances", 1);
        json.addProperty("maxControls", 0);
        var draft = new OutfitDraft(OutfitConfig.empty(), new Gson().fromJson(json, OutfitLimits.class));
        UUID selected = draft.add(resource(), anchor(), offset(1));
        var before = draft.configuration();
        long generation = draft.generation();
        reject(() -> draft.add(resource(), anchor(), offset(1)));
        reject(() -> draft.setControl(selected, "blocked", true));
        reject(() -> draft.setAnchor(selected, accessory(selected)));
        require(draft.configuration() == before && draft.generation() == generation && draft.selectedInstance().equals(selected),
                "failed mutations preserve the snapshot, preview generation, suffix watermark and selection");
        var oversized = new OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
        oversized.add(resource(), anchor(), offset(1));
        oversized.add(resource(), anchor(), offset(1));
        reject(() -> draft.importPreset(oversized.configuration()));
        require(draft.configuration() == before, "preset limit failures are atomic too");
    }

    private static OutfitConfig.ResourceRef resource() {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "a".repeat(64), OutfitConfig.Format.FIGURA);
    }

    private static OutfitConfig.AnchorRef anchor() {
        return OutfitConfig.AnchorRef.authorDefault("back_anchor");
    }

    private static OutfitConfig.AnchorRef accessory(UUID id) {
        return new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null, bone(id), null, null, null);
    }

    private static OutfitConfig.AnchorRef virtual(UUID id) {
        return new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL, null, null, id, null, null);
    }

    private static OutfitConfig.BoneRef bone(UUID id) {
        return new OutfitConfig.BoneRef(id, List.of(new OutfitConfig.BoneSegment("empty socket", 0)), "b".repeat(64));
    }

    private static OutfitConfig.Transform offset(double x) {
        return new OutfitConfig.Transform(new OutfitConfig.Vector(x, 0, 0), new OutfitConfig.Vector(0, 0, 0),
                new OutfitConfig.Vector(1, 1, 1));
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected draft mutation rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
