package top.aihmc.omnimorph.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.util.jar.JarFile;

/** Checks production RenderType factories against Forge-family runtime access flags. */
public final class RenderTypeFactoryAccessSmokeTest {
    private static final String RENDER_TYPE = "net/minecraft/client/renderer/RenderType";

    public static void main(String[] args) throws Exception {
        try (var minecraft = new JarFile(args[0]); var mod = new JarFile(args[1])) {
            ClassNode target = read(minecraft, RENDER_TYPE);
            int checked = 0;
            for (var entry : mod.stream().filter(value -> value.getName().endsWith(".class")).toList()) {
                ClassNode caller = read(mod, entry.getName().substring(0, entry.getName().length() - 6));
                for (var method : caller.methods) {
                    for (var instruction : method.instructions) {
                        if (!(instruction instanceof MethodInsnNode call)
                                || !(call.name.equals("m_173209_") || call.name.equals("m_173215_"))
                                || !call.desc.endsWith("L" + RENDER_TYPE + "$CompositeRenderType;")) {
                            continue;
                        }
                        var declaration = target.methods.stream()
                                .filter(value -> value.name.equals(call.name) && value.desc.equals(call.desc))
                                .findFirst().orElseThrow(() -> new AssertionError("Missing runtime factory: " + call.name));
                        int required = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                        if (call.getOpcode() != Opcodes.INVOKESTATIC || (declaration.access & required) != required) {
                            throw new AssertionError(caller.name + "." + method.name
                                    + " calls a non-public runtime factory: " + call.name + call.desc);
                        }
                        checked++;
                    }
                }
            }
            if (checked < 2) {
                throw new AssertionError("Expected production material and bone-highlight factories, found " + checked);
            }
            System.out.println("PASS " + checked + " production RenderType factory calls are public/static in patched SRG runtime");
            System.out.println("Not covered: full client initialization or rendered behavior");
        }
    }

    private static ClassNode read(JarFile archive, String name) throws IOException {
        var entry = archive.getJarEntry(name + ".class");
        if (entry == null) {
            throw new AssertionError("Missing class: " + name);
        }
        var node = new ClassNode();
        try (var input = archive.getInputStream(entry)) {
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }
}
