package top.aihmc.omnimorph.mixin.render.feature;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.DeferredMaterialDrawExtension;

@Mixin(PreparedRenderType.class)
public abstract class PreparedRenderTypeMixin implements DeferredMaterialDrawExtension {
    @Unique private Runnable figura$deferredDraw;
    @Unique private boolean figura$hasDeferredDraw;

    @Override
    public void figura$setDeferredDraw(Runnable draw) {
        figura$deferredDraw = draw;
        figura$hasDeferredDraw = true;
    }

    @Inject(method = "drawFromBuffer(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;)V",
            at = @At("HEAD"), cancellable = true)
    private void figura$drawDeferred(StagedVertexBuffer.ExecuteInfo info, CallbackInfo ci) {
        if (figura$hasDeferredDraw) {
            Runnable draw = figura$deferredDraw;
            figura$deferredDraw = null;
            // A repeated native execution must neither replay geometry nor expose the placeholder.
            ci.cancel();
            if (draw != null) {
                draw.run();
            }
        }
    }
}
