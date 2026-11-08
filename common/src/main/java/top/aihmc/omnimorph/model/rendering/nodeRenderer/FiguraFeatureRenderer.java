package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import org.figuramc.figura.utils.ui.UIHelper;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;

import java.util.List;

/** Figura and YSM geometry shares the native preparation, upload and material execution lifecycle. */
public final class FiguraFeatureRenderer extends RenderTypeFeatureRenderer<FiguraSubmission> {
    public static final FeatureRendererType<FiguraSubmission> TYPE = FeatureRendererType.create("OmniMorph");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<FiguraSubmission> submissions) {
        FiguraVertexSource vertices = this::getVertexBuilder;
        for (FiguraSubmission submission : submissions) {
            if (submission.avatar() == null) {
                continue;
            }
            var avatar = submission.avatar();
            var previousMode = avatar.renderMode;
            boolean previousPaperdoll = UIHelper.paperdoll;
            avatar.renderMode = submission.renderMode();
            UIHelper.paperdoll = submission.paperdoll();
            try {
                submission.renderer().apply(avatar, submission.renderState(), vertices);
            } finally {
                avatar.renderMode = previousMode;
                UIHelper.paperdoll = previousPaperdoll;
            }
        }
    }
}
