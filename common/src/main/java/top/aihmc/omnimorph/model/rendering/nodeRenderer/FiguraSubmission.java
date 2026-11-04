package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.apache.commons.lang3.function.TriFunction;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.utils.ui.UIHelper;

public record FiguraSubmission(Avatar avatar, EntityRenderState renderState,
                               TriFunction<Avatar, EntityRenderState, FiguraVertexSource, Void> renderer,
                               EntityRenderMode renderMode, boolean paperdoll) implements SubmitNode {

    @Override
    public FeatureRendererType<FiguraSubmission> featureType() {
        return FiguraFeatureRenderer.TYPE;
    }

    public FiguraSubmission(Avatar avatar, EntityRenderState renderState,
                            TriFunction<Avatar, EntityRenderState, FiguraVertexSource, Void> renderer) {
        this(avatar, renderState, renderer, avatar == null ? EntityRenderMode.OTHER : avatar.renderMode,
                UIHelper.paperdoll);
    }
}
