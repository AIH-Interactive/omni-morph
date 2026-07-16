package org.figuramc.figura.lua.api.ysm_actions;

import com.mojang.blaze3d.platform.InputConstants;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.api.keybind.FiguraKeybind;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.ysm.action.YsmActionWheelLayoutStore;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
            if (action.getItem() != null)
                data.put("item", action.getItem());
            data.put("effective_item", effectiveItem(action));
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
    @LuaMethodDoc("ysm_actions.get_wheel_pages")
    public List<String> getWheelPages() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return List.of();
        LinkedHashSet<String> pages = new LinkedHashSet<>();
        pages.add("extra_animation");
        pages.addAll(YsmActionWheelLayoutStore.pages(runtime.getModelKey()));
        for (YsmActionDefinition action : runtime.actions().all()) {
            if (!isWheelAction(action))
                continue;
            pages.add(defaultWheelPage(action));
            YsmActionWheelLayoutStore.Entry slot = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
            if (slot != null && slot.page() != null && !slot.page().isBlank())
                pages.add(slot.page());
        }
        return new ArrayList<>(pages);
    }

    @LuaWhitelist
    public List<String> get_wheel_pages() {
        return getWheelPages();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.get_wheel_layout")
    public Map<String, Map<Integer, String>> getWheelLayout() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return Map.of();
        Map<String, Map<Integer, String>> layout = new HashMap<>();
        for (YsmActionDefinition action : runtime.actions().all()) {
            if (!isWheelAction(action))
                continue;
            YsmActionWheelLayoutStore.Entry slot = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
            if (slot != null && slot.slot() > 0)
                layout.computeIfAbsent(slot.page(), ignored -> new HashMap<>()).put(slot.slot(), action.getId());
        }
        return layout;
    }

    @LuaWhitelist
    public Map<String, Map<Integer, String>> get_wheel_layout() {
        return getWheelLayout();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.get_wheel_page_slot_action")
    public String getWheelPageSlotAction(@LuaNotNil String page, int slot) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        YsmActionDefinition action = wheelSlotOwner(runtime, page, slot);
        return action == null ? null : action.getId();
    }

    @LuaWhitelist
    public String get_wheel_page_slot_action(@LuaNotNil String page, int slot) {
        return getWheelPageSlotAction(page, slot);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.trigger_wheel_page_slot")
    public boolean triggerWheelPageSlot(@LuaNotNil String page, int slot) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        YsmActionDefinition action = wheelSlotOwner(runtime, page, slot);
        return runtime != null && action != null && runtime.actions().trigger(action.getId());
    }

    @LuaWhitelist
    public boolean trigger_wheel_page_slot(@LuaNotNil String page, int slot) {
        return triggerWheelPageSlot(page, slot);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.stop_wheel_page_slot")
    public YsmActionsAPI stopWheelPageSlot(@LuaNotNil String page, int slot) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        YsmActionDefinition action = wheelSlotOwner(runtime, page, slot);
        if (runtime != null && action != null)
            runtime.actions().stop(action.getId());
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI stop_wheel_page_slot(@LuaNotNil String page, int slot) {
        return stopWheelPageSlot(page, slot);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.bind_wheel_page_slot")
    public FiguraKeybind bindWheelPageSlot(String name, @LuaNotNil String page, int slot, String key, boolean gui) {
        if (owner.luaRuntime == null)
            return null;
        String bindingName = name == null || name.isBlank() ? "YSM " + page + " " + slot : name;
        InputConstants.Key parsedKey = FiguraKeybind.parseStringKey(key == null || key.isBlank() ? "key.keyboard.unknown" : key);
        FiguraKeybind binding = new FiguraKeybind(owner, bindingName, parsedKey).gui(gui);
        removeKeybindsByName(bindingName);
        binding.press = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(triggerWheelPageSlot(page, slot));
            }
        };
        owner.luaRuntime.keybinds.keyBindings.add(binding);
        return binding;
    }

    @LuaWhitelist
    public FiguraKeybind bind_wheel_page_slot(String name, @LuaNotNil String page, int slot, String key, boolean gui) {
        return bindWheelPageSlot(name, page, slot, key, gui);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.bind_toggle_wheel_page_slot")
    public FiguraKeybind bindToggleWheelPageSlot(String name, @LuaNotNil String page, int slot, String key, boolean gui) {
        if (owner.luaRuntime == null)
            return null;
        String bindingName = name == null || name.isBlank() ? "YSM Toggle " + page + " " + slot : name;
        InputConstants.Key parsedKey = FiguraKeybind.parseStringKey(key == null || key.isBlank() ? "key.keyboard.unknown" : key);
        FiguraKeybind binding = new FiguraKeybind(owner, bindingName, parsedKey).gui(gui);
        removeKeybindsByName(bindingName);
        binding.press = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                YsmModelRuntime runtime = owner.getYsmRuntime();
                YsmActionDefinition action = wheelSlotOwner(runtime, page, slot);
                if (runtime == null || action == null)
                    return LuaValue.FALSE;
                if (runtime.actions().isActive(action.getId())) {
                    runtime.actions().stop(action.getId());
                    return LuaValue.TRUE;
                }
                return LuaValue.valueOf(runtime.actions().trigger(action.getId()));
            }
        };
        owner.luaRuntime.keybinds.keyBindings.add(binding);
        return binding;
    }

    @LuaWhitelist
    public FiguraKeybind bind_toggle_wheel_page_slot(String name, @LuaNotNil String page, int slot, String key, boolean gui) {
        return bindToggleWheelPageSlot(name, page, slot, key, gui);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.unbind_wheel_keybind")
    public int unbindWheelKeybind(@LuaNotNil String name) {
        return removeKeybindsByName(name);
    }

    @LuaWhitelist
    public int unbind_wheel_keybind(@LuaNotNil String name) {
        return unbindWheelKeybind(name);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.bind_action")
    public FiguraKeybind bindAction(String name, @LuaNotNil String id, String key, boolean gui) {
        if (owner.luaRuntime == null)
            return null;
        String bindingName = name == null || name.isBlank() ? "YSM Action " + id : name;
        InputConstants.Key parsedKey = FiguraKeybind.parseStringKey(key == null || key.isBlank() ? "key.keyboard.unknown" : key);
        FiguraKeybind binding = new FiguraKeybind(owner, bindingName, parsedKey).gui(gui);
        removeKeybindsByName(bindingName);
        binding.press = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(trigger(id));
            }
        };
        owner.luaRuntime.keybinds.keyBindings.add(binding);
        return binding;
    }

    @LuaWhitelist
    public FiguraKeybind bind_action(String name, @LuaNotNil String id, String key, boolean gui) {
        return bindAction(name, id, key, gui);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.bind_toggle_action")
    public FiguraKeybind bindToggleAction(String name, @LuaNotNil String id, String key, boolean gui) {
        if (owner.luaRuntime == null)
            return null;
        String bindingName = name == null || name.isBlank() ? "YSM Toggle Action " + id : name;
        InputConstants.Key parsedKey = FiguraKeybind.parseStringKey(key == null || key.isBlank() ? "key.keyboard.unknown" : key);
        FiguraKeybind binding = new FiguraKeybind(owner, bindingName, parsedKey).gui(gui);
        removeKeybindsByName(bindingName);
        binding.press = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                YsmModelRuntime runtime = owner.getYsmRuntime();
                if (runtime == null)
                    return LuaValue.FALSE;
                if (runtime.actions().isActive(id)) {
                    runtime.actions().stop(id);
                    return LuaValue.TRUE;
                }
                return LuaValue.valueOf(runtime.actions().trigger(id));
            }
        };
        owner.luaRuntime.keybinds.keyBindings.add(binding);
        return binding;
    }

    @LuaWhitelist
    public FiguraKeybind bind_toggle_action(String name, @LuaNotNil String id, String key, boolean gui) {
        return bindToggleAction(name, id, key, gui);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.add_wheel_page")
    public YsmActionsAPI addWheelPage(@LuaNotNil String page) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.addPage(runtime.getModelKey(), page);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI add_wheel_page(@LuaNotNil String page) {
        return addWheelPage(page);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.rename_wheel_page")
    public YsmActionsAPI renameWheelPage(@LuaNotNil String oldPage, @LuaNotNil String newPage) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.renamePage(runtime.getModelKey(), oldPage, newPage);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI rename_wheel_page(@LuaNotNil String oldPage, @LuaNotNil String newPage) {
        return renameWheelPage(oldPage, newPage);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.remove_wheel_page")
    public YsmActionsAPI removeWheelPage(@LuaNotNil String page) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.removePage(runtime.getModelKey(), page);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI remove_wheel_page(@LuaNotNil String page) {
        return removeWheelPage(page);
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
    public YsmActionsAPI stop_all() {
        return stopAll();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.stop_all")
    public YsmActionsAPI stopAll() {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            runtime.actions().stopAll();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.is_active")
    public boolean isActive(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime != null && runtime.actions().isActive(id);
    }

    @LuaWhitelist
    public boolean is_active(@LuaNotNil String id) {
        return isActive(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.time")
    public float time(@LuaNotNil String id) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        return runtime == null ? 0f : runtime.actions().time(id);
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
    @LuaMethodDoc("ysm_actions.clear_wheel_page")
    public YsmActionsAPI clearWheelPage(@LuaNotNil String page) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.clearPage(runtime.getModelKey(), page);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI clear_wheel_page(@LuaNotNil String page) {
        return clearWheelPage(page);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.clear_wheel_page_slot")
    public YsmActionsAPI clearWheelPageSlot(@LuaNotNil String page, int slot) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.clearSlot(runtime.getModelKey(), page, slot);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI clear_wheel_page_slot(@LuaNotNil String page, int slot) {
        return clearWheelPageSlot(page, slot);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.swap_wheel_page_slots")
    public YsmActionsAPI swapWheelPageSlots(@LuaNotNil String page, int firstSlot, int secondSlot) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.swapSlots(runtime.getModelKey(), page, firstSlot, secondSlot);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI swap_wheel_page_slots(@LuaNotNil String page, int firstSlot, int secondSlot) {
        return swapWheelPageSlots(page, firstSlot, secondSlot);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.compact_wheel_page")
    public YsmActionsAPI compactWheelPage(@LuaNotNil String page) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime != null)
            YsmActionWheelLayoutStore.compactPage(runtime.getModelKey(), page);
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI compact_wheel_page(@LuaNotNil String page) {
        return compactWheelPage(page);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_actions.auto_fill_wheel_page")
    public YsmActionsAPI autoFillWheelPage(@LuaNotNil String page) {
        YsmModelRuntime runtime = owner.getYsmRuntime();
        if (runtime == null)
            return this;
        for (YsmActionDefinition action : runtime.actions().all()) {
            if (!isWheelAction(action) || YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId()) != null)
                continue;
            int slot = firstEmptySlot(runtime, page);
            if (slot < 1)
                break;
            YsmActionWheelLayoutStore.set(runtime.getModelKey(), action.getId(), page, slot);
        }
        return this;
    }

    @LuaWhitelist
    public YsmActionsAPI auto_fill_wheel_page(@LuaNotNil String page) {
        return autoFillWheelPage(page);
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

    private static String defaultWheelPage(YsmActionDefinition action) {
        String page = action.getPage();
        return page == null || page.isBlank() || "root".equals(normalize(page)) ? "extra_animation" : page;
    }

    private static String effectiveItem(YsmActionDefinition action) {
        if (action.getItem() != null && !action.getItem().isBlank())
            return action.getItem();
        return action.getAnimation() != null && action.getAnimation().startsWith("#") ? "minecraft:comparator" : "minecraft:armor_stand";
    }

    private int firstEmptySlot(YsmModelRuntime runtime, String page) {
        for (int slot = 1; slot <= 8; slot++) {
            if (wheelSlotOwner(runtime, page, slot) == null)
                return slot;
        }
        return -1;
    }

    private YsmActionDefinition wheelSlotOwner(YsmModelRuntime runtime, String page, int slot) {
        if (runtime == null || page == null || page.isBlank() || slot < 1)
            return null;
        for (YsmActionDefinition action : runtime.actions().all()) {
            if (!isWheelAction(action))
                continue;
            YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
            if (entry != null && slot == entry.slot() && page.equals(entry.page()))
                return action;
        }
        return null;
    }

    private int removeKeybindsByName(String name) {
        if (owner.luaRuntime == null || name == null || name.isBlank())
            return 0;
        int before = owner.luaRuntime.keybinds.keyBindings.size();
        owner.luaRuntime.keybinds.keyBindings.removeIf(binding -> name.equals(binding.getName()));
        return before - owner.luaRuntime.keybinds.keyBindings.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.US).replace('-', '_').replace(' ', '_');
    }

    @Override
    public String toString() {
        return "YsmActionsAPI";
    }
}
