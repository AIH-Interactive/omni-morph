package top.aihmc.omnimorph.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;

/** A local test host for the real Mixin transformer, without a game launcher or client. */
public final class OfflineMixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, IClassTracker, ITransformerProvider {
    private final ClassLoader loader = ClassLoader.getSystemClassLoader();

    public IMixinTransformer createTransformer() {
        return getInternal(IMixinTransformerFactory.class).createTransformer();
    }

    @Override public String getName() { return "OMP offline verification"; }
    @Override public boolean isValid() { return true; }
    @Override public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.DEFAULT; }
    @Override public IClassProvider getClassProvider() { return this; }
    @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
    @Override public ITransformerProvider getTransformerProvider() { return this; }
    @Override public IClassTracker getClassTracker() { return this; }
    @Override public IMixinAuditTrail getAuditTrail() { return null; }
    @Override public IFeatureValidator getFeatureValidator() { return IFeatureValidator.ALLOW_ALL; }
    @Override public IAdviceProvider getAdviceProvider() { return IAdviceProvider.GENERIC; }
    @Override public Collection<String> getPlatformAgents() { return List.of(); }
    @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("omp-offline"); }
    @Override public InputStream getResourceAsStream(String name) { return loader.getResourceAsStream(name); }
    @Override public URL[] getClassPath() { return new URL[0]; }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
    @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader);
    }
    @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return findClass(name, initialize);
    }
    @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, false);
    }
    @Override public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, ClassReader.EXPAND_FRAMES);
    }
    @Override public ClassNode getClassNode(String name, boolean runTransformers, int flags) throws ClassNotFoundException, IOException {
        try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) {
                throw new ClassNotFoundException(name);
            }
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, flags);
            return node;
        }
    }
    @Override public void registerInvalidClass(String name) { }
    @Override public boolean isClassLoaded(String name) { return false; }
    @Override public String getClassRestrictions(String name) { return ""; }
    @Override public Collection<ITransformer> getTransformers() { return List.of(); }
    @Override public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    @Override public void addTransformerExclusion(String name) { }
}
