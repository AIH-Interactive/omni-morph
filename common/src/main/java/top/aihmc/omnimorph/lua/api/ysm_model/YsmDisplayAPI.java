package top.aihmc.omnimorph.lua.api.ysm_model;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.api.entity.EntityAPI;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import net.minecraft.world.entity.Entity;
import top.aihmc.omnimorph.model.ysm.YsmDisplayQuery;
import top.aihmc.omnimorph.model.ysm.sync.YsmDisplayState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * D025: player values the vanilla client does not share (food, experience, flying, movement input,
 * effect levels). The local player reads its own entity; other players read what their owner
 * published; unknown values are nil. Read-only: scripts cannot publish or change these values.
 */
@LuaWhitelist
@LuaTypeDoc(name = "YsmDisplayAPI", value = "ysm_display")
public class YsmDisplayAPI {
    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get")
    public Map<String, Object> get(EntityAPI<?> entity) {
        YsmDisplayState state = state(entity);
        if (state == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("food", state.foodLevel());
        result.put("experience_level", state.experienceLevel());
        result.put("flying", state.flying());
        result.put("strafe", state.strafeInput());
        result.put("vertical", state.verticalInput());
        result.put("forward", state.forwardInput());
        result.put("effects", state.effectLevels() == null ? null : new LinkedHashMap<>(state.effectLevels()));
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_display.is_synced")
    public boolean isSynced(EntityAPI<?> entity) {
        return state(entity) != null;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get_food")
    public Integer getFood(EntityAPI<?> entity) {
        YsmDisplayState state = state(entity);
        return state == null ? null : state.foodLevel();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get_experience_level")
    public Integer getExperienceLevel(EntityAPI<?> entity) {
        YsmDisplayState state = state(entity);
        return state == null ? null : state.experienceLevel();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_display.is_flying")
    public Boolean isFlying(EntityAPI<?> entity) {
        YsmDisplayState state = state(entity);
        return state == null ? null : state.flying();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get_movement_input")
    public FiguraVec3 getMovementInput(EntityAPI<?> entity) {
        YsmDisplayState state = state(entity);
        if (state == null) {
            return null;
        }
        return FiguraVec3.of(orZero(state.strafeInput()), orZero(state.verticalInput()), orZero(state.forwardInput()));
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get_effect_level")
    public Integer getEffectLevel(EntityAPI<?> entity, String effect) {
        YsmDisplayState state = state(entity);
        if (state == null || state.effectLevels() == null || effect == null) {
            return null;
        }
        return state.effectLevels().getOrDefault(effect, 0);
    }

    /** Base action of the entity's loaded YSM model (same value as its {@code ctrl.*} states), or nil. */
    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get_action_state")
    public String getActionState(EntityAPI<?> entity) {
        var runtime = runtime(entity);
        return runtime == null ? null : runtime.animations().actionState().animationName();
    }

    /** Current state of each declared controller of the entity's loaded YSM model, or nil. Read-only. */
    @LuaWhitelist
    @LuaMethodDoc("ysm_display.get_controller_states")
    public Map<String, String> getControllerStates(EntityAPI<?> entity) {
        var runtime = runtime(entity);
        if (runtime == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (var controller : runtime.animations().getControllers().values()) {
            result.put(controller.name(), runtime.animations().controllerRuntime().currentState(controller.name()));
        }
        return result;
    }

    private static top.aihmc.omnimorph.model.ysm.YsmModelRuntime runtime(EntityAPI<?> entity) {
        if (entity == null) {
            return null;
        }
        var avatar = org.figuramc.figura.avatar.AvatarManager.getAvatar(entity.getEntity());
        var runtime = avatar == null ? null : avatar.getYsmRuntime();
        return runtime == null || runtime.isClosed() ? null : runtime;
    }

    private static YsmDisplayState state(EntityAPI<?> entity) {
        if (entity == null) {
            return null;
        }
        Entity value = entity.getEntity();
        return value == null ? null : YsmDisplayQuery.of(value);
    }

    private static double orZero(Float value) {
        return value == null ? 0d : value;
    }

    @Override
    public String toString() {
        return "YsmDisplayAPI";
    }
}
