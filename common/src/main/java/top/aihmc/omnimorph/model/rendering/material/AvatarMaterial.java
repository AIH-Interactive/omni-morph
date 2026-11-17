package top.aihmc.omnimorph.model.rendering.material;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

import java.util.Collections;
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
    private final RenderType renderType;
    private final RenderType fadedRenderType;
    private final NativeShader shader;
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
        if (!Set.of("OPAQUE", "ALPHA", "ADDITIVE").contains(blend)) {
            throw new LuaError("Unknown shader blend: " + blend);
        }
        var samplers = new java.util.ArrayList<>(textures.keySet());
        boolean lightmap = definition.get("lightmap").optboolean(false);
        if (lightmap && !samplers.contains("Sampler2")) {
            samplers.add("Sampler2");
        }
        shader = new NativeShader(vertexSource, fragmentSource, MaterialVertexFormat.FORMAT, samplers);
        boolean depthTest = definition.get("depth_test").optboolean(true);
        boolean depthWrite = definition.get("depth_write").optboolean(!blend.equals("ALPHA"));
        boolean cull = definition.get("cull").optboolean(false);
        renderType = MaterialType.create(this, blend, depthTest, depthWrite, cull, lightmap);
        fadedRenderType = blend.equals("OPAQUE")
                ? MaterialType.create(this, "ALPHA", depthTest, false, cull, lightmap) : renderType;
        synchronized (OWNED) {
            OWNED.computeIfAbsent(owner, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(this);
        }
    }

    public RenderType renderType() {
        return renderType(false);
    }

    public RenderType renderType(boolean faded) {
        if (closed) {
            throw new LuaError("Shader material has been released");
        }
        textures.values().forEach(texture -> texture.uploadIfDirty(false, false));
        return faded ? fadedRenderType : renderType;
    }

    public static void release(Avatar owner) {
        Set<AvatarMaterial> materials;
        synchronized (OWNED) {
            materials = OWNED.remove(owner);
        }
        if (materials != null) {
            for (AvatarMaterial material : materials) {
                material.closed = true;
                Minecraft.getInstance().execute(material.shader::close);
            }
        }
    }

    /** Vanilla resource reloads retire GL programs; active owners compile again on the next draw. */
    public static void invalidateShaders() {
        synchronized (OWNED) {
            OWNED.values().forEach(materials -> materials.forEach(material -> material.shader.close()));
        }
    }

    private static final class MaterialType extends RenderType {
        private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard(
                "omnimorph_material_additive", () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                            GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                }, () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        private MaterialType() {
            super("avatar_shader_material", MaterialVertexFormat.FORMAT, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {});
        }

        private static RenderType create(AvatarMaterial material, String blend, boolean depthTest,
                                         boolean depthWrite, boolean cull, boolean lightmap) {
            TransparencyStateShard transparency = switch (blend) {
                case "ALPHA" -> TRANSLUCENT_TRANSPARENCY;
                case "ADDITIVE" -> ADDITIVE;
                default -> NO_TRANSPARENCY;
            };
            return create("avatar_shader_material", MaterialVertexFormat.FORMAT, VertexFormat.Mode.QUADS,
                    256, false, !blend.equals("OPAQUE"), CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> {
                                var program = material.shader.get();
                                material.textures.forEach((name, texture) -> program.setSampler(name, texture.getId()));
                                return program;
                            }))
                            .setTransparencyState(transparency)
                            .setLightmapState(lightmap ? LIGHTMAP : NO_LIGHTMAP)
                            .setCullState(cull ? CULL : NO_CULL)
                            .setDepthTestState(depthTest ? LEQUAL_DEPTH_TEST : NO_DEPTH_TEST)
                            .setWriteMaskState(depthWrite ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                            .createCompositeState(true));
        }
    }

}
