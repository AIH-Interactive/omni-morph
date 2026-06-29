package org.figuramc.figura.lua.api;

import org.figuramc.figura.animation.Animation;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.binding.MolangBindings;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.molang.storage.VariableStorage;
import org.luaj.vm2.LuaValue;

import java.util.*;

@LuaWhitelist
@LuaTypeDoc(
        name = "AnimationAPI",
        value = "animations"
)
public class AnimationAPI {

    private final Map<String, Map<String, Animation>> animTable;
    private final Avatar avatar;

    public AnimationAPI(Avatar avatar) {
        this.avatar = avatar;
        animTable = generateAnimTable(avatar);
    }

    private static Map<String, Map<String, Animation>> generateAnimTable(Avatar avatar) {
        HashMap<String, Map<String, Animation>> root = new HashMap<>();
        for (Animation animation : avatar.animations.values()) {
            // get or create animation table
            Map<String, Animation> animations = root.get(animation.modelName);
            if (animations == null)
                animations = new HashMap<>();

            // put animation on the model table
            animations.put(animation.name, animation);
            root.put(animation.modelName, animations);
        }
        return root;
    }

    @LuaWhitelist
    @LuaMethodDoc("animations.get_animations")
    public List<Animation> getAnimations() {
        List<Animation> list = new ArrayList<>();
        for (Map<String, Animation> value : animTable.values())
            list.addAll(value.values());
        return list;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload,
                    @LuaMethodOverload(
                            argumentTypes = Boolean.class,
                            argumentNames = "hold"
                    )
            },
            value = "animations.get_playing"
    )
    public List<Animation> getPlaying(boolean hold) {
        List<Animation> list = new ArrayList<>();
        for (Animation animation : avatar.animations.values())
            if (hold ? (animation.playState == Animation.PlayState.PLAYING || animation.playState == Animation.PlayState.HOLDING) : (animation.playState == Animation.PlayState.PLAYING))
                list.add(animation);
        return list;
    }

    @LuaWhitelist
    @LuaMethodDoc("animations.stop_all")
    public AnimationAPI stopAll() {
        for (Animation animation : avatar.animations.values())
            animation.stop();
        return this;
    }

    @LuaWhitelist
    public Map<String, Animation> __index(String val) {
        return val == null ? null : animTable.get(val);
    }


    // -- Molang Lua API -- //


    @LuaWhitelist
    @LuaMethodDoc("animations.eval_molang")
    public float evalMolang(@LuaNotNil String expression) {
        try {
            List<Expression> ast = Avatar.getMolangEngine().parse(expression);
            if (ast.isEmpty()) return 0f;

            Avatar.MolangContext ctx = avatar.getMolangContext();
            if (ctx == null) return 0f;

            ExpressionEvaluator<?> evaluator = ExpressionEvaluator.evaluator(ctx);
            return evaluator.evalAsFloat(ast.get(0));
        } catch (Exception e) {
            return 0f;
        }
    }

    @LuaWhitelist
    @LuaMethodDoc("animations.get_molang_var")
    public Float getMolangVar(@LuaNotNil String name) {
        Avatar.MolangContext ctx = avatar.getMolangContext();
        if (ctx == null) return null;

        String varName = name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;
        Object val = ctx.variables.getScoped(StringPool.computeIfAbsent(varName));
        return val instanceof Number ? ((Number) val).floatValue() : null;
    }

    @LuaWhitelist
    @LuaMethodDoc("animations.set_molang_var")
    public AnimationAPI setMolangVar(@LuaNotNil String name, float value) {
        Avatar.MolangContext ctx = avatar.getMolangContext();
        if (ctx == null) return this;

        String varName = name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;
        ctx.variables.setScoped(StringPool.computeIfAbsent(varName), value);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("animations.set_molang_vars")
    public AnimationAPI setMolangVars(@LuaNotNil LuaValue table) {
        if (!table.istable()) return this;
        Avatar.MolangContext ctx = avatar.getMolangContext();
        if (ctx == null) return this;

        LuaValue[] keys = table.checktable().keys();
        for (LuaValue key : keys) {
            LuaValue val = table.get(key);
            if (val.isnumber()) {
                ctx.variables.setScoped(
                    StringPool.computeIfAbsent(key.checkjstring()),
                    val.tofloat()
                );
            }
        }
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("animations.get_molang_vars")
    public Map<String, Float> getMolangVars() {
        Avatar.MolangContext ctx = avatar.getMolangContext();
        if (ctx == null) return Collections.emptyMap();

        Map<String, Float> result = new HashMap<>();
        ctx.variables.forEachPropertyName(name -> {
            Object val = ctx.variables.getScoped(StringPool.computeIfAbsent(name));
            if (val instanceof Number) {
                result.put(name, ((Number) val).floatValue());
            }
        });
        return result;
    }

    @Override
    public String toString() {
        return "AnimationsAPI";
    }
}
