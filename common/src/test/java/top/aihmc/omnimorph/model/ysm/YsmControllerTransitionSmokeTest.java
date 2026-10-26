package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerState;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Production JSON/parser/player path; expected sequences follow Sparkle's evaluateTransitions/process. */
public final class YsmControllerTransitionSmokeTest {
    public static void main(String[] args) {
        testSelfTransition();
        testInitialSlotBoundary();
        testVisitedTargetStopsSearch();
        testDeclaredSlotsStopChain();
        testChildEntryHasNoSlots();
        testSoundSelfTransition();
        testSoundGateBeforeVisited();
        testParseFailureIsFalse();
        System.out.println("PASS reference controller transitions: self re-entry, clocks, slot boundaries, cycles and sound gates");
    }

    private static void testSelfTransition() {
        Fixture fixture = new Fixture("""
                {"default":{"animations":["clip"],"blend_transition":0.2,
                  "on_entry":["mark('entry');"],"on_exit":["mark('exit');"],
                  "transitions":[{"default":"q.trigger"}]}}
                """);
        fixture.update();
        var outgoing = fixture.player.getControllerAnimation("fixture", "clip");
        outgoing.time = 3f;
        fixture.trace.clear();
        fixture.trigger[0] = 1f;
        fixture.update();
        var incoming = fixture.player.getControllerAnimation("fixture", "clip");
        require(incoming != null && incoming != outgoing && incoming.time == 0f,
                "self-transition restarts even a completed ONCE clip");
        require(outgoing.time == 3f && outgoing.targetWeight == 0f
                        && fixture.player.getActiveAnimations().containsValue(outgoing),
                "self-transition retains only the outgoing fade contribution");
        require(fixture.player.controllerRuntime().stateTime("fixture") == 0f,
                "self-transition resets the state clock");
        require(fixture.trace.equals(List.of("stop", "exit", "entry")),
                "self-transition performs exactly one stop/exit/entry per update: " + fixture.trace);
        fixture.update();
        require(fixture.player.getControllerAnimation("fixture", "clip") != incoming,
                "a true non-sound self-transition can re-enter on the next update");
    }

    private static void testInitialSlotBoundary() {
        Fixture fixture = new Fixture("""
                {"default":{"animations":["clip"],"transitions":[{"next":"1"}]},
                 "next":{"animations":["clip"]}}
                """);
        fixture.update();
        require(fixture.state().equals("default"), "initial entry with slots stops the first transition chain");
        fixture.update();
        require(fixture.state().equals("next"), "initial state's outgoing transitions run on the next update");
    }

    private static void testVisitedTargetStopsSearch() {
        Fixture fixture = new Fixture("""
                {"default":{"on_entry":["mark('default');"],
                  "transitions":[{"other":"q.trigger"},{"tail":"q.trigger && mark('late-condition')"}]},
                 "other":{"on_entry":["mark('other');"],"transitions":[{"default":"1"}]},
                 "tail":{"animations":["clip"]}}
                """);
        fixture.update();
        fixture.trace.clear();
        fixture.trigger[0] = 1f;
        fixture.update();
        require(fixture.state().equals("default"), "the previously current state may be re-entered once");
        require(fixture.trace.equals(List.of("stop", "other", "stop", "default")),
                "an already visited true target ends the search before later conditions: " + fixture.trace);
    }

    private static void testDeclaredSlotsStopChain() {
        for (String slots : List.of("[\"missing\"]", "[\"empty\"]", "[{\"clip\":\"0\"}]")) {
            Fixture fixture = new Fixture("""
                    {"default":{"transitions":[{"slot":"1"}]},
                     "slot":{"animations":%s,"transitions":[{"done":"1"}]},
                     "done":{"animations":["clip"]}}
                    """.formatted(slots));
            fixture.update();
            require(fixture.state().equals("slot"), "declared slots stop the chain regardless of playback: " + slots);
            fixture.update();
            require(fixture.state().equals("done"), "slot state may transition on a later update: " + slots);
        }
    }

    private static void testChildEntryHasNoSlots() {
        Fixture fixture = new Fixture("""
                {"default":{"transitions":[{"ysm-entry-child":"1"}]},
                 "ysm-entry-child":{"animations":["clip"],"transitions":[{"done":"1"}]},
                 "done":{"animations":["clip"]}}
                """);
        fixture.update();
        require(fixture.state().equals("done"), "child-entry states have no parent slots and do not stop the chain");
    }

    private static void testSoundSelfTransition() {
        Fixture fixture = new Fixture("""
                {"default":{"animations":["clip"],"sound_effects":["bell"],
                  "on_entry":["mark('entry');"],"on_exit":["mark('exit');"],
                  "transitions":[{"default":"q.trigger"}]}}
                """);
        fixture.update();
        fixture.trace.clear();
        fixture.trigger[0] = 1f;
        fixture.update();
        var first = fixture.player.getControllerAnimation("fixture", "clip");
        require(fixture.trace.equals(List.of("stop", "exit", "entry", "sound:bell")),
                "sound-bearing self-transition stops old sounds and requests the new entry sound");
        fixture.update();
        require(fixture.trace.size() == 4 && fixture.player.getControllerAnimation("fixture", "clip") == first,
                "held true sound self-transition does not chatter");
        fixture.trigger[0] = 0f;
        fixture.update();
        fixture.trigger[0] = 1f;
        fixture.update();
        require(fixture.trace.size() == 8 && fixture.player.getControllerAnimation("fixture", "clip") != first,
                "a false condition rearms sound-bearing self re-entry");
    }

    private static void testSoundGateBeforeVisited() {
        Fixture fixture = new Fixture("""
                {"default":{"transitions":[{"sound":"q.trigger"}]},
                 "sound":{"sound_effects":["bell"],"transitions":[{"middle":"1"}]},
                 "middle":{"transitions":[{"sound":"1"},{"tail":"1"}]},
                 "tail":{"animations":["clip"]}}
                """);
        fixture.update();
        fixture.trigger[0] = 1f;
        fixture.update();
        require(fixture.state().equals("middle"), "a newly gated visited target ends the current search");
        fixture.update();
        require(fixture.state().equals("tail"), "the sound gate is consumed before visited rejection and skips its held edge");
    }

    /** D009: Sparkle MolangParser returns MolangValue.failed (constant 0) for an unparsable expression. */
    private static void testParseFailureIsFalse() {
        Fixture fixture = new Fixture("""
                {"default":{"transitions":[{"broken":"((("}]},
                 "broken":{"animations":[{"clip":"((("}]}}
                """);
        fixture.update();
        fixture.update();
        require(fixture.state().equals("default"), "an unparsable transition condition is false, not unconditional");

        Fixture weighted = new Fixture("""
                {"default":{"animations":[{"clip":"((("}]}}
                """);
        weighted.update();
        var clip = weighted.player.getControllerAnimation("fixture", "clip");
        require(clip == null || clip.targetWeight == 0f, "an unparsable animation weight is 0, not full weight");
    }

    private static final class Fixture {
        private final List<String> trace = new ArrayList<>();
        private final float[] trigger = {0f};
        private final MolangEngine engine;
        private final YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
                // State expressions, not function controllers, are under test.
            }

            @Override
            public void stopControllerSounds(String controllerName) {
                trace.add("stop");
                super.stopControllerSounds(controllerName);
            }

            @Override
            public void playControllerSounds(String controllerName, YsmControllerState state) {
                state.soundEffects().forEach(sound -> trace.add("sound:" + sound));
            }
        };

        private Fixture(String states) {
            Variable condition = context -> trigger[0];
            ObjectBinding query = name -> name.equals("trigger") ? condition : null;
            Function mark = (context, arguments) -> {
                trace.add(arguments.getAsString(context, 0));
                return 1f;
            };
            engine = MolangEngine.fromCustomBinding(name -> switch (name) {
                case "q" -> query;
                case "mark" -> mark;
                default -> null;
            });
            var clip = new YsmAnimationClip("clip", 2f, YsmAnimationClip.LoopMode.ONCE);
            player.registerAnimations(Map.of("clip", clip));
            player.registerControllers(YsmAnimationControllerParser.parse(
                    "{\"animation_controllers\":{\"fixture\":{\"states\":" + states + "}}}", engine));
            trace.clear();
        }

        private void update() {
            player.controllerRuntime().update(ExpressionEvaluator.evaluator(trigger), .1f);
        }

        private String state() {
            return player.controllerRuntime().currentState("fixture");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
