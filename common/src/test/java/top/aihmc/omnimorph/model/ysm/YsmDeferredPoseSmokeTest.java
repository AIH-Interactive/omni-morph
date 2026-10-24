package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import top.aihmc.omnimorph.render.acceleration.AccelerationOwner;
import top.aihmc.omnimorph.render.acceleration.AccelerationService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class YsmDeferredPoseSmokeTest {
    private YsmDeferredPoseSmokeTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        YsmModelPart body = new YsmModelPart("Root", null, new float[3], new float[3]);
        body.addAnimPos(16, 0, 0);
        body.addAnimRot(0, 0, 90);
        body.posRaw().x = 16;
        Matrix4f submittedScale = new Matrix4f().scaling(2f);
        YsmRenderPose bodyPose = YsmRenderPose.capture(List.of(body), submittedScale);
        YsmModelPart arm = new YsmModelPart("Root", null, new float[3], new float[3]);
        arm.addAnimPos(32, 0, 0);
        YsmRenderPose armPose = YsmRenderPose.capture(List.of(arm), new Matrix4f());
        List<Matrix4f> drawn = new ArrayList<>();
        Runnable drawBody = () -> drawn.add(matrix(bodyPose));
        Runnable drawArm = () -> drawn.add(matrix(armPose));

        // Later render passes and model code overwrite every mutable source used by the submissions.
        body.resetAnimPose();
        body.posRaw().x = 1024;
        body.rotRaw().z = 45;
        body.scaleRaw().x = 9;
        body.setVisible(false);
        arm.resetAnimPose();
        arm.addAnimPos(320, 0, 0);
        submittedScale.scaling(9f);
        drawArm.run();
        drawBody.run();
        assertMatrix(drawn.get(0), new float[]{
                1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -2, 0, 0, 1
        }, "arm submission retains its own later-drawn pose");
        // Body animation translates -1 on X, then rotates the model's -1 X edit onto -1 Y.
        assertMatrix(drawn.get(1), new float[]{
                0, 2, 0, 0, -2, 0, 0, 0, 0, 0, 2, 0, -2, -2, 0, 1
        }, "body submission retains original animation, model edit and overall scale");
        require(bodyPose.part("root").visibleRaw(), "submitted visibility remains true");
        YsmModelPart detached = bodyPose.part("Root");
        detached.addAnimPos(999, 0, 0);
        require(bodyPose.part("Root").animPosRaw().x == 16, "returned render view cannot mutate immutable snapshot");
        testHiddenHierarchy();
        testStaleSubmission();
        testSleepingRoot();
        System.out.println("M3 deferred body/arm matrices, owned scale/visibility and immutable submission pose checks passed.");
    }

    private static Matrix4f matrix(YsmRenderPose pose) {
        try {
            Method transform = YsmRenderer.class.getDeclaredMethod("applyBoneTransform",
                    YsmGeometry.Bone.class, YsmModelPart.class, PoseStack.class);
            transform.setAccessible(true);
            PoseStack stack = new PoseStack();
            pose.applyModelScale(stack);
            YsmGeometry.Bone bone = new YsmGeometry.Bone("Root", null, new float[3], new float[3]);
            transform.invoke(new YsmRenderer(null), bone, pose.part("Root"), stack);
            return new Matrix4f(stack.last().pose());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("production bone transform failed", failure);
        }
    }

    private static void testSleepingRoot() {
        var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
        state.pose = net.minecraft.world.entity.Pose.SLEEPING;
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            state.bedOrientation = direction;
            var stack = new PoseStack();
            YsmRenderer.applyEntityRotation(stack, state, 37f);
            var up = stack.last().pose().transformDirection(new org.joml.Vector3f(0, 1, 0));
            require(Math.abs(up.y) < 0.0001f && Math.abs(up.length() - 1f) < 0.0001f,
                    "all bed directions rotate the native body onto the bed plane");
            float expected = switch (direction) {
                case SOUTH -> 90f;
                case WEST -> 0f;
                case NORTH -> 270f;
                case EAST -> 180f;
                default -> throw new AssertionError("horizontal bed required");
            };
            var reference = new Matrix4f().rotateY((float) Math.toRadians(expected))
                    .rotateZ((float) Math.toRadians(90)).rotateY((float) Math.toRadians(270));
            require(stack.last().pose().equals(reference, 0.0001f), "bed heading matches the reference root convention");
        }
        state.pose = net.minecraft.world.entity.Pose.STANDING;
        var standing = new PoseStack();
        YsmRenderer.applyEntityRotation(standing, state, 37f);
        require(standing.last().pose().equals(new Matrix4f().rotateY((float) Math.toRadians(143)), 0.0001f),
                "standing keeps its original root transform");
    }

    private static void testStaleSubmission() {
        AccelerationService service = AccelerationService.INSTANCE;
        AccelerationOwner owner = new AccelerationOwner();
        var lease = service.capture(owner, "body");
        YsmRenderPose pose = YsmRenderPose.capture(List.of(), new Matrix4f(), false, lease);
        service.beginFrame();
        YsmRenderer renderer = new YsmRenderer(null);
        require(!renderer.render(null, null, 0, null, pose), "old body frame rejected before renderer access");
        require(!renderer.renderFirstPersonArm(null, null, 0, true, pose), "old arm frame rejected before renderer access");
    }

    private static void testHiddenHierarchy() {
        YsmGeometry geometry = new YsmGeometry();
        geometry.bones.put("parent", new YsmGeometry.Bone("parent", null, new float[3], new float[3]));
        geometry.bones.put("child", new YsmGeometry.Bone("child", "parent", new float[3], new float[3]));
        YsmModelPart parent = new YsmModelPart("parent", null, new float[3], new float[3]);
        YsmModelPart child = new YsmModelPart("child", parent, new float[3], new float[3]);
        parent.mulAnimScale(0, 0, 0);
        YsmRenderPose hidden = YsmRenderPose.capture(List.of(parent, child), new Matrix4f());
        parent.resetAnimPose();
        require(!hidden.visibleInHierarchy("child", geometry), "attachment retains submission-time hidden ancestor");
        YsmRenderPose shown = YsmRenderPose.capture(List.of(parent, child), new Matrix4f());
        parent.setVisible(false);
        require(shown.visibleInHierarchy("child", geometry), "later hiding does not affect an earlier visible attachment");
    }

    private static void assertMatrix(Matrix4f actual, float[] expected, String message) {
        float[] values = actual.get(new float[16]);
        for (int index = 0; index < values.length; index++) {
            require(Float.isFinite(values[index]) && Math.abs(values[index] - expected[index]) < 0.00002f,
                    message + " coefficient " + index + ": " + values[index]);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
