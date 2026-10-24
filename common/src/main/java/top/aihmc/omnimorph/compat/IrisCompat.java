package top.aihmc.omnimorph.compat;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import top.aihmc.omnimorph.lua.api.particle.CustomParticlePipelines;
import top.aihmc.omnimorph.model.rendering.texture.FiguraRenderTypes;
import top.aihmc.omnimorph.model.ysm.YsmMaterialTexture;

public class IrisCompat {
    public static void assignPipelinesToIrisPrograms() {
        IrisApi.getInstance().assignPipeline(FiguraRenderTypes.FiguraRenderPipelines.FIGURA_SOLID, IrisProgram.ENTITIES);
        // Custom particle layers use dedicated pipelines; without an Iris program they fall back to the
        // unpatched shader and disappear or ignore the pack's lighting/tonemapping.
        for (var entry : CustomParticlePipelines.registered().entrySet()) {
            IrisApi.getInstance().assignPipeline(entry.getKey(),
                    entry.getValue() == CustomParticlePipelines.Blend.OPAQUE
                            ? IrisProgram.PARTICLES : IrisProgram.PARTICLES_TRANSLUCENT);
        }
        PBRTextureLoaderRegistry.INSTANCE.register(YsmMaterialTexture.class, (texture, resources, consumer) -> {
            var normal = texture.normalTexture();
            if (normal != null) {
                normal.uploadIfDirty(false, false);
                consumer.acceptNormalTexture(normal);
            }
            var specular = texture.specularTexture();
            if (specular != null) {
                specular.uploadIfDirty(false, false);
                consumer.acceptSpecularTexture(specular);
            }
        });
    }
}
