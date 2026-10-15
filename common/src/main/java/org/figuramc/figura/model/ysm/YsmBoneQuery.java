package org.figuramc.figura.model.ysm;

import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.molang.runtime.Struct;
import org.figuramc.figura.molang.storage.StringPool;

/** A read-only live view, matching the reference bone vector Struct's copy semantics. */
public final class YsmBoneQuery implements Struct {
    public enum Channel {
        POSITION,
        ROTATION,
        SCALE,
        PIVOT_ABS
    }

    private static final int X = StringPool.computeIfAbsent("x");
    private static final int Y = StringPool.computeIfAbsent("y");
    private static final int Z = StringPool.computeIfAbsent("z");

    private final YsmModelPart part;
    private final Channel channel;
    private final YsmBoneQueryRuntime runtime;
    private final boolean firstPerson;

    public YsmBoneQuery(YsmModelPart part, Channel channel, YsmBoneQueryRuntime runtime, boolean firstPerson) {
        this.part = part;
        this.channel = channel;
        this.runtime = runtime;
        this.firstPerson = firstPerson;
    }

    @Override
    public Object getProperty(int name) {
        int axis;
        if (name == X) {
            axis = 0;
        } else if (name == Y) {
            axis = 1;
        } else if (name == Z) {
            axis = 2;
        } else {
            return null;
        }
        return runtime.read(part, channel, axis, firstPerson);
    }

    static float read(YsmModelPart part, Channel channel, int axis) {
        return switch (channel) {
            case POSITION -> component(part.animPosRaw(), axis) + component(part.posRaw(), axis);
            case ROTATION -> {
                // Geometry stores renderer-oriented degrees; animation/user rotations use model degrees.
                float origin = component(part.getOriginRot(), axis);
                yield (axis == 2 ? origin : -origin)
                        + component(part.animRotRaw(), axis) + component(part.rotRaw(), axis);
            }
            case SCALE -> component(part.animScaleRaw(), axis) * component(part.scaleRaw(), axis);
            case PIVOT_ABS -> 0f; // Stored by the owning runtime only after rendering a tracked visible bone.
        };
    }

    private static float component(FiguraVec3 value, int axis) {
        return (float) switch (axis) {
            case 0 -> value.x;
            case 1 -> value.y;
            default -> value.z;
        };
    }

    @Override
    public void putProperty(int name, Object value) {
        // Reference bone vectors ignore writes; a script cannot alter bone state through this view.
    }

    @Override
    public Struct copy() {
        return this;
    }
}
