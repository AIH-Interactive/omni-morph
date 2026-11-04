package top.aihmc.omnimorph.mixin.render.nodeRenderer;

import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.apache.commons.lang3.function.TriFunction;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(SubmitNodeStorage.class)
public abstract class SubmitNodeStorageMixin implements NodeCollectorExtension {

    @Shadow
    public abstract SubmitNodeCollection order(int i);

    @Override
    public <S extends EntityRenderState> void submitFiguraModel(Avatar avatar, S renderState, TriFunction<Avatar, S, FiguraVertexSource, Void> renderer) {
        NodeCollectorExtension extension = (NodeCollectorExtension) this.order(0);
        extension.submitFiguraModel(avatar, renderState, renderer);
    }

}
