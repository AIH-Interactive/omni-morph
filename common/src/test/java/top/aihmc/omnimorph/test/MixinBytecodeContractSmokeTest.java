package top.aihmc.omnimorph.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Checks compiled selectors and members; this does not apply Mixins or validate their runtime effects. */
public final class MixinBytecodeContractSmokeTest {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final JarFile minecraft;
    private int checked;
    private int checkedLocals;

    private MixinBytecodeContractSmokeTest(JarFile minecraft) {
        this.minecraft = minecraft;
    }

    public static void main(String[] args) throws Exception {
        try (var minecraft = new JarFile(args[0])) {
            var audit = new MixinBytecodeContractSmokeTest(minecraft);
            for (int index = 1; index < args.length; index++) {
                Path source = Path.of(args[index]);
                if (Files.isRegularFile(source)) {
                    try (var archive = new JarFile(source.toFile())) {
                        for (var entry : archive.stream().filter(item -> item.getName().endsWith(".class")).toList()) {
                            try (var input = archive.getInputStream(entry)) {
                                audit.checkDeclaration(read(input.readAllBytes()));
                            }
                        }
                    }
                    continue;
                }
                try (var files = Files.walk(source)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                        audit.checkDeclaration(read(Files.readAllBytes(file)));
                    }
                }
            }
            audit.failures.forEach(message -> System.out.println("FAIL " + message));
            if (!audit.failures.isEmpty()) {
                throw new AssertionError(audit.failures.size() + " compiled Mixin contracts failed");
            }
            System.out.println("PASS compiled target/member/invocation contracts for " + audit.checked + " Mixins");
            System.out.println("PASS " + audit.checkedLocals + " typed @Local bindings at invocation sites");
            System.out.println("Not covered: Mixin transformation, frame reconstruction, generic local references, "
                    + "refmaps and rendered behavior");
        }
    }

    private void checkDeclaration(ClassNode mixin) throws IOException {
        AnnotationNode declaration = annotation(mixin.invisibleAnnotations, MIXIN);
        if (declaration == null) {
            declaration = annotation(mixin.visibleAnnotations, MIXIN);
        }
        if (declaration != null) {
            check(mixin, declaration);
        }
    }

    private void check(ClassNode mixin, AnnotationNode declaration) throws IOException {
        List<String> targets = new ArrayList<>();
        for (Object target : list(value(declaration, "value"))) {
            targets.add(((Type) target).getInternalName());
        }
        for (Object target : list(value(declaration, "targets"))) {
            targets.add(target.toString().replace('.', '/'));
        }
        for (String targetName : targets) {
            ClassNode target = target(targetName);
            if (target == null) {
                if (targetName.startsWith("net/minecraft/") || targetName.startsWith("com/mojang/")) {
                    failures.add(mixin.name + " missing target " + targetName);
                } else {
                    System.out.println("SKIPPED optional target unavailable in audit classpath: " + targetName);
                }
                continue;
            }
            checked++;
            for (var field : mixin.fields) {
                if (annotation(field.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;") != null
                        && !hasField(target, field.name, field.desc)) {
                    failures.add(mixin.name + " shadow field " + field.name + field.desc);
                }
            }
            for (MethodNode method : mixin.methods) {
                for (AnnotationNode rule : annotations(method)) {
                    checkMethod(mixin, target, method, rule);
                }
            }
        }
    }

    private void checkMethod(ClassNode mixin, ClassNode target, MethodNode method, AnnotationNode rule) throws IOException {
        String context = mixin.name + "." + method.name;
        Object annotationValue = value(rule, "value");
        String explicit = annotationValue instanceof String text ? text : null;
        if (rule.desc.endsWith("/Invoker;")) {
            String name = explicit == null ? method.name : explicit;
            String descriptor = name.equals("<init>")
                    ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(method.desc)) : method.desc;
            if (!hasMethod(target, name, descriptor)) {
                failures.add(context + " invoker " + name + method.desc);
            }
        } else if (rule.desc.endsWith("/Accessor;")) {
            Type[] arguments = Type.getArgumentTypes(method.desc);
            String descriptor = arguments.length == 0 ? Type.getReturnType(method.desc).getDescriptor()
                    : arguments[0].getDescriptor();
            if (explicit != null && !hasField(target, explicit, descriptor)) {
                failures.add(context + " accessor " + explicit + descriptor);
            }
        } else if (rule.desc.endsWith("/Shadow;")) {
            String name = method.name.startsWith("shadow$") ? method.name.substring(7) : method.name;
            if (!hasMethod(target, name, method.desc)) {
                failures.add(context + " shadow method " + name + method.desc);
            }
        }
        List<?> selectors = list(value(rule, "method"));
        if (selectors.isEmpty()) {
            return;
        }
        List<MethodNode> selected = target.methods.stream().filter(candidate -> selectors.stream().anyMatch(selector ->
                selector.equals(candidate.name) || selector.equals(candidate.name + candidate.desc))).toList();
        if (selected.isEmpty()) {
            if (!Integer.valueOf(0).equals(value(rule, "require"))) {
                failures.add(context + " selector " + selectors);
            } else {
                System.out.println("OPTIONAL selector absent: " + context + " " + selectors);
            }
            return;
        }
        if (rule.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
            Type[] parameters = Type.getArgumentTypes(method.desc);
            int callback = -1;
            for (int index = 0; index < parameters.length; index++) {
                if (parameters[index].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo")) {
                    callback = index;
                    break;
                }
            }
            if (callback < 0) {
                failures.add(context + " missing CallbackInfo parameter");
            } else if (callback != 0) {
                Type[] prefix = java.util.Arrays.copyOf(parameters, callback);
                if (selected.stream().noneMatch(candidate -> java.util.Arrays.equals(prefix,
                        Type.getArgumentTypes(candidate.desc)))) {
                    failures.add(context + " callback arguments " + java.util.Arrays.toString(prefix)
                            + " do not match " + selected.stream().map(candidate -> candidate.desc).toList());
                }
            }
        }
        for (Object point : list(value(rule, "at"))) {
            AnnotationNode at = (AnnotationNode) point;
            String invocation = (String) value(at, "target");
            if (invocation == null || !invocation.startsWith("L") || !invocation.contains(";")) {
                continue;
            }
            int matches = 0;
            for (MethodNode selectedMethod : selected) {
                for (AbstractInsnNode instruction : selectedMethod.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && invocation.equals("L" + call.owner + ";" + call.name + call.desc)) {
                        matches++;
                        checkLocals(context, method, selectedMethod, instruction);
                    } else if (instruction instanceof FieldInsnNode field
                            && invocation.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) {
                        matches++;
                    }
                }
            }
            Integer ordinal = (Integer) value(at, "ordinal");
            if ((matches == 0 || ordinal != null && ordinal >= matches)
                    && !Integer.valueOf(0).equals(value(rule, "require"))) {
                failures.add(context + " invocation " + invocation + " matches=" + matches + " ordinal=" + ordinal);
            }
        }
    }

    private void checkLocals(String context, MethodNode handler, MethodNode target, AbstractInsnNode point) {
        Type[] parameters = Type.getArgumentTypes(handler.desc);
        int position = target.instructions.indexOf(point);
        for (int index = 0; index < parameters.length; index++) {
            AnnotationNode local = handler.invisibleParameterAnnotations == null ? null
                    : annotation(handler.invisibleParameterAnnotations[index],
                            "Lcom/llamalad7/mixinextras/sugar/Local;");
            if (local == null && handler.visibleParameterAnnotations != null) {
                local = annotation(handler.visibleParameterAnnotations[index],
                        "Lcom/llamalad7/mixinextras/sugar/Local;");
            }
            if (local == null) {
                continue;
            }
            String descriptor = parameters[index].getDescriptor();
            if (descriptor.contains("/sugar/ref/")) {
                // Generic reference sugar needs its own type-aware check; do not claim coverage here.
                System.out.println("Not covered: generic local reference " + context);
                continue;
            }
            List<Integer> candidates;
            if (Boolean.TRUE.equals(value(local, "argsOnly"))) {
                // Mixin reconstructs argument types from the descriptor even when production LVT is absent.
                candidates = new ArrayList<>();
                int argumentSlot = (target.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                for (Type argument : Type.getArgumentTypes(target.desc)) {
                    if (argument.getDescriptor().equals(descriptor)) {
                        candidates.add(argumentSlot);
                    }
                    argumentSlot += argument.getSize();
                }
            } else {
                candidates = target.localVariables.stream()
                        .filter(variable -> variable.desc.equals(descriptor)
                                && target.instructions.indexOf(variable.start) <= position
                                && target.instructions.indexOf(variable.end) > position)
                        .map(variable -> variable.index).sorted().toList();
            }
            Integer ordinal = (Integer) value(local, "ordinal");
            Integer slot = (Integer) value(local, "index");
            boolean valid;
            if (slot != null && slot >= 0) {
                valid = candidates.contains(slot);
            } else if (ordinal != null && ordinal >= 0) {
                valid = ordinal < candidates.size();
            } else {
                valid = candidates.size() == 1;
            }
            checkedLocals++;
            if (!valid) {
                failures.add(context + " local " + descriptor + " ordinal=" + ordinal + " index=" + slot
                        + " candidates=" + candidates);
            }
        }
    }

    private boolean hasMethod(ClassNode owner, String name, String descriptor) throws IOException {
        if (owner.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor))) {
            return true;
        }
        ClassNode parent = owner.superName == null ? null : target(owner.superName);
        return parent != null && hasMethod(parent, name, descriptor);
    }

    private boolean hasField(ClassNode owner, String name, String descriptor) throws IOException {
        if (owner.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor))) {
            return true;
        }
        ClassNode parent = owner.superName == null ? null : target(owner.superName);
        return parent != null && hasField(parent, name, descriptor);
    }

    private ClassNode target(String name) throws IOException {
        if (classes.containsKey(name)) {
            return classes.get(name);
        }
        ClassNode result = null;
        var entry = minecraft.getJarEntry(name + ".class");
        try (var input = entry == null ? getClass().getClassLoader().getResourceAsStream(name + ".class")
                : minecraft.getInputStream(entry)) {
            if (input != null) {
                result = read(input.readAllBytes());
            }
        }
        classes.put(name, result);
        return result;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode result = new ClassNode();
        new ClassReader(bytes).accept(result, 0);
        return result;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> result = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            result.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            result.addAll(method.invisibleAnnotations);
        }
        return result;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream()
                .filter(node -> node.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (annotation.values.get(index).equals(name)) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        return null;
    }

    private static List<?> list(Object value) {
        return value == null ? List.of() : value instanceof List<?> values ? values : List.of(value);
    }
}
