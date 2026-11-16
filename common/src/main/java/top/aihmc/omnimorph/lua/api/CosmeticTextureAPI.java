package top.aihmc.omnimorph.lua.api;

import java.util.List;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.api.TextureAPI;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

/** Instance-owned textures without access to Minecraft's resource textures. */
@LuaWhitelist
@LuaTypeDoc(name = "CosmeticTextureAPI", value = "cosmetic_textures")
public final class CosmeticTextureAPI {
    private final TextureAPI textures;

    public CosmeticTextureAPI(Avatar owner) {
        textures = new TextureAPI(owner);
    }

    @LuaWhitelist
    public List<FiguraTexture> getTextures() {
        return textures.getTextures();
    }

    @LuaWhitelist
    public FiguraTexture newTexture(@LuaNotNil String name, int width, int height) {
        return textures.newTexture(name, width, height);
    }
}
