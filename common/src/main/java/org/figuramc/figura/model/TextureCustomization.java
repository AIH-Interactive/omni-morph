package org.figuramc.figura.model;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.mixin.render.MissingTextureAtlasSpriteAccessor;
import org.figuramc.figura.model.rendering.texture.AtlasTextureReadback;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import org.luaj.vm2.LuaError;

import java.util.Optional;

public class TextureCustomization {

    private final FiguraTextureSet.OverrideType first;
    private final Object second;

    public TextureCustomization(FiguraTextureSet.OverrideType first, Object second) {
        this.first = first;
        this.second = second;
    }

    public FiguraTextureSet.OverrideType getOverrideType() {
        return first;
    }

    public Object getValue() {
        return second;
    }

    public FiguraTexture getTexture(Avatar avatar, FiguraTextureSet textureSet) {
        if (avatar.render == null) return null;

        Identifier resourceLocation = textureSet.getOverrideTexture(avatar.owner, this);
        String name = resourceLocation.toString();
        if (avatar.renderer.customTextures.containsKey(name)) {
            return avatar.renderer.customTextures.get(name);
        }

        // is there a way to check if an atlas exists without getAtlas? cause that is the only thing that will cause an error, and try catch blocks can be pricy
        try {
            // will throw an error if the resource location is not a texture atlas, ignored anyway
            AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(resourceLocation);
            if (!(atlas instanceof TextureAtlas))
                throw new LuaError("Resource is not a texture atlas: " + resourceLocation);

            return AtlasTextureReadback.read(avatar, name, (TextureAtlas) atlas);
        } catch (Exception ignored) {}
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
            // if the string is a valid resourceLocation but does not point to a valid resource, missingno
            NativeImage image = resource.isPresent() ? NativeImage.read(resource.get().open()) : MissingTextureAtlasSpriteAccessor.generateImage(16, 16);
            return avatar.registerTexture(name, image, false);
        } catch (Exception e) {
            // spit an error if the player inputs a resource location that does point to a thing, but not to an image
            throw new LuaError(e.getMessage());
        }
    }
}
