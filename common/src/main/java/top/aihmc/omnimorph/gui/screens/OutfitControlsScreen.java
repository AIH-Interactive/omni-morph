package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.utils.FiguraText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Formal controls retain the exact resident pair; draft controls keep their separate preview lifecycle. */
public final class OutfitControlsScreen extends AvatarControlsScreen {
    private record Choice(Avatar owner, Component label) {}

    private final UUID account;
    private final UserData.OutfitState outfit;
    private final ClientLevel world;
    private final long connectionEpoch;
    private List<Choice> choices;
    private long baseDefinitions;
    private long instanceDefinitions;
    private boolean closed;

    private OutfitControlsScreen(Screen parent, Avatar owner, String page, UUID account, UserData.OutfitState outfit) {
        super(parent, owner, page);
        this.account = account;
        this.outfit = outfit;
        world = Minecraft.getInstance().level;
        connectionEpoch = NetworkStuff.connectionEpoch();
        choices = choices();
        baseDefinitions = outfit.base() == null ? 0 : outfit.base().controls.definitionRevision();
        instanceDefinitions = outfit.composition().controlGroupsRevision();
    }

    /** Shared by the original Lua/wheel controls entry and CPU handler validation. Does not open a window. */
    public static OutfitControlsScreen create(Screen parent, Avatar owner, String page) {
        if (owner == null) {
            return null;
        }
        UUID wearer = owner.cosmeticContext == null ? owner.owner : owner.cosmeticContext.wearerId();
        if (wearer == null) {
            return null;
        }
        var outfit = AvatarManager.getLoadedOutfit(wearer);
        if (outfit == null || !eligible(outfit, owner)) {
            return null;
        }
        UUID account = FiguraMod.getLocalPlayerUUID();
        if (!account.equals(wearer)) {
            return null;
        }
        return new OutfitControlsScreen(parent, owner, page, account, outfit);
    }

    private static boolean eligible(UserData.OutfitState outfit, Avatar owner) {
        if (!owner.loaded || owner.scriptError || !owner.accelerationOwner().isCurrent()) {
            return false;
        }
        if (owner == outfit.base()) {
            return true;
        }
        return owner.cosmeticContext != null && owner.cosmeticContext.scope() == CosmeticInstanceContext.Scope.OWNER
                && outfit.composition().controlGroups().stream().anyMatch(group -> group.avatar() == owner);
    }

    private List<Choice> choices() {
        var result = new ArrayList<Choice>();
        if (outfit.base() != null && eligible(outfit, outfit.base())) {
            result.add(new Choice(outfit.base(), FiguraText.of("gui.controls.title")));
        }
        for (var group : outfit.composition().controlGroups()) {
            if (eligible(outfit, group.avatar())) {
                String name = group.avatar().nbt.getCompoundOrEmpty("metadata")
                        .getStringOr("name", group.instance().resource().format().name());
                result.add(new Choice(group.avatar(), Component.literal(name + " #" + group.instance().displayOrdinal())));
            }
        }
        return List.copyOf(result);
    }

    @Override
    protected void init() {
        closed = false;
        super.init();
    }

    @Override
    protected boolean controlsActive() {
        return !closed && account.equals(FiguraMod.getLocalPlayerUUID())
                && world == Minecraft.getInstance().level && connectionEpoch == NetworkStuff.connectionEpoch()
                && AvatarManager.getLoadedOutfit(account) == outfit && eligible(outfit, avatar);
    }

    @Override
    protected void buildAdditionalControls() {
        if (choices.size() < 2) {
            return;
        }
        var expected = choices;
        int selectorWidth = Math.min(360, Math.max(1, width - 40));
        addRenderableWidget(new LibraryDropdown((width - selectorWidth) / 2, 4, selectorWidth,
                () -> expected.stream().map(Choice::label).toList(),
                () -> expected.indexOf(expected.stream().filter(choice -> choice.owner() == avatar).findFirst().orElse(null)),
                selected -> {
                    if (controlsActive() && expected == choices && selected >= 0 && selected < expected.size()
                            && eligible(outfit, expected.get(selected).owner())) {
                        selectControlAvatar(expected.get(selected).owner());
                    }
                }, 6));
    }

    private boolean withBudget(BooleanSupplier action) {
        if (!controlsActive()) {
            return false;
        }
        boolean[] result = {false};
        if (outfit.base() == null) {
            result[0] = action.getAsBoolean();
        } else {
            outfit.composition().withBaseBudget(outfit.base(), () -> result[0] = action.getAsBoolean());
        }
        return result[0];
    }

    @Override
    protected boolean applyControlValue(AvatarControlDefinition control, Object value) {
        return withBudget(() -> super.applyControlValue(control, value));
    }

    @Override
    protected void pressControl(AvatarControlDefinition control) {
        withBudget(() -> {
            super.pressControl(control);
            return true;
        });
    }

    @Override
    protected boolean includeWheelEditor() {
        return avatar.cosmeticContext == null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!controlsActive()) {
            onClose();
            return;
        }
        var current = choices();
        long currentBase = outfit.base() == null ? 0 : outfit.base().controls.definitionRevision();
        long currentInstances = outfit.composition().controlGroupsRevision();
        if (!current.equals(choices) || baseDefinitions != currentBase || instanceDefinitions != currentInstances) {
            choices = current;
            baseDefinitions = currentBase;
            instanceDefinitions = currentInstances;
            rebuild();
        }
    }

    @Override
    public void onClose() {
        closed = true;
        super.onClose();
    }

    @Override
    public void removed() {
        closed = true;
        super.removed();
    }
}
