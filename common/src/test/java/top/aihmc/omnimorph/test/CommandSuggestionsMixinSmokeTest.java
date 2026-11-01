package top.aihmc.omnimorph.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Checks the actual callback bindings against Minecraft's live locals at each suggestion invocation. */
public final class CommandSuggestionsMixinSmokeTest {
    public static void main(String[] args) throws Exception {
        ClassNode target = new ClassNode();
        try (var input = CommandSuggestionsMixinSmokeTest.class.getClassLoader()
                .getResourceAsStream("net/minecraft/client/gui/components/CommandSuggestions.class")) {
            if (input == null) {
                throw new AssertionError("Missing real Minecraft CommandSuggestions bytecode");
            }
            new ClassReader(input).accept(target, ClassReader.EXPAND_FRAMES);
        }
        for (String argument : args) {
            ClassNode mixin = new ClassNode();
            new ClassReader(Files.readAllBytes(Path.of(argument))).accept(mixin, 0);
            if (mixin.name.equals("org/figuramc/figura/mixin/gui/SuggestionsListMixin")) {
                checkCommonList(mixin);
                continue;
            }
            int checked = 0;
            for (MethodNode callback : mixin.methods) {
                AnnotationNode inject = annotation(callback.visibleAnnotations,
                        "Lorg/spongepowered/asm/mixin/injection/Inject;");
                if (inject == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<String> selectors = (List<String>) value(inject, "method");
                AnnotationNode at = injectionPoint(inject);
                String invocation = (String) value(at, "target");
                if (invocation == null || !invocation.contains("SharedSuggestionProvider;suggest(")) {
                    continue;
                }
                MethodNode method = target.methods.stream()
                        .filter(candidate -> selectors.contains(candidate.name)
                                || selectors.contains(candidate.name + candidate.desc))
                        .findFirst().orElseThrow(() -> new AssertionError("Missing target method " + selectors));
                MethodInsnNode call = null;
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode candidate
                            && invocation.equals("L" + candidate.owner + ";" + candidate.name + candidate.desc)) {
                        require(call == null, "suggestion invocation must be unambiguous");
                        call = candidate;
                    }
                }
                require(call != null, "callback invocation exists in real Minecraft method");
                int position = method.instructions.indexOf(call);
                Set<Integer> frameLocals = frameLocals(method, call);
                List<LocalVariableNode> live = method.localVariables.stream()
                        .filter(local -> local.index != 0
                                && frameLocals.contains(local.index)
                                && method.instructions.indexOf(local.start) <= position
                                && method.instructions.indexOf(local.end) > position)
                        .sorted(Comparator.comparingInt(local -> local.index)).toList();
                Type[] parameters = Type.getArgumentTypes(callback.desc);
                Object capture = value(inject, "locals");
                if (capture instanceof String[] mode && !mode[1].equals("NO_CAPTURE")) {
                    List<String> expected = new ArrayList<>();
                    for (int parameter = 1; parameter < parameters.length; parameter++) {
                        expected.add(parameters[parameter].getDescriptor());
                    }
                    List<String> actual = live.stream().map(local -> local.desc).toList();
                    require(actual.size() >= expected.size()
                                    && actual.subList(0, expected.size()).equals(expected),
                            mixin.name + ": incompatible captured locals; expected=" + expected + ", found=" + actual);
                } else {
                    for (int parameter = 1; parameter < parameters.length; parameter++) {
                        AnnotationNode local = parameterAnnotation(callback, parameter);
                        require(local != null, "every callback local must have an explicit @Local binding");
                        String descriptor = parameters[parameter].getDescriptor();
                        List<LocalVariableNode> candidates = live.stream()
                                .filter(variable -> variable.desc.equals(descriptor)).toList();
                        Integer ordinal = (Integer) value(local, "ordinal");
                        require(ordinal == null ? candidates.size() == 1 : ordinal >= 0 && ordinal < candidates.size(),
                                "local binding resolves: " + descriptor + " ordinal=" + ordinal);
                    }
                }
                checked++;
            }
            require(checked == 2, "both suggestion collection and post-assignment hooks checked");
            System.out.println("PASS actual 1.21.1 invocation/live-local bindings: " + mixin.name);
        }
        System.out.println("This bytecode regression does not claim full client/Mixin application validation.");
    }

    private static void checkCommonList(ClassNode mixin) throws Exception {
        ClassNode target = new ClassNode();
        try (var input = CommandSuggestionsMixinSmokeTest.class.getClassLoader().getResourceAsStream(
                "net/minecraft/client/gui/components/CommandSuggestions$SuggestionsList.class")) {
            require(input != null, "real nested suggestion list bytecode exists");
            new ClassReader(input).accept(target, 0);
        }
        MethodNode render = target.methods.stream().filter(method -> method.name.equals("render"))
                .findFirst().orElseThrow();
        int hooks = 0;
        for (MethodNode callback : mixin.methods) {
            AnnotationNode modify = annotation(callback.visibleAnnotations,
                    "Lorg/spongepowered/asm/mixin/injection/ModifyArg;");
            if (modify != null) {
                AnnotationNode at = injectionPoint(modify);
                String selector = (String) value(at, "target");
                boolean found = false;
                for (AbstractInsnNode instruction : render.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && selector.equals("L" + call.owner + ";" + call.name + call.desc)) {
                        Type[] parameters = Type.getArgumentTypes(callback.desc);
                        require(java.util.Arrays.equals(parameters, Type.getArgumentTypes(call.desc)),
                                "common ModifyArg handler arguments match real drawString call");
                        int index = (Integer) value(modify, "index");
                        require(Type.getReturnType(callback.desc).equals(parameters[index]),
                                "common ModifyArg returns the selected argument type");
                        found = true;
                    }
                }
                require(found, "common drawString injection point exists");
                hooks++;
            }
            AnnotationNode inject = annotation(callback.visibleAnnotations,
                    "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (inject != null) {
                Type[] parameters = Type.getArgumentTypes(callback.desc);
                Type[] targetParameters = Type.getArgumentTypes(render.desc);
                require(parameters.length == targetParameters.length + 1,
                        "common render callback has original arguments and CallbackInfo");
                for (int index = 0; index < targetParameters.length; index++) {
                    require(parameters[index].equals(targetParameters[index]), "common render argument matches");
                }
                require(value(inject, "locals") == null, "common list callback does not capture fragile locals");
                hooks++;
            }
        }
        require(hooks == 2, "both common suggestion-list hooks checked");
        System.out.println("PASS common suggestion-list render/ModifyArg contracts against real Minecraft bytecode");
    }

    private static Set<Integer> frameLocals(MethodNode method, AbstractInsnNode stop) {
        Set<Integer> live = new HashSet<>();
        live.add(0);
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction == stop) {
                break;
            }
            if (instruction instanceof FrameNode frame) {
                live.clear();
                int slot = 0;
                for (Object value : frame.local) {
                    if (!Opcodes.TOP.equals(value)) {
                        live.add(slot);
                    }
                    slot += Opcodes.LONG.equals(value) || Opcodes.DOUBLE.equals(value) ? 2 : 1;
                }
            } else if (instruction instanceof VarInsnNode variable
                    && variable.getOpcode() >= Opcodes.ISTORE && variable.getOpcode() <= Opcodes.ASTORE) {
                live.add(variable.var);
            }
        }
        return live;
    }

    private static AnnotationNode parameterAnnotation(MethodNode method, int parameter) {
        AnnotationNode result = method.visibleParameterAnnotations == null ? null
                : annotation(method.visibleParameterAnnotations[parameter], "Lcom/llamalad7/mixinextras/sugar/Local;");
        return result != null || method.invisibleParameterAnnotations == null ? result
                : annotation(method.invisibleParameterAnnotations[parameter], "Lcom/llamalad7/mixinextras/sugar/Local;");
    }

    private static AnnotationNode injectionPoint(AnnotationNode annotation) {
        Object point = value(annotation, "at");
        return point instanceof List<?> points ? (AnnotationNode) points.getFirst() : (AnnotationNode) point;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream()
                .filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (annotation.values.get(index).equals(key)) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
