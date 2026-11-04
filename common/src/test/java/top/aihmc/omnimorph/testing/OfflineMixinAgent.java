package top.aihmc.omnimorph.testing;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Applies production mixins before their native targets load; never starts Minecraft. */
public final class OfflineMixinAgent {
    public static void premain(String configuration, Instrumentation instrumentation) {
        System.setProperty("mixin.service", OfflineMixinService.class.getName());
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration(configuration);
        Mixins.addConfiguration("omnimorph-offline-platform.mixins.json");
        Mixins.addConfiguration("omnimorph-offline-pip.mixins.json");
        Mixins.addConfiguration("omnimorph-offline-particle.mixins.json");
        var transformer = ((OfflineMixinService) MixinService.getService()).createTransformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                    ProtectionDomain domain, byte[] bytes) {
                if (name == null || !name.startsWith("net/minecraft/")) {
                    return null;
                }
                String binaryName = name.replace('/', '.');
                try {
                    return transformer.transformClassBytes(binaryName, binaryName, bytes);
                } catch (Throwable failure) {
                    // Instrumentation ignores transformer exceptions. Reject the class instead of
                    // silently running an untransformed target and producing misleading evidence.
                    failure.printStackTrace(System.err);
                    return new byte[]{0};
                }
            }
        });
    }
}
