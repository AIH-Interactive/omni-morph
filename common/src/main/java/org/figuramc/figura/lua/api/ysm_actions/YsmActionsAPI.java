package org.figuramc.figura.lua.api.ysm_actions;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;

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

    @Override
    public String toString() {
        return "YsmActionsAPI";
    }
}
