package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.library.PreviewBoneHighlight;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.ysm.YsmRenderPose;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.permissions.Permissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One immutable draft generation. Ownership transfers only after successful construction. */
public final class OutfitComposition implements AutoCloseable {
    public record EditorInstance(OutfitConfig.Instance declaration, CosmeticMetadata metadata,
                                 SkeletonIndex.Binding<?> skeleton, boolean mayEdit,
                                 Set<OutfitConfig.BoneRef> exported) {
        public EditorInstance {
            exported = Set.copyOf(exported);
        }
    }

    public record EditorSnapshot(OutfitConfig configuration, SkeletonIndex.Binding<?> base,
                                 List<EditorInstance> instances) {
        public EditorSnapshot {
            instances = List.copyOf(instances);
        }
    }

    public record Draw(UUID instanceId, Avatar avatar, Matrix4f matrix, YsmRenderPose ysmPose,
                       FiguraRenderPose figuraPose) {
        public Draw {
            matrix = new Matrix4f(matrix);
        }

        @Override
        public Matrix4f matrix() {
            return new Matrix4f(matrix);
        }
    }

    private final OutfitConfig configuration;

    /** The server assigns the revision; all other prepared fields must remain identical. */
    public boolean matchesConfiguration(OutfitConfig committed, UUID wearerId) {
        if (closed || committed == null || wearer == null || !wearer.getUUID().equals(wearerId)) {
            return false;
        }
        return configuration.equals(new OutfitConfig(committed.schemaVersion(), configuration.revision(),
                committed.base(), committed.instances(), committed.virtualAnchors(), committed.displayOrdinals(),
                committed.showEquipment()));
    }

    private final OutfitLimits limits;

    public OutfitLimits limits() {
        return limits;
    }

    public OutfitConfig configuration() {
        return configuration;
    }

    public Map<UUID, Avatar> runtimeInstances() {
        if (closed) {
            return Map.of();
        }
        var active = new LinkedHashMap<UUID, Avatar>();
        for (var instance : configuration.instances()) {
            Avatar avatar = instances.get(instance.instanceId());
            if (instance.enabled() && avatar != null && !avatar.scriptError) {
                active.put(instance.instanceId(), avatar);
            }
        }
        return Map.copyOf(active);
    }

    private final LivingEntity wearer;
    private final Map<UUID, Avatar> instances;
    public record ControlGroup(OutfitConfig.Instance instance, Avatar avatar) {}
    private final Map<UUID, Long> controlDefinitions = new HashMap<>();
    private List<ControlGroup> controlGroups = List.of();
    private long controlGroupsRevision;

    /** Compare registration versions only; do not scan every control on every GUI tick. */
    public List<ControlGroup> controlGroups() {
        if (closed) {
            return List.of();
        }
        boolean changed = controlDefinitions.size() != instances.size();
        for (var entry : instances.entrySet()) {
            long revision = entry.getValue().controls.definitionRevision();
            if (!java.util.Objects.equals(controlDefinitions.put(entry.getKey(), revision), revision)) {
                changed = true;
            }
        }
        if (changed) {
            var groups = new ArrayList<ControlGroup>();
            for (var instance : configuration.instances()) {
                Avatar avatar = instances.get(instance.instanceId());
                if (instance.enabled() && avatar != null && !avatar.controls.isEmpty()) {
                    groups.add(new ControlGroup(instance, avatar));
                }
            }
            controlGroups = List.copyOf(groups);
            controlGroupsRevision++;
        }
        return controlGroups;
    }

    public long controlGroupsRevision() {
        controlGroups();
        return controlGroupsRevision;
    }
    private final Set<UUID> initialized = new HashSet<>();
    private final Avatar controllerOwner;
    private final FiguraLuaRuntime controllerRuntime;
    private final LuaTable controller;
    private final NativeBridge bridge = new NativeBridge();
    private final int instanceNodes;
    private final SkeletonIndex.Cache indexes;
    private final Map<UUID, Set<OutfitConfig.BoneRef>> exportedBones = new HashMap<>();
    private final Map<UUID, CosmeticBoneOverrides> boneOverrides = new HashMap<>();
    private final Map<UUID, String> categories = new HashMap<>();
    private final Map<UUID, CosmeticMetadata> editorMetadata = new HashMap<>();
    private final Map<Object, OutfitConfig.BoneRef> previewBones = new IdentityHashMap<>();
    private final Map<OutfitConfig.BoneRef, Object> previewParts = new HashMap<>();
    private final Map<OutfitConfig.AnchorRef, BoneHandle> previewAnchors = new HashMap<>();
    /** GUI diagnostic from the trusted selector; a null bone denotes its explicit entity-root fallback. */
    public record MountTarget(OutfitConfig.BoneRef bone, OutfitConfig.Transform offset) {}
    private final Map<UUID, MountTarget> previewMountTargets = new HashMap<>();
    private long previewMountSequence;

    public MountTarget previewMountTarget(UUID instance) {
        return closed ? null : previewMountTargets.get(instance);
    }

    public boolean hasPreviewFrame() {
        return !closed && previewMountSequence > 0;
    }
    private final Set<UUID> editableInstances = new HashSet<>();
    private final Map<UUID, UUID> automaticTailSelections = new HashMap<>();
    private final Set<String> diagnosed = new HashSet<>();
    private final Map<UUID, TrailBuffer> trails = new HashMap<>();
    private long frameSequence;

    private int retainedFrames;
    private boolean closed;
    private OutfitActionWheel actionWheel;

    public OutfitActionWheel actionWheel() {
        OutfitConfig.require(!closed);
        if (actionWheel == null) {
            actionWheel = new OutfitActionWheel(this::animationEntries, () -> !closed);
        }
        return actionWheel;
    }

    private List<OutfitActionWheel.Entry> animationEntries() {
        var entries = new ArrayList<OutfitActionWheel.Entry>();
        for (var instance : configuration.instances()) {
            Avatar avatar = instances.get(instance.instanceId());
            if (instance.enabled() && avatar != null) {
                var metadata = editorMetadata.get(instance.instanceId());
                String name = metadata == null ? instance.resource().format().name() : metadata.common().name;
                if (name == null || name.isBlank()) {
                    name = instance.resource().format().name();
                }
                entries.add(new OutfitActionWheel.Entry(instance, name, avatar));
            }
        }
        return List.copyOf(entries);
    }
    private boolean released;
    private Frame activeFrame;
    private top.aihmc.omnimorph.lua.api.particle.PreviewParticleScope previewParticles;
    private final MolangExecutionBudget vanillaMolangBudget = new MolangExecutionBudget();
    private Avatar budgetBase;
    private final OutfitInstructionBudget instructionBudget = new OutfitInstructionBudget(() -> budgetBase);
    private final OutfitEffectBudget effectBudget;

    public List<TrailBuffer.Sample> trailSamples(UUID instanceId) {
        TrailBuffer buffer = trails.get(instanceId);
        return buffer == null ? List.of() : buffer.snapshot();
    }

    public void bindPreviewParticles(top.aihmc.omnimorph.lua.api.particle.PreviewParticleScope particles) {
        OutfitConfig.require(!closed && (previewParticles == null || previewParticles == particles));
        particles.bindAll(instances.values());
        particles.useLimits(limits);
        previewParticles = particles;
    }

    private static void clearTrail(TrailBuffer trail) {
        trail.clear();
        OutfitTrailBudget.release(trail);
    }

    private static void invalidateTrail(TrailBuffer trail, long sequence) {
        trail.invalidate(sequence);
        if (trail.isEmpty()) {
            OutfitTrailBudget.release(trail);
        }
    }

    /** Diagnostic/UI state reported by the trusted Lua selector; explicit preferences live in AnchorRef. */
    public Map<UUID, UUID> automaticTailSelections() {
        return Map.copyOf(automaticTailSelections);
    }

    private record TrailTarget(OutfitConfig.BoneRef bone, OutfitConfig.Transform offset) {}

    private MolangExecutionBudget molangBudget() {
        return budgetBase == null || budgetBase.getMolangContext() == null
                ? vanillaMolangBudget : budgetBase.getMolangContext().executionBudget();
    }

    private void bindBaseBudget(Avatar base) {
        OutfitConfig.require(base != null && base.cosmeticContext == null
                && (budgetBase == null || budgetBase == base));
        budgetBase = base;
    }

    public OutfitComposition(OutfitConfig configuration, OutfitLimits limits, LivingEntity wearer,
                              Map<UUID, Avatar> instances, SkeletonIndex.Cache indexes) throws IOException {
        this(configuration, limits, wearer, instances, indexes,
                resource -> resource.source() == OutfitConfig.Source.LOCAL);
    }

    /** Cloud callers must supply the authorized resource policy; author declarations alone cannot grant access. */
    public OutfitComposition(OutfitConfig configuration, OutfitLimits limits, LivingEntity wearer,
                              Map<UUID, Avatar> instances, SkeletonIndex.Cache indexes,
                              Predicate<OutfitConfig.ResourceRef> mayUseExportedBones) throws IOException {
        this(configuration, limits, wearer, instances, indexes, mayUseExportedBones,
                resource -> resource.source() == OutfitConfig.Source.LOCAL);
    }

    public OutfitComposition(OutfitConfig configuration, OutfitLimits limits, LivingEntity wearer,
                              Map<UUID, Avatar> instances, SkeletonIndex.Cache indexes,
                              Predicate<OutfitConfig.ResourceRef> mayUseExportedBones,
                              Predicate<OutfitConfig.ResourceRef> mayEditBones) throws IOException {
        configuration.validateLimits(limits);
        this.configuration = configuration;
        this.limits = limits;
        this.wearer = wearer;
        this.indexes = indexes;
        this.instances = new LinkedHashMap<>(instances);
        Set<Avatar> owners = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<UUID> executionIds = new HashSet<>();
        int nodes = 0;
        for (var declaration : configuration.instances()) {
            Avatar avatar = instances.get(declaration.instanceId());
            OutfitConfig.require(avatar != null && avatar.loaded && avatar.nbt != null
                    && avatar.cosmeticContext != null && owners.add(avatar) && executionIds.add(avatar.owner)
                    && declaration.instanceId().equals(avatar.cosmeticContext.instanceId())
                    && declaration.resource().equals(avatar.cosmeticContext.resource())
                    && (wearer == null || wearer.getUUID().equals(avatar.cosmeticContext.wearerId())));
            SkeletonIndex index = avatar.isYsmNative()
                    ? new YsmSkeletonAdapter(avatar.getYsmRuntime(), declaration.resource(), declaration.instanceId(),
                            indexes, limits).binding().definition()
                    : new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer, declaration.resource(),
                            declaration.instanceId(), indexes, limits).binding().definition();
            nodes += index.nodes().size();
            OutfitConfig.require(nodes <= limits.maxTotalNodes());
            String manifest = avatar.nbt.getCompoundOrEmpty("metadata").getStringOr("cosmetic_json", "");
            var metadata = manifest.isEmpty() ? null : CosmeticMetadata.read(manifest);
            if (metadata != null) {
                editorMetadata.put(declaration.instanceId(), metadata);
            }
            boolean boneEditAllowed = mayEditBones.test(declaration.resource());
            if (boneEditAllowed) {
                editableInstances.add(declaration.instanceId());
            }
            categories.put(declaration.instanceId(), metadata == null ? "Unknown" : metadata.category());
            if (declaration.enabled() && metadata != null && metadata.category().equals("Trail")) {
                OutfitConfig.require(trails.size() < limits.maxTrails());
                trails.put(declaration.instanceId(), new TrailBuffer(limits));
            }
            boneOverrides.put(declaration.instanceId(), CosmeticBoneOverrides.bind(avatar, declaration, metadata,
                    boneEditAllowed, indexes, limits));
            if (metadata != null) {
                OutfitConfig.require(metadata.exportedBones().size() <= limits.maxExportedBones());
                for (var bone : metadata.exportedBones()) {
                    OutfitConfig.require(bone.path().size() <= limits.maxDepth());
                }
                if (metadata.hasBones() && index.hash().equals(metadata.skeletonHash())
                        && mayUseExportedBones.test(declaration.resource())) {
                    var exports = new HashSet<OutfitConfig.BoneRef>();
                    for (var bone : metadata.exportedBones()) {
                        if (index.resolve(bone).isPresent()) {
                            exports.add(new OutfitConfig.BoneRef(declaration.instanceId(), bone.path(), bone.skeletonHash()));
                        }
                    }
                    exportedBones.put(declaration.instanceId(), Set.copyOf(exports));
                }
            }
        }
        OutfitConfig.require(owners.size() == instances.size());
        instanceNodes = nodes;
        instructionBudget.bindInstances(this.instances.values());
        effectBudget = this.instances.isEmpty() ? null
                : new OutfitEffectBudget(() -> budgetBase, this.instances.values().iterator().next());
        for (Avatar avatar : this.instances.values()) {
            avatar.cosmeticContext.bindMolangBudget(this::molangBudget);
            avatar.cosmeticContext.bindInstructionBudget(instructionBudget);
            avatar.cosmeticContext.bindEffectBudget(effectBudget);
            TrailBuffer trail = trails.get(avatar.cosmeticContext.instanceId());
            if (trail != null) {
                avatar.cosmeticContext.bindTrailSource(partialTick -> {
                    if (closed || wearer == null || wearer.isRemoved() || wearer.level() == null) {
                        return null;
                    }
                    return trail.snapshotAt(wearer.level(), wearer.level().getGameTime() + (double) partialTick);
                });
            }
        }
        controllerOwner = Avatar.createLibraryPreview(UUID.randomUUID(), "OutfitController");
        controllerRuntime = new FiguraLuaRuntime(controllerOwner, Map.of());
        controllerRuntime.registerClass(NativeBridge.class);
        controllerRuntime.registerClass(BoneHandle.class);
        controllerRuntime.setGlobal("cosmetic_native", bridge);
        try (var source = OutfitComposition.class.getResourceAsStream(
                "/assets/omni-morph/scripts/cosmetic_controller.lua")) {
            if (source == null || !controllerRuntime.init(new ListTag())) {
                throw new IOException("COSMETIC_CONTROLLER_MISSING");
            }
            Varargs result = controllerRuntime.run(controllerRuntime.load("cosmetic_controller",
                    new String(source.readAllBytes(), StandardCharsets.UTF_8)), controllerOwner.init);
            if (result == null || !result.arg1().istable() || controllerOwner.scriptError) {
                throw new IOException("COSMETIC_CONTROLLER_FAILED");
            }
            controller = result.arg1().checktable();
        } catch (IOException | RuntimeException failure) {
            controllerOwner.clean();
            throw failure;
        }
    }

    /** Formal preparation only: validate author capabilities against loaded geometry before committing Apply. */
    public void validatePreparedBase(Avatar base) {
        OutfitConfig.require(!closed);
        if (configuration.base() == null) {
            OutfitConfig.require(base == null);
        } else {
            OutfitConfig.require(base != null && base.loaded && base.nbt != null && !base.scriptError
                    && base.cosmeticContext == null && base.accelerationOwner().isCurrent()
                    && base.nbt.getCompoundOrEmpty("metadata").getStringOr("resource_kind", "AVATAR").equals("AVATAR")
                    && configuration.base().equals(base.outfitSource())
                    && top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.isEncrypted(base.nbt.getCompoundOrEmpty("ysm"))
                    == (configuration.base().format() == OutfitConfig.Format.YSM_ENCRYPTED)
                    && base.isYsmNative() == (configuration.base().format() != OutfitConfig.Format.FIGURA));
        }
        if (base == null && instances.isEmpty() && configuration.virtualAnchors().isEmpty()) {
            return;
        }
        var baseSkeleton = base == null ? VanillaSkeletonAdapter.describe(limits)
                : editorSkeleton(base, configuration.base(), null);
        OutfitConfig.require(instanceNodes + baseSkeleton.definition().nodes().size() <= limits.maxTotalNodes());
        String family = base == null ? "VANILLA" : base.isYsmNative() ? "YSM" : "FIGURA";
        for (var declaration : configuration.instances()) {
            CosmeticMetadata metadata = editorMetadata.get(declaration.instanceId());
            if (metadata == null) {
                throw new IllegalArgumentException("COSMETIC_METADATA_REQUIRED");
            }
            metadata.validateLimits(limits);
            if (!metadata.supportedBases().contains(family)) {
                throw new IllegalArgumentException("COSMETIC_BASE_UNSUPPORTED");
            }
            if (!metadata.hasBones()) {
                continue;
            }
            var skeleton = editorSkeleton(instances.get(declaration.instanceId()), declaration.resource(),
                    declaration.instanceId()).definition();
            if (!skeleton.hash().equals(metadata.skeletonHash())) {
                throw new IllegalArgumentException("COSMETIC_DECLARATION_STALE");
            }
            for (var range : List.of(metadata.exportedBones(), metadata.editableBones())) {
                for (var bone : range) {
                    if (skeleton.resolve(bone).isEmpty()) {
                        throw new IllegalArgumentException("COSMETIC_DECLARATION_STALE");
                    }
                }
            }
        }
        // Missing manual targets remain paused by the controller. Preparation must not invent a fallback.
    }

    /** Called only when publishing/explicitly refreshing a GUI snapshot; no source parsing or IO. */
    public EditorSnapshot editorSnapshot(Avatar base) {
        OutfitConfig.require(!closed);
        SkeletonIndex.Binding<?> baseSkeleton = base == null ? VanillaSkeletonAdapter.describe(limits)
                : editorSkeleton(base, configuration.base(), null);
        previewBones.clear();
        previewParts.clear();
        previewAnchors.clear();
        registerPreviewBones(baseSkeleton, null);
        for (String name : OutfitConfig.STANDARD_ANCHORS) {
            var targets = StandardAnchors.resolve(baseSkeleton.definition(), configuration.base() == null, name);
            if (targets.size() == 1) {
                var target = targets.getFirst();
                previewAnchors.put(OutfitConfig.AnchorRef.authorDefault(name), new BoneHandle(target.bone(), target.offset()));
            }
        }
        var entries = new ArrayList<EditorInstance>();
        for (var declaration : configuration.instances()) {
            UUID id = declaration.instanceId();
            var skeleton = editorSkeleton(instances.get(id), declaration.resource(), id);
            var metadata = editorMetadata.get(id);
            if (declaration.enabled() && metadata != null && metadata.hasBones() && metadata.displayBones()) {
                registerPreviewBones(skeleton, id);
            }
            entries.add(new EditorInstance(declaration, editorMetadata.get(id),
                    skeleton, editableInstances.contains(id),
                    exportedBones.getOrDefault(id, Set.of())));
        }
        for (var anchor : configuration.virtualAnchors()) {
            boolean allowed = anchor.parent().instanceId() == null
                    ? baseSkeleton.definition().resolve(anchor.parent()).isPresent()
                    : exportedBones.getOrDefault(anchor.parent().instanceId(), Set.of()).contains(anchor.parent())
                            && previewParts.containsKey(anchor.parent());
            if (allowed) {
                previewAnchors.put(new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL, null, null,
                        anchor.id(), null, null), new BoneHandle(anchor.parent(), anchor.transform()));
            }
        }
        return new EditorSnapshot(configuration, baseSkeleton, entries);
    }

    private void registerPreviewBones(SkeletonIndex.Binding<?> binding, UUID instance) {
        for (int index = 0; index < binding.parts().size(); index++) {
            var bone = binding.definition().reference(instance, index);
            previewBones.put(binding.parts().get(index), bone);
            previewParts.put(bone, binding.parts().get(index));
            var type = instance == null ? OutfitConfig.AnchorType.BASE_BONE : OutfitConfig.AnchorType.ACCESSORY_BONE;
            if (instance == null || exportedBones.getOrDefault(instance, Set.of()).contains(bone)) {
                previewAnchors.put(new OutfitConfig.AnchorRef(type, null, bone, null, null, null), new BoneHandle(bone));
            }
        }
    }

    private SkeletonIndex.Binding<?> editorSkeleton(Avatar avatar, OutfitConfig.ResourceRef resource, UUID instance) {
        return avatar.isYsmNative()
                ? new YsmSkeletonAdapter(avatar.getYsmRuntime(), resource, instance, indexes, limits).binding()
                : new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer, resource, instance, indexes, limits).binding();
    }

    public void tick() {
        tick(null);
    }

    public void tick(Avatar base) {
        if (!closed) {
            for (TrailBuffer trail : trails.values()) {
                if (wearer == null || wearer.level() == null || wearer.isRemoved()) {
                    clearTrail(trail);
                } else {
                    trail.advance(wearer.level(), wearer.level().getGameTime());
                    if (trail.isEmpty()) {
                        OutfitTrailBudget.release(trail);
                    }
                }
            }
            instructionBudget.beginTick();
            if (effectBudget != null) {
                effectBudget.tick();
            }
            if (base == null) {
                runController("tick", 0);
            } else {
                withBaseBudget(base, () -> {
                    base.tick();
                    runController("tick", 0);
                });
            }
        }
    }

    /** The existing preview owns base ticking/preparation; scope its work with the same outfit quota. */
    public void withBaseBudget(Avatar base, Runnable preparation) {
        bindBaseBudget(base);
        try (var instructions = instructionBudget.enter();
             var budget = molangBudget().beginFrame(YsmRenderFrameClock.currentFrame())) {
            preparation.run();
        }
    }

    /** Retain at render extraction; release only when the native deferred frame has finished. */
    public Frame retain() {
        return retain(wearer);
    }

    Frame retain(LivingEntity viewWearer) {
        if (closed) {
            throw new IllegalStateException("OUTFIT_COMPOSITION_CLOSED");
        }
        retainedFrames++;
        return new Frame(viewWearer);
    }

    /** Global events share the same quotas and only reach instances accepted by the trusted mount selector. */
    public void executeAll(Avatar base, Consumer<Avatar> consumer) {
        if (closed) {
            return;
        }
        if (base != null) {
            bindBaseBudget(base);
        }
        try (var instructions = instructionBudget.enter();
             var budget = molangBudget().beginFrame(YsmRenderFrameClock.currentFrame())) {
            var eligible = runController("activeInstances", 0).arg1().checktable();
            var active = new ArrayList<Avatar>();
            for (int index = 1; index <= eligible.length(); index++) {
                UUID id = UUID.fromString(eligible.get(index).checkjstring());
                Avatar avatar = instances.get(id);
                if (avatar != null && initialized.contains(id)) {
                    active.add(avatar);
                }
            }
            if (base != null) {
                consumer.accept(base);
            }
            for (Avatar avatar : active) {
                if (closed) {
                    break;
                }
                if (avatar.accelerationOwner().isCurrent() && !avatar.scriptError) {
                    consumer.accept(avatar);
                }
            }
        }
    }

    private Varargs runController(String method, float delta) {
        controllerOwner.tick.reset(controllerOwner.permissions.get(Permissions.TICK_INST));
        Varargs result = controllerRuntime.run(controller.get(method), controllerOwner.tick, delta);
        if (result == null || controllerOwner.scriptError) {
            throw new IllegalStateException("COSMETIC_CONTROLLER_FAILED");
        }
        return result;
    }

    @Override
    public void close() {
        closed = true;
        if (actionWheel != null) {
            actionWheel.close();
        }
        if (previewParticles != null) {
            instances.values().forEach(previewParticles::retireOwner);
        }
        trails.values().forEach(OutfitComposition::clearTrail);
        releaseIfRetired();
    }

    private void releaseIfRetired() {
        if (!closed || retainedFrames != 0 || released) {
            return;
        }
        released = true;
        controllerOwner.clean();
        for (Avatar avatar : instances.values()) {
            avatar.clean();
        }
        instances.clear();
        initialized.clear();
        exportedBones.clear();
        boneOverrides.clear();
        categories.clear();
        previewBones.clear();
        previewParts.clear();
        previewAnchors.clear();
        previewMountTargets.clear();
        automaticTailSelections.clear();
        trails.clear();
    }

    public final class Frame implements AutoCloseable {
        private final long sequence = ++frameSequence;
        private final LivingEntity viewWearer;
        private final Object world;
        private final double worldTick;
        private final Vec3 previousPosition;
        private final Vec3 currentPosition;

        private float delta;
        private SkeletonPose basePose;
        private LivingEntityRenderState renderState;
        private final Map<UUID, YsmRenderPose> prepared = new HashMap<>();
        private final Map<UUID, FiguraRenderPose> figuraPoses = new HashMap<>();
        private final List<Draw> commands = new ArrayList<>();
        private final Set<UUID> emitted = new HashSet<>();
        private final Map<UUID, SkeletonPose> instancePoses = new HashMap<>();
        private boolean evaluated;
        private boolean frameClosed;
        private boolean drawing;
        private boolean frameReleased;
        private EntityRenderMode mode;
        private FiguraRenderPose figuraBasePose;
        private Avatar figuraBase;
        private boolean drawingBase;
        private boolean previewSelectionCaptured;
        private Object previewPart;
        private OutfitConfig.BoneRef previewBone;
        private OutfitConfig.Transform previewOffset;
        private UUID previewMount;
        private boolean previewRoot;
        private final Map<UUID, MountTarget> mountTargets = new HashMap<>();
        private List<PreviewBoneHighlight.Segment> previewHelpers;

        private Frame(LivingEntity viewWearer) {
            this.viewWearer = viewWearer;
            world = viewWearer == null ? null : viewWearer.level();
            worldTick = world == null ? 0 : viewWearer.level().getGameTime();
            previousPosition = world == null ? Vec3.ZERO : viewWearer.getPosition(0);
            currentPosition = world == null ? Vec3.ZERO : viewWearer.getPosition(1);
        }

        public LivingEntity renderWearer() {
            return viewWearer;
        }

        public void capturePreviewSelection(Object part) {
            OutfitConfig.require(!frameClosed && !evaluated && !previewSelectionCaptured);
            previewSelectionCaptured = true;
            if (part instanceof PreviewBoneHighlight.Mount mount) {
                if (configuration.instances().stream().anyMatch(instance -> instance.instanceId().equals(mount.instanceId())
                        && instance.enabled() && instance.anchor().type() == OutfitConfig.AnchorType.AUTO_TRAIL)) {
                    previewMount = mount.instanceId();
                }
            } else if (part instanceof OutfitConfig.AnchorRef anchor) {
                var handle = previewAnchors.get(anchor);
                if (handle != null) {
                    previewBone = handle.reference;
                    previewOffset = handle.offset;
                    previewPart = previewParts.get(previewBone);
                }
            } else {
                previewBone = previewBones.get(part);
                previewPart = previewBone == null ? null : part;
            }
        }

        public void withPreviewSelection(Runnable drawing) {
            if (previewSelectionCaptured) {
                PreviewBoneHighlight.withSelection(previewPart, drawing);
            } else {
                drawing.run();
            }
        }

        public List<PreviewBoneHighlight.Segment> previewHelpers(SkeletonPose base) {
            if (!previewSelectionCaptured || previewBone == null && !previewRoot || frameClosed) {
                return List.of();
            }
            if (previewHelpers == null) {
                int budget = Math.min(limits.maxResourceNodes(), limits.maxTotalNodes());
                if (previewRoot) {
                    previewHelpers = PreviewBoneHighlight.root(previewOffset, budget);
                } else {
                    var pose = previewBone.instanceId() == null ? base : instancePoses.get(previewBone.instanceId());
                    previewHelpers = previewOffset == null ? PreviewBoneHighlight.skeleton(pose, previewBone, budget)
                            : PreviewBoneHighlight.anchor(pose, previewBone, previewOffset, budget);
                }
            }
            return previewHelpers;
        }

        public void drawLocalParticles(Matrix4fc entityToDraw, net.minecraft.client.renderer.MultiBufferSource buffers,
                                       float partialTick) {
            if (previewParticles != null && LocalOutfitPreview.shows(OutfitComposition.this) && viewWearer != null) {
                previewParticles.extract(viewWearer.getPosition(partialTick),
                        net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation(), partialTick)
                        .draw(new Matrix4f(entityToDraw), buffers);
            }
        }

        /** Capture before deferring the base's renderEvent/native draw/postRenderEvent callback. */
        public void prepareFiguraBase(Avatar base) {
            bindBaseBudget(base);
            OutfitConfig.require(!frameClosed && !drawing && !drawingBase
                    && configuration.base() != null && configuration.base().format() == OutfitConfig.Format.FIGURA);
            if (figuraBasePose != null) {
                OutfitConfig.require(figuraBase == base);
                return;
            }
            var parts = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) base.renderer, configuration.base(),
                    null, indexes, limits).binding().parts();
            var lease = top.aihmc.omnimorph.render.acceleration.AccelerationService.INSTANCE.capture(
                    base.accelerationOwner(), String.valueOf(base.renderMode));
            figuraBasePose = new FiguraRenderPose(parts, lease);
            figuraBase = base;
        }

        public void drawFiguraBase(Avatar base, Runnable nativeDrawing) {
            if (frameClosed || drawingBase || figuraBasePose == null || figuraBase != base) {
                throw new IllegalStateException("OUTFIT_BASE_POSE_NOT_PREPARED");
            }
            drawingBase = true;
            try {
                try (var instructions = instructionBudget.enter();
                     var budget = molangBudget().beginFrame(YsmRenderFrameClock.currentFrame())) {
                    withPreviewSelection(() -> figuraBasePose.draw(nativeDrawing));
                }
            } finally {
                drawingBase = false;
                releaseFrame();
            }
        }

        /** YSM already retains its native render pose; only scope the shared budgets and lifetime. */
        public void drawYsmBase(Avatar base, Runnable nativeDrawing) {
            OutfitConfig.require(!frameClosed && !drawingBase && budgetBase == base
                    && configuration.base() != null && configuration.base().format() != OutfitConfig.Format.FIGURA);
            drawingBase = true;
            try (var instructions = instructionBudget.enter();
                 var budget = molangBudget().beginFrame(YsmRenderFrameClock.currentFrame())) {
                withPreviewSelection(nativeDrawing);
            } finally {
                drawingBase = false;
                releaseFrame();
            }
        }

        public SkeletonPose captureVanillaBase(net.minecraft.client.model.player.PlayerModel model,
                                               Matrix4fc nativeToEntity) {
            OutfitConfig.require(configuration.base() == null);
            return new VanillaSkeletonAdapter(model, limits, indexes).capture(nativeToEntity);
        }

        public SkeletonPose captureVanillaArm(net.minecraft.client.model.player.PlayerModel model,
                                              Matrix4fc nativeToEntity, boolean left) {
            OutfitConfig.require(configuration.base() == null);
            return new VanillaSkeletonAdapter(model, limits, indexes).captureArm(nativeToEntity, left);
        }

        public SkeletonPose captureFiguraArm(Avatar base, Matrix4fc nativeToEntity, boolean left,
                                             Runnable nativeDrawing) {
            bindBaseBudget(base);
            OutfitConfig.require(configuration.base() != null
                    && configuration.base().format() == OutfitConfig.Format.FIGURA);
            return new FiguraSkeletonAdapter((ImmediateFiguraRenderer) base.renderer, configuration.base(),
                    null, indexes, limits).captureArm(nativeToEntity, left, nativeDrawing);
        }

        public SkeletonPose captureFiguraBase(Avatar base, Matrix4fc nativeToEntity, Runnable nativeDrawing) {
            bindBaseBudget(base);
            OutfitConfig.require(configuration.base() != null
                    && configuration.base().format() == OutfitConfig.Format.FIGURA);
            return new FiguraSkeletonAdapter((ImmediateFiguraRenderer) base.renderer, configuration.base(),
                    null, indexes, limits).capture(nativeToEntity, nativeDrawing);
        }

        public SkeletonPose captureYsmBase(top.aihmc.omnimorph.model.ysm.YsmModelRuntime base,
                                           Matrix4fc nativeToEntity, YsmRenderPose pose) {
            bindBaseBudget(base.renderBudgetOwner());
            OutfitConfig.require(configuration.base() != null
                    && configuration.base().format() != OutfitConfig.Format.FIGURA);
            return new YsmSkeletonAdapter(base, configuration.base(), null, indexes, limits)
                    .capture(nativeToEntity, pose);
        }

        public SkeletonPose captureYsmArm(top.aihmc.omnimorph.model.ysm.YsmModelRuntime base,
                                          Matrix4fc nativeToEntity, boolean left, YsmRenderPose pose) {
            bindBaseBudget(base.renderBudgetOwner());
            OutfitConfig.require(configuration.base() != null
                    && configuration.base().format() != OutfitConfig.Format.FIGURA);
            return new YsmSkeletonAdapter(base, configuration.base(), null, indexes, limits)
                    .captureArm(nativeToEntity, left, pose);
        }

        /** The base engine supplies the same frame's normalized pose before accessory native drawing. */
        public void draw(SkeletonPose basePose, LivingEntityRenderState renderState, float delta, Consumer<Draw> renderer) {
            draw(basePose, renderState, delta, EntityRenderMode.FIGURA_GUI, renderer);
        }

        public void draw(SkeletonPose basePose, LivingEntityRenderState renderState, float delta,
                         EntityRenderMode mode, Consumer<Draw> renderer) {
            if (frameClosed || drawing || activeFrame != null) {
                throw new IllegalStateException("OUTFIT_FRAME_CLOSED_OR_REENTRANT");
            }
            drawing = true;
            try (var instructions = instructionBudget.enter();
                 var budget = molangBudget().beginFrame(YsmRenderFrameClock.currentFrame())) {
                if (!evaluated) {
                    OutfitConfig.require(instanceNodes + basePose.definition().nodes().size() <= limits.maxTotalNodes());
                    this.basePose = basePose;
                    this.renderState = renderState;
                    this.mode = mode;
                    this.delta = delta;
                    activeFrame = this;
                    try {
                        runController("beginFrame", delta);
                        int drawn = 0;
                        // Lua chooses ready mounts; native draws supply their actual pivots for the next wave.
                        for (int wave = 0; wave <= limits.maxInstances() && !frameClosed; wave++) {
                            runController("frame", delta);
                            int end = commands.size();
                            if (drawn == end) {
                                runController("fallbackFrame", delta);
                                end = commands.size();
                                if (drawn == end) {
                                    break;
                                }
                            }
                            while (drawn < end && !frameClosed) {
                                renderer.accept(commands.get(drawn++));
                            }
                        }
                        runController("endFrame", delta);
                        if (mode == EntityRenderMode.FIGURA_GUI && !closed && sequence >= previewMountSequence) {
                            previewMountTargets.clear();
                            previewMountTargets.putAll(mountTargets);
                            previewMountSequence = sequence;
                        }
                    } finally {
                        // A native draw failure freezes the prepared commands, just like an ordinary retained frame.
                        evaluated = true;
                        activeFrame = null;
                        this.basePose = null;
                        this.renderState = null;
                    }
                } else {
                    for (Draw command : List.copyOf(commands)) {
                        if (frameClosed) {
                            break;
                        }
                        renderer.accept(command);
                    }
                }
            } catch (RuntimeException | Error failure) {
                if (world != null && (mode == EntityRenderMode.RENDER || mode == EntityRenderMode.WORLD)) {
                    trails.values().forEach(trail -> invalidateTrail(trail, sequence));
                }
                throw failure;
            } finally {
                drawing = false;
                releaseFrame();
            }
        }

        /** Must run inside the command's retained Figura pose, including hidden-group fallback capture. */
        public void drawFiguraInstance(Draw command, Matrix4fc nativeToEntity, Runnable nativeDrawing) {
            boolean selectedInstance = previewBone != null && command.instanceId().equals(previewBone.instanceId());
            if (evaluated || !selectedInstance && exportedBones.getOrDefault(command.instanceId(), Set.of()).isEmpty()) {
                nativeDrawing.run();
                return;
            }
            var adapter = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) command.avatar().renderer,
                    command.avatar().cosmeticContext.resource(), command.instanceId(), indexes, limits);
            SkeletonPose pose = adapter.capture(nativeToEntity, nativeDrawing);
            if (!evaluated) {
                instancePoses.put(command.instanceId(), pose);
            }
        }

        public void captureYsmInstance(Draw command) {
            boolean selectedInstance = previewBone != null && command.instanceId().equals(previewBone.instanceId());
            if (!evaluated && (selectedInstance || !exportedBones.getOrDefault(command.instanceId(), Set.of()).isEmpty())) {
                var avatar = command.avatar();
                var adapter = new YsmSkeletonAdapter(avatar.getYsmRuntime(), avatar.cosmeticContext.resource(),
                        command.instanceId(), indexes, limits);
                var mounted = new Matrix4f().scale(1f / 16).mul(command.matrix()).scale(16);
                instancePoses.put(command.instanceId(), adapter.capture(mounted, command.ysmPose()));
            }
        }

        @Override
        public void close() {
            frameClosed = true;
            releaseFrame();
        }

        private void releaseFrame() {
            if (frameClosed && !drawing && !drawingBase && !frameReleased) {
                frameReleased = true;
                commands.clear();
                prepared.clear();
                figuraPoses.clear();
                instancePoses.clear();
                figuraBasePose = null;
                figuraBase = null;
                previewPart = null;
                previewBone = null;
                previewOffset = null;
                previewMount = null;
                mountTargets.clear();
                previewHelpers = null;
                retainedFrames--;
                releaseIfRetired();
            }
        }
    }

    @LuaWhitelist
    @LuaTypeDoc(name = "CosmeticBoneHandle", value = "cosmetic_bone_handle")
    public static final class BoneHandle {
        private final OutfitConfig.BoneRef reference;
        private final OutfitConfig.Transform offset;

        private BoneHandle(OutfitConfig.BoneRef reference) {
            this(reference, OutfitConfig.Transform.identity());
        }

        private BoneHandle(OutfitConfig.BoneRef reference, OutfitConfig.Transform offset) {
            this.reference = reference;
            this.offset = offset;
        }
    }

    /** Only the trusted built-in controller receives this bridge, never an author's Globals. */
    @LuaWhitelist
    @LuaTypeDoc(name = "CosmeticNativeBridge", value = "cosmetic_native")
    public final class NativeBridge {
        @LuaWhitelist
        public List<Map<String, Object>> getDeclarations() {
            var result = new ArrayList<Map<String, Object>>();
            for (var instance : configuration.instances()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", instance.instanceId().toString());
                value.put("enabled", instance.enabled());
                value.put("anchor_type", instance.anchor().type().name());
                value.put("anchor_name", instance.anchor().name());
                value.put("category", categories.get(instance.instanceId()));
                if (instance.anchor().preferredInstanceId() != null) {
                    value.put("preferred_tail", instance.anchor().preferredInstanceId().toString());
                }
                BoneHandle target = instance.anchor().bone() == null ? null : new BoneHandle(instance.anchor().bone());
                if (instance.anchor().type() == OutfitConfig.AnchorType.VIRTUAL) {
                    var anchor = configuration.virtualAnchors().stream()
                            .filter(candidate -> candidate.id().equals(instance.anchor().anchorId()))
                            .findFirst().orElseThrow();
                    target = new BoneHandle(anchor.parent(), anchor.transform());
                }
                value.put("bone", target);
                if (target != null && target.reference.instanceId() != null) {
                    value.put("parent_id", target.reference.instanceId().toString());
                }
                value.put("transform", FiguraMat4.of().set(instance.transform().modelMatrix()));
                result.add(value);
            }
            return result;
        }

        @LuaWhitelist
        public List<BoneHandle> findAnchors(String name) {
            var result = new ArrayList<BoneHandle>();
            if (activeFrame != null) {
                SkeletonIndex skeleton = activeFrame.basePose.definition();
                for (var target : StandardAnchors.resolve(skeleton, configuration.base() == null, name)) {
                    result.add(new BoneHandle(target.bone(), target.offset()));
                }
            }
            return result;
        }

        @LuaWhitelist
        public List<BoneHandle> findExportedAnchors(String id, String name) {
            var result = new ArrayList<BoneHandle>();
            for (var bone : exportedBones.getOrDefault(UUID.fromString(id), Set.of())) {
                if (bone.path().getLast().name().equals(name)) {
                    result.add(new BoneHandle(bone));
                }
            }
            return result;
        }

        @LuaWhitelist
        public List<BoneHandle> findBaseBones(String name) {
            if (activeFrame == null) {
                return List.of();
            }
            var skeleton = activeFrame.basePose.definition();
            return skeleton.uniqueNamed(name).map(index -> List.of(new BoneHandle(skeleton.reference(null, index))))
                    .orElse(List.of());
        }

        @LuaWhitelist
        public BoneHandle avatarRoot() {
            if (activeFrame == null) {
                return null;
            }
            var skeleton = activeFrame.basePose.definition();
            // Multi-root native models still have an entity root, without inventing a persistent bone path.
            return skeleton.roots().size() == 1
                    ? new BoneHandle(skeleton.reference(null, skeleton.roots().getFirst())) : new BoneHandle(null);
        }

        @LuaWhitelist
        public boolean isHealthy(String id) {
            Avatar avatar = instances.get(UUID.fromString(id));
            return avatar != null && avatar.loaded && !avatar.scriptError && avatar.accelerationOwner().isCurrent();
        }

        @LuaWhitelist
        public void recordAutomaticTail(String id, String tail) {
            UUID instanceId = UUID.fromString(id);
            OutfitConfig.require(configuration.instances().stream().anyMatch(instance -> instance.instanceId().equals(instanceId)
                    && instance.anchor().type() == OutfitConfig.AnchorType.AUTO_TRAIL));
            if (tail.isEmpty()) {
                automaticTailSelections.remove(instanceId);
            } else {
                UUID tailId = UUID.fromString(tail);
                OutfitConfig.require(!tailId.equals(instanceId) && "Tail".equals(categories.get(tailId)));
                automaticTailSelections.put(instanceId, tailId);
            }
        }

        @LuaWhitelist
        public FiguraMat4 boneMatrix(BoneHandle bone) {
            if (!hasBone(bone)) {
                return null;
            }
            if (bone.reference == null) {
                return FiguraMat4.of();
            }
            SkeletonPose pose = bone.reference.instanceId() == null ? activeFrame.basePose
                    : activeFrame.instancePoses.get(bone.reference.instanceId());
            var matrix = pose == null ? null : pose.matrix(bone.reference)
                    .map(value -> FiguraMat4.of().set(value.mul(bone.offset.modelMatrix()))).orElse(null);
            return matrix;
        }

        @LuaWhitelist
        public void sampleTrail(String id, BoneHandle bone, FiguraMat4 matrix, double minDistance,
                                double intervalTicks, double lifetimeTicks, double teleportDistance) {
            TrailBuffer buffer = trails.get(UUID.fromString(id));
            if (buffer == null || !trailSamplingFrame()) {
                return;
            }
            var policy = new TrailBuffer.Policy(minDistance, intervalTicks, lifetimeTicks, teleportDistance);
            var viewWearer = activeFrame.viewWearer;
            if (viewWearer.isRemoved() || viewWearer.level() != activeFrame.world || !hasBone(bone)
                    || viewWearer.level().getGameTime() - (activeFrame.worldTick + activeFrame.delta) >= policy.lifetimeTicks()) {
                invalidateTrail(buffer, activeFrame.sequence);
                return;
            }
            UUID trailBudget = previewParticles == null ? wearer.getUUID() : previewParticles.budgetId();
            if (!OutfitTrailBudget.acquire(buffer, trailBudget, limits.maxTrails())) {
                invalidateTrail(buffer, activeFrame.sequence);
                return;
            }
            var local = matrix.toMatrix4f().getTranslation(new org.joml.Vector3f());
            Vec3 origin = activeFrame.previousPosition.lerp(activeFrame.currentPosition, activeFrame.delta);
            var position = origin.add(local.x / 16d, local.y / 16d, local.z / 16d);
            var target = new TrailTarget(bone.reference, bone.offset);
            buffer.sample(activeFrame.sequence, activeFrame.world, target, activeFrame.worldTick + activeFrame.delta,
                    position, policy);
        }

        @LuaWhitelist
        public void resetTrail(String id) {
            TrailBuffer buffer = trails.get(UUID.fromString(id));
            if (buffer != null) {
                if (activeFrame == null) {
                    clearTrail(buffer);
                } else if (trailSamplingFrame()) {
                    invalidateTrail(buffer, activeFrame.sequence);
                }
            }
        }

        private boolean trailSamplingFrame() {
            if (activeFrame == null || activeFrame.world == null || wearer == null || closed) {
                return false;
            }
            if (previewParticles == null) {
                return activeFrame.mode == EntityRenderMode.RENDER || activeFrame.mode == EntityRenderMode.WORLD;
            }
            return (activeFrame.mode == EntityRenderMode.FIGURA_GUI
                    || LocalOutfitPreview.shows(OutfitComposition.this)) && previewParticles.isActive();
        }

        @LuaWhitelist
        public boolean hasBone(BoneHandle bone) {
            if (bone == null || activeFrame == null) {
                return false;
            }
            if (bone.reference == null) {
                return true;
            }
            if (bone.reference.instanceId() == null) {
                boolean defined = activeFrame.basePose != null && activeFrame.basePose.defines(bone.reference);
                return defined;
            }
            return exportedBones.getOrDefault(bone.reference.instanceId(), Set.of()).contains(bone.reference);
        }

        /** Accessory errors are not printed for non-local execution identities; surface the author's message here. */
        private static String scriptErrorText(Avatar avatar) {
            String text = avatar.errorText == null ? "<no error text>" : avatar.errorText.getString();
            text = text.replace('\n', ' ').replace('\t', ' ');
            return text.length() > 400 ? text.substring(0, 400) + "…" : text;
        }

        /** Diagnostic only: each distinct mount failure is logged once per composition. */
        private void diagnose(String reason) {
            if (diagnosed.add(reason)) {
                org.figuramc.figura.FiguraMod.LOGGER.warn("Outfit accessory not drawn: {}", reason);
            }
        }

        private Avatar initialized(String id) {
            Avatar avatar = instances.get(UUID.fromString(id));
            if (avatar == null) {
                throw new IllegalArgumentException("COSMETIC_INSTANCE_MISSING");
            }
            if (initialized.add(avatar.cosmeticContext.instanceId())) {
                avatar.initializePreviewPlayer(wearer);
            }
            return avatar;
        }

        @LuaWhitelist
        public boolean tick(String id) {
            Avatar avatar = initialized(id);
            avatar.tick();
            if (avatar.scriptError) {
                diagnose("accessory script error during tick; the controller stops this instance: "
                        + scriptErrorText(avatar));
            }
            return !avatar.scriptError;
        }

        @LuaWhitelist
        public boolean prepare(String id, float delta) {
            if (activeFrame == null) {
                throw new IllegalStateException("COSMETIC_FRAME_REQUIRED");
            }
            Avatar avatar = initialized(id);
            EntityRenderMode previousMode = avatar.renderMode;
            avatar.renderMode = activeFrame.mode;
            try {
                if (avatar.cosmeticContext.scope() == CosmeticInstanceContext.Scope.DRAFT) {
                    avatar.render(delta);
                }
                avatar.renderEvent(delta, FiguraMat4.of());
                avatar.clearAnimations();
                avatar.applyAnimations();
                var ysm = avatar.getYsmRuntime();
                if (ysm != null && activeFrame.renderState != null) {
                    ysm.updateAnimations(activeFrame.renderState, activeFrame.viewWearer);
                }
                if (avatar.scriptError) {
                    diagnose("accessory script error during prepare: " + scriptErrorText(avatar));
                    return false;
                }
                var overrides = boneOverrides.get(avatar.cosmeticContext.instanceId());
                activeFrame.prepared.put(avatar.cosmeticContext.instanceId(), ysm == null ? null
                        : ysm.captureRenderPose(false).withBoneOverrides(overrides.ysm()));
                if (ysm == null) {
                    var parts = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer,
                            avatar.cosmeticContext.resource(), avatar.cosmeticContext.instanceId(), indexes, limits)
                            .binding().parts();
                    var lease = top.aihmc.omnimorph.render.acceleration.AccelerationService.INSTANCE.capture(
                            avatar.accelerationOwner(), String.valueOf(avatar.renderMode));
                    activeFrame.figuraPoses.put(avatar.cosmeticContext.instanceId(), new FiguraRenderPose(parts, lease,
                            (ImmediateFiguraRenderer) avatar.renderer, overrides.figura()));
                }
                return true;
            } catch (MolangExecutionBudget.Exceeded exhausted) {
                diagnose("accessory instruction budget exhausted during prepare");
                return false;
            } finally {
                avatar.renderMode = previousMode;
            }
        }

        @LuaWhitelist
        public void recordMountTarget(String id, BoneHandle target) {
            UUID instance = UUID.fromString(id);
            OutfitConfig.require(activeFrame != null && activeFrame.prepared.containsKey(instance) && hasBone(target));
            var selected = new MountTarget(target.reference, target.offset);
            activeFrame.mountTargets.put(instance, selected);
            if (instance.equals(activeFrame.previewMount)) {
                activeFrame.previewBone = target.reference;
                activeFrame.previewOffset = target.offset;
                activeFrame.previewRoot = target.reference == null;
                activeFrame.previewPart = previewParts.get(target.reference);
            }
        }

        @LuaWhitelist
        public void emit(String id, FiguraMat4 matrix) {
            UUID instance = UUID.fromString(id);
            if (activeFrame == null || !activeFrame.prepared.containsKey(instance)
                    || activeFrame.emitted.size() >= limits.maxInstances() || !activeFrame.emitted.add(instance)) {
                throw new IllegalArgumentException("COSMETIC_DRAW_REJECTED");
            }
            Matrix4f transform = matrix.toMatrix4f();
            for (float value : transform.get(new float[16])) {
                OutfitConfig.require(Float.isFinite(value));
            }
            activeFrame.commands.add(new Draw(instance, instances.get(instance), transform,
                    activeFrame.prepared.get(instance), activeFrame.figuraPoses.get(instance)));
        }
    }
}
