package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import org.joml.Matrix4f;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.rendering.material.MaterialVertexFormat;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real first-person drawFigura -> pose capture -> renderer -> custom attribute writer. No pixel claim. */
public final class FirstPersonMaterialSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("first-person-material-");
        try {
            Files.createDirectories(temporary.resolve("model"));
            for (String relative : List.of("model/model.bbmodel", "script.lua")) {
                try (var input = FirstPersonMaterialSmokeTest.class.getResourceAsStream(
                        "/assets/omni-morph/cosmetic_templates/accessories/prismatic-badge/" + relative)) {
                    Files.copy(java.util.Objects.requireNonNull(input), temporary.resolve(relative));
                }
            }
            Files.writeString(temporary.resolve("avatar.json"), "{\"name\":\"First-person material fixture\"}");
            YsmMaterialNativeSmokeTest.replayFiguraModel(temporary, base -> {
                try {
                    verify(base, temporary);
                } catch (Exception error) {
                    throw new AssertionError(error);
                }
            });
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static void verify(Avatar base, Path source) throws Exception {
        var client = Minecraft.getInstance();
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var rendererField = field(Minecraft.class, "gameRenderer");
        var previousRenderer = rendererField.get(client);
        var cameraRenderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
        field(GameRenderer.class, "mainCamera").set(cameraRenderer, new Camera());
        rendererField.set(client, cameraRenderer);
        var resourcesField = field(Minecraft.class, "resourceManager");
        Object previousResources = resourcesField.get(client);
        var pack = new net.minecraft.server.packs.PathPackResources("native-material-test", source, false);
        var resources = new net.minecraft.server.packs.resources.ReloadableResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
            @Override
            public java.util.Optional<net.minecraft.server.packs.resources.Resource> getResource(
                    net.minecraft.resources.ResourceLocation id) {
                var url = FirstPersonMaterialSmokeTest.class.getClassLoader().getResource(
                        "assets/" + id.getNamespace() + "/" + id.getPath());
                return url == null ? java.util.Optional.empty() : java.util.Optional.of(
                        new net.minecraft.server.packs.resources.Resource(pack, url::openStream));
            }
        };
        resourcesField.set(client, resources);
        Avatar accessory = null;
        try {
            var limits = OutfitLimits.defaults();
            var baseResource = resource();
            var accessoryResource = resource();
            OutfitNativeRenderSmokeTest.addQuad(base);
            var indexes = new SkeletonIndex.Cache();
            var skeleton = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) base.renderer,
                    baseResource, null, indexes, limits).binding().definition();
            var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                    skeleton.reference(null, skeleton.uniqueNamed("Badge").orElseThrow()), null, null, null);
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), accessoryResource, 1, true,
                    anchor, OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            var context = new CosmeticInstanceContext(base.owner, UUID.randomUUID(), base.owner, instance,
                    limits, CosmeticInstanceContext.Scope.DRAFT);
            accessory = Avatar.createCosmetic(context, base.permissions, "first-person material accessory");
            accessory.load(LocalAvatarLoader.compileFiguraAvatar(source, UUID.randomUUID()));
            accessory.initializePreviewPlayer(null);
            require(!base.scriptError && !accessory.scriptError, "Both authored materials must initialize");
            var basePart = find(base.renderer.root, "Badge");
            var accessoryPart = find(accessory.renderer.root, "Badge");
            var baseMaterial = basePart.shaderMaterial;
            var accessoryMaterial = accessoryPart.shaderMaterial;
            baseMaterial.renderType();
            accessoryMaterial.renderType();
            var programField = field(top.aihmc.omnimorph.model.rendering.material.AvatarMaterial.class, "shader");
            var baseShader = (top.aihmc.omnimorph.model.rendering.material.NativeShader) programField.get(baseMaterial);
            var accessoryShader = (top.aihmc.omnimorph.model.rendering.material.NativeShader) programField.get(accessoryMaterial);
            var compiled = baseShader.get();
            require(org.lwjgl.opengl.GL20.glGetProgrami(compiled.getId(), org.lwjgl.opengl.GL20.GL_LINK_STATUS) != 0
                            && accessoryShader.get().getId() != compiled.getId(),
                    "Both authored native GL programs compile and link independently");
            top.aihmc.omnimorph.model.rendering.material.AvatarMaterial.invalidateShaders();
            require(!org.lwjgl.opengl.GL20.glIsProgram(compiled.getId()) && baseShader.get() != compiled,
                    "Resource reload retires native GL programs and active owners compile again");
            var pipelines = top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines.class;
            var variant = top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines.pipeline(1,
                    top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines.Blend.ALPHA,
                    3, false, true, 0.25f);
            var particleShader = pipelines.getDeclaredMethod("shader",
                    top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines.Pipeline.class);
            particleShader.setAccessible(true);
            var cutoffShader = (net.minecraft.client.renderer.ShaderInstance) particleShader.invoke(null, variant);
            require(org.lwjgl.opengl.GL20.glGetProgrami(cutoffShader.getId(), org.lwjgl.opengl.GL20.GL_LINK_STATUS) != 0,
                    "Custom particle alpha cutoff compiles with the real native fog/lightmap shader");
            top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines.invalidateShaders();
            require(!org.lwjgl.opengl.GL20.glIsProgram(cutoffShader.getId()),
                    "Particle shader variants retire on native resource reload");
            // Keep ordinary and custom-material geometry in the same discard pass.
            OutfitNativeRenderSmokeTest.addQuad(accessory);
            for (Avatar avatar : List.of(base, accessory)) {
                lua(avatar, """
                        fp_pre, fp_post = 0, 0
                        local badge = models.model.model.Cosmetic.Badge
                        badge:setPreRender(function()
                            fp_pre = fp_pre + 1
                            badge:setPos(fp_pre * 16, 0, 0)
                            badge:setMaterialTime(0.75)
                        end)
                        badge:setPostRender(function() fp_post = fp_post + 1 end)
                        """);
            }
            base.renderMode = EntityRenderMode.FIRST_PERSON_WORLD;
            base.renderer.allowMatrixUpdate = true;
            var config = new OutfitConfig(1, 0, baseResource, List.of(instance), List.of(),
                    Map.of(accessoryResource.identity(), 1), false);
            var state = new top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame(1, 0, 0, 0, 0);
            int light = 0x00f000f0;
            var draw = OutfitFirstPersonScripts.class.getDeclaredMethod("drawFigura",
                    OutfitComposition.Frame.class, Avatar.class,
                    top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame.class, int.class,
                    net.minecraft.client.player.LocalPlayer.class, float.class, Matrix4f.class,
                    float.class, net.minecraft.client.renderer.MultiBufferSource.class);
            draw.setAccessible(true);
            Matrix4f previousPose = null;
            try (var composition = new OutfitComposition(config, limits, null,
                    Map.of(instance.instanceId(), accessory), indexes)) {
                for (int iteration = 0; iteration < 2; iteration++) {
                    top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                    base.render.reset(Integer.MAX_VALUE);
                    accessory.render.reset(Integer.MAX_VALUE);
                    try (var frame = composition.retain()) {
                        frame.prepareFiguraBase(base);
                        // Production factory and exact crash chain, not a replacement test sink.
                        draw.invoke(null, frame, base, state, light, null, 0.5f, new Matrix4f(), 0f,
                                OutfitFirstPersonScripts.discardBuffers());
                    }
                    var pose = basePart.savedPartToWorldMat.toMatrix4f();
                    require(previousPose == null || !previousPose.equals(pose, 0.0001f),
                            "Full-body pose must refresh on successive first-person frames");
                    previousPose = pose;
                }
                for (Avatar avatar : List.of(base, accessory)) {
                    String role = avatar == base ? "base" : "accessory";
                    lua(avatar, "assert(fp_pre == 2 and fp_post == 2, 'Native part callbacks: '.."
                            + "fp_pre..'/'..fp_post..' avatar=" + role + "')");
                }
                require(basePart.shaderMaterial == baseMaterial && accessoryPart.shaderMaterial == accessoryMaterial,
                        "The first-person pass must retain both shader materials");
                // A normal draw still writes actual custom attributes after the discard passes.
                {
                    var format = MaterialVertexFormat.FORMAT;
                    var builder = new BufferBuilder(4096);
                    builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, format);
                    var discard = OutfitFirstPersonScripts.discardBuffers();
                    base.renderMode = EntityRenderMode.RENDER;
                    base.render.reset(Integer.MAX_VALUE);
                    base.render(null, 0, 0.5f, 1, new PoseStack(),
                            type -> type.format() == format ? builder : discard.getBuffer(type),
                            light, 0, null, PartFilterScheme.MODEL, false, false);
                    var mesh = builder.end();
                    try {
                        var vertices = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                        require(vertices.remaining() == 4 * format.getVertexSize(),
                                "The visible material quad must remain");
                        for (int index = 0; index < 4; index++) {
                            int offset = index * format.getVertexSize();
                            require(vertices.getFloat(offset + attributeOffset(format, MaterialVertexFormat.TIME)) == 0.75f,
                                    "Normal drawing must still write authored material time");
                            float uv = vertices.getFloat(offset + attributeOffset(format, MaterialVertexFormat.SECONDARY_UV));
                            require(uv >= 0.049f && uv <= 0.951f, "Normal drawing must retain the authored secondary UV");
                        }
                    } finally {
                        mesh.release();
                        org.lwjgl.system.MemoryUtil.memFree((java.nio.ByteBuffer)
                                field(BufferBuilder.class, "buffer").get(builder));
                    }
                }
                require(baseMaterial.renderType() != accessoryMaterial.renderType(),
                        "Both avatar-owned material render types remain distinct");
            }
            System.out.println("FIRST_PERSON_MATERIAL_OK chain=drawFigura+capture+renderer+writer"
                    + " callbacks=base+accessory pose=refreshed normal_attributes=retained GL_compile+reload=verified"
                    + " pixels=MANUAL/PENDING");
        } finally {
            if (accessory != null) {
                accessory.clean();
            }
            base.clean();
            rendererField.set(client, previousRenderer);
            resourcesField.set(client, previousResources);
            resources.close();
            pack.close();
        }
    }

    private static int attributeOffset(com.mojang.blaze3d.vertex.VertexFormat format,
                                       com.mojang.blaze3d.vertex.VertexFormatElement element) {
        int offset = 0;
        for (var candidate : format.getElements()) {
            if (candidate.equals(element)) {
                return offset;
            }
            offset += candidate.getByteSize();
        }
        throw new AssertionError("Missing attribute " + element);
    }

    private static OutfitConfig.ResourceRef resource() {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(),
                null, null, null, "a".repeat(64), OutfitConfig.Format.FIGURA);
    }

    private static FiguraModelPart find(FiguraModelPart part, String name) {
        if (part.name.equals(name)) {
            return part;
        }
        for (var child : part.children) {
            var found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void lua(Avatar avatar, String script) {
        avatar.render.reset(Integer.MAX_VALUE);
        require(avatar.luaRuntime.run(avatar.loadScript("first-person material contract", script), avatar.render) != null
                && !avatar.scriptError, "Lua contract failed: " + avatar.errorText);
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

}
