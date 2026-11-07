package top.aihmc.omm.preview;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.figuramc.figura.lua.api.action_wheel.Action;
import org.figuramc.figura.lua.api.action_wheel.Page;
import top.aihmc.omnimorph.avatar.AvatarTranslations;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.model.ysm.action.YsmActionDefinition;
import top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime;
import top.aihmc.omnimorph.model.ysm.action.YsmActionSchemaParser;

/** Model-local standalone host for OMP's real YSM action runtime and action/control schemas. */
public final class PreviewYsmRuntime {
    record ActionState(String animation, String mode, boolean active, float time) {}
    private final PreviewLua owner;
    private final PreviewActionPlayer player;
    private final YsmActionRuntime actions;
    private final AvatarTranslations translations;
    private int tick;

    PreviewYsmRuntime(PreviewLua owner, PreviewActionPlayer.Definition definition, PreviewMesh mesh, String language) {
        this.owner = owner;
        player = new PreviewActionPlayer(definition, owner.molang, mesh.skeleton);
        actions = new YsmActionRuntime(this);
        translations = new AvatarTranslations(language);
        var languages = new LinkedHashMap<String, byte[]>();
        mesh.ysmResources.languages().forEach(
            (name, json) -> languages.put(name, json.getBytes(StandardCharsets.UTF_8)));
        translations.load(languages);
        defaults();
        actions.buildDefaultsFromAnimations();
        for (var schema : mesh.ysmResources.schemas()) {
            YsmActionSchemaParser.apply(this, schema.path(), schema.json());
        }
    }

    public PreviewLua owner() {
        return owner;
    }
    public PreviewActionPlayer animations() {
        return player;
    }
    public YsmActionRuntime actions() {
        return actions;
    }
    public AvatarTranslations translations() {
        return translations;
    }
    public int tick() {
        return tick;
    }

    Map<String, ActionState> actionStates() {
        Map<String, ActionState> values = new LinkedHashMap<>();
        for (var action : actions.all()) {
            values.put(action.getId(),
                new ActionState(action.getAnimation(), action.getMode(), actions.isActive(action.getId()),
                    actions.time(action.getId())));
        }
        return values;
    }

    void clock(float time) {
        tick = (int) Math.floor(time * 20.0);
    }

    float widthScale() {
        return ((Number) owner.controls.getValue("ysm.width_scale")).floatValue();
    }

    float heightScale() {
        return ((Number) owner.controls.getValue("ysm.height_scale")).floatValue();
    }

    private void defaults() {
        owner.controls.register(new AvatarControlDefinition("ysm.header", AvatarControlType.LABEL).setTitle("YSM"));
        owner.controls.register(new AvatarControlDefinition("ysm.action_loop", AvatarControlType.TOGGLE)
                .setTitle("Loop actions")
                .setDefault(false));
        owner.controls.register(new AvatarControlDefinition("ysm.action_speed", AvatarControlType.SLIDER)
                .setTitle("Action speed")
                .setRange(0.1, 3, 0.1)
                .setDefault(1d));
        owner.controls.register(new AvatarControlDefinition("ysm.width_scale", AvatarControlType.SLIDER)
                .setTitle("Width scale")
                .setRange(0.1, 5, 0.05)
                .setDefault(1d));
        owner.controls.register(new AvatarControlDefinition("ysm.height_scale", AvatarControlType.SLIDER)
                .setTitle("Height scale")
                .setRange(0.1, 5, 0.05)
                .setDefault(1d));
    }

    void installWheel() {
        if (owner.actionWheel.getCurrentPage() != null) {
            return;
        }
        Page root = owner.actionWheel.newPage("YSM");
        root.newAction(1).setTitle("Settings").setItem("minecraft:comparator").setControlsPage("root");
        Map<String, Page> pages = new LinkedHashMap<>();
        Map<String, Integer> slots = new LinkedHashMap<>();
        Map<String, Set<Integer>> occupied = new LinkedHashMap<>();
        pages.put("root", root);
        slots.put("root", 2);
        occupied.computeIfAbsent("root", ignored -> new LinkedHashSet<>()).add(1);
        for (YsmActionDefinition action : actions.all()) {
            if (!isExtraAnimation(action)) {
                continue;
            }
            String pageId = action.getPage();
            if (pageId == null || pageId.isBlank() || "root".equals(normalize(pageId))) {
                pageId = "extra_animation";
            }
            Page page = pageFor(root, pages, slots, occupied, pageId);
            Action wheelAction = page.newAction(nextSlot(slots, occupied, pageId)).setTitleSupplier(action::getTitle);
            String animation = action.getAnimation();
            if (animation != null && animation.startsWith("#")) {
                wheelAction.setItem(action.getItem() == null ? "minecraft:comparator" : action.getItem())
                    .setControlsPage(animation.substring(1));
            } else {
                wheelAction.setItem(action.getItem() == null ? "minecraft:armor_stand" : action.getItem())
                    .setYsmAction(action.getId());
            }
        }
        owner.actionWheel.setPage(root);
    }

    private static Page pageFor(
        Page root, Map<String, Page> pages, Map<String, Integer> slots, Map<String, Set<Integer>> occupied, String id) {
        Page existing = pages.get(id);
        if (existing != null) {
            return existing;
        }
        Page page = new Page("YSM: " + id);
        pages.put(id, page);
        slots.put(id, 2);
        occupied.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(1);
        root.setAction(nextSlot(slots, occupied, "root"), new OpenPageAction(page, id));
        page.setAction(1, new OpenPageAction(root, "Back"));
        return page;
    }

    private static int nextSlot(Map<String, Integer> slots, Map<String, Set<Integer>> occupied, String page) {
        Set<Integer> used = occupied.computeIfAbsent(page, ignored -> new LinkedHashSet<>());
        int slot = slots.getOrDefault(page, 1);
        while (used.contains(slot)) {
            slot++;
        }
        used.add(slot);
        slots.put(page, slot + 1);
        return slot;
    }

    private static boolean isExtraAnimation(YsmActionDefinition action) {
        return normalize(action.getId()).contains("extra_animation")
            || normalize(action.getPage()).contains("extra_animation")
            || normalize(action.getAnimation()).contains("extra_animation");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static final class OpenPageAction extends Action {
        private final Page page;

        OpenPageAction(Page page, String title) {
            this.page = page;
            setTitle(title);
            setItem("minecraft:book");
        }

        @Override
        public void execute(PreviewLua owner, boolean left) {
            if (left) {
                owner.actionWheel.setPage(page);
            }
        }
    }
}
