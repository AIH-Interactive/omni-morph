package org.figuramc.figura.lua.api.math;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.matrix.FiguraMat2;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec2;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.utils.LuaUtils;
import org.luaj.vm2.LuaError;

@LuaWhitelist
@LuaTypeDoc(
        name = "MatricesAPI",
        value = "matrices"
)
public class MatricesAPI {

    public static final MatricesAPI INSTANCE = new MatricesAPI();

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload,
                    @LuaMethodOverload(
                            argumentTypes = {FiguraVec2.class, FiguraVec2.class},
                            argumentNames = {"col1", "col2"}
                    )
            },
            value = "matrices.mat2"
    )
    public static FiguraMat2 mat2(FiguraVec2 col1, FiguraVec2 col2) {
        if (col1 == null && col2 == null)
            return FiguraMat2.of();
        if (col1 == null || col2 == null)
            throw new LuaError("Invalid arguments to mat2(), needs 0 or 2 arguments!");
        return FiguraMat2.of(
                col1.x, col1.y,
                col2.x, col2.y
        );
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload,
                    @LuaMethodOverload(
                            argumentTypes = {FiguraVec3.class, FiguraVec3.class, FiguraVec3.class},
                            argumentNames = {"col1", "col2", "col3"}
                    )
            },
            value = "matrices.mat3"
    )
    public static FiguraMat3 mat3(FiguraVec3 col1, FiguraVec3 col2, FiguraVec3 col3) {
        if (col1 == null && col2 == null && col3 == null)
            return FiguraMat3.of();
        if (col1 == null || col2 == null || col3 == null)
            throw new LuaError("Invalid arguments to mat3(), needs 0 or 3 arguments!");
        return FiguraMat3.of(
                col1.x, col1.y, col1.z,
                col2.x, col2.y, col2.z,
                col3.x, col3.y, col3.z
        );
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload,
                    @LuaMethodOverload(
                            argumentTypes = {FiguraVec4.class, FiguraVec4.class, FiguraVec4.class, FiguraVec4.class},
                            argumentNames = {"col1", "col2", "col3", "col4"}
                    )
            },
            value = "matrices.mat4"
    )
    public static FiguraMat4 mat4(FiguraVec4 col1, FiguraVec4 col2, FiguraVec4 col3, FiguraVec4 col4) {
        if (col1 == null && col2 == null && col3 == null && col4 == null)
            return FiguraMat4.of();
        if (col1 == null || col2 == null || col3 == null || col4 == null)
            throw new LuaError("Invalid arguments to mat4(), needs 0 or 4 arguments!");
        return FiguraMat4.of(
                col1.x, col1.y, col1.z, col1.w,
                col2.x, col2.y, col2.z, col2.w,
                col3.x, col3.y, col3.z, col3.w,
                col4.x, col4.y, col4.z, col4.w
        );
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "matrices.compose4",
            overloads = @LuaMethodOverload(
                    argumentTypes = {FiguraVec3.class, FiguraVec4.class, FiguraVec3.class},
                    argumentNames = {"translation", "quaternion", "scale"}
            )
    )
    public static FiguraMat4 compose4(FiguraVec3 position, FiguraVec4 quaternion, FiguraVec3 scale) {
        if (position == null || quaternion == null || scale == null) {
            throw new LuaError("compose4 requires translation, quaternion and scale vectors");
        }
        for (double value : new double[]{position.x, position.y, position.z,
                quaternion.x, quaternion.y, quaternion.z, quaternion.w, scale.x, scale.y, scale.z}) {
            if (!Double.isFinite(value)) {
                throw new LuaError("compose4 requires finite vector components");
            }
        }
        double lengthSquared = quaternion.x * quaternion.x + quaternion.y * quaternion.y
                + quaternion.z * quaternion.z + quaternion.w * quaternion.w;
        if (!(lengthSquared > 0) || !Double.isFinite(lengthSquared)) {
            throw new LuaError("compose4 requires a nonzero finite quaternion");
        }
        var rotation = new org.joml.Quaterniond(quaternion.x, quaternion.y, quaternion.z, quaternion.w)
                .normalize();
        var matrix = new org.joml.Matrix4d().translation(position.x, position.y, position.z)
                .rotate(rotation).scale(scale.x, scale.y, scale.z);
        return FiguraMat4.of(
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33());
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "matrices.compose_euler_xyz",
            overloads = @LuaMethodOverload(
                    argumentTypes = {FiguraVec3.class, FiguraVec3.class},
                    argumentNames = {"first", "second"}
            )
    )
    public static FiguraVec3 composeEulerXYZ(FiguraVec3 first, FiguraVec3 second) {
        if (first == null || second == null) {
            throw new LuaError("composeEulerXYZ requires two rotation vectors");
        }
        for (double component : new double[]{first.x, first.y, first.z, second.x, second.y, second.z}) {
            if (!Float.isFinite((float) component)) {
                throw new LuaError("composeEulerXYZ requires finite float rotation components");
            }
        }
        float radiansPerDegree = (float) (Math.PI / 180);
        var composition = eulerXYZRotation(first, radiansPerDegree)
                .mul(eulerXYZRotation(second, radiansPerDegree));
        var angles = composition.getEulerAnglesXYZ(new org.joml.Vector3f()).div(radiansPerDegree);
        return FiguraVec3.of(angles.x, angles.y, angles.z);
    }

    private static org.joml.Quaternionf eulerXYZRotation(FiguraVec3 angles, float radiansPerDegree) {
        return new org.joml.Quaternionf().rotationXYZ((float) angles.x * radiansPerDegree,
                (float) angles.y * radiansPerDegree, (float) angles.z * radiansPerDegree);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            value = "matrices.sample4",
            overloads = @LuaMethodOverload(
                    argumentTypes = {org.luaj.vm2.LuaTable.class, org.luaj.vm2.LuaTable.class,
                            Double.class, Integer.class},
                    argumentNames = {"rest", "keyframes", "time", "channels"}
            )
    )
    public static FiguraMat4 sample4(org.luaj.vm2.LuaTable rest, org.luaj.vm2.LuaTable frames,
                                    double time, int channels) {
        if (!Double.isFinite(time) || channels < 0 || channels > 7) {
            throw new LuaError("sample4 requires finite time and a channel mask from 0 to 7");
        }
        double[] position = transformComponents(rest.get("position"), 3);
        double[] rotation = transformComponents(rest.get("quaternion"), 4);
        double[] scale = transformComponents(rest.get("scale"), 3);
        if (frames != null && frames.length() > 0) {
            int low = 1;
            int high = frames.length();
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                if (frames.get(middle).get("time").checkdouble() <= time) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            var first = frames.get(low).checktable();
            var last = frames.get(Math.min(low + 1, frames.length())).checktable();
            double start = first.get("time").checkdouble();
            double duration = last.get("time").checkdouble() - start;
            double amount = duration == 0 ? 0 : net.minecraft.util.Mth.clamp((time - start) / duration, 0, 1);
            if ((channels & 1) != 0 && !first.get("position").isnil()) {
                position = interpolateComponents(first, last, "position", 3, amount);
            }
            if ((channels & 2) != 0 && !first.get("quaternion").isnil()) {
                double[] a = transformComponents(first.get("quaternion"), 4);
                double[] b = last.get("quaternion").isnil()
                        ? a : transformComponents(last.get("quaternion"), 4);
                var quaternion = normalizedQuaternion(a).slerp(normalizedQuaternion(b), amount);
                rotation = new double[]{quaternion.x, quaternion.y, quaternion.z, quaternion.w};
            }
            if ((channels & 4) != 0 && !first.get("scale").isnil()) {
                scale = interpolateComponents(first, last, "scale", 3, amount);
            }
        }
        return compose4(FiguraVec3.of(position[0], position[1], position[2]),
                FiguraVec4.of(rotation[0], rotation[1], rotation[2], rotation[3]),
                FiguraVec3.of(scale[0], scale[1], scale[2]));
    }

    private static double[] transformComponents(org.luaj.vm2.LuaValue value, int count) {
        var table = value.checktable();
        double[] result = new double[count];
        for (int index = 0; index < count; index++) {
            result[index] = table.get(index + 1).checkdouble();
            if (!Double.isFinite(result[index])) {
                throw new LuaError("sample4 requires finite transform components");
            }
        }
        return result;
    }

    private static org.joml.Quaterniond normalizedQuaternion(double[] components) {
        var quaternion = new org.joml.Quaterniond(
                components[0], components[1], components[2], components[3]);
        if (!(quaternion.lengthSquared() > 0) || !Double.isFinite(quaternion.lengthSquared())) {
            throw new LuaError("sample4 requires a nonzero finite quaternion");
        }
        return quaternion.normalize();
    }

    private static double[] interpolateComponents(org.luaj.vm2.LuaTable first,
                                                  org.luaj.vm2.LuaTable last,
                                                  String channel, int count, double amount) {
        double[] a = transformComponents(first.get(channel), count);
        double[] b = last.get(channel).isnil() ? a : transformComponents(last.get(channel), count);
        for (int index = 0; index < count; index++) {
            a[index] += (b[index] - a[index]) * amount;
        }
        return a;
    }

    // -- ROTATION MATRICES --// 
    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.rotation2"
    )
    public static FiguraMat2 rotation2(double degrees) {
        FiguraMat2 mat = FiguraMat2.of();
        mat.rotate(degrees);
        return mat;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            value = "matrices.rotation3"
    )
    public static FiguraMat3 rotation3(Object x, Double y, Double z) {
        FiguraVec3 angles = LuaUtils.parseVec3("rotation3", x, y, z);
        FiguraMat3 result = FiguraMat3.of();
        result.rotateZYX(angles);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.x_rotation3"
    )
    public static FiguraMat3 xRotation3(double degrees) {
        FiguraMat3 result = FiguraMat3.of();
        result.rotateX(degrees);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.y_rotation3"
    )
    public static FiguraMat3 yRotation3(double degrees) {
        FiguraMat3 result = FiguraMat3.of();
        result.rotateY(degrees);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.z_rotation3"
    )
    public static FiguraMat3 zRotation3(double degrees) {
        FiguraMat3 result = FiguraMat3.of();
        result.rotateZ(degrees);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            value = "matrices.rotation4"
    )
    public static FiguraMat4 rotation4(Object x, Double y, Double z) {
        FiguraVec3 angles = LuaUtils.parseVec3("rotation4", x, y, z);
        FiguraMat4 result = FiguraMat4.of();
        result.rotateZYX(angles);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.x_rotation4"
    )
    public static FiguraMat4 xRotation4(double degrees) {
        FiguraMat4 result = FiguraMat4.of();
        result.rotateX(degrees);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.y_rotation4"
    )
    public static FiguraMat4 yRotation4(double degrees) {
        FiguraMat4 result = FiguraMat4.of();
        result.rotateY(degrees);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Double.class,
                    argumentNames = "angle"
            ),
            value = "matrices.z_rotation4"
    )
    public static FiguraMat4 zRotation4(double degrees) {
        FiguraMat4 result = FiguraMat4.of();
        result.rotateZ(degrees);
        return result;
    }

    // -- SCALE MATRICES --// 
    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec2.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class},
                            argumentNames = {"x", "y"}
                    )
            },
            value = "matrices.scale2"
    )
    public static FiguraMat2 scale2(Object x, Double y) {
        FiguraVec2 vec = LuaUtils.parseVec2("scale2", x, y, 1, 1);
        FiguraMat2 result = FiguraMat2.of();
        result.scale(vec);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            value = "matrices.scale3"
    )
    public static FiguraMat3 scale3(Object x, Double y, Double z) {
        FiguraVec3 scale = LuaUtils.parseOneArgVec("scale3", x, y, z, 1d);
        FiguraMat3 result = FiguraMat3.of();
        result.scale(scale);
        return result;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            value = "matrices.scale4"
    )
    public static FiguraMat4 scale4(Object x, Double y, Double z) {
        FiguraVec3 scale = LuaUtils.parseOneArgVec("scale4", x, y, z, 1d);
        FiguraMat4 result = FiguraMat4.of();
        result.scale(scale);
        return result;
    }

    // -- TRANSLATION MATRICES --// 
    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec2.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class},
                            argumentNames = {"x", "y"}
                    )
            },
            value = "matrices.translate3"
    )
    public static FiguraMat3 translate3(Object x, Double y) {
        FiguraVec2 offset = LuaUtils.parseVec2("translate3", x, y);
        FiguraMat3 result = FiguraMat3.of();
        result.translate(offset);
        return result;
    }
    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "vec"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            value = "matrices.translate4"
    )
    public static FiguraMat4 translate4(Object x, Double y, Double z) {
        FiguraVec3 offset = LuaUtils.parseVec3("translate4", x, y, z);
        FiguraMat4 result = FiguraMat4.of();
        result.translate(offset);
        return result;
    }

    @Override
    public String toString() {
        return "MatricesAPI";
    }
}
