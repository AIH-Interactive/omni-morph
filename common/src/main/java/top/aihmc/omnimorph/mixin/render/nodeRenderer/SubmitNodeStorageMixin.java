package top.aihmc.omnimorph.mixin.render.nodeRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.apache.commons.lang3.function.TriFunction;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FiguraSubmission;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(SubmitNodeStorage.class)
public abstract class SubmitNodeStorageMixin implements NodeCollectorExtension {

    @Shadow
    public abstract SubmitNodeCollection order(int i);

    @Override
    public <S extends EntityRenderState> void submitFiguraModel(Avatar avatar, S renderState, TriFunction<Avatar, S, MultiBufferSource, Void> renderer) {
        NodeCollectorExtension extension = (NodeCollectorExtension) this.order(0);
        extension.submitFiguraModel(avatar, renderState, renderer);
    }

    // dummy
    @Override
    public List<FiguraSubmission> getFiguraSubmissions() {
        return List.of();
    }
}
