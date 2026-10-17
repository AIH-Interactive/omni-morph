package top.aihmc.omnimorph.compat;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import top.aihmc.omnimorph.model.rendering.texture.FiguraRenderTypes;
import top.aihmc.omnimorph.model.ysm.YsmMaterialTexture;

public class IrisCompat {
    public static void assignPipelinesToIrisPrograms() {
        IrisApi.getInstance().assignPipeline(FiguraRenderTypes.FiguraRenderPipelines.FIGURA_SOLID, IrisProgram.ENTITIES);
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
