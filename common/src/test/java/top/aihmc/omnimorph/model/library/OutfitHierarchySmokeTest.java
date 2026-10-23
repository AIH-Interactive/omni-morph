package top.aihmc.omnimorph.model.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import top.aihmc.omnimorph.model.outfit.VanillaSkeletonAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class OutfitHierarchySmokeTest {
    private record Part(String name, List<Part> children) {}

    static void run() {
        var limits = OutfitLimits.defaults();
        var base = VanillaSkeletonAdapter.describe(limits);
        var instances = new ArrayList<OutfitConfig.Instance>();
        var entries = new ArrayList<OutfitComposition.EditorInstance>();
        var watermarks = new java.util.HashMap<String, Integer>();
        var shared = resource();
        for (int copy = 1; copy <= 5; copy++) {
            var resource = copy <= 2 ? shared : resource();
            UUID id = UUID.randomUUID();
            Part tip = new Part("tailtip", List.of());
            Part root = new Part("Cosmetic", List.of(tip));
            var skeleton = SkeletonIndex.capture(id, List.of(root), Part::name, Part::children, limits);
            var declaration = new OutfitConfig.Instance(id, resource, copy <= 2 ? copy : 1, copy != 2,
                    OutfitConfig.AnchorRef.authorDefault("hip"), OutfitConfig.Transform.identity(),
                    List.of(), Map.of(), Map.of());
            instances.add(declaration);
            watermarks.put(resource.identity(), declaration.displayOrdinal());
            var metadata = metadata(skeleton.definition(), copy < 5, copy != 4);
            entries.add(new OutfitComposition.EditorInstance(declaration, metadata, skeleton, copy != 3,
                    copy == 1 ? Set.of(skeleton.definition().reference(id, 1)) : Set.of()));
        }
        var anchor = new OutfitConfig.VirtualAnchor(UUID.randomUUID(), "共享位置",
                base.definition().reference(null, base.definition().uniqueNamed("Head").orElseThrow()),
                OutfitConfig.Transform.identity());
        var configuration = new OutfitConfig(1, 0, null, instances, List.of(anchor), watermarks, true);
        var tree = OutfitHierarchy.capture(new OutfitComposition.EditorSnapshot(configuration, base, entries),
                new OutfitHierarchy.Labels("基础", "装饰", "标准", "虚拟", "无骨骼", "隐藏", "停用"));
        require(tree.hierarchy().roots().size() == 4, "Base, standards, instances and virtuals are separate groups");
        require(count(tree, OutfitHierarchy.Kind.STANDARD) == 11 && count(tree, OutfitHierarchy.Kind.INSTANCE) == 5,
                "All eleven standard anchors and every duplicate instance have independent entries");
        require(tree.hierarchy().nodes().stream().anyMatch(node -> node.name().equals("星尾 #1"))
                        && tree.hierarchy().nodes().stream().anyMatch(node -> node.name().equals("星尾 #2 · 停用")),
                "Stable duplicate suffixes and disabled state are displayed");
        var firstTip = selected(tree, instances.get(0).instanceId());
        var secondTip = selected(tree, instances.get(1).instanceId());
        require(firstTip.bone().path().equals(secondTip.bone().path())
                        && !firstTip.bone().instanceId().equals(secondTip.bone().instanceId())
                        && firstTip.editable() && firstTip.mountable() && !secondTip.mountable(),
                "Same named paths retain owning UUIDs; edit and cross-instance export grants remain separate");
        require(!selected(tree, instances.get(2).instanceId()).editable(), "Backend edit denial remains read-only");
        require(tree.targets().values().stream().noneMatch(target -> target.kind() == OutfitHierarchy.Kind.INSTANCE_BONE
                        && (instances.get(3).instanceId().equals(target.instanceId())
                        || instances.get(4).instanceId().equals(target.instanceId()))),
                "Author-hidden and no-bone resources expose status entries instead of internal bones");
        require(tree.targets().values().stream().anyMatch(target -> anchor.id().equals(target.anchorId())
                        && target.kind() == OutfitHierarchy.Kind.VIRTUAL && target.bone() == null),
                "Virtual target identity is not confused with a native bone reference");

        var choices = tree.anchors(instances.get(1).instanceId());
        require(choices.stream().anyMatch(choice -> choice.anchor().type() == OutfitConfig.AnchorType.BASE_BONE)
                        && choices.stream().anyMatch(choice -> firstTip.bone().equals(choice.anchor().bone()))
                        && choices.stream().anyMatch(choice -> anchor.id().equals(choice.anchor().anchorId())),
                "Attachment choices include real base bones, authorized exports and shared virtual anchors");
        require(choices.stream().noneMatch(choice -> secondTip.bone().equals(choice.anchor().bone()))
                        && tree.anchors(instances.getFirst().instanceId()).stream()
                        .noneMatch(choice -> firstTip.bone().equals(choice.anchor().bone())),
                "Unexported and self-owned bones cannot be offered as cross-instance attachment targets");
        var exported = choices.stream().filter(choice -> firstTip.bone().equals(choice.anchor().bone()))
                .findFirst().orElseThrow();
        require(exported.label().contains("星尾 #1") && exported.label().contains("tailtip"),
                "Choice labels show the owning duplicate and bone path");
        var draft = new top.aihmc.omnimorph.model.outfit.OutfitDraft(configuration, limits);
        draft.setAnchor(instances.get(1).instanceId(), exported.anchor());
        var encoded = top.aihmc.omnimorph.model.outfit.OutfitCodec.encode(draft.configuration(), limits);
        require(encoded.contains(firstTip.bone().skeletonHash())
                        && draft.instance(instances.get(1).instanceId()).anchor().bone().equals(firstTip.bone()),
                "Manual attachments retain instance/path/hash references through the configuration codec");

        var state = new BoneTreeState();
        state.replace(tree);
        state.search("tailtip");
        require(state.rows().stream().filter(BoneTreeState.Row::matched)
                        .filter(row -> tree.target(row.node().id()).kind() == OutfitHierarchy.Kind.INSTANCE_BONE).count() == 3,
                "Search includes both duplicate paths and the read-only resource, with hidden bones excluded");
        int id = tree.targets().entrySet().stream().filter(entry -> entry.getValue().equals(firstTip))
                .findFirst().orElseThrow().getKey();
        state.select(id);
        require(state.selectedTarget().equals(firstTip) && state.selectedPart() == entries.getFirst().skeleton().parts().get(1),
                "Typed selection and actual part identity remain paired");
        state.select(tree.hierarchy().roots().getFirst());
        require(state.selectedTarget().kind() == OutfitHierarchy.Kind.BASE && state.selectedPart() == null,
                "Group headers never masquerade as a native part to highlight");
        viewState(tree, configuration, base, entries);
        state.replace(PreviewHierarchy.EMPTY);
        require(state.selectedTarget() == null && state.selectedPart() == null, "Replacing/retiring the tree clears ownership");
        System.out.println("PASS outfit hierarchy: typed base/instance/standard/virtual groups, duplicate paths, "
                + "declared visibility, edit/export policy, search and retirement; GUI/highlight pixels MANUAL/PENDING");
    }

    private static void viewState(OutfitHierarchy tree, OutfitConfig configuration,
                                  SkeletonIndex.Binding<?> base, List<OutfitComposition.EditorInstance> entries) {
        var state = new BoneTreeState();
        state.replace(tree);
        var baseView = new BoneTreeState.View(OutfitHierarchy.Kind.BASE, null);
        var instanceView = new BoneTreeState.View(OutfitHierarchy.Kind.INSTANCE, entries.getFirst().declaration().instanceId());
        var virtualView = new BoneTreeState.View(OutfitHierarchy.Kind.VIRTUAL_GROUP, null);
        var readonlyView = new BoneTreeState.View(OutfitHierarchy.Kind.INSTANCE, entries.get(2).declaration().instanceId());
        require(state.viewChoices().stream().map(BoneTreeState.ViewChoice::view).toList()
                        .equals(List.of(BoneTreeState.View.ALL, baseView,
                                new BoneTreeState.View(OutfitHierarchy.Kind.STANDARD_GROUP, null), instanceView, readonlyView, virtualView)),
                "View choices include only enabled, author-visible instances with actual skeletons");
        state.view(baseView);
        require(state.rows().size() == 1, "Each view starts collapsed at its own title");
        int baseRoot = state.rows().getFirst().node().id();
        state.toggle(baseRoot);
        var expandedBase = state.rows().stream().map(row -> row.node().id()).toList();
        state.search("Head");
        state.scroll(.35);
        state.horizontalScroll(.6);
        state.view(instanceView);
        require(state.query().isEmpty() && state.rows().size() == 1, "Instance views start with independent empty search");
        state.search("tailtip");
        var target = state.rows().stream().filter(BoneTreeState.Row::matched).findFirst().orElseThrow();
        state.select(target.node().id());
        var bone = state.selectedTarget().bone();
        state.view(virtualView);
        state.search("共享");
        state.view(baseView);
        require(state.query().equals("Head") && state.scroll() == .35 && state.horizontalScroll() == .6,
                "Returning to the base restores search and both scroll positions");
        state.search("");
        require(state.rows().stream().map(row -> row.node().id()).toList().equals(expandedBase),
                "Clearing search restores the base view's expansion from before the search");
        state.view(instanceView);
        require(state.query().equals("tailtip") && bone.equals(state.selectedTarget().bone()),
                "Returning to an instance restores its search and exact owned bone");
        var reversed = new ArrayList<>(entries);
        java.util.Collections.reverse(reversed);
        var rebound = OutfitHierarchy.capture(new OutfitComposition.EditorSnapshot(configuration, base, reversed),
                new OutfitHierarchy.Labels("基础", "装饰", "标准", "虚拟", "无骨骼", "隐藏", "停用"));
        state.replace(rebound);
        require(state.view().equals(instanceView) && state.query().equals("tailtip")
                        && bone.equals(state.selectedTarget().bone()) && state.selected() != target.node().id(),
                "Reordered snapshot IDs preserve selection by instance/path/hash instead of old integers");
        var active = new java.util.HashSet<>(rebound.activeInstances());
        active.remove(instanceView.instance());
        state.replace(new OutfitHierarchy(rebound.hierarchy(), rebound.targets(), active));
        require(state.view().equals(BoneTreeState.View.ALL) && state.selectedTarget() == null,
                "Disabling an instance retires its independent view and selected native bone");
        state.view(virtualView);
        require(state.query().equals("共享"), "Other views survive an unrelated instance retirement");
        state.replace(PreviewHierarchy.EMPTY);
        state.replace(tree);
        state.view(baseView);
        require(state.query().isEmpty() && state.rows().size() == 1,
                "Leaving the outfit clears retained views instead of leaking state to another session");
        require(state.locate(OutfitConfig.AnchorRef.authorDefault("head_anchor"))
                        && state.selectedPart() instanceof OutfitConfig.AnchorRef standard
                        && standard.type() == OutfitConfig.AnchorType.STANDARD,
                "Standard target focus stays a tagged anchor instead of a fake native model part");
        var virtual = configuration.virtualAnchors().getFirst();
        var virtualRef = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL, null, null, virtual.id(), null, null);
        require(state.locate(virtualRef) && virtualRef.equals(state.selectedPart())
                        && state.view().equals(virtualView),
                "Virtual target focus uses its stable anchor ID and reveals the matching view");
        var baseRef = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null, virtual.parent(), null, null, null);
        require(state.locate(baseRef) && baseRef.equals(state.selectedPart()) && state.view().equals(baseView),
                "Vanilla descriptors retain a typed bone reference until the native model supplies the pose");
    }

    private static long count(OutfitHierarchy tree, OutfitHierarchy.Kind kind) {
        return tree.targets().values().stream().filter(target -> target.kind() == kind).count();
    }

    private static OutfitHierarchy.Target selected(OutfitHierarchy tree, UUID instance) {
        return tree.targets().values().stream().filter(target -> target.kind() == OutfitHierarchy.Kind.INSTANCE_BONE
                && instance.equals(target.instanceId()) && target.bone().path().getLast().name().equals("tailtip"))
                .findFirst().orElseThrow();
    }

    private static OutfitConfig.ResourceRef resource() {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null, null,
                "a".repeat(64), OutfitConfig.Format.FIGURA);
    }

    private static CosmeticMetadata metadata(SkeletonIndex skeleton, boolean hasBones, boolean display) {
        var json = new JsonObject();
        json.addProperty("name", "星尾");
        json.addProperty("category", "Tail");
        json.addProperty("has_bones", hasBones);
        json.addProperty("display_bones", hasBones && display);
        json.addProperty("edit_bones", hasBones);
        if (hasBones) {
            json.addProperty("skeleton_hash", skeleton.hash());
            var editable = new JsonArray();
            editable.add(new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create().toJsonTree(skeleton.reference(null, 1)));
            json.add("editable_bones", editable);
        }
        return CosmeticMetadata.read(json.toString());
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
