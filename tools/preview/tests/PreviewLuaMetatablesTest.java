package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import top.aihmc.omm.preview.data.PreviewTags.CompoundTag;

/** Uses real Animation/AnimationAPI objects without sampling or loading any model file. */
public final class PreviewLuaMetatablesTest {
    private static PreviewLua runtime() throws Exception {
        var states = new JsonObject();
        var lua =
            new PreviewLua(new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42));
        var definition = new PreviewFiguraParts();
        var data = new CompoundTag();
        data.putString("mdl", "unit");
        data.putString("name", "wave");
        data.putString("loop", "loop");
        data.putFloat("len", 1);
        definition.animations.add(data);
        lua.attachFigura(definition);
        lua.initialize();
        return lua;
    }

    public static void main(String[] args) throws Exception {
        try (var first = runtime(); var second = runtime()) {
            if (!first.evaluate("""
                    local animation = animations.unit.wave
                    assert(type(animation) == 'Animation' and type(animations) == 'AnimationAPI')
                    assert(figuraMetatables.Animation == getmetatable(animation))
                    assert(figuraMetatables.AnimationAPI == getmetatable(animations))
                    local action = action_wheel:newPage():newAction(1)
                    action:color(vec(0.2, 0.3, 0.4))
                    local color = action:getColor()
                    assert(type(color) == 'Vector3' and getmetatable(color) == figuraMetatables.Vector3)
                    assert(math.abs((color + vec(1, 0, 0)).x - 1.2) < 0.000001)
                    local original = figuraMetatables.Animation.__index
                    figuraMetatables.Animation.__index = function(self, key)
                      if key == 'custom' then return function() return 42 end end
                      if type(original) == 'table' then return original[key] end
                      return original(self, key)
                    end
                    assert(animation:custom() == 42 and animation:getLength() == 1)
                    local tagged = setmetatable({}, {
                      __type = 'Tagged',
                      __pairs = function() return next, { value = 7 }, nil end,
                      __ipairs = function() return ipairs({8, 9}) end
                    })
                    assert(type(tagged) == 'Tagged')
                    local value = 0; for _, v in pairs(tagged) do value = value + v end
                    assert(value == 7, '__pairs was not dispatched')
                    local sum = 0; for _, v in ipairs(tagged) do sum = sum + v end
                    assert(sum == 17 and type(nil) == 'nil' and type({}) == 'table')
                    local normal = 0; for _, v in ipairs({2, 3}) do normal = normal + v end
                    return normal == 5
                    """).arg1().checkboolean()) {
                throw new AssertionError("OMP metatable/type/iteration semantics differ");
            }
            if (!second
                    .evaluate("return animations.unit.wave.custom == nil and "
                        + "type(animations.unit.wave) == 'Animation'")
                    .arg1()
                    .checkboolean()) {
                throw new AssertionError("Animation metatable edits leaked between instances");
            }
        }
        System.out.println("Lua metatables PASS: actual animation interception/type, pairs/ipairs and instance "
            + "isolation; no model loads");
    }
}
