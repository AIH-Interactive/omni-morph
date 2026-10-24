package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;

import java.util.UUID;

/** One local player's uncommitted render preview. No UserData, server or observer state is changed. */
public final class LocalOutfitPreview {
    private static LibraryPreviewSession session;
    private static Object editor;
    private static UUID account;
    private static ClientLevel world;
    private static boolean ownsSession;

    private LocalOutfitPreview() {}

    public static void show(Object owner, LibraryPreviewSession preview) {
        if (session != preview) {
            clear();
        }
        session = preview;
        editor = owner;
        account = FiguraMod.getLocalPlayerUUID();
        world = Minecraft.getInstance().level;
        ownsSession = false;
    }

    public static boolean uses(LibraryPreviewSession preview) {
        return session != null && session == preview;
    }

    public static boolean active() {
        return state(Minecraft.getInstance().player) != null;
    }

    public static boolean shows(OutfitComposition composition) {
        var local = state(Minecraft.getInstance().player);
        return local != null && local.composition() == composition;
    }

    /** The editor gives up its session only when closing with world preview enabled. */
    public static void detach(Object owner) {
        if (session != null && editor == owner) {
            ownsSession = true;
        }
    }

    public static void clear(Object owner) {
        if (editor == owner) {
            clear();
        }
    }

    public static void clear() {
        var previous = session;
        boolean close = ownsSession;
        session = null;
        editor = null;
        account = null;
        world = null;
        ownsSession = false;
        if (close && previous != null) {
            OutfitRenderBinding.retirePreview(previous);
        }
    }

    public static UserData.OutfitState state(Entity entity) {
        var client = Minecraft.getInstance();
        if (session == null || entity == null || entity != client.player || AvatarManager.panic) {
            return null;
        }
        if (client.level != world || !account.equals(FiguraMod.getLocalPlayerUUID())) {
            clear();
            return null;
        }
        return session.outfitState();
    }

    public static void tick() {
        if (session == null) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.player == null || client.level != world || !account.equals(FiguraMod.getLocalPlayerUUID())
                || session.status().stage() == LibraryPreviewSession.Stage.CLOSED) {
            clear();
        } else if (ownsSession && !AvatarManager.panic) {
            session.tick();
        }
    }
}
