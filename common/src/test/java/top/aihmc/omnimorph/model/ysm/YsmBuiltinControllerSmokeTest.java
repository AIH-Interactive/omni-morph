package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import java.lang.reflect.Method;

/** Production state machine and native movement selection without rendered-pixel claims. */
public final class YsmBuiltinControllerSmokeTest {
    public static void main(String[] args) throws Exception {
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        Variable custom = execution -> ((Avatar.MolangContext) execution.entity()).variables.getPath("custom");
        ObjectBinding query = name -> name.equals("custom") ? custom : null;
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("q") ? query : null);
        YsmAnimationPlayer player = new YsmAnimationPlayer(null) {
            @Override
            public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
            }
        };
        player.registerAnimations(YsmAnimationParser.parse("""
                {"animations":{"idle":{"loop":true,"bones":{"root":{"rotation":[1,0,0]}}},
                 "author":{"loop":true,"bones":{"root":{"rotation":[20,0,0]}}}}}
                """, engine));
        player.registerControllers(YsmAnimationControllerParser.parse("""
                {"animation_controllers":{"controller.animation.player.main":{"initial_state":"ysm-builtin","states":{
                  "ysm-builtin":{"animations":["author"],"transitions":[{"custom":"q.custom"}]},
                  "custom":{"animations":["author"],"transitions":[{"ysm-builtin":"!q.custom"}]}
                }}}}
                """, engine));
        Method nativeUpdate = YsmAnimationPlayer.class.getDeclaredMethod("updateNativeStateMachine",
                LivingEntityRenderState.class, net.minecraft.world.entity.LivingEntity.class);
        nativeUpdate.setAccessible(true);
        var evaluator = ExpressionEvaluator.evaluator(context);
        context.variables.setPath("custom", 0f);
        player.controllerRuntime().update(evaluator, 0f);
        nativeUpdate.invoke(player, new LivingEntityRenderState(), null);
        var first = player.getActiveAnimation("idle");
        require(first != null, "builtin state selects native movement");
        require(player.getControllerAnimation("controller.animation.player.main", "author") == null,
                "builtin state ignores its declared animation slots");
        first.time = .6f;
        context.variables.setPath("custom", 1f);
        player.controllerRuntime().update(evaluator, .1f);
        nativeUpdate.invoke(player, new LivingEntityRenderState(), null);
        require(player.getActiveAnimation("idle") == null, "custom state clears native movement instead of blending both sources");
        require(player.getControllerAnimation("controller.animation.player.main", "author") != null,
                "custom state selects the author source");
        context.variables.setPath("custom", 0f);
        player.controllerRuntime().update(evaluator, .1f);
        nativeUpdate.invoke(player, new LivingEntityRenderState(), null);
        require(player.getActiveAnimation("idle") != first && player.getActiveAnimation("idle").time == 0f,
                "returning to builtin starts a new native clock");
        System.out.println("PASS builtin/custom source switching: exclusivity, special slot semantics and native clock restart");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
