import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Offline target/descriptor/invocation audit. Does not bootstrap Minecraft or apply Mixins. */
public final class VerifyMixinTargets {
    private final List<Path> classpath;
    private final Map<String, ClassModel> cache = new HashMap<>();
    private int failures;
    private int checked;

    private VerifyMixinTargets(Path classpathFile) throws IOException {
        classpath = List.of(Files.readString(classpathFile).strip().split(java.io.File.pathSeparator))
                .stream().map(Path::of).toList();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: VerifyMixinTargets.java <classes-directory> <classpath-file>");
        }
        VerifyMixinTargets audit = new VerifyMixinTargets(Path.of(args[1]));
        try (var files = Files.walk(Path.of(args[0]))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                audit.check(ClassFile.of().parse(Files.readAllBytes(file)));
            }
        }
        System.out.println("Checked mixins=" + audit.checked + ", failures=" + audit.failures);
        System.out.println("Scope: static targets, shadows, selectors and INVOKE sites; not transformation, locals or runtime behavior.");
        if (audit.checked == 0 || audit.failures != 0) {
            throw new IllegalStateException("Mixin target audit failed");
        }
    }

    private ClassModel load(String name) throws IOException {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }
        for (Path entry : classpath) {
            if (Files.isDirectory(entry)) {
                Path file = entry.resolve(name + ".class");
                if (Files.exists(file)) {
                    ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
                    cache.put(name, model);
                    return model;
                }
            } else if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
                try (JarFile jar = new JarFile(entry.toFile())) {
                    var resource = jar.getJarEntry(name + ".class");
                    if (resource != null) {
                        try (var input = jar.getInputStream(resource)) {
                            ClassModel model = ClassFile.of().parse(input.readAllBytes());
                            cache.put(name, model);
                            return model;
                        }
                    }
                }
            }
        }
        try (var input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            ClassModel model = input == null ? null : ClassFile.of().parse(input.readAllBytes());
            cache.put(name, model);
            return model;
        }
    }

    private static List<Annotation> annotations(AttributedElement element) {
        List<Annotation> result = new ArrayList<>();
        element.findAttribute(Attributes.runtimeVisibleAnnotations()).ifPresent(a -> result.addAll(a.annotations()));
        element.findAttribute(Attributes.runtimeInvisibleAnnotations()).ifPresent(a -> result.addAll(a.annotations()));
        return result;
    }

    private static AnnotationValue value(Annotation annotation, String key) {
        return annotation.elements().stream().filter(e -> e.name().equalsString(key))
                .map(e -> e.value()).findFirst().orElse(null);
    }

    private static List<AnnotationValue> values(AnnotationValue value) {
        return value == null ? List.of() : value instanceof AnnotationValue.OfArray array ? array.values() : List.of(value);
    }

    private static String string(AnnotationValue value) {
        return value instanceof AnnotationValue.OfString text ? text.stringValue() : "";
    }

    private static boolean named(Annotation annotation, String name) {
        return annotation.className().stringValue().endsWith("/" + name + ";");
    }

    private void fail(String mixin, String message) {
        failures++;
        System.out.println("FAIL " + mixin + ": " + message);
    }

    private List<MethodModel> methods(ClassModel target, String selector) {
        int descriptorStart = selector.indexOf('(');
        String name = descriptorStart < 0 ? selector : selector.substring(0, descriptorStart);
        String descriptor = descriptorStart < 0 ? null : selector.substring(descriptorStart);
        return target.methods().stream().filter(method -> method.methodName().equalsString(name)
                && (descriptor == null || method.methodType().equalsString(descriptor))).toList();
    }

    private boolean hasMember(ClassModel target, String name, String descriptor, boolean field) throws IOException {
        if (target == null) {
            return false;
        }
        boolean found = field
                ? target.fields().stream().anyMatch(f -> f.fieldName().equalsString(name) && f.fieldType().equalsString(descriptor))
                : !methods(target, name + descriptor).isEmpty();
        if (found) {
            return true;
        }
        if (target.superclass().isPresent() && hasMember(load(target.superclass().get().asInternalName()), name, descriptor, field)) {
            return true;
        }
        for (var parent : target.interfaces()) {
            if (hasMember(load(parent.asInternalName()), name, descriptor, field)) {
                return true;
            }
        }
        return false;
    }

    private void check(ClassModel mixin) throws IOException {
        Annotation declaration = annotations(mixin).stream().filter(a -> named(a, "Mixin")).findFirst().orElse(null);
        if (declaration == null) {
            return;
        }
        checked++;
        List<String> targets = new ArrayList<>();
        for (var target : values(value(declaration, "value"))) {
            String descriptor = ((AnnotationValue.OfClass) target).className().stringValue();
            targets.add(descriptor.substring(1, descriptor.length() - 1));
        }
        for (var target : values(value(declaration, "targets"))) {
            targets.add(string(target).replace('.', '/'));
        }
        String name = mixin.thisClass().asInternalName();
        for (String owner : targets) {
            ClassModel target = load(owner);
            if (target == null) {
                if (annotations(mixin).stream().anyMatch(a -> named(a, "Pseudo"))) {
                    System.out.println("OPTIONAL absent " + name + " -> " + owner);
                } else {
                    fail(name, "missing target " + owner);
                }
                continue;
            }
            int before = failures;
            for (var field : mixin.fields()) {
                if (annotations(field).stream().anyMatch(a -> named(a, "Shadow"))
                        && !hasMember(target, field.fieldName().stringValue(), field.fieldType().stringValue(), true)) {
                    fail(name, "missing shadow " + field.fieldName().stringValue() + field.fieldType().stringValue());
                }
            }
            Map<String, Boolean> groups = new HashMap<>();
            for (var handler : mixin.methods()) {
                Annotation group = annotations(handler).stream().filter(a -> named(a, "Group")).findFirst().orElse(null);
                String groupName = group == null ? null : string(value(group, "name"));
                for (var annotation : annotations(handler)) {
                    if (named(annotation, "Accessor") || named(annotation, "Invoker")) {
                        String memberName = string(value(annotation, "value"));
                        var type = handler.methodTypeSymbol();
                        if (memberName.isEmpty()) {
                            String handlerName = handler.methodName().stringValue();
                            String suffix = handlerName.replaceFirst("^(get|set|is|invoke|call)", "");
                            memberName = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
                        }
                        boolean field = named(annotation, "Accessor");
                        String descriptor = field
                                ? (type.returnType().descriptorString().equals("V")
                                    ? type.parameterType(0) : type.returnType()).descriptorString()
                                : memberName.equals("<init>")
                                    ? type.changeReturnType(java.lang.constant.ConstantDescs.CD_void).descriptorString()
                                    : handler.methodType().stringValue();
                        if (!hasMember(target, memberName, descriptor, field)) {
                            fail(name, "missing accessor/invoker " + memberName + descriptor);
                        }
                    }
                    if (named(annotation, "Shadow")) {
                        String shadowName = handler.methodName().stringValue().replaceFirst("^shadow\\$", "");
                        if (!hasMember(target, shadowName, handler.methodType().stringValue(), false)) {
                            fail(name, "missing shadow " + shadowName + handler.methodType().stringValue());
                        }
                    }
                    List<AnnotationValue> selectors = values(value(annotation, "method"));
                    if (selectors.isEmpty()) {
                        continue;
                    }
                    List<MethodModel> selected = new ArrayList<>();
                    for (var selector : selectors) {
                        selected.addAll(methods(target, string(selector)));
                    }
                    if (groupName != null) {
                        groups.merge(groupName, !selected.isEmpty(), Boolean::logicalOr);
                    }
                    if (selected.isEmpty()) {
                        boolean optional = value(annotation, "require") instanceof AnnotationValue.OfInt requirement
                                && requirement.intValue() == 0;
                        if (!optional) {
                            fail(name, handler.methodName().stringValue() + " missing selectors " + selectors);
                        }
                        continue;
                    }
                    if (named(annotation, "Inject")) {
                        var parameters = handler.methodTypeSymbol().parameterList();
                        int callbackIndex = -1;
                        for (int i = 0; i < parameters.size(); i++) {
                            if (parameters.get(i).descriptorString().startsWith("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")) {
                                callbackIndex = i;
                                break;
                            }
                        }
                        if (callbackIndex > 0) {
                            var capturedParameters = parameters.subList(0, callbackIndex);
                            if (selected.stream().noneMatch(method -> method.methodTypeSymbol().parameterList().equals(capturedParameters))) {
                                fail(name, handler.methodName().stringValue() + " callback arguments differ from target " + capturedParameters);
                            }
                        }
                    }
                    for (var atValue : values(value(annotation, "at"))) {
                        Annotation at = ((AnnotationValue.OfAnnotation) atValue).annotation();
                        String injectionPoint = string(value(at, "value"));
                        if (!injectionPoint.equals("INVOKE") && !injectionPoint.equals("FIELD")) {
                            continue;
                        }
                        String invocation = string(value(at, "target"));
                        int count = 0;
                        for (var method : selected) {
                            if (method.code().isPresent()) {
                                for (var instruction : method.code().get()) {
                                    if (injectionPoint.equals("INVOKE") && instruction instanceof InvokeInstruction invoke) {
                                        String actual = "L" + invoke.owner().asInternalName() + ";"
                                                + invoke.name().stringValue() + invoke.type().stringValue();
                                        if (actual.equals(invocation)) {
                                            count++;
                                        }
                                    } else if (injectionPoint.equals("FIELD") && instruction instanceof FieldInstruction field) {
                                        String actual = "L" + field.owner().asInternalName() + ";"
                                                + field.name().stringValue() + ":" + field.type().stringValue();
                                        int opcode = value(at, "opcode") instanceof AnnotationValue.OfInt number
                                                ? number.intValue() : -1;
                                        if (actual.equals(invocation) && (opcode < 0 || field.opcode().bytecode() == opcode)) {
                                            count++;
                                        }
                                    }
                                }
                            }
                        }
                        int ordinal = value(at, "ordinal") instanceof AnnotationValue.OfInt number ? number.intValue() : -1;
                        if (count == 0 || ordinal >= count) {
                            fail(name, handler.methodName().stringValue() + " " + injectionPoint + " count=" + count
                                    + " ordinal=" + ordinal + " " + invocation);
                        }
                    }
                }
            }
            for (var group : groups.entrySet()) {
                if (!group.getValue()) {
                    fail(name, "no selector for required injection group " + group.getKey());
                }
            }
            if (before == failures) {
                System.out.println("PASS " + name + " -> " + owner);
            }
        }
    }
}
