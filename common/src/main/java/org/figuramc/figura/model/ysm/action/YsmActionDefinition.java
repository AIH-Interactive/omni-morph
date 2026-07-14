package org.figuramc.figura.model.ysm.action;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;

@LuaWhitelist
@LuaTypeDoc(
        name = "YsmAction",
        value = "ysm_action"
)
public class YsmActionDefinition {
    private final String id;
    private String title;
    private String animation;
    private String page = "root";
    private boolean loop;

    public YsmActionDefinition(String id) {
        this.id = id;
        this.title = id;
        this.animation = id;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_id")
    public String getId() {
        return id;
    }

    @LuaWhitelist
    public String get_id() {
        return getId();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_title")
    public String getTitle() {
        return title == null ? id : title;
    }

    @LuaWhitelist
    public String get_title() {
        return getTitle();
    }

    public YsmActionDefinition setTitle(String title) {
        this.title = title;
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_animation")
    public String getAnimation() {
        return animation;
    }

    @LuaWhitelist
    public String get_animation() {
        return getAnimation();
    }

    public YsmActionDefinition setAnimation(String animation) {
        this.animation = animation;
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_page")
    public String getPage() {
        return page;
    }

    @LuaWhitelist
    public String get_page() {
        return getPage();
    }

    public YsmActionDefinition setPage(String page) {
        this.page = page == null || page.isBlank() ? "root" : page;
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.is_loop")
    public boolean isLoop() {
        return loop;
    }

    public YsmActionDefinition setLoop(boolean loop) {
        this.loop = loop;
        return this;
    }

    @Override
    public String toString() {
        return "YSM Action (" + id + ")";
    }
}
