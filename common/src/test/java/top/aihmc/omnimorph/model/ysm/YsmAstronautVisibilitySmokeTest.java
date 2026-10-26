package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.nio.file.Path;

/**
 * Real-resource CPU visibility check for the authored alternate-form switch. While roaming.a is 0
 * the author's parallel1 layer hides the FOX branch; the first native movement state must not stop
 * that always-on layer. No GPU or live-input validation.
 */
public final class YsmAstronautVisibilitySmokeTest {
    public static void main(String[] args) throws Exception {
        YsmMaterialNativeSmokeTest.replayAcceleration(Path.of(args[0]), runtime -> {
            var context = runtime.owner().getMolangContext();
            context.variables.setPath("roaming.a", 0f);
            context.variables.setPath("roaming.helmet", 0f);
            var state = new LivingEntityRenderState();
            for (int frame = 0; frame < 40; frame++) {
                state.ageInTicks = frame;
                // Idle first, then walk: the idle-to-walk native change stops default actions.
                state.walkAnimationSpeed = frame < 10 ? 0f : 0.2f;
                YsmRenderFrameClock.beginFrame();
                runtime.updateAnimations(state, null);
                var fox = runtime.getPart("FOX");
                if (!fox.hiddenScaleRaw()) {
                    throw new AssertionError("FOX branch exposed in human mode at frame " + frame
                            + " (scale " + fox.animScaleRaw() + ")");
                }
                var body = runtime.getPart("AllBody");
                if (!body.visibleRaw()) {
                    throw new AssertionError("human body hidden at frame " + frame);
                }
            }
            System.out.println(
                    "PASS astronaut visibility: the authored parallel layer survives the idle-to-walk state change");
        });
    }
}
