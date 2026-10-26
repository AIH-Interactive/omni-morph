package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.apache.commons.lang3.function.TriFunction;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.utils.ui.UIHelper;

public record FiguraSubmission(Avatar avatar, EntityRenderState renderState,
                               TriFunction<Avatar, EntityRenderState, MultiBufferSource, Void> renderer,
                               EntityRenderMode renderMode, boolean paperdoll) {

    public FiguraSubmission(Avatar avatar, EntityRenderState renderState,
                            TriFunction<Avatar, EntityRenderState, MultiBufferSource, Void> renderer) {
        this(avatar, renderState, renderer, avatar == null ? EntityRenderMode.OTHER : avatar.renderMode,
                UIHelper.paperdoll);
    }
}
