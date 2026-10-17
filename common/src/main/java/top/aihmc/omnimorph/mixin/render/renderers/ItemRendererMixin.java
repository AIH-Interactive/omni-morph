package top.aihmc.omnimorph.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.ducks.FiguraItemRendererExtension;
import top.aihmc.omnimorph.ducks.FiguraItemStackRenderStateExtension;
import top.aihmc.omnimorph.lua.api.world.ItemStackAPI;
import top.aihmc.omnimorph.lua.api.world.WorldAPI;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemRendererMixin implements FiguraItemRendererExtension {
    @Unique
    private final ItemStackRenderState figura$ScratchRenderState = new ItemStackRenderState();

    @Unique
    public int figura$getModelComplexity(ItemStack stack, RandomSource randomSource) {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(figura$ScratchRenderState, stack, ItemDisplayContext.NONE, WorldAPI.getCurrentWorld(), null, 1);
        if (((FiguraItemStackRenderStateExtension)(this.figura$ScratchRenderState)).figura$getQuads() != null && !((FiguraItemStackRenderStateExtension) (this.figura$ScratchRenderState)).figura$getQuads().isEmpty())
            return ((FiguraItemStackRenderStateExtension)(this.figura$ScratchRenderState)).figura$getQuads().size();
        return 20;
    }
}
