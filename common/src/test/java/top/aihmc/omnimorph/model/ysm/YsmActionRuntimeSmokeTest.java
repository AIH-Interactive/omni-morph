package top.aihmc.omnimorph.model.ysm;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Items;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlRuntime;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import org.figuramc.figura.gui.ActionWheel;
import top.aihmc.omnimorph.gui.screens.AvatarControlsScreen;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.lua.api.action_wheel.Action;
import org.figuramc.figura.lua.api.action_wheel.ActionWheelAPI;
import org.figuramc.figura.lua.api.keybind.KeybindAPI;
import top.aihmc.omnimorph.lua.api.ysm_actions.YsmActionsAPI;
import org.figuramc.figura.lua.api.world.WorldAPI;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime;
import top.aihmc.omnimorph.model.ysm.action.YsmActionSchemaParser;
import top.aihmc.omnimorph.model.ysm.action.YsmActionWheelLayoutStore;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import org.luaj.vm2.LuaValue;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Production action and callback paths on CPU; this is not a native input or GUI rendering test. */
public final class YsmActionRuntimeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("figura-actions-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        int previousTick = FiguraMod.ticks;
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            testModes();
            testCallbacks();
            testLayout();
            testOutfitWheel();
            System.out.println("M5 action runtime: hold/release, toggle/cooldown/speed, callback ownership and disk-restored wheel layout PASS; native input/GUI pending.");
        } finally {
            ActionWheel.releaseYsmPress();
            clearLayout();
            FiguraMod.ticks = previousTick;
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected action fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void testModes() throws Exception {
        Fixture first = new Fixture("action-a");
        Fixture second = new Fixture("action-b");
        FiguraMod.ticks = 100;
        var input = new YsmActionRuntime.Press();
        require(input.press(first.runtime.actions(), "extra_animation.hold"), "hold press accepted");
        var held = first.runtime.animations().getActiveAnimation("held");
        require(held != null && held.loop && held.speed == 3f, "hold loops with model speed2 times user multiplier1.5");
        require(input.press(first.runtime.actions(), "extra_animation.hold"), "duplicate held input does not restart or hit cooldown");
        require(input.release(), "hold releases its animation even during cooldown");
        require(!first.runtime.actions().isActive("extra_animation.hold") && !input.release(), "hold release is scoped and idempotent");
        require(!first.runtime.actions().trigger("extra_animation.hold"), "cooldown prevents immediate retrigger");
        require(second.runtime.actions().trigger("extra_animation.hold"), "same action in another model has independent cooldown");
        FiguraMod.ticks = 110;
        require(input.press(first.runtime.actions(), "extra_animation.hold"), "hold becomes available at exact cooldown tick");
        first.runtime.actions().stop("extra_animation.hold");
        FiguraMod.ticks = 120;
        require(first.runtime.actions().trigger("extra_animation.hold"), "same clip starts a replacement playback");
        require(!input.release() && first.runtime.actions().isActive("extra_animation.hold"),
                "stale release cannot stop a replacement animation instance");
        FiguraMod.ticks = 140;
        require(input.press(first.runtime.actions(), "extra_animation.hold"), "hold input captures current playback");
        var reused = first.runtime.animations().play("held", true, 1f);
        require(reused == first.runtime.animations().getActiveAnimation("held"), "direct replay uses the existing animation object");
        require(!input.release() && first.runtime.actions().isActive("extra_animation.hold"),
                "stale input cannot stop a new playback generation on a reused object");

        require(first.runtime.actions().trigger("extra_animation.toggle"), "toggle starts");
        require(!first.runtime.actions().isActive("extra_animation.hold"), "new action stops the prior action");
        require(first.runtime.animations().getActiveAnimation("toggle").loop, "toggle is looping");
        require(first.runtime.actions().trigger("extra_animation.toggle"), "toggle can stop during its cooldown");
        require(!first.runtime.actions().isActive("extra_animation.toggle"), "toggle stopped");
        require(!first.runtime.actions().trigger("extra_animation.toggle"), "toggle restart remains subject to cooldown");
        FiguraMod.ticks = 150;
        require(input.press(first.runtime.actions(), "extra_animation.toggle"), "toggle available after cooldown");
        require(!input.release() && first.runtime.actions().isActive("extra_animation.toggle"), "input release does not stop a toggle");
        require(input.press(first.runtime.actions(), "extra_animation.press"), "one-shot press accepted");
        require(!first.runtime.animations().getActiveAnimation("press").loop, "press uses one-shot playback by default");
        require(!input.release() && first.runtime.actions().isActive("extra_animation.press"), "input release does not stop a one-shot");
        first.owner.controls.setValue(first.owner, "ysm.action_loop", true);
        require(first.runtime.actions().trigger("extra_animation.press"), "explicit user loop accepted");
        require(first.runtime.animations().getActiveAnimation("press").loop, "user loop control is consumed");
        first.owner.controls.get("ysm.action_speed").setValue(Double.NaN);
        require(first.runtime.actions().trigger("extra_animation.press"), "invalid multiplier cannot poison playback");
        require(first.runtime.animations().getActiveAnimation("press").speed == 1f, "invalid speed uses a stable default");
        first.runtime.actions().close();
        require(!first.runtime.animations().isActive("press") && !first.runtime.actions().trigger("extra_animation.press"),
                "closed model stops actions and rejects retained input requests");
        second.runtime.actions().close();
    }

    private static void testOutfitWheel() throws Exception {
        Fixture base = new Fixture("wheel-base");
        Fixture first = new Fixture("wheel-copy-one");
        Fixture second = new Fixture("wheel-copy-two");
        var account = java.util.UUID.randomUUID();
        var outfit = java.util.UUID.randomUUID();
        var resource = new top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef(
                top.aihmc.omnimorph.model.outfit.OutfitConfig.Source.LOCAL, java.util.UUID.randomUUID(), null,
                null, null, "8".repeat(64), top.aihmc.omnimorph.model.outfit.OutfitConfig.Format.YSM_PLAIN);
        var entries = new java.util.ArrayList<top.aihmc.omnimorph.model.outfit.OutfitActionWheel.Entry>();
        for (int index = 0; index < 2; index++) {
            Fixture fixture = index == 0 ? first : second;
            var declaration = new top.aihmc.omnimorph.model.outfit.OutfitConfig.Instance(java.util.UUID.randomUUID(),
                    resource, index + 1, true, top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorRef.authorDefault("head_anchor"),
                    top.aihmc.omnimorph.model.outfit.OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            install(fixture.owner, "cosmeticContext", new top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext(
                    account, outfit, account, declaration, top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults(),
                    top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.DRAFT));
            install(fixture.owner, "loaded", true);
            var api = new top.aihmc.omnimorph.lua.api.CosmeticAnimationsAPI(fixture.owner);
            var page = fixture.owner.luaRuntime.action_wheel.newPage("native animation page");
            page.newAction(1).setTitle("Hold").setYsmAction("extra_animation.hold");
            for (int slot = 2; slot <= 8; slot++) {
                page.newAction(slot).setTitle("Author " + slot);
            }
            fixture.owner.luaRuntime.action_wheel.setPage(page);
            api.setPage(page);
            entries.add(new top.aihmc.omnimorph.model.outfit.OutfitActionWheel.Entry(declaration, "星翼", fixture.owner));
        }
        var authored = base.owner.luaRuntime.action_wheel.newPage("full base wheel");
        for (int slot = 1; slot <= 8; slot++) {
            authored.newAction(slot).setTitle("Base " + slot);
        }
        base.owner.luaRuntime.action_wheel.setPage(authored);
        Object original = authored.getActions(null);
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var wheel = new top.aihmc.omnimorph.model.outfit.OutfitActionWheel(() -> List.copyOf(entries), active::get)) {
            var root = wheel.page(base.owner);
            require(root.getGroupCount() == 2 && root.slots()[0] == authored.getAction(1)
                            && authored.getActions(null).equals(original),
                    "a full base wheel keeps all original slots and appends navigation in another group");
            root.setSlotsShift(2);
            wheel.execute(base.owner, 0, true);
            wheel.execute(base.owner, 1, true);
            var copies = wheel.page(base.owner);
            require(copies.slots()[1].getTitle().equals("星翼 #1") && copies.slots()[2].getTitle().equals("星翼 #2"),
                    "resource grouping retains both ordered duplicate entries");
            wheel.execute(base.owner, 1, true);
            FiguraMod.ticks = 500;
            wheel.execute(base.owner, 0, true);
            require(first.runtime.actions().isActive("extra_animation.hold")
                            && !second.runtime.actions().isActive("extra_animation.hold") && !first.owner.isHost,
                    "aggregated native actions execute on their real non-host cosmetic owner");
            wheel.release();
            require(!first.runtime.actions().isActive("extra_animation.hold"), "release stops only this press's first-copy hold");
            var leaf = wheel.page(base.owner);
            leaf.setSlotsShift(2);
            wheel.execute(base.owner, 0, true);
            wheel.execute(base.owner, 2, true);
            FiguraMod.ticks = 520;
            wheel.execute(base.owner, 0, true);
            require(second.runtime.actions().isActive("extra_animation.hold")
                            && !first.runtime.actions().isActive("extra_animation.hold"),
                    "the second copy has a separate action runtime and independent cooldown");
            second.runtime.actions().stop("extra_animation.hold");
            FiguraMod.ticks = 540;
            second.runtime.actions().trigger("extra_animation.hold");
            wheel.release();
            require(second.runtime.actions().isActive("extra_animation.hold"),
                    "releasing an old input cannot stop a replacement playback generation");
            second.runtime.actions().stopAll();
            leaf = wheel.page(base.owner);
            leaf.setSlotsShift(2);
            wheel.execute(base.owner, 0, true);
            var oldEntry = wheel.page(base.owner).slots()[2];
            entries.removeLast();
            wheel.page(base.owner);
            oldEntry.execute(null, true);
            require(!second.runtime.actions().isActive("extra_animation.hold") && !wheel.page(base.owner).getTitle().equals("native animation page"),
                    "removed-copy navigation is rejected instead of targeting the remaining copy");
            var changedBase = base.owner.luaRuntime.action_wheel.newPage("script-changed base");
            changedBase.newAction(1).setTitle("New author action");
            base.owner.luaRuntime.action_wheel.setPage(changedBase);
            require(wheel.page(base.owner).slots()[0] == changedBase.getAction(1),
                    "a base script page change is immediately reflected without overwriting author pages");
            var vanilla = wheel.page(null);
            require(vanilla.slots()[0] != null, "vanilla/no base Avatar still presents the host animation entry");
            wheel.execute(null, 0, true);
            wheel.execute(null, 1, true);
            FiguraMod.ticks = 560;
            wheel.execute(null, 0, true);
            require(first.runtime.actions().isActive("extra_animation.hold"), "single-copy resources enter their own page directly");
            active.set(false);
            require(wheel.page(null) == null && !first.runtime.actions().isActive("extra_animation.hold"),
                    "retired/unauthorized presentation releases owned holds and exposes no stale page");
        } finally {
            first.runtime.actions().close();
            second.runtime.actions().close();
            base.runtime.actions().close();
        }
        require(authored.getActions(null).equals(original), "aggregation never mutates original base actions");
        System.out.println("PASS outfit wheel/native YSM: full base slots, no-base navigation, duplicate grouping, real owner/cooldown, "
                + "hold generations, script page changes and removed/retired cleanup; HUD/input/GPU MANUAL/PENDING");
    }

    private static void testCallbacks() throws Exception {
        Fixture fixture = new Fixture("callbacks");
        FiguraMod.ticks = 200;
        YsmActionsAPI api = new YsmActionsAPI(fixture.owner);
        var binding = api.bindAction("Hold", "extra_animation.hold", "key.keyboard.unknown", false);
        require(binding != null && binding.press.invoke(LuaValue.NONE).arg1().toboolean(), "formal action keybind press callback starts hold");
        require(binding.release.invoke(LuaValue.NONE).arg1().toboolean(), "formal action keybind release callback stops hold");
        FiguraMod.ticks = 210;
        binding.press.invoke(LuaValue.NONE);
        require(api.unbindWheelKeybind("Hold") == 1 && !fixture.runtime.actions().isActive("extra_animation.hold"),
                "removing a pressed binding releases its owned hold");

        YsmActionWheelLayoutStore.set("callbacks", "extra_animation.hold", "favorites", 4);
        YsmActionWheelLayoutStore.set("callbacks", "extra_animation.press", "favorites", 5);
        var slot = api.bindWheelPageSlot("Slot Hold", "favorites", 4, "key.keyboard.unknown", false);
        FiguraMod.ticks = 220;
        require(slot.press.invoke(LuaValue.NONE).arg1().toboolean(), "formal slot keybind resolves current action");
        YsmActionWheelLayoutStore.set("callbacks", "extra_animation.press", "favorites", 4);
        fixture.runtime.actions().trigger("extra_animation.press");
        slot.release.invoke(LuaValue.NONE);
        require(fixture.runtime.actions().isActive("extra_animation.press"), "release does not re-resolve an edited slot and stop its new action");

        var toggle = api.bindToggleAction("Toggle", "extra_animation.toggle", "key.keyboard.unknown", false);
        require(toggle.press.invoke(LuaValue.NONE).arg1().toboolean(), "formal toggle binding starts its action");
        require(toggle.press.invoke(LuaValue.NONE).arg1().toboolean()
                && !fixture.runtime.actions().isActive("extra_animation.toggle"), "formal toggle binding stops during cooldown");
        var toggleSlot = api.bindToggleWheelPageSlot("Toggle Slot", "favorites", 4, "key.keyboard.unknown", false);
        Fixture replacement = new Fixture("replacement");
        install(fixture.owner, "ysmRuntime", replacement.runtime);
        FiguraMod.ticks = 230;
        require(!binding.press.invoke(LuaValue.NONE).arg1().toboolean(), "old action binding rejects a different model runtime");
        require(!slot.press.invoke(LuaValue.NONE).arg1().toboolean(), "old slot binding rejects a different model runtime");
        require(!toggle.press.invoke(LuaValue.NONE).arg1().toboolean(), "old toggle binding rejects a different model runtime");
        require(!toggleSlot.press.invoke(LuaValue.NONE).arg1().toboolean(), "old toggle-slot binding rejects a different model runtime");
        install(fixture.owner, "ysmRuntime", fixture.runtime);
        Field wheelInput = ActionWheel.class.getDeclaredField("YSM_PRESS");
        wheelInput.setAccessible(true);
        var input = (YsmActionRuntime.Press) wheelInput.get(null);
        new Action().setYsmAction("extra_animation.hold").execute(fixture.owner, true, input);
        require(fixture.runtime.actions().isActive("extra_animation.hold"), "actual Action execution owns hold input");
        ActionWheel.setEnabled(false);
        require(!fixture.runtime.actions().isActive("extra_animation.hold"), "closing wheel releases its held action");
        fixture.runtime.actions().close();
        replacement.runtime.actions().close();
    }

    private static void testLayout() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousClient = instance.get(null);
        Field cached = WorldAPI.class.getDeclaredField("cached");
        cached.setAccessible(true);
        Object previousWorld = cached.get(null);
        ScreenClient client = (ScreenClient) unsafe.allocateInstance(ScreenClient.class);
        client.level = (RegistryLevel) unsafe.allocateInstance(RegistryLevel.class);
        instance.set(null, client);
        try {
            // The CPU fixture has no data-pack loader; bind only the four icon items used by this wheel.
            for (var item : List.of(Items.COMPARATOR, Items.BOOK, Items.ARROW, Items.ARMOR_STAND)) {
                item.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
            }
            var item = net.minecraft.commands.arguments.item.ItemArgument.item(
                    net.minecraft.commands.CommandBuildContext.simple(client.level.registryAccess(), client.level.enabledFeatures()))
                    .parse(new com.mojang.brigadier.StringReader("minecraft:comparator")).createItemStack(1);
            new org.figuramc.figura.lua.api.world.ItemStackAPI(item);
            testInstalledLayout();
            testPageTabOverflow();
        } finally {
            instance.set(null, previousClient);
            cached.set(null, previousWorld);
        }
    }

    private static void testInstalledLayout() throws Exception {
        clearLayout();
        YsmActionWheelLayoutStore.addPage("layout-a", "favorites");
        YsmActionWheelLayoutStore.set("layout-a", "extra_animation.hold", "favorites", 4);
        require(Files.isRegularFile(FiguraMod.getCacheDirectory().resolve("ysm_action_wheel.nbt")), "real layout cache written");
        clearLayout();
        require(YsmActionWheelLayoutStore.pages("layout-a").contains("favorites"), "page restored from disk");
        require(YsmActionWheelLayoutStore.get("layout-a", "extra_animation.hold").slot() == 4, "slot restored from disk");
        require(YsmActionWheelLayoutStore.get("layout-b", "extra_animation.hold") == null, "same-ID model layout remains isolated");
        Fixture fixture = new Fixture("layout-a");
        ActionWheelAPI wheel = fixture.owner.luaRuntime.action_wheel;
        fixture.runtime.installDefaultActionWheel(wheel);
        var root = wheel.getCurrentPage();
        Action open = null;
        for (Action candidate : root.slots()) {
            if (candidate != null && "favorites".equals(candidate.getTitle())) {
                open = candidate;
            }
        }
        require(open != null, "restored page is installed in the actual wheel");
        open.execute(fixture.owner, true, new YsmActionRuntime.Press());
        require(wheel.getCurrentPage() != root, "existing page action virtual dispatch remains intact");
        require("extra_animation.hold".equals(wheel.getCurrentPage().getAction(4).getYsmAction()), "actual wheel consumes the restored slot");
        wheel.getCurrentPage().getAction(1).execute(fixture.owner, true, new YsmActionRuntime.Press());
        require(wheel.getCurrentPage() == root, "existing back action dispatch remains intact");
        testFormRouting(fixture);
        fixture.runtime.actions().close();
    }

    private static void testPageTabOverflow() throws Exception {
        Fixture fixture = new Fixture("tabs");
        var addTabs = AvatarControlsScreen.class.getDeclaredMethod("addPageTabs");
        addTabs.setAccessible(true);
        var contentTop = AvatarControlsScreen.class.getDeclaredMethod("contentTop");
        contentTop.setAccessible(true);
        Field pagesField = AvatarControlsScreen.class.getDeclaredField("pages");
        pagesField.setAccessible(true);
        Field tabsField = AvatarControlsScreen.class.getDeclaredField("pageTabs");
        tabsField.setAccessible(true);
        Field barField = AvatarControlsScreen.class.getDeclaredField("pageTabScrollbar");
        barField.setAccessible(true);
        try {
            for (int count : List.of(2, 12)) {
                var screen = new AvatarControlsScreen(null, fixture.owner, "page" + (count - 1));
                screen.width = 600;
                screen.height = 360;
                @SuppressWarnings("unchecked")
                List<String> pages = (List<String>) pagesField.get(screen);
                for (int index = 0; index < count; index++) {
                    pages.add("page" + index);
                }
                addTabs.invoke(screen);
                @SuppressWarnings("unchecked")
                List<org.figuramc.figura.gui.widgets.Button> tabs =
                        (List<org.figuramc.figura.gui.widgets.Button>) tabsField.get(screen);
                require(tabs.size() == count, "all page tabs are retained, including overflowing pages");
                var bar = (org.figuramc.figura.gui.widgets.ScrollBarWidget) barField.get(screen);
                if (count == 2) {
                    require(bar == null && (int) contentTop.invoke(screen) == 80,
                            "fitting tabs keep the original layout without a scrollbar");
                    continue;
                }
                require(bar != null && bar.getWidth() > bar.getHeight(), "overflow creates the existing scrollbar in horizontal orientation");
                require((int) contentTop.invoke(screen) > bar.getY() + bar.getHeight(),
                        "controls begin below the horizontal scrollbar");
                var last = tabs.getLast();
                int left = bar.getX();
                int right = left + bar.getWidth();
                require(last.getX() >= left && last.getX() + last.getWidth() <= right,
                        "opening the last page automatically reveals its selected tab");
                bar.setScrollProgressNoAnim(0);
                require(tabs.getFirst().getX() == left && !last.isMouseOver(last.getX() + 1, last.getY() + 1),
                        "left endpoint reveals the first tab and clips hit testing for hidden tabs");
                require(screen.mouseScrolled(left + 1, last.getY() + 1, -1, 0)
                                && bar.getScrollProgress() > 0,
                        "wheel input over tabs scrolls horizontally");
                bar.setScrollProgressNoAnim(1);
                require(last.getX() + last.getWidth() == right && last.isMouseOver(right - 1, last.getY() + 1),
                        "right endpoint exposes the final tab and permits hits only inside the viewport");
                require(!last.isMouseOver(right, last.getY() + 1), "clipped tabs cannot capture clicks beyond the preview boundary");
                bar.setScrollProgressNoAnim(0.5);
                Field dragging = org.figuramc.figura.gui.widgets.ScrollBarWidget.class.getDeclaredField("isScrolling");
                dragging.setAccessible(true);
                dragging.setBoolean(bar, true);
                require(bar.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(right - 1, bar.getY() + 1, null), 20, 0)
                                && bar.getScrollProgress() > 0.5,
                        "the existing scrollbar drag path uses the horizontal axis");
                bar.setScrollProgressNoAnim(2);
                require(bar.getScrollProgress() == 1, "tab scrolling clamps at the final page");
            }
        } finally {
            fixture.runtime.actions().close();
        }
        System.out.println("Controls page tab overflow: all tabs, horizontal wheel/drag, bounds and selected-page visibility PASS; real GUI pending.");
    }

    private static void testFormRouting(Fixture fixture) throws Exception {
        YsmActionSchemaParser.apply(fixture.runtime, "ysm.json", """
                {"actions":[{"id":"extra_animation.settings","controls_page":"style"}],
                 "properties":{"extra_animation_buttons":[{"id":"style","name":"Style","config_forms":[]}]}}
                """);
        YsmActionsAPI api = new YsmActionsAPI(fixture.owner);
        ScreenClient client = (ScreenClient) Minecraft.getInstance();
        require(!api.trigger("extra_animation.settings") && client.screen == null,
                "non-host model cannot open the local player's controls");
        install(fixture.owner, "isHost", true);
        require(api.trigger("extra_animation.settings"), "Lua trigger resolves a form reference");
        require(client.screen instanceof AvatarControlsScreen, "existing controls screen receives the request");
        Field page = AvatarControlsScreen.class.getDeclaredField("page");
        page.setAccessible(true);
        require("style".equals(page.get(client.screen)), "form action routes to its declared page");
        YsmActionWheelLayoutStore.set("layout-a", "extra_animation.settings", "favorites", 5);
        require(api.triggerWheelPageSlot("favorites", 5), "Lua wheel-slot trigger resolves the form reference");
        var direct = api.bindAction("Settings", "extra_animation.settings", "key.keyboard.unknown", false);
        require(direct.press.invoke(LuaValue.NONE).arg1().toboolean(), "action keybind resolves the form reference");
        var slot = api.bindWheelPageSlot("Settings Slot", "favorites", 5, "key.keyboard.unknown", false);
        require(slot.press.invoke(LuaValue.NONE).arg1().toboolean(), "slot keybind resolves the form reference");
        require(!fixture.runtime.animations().isActive("#style"), "opening a form never creates an animation slot");
    }

    private static final class ScreenClient extends Minecraft {
        private ScreenClient() {
            super(null);
        }

        @Override
        public void setScreen(Screen screen) {
            this.screen = screen;
        }
    }

    private static void clearLayout() throws Exception {
        for (String field : List.of("LAYOUTS", "PAGES")) {
            Field value = YsmActionWheelLayoutStore.class.getDeclaredField(field);
            value.setAccessible(true);
            ((Map<?, ?>) value.get(null)).clear();
        }
        Field loaded = YsmActionWheelLayoutStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
    }

    private static final class RegistryLevel extends ClientLevel {
        private RegistryLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        }

        @Override
        public FeatureFlagSet enabledFeatures() {
            return FeatureFlags.DEFAULT_FLAGS;
        }
    }

    private static final class Fixture {
        private final Avatar owner;
        private final YsmModelRuntime runtime;

        private Fixture(String key) throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Unsafe unsafe = (Unsafe) field.get(null);
            owner = (Avatar) unsafe.allocateInstance(Avatar.class);
            install(owner, "controls", new AvatarControlRuntime());
            install(owner, "molangContext", new Avatar.MolangContext(owner));
            Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            runtime = constructor.newInstance(owner, new YsmGeometry(), null, null, "fixture", false,
                    "NEW", key, Map.of(), 1f, 1f, false);
            install(owner, "ysmRuntime", runtime);
            FiguraLuaRuntime lua = (FiguraLuaRuntime) unsafe.allocateInstance(FiguraLuaRuntime.class);
            lua.keybinds = new KeybindAPI(owner);
            lua.action_wheel = new ActionWheelAPI(owner);
            install(owner, "luaRuntime", lua);
            owner.controls.register(new AvatarControlDefinition("ysm.action_speed", AvatarControlType.SLIDER)
                    .setRange(0.1, 5, 0.05).setDefault(1.5));
            owner.controls.register(new AvatarControlDefinition("ysm.action_loop", AvatarControlType.TOGGLE).setDefault(false));
            Map<String, YsmAnimationClip> clips = new LinkedHashMap<>();
            for (String name : List.of("held", "toggle", "press")) {
                YsmAnimationClip clip = new YsmAnimationClip("animation." + name, 2f, YsmAnimationClip.LoopMode.ONCE);
                clips.put(clip.name, clip);
            }
            runtime.animations().registerAnimations(clips);
            YsmActionSchemaParser.apply(runtime, "ysm.json", """
                    {"actions":[
                     {"id":"extra_animation.hold","animation":"held","mode":"hold","cooldown":10,"speed":2},
                     {"id":"extra_animation.toggle","animation":"toggle","mode":"toggle","cooldown":10},
                     {"id":"extra_animation.press","animation":"press","mode":"press"}]}
                    """);
        }
    }

    private static void install(Avatar owner, String name, Object value) throws Exception {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
