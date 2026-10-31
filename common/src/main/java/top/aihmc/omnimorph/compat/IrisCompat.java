package top.aihmc.omnimorph.compat;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import top.aihmc.omnimorph.model.ysm.YsmMaterialTexture;

public class IrisCompat {
    public static boolean isShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    /**
     * Registers YSM normal and specular maps with Iris' PBR loader.
     * <p>
     * MC 1.21.1 render types use vanilla shader programs, which Iris patches on its own,
     * so no per-pipeline program assignment is needed here.
     */
    public static void registerMaterialTextures() {
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
