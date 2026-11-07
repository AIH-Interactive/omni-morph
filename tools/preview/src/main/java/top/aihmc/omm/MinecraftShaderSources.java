package top.aihmc.omm;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/** Supplies local Minecraft shader resources to its original import preprocessor. */
final class MinecraftShaderSources implements ShaderSource {
    private record Key(Identifier identifier, ShaderType type) {
    }

    private final Map<Identifier, Resource> resources = new HashMap<>();
    private final Map<Key, String> processed = new HashMap<>();
    private final Method preprocessor;

    MinecraftShaderSources() throws Exception {
        Path jar = Path.of(ShaderManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith("assets/minecraft/shaders/")) {
                    continue;
                }
                byte[] bytes;
                try (var stream = zip.getInputStream(entry)) {
                    bytes = stream.readAllBytes();
                }
                Identifier id = Identifier.withDefaultNamespace(entry.getName().substring("assets/minecraft/".length()));
                resources.put(id, new Resource(null, () -> new ByteArrayInputStream(bytes)));
            }
        }
        preprocessor = ShaderManager.class.getDeclaredMethod("createPreprocessor", Map.class, Identifier.class);
        preprocessor.setAccessible(true);
        System.out.println("MINECRAFT SHADER SOURCE " + jar + " resources=" + resources.size());
    }

    @Override
    public String get(Identifier identifier, ShaderType type) {
        Key key = new Key(identifier, type);
        String cached = processed.get(key);
        if (cached != null) {
            return cached;
        }
        Identifier file = type.idConverter().idToFile(identifier);
        Resource resource = resources.get(file);
        if (resource == null) {
            throw new IllegalArgumentException("Missing local Minecraft shader: " + file);
        }
        try (var stream = resource.open()) {
            var processor = (GlslPreprocessor) preprocessor.invoke(null, resources, file);
            String result = String.join("", processor.process(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            processed.put(key, result);
            return result;
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot prepare local Minecraft shader " + file, failure);
        }
    }
}
