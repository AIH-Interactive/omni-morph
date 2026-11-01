package top.aihmc.omnimorph.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import javax.imageio.spi.ImageReaderSpi;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Replays JPMS package-supplier checks from actual packaged classes without starting Minecraft. */
public final class LoaderModuleSmokeTest {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        verifyPlatformBridges(jar);
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        packages.put("omni_morph", new HashSet<>());
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            var entries = archive.entries();
            int dependency = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                addPackage(packages.get("omni_morph"), entry.getName());
                if (entry.getName().startsWith("META-INF/jars/") && entry.getName().endsWith(".jar")) {
                    Set<String> nested = new HashSet<>();
                    try (var input = new ZipInputStream(archive.getInputStream(entry))) {
                        for (var file = input.getNextEntry(); file != null; file = input.getNextEntry()) {
                            addPackage(nested, file.getName());
                        }
                    }
                    packages.put("dependency" + dependency++, nested);
                }
            }
        }
        Map<String, ModuleReference> modules = new LinkedHashMap<>();
        for (var entry : packages.entrySet()) {
            modules.put(entry.getKey(), reference(ModuleDescriptor.newAutomaticModule(entry.getKey())
                    .packages(entry.getValue()).build()));
        }
        var minecraft = ModuleDescriptor.newModule("minecraft");
        modules.keySet().forEach(minecraft::requires);
        modules.put("minecraft", reference(minecraft.build()));
        ModuleFinder finder = new ModuleFinder() {
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(modules.get(name));
            }
            public Set<ModuleReference> findAll() {
                return Set.copyOf(modules.values());
            }
        };
        Configuration.resolveAndBind(finder, List.of(ModuleLayer.boot().configuration()),
                ModuleFinder.of(), Set.of("minecraft"));
        System.out.println("PASS actual JAR package ownership through Java module resolver: modules=" + modules.size());
        verifyAvif(jar);
    }

    private static void verifyPlatformBridges(Path jar) throws Exception {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (var archive = new ZipFile(jar.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                var node = new ClassNode();
                try (var input = archive.getInputStream(entry)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                if ((node.version & 0xffff) > Opcodes.V17) {
                    throw new AssertionError("Outer JAR class exceeds Java 17: " + node.name);
                }
                classes.put(node.name, node);
            }
        }
        int bridges = 0;
        String annotation = "Ldev/architectury/injectables/annotations/ExpectPlatform;";
        for (ClassNode type : classes.values()) {
            for (MethodNode method : type.methods) {
                if (method.invisibleAnnotations == null || method.invisibleAnnotations.stream()
                        .noneMatch(value -> value.desc.equals(annotation))) {
                    continue;
                }
                boolean resolved = false;
                for (var instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode call) || !call.owner.contains("/forge/")) {
                        continue;
                    }
                    ClassNode implementation = classes.get(call.owner);
                    boolean exists = implementation != null && implementation.methods.stream().anyMatch(target ->
                            target.name.equals(call.name) && target.desc.equals(call.desc)
                                    && (target.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                                    == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
                    if (call.getOpcode() != Opcodes.INVOKESTATIC || !exists) {
                        throw new AssertionError("Unresolved transformed bridge: " + call.owner + "." + call.name + call.desc);
                    }
                    resolved = true;
                }
                if (!resolved) {
                    throw new AssertionError("Untransformed platform bridge: " + type.name + "." + method.name);
                }
                bridges++;
            }
        }
        if (bridges == 0) {
            throw new AssertionError("No platform bridges checked");
        }
        System.out.println("PASS Java 17 outer classes and " + bridges + " transformed @ExpectPlatform owners/descriptors");
    }

    private static ModuleReference reference(ModuleDescriptor descriptor) {
        return new ModuleReference(descriptor, URI.create("memory:/" + descriptor.name())) {
            public ModuleReader open() throws IOException {
                throw new IOException("Package-resolution probe does not load module resources");
            }
        };
    }

    private static void addPackage(Set<String> packages, String name) {
        if (name.endsWith(".class") && name.contains("/") && !name.startsWith("META-INF/")) {
            packages.add(name.substring(0, name.lastIndexOf('/')).replace('/', '.'));
        }
    }

    private static void verifyAvif(Path jar) throws Exception {
        Path temporary = Files.createTempDirectory("loader-avif-probe-").toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try {
            List<java.net.URL> urls = new ArrayList<>();
            urls.add(jar.toUri().toURL());
            try (ZipFile archive = new ZipFile(jar.toFile())) {
                if (archive.getEntry("com/github/ustc_zzzz/imageio/avif/Native.class") == null) {
                    throw new AssertionError("AVIF JNI classes must share the outer module with the adapter");
                }
                var entries = archive.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.getName().startsWith("META-INF/jars/") && entry.getName().endsWith(".jar")) {
                        Path nested = temporary.resolve("dependency" + files.size() + ".jar");
                        try (var input = archive.getInputStream(entry)) {
                            Files.copy(input, nested);
                        }
                        files.add(nested);
                        urls.add(nested.toUri().toURL());
                    }
                }
            }
            try (URLClassLoader loader = new URLClassLoader(urls.toArray(java.net.URL[]::new),
                    ClassLoader.getPlatformClassLoader())) {
                boolean registered = ServiceLoader.load(ImageReaderSpi.class, loader).stream()
                        .anyMatch(provider -> provider.type().getName()
                                .equals("com.github.ustc_zzzz.imageio.avif.AVIFImageReaderSpi"));
                if (!registered) {
                    throw new AssertionError("Packaged AVIF ImageIO service registration missing");
                }
                Class<?> limits = loader.loadClass("top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits");
                Class<?> budget = loader.loadClass("top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget");
                Object allowance = budget.getConstructor(limits, BooleanSupplier.class, long.class)
                        .newInstance(limits.getField("DEFAULT").get(null), (BooleanSupplier) () -> false, 10_000L);
                byte[] encoded;
                try (var input = LoaderModuleSmokeTest.class.getResourceAsStream("/ysm/image-vectors/rgba-quadrants.avif")) {
                    encoded = input.readAllBytes();
                }
                Class<?> decoder = loader.loadClass("com.github.ustc_zzzz.imageio.avif.FiguraAvifDecoder");
                var image = (java.awt.image.BufferedImage) decoder
                        .getMethod("read", byte[].class, int.class, int.class, budget)
                        .invoke(null, encoded, 0, 0, allowance);
                if (image == null || image.getWidth() != 16 || image.getHeight() != 16) {
                    throw new AssertionError("Packaged AVIF JNI decoding failed");
                }
                pixel(image.getRGB(3, 3), 0xffff0000);
                pixel(image.getRGB(12, 3), 0x8000ff00);
                pixel(image.getRGB(3, 12), 0x400000ff);
                if (image.getRGB(12, 12) >>> 24 != 0) {
                    throw new AssertionError("Packaged AVIF alpha differs");
                }
                image.flush();
                System.out.println("PASS isolated final-JAR AVIF ImageIO registration and actual native decode");
            }
        } finally {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
            Files.deleteIfExists(temporary);
        }
    }

    private static void pixel(int actual, int expected) {
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs((actual >>> shift & 255) - (expected >>> shift & 255)) > 2) {
                throw new AssertionError("Packaged AVIF pixel differs from the independent vector");
            }
        }
    }
}
