package top.aihmc.omnimorph.ducks;

import com.mojang.blaze3d.vertex.PoseStack;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

// this is used to set callbacks for pre and post rendering for Models, Model Parts, and other things
public interface FiguraSubmitCallBackExtension {
    void figura$addPreRenderingCallback(BiFunction<FiguraVertexSource, PoseStack, Boolean> callback);
    default List<BiFunction<FiguraVertexSource, PoseStack, Boolean>> figura$getPreRenderingCallbacks() {
        return List.of();
    }
    void figura$addPostRenderingCallback(Runnable callback);
    default List<Runnable> figura$getPostRenderingCallbacks() {
        return List.of();
    }

    default boolean figura$getPreventAnimSetup() {
        return false;
    }

    default void figura$setPreventAnimSetup(boolean prevent) {

    }
}
