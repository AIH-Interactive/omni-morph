package top.aihmc.omnimorph.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

/** Checks that world rendering actually reaches the particle callback/draw injection targets. */
public final class ParticleWorldEntrySmokeTest {
    public static void main(String[] args) throws Exception {
        try (var minecraft = new JarFile(args[0]); var mod = new JarFile(args[1])) {
            ClassNode world = read(minecraft, "net/minecraft/client/renderer/LevelRenderer");
            Set<String> entries = new HashSet<>();
            for (var method : world.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/minecraft/client/particle/ParticleEngine")
                            && (call.name.equals("render") || call.name.equals("m_107336_"))) {
                        entries.add(call.name + call.desc);
                    }
                }
            }
            if (entries.isEmpty()) {
                throw new AssertionError("No actual world particle entry point checked");
            }
            ClassNode mixin = read(mod, "org/figuramc/figura/mixin/particle/ParticleEngineMixin");
            check(mixin, "figura$runParticleFrameCallbacks", entries);
            check(mixin, "figura$renderCustomParticles", entries);
            MethodNode draw = mixin.methods.stream()
                    .filter(method -> method.name.equals("figura$renderCustomParticles")).findFirst().orElseThrow();
            boolean forwardsView = false;
            for (var instruction : draw.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("renderWorld")) {
                    forwardsView = call.desc.startsWith("(Lcom/mojang/blaze3d/vertex/PoseStack;")
                            && call.desc.contains("Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;");
                }
            }
            if (!forwardsView) {
                throw new AssertionError("World draw must forward the incoming PoseStack and owned BufferSource");
            }
            ClassNode runtime = read(mod, "top/aihmc/omnimorph/lua/api/particle/CustomParticleRuntime");
            boolean appliesView = false;
            for (var method : runtime.methods) {
                if (method.name.equals("renderWorld")) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.name.equals("applyWorldView")) {
                            appliesView = true;
                        }
                    }
                }
            }
            if (!appliesView) {
                throw new AssertionError("World draw does not apply the tested view composition");
            }
            System.out.println("PASS particle callbacks and draw hook target actual patched LevelRenderer calls: " + entries);
            System.out.println("Not covered: Mixin application or game pixels");
        }
    }

    private static void check(ClassNode mixin, String name, Set<String> entries) {
        MethodNode handler = mixin.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
        var annotations = handler.visibleAnnotations;
        AnnotationNode inject = annotations == null ? null : annotations.stream()
                .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                .findFirst().orElse(null);
        if (inject == null && handler.invisibleAnnotations != null) {
            inject = handler.invisibleAnnotations.stream()
                    .filter(value -> value.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
                    .findFirst().orElse(null);
        }
        Set<String> selectors = new HashSet<>();
        if (inject != null) {
            for (int index = 0; index < inject.values.size(); index += 2) {
                if (inject.values.get(index).equals("method")) {
                    for (Object value : (java.util.List<?>) inject.values.get(index + 1)) {
                        selectors.add(value.toString());
                    }
                }
            }
        }
        if (!selectors.containsAll(entries)) {
            throw new AssertionError(name + " targets " + selectors + "; actual world calls " + entries);
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
