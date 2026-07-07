package org.figuramc.figura.lua.api.ysm_model;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.ysm.YsmModelPart;
import org.figuramc.figura.model.ysm.YsmModelRuntime;

import java.util.Collection;
import java.util.LinkedHashSet;

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
