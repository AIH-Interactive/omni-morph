package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.decoration.ArmorStand;
import top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

/** Native regression checks; no window or Minecraft visual pass is claimed. */
public final class YsmPublicPoseSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        YsmMaterialNativeSmokeTest.replayModel(root.resolve("DS鲸鱼娘.ysm"), runtime -> {
            var entity = groundedEntity();
            for (int tick = 0; tick < 6; tick++) {
                entity.tickCount = tick;
                YsmRenderFrameClock.beginFrame();
                runtime.updateAnimations(new YsmEntityFrame(tick, 0, 0, 0, 0), entity, true);
            }
            for (boolean left : new boolean[]{false, true}) {
                int actual = armVertices(runtime, left);
                var hidden = new java.util.LinkedHashMap<YsmModelPart, Boolean>();
                for (var bone : runtime.getArmGeometry().bones.values()) {
                    if (!YsmBoneMapper.isFirstPersonArmCandidate(bone.name, runtime.armRoleOf(bone.name), left)) {
                        var part = runtime.getArmPart(bone.name);
                        hidden.put(part, part.visibleRaw());
                        part.setVisible(false);
                    }
                }
                int oneHand;
                try {
                    oneHand = armVertices(runtime, left);
                } finally {
                    hidden.forEach(YsmModelPart::setVisible);
                }
                if (oneHand == 0 || actual != oneHand) {
                    throw new AssertionError("opposite hand leaked through shared root: left=" + left
                            + " actual=" + actual + " oneHand=" + oneHand);
                }
            }
            System.out.println("PASS real YSM shared arm hierarchy: each hand omits the opposite subtree; GPU pending");
        });
        YsmMaterialNativeSmokeTest.replayModel(root.resolve("少女-哥伦比娅.ysm"), runtime -> {
            var entity = groundedEntity();
            YsmEntityFrame state = null;
            for (float pitch : new float[]{0, -80, 80}) {
                for (int tick = 0; tick < 60; tick++) {
                    entity.tickCount++;
                    state = new YsmEntityFrame(entity.tickCount, 0, 40f, pitch, 0);
                    YsmRenderFrameClock.beginFrame();
                    runtime.updateAnimations(state, entity, false);
                }
                var head = runtime.getPart("Head");
                if (pitch == -80f && head.animRotRaw().x > -60f) {
                    throw new AssertionError("steady upward head tracking is missing after authored animation sampling");
                }
                if (pitch == 80f && head.animRotRaw().x < 60f) {
                    throw new AssertionError("steady downward head tracking is missing after authored animation sampling");
                }
                if (head.animRotRaw().y < 25f) {
                    throw new AssertionError("relative yaw is not added after the authored head pose");
                }
                double tracked = head.animRotRaw().x;
                runtime.updateAnimations(state, entity, false);
                if (Math.abs(head.animRotRaw().x - tracked) > 3) {
                    throw new AssertionError("a repeated body pass accumulates head tracking");
                }
                try (var evaluation = runtime.boneQueries().beginEvaluation()) {
                    var query = new YsmBoneQuery(head, YsmBoneQuery.Channel.ROTATION, runtime.boneQueries(), false);
                    float authored = ((Number) query.getProperty(
                            top.aihmc.omnimorph.molang.storage.StringPool.computeIfAbsent("x"))).floatValue();
                    double offset = runtime.owner().getMolangContext().ysm_head_pitch;
                    if (Math.abs(authored - head.animRotRaw().x - offset) > 3) {
                        throw new AssertionError("render head tracking feeds back into authored bone queries");
                    }
                }
            }
            System.out.println("PASS real YSM steady head pitch/yaw, repeated pass and authored query isolation; hair/GPU visual checks pending");
        });
    }

    private static ArmorStand groundedEntity() {
        return new ArmorStand(null, 0, 0, 0) {
            @Override
            public boolean onGround() {
                return true;
            }

            @Override
            public boolean onClimbable() {
                return false;
            }
        };
    }

    private static int armVertices(YsmModelRuntime runtime, boolean left) {
        int[] vertices = {0};
        var consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, values) -> {
                    if (method.getName().equals("vertex")) {
                        vertices[0]++;
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        runtime.renderer().renderFirstPersonArm(new PoseStack(), type -> consumer, 0, left,
                runtime.captureRenderPose(true));
        return vertices[0];
    }
}
