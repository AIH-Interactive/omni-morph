package top.aihmc.omnimorph.neoforge;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;

/** Local regression for the packaged JPMS boundary; never run by Actions. */
public final class AvifModuleSmokeTest {
    public static void main(String[] args) throws Exception {
        ModuleFinder finder = ModuleFinder.of(Path.of(args[0]));
        String name = finder.findAll().iterator().next().descriptor().name();
        var configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), Set.of(name));
        ModuleLayer layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getPlatformClassLoader());
        ClassLoader loader = layer.findLoader(name);
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> adapter = Class.forName("com.github.ustc_zzzz.imageio.avif.FiguraAvifDecoder", false, loader);
        Class<?> provider = Class.forName("com.github.ustc_zzzz.imageio.avif.AVIFImageReaderSpi", true, loader);
        Class<?> nativeApi = Class.forName("com.github.ustc_zzzz.imageio.avif.Native", false, loader);
        if (adapter.getModule() != provider.getModule() || adapter.getModule() != nativeApi.getModule()) {
            throw new AssertionError("AVIF classes must share one named module");
        }
        ImageIO.scanForPlugins();
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xff12ab34);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "PNG", bytes)) {
            throw new AssertionError("PNG writer not available");
        }
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        if (decoded.getRGB(0, 0) != source.getRGB(0, 0)) {
            throw new AssertionError("PNG round-trip changed pixels");
        }
        System.out.println("PASS: AVIF provider/JNI adapter share module " + name + "; ImageIO initialization and PNG conversion succeeded");
    }
}
