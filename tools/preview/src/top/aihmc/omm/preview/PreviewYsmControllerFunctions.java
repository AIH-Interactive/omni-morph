package top.aihmc.omm.preview;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;

/** Original CtrlBinding function commands routed to actual instance-owned controller slots. */
final class PreviewYsmControllerFunctions {
    private PreviewYsmControllerFunctions() {}

    static Function binding(String operation) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                PreviewMolang host = (PreviewMolang) execution.entity();
                Object value = host.controller.getPath("controller");
                if (host.controllers == null || !(value instanceof String controller) || controller.isBlank()) {
                    return null;
                }
                var player = host.controllers;
                switch (operation) {
                    case "indicate_reload" -> player.requestControllerFunctionReload(controller);
                    case "reset" ->
                        player.clearControllerFunctionAnimation(
                            controller, player.controllerFunctionTransitionLength(controller));
                    case "set_beginning_transition_length" ->
                        player.setControllerFunctionTransitionLength(controller, args.getAsFloat(execution, 0));
                    case "set_animation" -> {
                        String animation = args.getAsString(execution, 0);
                        if (animation == null || animation.isEmpty())
                            return null;
                        var fallback = player.loopModeFor(animation, YsmAnimationClip.LoopMode.ONCE);
                        var mode = args.size() == 2 ? loopMode(args.getAsInt(execution, 1), fallback) : fallback;
                        player.playControllerFunctionAnimation(
                            controller, animation, mode, player.controllerFunctionTransitionLength(controller));
                    }
                    default -> throw new IllegalArgumentException("Unknown controller operation: " + operation);
                }
                return null;
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return switch (operation) {
                    case "set_animation" -> size == 1 || size == 2;
                    case "set_beginning_transition_length" -> size == 1;
                    default -> size == 0;
                };
            }
        };
    }

    private static YsmAnimationClip.LoopMode loopMode(int value, YsmAnimationClip.LoopMode fallback) {
        return switch (value) {
            case 10 -> YsmAnimationClip.LoopMode.LOOP;
            case 11 -> YsmAnimationClip.LoopMode.ONCE;
            case 12 -> YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME;
            default -> fallback;
        };
    }
}
