package top.aihmc.omnimorph.model.outfit;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.lua.api.action_wheel.Action;
import org.figuramc.figura.lua.api.action_wheel.ActionWheelAPI;
import org.figuramc.figura.lua.api.action_wheel.Page;
import top.aihmc.omnimorph.model.ysm.action.YsmActionRuntime;
import org.figuramc.figura.utils.FiguraText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Host-owned navigation. Author pages/actions and each callback's Avatar remain untouched. */
public final class OutfitActionWheel implements AutoCloseable {
    public record Entry(OutfitConfig.Instance instance, String name, Avatar owner) {
        public String label() {
            return name + " #" + instance.displayOrdinal();
        }
    }

    private enum Location { BASE, RESOURCES, COPIES, INSTANCE }
    private record View(Page page, Avatar owner, ActionWheelAPI api) {}

    private final Supplier<List<Entry>> source;
    private final BooleanSupplier current;
    private final YsmActionRuntime.Press input = new YsmActionRuntime.Press();
    private List<Entry> entries = List.of();
    private Location location = Location.BASE;
    private Avatar base;
    private FiguraLuaRuntime baseRuntime;
    private Page basePage;
    private ProjectedPage root;
    private Page resources;
    private Page copies;
    private String resource;
    private Entry selected;
    private FiguraLuaRuntime selectedRuntime;
    private ProjectedPage instancePage;
    private boolean closed;
    private long navigationRevision;

    public OutfitActionWheel(Supplier<List<Entry>> source, BooleanSupplier current) {
        this.source = Objects.requireNonNull(source);
        this.current = Objects.requireNonNull(current);
    }

    public Page page(Avatar base) {
        return view(base).page();
    }

    public Avatar selectedOwner(Avatar base) {
        return view(base).owner();
    }

    public boolean available() {
        refreshEntries();
        return !entries.isEmpty();
    }

    private void refreshEntries() {
        List<Entry> latest = closed || !current.getAsBoolean() ? List.of() : source.get().stream()
                .filter(OutfitActionWheel::eligible).toList();
        if (entries.equals(latest)) {
            return;
        }
        entries = List.copyOf(latest);
        navigationRevision++;
        resources = null;
        copies = null;
        root = null;
        if (selected != null && entries.stream().noneMatch(entry -> sameOwner(entry, selected))) {
            leaveInstance();
            location = entries.isEmpty() ? Location.BASE : Location.RESOURCES;
        }
        if (location == Location.COPIES && matchingResource().isEmpty()) {
            location = entries.isEmpty() ? Location.BASE : Location.RESOURCES;
        }
        if (entries.isEmpty()) {
            release();
            location = Location.BASE;
        }
    }

    private static boolean eligible(Entry entry) {
        Avatar owner = entry.owner();
        var context = owner.cosmeticContext;
        Page page = owner.cosmeticAnimations == null ? null : owner.cosmeticAnimations.animationPage();
        return entry.instance().enabled() && context != null
                && context.instanceId().equals(entry.instance().instanceId())
                && context.scope() != CosmeticInstanceContext.Scope.OBSERVER && owner.loaded && !owner.scriptError
                && page != null && !page.isEmpty();
    }

    private static boolean sameOwner(Entry first, Entry second) {
        return first.instance().instanceId().equals(second.instance().instanceId()) && first.owner() == second.owner();
    }

    private View view(Avatar nextBase) {
        refreshEntries();
        FiguraLuaRuntime runtime = nextBase == null ? null : nextBase.luaRuntime;
        Page page = runtime == null ? null : runtime.action_wheel.getCurrentPage();
        if (base != nextBase || baseRuntime != runtime || basePage != page) {
            leaveInstance();
            location = Location.BASE;
            base = nextBase;
            baseRuntime = runtime;
            basePage = page;
            root = null;
            resources = null;
            copies = null;
            navigationRevision++;
        }
        if (closed || !current.getAsBoolean()) {
            return new View(null, null, null);
        }
        if (location == Location.INSTANCE) {
            if (selected.owner().luaRuntime != selectedRuntime || !eligible(selected)) {
                leaveInstance();
                location = Location.RESOURCES;
            } else {
                Page authored = selectedRuntime.action_wheel.getCurrentPage();
                if (authored == null) {
                    leaveInstance();
                    location = Location.RESOURCES;
                } else {
                    if (instancePage == null || instancePage.source != authored) {
                        instancePage = new ProjectedPage(authored, navigation("gui.controls.back", () -> {
                            leaveInstance();
                            location = matchingResource().size() > 1 ? Location.COPIES : Location.RESOURCES;
                        }));
                    }
                    return new View(instancePage, selected.owner(), selectedRuntime.action_wheel);
                }
            }
        }
        if (location == Location.COPIES) {
            if (copies == null) {
                copies = new Page(resource);
                copies.setAction(1, navigation("gui.controls.back", () -> location = Location.RESOURCES));
                for (Entry entry : matchingResource()) {
                    copies.setAction(null, namedNavigation(entry.label(), () -> enter(entry)));
                }
            }
            return new View(copies, null, null);
        }
        if (location == Location.RESOURCES) {
            if (resources == null) {
                resources = new Page(FiguraText.of("gui.wardrobe.cosmetic_animations").getString());
                resources.setAction(1, navigation("gui.controls.back", () -> location = Location.BASE));
                var grouped = new LinkedHashMap<String, List<Entry>>();
                for (Entry entry : entries) {
                    grouped.computeIfAbsent(entry.instance().resource().identity(), ignored -> new ArrayList<>()).add(entry);
                }
                for (var group : grouped.entrySet()) {
                    Entry first = group.getValue().getFirst();
                    resources.setAction(null, namedNavigation(first.name(), () -> {
                        resource = group.getKey();
                        copies = null;
                        if (matchingResource().size() == 1) {
                            enter(matchingResource().getFirst());
                        } else {
                            location = Location.COPIES;
                        }
                    }));
                }
            }
            return new View(resources, null, null);
        }
        if (entries.isEmpty()) {
            return new View(basePage, base, baseRuntime == null ? null : baseRuntime.action_wheel);
        }
        if (root == null) {
            root = new ProjectedPage(basePage, navigation("gui.wardrobe.cosmetic_animations",
                    () -> location = Location.RESOURCES));
        }
        return new View(root, base, baseRuntime == null ? null : baseRuntime.action_wheel);
    }

    private List<Entry> matchingResource() {
        return entries.stream().filter(entry -> entry.instance().resource().identity().equals(resource)).toList();
    }

    private void enter(Entry entry) {
        // Menu callbacks may outlive a removed/replaced instance or the whole preview session.
        refreshEntries();
        if (closed || !current.getAsBoolean() || entries.stream().noneMatch(value -> sameOwner(value, entry))) {
            return;
        }
        leaveInstance();
        selected = entry;
        selectedRuntime = entry.owner().luaRuntime;
        selectedRuntime.action_wheel.setPage(entry.owner().cosmeticAnimations.animationPage());
        location = Location.INSTANCE;
    }

    private void leaveInstance() {
        release();
        selected = null;
        selectedRuntime = null;
        instancePage = null;
    }

    public boolean execute(Avatar base, int index, boolean left) {
        if (index < 0 || index >= 8) {
            return false;
        }
        View view = view(base);
        if (view.page() == null) {
            return false;
        }
        Action action = view.page().slots()[index];
        if (action instanceof Navigation) {
            action.execute(view.owner(), left, input);
            return true;
        }
        try (var budget = enterBudget(view.owner())) {
            if (view.api() != null && view.api().execute(view.owner(), left)) {
                return true;
            }
            View refreshed = view(base);
            if (view.owner() != refreshed.owner() || refreshed.page() == null) {
                return false;
            }
            action = refreshed.page().slots()[index];
            if (action == null || view.owner() == null) {
                return false;
            }
            action.execute(view.owner(), left, input);
        }
        return true;
    }

    public void scroll(Avatar base, int selected, double delta) {
        if (!Double.isFinite(delta) || delta == 0) {
            return;
        }
        View view = view(base);
        if (view.page() == null) {
            return;
        }
        try (var budget = enterBudget(view.owner())) {
            if (view.api() != null && view.api().mouseScroll(view.owner(), delta)) {
                return;
            }
            if (selected >= 0 && selected < 8) {
                Action action = view.page().slots()[selected];
                if (action != null && action.scroll != null && view.owner() != null) {
                    action.mouseScroll(view.owner(), delta);
                    return;
                }
            }
            view.page().setSlotsShift(view.page().getSlotsShift() - (int) Math.signum(delta));
        }
    }

    private static OutfitInstructionBudget.Scope enterBudget(Avatar owner) {
        var budget = owner == null || owner.cosmeticContext == null ? null : owner.cosmeticContext.instructionBudget();
        return budget == null ? null : budget.enter();
    }

    private Action navigation(String key, Runnable navigate) {
        return namedNavigation(FiguraText.of(key).getString(), navigate);
    }

    private Action namedNavigation(String name, Runnable navigate) {
        return new Navigation(name, navigate);
    }

    private final class Navigation extends Action {
        private final Runnable navigate;
        private final long revision = navigationRevision;

        private Navigation(String name, Runnable navigate) {
            setTitle(name);
            this.navigate = navigate;
        }

        @Override
        public void execute(Avatar ignored, boolean left) {
            refreshEntries();
            if (left && !closed && current.getAsBoolean() && revision == navigationRevision) {
                release();
                navigate.run();
            }
        }
    }

    /** Append one host entry after the authored slots, including a completely full first group. */
    private static final class ProjectedPage extends Page {
        private final Page source;
        private final Action extra;
        private int shift;

        private ProjectedPage(Page source, Action extra) {
            super(source == null ? null : source.getTitle());
            this.source = source;
            this.extra = extra;
            shift = source == null ? 0 : source.getSlotsShift() - 1;
        }

        private int extraSlot() {
            return source == null || source.isEmpty() ? 0 : source.getGreatestSlot() + 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public int getGreatestSlot() {
            return extraSlot();
        }

        @Override
        public int getGroupCount() {
            return extraSlot() / 8 + 1;
        }

        @Override
        public int getSlotsShift() {
            return shift + 1;
        }

        @Override
        public Page setSlotsShift(int value) {
            shift = Math.clamp(value - 1, 0, getGroupCount() - 1);
            return this;
        }

        @Override
        public Action[] slots() {
            return slots(shift);
        }

        @Override
        public Action[] slots(int group) {
            Action[] slots = source == null ? new Action[8] : source.slots(group);
            int index = extraSlot() - group * 8;
            if (index >= 0 && index < slots.length) {
                slots[index] = extra;
            }
            return slots;
        }
    }

    public void release() {
        input.release();
    }

    @Override
    public void close() {
        closed = true;
        leaveInstance();
        entries = List.of();
        root = null;
        resources = null;
        copies = null;
        base = null;
        baseRuntime = null;
        basePage = null;
    }
}
