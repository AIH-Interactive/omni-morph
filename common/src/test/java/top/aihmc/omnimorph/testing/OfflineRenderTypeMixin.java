package top.aihmc.omnimorph.testing;

import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import java.util.List;

/** Records platform state only. Production batching, ordering and deferred execution remain unchanged. */
@Mixin(RenderType.class)
public abstract class OfflineRenderTypeMixin {
    @Shadow public abstract RenderPipeline pipeline();

    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private void recordPlatformState(CallbackInfoReturnable<PreparedRenderType> callback) {
        callback.setReturnValue(new PreparedRenderType("offline", pipeline(), null, null, null, List.of()));
    }
}
