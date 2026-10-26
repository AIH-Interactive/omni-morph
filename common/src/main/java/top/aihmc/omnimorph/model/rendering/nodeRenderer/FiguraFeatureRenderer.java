package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.List;
import java.util.Map;

public class FiguraFeatureRenderer {
    public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
        List<FiguraSubmission> figuraSubmissions = new java.util.ArrayList<>(((NodeCollectorExtension) submitNodeCollection).getFiguraSubmissions());

        for (FiguraSubmission figuraSubmission : figuraSubmissions) {
            if (figuraSubmission.avatar() == null)
                continue;

            var avatar = figuraSubmission.avatar();
            var previousMode = avatar.renderMode;
            boolean previousPaperdoll = UIHelper.paperdoll;
            // World and wardrobe submissions can share an Avatar but execute after both
            // views have been submitted. Use the context captured by this command.
            avatar.renderMode = figuraSubmission.renderMode();
            UIHelper.paperdoll = figuraSubmission.paperdoll();
            try {
                figuraSubmission.renderer().apply(avatar, figuraSubmission.renderState(), bufferSource);
            } finally {
                avatar.renderMode = previousMode;
                UIHelper.paperdoll = previousPaperdoll;
            }
        }

        if (UIHelper.paperdoll) {
            bufferSource.endBatch();
        }
    }
}
