package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.ysm.YsmRenderPose;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.permissions.Permissions;

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

/** One immutable draft generation. Ownership transfers only after successful construction. */
public final class OutfitComposition implements AutoCloseable {
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
    private final OutfitLimits limits;
    private final LivingEntity wearer;
    private final Map<UUID, Avatar> instances;
    private final Set<UUID> initialized = new HashSet<>();
    private final Avatar controllerOwner;
    private final FiguraLuaRuntime controllerRuntime;
    private final LuaTable controller;
    private final NativeBridge bridge = new NativeBridge();
    private final int instanceNodes;
    private final SkeletonIndex.Cache indexes;
    private int retainedFrames;
    private boolean closed;
    private boolean released;
    private Frame activeFrame;
    private final MolangExecutionBudget vanillaMolangBudget = new MolangExecutionBudget();
    private Avatar budgetBase;
    private final OutfitInstructionBudget instructionBudget = new OutfitInstructionBudget(() -> budgetBase);
    private final OutfitEffectBudget effectBudget;

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

    public void tick() {
        tick(null);
    }

    public void tick(Avatar base) {
        if (!closed) {
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

    /** Retain at GUI extraction; release only when the native deferred frame has finished. */
    public Frame retain() {
        if (closed) {
            throw new IllegalStateException("OUTFIT_COMPOSITION_CLOSED");
        }
        retainedFrames++;
        return new Frame();
    }

    private void runController(String method, float delta) {
        controllerOwner.tick.reset(controllerOwner.permissions.get(Permissions.TICK_INST));
        if (controllerRuntime.run(controller.get(method), controllerOwner.tick, delta) == null
                || controllerOwner.scriptError) {
            throw new IllegalStateException("COSMETIC_CONTROLLER_FAILED");
        }
    }

    @Override
    public void close() {
        closed = true;
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
    }

    public final class Frame implements AutoCloseable {
        private SkeletonPose basePose;
        private LivingEntityRenderState renderState;
        private final Map<UUID, YsmRenderPose> prepared = new HashMap<>();
        private final Map<UUID, FiguraRenderPose> figuraPoses = new HashMap<>();
        private final List<Draw> commands = new ArrayList<>();
        private final Set<UUID> emitted = new HashSet<>();
        private boolean evaluated;
        private boolean frameClosed;
        private boolean drawing;
        private boolean frameReleased;
        private EntityRenderMode mode;
        private FiguraRenderPose figuraBasePose;
        private Avatar figuraBase;
        private boolean drawingBase;

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
                    figuraBasePose.draw(nativeDrawing);
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
                nativeDrawing.run();
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
                    activeFrame = this;
                    try {
                        runController("frame", delta);
                        evaluated = true;
                    } finally {
                        activeFrame = null;
                        this.basePose = null;
                        this.renderState = null;
                    }
                }
                for (Draw command : List.copyOf(commands)) {
                    if (frameClosed) {
                        break;
                    }
                    renderer.accept(command);
                }
            } finally {
                drawing = false;
                releaseFrame();
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
                figuraBasePose = null;
                figuraBase = null;
                retainedFrames--;
                releaseIfRetired();
            }
        }
    }

    @LuaWhitelist
    @LuaTypeDoc(name = "CosmeticBoneHandle", value = "cosmetic_bone_handle")
    public static final class BoneHandle {
        private final OutfitConfig.BoneRef reference;

        private BoneHandle(OutfitConfig.BoneRef reference) {
            this.reference = reference;
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
                value.put("bone", instance.anchor().bone() == null ? null : new BoneHandle(instance.anchor().bone()));
                value.put("transform", FiguraMat4.of().set(instance.transform().modelMatrix()));
                result.add(value);
            }
            return result;
        }

        @LuaWhitelist
        public List<BoneHandle> findBones(String name) {
            var result = new ArrayList<BoneHandle>();
            if (activeFrame != null) {
                SkeletonIndex skeleton = activeFrame.basePose.definition();
                for (int index = 0; index < skeleton.nodes().size(); index++) {
                    if (skeleton.nodes().get(index).name().equals(name)) {
                        result.add(new BoneHandle(skeleton.reference(null, index)));
                    }
                }
            }
            return result;
        }

        @LuaWhitelist
        public FiguraMat4 boneMatrix(BoneHandle bone) {
            return bone == null || activeFrame == null ? null
                    : activeFrame.basePose.matrix(bone.reference).map(matrix -> FiguraMat4.of().set(matrix)).orElse(null);
        }

        @LuaWhitelist
        public boolean hasBone(BoneHandle bone) {
            return bone != null && activeFrame != null && activeFrame.basePose.defines(bone.reference);
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
                avatar.render(delta);
                avatar.renderEvent(delta, FiguraMat4.of());
                avatar.clearAnimations();
                avatar.applyAnimations();
                var ysm = avatar.getYsmRuntime();
                if (ysm != null && activeFrame.renderState != null) {
                    ysm.updateAnimations(activeFrame.renderState, wearer);
                }
                if (avatar.scriptError) {
                    return false;
                }
                activeFrame.prepared.put(avatar.cosmeticContext.instanceId(), ysm == null ? null : ysm.captureRenderPose(false));
                if (ysm == null) {
                    var parts = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer,
                            avatar.cosmeticContext.resource(), avatar.cosmeticContext.instanceId(), indexes, limits)
                            .binding().parts();
                    var lease = top.aihmc.omnimorph.render.acceleration.AccelerationService.INSTANCE.capture(
                            avatar.accelerationOwner(), String.valueOf(avatar.renderMode));
                    activeFrame.figuraPoses.put(avatar.cosmeticContext.instanceId(), new FiguraRenderPose(parts, lease));
                }
                return true;
            } catch (MolangExecutionBudget.Exceeded exhausted) {
                return false;
            } finally {
                avatar.renderMode = previousMode;
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
