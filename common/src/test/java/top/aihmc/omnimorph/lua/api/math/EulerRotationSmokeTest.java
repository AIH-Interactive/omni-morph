package top.aihmc.omnimorph.lua.api.math;

import org.figuramc.figura.lua.api.math.MatricesAPI;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.joml.Matrix4f;
import org.luaj.vm2.LuaError;

/** Rotation composition remains independent of any avatar format or decoration. */
public final class EulerRotationSmokeTest {
    public static void verify() {
        float radiansPerDegree = (float) (Math.PI / 180);
        for (float[] first : new float[][]{{0, 0, 0}, {10, 20, 30}, {-75, 40, 110}}) {
            for (float[] second : new float[][]{{90, 0, 0}, {0, -45, 25}, {13, -27, 43}}) {
                var input = FiguraVec3.of(first[0], first[1], first[2]);
                var result = MatricesAPI.composeEulerXYZ(input,
                        FiguraVec3.of(second[0], second[1], second[2]));
                var expected = new Matrix4f().rotationXYZ(first[0] * radiansPerDegree,
                        first[1] * radiansPerDegree, first[2] * radiansPerDegree)
                        .rotateXYZ(second[0] * radiansPerDegree,
                                second[1] * radiansPerDegree, second[2] * radiansPerDegree);
                var actual = new Matrix4f().rotationXYZ((float) result.x * radiansPerDegree,
                        (float) result.y * radiansPerDegree, (float) result.z * radiansPerDegree);
                if (!actual.equals(expected, 0.00002f)
                        || input.x != first[0] || input.y != first[1] || input.z != first[2]) {
                    throw new AssertionError("XYZ composition must preserve geometry and its input");
                }
            }
        }
        reject(() -> MatricesAPI.composeEulerXYZ(null, FiguraVec3.of()));
        reject(() -> MatricesAPI.composeEulerXYZ(FiguraVec3.of(), FiguraVec3.of(Double.NaN, 0, 0)));
        reject(() -> MatricesAPI.composeEulerXYZ(FiguraVec3.of(1e100, 0, 0), FiguraVec3.of()));
        System.out.println("PASS generic XYZ Euler composition: order, matrix geometry, inputs and errors");
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid Euler input must raise LuaError");
    }
}
