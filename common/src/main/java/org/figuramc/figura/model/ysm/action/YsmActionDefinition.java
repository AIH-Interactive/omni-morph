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
    private String mode = "press";
    private boolean loop;
    private int cooldownTicks;
    private float speed = 1f;

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

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_title")
    public YsmActionDefinition setTitle(String title) {
        this.title = title;
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_title(String title) {
        return setTitle(title);
    }

    @LuaWhitelist
    public YsmActionDefinition title(String title) {
        return setTitle(title);
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

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_animation")
    public YsmActionDefinition setAnimation(String animation) {
        this.animation = animation;
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_animation(String animation) {
        return setAnimation(animation);
    }

    @LuaWhitelist
    public YsmActionDefinition animation(String animation) {
        return setAnimation(animation);
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

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_page")
    public YsmActionDefinition setPage(String page) {
        this.page = page == null || page.isBlank() ? "root" : page;
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_page(String page) {
        return setPage(page);
    }

    @LuaWhitelist
    public YsmActionDefinition page(String page) {
        return setPage(page);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.is_loop")
    public boolean isLoop() {
        return loop;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_loop")
    public YsmActionDefinition setLoop(boolean loop) {
        this.loop = loop;
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_loop(boolean loop) {
        return setLoop(loop);
    }

    @LuaWhitelist
    public YsmActionDefinition loop(boolean loop) {
        return setLoop(loop);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_mode")
    public String getMode() {
        return mode;
    }

    @LuaWhitelist
    public String get_mode() {
        return getMode();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_mode")
    public YsmActionDefinition setMode(String mode) {
        this.mode = mode == null || mode.isBlank() ? "press" : mode.toLowerCase(java.util.Locale.US);
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_mode(String mode) {
        return setMode(mode);
    }

    @LuaWhitelist
    public YsmActionDefinition mode(String mode) {
        return setMode(mode);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_cooldown")
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @LuaWhitelist
    public int get_cooldown() {
        return getCooldownTicks();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_cooldown")
    public YsmActionDefinition setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(0, cooldownTicks);
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_cooldown(int cooldownTicks) {
        return setCooldownTicks(cooldownTicks);
    }

    @LuaWhitelist
    public YsmActionDefinition cooldown(int cooldownTicks) {
        return setCooldownTicks(cooldownTicks);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.get_speed")
    public float getSpeed() {
        return speed;
    }

    @LuaWhitelist
    public float get_speed() {
        return getSpeed();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_action.set_speed")
    public YsmActionDefinition setSpeed(float speed) {
        this.speed = speed <= 0f ? 1f : speed;
        return this;
    }

    @LuaWhitelist
    public YsmActionDefinition set_speed(float speed) {
        return setSpeed(speed);
    }

    @LuaWhitelist
    public YsmActionDefinition speed(float speed) {
        return setSpeed(speed);
    }

    @Override
    public String toString() {
        return "YSM Action (" + id + ")";
    }
}
