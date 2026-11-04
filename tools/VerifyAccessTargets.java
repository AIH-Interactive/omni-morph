import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;

/** Local AW/AT target check against compiled Minecraft classes; does not load the game. */
public final class VerifyAccessTargets {
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: VerifyAccessTargets.java <access-widener-or-transformer> <minecraft.jar>");
        }

        Path accessWidener = Path.of(arguments[0]);
        Path minecraftJar = Path.of(arguments[1]);
        Map<String, ClassModel> classes = new HashMap<>();
        int checked = 0;
        int failures = 0;
        int lineNumber = 0;
        boolean accessWidenerFormat = Files.readAllLines(accessWidener).stream()
                .anyMatch(line -> line.strip().startsWith("accessWidener "));

        try (JarFile jar = new JarFile(minecraftJar.toFile())) {
            for (String line : Files.readAllLines(accessWidener)) {
                lineNumber++;
                String declaration = line.split("#", 2)[0].strip();
                if (declaration.isEmpty() || declaration.startsWith("accessWidener ")) {
                    continue;
                }

                checked++;
                String[] parts = declaration.split("\\s+");
                if (parts.length < (accessWidenerFormat ? 3 : 2)) {
                    throw new IllegalArgumentException("Malformed access declaration at line " + lineNumber);
                }
                String owner = accessWidenerFormat ? parts[2] : parts[1].replace('.', '/');
                ClassModel model = classes.get(owner);
                if (model == null) {
                    var entry = jar.getJarEntry(owner + ".class");
                    if (entry == null) {
                        System.out.println("FAIL line " + lineNumber + ": missing class " + owner);
                        failures++;
                        continue;
                    }
                    try (var input = jar.getInputStream(entry)) {
                        model = ClassFile.of().parse(input.readAllBytes());
                    }
                    classes.put(owner, model);
                }

                String kind = accessWidenerFormat ? parts[1]
                        : parts.length == 2 ? "class" : parts[2].contains("(") ? "method" : "field";
                String member = kind.equals("class") ? "" : parts[accessWidenerFormat ? 3 : 2];
                int descriptorStart = member.indexOf('(');
                String memberName = descriptorStart < 0 ? member : member.substring(0, descriptorStart);
                String descriptor = accessWidenerFormat && !kind.equals("class") ? parts[4]
                        : descriptorStart < 0 ? null : member.substring(descriptorStart);
                boolean found = switch (kind) {
                    case "class" -> true;
                    case "field" -> model.fields().stream().anyMatch(field ->
                            field.fieldName().stringValue().equals(memberName)
                                    && (descriptor == null || field.fieldType().stringValue().equals(descriptor)));
                    case "method" -> model.methods().stream().anyMatch(method ->
                            method.methodName().stringValue().equals(memberName)
                                    && method.methodType().stringValue().equals(descriptor));
                    default -> throw new IllegalArgumentException("Unknown access target kind: " + kind);
                };
                if (found) {
                    System.out.println("PASS line " + lineNumber + ": " + declaration);
                } else {
                    System.out.println("FAIL line " + lineNumber + ": missing member/descriptor " + declaration);
                    failures++;
                }
            }
        }

        System.out.println("Checked " + checked + " " + (accessWidenerFormat ? "AW" : "AT")
                + " targets against " + minecraftJar + "; failures=" + failures);
        System.out.println("Scope: declared target existence and exact descriptors; not loader transformation or runtime injection.");
        if (checked == 0 || failures != 0) {
            throw new IllegalStateException("Access target validation failed");
        }
    }
}
