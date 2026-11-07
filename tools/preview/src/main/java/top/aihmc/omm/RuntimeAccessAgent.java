package top.aihmc.omm;

import com.google.gson.JsonParser;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.zip.ZipFile;
import net.fabricmc.loader.impl.lib.classtweaker.api.ClassTweaker;
import net.fabricmc.loader.impl.lib.classtweaker.api.ClassTweakerReader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Applies the selected MOD's own access rules using the installed Fabric implementation. */
public final class RuntimeAccessAgent {
    private RuntimeAccessAgent() {
    }

    public static void premain(String modJar, Instrumentation instrumentation) throws Exception {
        System.setProperty("omm.preview.modJar", modJar);
        ClassTweaker tweaker = ClassTweaker.newInstance();
        String resource;
        try (ZipFile zip = new ZipFile(modJar)) {
            var metadata = JsonParser.parseString(new String(
                zip.getInputStream(zip.getEntry("fabric.mod.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!metadata.has("accessWidener")) {
                System.out.println("ACCESS RULES none declared by " + modJar);
                return;
            }
            resource = metadata.get("accessWidener").getAsString();
            ClassTweakerReader.create(tweaker).read(zip.getInputStream(zip.getEntry(resource)).readAllBytes(), "official");
        }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                    ProtectionDomain domain, byte[] bytes) {
                if (name.equals("top/aihmc/omnimorph/config/fabric/ConfigKeyBindImpl")) {
                    return adaptKeyRegistration(bytes);
                }
                if (name.equals("top/aihmc/omnimorph/molang/runtime/binding/MolangBindings$QueryBinding")) {
                    return adaptConfiguredQueries(bytes);
                }
                if (name.equals("org/figuramc/figura/FiguraMod")) {
                    return adaptLocalPlayerIdentity(bytes);
                }
                if (name.equals("org/figuramc/figura/avatar/Avatar")) {
                    bytes = adaptAvatarSoundDisposal(bytes);
                    if (!tweaker.getTargets().contains(name)) {
                        return bytes;
                    }
                }
                if (name.equals("top/aihmc/omnimorph/model/ysm/YsmAudioResources")) {
                    return adaptAudioDisposal(bytes);
                }
                if (name.equals("org/figuramc/figura/model/rendering/texture/FiguraTexture")) {
                    return adaptTextureRemoval(bytes);
                }
                if (!tweaker.getTargets().contains(name)) {
                    return null;
                }
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(tweaker.createClassVisitor(Opcodes.ASM9, writer, null), 0);
                return writer.toByteArray();
            }
        });
        System.out.println("ACCESS RULES " + resource + " targets=" + tweaker.getTargets().size());
    }

    private static byte[] adaptConfiguredQueries(byte[] bytes) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("getProperty") || !descriptor.equals("(Ljava/lang/String;)Ljava/lang/Object;")) {
                    return method;
                }
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "top/aihmc/omm/ConfiguredMolangQueries", "bind",
                                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] adaptLocalPlayerIdentity(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                boolean pushProfiler = name.equals("pushProfiler") && descriptor.equals("(Ljava/lang/String;)V");
                boolean popProfiler = name.equals("popProfiler") && descriptor.equals("()V");
                if (pushProfiler || popProfiler) {
                    method.visitCode();
                    method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/util/profiling/Profiler", "get",
                        "()Lnet/minecraft/util/profiling/ProfilerFiller;", false);
                    if (pushProfiler) {
                        method.visitVarInsn(Opcodes.ALOAD, 0);
                    }
                    method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "net/minecraft/util/profiling/ProfilerFiller",
                        pushProfiler ? "push" : "pop", descriptor, true);
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(2, pushProfiler ? 1 : 0);
                    method.visitEnd();
                    return null;
                }
                if (!name.equals("getLocalPlayerUUID") || !descriptor.equals("()Ljava/util/UUID;")) {
                    return method;
                }
                // This host query replaces the Minecraft player/session lookup only.
                method.visitCode();
                method.visitLdcInsn("omm.preview.localPlayerUuid");
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/UUID", "fromString",
                    "(Ljava/lang/String;)Ljava/util/UUID;", false);
                method.visitInsn(Opcodes.ARETURN);
                method.visitMaxs(1, 0);
                method.visitEnd();
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] adaptKeyRegistration(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method,
                                                String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                            && owner.equals("net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper")
                            && method.equals("registerKeyMapping")
                            && descriptor.equals("(Lnet/minecraft/client/KeyMapping;)Lnet/minecraft/client/KeyMapping;")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "top/aihmc/omm/PreviewKeyBindings",
                                "register", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                            super.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/client/KeyMapping");
                        } else {
                            super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                        }
                    }
                };
            }
        }, 0);
        System.out.println("HOST INPUT original MOD key mappings registered with preview");
        return writer.toByteArray();
    }

    private static byte[] adaptAvatarSoundDisposal(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("clearSounds") || !descriptor.equals("()V")) {
                    return method;
                }
                method.visitCode();
                method.visitVarInsn(Opcodes.ALOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESTATIC, "top/aihmc/omm/PreviewAvatarResources",
                    "clearSounds", "(Ljava/lang/Object;)V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(1, 1);
                method.visitEnd();
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] adaptAudioDisposal(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("close") || !descriptor.equals("()V")) {
                    return delegate;
                }
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                            && owner.equals("org/figuramc/figura/lua/api/sound/SoundAPI")
                            && name.equals("getSoundEngine")
                            && descriptor.equals("()Lorg/figuramc/figura/ducks/SoundEngineAccessor;")) {
                            return;
                        }
                        if (opcode == Opcodes.INVOKEINTERFACE
                            && owner.equals("org/figuramc/figura/ducks/SoundEngineAccessor")
                            && name.equals("figura$releaseBuffer")
                            && descriptor.equals("(Lcom/mojang/blaze3d/audio/SoundBuffer;)V")) {
                            // Detached library previews disable sound playback. Preserve the original
                            // disposal operation without dispatching through a nonexistent game executor.
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "com/mojang/blaze3d/audio/SoundBuffer",
                                "discardAlBuffer", "()V", false);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] adaptTextureRemoval(byte[] bytes) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("closeFromRenderThread") && descriptor.equals("()V")) {
                    // The host owns the render thread; no Minecraft task executor exists.
                    method.visitCode();
                    method.visitVarInsn(Opcodes.ALOAD, 0);
                    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                        "org/figuramc/figura/model/rendering/texture/FiguraTexture", "close", "()V", false);
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(1, 1);
                    method.visitEnd();
                    return null;
                }
                if (!name.equals("queueRemoval") || !descriptor.equals("()V")) {
                    return method;
                }
                // Preview textures are never registered in Minecraft's TextureManager. The
                // original close() still releases NativeImage, backup and GPU texture resources.
                method.visitCode();
                method.visitInsn(Opcodes.RETURN);
                method.visitMaxs(0, 1);
                method.visitEnd();
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }
}
