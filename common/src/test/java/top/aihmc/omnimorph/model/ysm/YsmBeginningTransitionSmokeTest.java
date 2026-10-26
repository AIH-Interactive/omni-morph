package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Exercises real clip events and both controller entry points with a CPU timeline. */
public final class YsmBeginningTransitionSmokeTest {
    public static void main(String[] args) throws Exception {
        testDeclaredController();
        testFunctionController();
        testOvershootAndCancellation();
        System.out.println("PASS beginning transitions: frozen clocks, zero/mid events, refresh, speed, overshoot and cancellation");
    }

    private static void testDeclaredController() throws Exception {
        Fixture fixture = new Fixture();
        fixture.player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"fixture":{"states":{"default":{
                  "animations":["clip"],"blend_transition":0.5,"on_entry":["mark('entry');"]
                }}}}}
                """, fixture.engine));
        fixture.player.controllerRuntime().update(fixture.evaluator, 0f);
        var playing = fixture.player.getControllerAnimation("fixture", "clip");
        require(fixture.events.equals(List.of("entry")), "entry expressions run now but clip events wait for beginning transition");
        fixture.events.clear();
        fixture.advance(.25f);
        require(playing.time == 0f && fixture.events.isEmpty(), "declared clip clock and events freeze during transition");
        fixture.player.controllerRuntime().update(fixture.evaluator, .25f);
        fixture.advance(.25f);
        require(playing.time == 0f && fixture.events.equals(List.of("zero")),
                "exact transition boundary dispatches zero events once; refresh does not extend delay");
        fixture.advance(.125f);
        require(playing.time == .125f && fixture.events.equals(List.of("zero", "mid")),
                "clip starts running after the transition with its original event schedule");
    }

    private static void testFunctionController() throws Exception {
        Fixture fixture = new Fixture();
        fixture.player.setControllerFunctionTransitionLength("fixture", .5f);
        var playing = fixture.player.playControllerFunctionAnimation("fixture", "clip",
                YsmAnimationClip.LoopMode.LOOP, 1f, fixture.player.controllerFunctionTransitionLength("fixture"));
        fixture.advance(.25f);
        require(playing.time == 0f && fixture.events.isEmpty(), "function controller also freezes clock and events");
        fixture.player.playControllerFunctionAnimation("fixture", "clip", playing.loopMode, 1f, .5f);
        fixture.advance(.25f);
        require(playing.time == 0f && fixture.events.equals(List.of("zero")), "same function animation refresh preserves delay progress");
        fixture.advance(.125f);
        require(playing.time == .125f && fixture.events.equals(List.of("zero", "mid")), "function animation starts after delay");
        fixture.player.requestControllerFunctionReload("fixture");
        var reloaded = fixture.player.playControllerFunctionAnimation("fixture", "clip", playing.loopMode, 1f, .5f);
        fixture.events.clear();
        fixture.advance(.25f);
        require(reloaded != playing && reloaded.time == 0f && fixture.events.isEmpty(), "explicit reload starts a fresh transition");
    }

    private static void testOvershootAndCancellation() throws Exception {
        Fixture fixture = new Fixture();
        var playing = fixture.player.playControllerFunctionAnimation("fixture", "clip",
                YsmAnimationClip.LoopMode.ONCE, 2f, .5f);
        fixture.advance(.75f);
        require(playing.time == .5f && fixture.events.equals(List.of("zero", "mid")),
                "wall-clock delay is independent of speed and overshoot advances only the remaining interval");
        Fixture cancelled = new Fixture();
        cancelled.player.playControllerAnimation("fixture", "clip", null, 1f, .5f, cancelled.evaluator);
        cancelled.player.fadeOutControllerAnimation("fixture", "clip", 0f);
        cancelled.advance(1f);
        require(cancelled.events.isEmpty(), "cancelling a pending transition never emits its events");
        Fixture immediate = new Fixture();
        immediate.player.playControllerAnimation("fixture", "clip", null, 1f, 0f, immediate.evaluator);
        require(immediate.events.equals(List.of("zero")), "zero-duration transition still dispatches initial events immediately");
    }

    private static final class Fixture {
        private final List<String> events = new ArrayList<>();
        private final MolangEngine engine;
        private final ExpressionEvaluator<?> evaluator = ExpressionEvaluator.evaluator(new Avatar.MolangContext(null));
        private final YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> value) {
            }
        };

        private Fixture() {
            Function mark = (execution, arguments) -> {
                events.add(arguments.getAsString(execution, 0));
                return 0f;
            };
            engine = MolangEngine.fromCustomBinding(name -> name.equals("mark") ? mark : null);
            player.registerAnimations(YsmAnimationParser.parse("""
                    {"animations":{"clip":{"animation_length":1,"loop":true,
                      "bones":{"root":{"rotation":{"0":[0,0,0],"1":[90,0,0]}}},
                      "timeline":{"0":"mark('zero');","0.125":"mark('mid');"}}}}
                    """, engine));
        }

        private void advance(float delta) throws Exception {
            Method advance = YsmAnimationPlayer.class.getDeclaredMethod("advanceAnimationTimeline",
                    float.class, ExpressionEvaluator.class);
            advance.setAccessible(true);
            advance.invoke(player, delta, evaluator);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
