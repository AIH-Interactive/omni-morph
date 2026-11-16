package top.aihmc.omnimorph.model.rendering.material;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
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
        vertexSource = definition.get("vertex_shader").checkjstring();
        fragmentSource = definition.get("fragment_shader").checkjstring();
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
            case "ADDITIVE" -> new ColorTargetState(new BlendFunction(SourceFactor.SRC_ALPHA,
                    DestFactor.ONE, SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA));
            default -> throw new LuaError("Unknown shader blend: " + blend);
        };
        CompareOp depthTest = definition.get("depth_test").optboolean(true)
                ? DepthStencilState.DEFAULT.depthTest() : CompareOp.ALWAYS_PASS;
        String key = digest(vertexSource + "\n" + fragmentSource);
        var builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET,
                        RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(new FiguraIdentifier("avatar_material/" + key))
                .withVertexShader(new FiguraIdentifier("avatar_material/" + key + "/vertex"))
                .withFragmentShader(new FiguraIdentifier("avatar_material/" + key + "/fragment"))
                .withVertexFormat(MaterialVertexFormat.FORMAT, VertexFormat.Mode.QUADS)
                .withCull(definition.get("cull").optboolean(false))
                .withColorTargetState(color)
                .withDepthStencilState(new DepthStencilState(depthTest,
                        definition.get("depth_write").optboolean(!blend.equals("ALPHA"))));
        textures.keySet().forEach(builder::withSampler);
        if (definition.get("lightmap").optboolean(false)) {
            builder.withSampler("Sampler2");
        }
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
        var result = RenderSystem.getDevice().precompilePipeline(selectedPipeline,
                (id, type) -> type == ShaderType.VERTEX ? vertexSource : fragmentSource);
        if (!result.isValid()) {
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
            var device = RenderSystem.getDevice();
            Minecraft.getInstance().execute(device::clearPipelineCache);
        }
    }

    /** Native cache misses after resource reloads must still resolve active avatars' shaders. */
    public static String shaderSource(Identifier id, ShaderType type) {
        synchronized (OWNED) {
            for (Set<AvatarMaterial> materials : OWNED.values()) {
                for (AvatarMaterial material : materials) {
                    if (!material.closed) {
                        if (type == ShaderType.VERTEX && id.equals(material.pipeline.getVertexShader())) {
                            return material.vertexSource;
                        }
                        if (type == ShaderType.FRAGMENT && id.equals(material.pipeline.getFragmentShader())) {
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
