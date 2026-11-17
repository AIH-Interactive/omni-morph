package top.aihmc.omnimorph.testing;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

import java.nio.file.Path;

/** Applies the released shader-source Mixin and invokes the actual transformed constructor. */
public final class PipelineCacheMixinSmokeTest {
    private static final String TARGET = "com.mojang.blaze3d.pipeline.PipelineCache";
    private static final String MIXIN = "top/aihmc/omnimorph/mixin/AvatarShaderSourceMixin.class";

    private PipelineCacheMixinSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        String platform = args[0];
        Path artifact = Path.of(args[1]).toRealPath();
        var loader = ClassLoader.getSystemClassLoader();
        var mixinUrl = loader.getResource(MIXIN);
        require(mixinUrl != null && mixinUrl.toString().contains(artifact.toUri().getRawPath()),
                "The Mixin must come from the final platform artifact: " + mixinUrl);
        var targetUrl = loader.getResource(TARGET.replace('.', '/') + ".class");
        require(targetUrl != null, "PipelineCache target bytes are available");
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
        Class<?> targetClass = new TargetLoader(loader).define(transformed);

        var source = new RecordingSource();
        // A device is not needed for construction, source lookup, or closing an empty cache.
        var cache = (AutoCloseable) targetClass.getConstructor(GpuDevice.class, ShaderSource.class)
                .newInstance(null, source);
        try {
            var sourceField = targetClass.getDeclaredField("shaderSource");
            sourceField.setAccessible(true);
            var wrapped = (ShaderSource) sourceField.get(cache);
            require(wrapped != source, "The actual constructor must store the avatar-aware source wrapper");
            require("vanilla-shader".equals(wrapped.getShader(null, ShaderType.VERTEX)),
                    "The wrapper must retain ordinary shader lookup");
            require(wrapped.getInclude(null) == null && source.includeRequests == 1,
                    "The wrapper must delegate include lookup");
        } finally {
            cache.close();
        }
        require(source.closeCalls == 1, "Closing the real cache must close the original source once");
        System.out.println("PASS " + platform
                + " actual Sponge Mixin application and JVM constructor: PipelineCache wraps shader sources and forwards close");
    }

    private static final class TargetLoader extends ClassLoader {
        private TargetLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(byte[] bytes) {
            return defineClass(TARGET, bytes, 0, bytes.length);
        }
    }

    private static final class RecordingSource implements ShaderSource {
        private int includeRequests;
        private int closeCalls;

        @Override
        public String getShader(Identifier id, ShaderType type) {
            return "vanilla-shader";
        }

        @Override
        public CachedIncludeSource getInclude(Identifier id) {
            includeRequests++;
            return null;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
