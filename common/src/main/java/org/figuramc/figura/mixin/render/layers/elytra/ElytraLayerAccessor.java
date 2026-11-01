package org.figuramc.figura.mixin.render.layers.elytra;

import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ElytraLayer.class)
public interface ElytraLayerAccessor {
    @Accessor("WINGS_LOCATION")
    static ResourceLocation getWingsLocation() {
        throw new AssertionError();
    }
}
