package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Checks the production view composition over camera-facing quad corners at multiple angles. */
public final class ParticleWorldViewSmokeTest {
    public static void main(String[] args) {
        for (float yaw : new float[] {0f, 45f, 90f, 180f, 270f}) {
            for (float pitch : new float[] {-75f, 0f, 60f}) {
                Quaternionf camera = new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0f);
                PoseStack view = new PoseStack();
                view.mulPose(camera.conjugate(new Quaternionf()));
                Matrix4f originalView = new Matrix4f(view.last().pose());
                PoseStack global = new PoseStack();
                global.translate(100, -50, 200);
                Matrix4f originalGlobal = new Matrix4f(global.last().pose());
                global.pushPose();
                try {
                    CustomParticleRuntime.applyWorldView(global, view);
                    for (int x : new int[] {-1, 1}) {
                        for (int y : new int[] {-1, 1}) {
                            Vector3f corner = new Vector3f(x, y, 0).rotate(camera);
                            global.last().pose().transformPosition(corner);
                            require(corner.distance(new Vector3f(x + 100, y - 50, 200)) < 0.0001f,
                                    "billboard loses view cancellation at yaw=" + yaw + " pitch=" + pitch);
                        }
                    }
                    require(view.last().pose().equals(originalView), "incoming view is immutable");
                } finally {
                    global.popPose();
                }
                require(global.last().pose().equals(originalGlobal), "global stack restores exactly");
            }
        }
        System.out.println("PASS production world-view composition at 15 camera angles, camera-relative vertices and stack restoration");
        System.out.println("Not covered: game pixels or shader-pack integration");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
