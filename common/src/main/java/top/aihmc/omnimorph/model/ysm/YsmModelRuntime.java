package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.render.acceleration.AccelerationService;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.avatar.AvatarTranslations;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarKind;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;
import top.aihmc.omnimorph.lua.api.action_wheel.Action;
import top.aihmc.omnimorph.lua.api.action_wheel.ActionWheelAPI;
import top.aihmc.omnimorph.lua.api.action_wheel.Page;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.animation.YsmDefaultAnimationResolver;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.action.YsmActionDefinition;
import top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime;
import top.aihmc.omnimorph.model.ysm.action.YsmActionSchemaParser;
import top.aihmc.omnimorph.model.ysm.action.YsmActionWheelLayoutStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YsmModelRuntime implements AutoCloseable {
    private static final float DEFAULT_YSM_SCALE = 0.7f;

    private final Avatar owner;
    private Avatar complexityOwner;
    private final YsmGeometry geometry;
    private YsmGeometry armGeometry;
    private FiguraTexture texture;
    private String textureId;
    private boolean textureTranslucent;
    private final Map<String, byte[]> textureData;
    private Map<String, MaterialData> materialData = Map.of();
    private final Map<String, YsmModelPart> parts = new LinkedHashMap<>();
    private final Map<String, YsmModelPart> armParts = new LinkedHashMap<>();
    private final Map<String, YsmBoneRole> boneRoles = new LinkedHashMap<>();
    private final Map<String, YsmBoneRole> armBoneRoles = new LinkedHashMap<>();
    private final Map<String, YsmLocator> locators = new LinkedHashMap<>();
    private final Map<String, YsmAttachmentPoint> attachmentPoints = new LinkedHashMap<>();
    private final Map<String, FiguraMat4> locatorWorldMatrices = new LinkedHashMap<>();
    private final Map<String, YsmSubEntity> subEntities = new LinkedHashMap<>();
    private final Map<String, String> controllerSources = new LinkedHashMap<>();
    private final Set<String> controllerDiagnostics = new LinkedHashSet<>();
    private final YsmRenderer renderer;
    private final YsmBoneQueryRuntime boneQueries = new YsmBoneQueryRuntime(this);
    private final YsmParticleRuntime particles = new YsmParticleRuntime(this);
    private final YsmSoundRuntime sounds = new YsmSoundRuntime(this);
    private final YsmAudioResources audio = new YsmAudioResources(this);
    private final AvatarTranslations translations = new AvatarTranslations();
    private final YsmGuiResources guiResources;
    private final YsmAnimationPlayer animationPlayer;
    private final YsmActionRuntime actions;
    private final YsmMolangFunctionRuntime functions;
    private final String kind;
    private final boolean encryptedSource;
    private boolean allCutout;
    private boolean renderLayersFirst;
    private boolean previewRotationDisabled;
    private boolean guiNoLighting;
    private boolean previewRuntime;
    private boolean subEntityRuntime;
    private long subEntityQueryFrame = Long.MIN_VALUE;
    private boolean mergeMultilineExpressions;
    private ListTag functionInput = new ListTag();
    private YsmAudioResources sharedAudio;
    private final YsmSubEntityInstances subEntityInstances = new YsmSubEntityInstances(this);
    private boolean ownsTexture = true;
    private volatile boolean closed;
    private CompoundTag previewInput;
    private YsmModelRuntime preview;
    private FiguraTexture previewSourceTexture;
    private Map<String, Object> previewControls;
    private boolean previewAttempted;
    private final String modelKey;
    private String contentHash = "";
    private final float defaultWidthScale;
    private final float defaultHeightScale;
    private String defaultAnimationSignature = "";
    private final List<String> defaultAnimationDiagnostics = new ArrayList<>();

    private YsmModelRuntime(Avatar owner, YsmGeometry geometry, YsmGeometry armGeometry, FiguraTexture texture, String textureId, boolean textureTranslucent, String kind, String modelKey, Map<String, byte[]> textureData, float defaultWidthScale, float defaultHeightScale, boolean encryptedSource) {
        this.owner = owner;
        this.complexityOwner = owner;
        this.encryptedSource = encryptedSource;
        this.geometry = geometry;
        this.armGeometry = armGeometry;
        this.texture = texture;
        this.textureId = textureId;
        this.textureTranslucent = textureTranslucent;
        this.textureData = textureData == null ? Map.of() : Map.copyOf(textureData);
        this.kind = kind != null ? kind : YsmAvatarKind.NONE.name();
        this.modelKey = modelKey == null || modelKey.isBlank() ? this.kind + ":" + textureId : modelKey;
        this.defaultWidthScale = sanitizeScale(defaultWidthScale);
        this.defaultHeightScale = sanitizeScale(defaultHeightScale);
        buildParts();
        buildAttachmentPoints();
        dumpDebugInfo();
        this.renderer = new YsmRenderer(this);
        this.guiResources = new YsmGuiResources(owner);
        this.animationPlayer = new YsmAnimationPlayer(this);
        this.actions = new YsmActionRuntime(this);
        this.functions = new YsmMolangFunctionRuntime(this);
        this.functions.setControllerEventDomain("projectile".equals(this.kind) || "vehicle".equals(this.kind)
                ? this.kind : "player");
    }

    public static YsmModelRuntime fromNbt(Avatar owner, CompoundTag tag) throws java.io.IOException {
        return fromNbt(owner, tag, () -> false);
    }

    public static YsmModelRuntime fromNbt(Avatar owner, CompoundTag tag,
                                           java.util.function.BooleanSupplier cancelled) throws java.io.IOException {
        return fromNbt(owner, tag, cancelled, stage -> {});
    }

    public static YsmModelRuntime fromNbt(Avatar owner, CompoundTag tag,
            java.util.function.BooleanSupplier cancelled,
            java.util.function.Consumer<top.aihmc.omnimorph.avatar.AvatarLoadProgress.Stage> progress) throws java.io.IOException {
        if (YsmSourcePayload.isEncrypted(tag)) {
            try (YsmModelData data = YsmSourcePayload.openData(tag, cancelled)) {
                progress.accept(top.aihmc.omnimorph.avatar.AvatarLoadProgress.Stage.BUILDING);
                return fromData(owner, data, tag);
            }
        }
        progress.accept(top.aihmc.omnimorph.avatar.AvatarLoadProgress.Stage.BUILDING);
        String mainJson = new String(tag.getByteArray("main_model").orElse(new byte[0]), StandardCharsets.UTF_8);
        if (mainJson.isBlank())
            throw new java.io.IOException("YSM main model is empty");
        YsmGeometry geometry = YsmGeometryParser.parse(mainJson);
        YsmModelValidator.validateAndLog(tag.getStringOr("main_model_path", "main_model"), geometry);

        YsmGeometry armGeometry = null;
        String armJson = new String(tag.getByteArray("arm_model").orElse(new byte[0]), StandardCharsets.UTF_8);
        if (!armJson.isBlank()) {
            try {
                armGeometry = YsmGeometryParser.parse(armJson);
                YsmModelValidator.validateAndLog(tag.getStringOr("arm_model_path", "arm_model"), armGeometry);
            } catch (Exception ignored) {
            }
        }

        return assemble(owner, tag, geometry, armGeometry);
    }

    public static YsmModelRuntime fromData(Avatar owner, YsmModelData data,
                                           CompoundTag tag) throws java.io.IOException {
        try {
            if (data.encrypted()) {
                String requested = tag.getStringOr("texture_id", data.manifest().defaultTexture());
                var textures = data.manifest().textures();
                var selected = textures.stream().filter(texture -> texture.id().equals(requested)).findFirst()
                        .orElse(textures.isEmpty() ? null : textures.getFirst());
                tag = YsmAvatarLoader.toRuntimeTag(data, selected);
            }
            return assemble(owner, tag, data.mainGeometry(), data.armGeometry(), data);
        } catch (java.io.IOException | RuntimeException e) {
            if (data.encrypted()) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA,
                        "runtime/assembly", 0);
            }
            throw e;
        }
    }

    private static YsmModelRuntime assemble(Avatar owner, CompoundTag tag, YsmGeometry geometry,
                                             YsmGeometry armGeometry) throws java.io.IOException {
        return assemble(owner, tag, geometry, armGeometry, null);
    }

    private static YsmModelRuntime assemble(Avatar owner, CompoundTag tag, YsmGeometry geometry,
                                             YsmGeometry armGeometry, YsmModelData data) throws java.io.IOException {
        byte[] textureBytes = tag.getByteArray("texture").orElse(new byte[0]);
        Map<String, byte[]> textureData = readTextureEntries(tag);
        Map<String, MaterialData> materials = readMaterialEntries(tag);
        String textureId = tag.getStringOr("texture_id", "ysm_texture");
        byte[] selectedTexture = textureData.getOrDefault(textureId, textureBytes);
        boolean textureTranslucent = geometry.applyTextureAlpha(selectedTexture);
        if (armGeometry != null)
            textureTranslucent |= armGeometry.applyTextureAlpha(selectedTexture);
        FiguraTexture texture = materialTexture(owner, textureId,
                selectedTexture.length == 0 ? onePixelPng() : selectedTexture, materials.get(textureId));
        String kind = tag.getStringOr("kind", YsmAvatarKind.NONE.name());
        String modelKey = tag.getStringOr("source_path", tag.getStringOr("main_model_path", "ysm"));
        if (tag.contains("content_sha256")) {
            modelKey = "ysm:" + tag.getIntOr("engine_schema_version", 1) + ":" + tag.getStringOr("content_sha256", "");
        }
        float defaultWidthScale = tag.getFloatOr("width_scale", DEFAULT_YSM_SCALE);
        float defaultHeightScale = tag.getFloatOr("height_scale", DEFAULT_YSM_SCALE);
        YsmModelRuntime runtime = null;
        try {
            runtime = new YsmModelRuntime(owner, geometry, armGeometry, texture, textureId,
                    textureTranslucent, kind, modelKey, textureData, defaultWidthScale, defaultHeightScale, data != null && data.encrypted());
            runtime.contentHash = tag.getStringOr("content_sha256", "");
            runtime.previewInput = new CompoundTag();
            for (String key : List.of("animations", "animation_controllers", "functions", "action_schemas")) {
                runtime.previewInput.put(key, tag.getListOrEmpty(key).copy());
            }
            for (String key : List.of("main_model", "arm_model")) {
                if (tag.contains(key)) {
                    runtime.previewInput.putByteArray(key, tag.getByteArray(key).orElseThrow().clone());
                }
            }
            runtime.previewInput.putBoolean("merge_multiline_expr", tag.getBooleanOr("merge_multiline_expr", false));
            runtime.functionInput = tag.getListOrEmpty("functions").copy();
            runtime.mergeMultilineExpressions = tag.getBooleanOr("merge_multiline_expr", false);
            runtime.guiNoLighting = tag.getBooleanOr("gui_no_lighting", false);
            runtime.previewRotationDisabled = tag.getBooleanOr("disable_preview_rotation", false);
            runtime.renderLayersFirst = tag.getBooleanOr("render_layers_first", false);
            runtime.allCutout = tag.getBooleanOr("all_cutout", false);
            runtime.materialData = Map.copyOf(materials);
            runtime.audio.load(tag.getListOrEmpty("sound_entries"));
            runtime.translations.load(tag.getListOrEmpty("language_entries"));
            runtime.guiResources.load(tag);
            runtime.readSubEntities(tag, selectedTexture, data);
            MolangEngine ysmMolangEngine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
            runtime.animationPlayer.registerAnimations(readAnimations(tag, ysmMolangEngine));
            YsmModelRuntime loadedRuntime = runtime;
            runtime.animationPlayer.registerControllers(readAnimationControllers(tag, ysmMolangEngine, (path, diagnostic) -> {
                loadedRuntime.recordControllerDiagnostic(controllerDiagnostic(loadedRuntime.modelKey,
                        loadedRuntime.encryptedSource, path, diagnostic));
            }, runtime.controllerSources));
            runtime.functions.load(tag.getListOrEmpty("functions"));
            runtime.animationPlayer.registerFunctionEventControllers(runtime.functions.controllerEventNames());
            if (owner.cosmeticContext == null && owner.nbt != null
                    && !"ACCESSORY".equals(owner.nbt.getCompoundOrEmpty("metadata").getStringOr("resource_kind", ""))) {
                runtime.loadDefaultAnimations(tag, ysmMolangEngine);
            }
            runtime.animationPlayer.startBaseAnimations();
            runtime.registerDefaultControls();
            runtime.readActionSchemas(tag);
            if (runtime.actions.all().isEmpty())
                runtime.actions.buildDefaultsFromAnimations();
            runtime.registerDiscoveredControls(tag);
            return runtime;
        } catch (Exception e) {
            if (runtime == null) {
                texture.close();
            } else {
                runtime.close();
            }
            throw e;
        }
    }

    private void loadDefaultAnimations(CompoundTag tag, MolangEngine engine) {
        try {
            resolveDefaultAnimations(tag, engine);
        } catch (java.io.IOException failure) {
            // Keep the author's model usable and retain an explicit fallback failure for the UI.
            defaultAnimationDiagnostics.add(failure.getMessage());
        }
    }

    private void resolveDefaultAnimations(CompoundTag tag, MolangEngine engine) throws java.io.IOException {
        if (subEntityRuntime || "projectile".equals(kind) || "vehicle".equals(kind)) {
            return;
        }
        Set<String> declared = new LinkedHashSet<>();
        try {
            for (Tag value : tag.getListOrEmpty("animations")) {
                if (!(value instanceof CompoundTag animation)) {
                    continue;
                }
                String json = new String(animation.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
                if (json.isBlank()) {
                    continue;
                }
                var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                if (root.has("animations")) {
                    declared.addAll(root.getAsJsonObject("animations").keySet());
                }
            }
        } catch (RuntimeException invalid) {
            defaultAnimationDiagnostics.add("DEFAULT_FALLBACK_SKIPPED_AUTHOR_PARSE_ERROR");
            return;
        }
        Set<String> excluded = animationPlayer.defaultExcludedNames(declared);
        if (declared.stream().anyMatch(name -> !animationPlayer.getClips().containsKey(name))) {
            defaultAnimationDiagnostics.add("DEFAULT_FALLBACK_SKIPPED_DAMAGED_AUTHOR_ANIMATION");
        }
        Set<String> mainExcluded = new LinkedHashSet<>(excluded);
        boolean mainControlled = animationPlayer.getControllers().values().stream()
                .anyMatch(controller -> controller.slot() == top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot.MAIN);
        boolean handsControlled = mainControlled || animationPlayer.getControllers().values().stream()
                .anyMatch(controller -> controller.slot() == top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot.POST_SWING);
        boolean armsControlled = animationPlayer.getControllers().values().stream()
                .anyMatch(controller -> controller.slot() == top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot.FIRST_PERSON_ARM);
        if (mainControlled) {
            mainExcluded.addAll(YsmDefaultAnimationResolver.MAIN_ACTIONS);
        }
        var main = YsmDefaultAnimationResolver.resolve(modelKey, YsmDefaultAnimationResolver.Domain.MAIN,
                geometry.bones.keySet(), Map.of(), mainExcluded, Set.of(), true);
        var hands = YsmDefaultAnimationResolver.resolve(modelKey, YsmDefaultAnimationResolver.Domain.ARM,
                geometry.bones.keySet(), Map.of(), excluded, Set.of(), !handsControlled);
        var arms = YsmDefaultAnimationResolver.resolve(modelKey, YsmDefaultAnimationResolver.Domain.ARM,
                armGeometry == null ? geometry.bones.keySet() : armGeometry.bones.keySet(),
                Map.of(), excluded, Set.of(), !armsControlled);
        Map<String, YsmAnimationClip> effective = new LinkedHashMap<>(main.compile(engine));
        effective.putAll(hands.compile(engine));
        animationPlayer.registerDefaultAnimations(effective, arms.compile(engine));
        defaultAnimationSignature = YsmDefaultAnimationResolver.signature(main, hands, arms);
        for (var selection : List.of(main, hands, arms)) {
            for (var entry : selection.definitions().entrySet()) {
                if (!entry.getValue().missingBones().isEmpty() && defaultAnimationDiagnostics.size() < 32) {
                    defaultAnimationDiagnostics.add("DEFAULT_ANIMATION_UNMAPPED_BONES:" + entry.getKey()
                            + ":" + entry.getValue().missingBones().size());
                }
            }
        }
    }

    public String defaultAnimationTemplateVersion() {
        return defaultAnimationSignature.isEmpty() ? "" : YsmDefaultAnimationResolver.VERSION;
    }

    public String defaultAnimationSignature() {
        return defaultAnimationSignature;
    }

    public List<String> defaultAnimationDiagnostics() {
        return List.copyOf(defaultAnimationDiagnostics);
    }

    private static Map<String, byte[]> readTextureEntries(CompoundTag tag) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Tag textureTag : tag.getListOrEmpty("texture_entries")) {
            if (!(textureTag instanceof CompoundTag texture))
                continue;
            byte[] data = texture.getByteArray("data").orElse(new byte[0]);
            if (data.length == 0)
                continue;
            registerTextureKey(result, texture.getStringOr("id", ""), data);
            registerTextureKey(result, texture.getStringOr("path", ""), data);
        }
        byte[] fallback = tag.getByteArray("texture").orElse(new byte[0]);
        if (fallback.length > 0)
            registerTextureKey(result, tag.getStringOr("texture_id", "ysm_texture"), fallback);
        return result;
    }

    private static Map<String, MaterialData> readMaterialEntries(CompoundTag tag) {
        Map<String, MaterialData> result = new LinkedHashMap<>();
        for (Tag value : tag.getListOrEmpty("texture_entries")) {
            if (!(value instanceof CompoundTag texture)) {
                continue;
            }
            MaterialData material = new MaterialData(texture.getByteArray("normal_data").orElse(new byte[0]),
                    texture.getByteArray("specular_data").orElse(new byte[0]));
            registerTextureKey(result, texture.getStringOr("id", ""), material);
            registerTextureKey(result, texture.getStringOr("path", ""), material);
        }
        return result;
    }

    private record MaterialData(byte[] normal, byte[] specular) {
    }

    private static YsmMaterialTexture materialTexture(Avatar owner, String id, byte[] base, MaterialData material) {
        return new YsmMaterialTexture(owner, id, base, material == null ? new byte[0] : material.normal(),
                material == null ? new byte[0] : material.specular());
    }

    private static <T> void registerTextureKey(Map<String, T> result, String key, T data) {
        if (key == null || key.isBlank())
            return;
        result.putIfAbsent(key, data);
        result.putIfAbsent(key.toLowerCase(java.util.Locale.US), data);
        String normalized = key.replace('\\', '/');
        result.putIfAbsent(normalized, data);
        result.putIfAbsent(normalized.toLowerCase(java.util.Locale.US), data);
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        result.putIfAbsent(fileName, data);
        result.putIfAbsent(fileName.toLowerCase(java.util.Locale.US), data);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String stem = fileName.substring(0, dot);
            result.putIfAbsent(stem, data);
            result.putIfAbsent(stem.toLowerCase(java.util.Locale.US), data);
        }
    }

    private static Map<String, YsmAnimationClip> readAnimations(CompoundTag tag) {
        return readAnimations(tag, Avatar.getMolangEngine());
    }

    private static Map<String, YsmAnimationClip> readAnimations(CompoundTag tag, MolangEngine engine) {
        Map<String, YsmAnimationClip> result = new HashMap<>();
        for (Tag animationTag : tag.getListOrEmpty("animations")) {
            if (!(animationTag instanceof CompoundTag animation))
                continue;
            String json = new String(animation.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            if (json.isBlank())
                continue;
            mergeAnimations(result, YsmAnimationParser.parse(json, true, engine,
                    tag.getBooleanOr("merge_multiline_expr", false)));
        }
        return result;
    }

    private static void mergeAnimations(Map<String, YsmAnimationClip> target, Map<String, YsmAnimationClip> source) {
        if (source == null || source.isEmpty())
            return;
        for (Map.Entry<String, YsmAnimationClip> entry : source.entrySet()) {
            String name = entry.getKey();
            YsmAnimationClip incoming = entry.getValue();
            if (name == null || incoming == null)
                continue;
            YsmAnimationClip existing = target.get(name);
            if (existing == null || shouldReplaceAnimation(existing, incoming))
                target.put(name, incoming);
        }
    }

    private static boolean shouldReplaceAnimation(YsmAnimationClip existing, YsmAnimationClip incoming) {
        if (isEmptyAnimation(existing))
            return !isEmptyAnimation(incoming);
        if (isEmptyAnimation(incoming))
            return false;
        return animationWeight(incoming) > animationWeight(existing);
    }

    private static boolean isEmptyAnimation(YsmAnimationClip clip) {
        return clip == null || clip.boneAnimations.isEmpty() && clip.events.isEmpty();
    }

    private static int animationWeight(YsmAnimationClip clip) {
        if (clip == null)
            return 0;
        return clip.boneAnimations.size() * 2 + clip.events.size();
    }

    private static Map<String, YsmAnimationController> readAnimationControllers(CompoundTag tag) {
        return readAnimationControllers(tag, Avatar.getMolangEngine());
    }

    private static Map<String, YsmAnimationController> readAnimationControllers(CompoundTag tag, MolangEngine engine) {
        return readAnimationControllers(tag, engine, null);
    }

    private static Map<String, YsmAnimationController> readAnimationControllers(CompoundTag tag, MolangEngine engine,
            java.util.function.BiConsumer<String, YsmAnimationControllerParser.Diagnostic> diagnostics) {
        return readAnimationControllers(tag, engine, diagnostics, null);
    }

    private static Map<String, YsmAnimationController> readAnimationControllers(CompoundTag tag, MolangEngine engine,
            java.util.function.BiConsumer<String, YsmAnimationControllerParser.Diagnostic> diagnostics,
            Map<String, String> sources) {
        Map<String, YsmAnimationController> result = new LinkedHashMap<>();
        for (Tag controllerTag : tag.getListOrEmpty("animation_controllers")) {
            if (!(controllerTag instanceof CompoundTag controller))
                continue;
            String json = new String(controller.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            if (json.isBlank())
                continue;
            String path = controller.getStringOr("path", "animation_controllers");
            Map<String, YsmAnimationController> parsed = YsmAnimationControllerParser.parse(json, engine,
                    diagnostics == null ? null : diagnostic -> diagnostics.accept(path, diagnostic),
                    tag.getBooleanOr("merge_multiline_expr", false));
            result.putAll(parsed);
            if (sources != null) {
                parsed.keySet().forEach(name -> sources.put(name, path));
            }
        }
        return result;
    }

    static String controllerDiagnostic(String model, boolean encrypted, String path,
                                       YsmAnimationControllerParser.Diagnostic diagnostic) {
        if (encrypted) {
            return "Encrypted YSM operation failed: runtime/controller-parse";
        }
        return "YSM controller parse failed: model=" + model + " resource=" + path
                + " controller=" + diagnostic.controller() + " state=" + diagnostic.state()
                + " reason=" + diagnostic.message();
    }

    public String controllerSource(String controller) {
        return encryptedSource ? "" : controllerSources.getOrDefault(controller, "<runtime-controller>");
    }

    public void reportControllerExecutionFailure(Exception failure) {
        String message = encryptedSource ? "Encrypted YSM operation failed: runtime/controller-execution"
                : "YSM controller execution failed: " + functions.bindingDiagnosticContext()
                        + " reason=" + failure.getMessage();
        recordControllerDiagnostic(message);
    }

    private void recordControllerDiagnostic(String message) {
        if (controllerDiagnostics.size() < 16 && controllerDiagnostics.add(message)) {
            FiguraMod.LOGGER.warn(message);
        }
    }

    List<String> controllerDiagnosticMessages() {
        return List.copyOf(controllerDiagnostics);
    }

    private void buildParts() {
        for (YsmGeometry.Bone bone : geometry.bones.values())
            buildPart(bone, geometry, parts, boneRoles, locators);
        if (armGeometry != null) {
            for (YsmGeometry.Bone bone : armGeometry.bones.values())
                buildPart(bone, armGeometry, armParts, armBoneRoles, null);
        }
    }

    private YsmModelPart buildPart(YsmGeometry.Bone bone, YsmGeometry sourceGeometry, Map<String, YsmModelPart> targetParts, Map<String, YsmBoneRole> targetRoles, Map<String, YsmLocator> targetLocators) {
        YsmModelPart existing = targetParts.get(bone.name);
        if (existing != null)
            return existing;

        YsmGeometry.Bone parentBone = bone.parentName == null || bone.parentName.isBlank() ? null : sourceGeometry.bones.get(bone.parentName);
        YsmModelPart parent = parentBone == null ? null : buildPart(parentBone, sourceGeometry, targetParts, targetRoles, targetLocators);
        YsmModelPart part = new YsmModelPart(bone.name, parent, bone.pivot, bone.rotation);
        part.setDefaultVisible(bone.visible);
        YsmBoneRole role = YsmBoneMapper.roleOf(bone);
        targetRoles.put(bone.name, role);
        targetRoles.putIfAbsent(bone.name.toLowerCase(java.util.Locale.US), role);
        YsmLocator locator = YsmBoneMapper.locatorOf(bone);
        if (targetLocators != null && locator != null) {
            targetLocators.put(locator.name(), locator);
            targetLocators.putIfAbsent(locator.name().toLowerCase(java.util.Locale.US), locator);
        }
        targetParts.put(bone.name, part);
        targetParts.putIfAbsent(bone.name.toLowerCase(java.util.Locale.US), part);
        return part;
    }

    public Avatar owner() {
        return owner;
    }

    public YsmGeometry geometry() {
        return geometry;
    }

    public void recordComplexity(int faces) {
        complexityOwner.recordYsmComplexity(faces);
    }

    /** Detached GUI playback and physical sub-entities still draw against this original owner. */
    public Avatar renderBudgetOwner() {
        return complexityOwner;
    }

    public int outfitFaceAllowance() {
        recordComplexity(0);
        var budget = top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.current(complexityOwner);
        return budget == null ? Integer.MAX_VALUE : budget.allowance(complexityOwner, complexityOwner.complexity);
    }

    public boolean consumeOutfitFaces(int faces) {
        var budget = top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.current(complexityOwner);
        if (budget != null) {
            if (outfitFaceAllowance() < faces) {
                return false;
            }
            budget.charge(complexityOwner, complexityOwner.complexity, faces);
        }
        recordComplexity(faces);
        return true;
    }

    public YsmGeometry getArmGeometry() {
        return armGeometry;
    }

    public boolean hasArmModel() {
        return armGeometry != null;
    }

    public FiguraTexture texture() {
        return texture;
    }

    public String textureId() {
        return textureId;
    }

    public List<String> textureIds() {
        return textureData.keySet().stream().sorted().toList();
    }

    public boolean isGuiNoLighting() {
        return guiNoLighting;
    }

    public boolean isPreviewRotationDisabled() {
        return previewRotationDisabled;
    }

    public boolean isRenderLayersFirst() {
        return renderLayersFirst;
    }

    public boolean isAllCutout() {
        return allCutout;
    }

    public boolean isTextureTranslucent() {
        return textureTranslucent;
    }

    public boolean setTexture(String id) {
        if (id == null || id.isBlank())
            return false;
        byte[] data = textureData.get(id);
        if (data == null)
            data = textureData.get(id.toLowerCase(java.util.Locale.US));
        if (data == null)
            return false;
        if (id.equals(textureId))
            return true;
        MaterialData material = materialData.get(id);
        if (material == null) {
            material = materialData.get(id.toLowerCase(java.util.Locale.US));
        }
        FiguraTexture replacement = materialTexture(owner, id, data, material);
        FiguraTexture previous = texture;
        boolean newTextureTranslucent = geometry.applyTextureAlpha(data);
        if (armGeometry != null)
            newTextureTranslucent |= armGeometry.applyTextureAlpha(data);
        texture = replacement;
        textureId = id;
        textureTranslucent = newTextureTranslucent;
        if (ownsTexture) {
            previous.close();
        }
        ownsTexture = true;
        return true;
    }

    public String getKind() {
        return kind;
    }

    public String getModelKey() {
        return modelKey;
    }

    public String contentHash() {
        return contentHash;
    }

    public float widthScale() {
        Object value = owner.controls.getValue("ysm.width_scale");
        return value instanceof Number number ? sanitizeScale(number.floatValue()) : defaultWidthScale;
    }

    public float heightScale() {
        Object value = owner.controls.getValue("ysm.height_scale");
        return value instanceof Number number ? sanitizeScale(number.floatValue()) : defaultHeightScale;
    }

    public void applyModelScale(PoseStack stack) {
        stack.scale(
                0.9375f * widthScale(),
                0.9375f * heightScale(),
                0.9375f * widthScale()
        );
    }

    public YsmRenderer renderer() {
        return renderer;
    }

    public YsmBoneQueryRuntime boneQueries() {
        return boneQueries;
    }

    public YsmGuiResources guiResources() {
        return guiResources;
    }

    public AvatarTranslations translations() {
        return translations;
    }

    public YsmAudioResources audio() {
        return sharedAudio == null ? audio : sharedAudio;
    }

    public YsmSoundRuntime sounds() {
        return sounds;
    }

    public YsmParticleRuntime particles() {
        return particles;
    }

    public Iterable<YsmSubEntity> subEntities() {
        return new LinkedHashSet<>(subEntities.values());
    }

    public YsmSubEntity getSubEntity(String id) {
        if (id == null)
            return null;
        YsmSubEntity entity = subEntities.get(id);
        return entity != null ? entity : subEntities.get(id.toLowerCase(java.util.Locale.US));
    }

    public YsmSubEntity getSubEntity(String kind, String entityId) {
        if (entityId != null && !entityId.isBlank()) {
            YsmSubEntity direct = getSubEntity(kind + ":" + entityId);
            if (direct == null)
                direct = getSubEntity(entityId);
            if (direct != null && direct.matches(kind, entityId))
                return direct;
        }
        for (YsmSubEntity entity : new LinkedHashSet<>(subEntities.values())) {
            if (entity.matches(kind, entityId))
                return entity;
        }
        return null;
    }

    public YsmSubEntity getSubEntityForEntity(String kind, Entity entity) {
        if (entity == null) {
            return null;
        }
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        YsmSubEntity direct = getSubEntity(kind, typeId);
        if (direct != null) {
            return direct;
        }
        return new LinkedHashSet<>(subEntities.values()).stream()
                .filter(definition -> definition.matches(kind, entity)).findFirst().orElse(null);
    }

    public boolean ownsSubEntity(Entity entity, String kind) {
        Entity controller = "projectile".equals(kind) && entity instanceof Projectile projectile
                ? projectile.getOwner() : entity.getFirstPassenger();
        if (controller != null) {
            return owner.owner.equals(controller.getUUID());
        }
        return "vehicle".equals(kind) && YsmSubEntityInstances.vehicleOwner(entity) == this;
    }

    public YsmModelRuntime prepareSubEntity(Entity entity, String kind, String typeId, float partialTick) {
        if (closed || entity == null || entity.isRemoved() || !ownsSubEntity(entity, kind)) {
            return null;
        }
        try {
            YsmModelRuntime instance = subEntityInstances.acquire(entity, kind, typeId);
            if (instance != null) {
                instance.owner().renderMode = owner.renderMode;
                instance.updateSubEntity(entity, partialTick);
            }
            return instance;
        } catch (RuntimeException failure) {
            recordControllerDiagnostic(encryptedSource ? "Encrypted YSM operation failed: runtime/sub-entity"
                    : "YSM sub-entity runtime failed: " + kind + "/" + typeId + "/" + failure.getClass().getSimpleName());
            return null;
        }
    }

    public boolean renderSubEntity(PoseStack stack, MultiBufferSource bufferSource, int light, String kind, String entityId) {
        return renderer.renderSubEntity(stack, bufferSource, light, getSubEntity(kind, entityId));
    }

    public boolean renderSubEntity(PoseStack stack, MultiBufferSource bufferSource, int light,
                                   String kind, String typeId, Entity entity, float partialTick) {
        if (closed || entity == null || entity.isRemoved()) {
            return false;
        }
        try {
            YsmModelRuntime instance = prepareSubEntity(entity, kind, typeId, partialTick);
            if (instance == null) {
                return false;
            }
            return instance.renderer().render(stack, bufferSource, light, YsmPartMask.DEBUG_ALL,
                    instance.captureRenderPose(false));
        } catch (RuntimeException failure) {
            recordControllerDiagnostic(encryptedSource ? "Encrypted YSM operation failed: runtime/sub-entity"
                    : "YSM sub-entity runtime failed: " + kind + "/" + typeId + "/" + failure.getClass().getSimpleName());
            return false;
        }
    }

    public YsmSubEntityInstances subEntityInstances() {
        return subEntityInstances;
    }

    public boolean isSubEntityRuntime() {
        return subEntityRuntime;
    }

    /** Compiles instance-bound ASTs while borrowing the definition's immutable geometry and image. */
    public YsmModelRuntime createSubEntityRuntime(YsmSubEntity definition, Entity entity) {
        if (closed || definition == null || entity == null || entity.isRemoved()) {
            throw new IllegalStateException("Sub-entity requires a live model and entity");
        }
        YsmModelRuntime child = owner.createYsmEntityRuntime(entity, childOwner -> {
            YsmModelRuntime instance = new YsmModelRuntime(childOwner, definition.geometry(), null,
                    definition.texture(), definition.id(), definition.isTextureTranslucent(), definition.kind(),
                    modelKey, Map.of(), 1f, 1f, encryptedSource);
            instance.ownsTexture = false;
            instance.complexityOwner = complexityOwner;
            instance.subEntityRuntime = true;
            instance.sharedAudio = audio();
            instance.allCutout = allCutout;
            instance.mergeMultilineExpressions = mergeMultilineExpressions;
            try {
                MolangEngine engine = MolangEngine.fromCustomBinding(childOwner.getAvatarBindings());
                for (String json : definition.animationResources()) {
                    instance.animationPlayer.registerAnimations(
                            YsmAnimationParser.parse(json, true, engine, mergeMultilineExpressions));
                }
                for (String json : definition.controllerResources()) {
                    instance.animationPlayer.registerControllers(YsmAnimationControllerParser.parse(json, engine,
                            diagnostic -> instance.recordControllerDiagnostic(controllerDiagnostic(modelKey,
                                    encryptedSource, definition.controllerPath(), diagnostic)),
                            mergeMultilineExpressions));
                }
                instance.functions.load(functionInput);
                instance.animationPlayer.registerFunctionEventControllers(instance.functions.controllerEventNames());
                instance.animationPlayer.setNativeStateMachineEnabled(false);
                return instance;
            } catch (RuntimeException failure) {
                instance.close();
                throw failure;
            }
        });
        try {
            // Initial events must see the already attached detached execution owner.
            child.animationPlayer.startBaseAnimations();
            return child;
        } catch (RuntimeException failure) {
            child.close();
            throw failure;
        }
    }

    public void updateSubEntity(Entity entity, float partialTick) {
        if (closed || !subEntityRuntime || entity == null || entity.isRemoved()) {
            return;
        }
        long frame = YsmRenderFrameClock.currentFrame();
        if (subEntityQueryFrame != frame) {
            owner.getMolangContext().populateQueryContext(entity);
            subEntityQueryFrame = frame;
        }
        LivingEntityRenderState state = new LivingEntityRenderState();
        state.ageInTicks = entity.tickCount + partialTick;
        state.walkAnimationSpeed = (float) entity.getDeltaMovement().horizontalDistance();
        animationPlayer.update(state, entity instanceof LivingEntity living ? living : null, false);
    }

    public YsmAnimationPlayer animations() {
        return animationPlayer;
    }

    public YsmActionRuntime actions() {
        return actions;
    }

    /** Replays model controls after publication, so restored commands can resolve fn/ysm bindings. */
    public void restoreControls() {
        if (owner.getYsmRuntime() != this) {
            throw new IllegalStateException("YSM controls require an attached model runtime");
        }
        owner.controls.loadSavedValues(owner, modelKey);
        owner.controls.syncAll(owner);
    }

    public YsmMolangFunctionRuntime functions() {
        return functions;
    }

    public void installDefaultActionWheel(ActionWheelAPI wheel) {
        if (wheel == null || wheel.getCurrentPage() != null)
            return;

        Page root = wheel.newPage("YSM");
        root.newAction(1)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.settings").getString())
                .setItem("minecraft:comparator")
                .setControlsPage("root");

        Map<String, Page> pages = new LinkedHashMap<>();
        Map<String, Integer> slots = new LinkedHashMap<>();
        Map<String, Set<Integer>> occupied = new LinkedHashMap<>();
        pages.put("root", root);
        slots.put("root", 2);
        occupied.computeIfAbsent("root", ignored -> new LinkedHashSet<>()).add(1);
        for (String pageId : YsmActionWheelLayoutStore.pages(modelKey)) {
            if (pageId != null && !pageId.isBlank())
                pageFor(root, pages, slots, occupied, pageId);
        }

        for (YsmActionDefinition action : actions.all()) {
            if (!isExtraAnimation(action))
                continue;
            String pageId = action.getPage();
            if (pageId == null || pageId.isBlank() || "root".equals(normalize(pageId)))
                pageId = "extra_animation";
            YsmActionWheelLayoutStore.Entry layout = YsmActionWheelLayoutStore.get(modelKey, action.getId());
            if (layout != null && layout.page() != null && !layout.page().isBlank())
                pageId = layout.page();
            Page page = pageFor(root, pages, slots, occupied, pageId);
            int slot = nextSlot(slots, occupied, pageId, layout == null ? -1 : layout.slot());
            String animation = action.getAnimation();
            Action wheelAction = page.newAction(slot).setTitle(action.getTitle()).setTitleSupplier(action::getTitle);
            if (animation != null && animation.startsWith("#")) {
                wheelAction.setItem(action.getItem() == null ? "minecraft:comparator" : action.getItem()).setControlsPage(animation.substring(1));
            } else {
                wheelAction.setItem(action.getItem() == null ? "minecraft:armor_stand" : action.getItem()).setYsmAction(action.getId());
            }
        }
        wheel.setPage(root);
    }

    private static Page pageFor(Page root, Map<String, Page> pages, Map<String, Integer> slots, Map<String, Set<Integer>> occupied, String id) {
        String pageId = id == null || id.isBlank() ? "root" : id;
        Page existing = pages.get(pageId);
        if (existing != null)
            return existing;
        Page page = new Page("YSM: " + pageId);
        pages.put(pageId, page);
        slots.put(pageId, 2);
        occupied.computeIfAbsent(pageId, ignored -> new LinkedHashSet<>()).add(1);
        root.setAction(nextSlot(slots, occupied, "root", -1), new OpenPageAction(page, pageId));
        page.setAction(1, new BackAction(root));
        return page;
    }

    private static int nextSlot(Map<String, Integer> slots, Map<String, Set<Integer>> occupied, String page, int preferred) {
        String key = page == null || page.isBlank() ? "root" : page;
        Set<Integer> used = occupied.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        if (preferred > 0 && !used.contains(preferred)) {
            used.add(preferred);
            slots.put(key, Math.max(slots.getOrDefault(key, 1), preferred + 1));
            return preferred;
        }
        int slot = slots.getOrDefault(key, 1);
        while (used.contains(slot))
            slot++;
        used.add(slot);
        slots.put(key, slot + 1);
        return slot;
    }

    private static boolean isExtraAnimation(YsmActionDefinition action) {
        if (action == null)
            return false;
        return normalize(action.getId()).contains("extra_animation")
                || normalize(action.getPage()).contains("extra_animation")
                || normalize(action.getAnimation()).contains("extra_animation");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.US).replace('-', '_').replace(' ', '_');
    }

    private static class BackAction extends Action {
        private final Page root;

        private BackAction(Page root) {
            this.root = root;
            setTitleSupplier(() -> FiguraText.of("gui.controls.back").getString());
            setItem("minecraft:arrow");
        }

        @Override
        public void execute(Avatar avatar, boolean left) {
            if (left && avatar != null && avatar.luaRuntime != null)
                avatar.luaRuntime.action_wheel.setPage(root);
        }
    }

    private static class OpenPageAction extends Action {
        private final Page page;

        private OpenPageAction(Page page, String title) {
            this.page = page;
            setTitle(title == null || title.isBlank() ? FiguraText.of("gui.controls.extra_animation").getString() : title);
            setItem("minecraft:book");
        }

        @Override
        public void execute(Avatar avatar, boolean left) {
            if (left && avatar != null && avatar.luaRuntime != null)
                avatar.luaRuntime.action_wheel.setPage(page);
        }
    }

    public YsmModelPart getPart(String name) {
        if (name == null)
            return null;
        YsmModelPart part = parts.get(name);
        return part != null ? part : parts.get(name.toLowerCase(java.util.Locale.US));
    }

    public YsmModelPart getArmPart(String name) {
        if (name == null)
            return null;
        YsmModelPart part = armParts.get(name);
        if (part == null)
            part = armParts.get(name.toLowerCase(java.util.Locale.US));
        return part != null ? part : getPart(name);
    }

    public List<YsmModelPart> getAnimationParts(String name) {
        List<YsmModelPart> result = new ArrayList<>();
        addPart(result, parts, name);
        addPart(result, armParts, name);
        return result;
    }

    public List<YsmModelPart> getAnimationParts(String name, boolean firstPerson) {
        List<YsmModelPart> result = new ArrayList<>();
        addPart(result, firstPerson && hasArmModel() ? armParts : parts, name);
        return result;
    }

    private void readActionSchemas(CompoundTag tag) {
        for (Tag schemaTag : tag.getListOrEmpty("action_schemas")) {
            if (!(schemaTag instanceof CompoundTag schema))
                continue;
            String path = schema.getStringOr("path", "schema");
            String json = new String(schema.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            YsmActionSchemaParser.apply(this, path, json);
        }
    }

    private static final Pattern MOLANG_VARIABLE = Pattern.compile("(?<![A-Za-z0-9_])(?:v|variable)\\.([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)");
    private static final Pattern MOLANG_ASSIGNMENT = Pattern.compile("(?<![A-Za-z0-9_])(?:v|variable)\\.([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\s*(?<![!<>=])=(?!=)");

    private void registerDiscoveredControls(CompoundTag tag) {
        Set<String> reads = new LinkedHashSet<>();
        Set<String> writes = new LinkedHashSet<>();
        collectMolangVariables(tag.getListOrEmpty("action_schemas"), reads, writes);
        collectMolangVariables(tag.getListOrEmpty("animation_controllers"), reads, writes);
        collectMolangVariables(tag.getListOrEmpty("animations"), reads, writes);

        Set<String> existingBindings = new LinkedHashSet<>();
        for (AvatarControlDefinition control : owner.controls.all()) {
            String binding = control.binding();
            if (binding != null && !binding.isBlank())
                existingBindings.add(normalizeMolangBinding(binding));
        }

        boolean headerAdded = false;
        for (String variable : reads) {
            if (!isDiscoverableControlVariable(variable, writes))
                continue;
            String binding = "v." + variable;
            if (existingBindings.contains(normalizeMolangBinding(binding)))
                continue;
            if (!headerAdded) {
                owner.controls.register(new AvatarControlDefinition("ysm.discovered.header", AvatarControlType.LABEL)
                        .setTitleSupplier(() -> FiguraText.of("gui.controls.discovered").getString())
                        .setPage("ysm_discovered"));
                headerAdded = true;
            }
            owner.controls.register(new AvatarControlDefinition("ysm.discovered." + controlId(variable), AvatarControlType.TOGGLE)
                    .setTitle(titleFromVariable(variable))
                    .setPage("ysm_discovered")
                    .setBinding(binding));
            existingBindings.add(normalizeMolangBinding(binding));
        }
    }

    private static void collectMolangVariables(Iterable<Tag> tags, Set<String> reads, Set<String> writes) {
        for (Tag entryTag : tags) {
            if (!(entryTag instanceof CompoundTag entry))
                continue;
            String json = new String(entry.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            if (json.isBlank())
                continue;
            Matcher readMatcher = MOLANG_VARIABLE.matcher(json);
            while (readMatcher.find())
                reads.add(readMatcher.group(1));
            Matcher writeMatcher = MOLANG_ASSIGNMENT.matcher(json);
            while (writeMatcher.find())
                writes.add(writeMatcher.group(1));
        }
    }

    private static boolean isDiscoverableControlVariable(String variable, Set<String> writes) {
        if (variable == null || variable.isBlank())
            return false;
        String lower = variable.toLowerCase(java.util.Locale.US);
        if (!lower.startsWith("roaming."))
            return false;
        if (lower.endsWith("_state") || lower.contains("_state."))
            return false;
        return !writes.contains(variable);
    }

    private static String normalizeMolangBinding(String binding) {
        String normalized = binding.trim();
        if (normalized.startsWith("variable."))
            normalized = "v." + normalized.substring("variable.".length());
        return normalized.toLowerCase(java.util.Locale.US);
    }

    private static String controlId(String variable) {
        return variable.replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("_+", "_");
    }

    private static String titleFromVariable(String variable) {
        String value = variable;
        if (value.startsWith("roaming."))
            value = value.substring("roaming.".length());
        StringBuilder builder = new StringBuilder(value.length());
        boolean nextUpper = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' || c == '.' || c == '-') {
                builder.append(' ');
                nextUpper = true;
            } else if (nextUpper) {
                builder.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }

    private static void addPart(List<YsmModelPart> result, Map<String, YsmModelPart> source, String name) {
        if (name == null)
            return;
        YsmModelPart part = source.get(name);
        if (part == null)
            part = source.get(name.toLowerCase(java.util.Locale.US));
        if (part != null && !result.contains(part))
            result.add(part);
    }

    public Map<String, YsmModelPart> parts() {
        return parts;
    }

    public Iterable<YsmModelPart> uniqueParts() {
        LinkedHashSet<YsmModelPart> result = new LinkedHashSet<>(parts.values());
        result.addAll(armParts.values());
        return result;
    }

    public Iterable<YsmModelPart> uniqueParts(boolean firstPerson) {
        return new LinkedHashSet<>((firstPerson && hasArmModel() ? armParts : parts).values());
    }

    public List<YsmModelPart> rootParts() {
        List<YsmModelPart> result = new ArrayList<>();
        for (YsmGeometry.Bone root : geometry.roots) {
            YsmModelPart part = getPart(root.name);
            if (part != null)
                result.add(part);
        }
        return result;
    }

    public YsmBoneRole roleOf(String boneName) {
        if (boneName == null)
            return YsmBoneRole.UNKNOWN;
        YsmBoneRole role = boneRoles.get(boneName);
        if (role != null)
            return role;
        role = boneRoles.get(boneName.toLowerCase(java.util.Locale.US));
        return role != null ? role : YsmBoneMapper.roleOfName(boneName);
    }

    public YsmBoneRole armRoleOf(String boneName) {
        if (boneName == null)
            return YsmBoneRole.UNKNOWN;
        YsmBoneRole role = armBoneRoles.get(boneName);
        if (role != null)
            return role;
        role = armBoneRoles.get(boneName.toLowerCase(java.util.Locale.US));
        return role != null ? role : roleOf(boneName);
    }

    public YsmLocator getLocator(String name) {
        if (name == null)
            return null;
        YsmLocator locator = locators.get(name);
        return locator != null ? locator : locators.get(name.toLowerCase(java.util.Locale.US));
    }

    public Iterable<YsmLocator> locators() {
        return new LinkedHashSet<>(locators.values());
    }

    public FiguraMat4 getBoneWorldMatrix(String boneName) {
        YsmModelPart part = getPart(boneName);
        return part == null ? FiguraMat4.of() : part.getWorldMatrix();
    }

    public FiguraVec3 getBoneWorldPos(String boneName) {
        YsmModelPart part = getPart(boneName);
        return part == null ? FiguraVec3.of() : part.getWorldPos();
    }

    public FiguraMat4 getLocatorWorldMatrix(String name) {
        YsmLocator locator = getLocator(name);
        return locator == null ? FiguraMat4.of() : getLocatorBoneWorldMatrix(locator.boneName());
    }

    public FiguraVec3 getLocatorWorldPos(String name) {
        YsmLocator locator = getLocator(name);
        return locator == null ? FiguraVec3.of() : getLocatorWorldMatrix(name).apply(0d, 0d, 0d);
    }

    public YsmAttachmentPoint getAttachmentPoint(String name) {
        if (name == null)
            return null;
        YsmAttachmentPoint point = attachmentPoints.get(name);
        return point != null ? point : attachmentPoints.get(name.toLowerCase(java.util.Locale.US));
    }

    /**
     * Selects the canonical YSM hand locator before falling back to the
     * role-derived attachment point. Exporters often append numeric suffixes
     * to alternate/parallel hand rigs; their JSON order is not a hand-anchor
     * priority signal.
     */
    public YsmAttachmentPoint getHandAttachmentPoint(boolean left) {
        String canonicalName = left ? "LeftHandLocator" : "RightHandLocator";
        for (YsmLocator locator : new LinkedHashSet<>(locators.values())) {
            if (canonicalName.equalsIgnoreCase(locator.name()))
                return getAttachmentPoint(locator.name());
        }
        return getAttachmentPoint(left ? "left_hand" : "right_hand");
    }

    public Iterable<YsmAttachmentPoint> attachmentPoints() {
        return new LinkedHashSet<>(attachmentPoints.values());
    }

    public FiguraMat4 getAttachmentWorldMatrix(String name) {
        YsmAttachmentPoint point = getAttachmentPoint(name);
        if (point == null)
            return FiguraMat4.of();
        return point.locator() == null ? getBoneWorldMatrix(point.boneName()) : getLocatorBoneWorldMatrix(point.boneName());
    }

    public FiguraVec3 getAttachmentWorldPos(String name) {
        YsmAttachmentPoint point = getAttachmentPoint(name);
        return point == null ? FiguraVec3.of() : getAttachmentWorldMatrix(name).apply(0d, 0d, 0d);
    }

    public boolean isBoneVisibleInHierarchy(String boneName) {
        if (boneName == null || boneName.isBlank())
            return false;
        YsmGeometry.Bone bone = getBoneIgnoreCase(boneName);
        while (bone != null) {
            YsmModelPart part = getPart(bone.name);
            if (part != null && (!part.visibleRaw() || part.hiddenScaleRaw()))
                return false;
            if (bone.parentName == null || bone.parentName.isBlank())
                return true;
            bone = getBoneIgnoreCase(bone.parentName);
        }
        return true;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isPreviewRuntime() {
        return previewRuntime;
    }

    /** Selects a detached default-pose preview, leaving world playback untouched. */
    public synchronized YsmModelRuntime forRenderPass() {
        if (closed || previewRuntime || previewInput == null
                || owner.renderMode != top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI) {
            return this;
        }
        Map<String, Object> controls = new LinkedHashMap<>();
        owner.controls.all().forEach(control -> controls.put(control.id(), control.getValue()));
        if (!java.util.Objects.equals(controls, previewControls)) {
            if (preview != null) {
                preview.close();
                preview = null;
            }
            previewControls = controls;
            previewAttempted = false;
        }
        if (!previewAttempted) {
            previewAttempted = true;
            try {
                preview = createPreview(controls);
                previewSourceTexture = texture;
            } catch (java.io.IOException | RuntimeException failure) {
                if (preview != null) {
                    preview.close();
                    preview = null;
                }
                if (encryptedSource) {
                    FiguraMod.LOGGER.warn("Encrypted YSM operation failed: runtime/preview-default-pose");
                } else {
                    FiguraMod.LOGGER.warn("YSM default-pose preview could not be initialized", failure);
                }
            }
        }
        if (preview != null && previewSourceTexture != texture) {
            if (preview.ownsTexture) {
                preview.texture.close();
            }
            preview.texture = texture;
            preview.textureId = textureId;
            preview.textureTranslucent = textureTranslucent;
            preview.ownsTexture = false;
            preview.materialData = materialData;
            byte[] pixels = textureData.get(textureId);
            if (pixels != null) {
                preview.geometry.applyTextureAlpha(pixels);
                if (preview.armGeometry != null) {
                    preview.armGeometry.applyTextureAlpha(pixels);
                }
            }
            previewSourceTexture = texture;
        }
        return preview;
    }

    private YsmModelRuntime createPreview(Map<String, Object> controls) throws java.io.IOException {
        // Binary sources already contain baked faces and intentionally have no model JSON.
        YsmGeometry previewGeometry = YsmModelData.copyGeometry(geometry);
        YsmGeometry previewArms = armGeometry == null ? null : YsmModelData.copyGeometry(armGeometry);
        byte[] pixels = textureData.get(textureId);
        if (pixels != null) {
            previewGeometry.applyTextureAlpha(pixels);
            if (previewArms != null) {
                previewArms.applyTextureAlpha(pixels);
            }
        }
        return owner.createYsmPreviewRuntime(previewOwner -> {
            var result = new YsmModelRuntime(previewOwner, previewGeometry, previewArms, texture, textureId,
                    textureTranslucent, kind, modelKey, textureData, defaultWidthScale, defaultHeightScale, encryptedSource);
            result.previewRuntime = true;
            result.complexityOwner = complexityOwner;
            result.ownsTexture = false;
            result.materialData = materialData;
            result.guiNoLighting = guiNoLighting;
            result.allCutout = allCutout;
            result.renderLayersFirst = renderLayersFirst;
            result.previewRotationDisabled = previewRotationDisabled;
            MolangEngine engine = MolangEngine.fromCustomBinding(previewOwner.getAvatarBindings());
            result.animationPlayer.registerAnimations(readAnimations(previewInput, engine));
            result.animationPlayer.registerControllers(readAnimationControllers(previewInput, engine,
                    (path, diagnostic) -> result.recordControllerDiagnostic(controllerDiagnostic(
                            result.modelKey, result.encryptedSource, path, diagnostic)), result.controllerSources));
            result.functions.load(previewInput.getListOrEmpty("functions"));
            result.animationPlayer.registerFunctionEventControllers(result.functions.controllerEventNames());
            if (!defaultAnimationSignature.isEmpty()) {
                result.loadDefaultAnimations(previewInput, engine);
            }
            result.animationPlayer.startBaseAnimations();
            result.registerDefaultControls();
            result.readActionSchemas(previewInput);
            for (var control : previewOwner.controls.all()) {
                if (controls.containsKey(control.id())) {
                    control.setValue(controls.get(control.id()));
                }
            }
            return result;
        });
    }

    public void updateAnimations(LivingEntityRenderState state, LivingEntity entity) {
        updateAnimations(state, entity, false);
    }

    public void updateAnimations(LivingEntityRenderState state, LivingEntity entity, boolean firstPerson) {
        if (closed) {
            return;
        }
        Object nativeState = owner.controls.getValue("ysm.native_state_machine");
        if (nativeState instanceof Boolean enabled) {
            animationPlayer.setNativeStateMachineEnabled(enabled);
        }
        animationPlayer.update(state, entity, firstPerson);
    }

    private void registerDefaultControls() {
        owner.controls.register(new AvatarControlDefinition("ysm.header", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.ysm_runtime").getString())
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.native_state_machine", AvatarControlType.TOGGLE)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.native_state_machine").getString())
                .setDefault(true)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.action_loop", AvatarControlType.TOGGLE)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.action_loop").getString())
                .setDefault(false)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.action_speed", AvatarControlType.SLIDER)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.action_speed").getString())
                .setRange(0.1d, 3.0d, 0.1d)
                .setDefault(1.0d)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.width_scale", AvatarControlType.SLIDER)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.width_scale").getString())
                .setRange(0.1d, 5.0d, 0.05d)
                .setDefault((double) defaultWidthScale)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.height_scale", AvatarControlType.SLIDER)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.height_scale").getString())
                .setRange(0.1d, 5.0d, 0.05d)
                .setDefault((double) defaultHeightScale)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.header", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.ysm_debug").getString())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.functions", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.functions_events", functions.functionCount(), functions.eventCount()).getString())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.function_names", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.functions", debugPreview(functions.functionDebugNames())).getString())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.event_names", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.events", debugPreview(new ArrayList<>(functions.eventDebugNames().keySet()))).getString())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.recent_events", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.recent", debugPreview(functions.recentEvents())).getString())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.sync_keys", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.sync", debugPreview(functions.recentSyncKeys())).getString())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.call_stack", AvatarControlType.LABEL)
                .setTitleSupplier(() -> FiguraText.of("gui.controls.stack", debugPreview(functions.callStackDebugNames())).getString())
                .setPage("ysm_debug"));
    }

    private static String debugPreview(List<String> values) {
        if (values == null || values.isEmpty())
            return "-";
        int limit = Math.min(4, values.size());
        String joined = String.join(", ", values.subList(0, limit));
        return values.size() > limit ? joined + ", +" + (values.size() - limit) : joined;
    }

    private static float sanitizeScale(float value) {
        return Float.isFinite(value) && value > 0f ? value : 1f;
    }

    public void updateWorldMatrices(PoseStack stack) {
        updateWorldMatrices(stack, null);
    }

    public void updateWorldMatrices(PoseStack stack, YsmRenderPose pose) {
        if (stack == null)
            return;
        locatorWorldMatrices.clear();
        for (YsmGeometry.Bone root : geometry.roots)
            updateWorldMatrix(root, stack, pose);
    }

    public YsmRenderPose captureRenderPose(boolean firstPerson) {
        PoseStack scale = new PoseStack();
        if (!firstPerson) {
            applyModelScale(scale);
        }
        boolean unlit = guiNoLighting && owner.renderMode == top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
        var lease = AccelerationService.INSTANCE.capture(
                owner.accelerationOwner(), String.valueOf(owner.renderMode));
        return YsmRenderPose.capture(uniqueParts(firstPerson), scale.last().pose(), unlit, lease);
    }

    /** Current body pivots in native block space, without overwriting world-space Lua queries. */
    public Map<YsmModelPart, Matrix4f> captureBonePivots(YsmRenderPose pose) {
        if (closed || pose != null && !pose.isCurrent()) {
            throw new IllegalStateException("YSM_POSE_EXPIRED");
        }
        PoseStack stack = new PoseStack();
        if (pose == null) {
            applyModelScale(stack);
        } else {
            pose.applyModelScale(stack);
        }
        Map<YsmModelPart, Matrix4f> matrices = new IdentityHashMap<>();
        for (YsmGeometry.Bone root : geometry.roots) {
            captureBonePivots(root, stack, pose, matrices);
        }
        return matrices;
    }

    /** First-person native geometry has its own pivots and deliberately omits body model scaling. */
    public Map<YsmModelPart, Matrix4f> captureFirstPersonPivots(boolean left, YsmRenderPose pose) {
        if (closed || pose != null && !pose.isCurrent()) {
            throw new IllegalStateException("YSM_POSE_EXPIRED");
        }
        boolean armParts = hasArmModel();
        YsmGeometry source = armParts ? armGeometry : geometry;
        Map<YsmModelPart, Matrix4f> matrices = new IdentityHashMap<>();
        PoseStack stack = new PoseStack();
        for (YsmGeometry.Bone root : source.roots) {
            YsmBoneRole role = armParts ? armRoleOf(root.name) : roleOf(root.name);
            if (YsmBoneMapper.isFirstPersonArmCandidate(root.name, role, left)) {
                captureFirstPersonPivots(root, stack, pose, armParts, left, false, matrices);
            }
        }
        return matrices;
    }

    private void captureFirstPersonPivots(YsmGeometry.Bone bone, PoseStack stack, YsmRenderPose pose,
                                          boolean armParts, boolean left, boolean selected,
                                          Map<YsmModelPart, Matrix4f> matrices) {
        YsmBoneRole role = armParts ? armRoleOf(bone.name) : roleOf(bone.name);
        YsmBoneRole ownHand = left ? YsmBoneRole.LEFT_HAND : YsmBoneRole.RIGHT_HAND;
        YsmBoneRole otherHand = left ? YsmBoneRole.RIGHT_HAND : YsmBoneRole.LEFT_HAND;
        if (role == otherHand) {
            return;
        }
        selected = selected || role == ownHand || role == YsmBoneRole.FIRST_PERSON_ARM;
        YsmModelPart part = pose == null ? (armParts ? getArmPart(bone.name) : getPart(bone.name))
                : pose.part(bone.name);
        // Bind only an existing body bone of the same name. An unrelated arm-only bone is unresolved.
        YsmModelPart bodyPart = getPart(bone.name);
        if (selected && bodyPart != null) {
            stack.pushPose();
            try {
                applyBoneTransform(stack, bone, true, part);
                matrices.put(bodyPart, new Matrix4f(stack.last().pose()));
            } finally {
                stack.popPose();
            }
        }
        stack.pushPose();
        try {
            applyBoneTransform(stack, bone, false, part);
            for (YsmGeometry.Bone child : bone.children) {
                captureFirstPersonPivots(child, stack, pose, armParts, left, selected, matrices);
            }
        } finally {
            stack.popPose();
        }
    }

    private void captureBonePivots(YsmGeometry.Bone bone, PoseStack stack, YsmRenderPose pose,
                                    Map<YsmModelPart, Matrix4f> matrices) {
        YsmModelPart part = getPart(bone.name);
        YsmModelPart transformPart = pose == null ? part : pose.part(bone.name);
        stack.pushPose();
        try {
            applyBoneTransform(stack, bone, true, transformPart);
            if (part != null) {
                matrices.put(part, new Matrix4f(stack.last().pose()));
            }
        } finally {
            stack.popPose();
        }
        stack.pushPose();
        try {
            applyBoneTransform(stack, bone, false, transformPart);
            for (YsmGeometry.Bone child : bone.children) {
                captureBonePivots(child, stack, pose, matrices);
            }
        } finally {
            stack.popPose();
        }
    }

    public boolean renderFirstPersonArm(PoseStack stack, MultiBufferSource bufferSource, int light, boolean left) {
        return renderer.renderFirstPersonArm(stack, bufferSource, light, left);
    }

    public boolean renderFirstPersonArm(PoseStack stack, MultiBufferSource bufferSource, int light,
                                        boolean left, YsmRenderPose pose) {
        return renderer.renderFirstPersonArm(stack, bufferSource, light, left, pose);
    }

    public boolean applyAttachmentTransform(PoseStack stack, String attachmentName) {
        YsmAttachmentPoint point = getAttachmentPoint(attachmentName);
        YsmGeometry.Bone bone = findAttachmentBone(point);
        if (bone == null)
            return false;
        if (!isBoneVisibleInHierarchy(bone.name))
            return false;
        stack.scale(0.9375f, 0.9375f, 0.9375f);
        applyBoneChain(stack, bone, point.locator() != null);
        return true;
    }

    public boolean applyHandItemTransform(PoseStack stack, boolean left) {
        return applyHandItemTransform(stack, null, ItemStack.EMPTY, left);
    }

    public boolean applyHandItemTransform(PoseStack stack, LivingEntity entity, ItemStack itemStack, boolean left) {
        YsmAttachmentPoint point = handAttachment(itemStack, left);
        YsmGeometry.Bone bone = findAttachmentBone(point);
        boolean locator = point != null && point.locator() != null;
        if (bone == null) {
            bone = findHandBone(left);
        }
        if (bone != null && !isBoneVisibleInHierarchy(bone.name)) {
            return false;
        }
        applyModelScale(stack);
        if (bone == null) {
            stack.translate(left ? -0.42d : 0.42d, 0.78d, 0d);
        } else {
            if (!locator)
                locator = bone.name.toLowerCase(java.util.Locale.US).contains("locator");
            applyBoneChain(stack, bone, locator);
        }
        YsmHeldItemTransforms.apply(stack, entity, itemStack, left, !locator);
        return true;
    }

    private YsmAttachmentPoint handAttachment(ItemStack itemStack, boolean left) {
        return getHandAttachmentPoint(left);
    }

    @Override
    public synchronized void close() {
        closed = true;
        owner.accelerationOwner().retire();
        renderer.clearAccelerationMeshes();
        subEntityInstances.close();
        for (Tag value : functionInput) {
            if (value instanceof CompoundTag entry) {
                entry.getByteArray("data").ifPresent(bytes -> java.util.Arrays.fill(bytes, (byte) 0));
            }
        }
        functionInput.clear();
        sharedAudio = null;
        if (preview != null) {
            preview.close();
            preview = null;
        }
        if (previewInput != null) {
            for (String key : List.of("main_model", "arm_model")) {
                previewInput.getByteArray(key).ifPresent(bytes -> java.util.Arrays.fill(bytes, (byte) 0));
            }
            for (String key : List.of("animations", "animation_controllers", "functions", "action_schemas")) {
                for (Tag value : previewInput.getListOrEmpty(key)) {
                    if (value instanceof CompoundTag entry) {
                        entry.getByteArray("data").ifPresent(bytes -> java.util.Arrays.fill(bytes, (byte) 0));
                    }
                }
            }
            previewInput = null;
        }
        previewControls = null;
        previewSourceTexture = null;
        actions.close();
        controllerSources.clear();
        controllerDiagnostics.clear();
        particles.close();
        sounds.close();
        boneQueries.clear();
        animationPlayer.closeSounds();
        audio.close();
        translations.close();
        guiResources.close();
        materialData = Map.of();
        animationPlayer.clearControllerFunctionTransitions();
        functions.clearEventState();
        Avatar.MolangContext context = owner.getMolangContext();
        if (context != null) {
            if (subEntityRuntime) {
                context.entity = null;
            }
            context.updateYsmPositionContext(null);
            context.updateYsmMovementContext(Float.NaN);
            YsmHandQuery.capture(context);
        }
        if (ownsTexture) {
            texture.close();
        }
        for (YsmSubEntity entity : new LinkedHashSet<>(subEntities.values())) {
            entity.close();
        }
        subEntities.clear();
    }

    private void readSubEntities(CompoundTag tag, byte[] fallbackTexture, YsmModelData data) {
        for (Tag entityTag : tag.getListOrEmpty("sub_entities")) {
            if (!(entityTag instanceof CompoundTag subTag))
                continue;
            try {
                YsmGeometry direct = data == null ? null : data.geometry(subTag.getStringOr("model_path", ""));
                addSubEntity(direct == null ? YsmSubEntity.fromNbt(owner, subTag, fallbackTexture)
                        : YsmSubEntity.fromGeometry(owner, subTag, fallbackTexture, direct));
            } catch (Exception e) {
                if (encryptedSource) {
                    FiguraMod.LOGGER.warn("Encrypted YSM operation failed: runtime/subentity");
                } else {
                    FiguraMod.LOGGER.warn("Failed to load YSM sub entity {} from {}", subTag.getStringOr("id", "sub_entity"), modelKey, e);
                }
            }
        }
    }

    private void addSubEntity(YsmSubEntity entity) {
        if (entity == null)
            return;
        registerSubEntityKey(entity.id(), entity);
        registerSubEntityKey(entity.kind() + ":" + entity.id(), entity);
        for (String matchId : entity.matchIds()) {
            registerSubEntityKey(matchId, entity);
            registerSubEntityKey(entity.kind() + ":" + matchId, entity);
        }
    }

    private void registerSubEntityKey(String key, YsmSubEntity entity) {
        if (key == null || key.isBlank())
            return;
        subEntities.putIfAbsent(key, entity);
        subEntities.putIfAbsent(key.toLowerCase(java.util.Locale.US), entity);
        if (!key.startsWith("minecraft:"))
            subEntities.putIfAbsent("minecraft:" + key.toLowerCase(java.util.Locale.US), entity);
    }

    private YsmGeometry.Bone findHandBone(boolean left) {
        YsmGeometry.Bone locator = findHandLocator(left);
        if (locator != null)
            return locator;

        String side = left ? "Left" : "Right";
        for (String name : List.of(side + "HandLocator", side + "Item", side + "Hand", side + "Palm", side + "Wrist", side + "ForeArm", side + "LowerArm", side + "Arm")) {
            YsmGeometry.Bone bone = getBoneIgnoreCase(name);
            if (bone != null)
                return bone;
        }
        return null;
    }

    private YsmGeometry.Bone findHandLocator(boolean left) {
        YsmBoneRole role = left ? YsmBoneRole.LEFT_HAND : YsmBoneRole.RIGHT_HAND;
        for (YsmLocator locator : new LinkedHashSet<>(locators.values())) {
            if (locator.role() == role && locator.leftSide() == left) {
                YsmGeometry.Bone bone = geometry.bones.get(locator.boneName());
                if (bone != null)
                    return bone;
            }
        }
        return null;
    }

    private YsmGeometry.Bone findAttachmentBone(YsmAttachmentPoint point) {
        if (point == null || point.boneName() == null || point.boneName().isBlank())
            return null;
        YsmGeometry.Bone bone = geometry.bones.get(point.boneName());
        if (bone != null)
            return bone;
        return getBoneIgnoreCase(point.boneName());
    }

    private void buildAttachmentPoints() {
        for (YsmLocator locator : new LinkedHashSet<>(locators.values())) {
            YsmModelPart part = getPart(locator.boneName());
            YsmAttachmentType type = YsmAttachmentType.fromRole(locator.role(), locator.leftSide());
            addAttachmentPoint(new YsmAttachmentPoint(
                    locator.name(),
                    locator.boneName(),
                    type,
                    locator.role(),
                    locator.leftSide(),
                    locator.defaultItemTransform(),
                    locator,
                    part
            ));
        }

        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            YsmBoneRole role = roleOf(bone.name);
            YsmAttachmentType type = attachmentTypeForFallback(role);
            if (type == YsmAttachmentType.UNKNOWN || hasAttachmentFor(type, role))
                continue;
            boolean left = role == YsmBoneRole.LEFT_HAND;
            addAttachmentPoint(new YsmAttachmentPoint(
                    bone.name,
                    bone.name,
                    type,
                    role,
                    left,
                    defaultItemTransform(type),
                    null,
                    getPart(bone.name)
            ));
        }
    }

    private void addAttachmentPoint(YsmAttachmentPoint point) {
        if (point == null || point.name() == null || point.name().isBlank())
            return;
        attachmentPoints.put(point.name(), point);
        attachmentPoints.putIfAbsent(point.name().toLowerCase(java.util.Locale.US), point);
        attachmentPoints.putIfAbsent(point.type().name().toLowerCase(java.util.Locale.US), point);
        attachmentPoints.putIfAbsent(point.role().name().toLowerCase(java.util.Locale.US), point);
    }

    public boolean encryptedSource() {
        return encryptedSource;
    }

    private void dumpDebugInfo() {
        if (encryptedSource || !FiguraMod.debugModeEnabled())
            return;
        YsmModelValidator.ModelStats mainStats = YsmModelValidator.validate("runtime/main", geometry).stats();
        YsmModelValidator.ModelStats armStats = armGeometry == null ? YsmModelValidator.ModelStats.empty() : YsmModelValidator.validate("runtime/arm", armGeometry).stats();
        FiguraMod.debug("YSM runtime kind={} texture={} main={} bones/{} cubes/{} quads, arm={} bones/{} cubes, locators={}, attachments={}",
                kind,
                textureId,
                mainStats.boneCount(),
                mainStats.cubeCount(),
                mainStats.quadCount(),
                armStats.boneCount(),
                armStats.cubeCount(),
                new LinkedHashSet<>(locators.values()).size(),
                new LinkedHashSet<>(attachmentPoints.values()).size());
    }

    private boolean hasAttachmentFor(YsmAttachmentType type, YsmBoneRole role) {
        for (YsmAttachmentPoint point : new LinkedHashSet<>(attachmentPoints.values())) {
            if (point.type() == type || point.role() == role)
                return true;
        }
        return false;
    }

    private static YsmAttachmentType attachmentTypeForFallback(YsmBoneRole role) {
        return switch (role) {
            case LEFT_HAND -> YsmAttachmentType.LEFT_HAND;
            case RIGHT_HAND -> YsmAttachmentType.RIGHT_HAND;
            case BACKPACK -> YsmAttachmentType.BACKPACK;
            case BLADE -> YsmAttachmentType.BLADE;
            case SHEATH -> YsmAttachmentType.SHEATH;
            case ELYTRA -> YsmAttachmentType.ELYTRA;
            case HEAD -> YsmAttachmentType.HEAD;
            case HELMET -> YsmAttachmentType.HELMET;
            default -> YsmAttachmentType.UNKNOWN;
        };
    }

    private static String defaultItemTransform(YsmAttachmentType type) {
        return switch (type) {
            case LEFT_HAND -> "third_person_left_hand";
            case RIGHT_HAND -> "third_person_right_hand";
            case BACKPACK -> "backpack";
            case BLADE -> "blade";
            case SHEATH -> "sheath";
            case ELYTRA -> "elytra";
            case HEAD, HELMET -> "head";
            default -> "";
        };
    }

    private YsmGeometry.Bone getBoneIgnoreCase(String name) {
        YsmGeometry.Bone exact = geometry.bones.get(name);
        if (exact != null)
            return exact;
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            if (bone.name.equalsIgnoreCase(name))
                return bone;
        }
        return null;
    }

    private void applyBoneChain(PoseStack stack, YsmGeometry.Bone bone) {
        applyBoneChain(stack, bone, false);
    }

    private void updateWorldMatrix(YsmGeometry.Bone bone, PoseStack stack, YsmRenderPose pose) {
        YsmModelPart part = getPart(bone.name);
        YsmModelPart transformPart = pose == null ? part : pose.part(bone.name);
        stack.pushPose();
        applyBoneTransform(stack, bone, true, transformPart);
        locatorWorldMatrices.put(bone.name, FiguraMat4.of().set(stack.last().pose()));
        locatorWorldMatrices.putIfAbsent(bone.name.toLowerCase(java.util.Locale.US), locatorWorldMatrices.get(bone.name));
        stack.popPose();

        stack.pushPose();
        applyBoneTransform(stack, bone, false, transformPart);
        if (part != null)
            part.setWorldMatrix(FiguraMat4.of().set(stack.last().pose()));
        for (YsmGeometry.Bone child : bone.children)
            updateWorldMatrix(child, stack, pose);
        stack.popPose();
    }

    private FiguraMat4 getLocatorBoneWorldMatrix(String boneName) {
        if (boneName == null)
            return FiguraMat4.of();
        FiguraMat4 matrix = locatorWorldMatrices.get(boneName);
        if (matrix == null)
            matrix = locatorWorldMatrices.get(boneName.toLowerCase(java.util.Locale.US));
        return matrix == null ? getBoneWorldMatrix(boneName) : matrix.copy();
    }

    private void applyBoneChain(PoseStack stack, YsmGeometry.Bone bone, boolean locatorMode) {
        List<YsmGeometry.Bone> chain = new ArrayList<>();
        for (YsmGeometry.Bone current = bone; current != null; current = current.parentName == null ? null : geometry.bones.get(current.parentName))
            chain.add(0, current);
        for (int i = 0; i < chain.size(); i++)
            applyBoneTransform(stack, chain.get(i), locatorMode && i == chain.size() - 1);
    }

    private void applyBoneTransform(PoseStack stack, YsmGeometry.Bone bone) {
        applyBoneTransform(stack, bone, false);
    }

    private void applyBoneTransform(PoseStack stack, YsmGeometry.Bone bone, boolean stopAtPivot) {
        YsmModelPart part = getPart(bone.name);
        applyBoneTransform(stack, bone, stopAtPivot, part);
    }

    private void applyBoneTransform(PoseStack stack, YsmGeometry.Bone bone, boolean stopAtPivot, YsmModelPart part) {
        double px = bone.pivot[0] / 16d;
        double py = bone.pivot[1] / 16d;
        double pz = bone.pivot[2] / 16d;
        stack.translate(px, py, pz);
        if (part != null) {
            FiguraVec3 animPos = part.animPosRaw();
            FiguraVec3 animRot = part.animRotRaw();
            FiguraVec3 animScale = part.animScaleRaw();
            stack.translate(-animPos.x / 16d, animPos.y / 16d, animPos.z / 16d);
            rotate(stack, bone.rotation[0] - (float) animRot.x,
                    bone.rotation[1] - (float) animRot.y, bone.rotation[2] + (float) animRot.z);
            stack.scale((float) animScale.x, (float) animScale.y, (float) animScale.z);

            FiguraVec3 pos = part.posRaw();
            FiguraVec3 rot = part.rotRaw();
            FiguraVec3 scale = part.scaleRaw();
            stack.translate(-pos.x / 16d, pos.y / 16d, pos.z / 16d);
            rotate(stack, (float) -rot.x, (float) -rot.y, (float) rot.z);
            stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        } else {
            rotate(stack, bone.rotation[0], bone.rotation[1], bone.rotation[2]);
        }
        if (!stopAtPivot)
            stack.translate(-px, -py, -pz);
    }

    private void rotate(PoseStack stack, float x, float y, float z) {
        if (z != 0f)
            stack.mulPose(Axis.ZP.rotationDegrees(z));
        if (y != 0f)
            stack.mulPose(Axis.YP.rotationDegrees(y));
        if (x != 0f)
            stack.mulPose(Axis.XP.rotationDegrees(x));
    }

    private static byte[] onePixelPng() {
        return java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/atX7pQAAAAASUVORK5CYII=");
    }

}
