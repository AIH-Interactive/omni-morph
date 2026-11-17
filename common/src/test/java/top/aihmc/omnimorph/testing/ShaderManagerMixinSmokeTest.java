package top.aihmc.omnimorph.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

import java.nio.file.Path;

/** Applies the 26.2 shader lookup injection; this version has no PipelineCache constructor hook. */
public final class ShaderManagerMixinSmokeTest {
    private static final String TARGET = "net.minecraft.client.renderer.ShaderManager";
    private static final String MIXIN = "top/aihmc/omnimorph/mixin/AvatarShaderSourceMixin.class";

    private ShaderManagerMixinSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        String platform = args[0];
        Path artifact = Path.of(args[1]).toRealPath();
        var loader = ClassLoader.getSystemClassLoader();
        var mixinUrl = loader.getResource(MIXIN);
        require(mixinUrl != null && mixinUrl.toString().contains(artifact.toUri().getRawPath()),
                "The Mixin must come from the final platform artifact: " + mixinUrl);
        var targetUrl = loader.getResource(TARGET.replace('.', '/') + ".class");
        require(targetUrl != null, "ShaderManager target bytes are available");
        require(targetUrl.toString().contains("neoforge-") == platform.equals("neoforge"),
                "The target must use the selected platform's Minecraft JAR: " + targetUrl);
        System.out.println("Platform=" + platform + " artifact=" + artifact);
        System.out.println("Mixin=" + mixinUrl + " target=" + targetUrl);

        System.setProperty("mixin.service", OfflineMixinService.class.getName());
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("omni-morph-common.mixins.json");
        var transformer = ((OfflineMixinService) MixinService.getService()).createTransformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        byte[] original;
        try (var input = targetUrl.openStream()) {
            original = input.readAllBytes();
        }
        byte[] transformed = transformer.transformClassBytes(TARGET, TARGET, original);
        var node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        var lookup = node.methods.stream()
                .filter(method -> method.name.equals("getShader")
                        && method.desc.equals("(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;"))
                .findFirst().orElseThrow(() -> new AssertionError("Native shader lookup is missing"));
        var handler = node.methods.stream()
                .filter(method -> method.name.contains("avatarShader"))
                .findFirst().orElseThrow(() -> new AssertionError("Shader handler was not merged"));
        boolean callsHandler = false;
        for (var instruction : lookup.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)
                    && call.name.equals(handler.name) && call.desc.equals(handler.desc)) {
                callsHandler = true;
            }
        }
        require(callsHandler, "Native shader lookup must call the avatar-aware handler");
        boolean resolvesAvatar = false;
        for (var instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("top/aihmc/omnimorph/model/rendering/material/AvatarMaterial")
                    && call.name.equals("shaderSource")) {
                resolvesAvatar = true;
            }
        }
        require(resolvesAvatar, "The handler must retain avatar shader source lookup");
        System.out.println("PASS " + platform
                + " actual Sponge Mixin application: packaged AvatarShaderSourceMixin -> ShaderManager.getShader");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
