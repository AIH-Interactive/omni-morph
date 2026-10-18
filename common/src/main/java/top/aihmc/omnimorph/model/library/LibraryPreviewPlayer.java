package top.aihmc.omnimorph.model.library;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;
import java.util.function.Supplier;

/** Detached identity with the current player's native skin and skin-layer preferences. */
final class LibraryPreviewPlayer extends RemotePlayer {
    private final Supplier<? extends AbstractClientPlayer> appearancePlayer;

    LibraryPreviewPlayer(ClientLevel level, UUID owner, Supplier<? extends AbstractClientPlayer> appearancePlayer) {
        super(level, new GameProfile(owner, "LibraryPreview"));
        this.appearancePlayer = appearancePlayer;
        setOnGround(true);
    }

    @Override
    public PlayerSkin getSkin() {
        AbstractClientPlayer player = appearancePlayer == null ? null : appearancePlayer.get();
        return player == null ? DefaultPlayerSkin.get(getUUID()) : player.getSkin();
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart part) {
        AbstractClientPlayer player = appearancePlayer == null ? null : appearancePlayer.get();
        return player == null || player.isModelPartShown(part);
    }
}
