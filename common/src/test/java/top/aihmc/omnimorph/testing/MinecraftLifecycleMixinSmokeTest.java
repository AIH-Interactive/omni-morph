package top.aihmc.omnimorph.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

import java.nio.file.Path;

/** Applies the packaged common configuration with Sponge Mixin to each platform's actual target bytes. */
public final class MinecraftLifecycleMixinSmokeTest {
    private static final String TARGET = "net.minecraft.client.Minecraft";
    private static final String MIXIN = "org/figuramc/figura/mixin/MinecraftMixin.class";

    private MinecraftLifecycleMixinSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        String platform = args[0];
        Path artifact = Path.of(args[1]).toRealPath();
        var loader = ClassLoader.getSystemClassLoader();
        var mixinUrl = loader.getResource(MIXIN);
        require(mixinUrl != null && mixinUrl.toString().contains(artifact.toUri().getRawPath()),
                "The Mixin must come from the final platform artifact: " + mixinUrl);
        var targetUrl = loader.getResource(TARGET.replace('.', '/') + ".class");
        require(targetUrl != null, "Minecraft target bytes are available");
        require(targetUrl.toString().contains("neoforge-") == platform.equals("neoforge"),
                "The target must use the selected platform's Minecraft JAR: " + targetUrl);
        System.out.println("Platform=" + platform + " artifact=" + artifact);
        System.out.println("Mixin=" + mixinUrl + " target=" + targetUrl);

        System.setProperty("mixin.service", OfflineMixinService.class.getName());
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("figura-common.mixins.json");
        var transformer = ((OfflineMixinService) MixinService.getService()).createTransformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        byte[] original;
        try (var input = targetUrl.openStream()) {
            original = input.readAllBytes();
        }
        byte[] transformed = transformer.transformClassBytes(TARGET, TARGET, original);
        var node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var close = node.methods.stream()
                .filter(method -> method.name.equals("close") && method.desc.equals("()V"))
                .findFirst().orElseThrow(() -> new AssertionError("Native close()V is missing"));
        var cleanup = node.methods.stream()
                .filter(method -> method.name.contains("figura$closePerformanceHttp"))
                .findFirst().orElseThrow(() -> new AssertionError("Sponge did not merge the cleanup handler"));
        boolean callsHandler = false;
        for (var instruction : close.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)
                    && call.name.equals(cleanup.name) && call.desc.equals(cleanup.desc)) {
                callsHandler = true;
                break;
            }
            require(instruction.getOpcode() != Opcodes.RETURN, "Cleanup must precede the native return");
        }
        require(callsHandler, "The native close method must invoke the injected cleanup");
        boolean closesServer = false;
        for (var instruction : cleanup.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("top/aihmc/omnimorph/debug/PerformanceDebug")
                    && call.name.equals("close") && call.desc.equals("()V")) {
                closesServer = true;
            }
        }
        require(closesServer, "The injected handler must retain production HTTP cleanup");
        System.out.println("PASS " + platform + " actual Sponge Mixin application: packaged MinecraftMixin -> close()V -> PerformanceDebug.close()");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
