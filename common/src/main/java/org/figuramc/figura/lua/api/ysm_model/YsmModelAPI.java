package org.figuramc.figura.lua.api.ysm_model;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.model.ysm.YsmSubEntity;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.YsmAttachmentPoint;
import org.figuramc.figura.model.ysm.YsmLocator;
import org.figuramc.figura.model.ysm.YsmModelPart;
import org.figuramc.figura.model.ysm.YsmModelRuntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@LuaWhitelist
@LuaTypeDoc(name = "YsmModelAPI", value = "ysm_model")
public class YsmModelAPI {
    private final Avatar owner;

    public YsmModelAPI(Avatar owner) {
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_part")
    public YsmModelPart getPart(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? null : runtime.getPart(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_parts")
    public Collection<YsmModelPart> getParts() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.List.of() : new LinkedHashSet<>(runtime.parts().values());
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_root_parts")
    public List<YsmModelPart> getRootParts() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.List.of() : runtime.rootParts();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_locator")
    public YsmLocator getLocator(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? null : runtime.getLocator(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_locators")
    public List<YsmLocator> getLocators() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.List.of();
        List<YsmLocator> result = new ArrayList<>();
        runtime.locators().forEach(result::add);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_bone_world_matrix")
    public FiguraMat4 getBoneWorldMatrix(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? FiguraMat4.of() : runtime.getBoneWorldMatrix(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_bone_world_pos")
    public FiguraVec3 getBoneWorldPos(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? FiguraVec3.of() : runtime.getBoneWorldPos(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_locator_world_matrix")
    public FiguraMat4 getLocatorWorldMatrix(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? FiguraMat4.of() : runtime.getLocatorWorldMatrix(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_locator_world_pos")
    public FiguraVec3 getLocatorWorldPos(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? FiguraVec3.of() : runtime.getLocatorWorldPos(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_attachment")
    public YsmAttachmentPoint getAttachment(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? null : runtime.getAttachmentPoint(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_attachments")
    public List<YsmAttachmentPoint> getAttachments() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.List.of();
        List<YsmAttachmentPoint> result = new ArrayList<>();
        runtime.attachmentPoints().forEach(result::add);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_attachment_world_matrix")
    public FiguraMat4 getAttachmentWorldMatrix(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? FiguraMat4.of() : runtime.getAttachmentWorldMatrix(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_attachment_world_pos")
    public FiguraVec3 getAttachmentWorldPos(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? FiguraVec3.of() : runtime.getAttachmentWorldPos(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_texture")
    public String getTexture() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? "" : runtime.textureId();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.set_texture")
    public YsmModelAPI setTexture(String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.setTexture(id);
        return this;
    }

    @LuaWhitelist
    public YsmModelAPI texture(String id) {
        return setTexture(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.is_ysm")
    public boolean isYsm() {
        return owner.isYsmNative();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_kind")
    public String getKind() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? "none" : runtime.getKind().toLowerCase(java.util.Locale.US);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.is_native_state_machine_enabled")
    public boolean isNativeStateMachineEnabled() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime != null && runtime.animations().isNativeStateMachineEnabled();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.set_native_state_machine_enabled")
    public YsmModelAPI setNativeStateMachineEnabled(boolean enabled) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.animations().setNativeStateMachineEnabled(enabled);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.is_native_animation_enabled")
    public boolean isNativeAnimationEnabled(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime != null && runtime.animations().isNativeAnimationEnabled(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.set_native_animation_enabled")
    public YsmModelAPI setNativeAnimationEnabled(String name, boolean enabled) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.animations().setNativeAnimationEnabled(name, enabled);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.play_animation")
    public YsmModelAPI playAnimation(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.animations().play(name, true, 1f);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.stop_animation")
    public YsmModelAPI stopAnimation(String name) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.animations().stop(name);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_controllers")
    public List<String> getControllers() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.List.of();
        return runtime.animations().getControllers().values().stream()
                .map(YsmAnimationController::name)
                .distinct()
                .toList();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_controller_states")
    public Map<String, String> getControllerStates() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (YsmAnimationController controller : runtime.animations().getControllers().values())
            result.put(controller.name(), runtime.animations().controllerRuntime().currentState(controller.name()));
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_active_animations")
    public Map<String, Map<String, Object>> getActiveAnimations() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.Map.of();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, YsmAnimationPlayer.PlayingAnimation> entry : runtime.animations().getActiveAnimations().entrySet()) {
            YsmAnimationPlayer.PlayingAnimation animation = entry.getValue();
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("name", animation.clip.name);
            state.put("time", animation.time);
            state.put("length", animation.clip.length);
            state.put("weight", animation.weight);
            state.put("target_weight", animation.targetWeight);
            state.put("loop", animation.loopMode.name().toLowerCase(java.util.Locale.US));
            state.put("native", animation.isNative);
            result.put(entry.getKey(), state);
        }
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_functions")
    public List<String> getFunctions() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.List.of() : runtime.functions().functionDebugNames();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_events")
    public Map<String, List<String>> getEvents() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.Map.of() : runtime.functions().eventDebugNames();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_recent_events")
    public List<String> getRecentEvents() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.List.of() : runtime.functions().recentEvents();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_recent_sync_keys")
    public List<String> getRecentSyncKeys() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? java.util.List.of() : runtime.functions().recentSyncKeys();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_actions")
    public List<String> getActions() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.List.of();
        return runtime.actions().all().stream()
                .map(YsmActionDefinition::getId)
                .toList();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_controls")
    public List<String> getControls() {
        return owner.controls.all().stream()
                .map(AvatarControlDefinition::id)
                .filter(id -> id != null && id.startsWith("ysm."))
                .toList();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model.get_sub_entities")
    public List<String> getSubEntities() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return java.util.List.of();
        List<String> result = new ArrayList<>();
        for (YsmSubEntity entity : runtime.subEntities())
            result.add(entity.kind() + ":" + entity.id());
        return result;
    }

    @LuaWhitelist
    public Object __index(String key) {
        if (key == null)
            return null;
        if ("ALL".equalsIgnoreCase(key))
            return getParts();
        return getPart(key);
    }

    @Override
    public String toString() {
        return "YsmModelAPI";
    }
}
