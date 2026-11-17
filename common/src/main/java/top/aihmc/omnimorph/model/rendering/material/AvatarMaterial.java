package top.aihmc.omnimorph.model.rendering.material;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Avatar-owned shader materials submitted through Minecraft's normal buffered renderer. */
public final class AvatarMaterial {
    private static final Map<Avatar, Set<AvatarMaterial>> OWNED = new WeakHashMap<>();

    private final String vertexSource;
    private final String fragmentSource;
    private final Map<String, FiguraTexture> textures = new LinkedHashMap<>();
    private final RenderPipeline pipeline;
    private final RenderType renderType;
    private final RenderPipeline fadedPipeline;
    private final RenderType fadedRenderType;
    private boolean compiled;
    private boolean closed;
    public float time;

    public AvatarMaterial(Avatar owner, LuaTable definition) {
        var sources = MaterialShaderSource.prepare(definition.get("vertex_shader").checkjstring(),
                definition.get("fragment_shader").checkjstring());
        vertexSource = sources.vertex();
        fragmentSource = sources.fragment();
        LuaTable bindings = definition.get("textures").checktable();
        for (var name : bindings.keys()) {
            String sampler = name.checkjstring();
            if (!sampler.matches("[A-Za-z_][A-Za-z_0-9]*")) {
                throw new LuaError("Invalid shader sampler name: " + sampler);
            }
            Object value = bindings.get(name).touserdata(FiguraTexture.class);
            if (!(value instanceof FiguraTexture texture) || !texture.belongsTo(owner)) {
                throw new LuaError("Shader samplers must use this avatar's textures");
            }
            textures.put(sampler, texture);
        }
        String blend = definition.get("blend").optjstring("OPAQUE");
        ColorTargetState color = switch (blend) {
            case "OPAQUE" -> ColorTargetState.DEFAULT;
            case "ALPHA" -> new ColorTargetState(BlendFunction.TRANSLUCENT);
            case "ADDITIVE" -> new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA,
                    BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA));
            default -> throw new LuaError("Unknown shader blend: " + blend);
        };
        CompareOp depthTest = definition.get("depth_test").optboolean(true)
                ? DepthStencilState.DEFAULT.depthTest() : CompareOp.ALWAYS_PASS;
        String key = digest(vertexSource + "\n" + fragmentSource);
        var builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(new FiguraIdentifier("avatar_material/" + key))
                .withVertexShader(new FiguraIdentifier("avatar_material/" + key + "/vertex"))
                .withFragmentShader(new FiguraIdentifier("avatar_material/" + key + "/fragment"))
                .withVertexBinding(0, MaterialVertexFormat.FORMAT)
                .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS)
                .withCull(definition.get("cull").optboolean(false))
                .withColorTargetState(color)
                .withDepthStencilState(new DepthStencilState(depthTest,
                        definition.get("depth_write").optboolean(!blend.equals("ALPHA"))));
        var samplers = com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder();
        textures.keySet().forEach(name -> samplers.withUniform(name,
                com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER));
        if (definition.get("lightmap").optboolean(false)) {
            samplers.withUniform("Sampler2", com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER);
        }
        builder.withBindGroupLayout(samplers.build());
        pipeline = builder.build();
        boolean lightmap = definition.get("lightmap").optboolean(false);
        renderType = renderType(pipeline, lightmap, !blend.equals("OPAQUE"));
        fadedPipeline = blend.equals("OPAQUE")
                ? builder.withLocation(new FiguraIdentifier("avatar_material/" + key + "/fade"))
                        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                        .withDepthStencilState(new DepthStencilState(depthTest, false))
                        .build()
                : pipeline;
        fadedRenderType = fadedPipeline == pipeline ? renderType : renderType(fadedPipeline, lightmap, true);
        synchronized (OWNED) {
            OWNED.computeIfAbsent(owner, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(this);
        }
    }

    public RenderType renderType() {
        return renderType(false);
    }

    private RenderType renderType(RenderPipeline pipeline, boolean lightmap, boolean sort) {
        var setup = RenderSetup.builder(pipeline)
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
        textures.forEach((name, texture) -> setup.withTexture(name, texture.getLocation(), texture::getSampler));
        if (lightmap) {
            setup.useLightmap();
        }
        if (sort) {
            setup.sortOnUpload();
        }
        return new RenderType("avatar_shader_material", setup.createRenderSetup());
    }

    public RenderType renderType(boolean faded) {
        if (closed) {
            throw new LuaError("Shader material has been released");
        }
        textures.values().forEach(texture -> texture.uploadIfDirty(false, false));
        // The native cache can be cleared by a resource reload or another avatar's retirement.
        // Supplying the source here also makes the next normal draw valid after either event.
        var selectedPipeline = faded ? fadedPipeline : pipeline;
        var result = RenderSystem.getCompiledPipelineNullable(selectedPipeline);
        if (result == null || result.isClosed()) {
            throw new LuaError("Shader material failed native pipeline compilation");
        }
        compiled = true;
        return faded ? fadedRenderType : renderType;
    }

    public static void release(Avatar owner) {
        Set<AvatarMaterial> materials;
        synchronized (OWNED) {
            materials = OWNED.remove(owner);
        }
        if (materials == null) {
            return;
        }
        boolean clearCache = materials.stream().anyMatch(material -> material.compiled);
        materials.forEach(material -> material.closed = true);
        if (clearCache) {
            // Minecraft owns shader programs and exposes cache-wide retirement, not per-pipeline close.
            Minecraft.getInstance().execute(() -> {
                if (RenderSystem.currentPipelineCache != null) {
                    RenderSystem.currentPipelineCache.clear();
                }
                if (RenderSystem.fallbackPipelineCache != null) {
                    RenderSystem.fallbackPipelineCache.clear();
                }
            });
        }
    }

    /** Adds model-owned shaders while retaining Minecraft's include and ordinary shader sources. */
    public static com.mojang.renderpearl.api.pipeline.ShaderSource sources(
            com.mojang.renderpearl.api.pipeline.ShaderSource original) {
        return new com.mojang.renderpearl.api.pipeline.ShaderSource() {
            @Override
            public String getShader(Identifier id, ShaderType type) {
                String authored = shaderSource(id, type);
                return authored != null ? authored : original.getShader(id, type);
            }

            @Override
            public CachedIncludeSource getInclude(Identifier id) {
                return original.getInclude(id);
            }

            @Override
            public void close() {
                original.close();
            }
        };
    }

    /** Native cache misses after resource reloads must still resolve active avatars' shaders. */
    public static String shaderSource(Identifier id, ShaderType type) {
        synchronized (OWNED) {
            for (Set<AvatarMaterial> materials : OWNED.values()) {
                for (AvatarMaterial material : materials) {
                    if (!material.closed) {
                        if (type == ShaderType.VERTEX && id.equals(material.pipeline.getShaders().get(ShaderType.VERTEX))) {
                            return material.vertexSource;
                        }
                        if (type == ShaderType.FRAGMENT && id.equals(material.pipeline.getShaders().get(ShaderType.FRAGMENT))) {
                            return material.fragmentSource;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
