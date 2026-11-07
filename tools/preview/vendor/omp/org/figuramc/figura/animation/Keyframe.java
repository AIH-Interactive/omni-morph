package org.figuramc.figura.animation;

import org.joml.Vector3d;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import top.aihmc.omm.preview.PreviewLua;
import top.aihmc.omm.preview.data.PreviewTags.*;

/** Original pre/end and cached Lua-expression keyframe semantics with standalone tagged data. */
public final class Keyframe implements Comparable<Keyframe> {
    private final PreviewLua owner;
    private final Animation animation;
    private final CompoundTag data;
    private final float time;
    private final Interpolation interpolation;
    private final LuaValue[][] cache = new LuaValue[2][3];

    public Keyframe(PreviewLua owner, Animation animation, CompoundTag data) {
        this.owner = owner;
        this.animation = animation;
        this.data = data;
        time = data.number("time", 0);
        interpolation = Interpolation.valueOf(data.string("int", "linear").toUpperCase(java.util.Locale.ROOT));
    }

    private Vector3d target(String key, int slot, float delta) {
        if (!data.contains(key)) {
            return key.equals("end") ? target("pre", 0, delta) : new Vector3d();
        }
        ListTag values = data.list(key);
        double[] result = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            Tag value = values.get(axis);
            if (value instanceof NumericTag number) {
                result[axis] = number.number().doubleValue();
            } else {
                String source = ((StringTag) value).value();
                try {
                    result[axis] = Double.parseDouble(source);
                } catch (NumberFormatException expression) {
                    if (cache[slot][axis] == null) {
                        String name = animation.name + " keyframe (time=" + time + ", axis=" + axis + ")";
                        try {
                            cache[slot][axis] = owner.loadScript(name, "return " + source);
                        } catch (LuaError invalidExpression) {
                            cache[slot][axis] = owner.loadScript(name, source);
                        }
                    }
                    var returned = owner.callAnimation(cache[slot][axis], delta, animation);
                    if (!returned.isnumber(1)) {
                        throw new LuaError("Keyframe expression must return a number: " + source);
                    }
                    result[axis] = returned.todouble(1);
                }
            }
        }
        return new Vector3d(result);
    }

    public Vector3d getTargetA(float delta) {
        return target("pre", 0, delta);
    }
    public Vector3d getTargetB(float delta) {
        return target("end", 1, delta);
    }
    public float getTime() {
        return time;
    }
    public Interpolation getInterpolation() {
        return interpolation;
    }

    private Vector3d vector(String key, double fallback) {
        if (!data.contains(key))
            return new Vector3d(fallback);
        ListTag values = data.list(key);
        return new Vector3d(values.number(0), values.number(1), values.number(2));
    }

    public Vector3d getBezierLeft() {
        return vector("bl", 0);
    }
    public Vector3d getBezierRight() {
        return vector("br", 0);
    }
    public Vector3d getBezierLeftTime() {
        return vector("blt", -0.1).add(1, 1, 1).max(new Vector3d()).min(new Vector3d(1));
    }
    public Vector3d getBezierRightTime() {
        return vector("brt", 0.1).max(new Vector3d()).min(new Vector3d(1));
    }

    @Override
    public int compareTo(Keyframe other) {
        return Float.compare(time, other.time);
    }
}
