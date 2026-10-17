package top.aihmc.omnimorph.lua.api;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.lua.LuaNotNil;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaMethodDoc;
import top.aihmc.omnimorph.lua.docs.LuaMethodOverload;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import top.aihmc.omnimorph.math.vector.FiguraVec4;
import top.aihmc.omnimorph.mixin.render.TextureAtlasAccessor;
import top.aihmc.omnimorph.utils.LuaUtils;

import java.util.ArrayList;
import java.util.List;

@LuaWhitelist
@LuaTypeDoc(
        name = "TextureAtlas",
        value = "texture_atlas"
)
public class TextureAtlasAPI {

    private final TextureAtlas atlas;

    public TextureAtlasAPI(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    @LuaWhitelist
    @LuaMethodDoc("texture_atlas.list_sprites")
    public List<String> listSprites() {
        List<String> list = new ArrayList<>();
        for (Identifier res : ((TextureAtlasAccessor) atlas).figura$getTexturesByName().keySet())
            list.add(res.toString());
        return list;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "path"
            ),
            value = "texture_atlas.get_sprite_uv"
    )
    public FiguraVec4 getSpriteUV(@LuaNotNil String sprite) {
        Identifier spriteLocation = LuaUtils.parsePath(sprite);
        TextureAtlasSprite s = atlas.getSprite(spriteLocation);
        return FiguraVec4.of(s.getU0(), s.getV0(), s.getU1(), s.getV1());
    }

    @LuaWhitelist
    @LuaMethodDoc("texture_atlas.get_width")
    public int getWidth() {
        return ((TextureAtlasAccessor) atlas).figura$getWidth();
    }

    @LuaWhitelist
    @LuaMethodDoc("texture_atlas.get_height")
    public int getHeight() {
        return ((TextureAtlasAccessor) atlas).figura$getHeight();
    }

    @Override
    public String toString() {
        return "TextureAtlas (" + atlas.location() + ")";
    }
}
