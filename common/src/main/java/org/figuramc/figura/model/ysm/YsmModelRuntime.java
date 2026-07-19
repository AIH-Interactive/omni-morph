package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.avatar.ysm.YsmAvatarKind;
import org.figuramc.figura.lua.api.action_wheel.Action;
import org.figuramc.figura.lua.api.action_wheel.ActionWheelAPI;
import org.figuramc.figura.lua.api.action_wheel.Page;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationParser;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmAnimationControllerParser;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.ysm.action.YsmActionRuntime;
import org.figuramc.figura.model.ysm.action.YsmActionSchemaParser;
import org.figuramc.figura.model.ysm.action.YsmActionWheelLayoutStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YsmModelRuntime implements AutoCloseable {
    private final Avatar owner;
    private final YsmGeometry geometry;
    private YsmGeometry armGeometry;
    private FiguraTexture texture;
    private String textureId;
    private boolean textureTranslucent;
    private final Map<String, byte[]> textureData;
    private final Map<String, YsmModelPart> parts = new LinkedHashMap<>();
    private final Map<String, YsmModelPart> armParts = new LinkedHashMap<>();
    private final Map<String, YsmBoneRole> boneRoles = new LinkedHashMap<>();
    private final Map<String, YsmBoneRole> armBoneRoles = new LinkedHashMap<>();
    private final Map<String, YsmLocator> locators = new LinkedHashMap<>();
    private final Map<String, YsmAttachmentPoint> attachmentPoints = new LinkedHashMap<>();
    private final Map<String, FiguraMat4> locatorWorldMatrices = new LinkedHashMap<>();
    private final Map<String, YsmSubEntity> subEntities = new LinkedHashMap<>();
    private final YsmRenderer renderer;
    private final YsmAnimationPlayer animationPlayer;
    private final YsmActionRuntime actions;
    private final YsmMolangFunctionRuntime functions;
    private final String kind;
    private final String modelKey;
    private final float defaultWidthScale;
    private final float defaultHeightScale;

    private YsmModelRuntime(Avatar owner, YsmGeometry geometry, YsmGeometry armGeometry, FiguraTexture texture, String textureId, boolean textureTranslucent, String kind, String modelKey, Map<String, byte[]> textureData, float defaultWidthScale, float defaultHeightScale) {
        this.owner = owner;
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
        this.animationPlayer = new YsmAnimationPlayer(this);
        this.actions = new YsmActionRuntime(this);
        this.functions = new YsmMolangFunctionRuntime(this);
    }

    public static YsmModelRuntime fromNbt(Avatar owner, CompoundTag tag) throws java.io.IOException {
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

        byte[] textureBytes = tag.getByteArray("texture").orElse(new byte[0]);
        Map<String, byte[]> textureData = readTextureEntries(tag);
        String textureId = tag.getStringOr("texture_id", "ysm_texture");
        byte[] selectedTexture = textureData.getOrDefault(textureId, textureBytes);
        boolean textureTranslucent = geometry.applyTextureAlpha(selectedTexture);
        if (armGeometry != null)
            textureTranslucent |= armGeometry.applyTextureAlpha(selectedTexture);
        FiguraTexture texture = new FiguraTexture(owner, textureId, selectedTexture.length == 0 ? onePixelPng() : selectedTexture);
        String kind = tag.getStringOr("kind", YsmAvatarKind.NONE.name());
        String modelKey = tag.getStringOr("source_path", tag.getStringOr("main_model_path", "ysm"));
        float defaultWidthScale = tag.getFloatOr("width_scale", 1f);
        float defaultHeightScale = tag.getFloatOr("height_scale", 1f);
        YsmModelRuntime runtime = new YsmModelRuntime(owner, geometry, armGeometry, texture, textureId, textureTranslucent, kind, modelKey, textureData, defaultWidthScale, defaultHeightScale);
        runtime.readSubEntities(tag, selectedTexture);
        MolangEngine ysmMolangEngine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
        runtime.animationPlayer.registerAnimations(readAnimations(tag, ysmMolangEngine));
        runtime.animationPlayer.registerControllers(readAnimationControllers(tag, ysmMolangEngine));
        runtime.functions.load(tag.getListOrEmpty("functions"));
        runtime.animationPlayer.startBaseAnimations();
        runtime.registerDefaultControls();
        runtime.readActionSchemas(tag);
        if (runtime.actions.all().isEmpty())
            runtime.actions.buildDefaultsFromAnimations();
        runtime.registerDiscoveredControls(tag);
        owner.controls.loadSavedValues(owner);
        owner.controls.syncAll(owner);
        return runtime;
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

    private static void registerTextureKey(Map<String, byte[]> result, String key, byte[] data) {
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
            mergeAnimations(result, YsmAnimationParser.parse(json, engine));
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
        Map<String, YsmAnimationController> result = new LinkedHashMap<>();
        for (Tag controllerTag : tag.getListOrEmpty("animation_controllers")) {
            if (!(controllerTag instanceof CompoundTag controller))
                continue;
            String json = new String(controller.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            if (json.isBlank())
                continue;
            result.putAll(YsmAnimationControllerParser.parse(json, engine));
        }
        return result;
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
        FiguraTexture previous = texture;
        boolean newTextureTranslucent = geometry.applyTextureAlpha(data);
        if (armGeometry != null)
            newTextureTranslucent |= armGeometry.applyTextureAlpha(data);
        texture = new FiguraTexture(owner, id, data);
        textureId = id;
        textureTranslucent = newTextureTranslucent;
        previous.close();
        return true;
    }

    public String getKind() {
        return kind;
    }

    public String getModelKey() {
        return modelKey;
    }

    public float widthScale() {
        Object value = owner.controls.getValue("ysm.width_scale");
        return value instanceof Number number ? sanitizeScale(number.floatValue()) : defaultWidthScale;
    }

    public float heightScale() {
        Object value = owner.controls.getValue("ysm.height_scale");
        return value instanceof Number number ? sanitizeScale(number.floatValue()) : defaultHeightScale;
    }

    public YsmRenderer renderer() {
        return renderer;
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

    public boolean renderSubEntity(PoseStack stack, MultiBufferSource bufferSource, int light, String kind, String entityId) {
        return renderer.renderSubEntity(stack, bufferSource, light, getSubEntity(kind, entityId));
    }

    public YsmAnimationPlayer animations() {
        return animationPlayer;
    }

    public YsmActionRuntime actions() {
        return actions;
    }

    public YsmMolangFunctionRuntime functions() {
        return functions;
    }

    public void installDefaultActionWheel(ActionWheelAPI wheel) {
        if (wheel == null || wheel.getCurrentPage() != null)
            return;

        Page root = wheel.newPage("YSM");
        root.newAction(1)
                .setTitle("Settings")
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
            Action wheelAction = page.newAction(slot).setTitle(action.getTitle());
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
            setTitle("Back");
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
            setTitle(title == null || title.isBlank() ? "Extra Animation" : title);
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
                        .setTitle("YSM Discovered Controls")
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

    public void updateAnimations(LivingEntityRenderState state, LivingEntity entity) {
        Object nativeState = owner.controls.getValue("ysm.native_state_machine");
        if (nativeState instanceof Boolean enabled)
            animationPlayer.setNativeStateMachineEnabled(enabled);
        animationPlayer.update(state, entity);
    }

    private void registerDefaultControls() {
        owner.controls.register(new AvatarControlDefinition("ysm.header", AvatarControlType.LABEL)
                .setTitle("YSM Runtime")
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.native_state_machine", AvatarControlType.TOGGLE)
                .setTitle("Native state machine")
                .setDefault(true)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.action_loop", AvatarControlType.TOGGLE)
                .setTitle("Loop wheel actions")
                .setDefault(false)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.action_speed", AvatarControlType.SLIDER)
                .setTitle("Action speed")
                .setRange(0.1d, 3.0d, 0.1d)
                .setDefault(1.0d)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.width_scale", AvatarControlType.SLIDER)
                .setTitle("Width scale")
                .setRange(0.1d, 5.0d, 0.05d)
                .setDefault((double) defaultWidthScale)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.height_scale", AvatarControlType.SLIDER)
                .setTitle("Height scale")
                .setRange(0.1d, 5.0d, 0.05d)
                .setDefault((double) defaultHeightScale)
                .setPage("root"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.header", AvatarControlType.LABEL)
                .setTitle("YSM Debug")
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.functions", AvatarControlType.LABEL)
                .setTitle("Functions: " + functions.functionCount() + "  Events: " + functions.eventCount())
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.function_names", AvatarControlType.LABEL)
                .setTitle("fn: " + debugPreview(functions.functionDebugNames()))
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.event_names", AvatarControlType.LABEL)
                .setTitle("events: " + debugPreview(new ArrayList<>(functions.eventDebugNames().keySet())))
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.recent_events", AvatarControlType.LABEL)
                .setTitle("recent: " + debugPreview(functions.recentEvents()))
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.sync_keys", AvatarControlType.LABEL)
                .setTitle("sync: " + debugPreview(functions.recentSyncKeys()))
                .setPage("ysm_debug"));
        owner.controls.register(new AvatarControlDefinition("ysm.debug.call_stack", AvatarControlType.LABEL)
                .setTitle("stack: " + debugPreview(functions.callStackDebugNames()))
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
        if (stack == null)
            return;
        locatorWorldMatrices.clear();
        for (YsmGeometry.Bone root : geometry.roots)
            updateWorldMatrix(root, stack);
    }

    public boolean renderFirstPersonArm(PoseStack stack, MultiBufferSource bufferSource, int light, boolean left) {
        return renderer.renderFirstPersonArm(stack, bufferSource, light, left);
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
        YsmAttachmentPoint point = getAttachmentPoint(left ? "left_hand" : "right_hand");
        YsmGeometry.Bone bone = findAttachmentBone(point);
        stack.scale(0.9375f, 0.9375f, 0.9375f);
        if (bone == null) {
            bone = findHandBone(left);
        }
        if (bone == null) {
            stack.translate(left ? -0.42d : 0.42d, 0.78d, 0d);
            stack.mulPose(Axis.XP.rotationDegrees(-90f));
        } else {
            if (!isBoneVisibleInHierarchy(bone.name))
                return false;
            boolean locator = point != null && point.locator() != null;
            if (!locator)
                locator = bone.name.toLowerCase(java.util.Locale.US).contains("locator");
            applyBoneChain(stack, bone, locator);
            stack.translate(0d, -0.0625d, -0.1d);
            stack.mulPose(Axis.XP.rotationDegrees(-90f));
        }
        return true;
    }

    @Override
    public void close() {
        texture.close();
        for (YsmSubEntity entity : new LinkedHashSet<>(subEntities.values()))
            entity.close();
    }

    private void readSubEntities(CompoundTag tag, byte[] fallbackTexture) {
        for (Tag entityTag : tag.getListOrEmpty("sub_entities")) {
            if (!(entityTag instanceof CompoundTag subTag))
                continue;
            try {
                addSubEntity(YsmSubEntity.fromNbt(owner, subTag, fallbackTexture));
            } catch (Exception e) {
                FiguraMod.LOGGER.warn("Failed to load YSM sub entity {} from {}", subTag.getStringOr("id", "sub_entity"), modelKey, e);
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

    private void dumpDebugInfo() {
        if (!FiguraMod.debugModeEnabled())
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

    private void updateWorldMatrix(YsmGeometry.Bone bone, PoseStack stack) {
        YsmModelPart part = getPart(bone.name);
        stack.pushPose();
        applyBoneTransform(stack, bone, true);
        locatorWorldMatrices.put(bone.name, FiguraMat4.of().set(stack.last().pose()));
        locatorWorldMatrices.putIfAbsent(bone.name.toLowerCase(java.util.Locale.US), locatorWorldMatrices.get(bone.name));
        stack.popPose();

        stack.pushPose();
        applyBoneTransform(stack, bone);
        if (part != null)
            part.setWorldMatrix(FiguraMat4.of().set(stack.last().pose()));
        for (YsmGeometry.Bone child : bone.children)
            updateWorldMatrix(child, stack);
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
        double px = bone.pivot[0] / 16d;
        double py = bone.pivot[1] / 16d;
        double pz = bone.pivot[2] / 16d;
        stack.translate(px, py, pz);
        rotate(stack, bone.rotation[0], bone.rotation[1], bone.rotation[2]);
        if (part != null) {
            FiguraVec3 animPos = part.animPosRaw();
            FiguraVec3 animRot = part.animRotRaw();
            FiguraVec3 animScale = part.animScaleRaw();
            stack.translate(-animPos.x / 16d, animPos.y / 16d, animPos.z / 16d);
            rotate(stack, (float) -animRot.x, (float) -animRot.y, (float) animRot.z);
            stack.scale((float) animScale.x, (float) animScale.y, (float) animScale.z);

            FiguraVec3 pos = part.posRaw();
            FiguraVec3 rot = part.rotRaw();
            FiguraVec3 scale = part.scaleRaw();
            stack.translate(-pos.x / 16d, pos.y / 16d, pos.z / 16d);
            rotate(stack, (float) -rot.x, (float) -rot.y, (float) rot.z);
            stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
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
