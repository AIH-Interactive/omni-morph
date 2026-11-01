package top.aihmc.omnimorph.ducks;

import net.minecraft.resources.ResourceLocation;

// Mojang no longer ships the effects field in GameRenderer, instead statically calling the shaders themselves
public interface GameEffects {

    static ResourceLocation[] getEffects() {
        return EFFECTS;
    }

    ResourceLocation[] EFFECTS = new ResourceLocation[]{new ResourceLocation("shaders/post/blur.json"), new ResourceLocation("shaders/post/entity_outline.json"), new ResourceLocation("shaders/post/invert.json"), new ResourceLocation("shaders/post/blur.json"), new ResourceLocation("shaders/post/creeper.json"), new ResourceLocation("shaders/post/spider.json")};
}
