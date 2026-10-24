package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationFrameClock;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;
import org.joml.Matrix4f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Drives the production player's internal CPU steps; game/entity/window validation remains separate. */
public final class YsmPlayerTimelineSmokeTest {
    private YsmPlayerTimelineSmokeTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        List<String> trace = new ArrayList<>();
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        context.variables.setPath("weight", 1f);
        context.variables.setPath("switch", 0f);
        ObjectBinding query = name -> (Variable) execution -> {
            Avatar.MolangContext value = (Avatar.MolangContext) execution.entity();
            return switch (name) {
                case "anim_time" -> value.anim_time;
                case "is_first_person" -> value.is_first_person;
                default -> value.variables.getPath(name);
            };
        };
        Function mark = (execution, arguments) -> {
            Avatar.MolangContext value = (Avatar.MolangContext) execution.entity();
            trace.add(value.controller.getPath("controller") + ":" + arguments.getAsString(execution, 0));
            return 0f;
        };
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> switch (name) {
            case "q" -> query;
            case "mark" -> mark;
            default -> null;
        });
        YsmModelRuntime runtime = cpuRuntime();
        YsmAnimationPlayer player = runtime.animations();
        player.registerAnimations(YsmAnimationParser.parse("""
                {"animations":{
                  "loop":{"animation_length":1,"loop":true,"blend_weight":"q.weight",
                    "bones":{"root":{"position":["16*q.anim_time+16*q.is_first_person",0,0]}},
                    "timeline":{"0":"mark('zero');","0.5":"mark('half');","1":"mark('end');"}},
                  "next":{"animation_length":1,"loop":true,"blend_weight":"q.weight",
                    "bones":{"root":{"position":[64,0,0],"rotation":[0,0,90]}},
                    "timeline":{"0":"mark('next.zero');","0.05":"mark('next.mid');"}}
                }}
                """, engine));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "player.main":{"initial_state":"active","states":{
                    "active":{"animations":["loop"],"on_exit":["mark('exit');"],"transitions":[{"next":"q.switch"}]},
                    "next":{"animations":["next"],"blend_transition":0.2,"blend_via_shortest_path":true}}},
                  "player.parallel_1":{"initial_state":"active","states":{"active":{"animations":[{"loop":"0.2"}]}}}
                }}
                """, engine));
        var evaluator = ExpressionEvaluator.evaluator(context);
        player.controllerRuntime().update(evaluator, 0f, false);
        require(trace.equals(List.of("player.main:zero", "player.parallel_1:zero")), "initial events have distinct owners");
        YsmAnimationFrameClock clock = new YsmAnimationFrameClock();
        float[] times = {0.05f, 0.3f, 0.55f, 0.8f, 0.05f};
        for (int frame = 0; frame < times.length; frame++) {
            for (int pass = 0; pass < 3; pass++) {
                var step = clock.step(frame + 1, 10f + frame * 5f + pass);
                if (step.advance()) {
                    advance(player, evaluator, step.deltaSeconds());
                    player.controllerRuntime().update(evaluator, step.deltaSeconds(), false);
                }
                boolean armPass = pass == 1;
                sample(player, evaluator, armPass);
                assertMatrix(matrix(runtime, armPass), -(times[frame] + (armPass ? 1f : 0f)) * 1.2f, 0f,
                        "frame " + frame + " pass " + pass);
            }
        }
        require(trace.equals(List.of("player.main:zero", "player.parallel_1:zero",
                "player.main:half", "player.parallel_1:half", "player.main:end", "player.main:zero",
                "player.parallel_1:end", "player.parallel_1:zero")), "loop endpoint event differential: " + trace);
        var retired = player.getControllerAnimation("player.main", "loop");
        float retiredTime = retired.time;
        context.variables.setPath("switch", 1f);
        player.controllerRuntime().update(evaluator, 0f, false);
        require(trace.subList(trace.size() - 2, trace.size()).equals(List.of("player.main:exit", "player.main:next.zero")),
                "state exit precedes incoming initial timeline event");
        advance(player, evaluator, 0.1f);
        player.controllerRuntime().update(evaluator, 0.1f, false);
        sample(player, evaluator, false);
        assertMatrix(matrix(runtime, false), -2.055f, 45f, "body transition retains its own outgoing snapshot");
        sample(player, evaluator, true);
        assertMatrix(matrix(runtime, true), -2.755f, 45f, "arm transition retains its own outgoing snapshot");
        equal(retiredTime, retired.time, "retired clip never advances");
        require(trace.getLast().equals("player.main:next.mid"), "only incoming clip emits transition timeline event");
        advance(player, evaluator, 0.1f);
        player.controllerRuntime().update(evaluator, 0.1f, false);
        for (float weight : new float[]{0f, 0.2f, 1f}) {
            context.variables.setPath("weight", weight);
            sample(player, evaluator, false);
            float time = player.getControllerAnimation("player.parallel_1", "loop").time;
            assertMatrix(matrix(runtime, false), -(4f + time * 0.2f) * weight, 90f * weight,
                    "live weight after completed transition " + weight);
        }
        testSmallLoopTime(player, evaluator);
        testNestedChildTransitions(engine, trace);
        System.out.println("M3 production player CPU timeline, actual body/arm matrices, retired events and repeated-pass differential passed.");
    }

    private static void testNestedChildTransitions(MolangEngine engine, List<String> trace)
            throws ReflectiveOperationException {
        trace.clear();
        YsmModelRuntime runtime = cpuRuntime();
        YsmAnimationPlayer player = runtime.animations();
        player.registerAnimations(YsmAnimationParser.parse("""
                {"animations":{
                  "child":{"loop":true,"animation_length":1,
                    "bones":{"root":{"position":["16+16*q.is_first_person",0,0]}},
                    "timeline":{"0":"mark('child.zero');","0.05":"mark('child.mid');"}},
                  "parent":{"loop":true,"animation_length":1,
                    "bones":{"root":{"position":[64,0,0],"rotation":[0,0,90]}}}
                }}
                """, engine));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{
                  "player.main":{"initial_state":"ysm-entry-child","states":{
                    "ysm-entry-child":{"on_exit":["mark('root.exit');"],"transitions":[{"end":"q.switch"}]},
                    "end":{"animations":["parent"],"blend_transition":0.2,
                      "transitions":[{"ysm-entry-child":"!q.switch"}]}}},
                  "player.main.child":{"initial_state":"ysm-entry-grand","states":{
                    "ysm-entry-grand":{"on_exit":["mark('child.exit');"]}}},
                  "player.main.child.grand":{"states":{"default":{"animations":["child"],
                    "on_exit":["mark('grand.exit');"]}}}
                }}
                """, engine));
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        context.variables.setPath("switch", 0f);
        var evaluator = ExpressionEvaluator.evaluator(context);
        player.controllerRuntime().update(evaluator, 0f, false);
        sample(player, evaluator, false);
        assertMatrix(matrix(runtime, false), -1f, 0f, "active nested child body");
        sample(player, evaluator, true);
        assertMatrix(matrix(runtime, true), -2f, 0f, "active nested child arm");
        var oldChild = player.getControllerAnimation("player.main.child.grand", "child");
        context.variables.setPath("switch", 1f);
        player.controllerRuntime().update(evaluator, 0f, false);
        require(trace.equals(List.of("player.main.child.grand:child.zero", "player.main.child.grand:grand.exit",
                "player.main.child:child.exit", "player.main:root.exit")), "nested exit owner/order differential: " + trace);
        sample(player, evaluator, false);
        assertMatrix(matrix(runtime, false), -1f, 0f, "parent takeover begins at the nested child body pose");
        sample(player, evaluator, true);
        assertMatrix(matrix(runtime, true), -2f, 0f, "parent takeover begins at the nested child arm pose");
        advance(player, evaluator, 0.1f);
        player.controllerRuntime().update(evaluator, 0.1f, false);
        sample(player, evaluator, false);
        assertMatrix(matrix(runtime, false), -2.5f, 45f, "nested child to parent body midpoint");
        sample(player, evaluator, true);
        assertMatrix(matrix(runtime, true), -3f, 45f, "nested child to parent arm midpoint");
        equal(0f, oldChild.time, "exited child timeline remains stopped");
        require(trace.size() == 4, "exited child does not emit its later timeline event");
        advance(player, evaluator, 0.1f);
        player.controllerRuntime().update(evaluator, 0.1f, false);
        sample(player, evaluator, false);
        assertMatrix(matrix(runtime, false), -4f, 90f, "parent takeover endpoint");
        context.variables.setPath("switch", 0f);
        player.controllerRuntime().update(evaluator, 0f, false);
        sample(player, evaluator, false);
        assertMatrix(matrix(runtime, false), -1f, 0f, "nested child re-entry releases old parent pose");
        require(player.getControllerAnimation("player.main.child.grand", "child") != oldChild
                && trace.getLast().equals("player.main.child.grand:child.zero"), "nested child re-entry owns a new initial event/instance");
    }

    private static YsmModelRuntime cpuRuntime() throws ReflectiveOperationException {
        // Use the real model/part/animation/runtime classes, without allocating a GPU texture or game entity.
        Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                String.class, String.class, Map.class, float.class, float.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, geometry(), geometry(), null, "fixture", false,
                "NEW", "timeline-fixture", Map.of(), 1f, 1f, true);
    }

    private static YsmGeometry geometry() {
        YsmGeometry geometry = new YsmGeometry();
        var bone = new YsmGeometry.Bone("root", null, new float[3], new float[3]);
        geometry.bones.put(bone.name, bone);
        geometry.roots.add(bone);
        return geometry;
    }

    private static void advance(YsmAnimationPlayer player, ExpressionEvaluator<?> evaluator, float delta)
            throws ReflectiveOperationException {
        invoke(player, "advanceAnimationTimeline", new Class<?>[]{float.class, ExpressionEvaluator.class}, delta, evaluator);
        invoke(player, "advanceControllerPoseLayers", new Class<?>[]{float.class}, delta);
    }

    private static void sample(YsmAnimationPlayer player, ExpressionEvaluator<?> evaluator, boolean firstPerson)
            throws ReflectiveOperationException {
        invoke(player, "sampleAnimations", new Class<?>[]{ExpressionEvaluator.class, boolean.class}, evaluator, firstPerson);
    }

    private static void invoke(YsmAnimationPlayer player, String name, Class<?>[] types, Object... arguments)
            throws ReflectiveOperationException {
        Method method = YsmAnimationPlayer.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(player, arguments);
    }

    private static Matrix4f matrix(YsmModelRuntime runtime, boolean firstPerson) throws ReflectiveOperationException {
        Method transform = YsmRenderer.class.getDeclaredMethod("applyBoneTransform",
                YsmGeometry.Bone.class, YsmModelPart.class, PoseStack.class);
        transform.setAccessible(true);
        PoseStack stack = new PoseStack();
        transform.invoke(runtime.renderer(), runtime.geometry().bones.get("root"),
                firstPerson ? runtime.getArmPart("root") : runtime.getPart("root"), stack);
        return new Matrix4f(stack.last().pose());
    }

    private static void testSmallLoopTime(YsmAnimationPlayer player, ExpressionEvaluator<?> evaluator)
            throws ReflectiveOperationException {
        var playing = player.getControllerAnimation("player.parallel_1", "loop");
        playing.time = 0f;
        invoke(player, "advanceAnimationTimeline", new Class<?>[]{float.class, ExpressionEvaluator.class}, 0.1f, evaluator);
        require(Float.floatToIntBits(playing.time) == Float.floatToIntBits(0.1f),
                "positive loop time must not lose precision through adding and subtracting clip length");
    }

    private static void assertMatrix(Matrix4f actual, float x, float degrees, String message) {
        float cosine = (float) Math.cos(Math.toRadians(degrees));
        float sine = (float) Math.sin(Math.toRadians(degrees));
        float[] expected = {cosine, sine, 0, 0, -sine, cosine, 0, 0, 0, 0, 1, 0, x, 0, 0, 1};
        float[] values = actual.get(new float[16]);
        for (int index = 0; index < expected.length; index++) {
            equal(expected[index], values[index], message + " coefficient " + index);
        }
    }

    private static void equal(float expected, float actual, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) < 0.00003f, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
