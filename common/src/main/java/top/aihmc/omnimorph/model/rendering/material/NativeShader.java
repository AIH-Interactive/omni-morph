package top.aihmc.omnimorph.model.rendering.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.luaj.vm2.LuaError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owns a 1.21.1 shader program; resources are supplied without writing author code to disk. */
public final class NativeShader implements AutoCloseable {
    private final String name = "omnimorph_" + UUID.randomUUID().toString().replace("-", "");
    private final String vertex;
    private final String fragment;
    private final VertexFormat format;
    private final List<String> samplers;
    private ShaderInstance shader;

    public NativeShader(String vertex, String fragment, VertexFormat format, List<String> samplers) {
        this.vertex = vertex;
        this.fragment = fragment;
        this.format = format;
        this.samplers = List.copyOf(samplers);
    }

    public ShaderInstance get() {
        if (shader == null) {
            try {
                shader = new ShaderInstance(resources(Minecraft.getInstance().getResourceManager()), name, format);
            } catch (IOException exception) {
                throw new LuaError("Shader material failed native compilation: " + exception.getMessage());
            }
        }
        return shader;
    }

    ResourceProvider resources(ResourceProvider parent) {
        return id -> {
            String path = id.getPath();
            String source = path.equals("shaders/core/" + name + ".json") ? definition()
                    : path.equals("shaders/core/" + name + ".vsh") ? vertex
                    : path.equals("shaders/core/" + name + ".fsh") ? fragment : null;
            if (source == null) {
                return parent.getResource(id);
            }
            var pack = parent.getResource(net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                    "shaders/core/particle.json")).orElseThrow().source();
            byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            return Optional.of(new Resource(pack, () -> new ByteArrayInputStream(bytes)));
        };
    }

    private String definition() {
        JsonObject json = new JsonObject();
        json.addProperty("vertex", name);
        json.addProperty("fragment", name);
        JsonArray attributes = new JsonArray();
        format.getElementAttributeNames().forEach(attributes::add);
        json.add("attributes", attributes);
        JsonArray bindings = new JsonArray();
        for (String sampler : samplers) {
            JsonObject binding = new JsonObject();
            binding.addProperty("name", sampler);
            bindings.add(binding);
        }
        json.add("samplers", bindings);
        JsonArray uniforms = new JsonArray();
        uniform(uniforms, "ModelViewMat", "matrix4x4", 16, 1);
        uniform(uniforms, "ProjMat", "matrix4x4", 16, 1);
        uniform(uniforms, "TextureMat", "matrix4x4", 16, 1);
        uniform(uniforms, "ColorModulator", "float", 4, 1);
        uniform(uniforms, "FogStart", "float", 1, 0);
        uniform(uniforms, "FogEnd", "float", 1, 1);
        uniform(uniforms, "FogColor", "float", 4, 0);
        uniform(uniforms, "FogShape", "int", 1, 0);
        uniform(uniforms, "GameTime", "float", 1, 0);
        uniform(uniforms, "ScreenSize", "float", 2, 1);
        uniform(uniforms, "Light0_Direction", "float", 3, 0);
        uniform(uniforms, "Light1_Direction", "float", 3, 0);
        json.add("uniforms", uniforms);
        return json.toString();
    }

    private void uniform(JsonArray uniforms, String name, String type, int count, float value) {
        // ShaderInstance logs every declared JSON uniform absent from GLSL. Only describe author declarations.
        var declaration = java.util.regex.Pattern.compile("\\buniform\\s+\\w+\\s+" + name + "\\b");
        if (!declaration.matcher(vertex).find() && !declaration.matcher(fragment).find()) {
            return;
        }
        JsonObject uniform = new JsonObject();
        uniform.addProperty("name", name);
        uniform.addProperty("type", type);
        uniform.addProperty("count", count);
        JsonArray values = new JsonArray();
        if (type.equals("matrix4x4")) {
            for (int index = 0; index < 16; index++) {
                values.add(index % 5 == 0 ? 1 : 0);
            }
        } else {
            values.add(value);
        }
        uniform.add("values", values);
        uniforms.add(uniform);
    }

    @Override
    public void close() {
        if (shader != null) {
            shader.close();
            shader = null;
        }
    }
}
