package org.figuramc.figura.lua.api.ysm_actions;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.ysm.action.YsmActionWheelLayoutStore;

import java.util.HashMap;
import java.util.Map;

@LuaWhitelist
@LuaTypeDoc(
        name = "YsmActionsAPI",
        value = "ysm_actions"
)
public class YsmActionsAPI {
    private final Avatar owner;

    public YsmActionsAPI(Avatar owner) {
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.get_actions")
    public Map<String, YsmActionDefinition> getActions() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? Map.of() : runtime.actions().asMap();
    }

    @LuaWhitelist
    public Map<String, YsmActionDefinition> get_actions() {
        return getActions();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.get_wheel_actions")
    public Map<String, Map<String, Object>> getWheelActions() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return Map.of();
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (YsmActionDefinition action : runtime.actions().all()) {
            if (!isWheelAction(action))
                continue;
            Map<String, Object> data = new HashMap<>();
            data.put("title", action.getTitle());
            data.put("animation", action.getAnimation());
            data.put("page", action.getPage());
            data.put("loop", action.isLoop());
            data.put("mode", action.getMode());
            YsmActionWheelLayoutStore.Entry slot = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
            if (slot != null) {
                data.put("layout_page", slot.page());
                data.put("slot", slot.slot());
            }
            result.put(action.getId(), data);
        }
        return result;
    }

    @LuaWhitelist
    public Map<String, Map<String, Object>> get_wheel_actions() {
        return getWheelActions();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.get_action")
    public YsmActionDefinition getAction(String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? null : runtime.actions().get(id);
    }

    @LuaWhitelist
    public YsmActionDefinition get_action(String id) {
        return getAction(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.new_action")
    public YsmActionDefinition newAction(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return null;
        YsmActionDefinition action = new YsmActionDefinition(id);
        runtime.actions().register(action);
        return action;
    }

    @LuaWhitelist
    public YsmActionDefinition new_action(@LuaNotNil String id) {
        return newAction(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.register")
    public YsmActionDefinition register(@LuaNotNil YsmActionDefinition action) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? action : runtime.actions().register(action);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.trigger")
    public boolean trigger(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime != null && runtime.actions().trigger(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.stop")
    public YsmActionsAPI stop(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.actions().stop(id);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.get_wheel_slot")
    public Map<String, Object> getWheelSlot(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return null;
        YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), id);
        if (entry == null)
            return null;
        Map<String, Object> result = new HashMap<>();
        result.put("page", entry.page());
        result.put("slot", entry.slot());
        return result;
    }

    @LuaWhitelist
    public Map<String, Object> get_wheel_slot(@LuaNotNil String id) {
        return getWheelSlot(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.set_wheel_slot")
    public YsmActionsAPI setWheelSlot(@LuaNotNil String id, String page, int slot) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.set(runtime.getModelKey(), id, page, slot);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI set_wheel_slot(@LuaNotNil String id, String page, int slot) {
        return setWheelSlot(id, page, slot);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.clear_wheel_slot")
    public YsmActionsAPI clearWheelSlot(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.set(runtime.getModelKey(), id, null, -1);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI clear_wheel_slot(@LuaNotNil String id) {
        return clearWheelSlot(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.refresh_wheel")
    public YsmActionsAPI refreshWheel() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null && owner.luaRuntime != null) {
            owner.luaRuntime.action_wheel.setPage(null);
            runtime.installDefaultActionWheel(owner.luaRuntime.action_wheel);
        }
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI refresh_wheel() {
        return refreshWheel();
    }

    private static boolean isWheelAction(YsmActionDefinition action) {
        if (action == null)
            return false;
        return normalize(action.getId()).contains("extra_animation")
                || normalize(action.getPage()).contains("extra_animation")
                || normalize(action.getAnimation()).contains("extra_animation");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.US).replace('-', '_').replace(' ', '_');
    }

    @Override
    public String toString() {
        return "YsmActionsAPI";
    }
}
