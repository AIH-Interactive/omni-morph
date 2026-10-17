package top.aihmc.omnimorph.lua.api.nameplate;

import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.avatar.Badges;
import top.aihmc.omnimorph.font.Emojis;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaMethodDoc;
import top.aihmc.omnimorph.lua.docs.LuaMethodOverload;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import top.aihmc.omnimorph.utils.TextUtils;
import org.luaj.vm2.LuaError;

@LuaWhitelist
@LuaTypeDoc(
        name = "NameplateCustomization",
        value = "nameplate_customization"
)
public class NameplateCustomization {

    private Component json;
    private String text;

    private Component parseJsonText(String text) {
        Component component = TextUtils.tryParseJson(text);
        component = Badges.noBadges4U(component);
        component = TextUtils.removeClickableObjects(component);
        component = Emojis.applyEmojis(component);
        component = Emojis.removeBlacklistedEmojis(component);
        return component;
    }

    public Component getJson() {
        return json;
    }

    @LuaWhitelist
    @LuaMethodDoc("nameplate_customization.get_text")
    public String getText() {
        return this.text;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "text"
            ),
            value = "nameplate_customization.set_text"
    )
    public NameplateCustomization setText(String text) {
        this.text = text;
        if (text != null) {
            Component component = parseJsonText(text);
            if (component.getString().length() > 64)
                throw new LuaError("Text length exceeded limit of 64 characters");
            json = component;
        } else {
            json = null;
        }
        return this;
    }

    @Override
    public String toString() {
        return "NameplateCustomization";
    }
}
