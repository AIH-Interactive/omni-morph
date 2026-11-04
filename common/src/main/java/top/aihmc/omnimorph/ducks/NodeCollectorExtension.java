package top.aihmc.omnimorph.ducks;

import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.apache.commons.lang3.function.TriFunction;
import org.figuramc.figura.avatar.Avatar;


@FunctionalInterface
interface QuadFunction<A, B, C, D, R> {
    R apply(A a, B b, C c, D d);
}

public interface NodeCollectorExtension {
    <S extends EntityRenderState> void submitFiguraModel(Avatar avatar, S renderState, TriFunction<Avatar, S, FiguraVertexSource, Void> renderer);
}

