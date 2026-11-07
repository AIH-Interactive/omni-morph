package top.aihmc.omm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Checks the unmodified native binary shipped in the selected MOD. */
public final class JarNativeProbe {
    private JarNativeProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path jarPath = Path.of(args[1]).toAbsolutePath();
        byte[] binary;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            try (var input = jar.getInputStream(jar.getJarEntry("natives/windows-x64/ysm-core.dll"))) {
                binary = input.readAllBytes();
            }
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary));
        Path library = Path.of("build/preview/jar-host/native", args[0], hash, "ysm-core.dll").toAbsolutePath();
        Files.createDirectories(library.getParent());
        if (!Files.exists(library)) {
            Files.write(library, binary);
        }
        System.load(library.toString());
        System.out.println("NATIVE LOADED " + jarPath + " sha256=" + hash);
        if (args[0].equals("spm")) {
            Class<?> bridge = Class.forName("com.elfmcys.yesstevemodel.geckolib3.geo.render.built.ModelAccelerationBridge");
            var version = bridge.getDeclaredMethod("nGetAbiVersion");
            version.setAccessible(true);
            System.out.println("ABI " + version.invoke(null));
        }
    }
}
