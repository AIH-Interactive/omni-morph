package top.aihmc.omnimorph.ducks;

/** A prepared native material may replace its placeholder draw with one deferred acceleration boundary. */
public interface DeferredMaterialDrawExtension {
    void figura$setDeferredDraw(java.util.function.BiConsumer<
            com.mojang.renderpearl.api.commands.RenderPass,
            com.mojang.renderpearl.api.pipeline.RenderPipeline> draw);
}
