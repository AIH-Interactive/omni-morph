package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import top.aihmc.omnimorph.model.library.LibraryPreviewBinding;

/** Client presentation/input lookup; equipment, synchronization and permission storage keep their own identity. */
public final class VisibleAvatar {
    private VisibleAvatar() {
    }

    public static Avatar forEntity(Entity entity) {
        if (entity == null || AvatarManager.panic) {
            return null;
        }
        // Explicit GUI scopes, including vanilla, take precedence over the world draft.
        if (LibraryPreviewBinding.isVanilla(entity) || LibraryPreviewBinding.avatar(entity) != null) {
            return AvatarManager.getAvatar(entity);
        }
        var draft = LocalOutfitPreview.state(entity);
        return draft != null ? draft.base() : AvatarManager.getAvatar(entity);
    }

    public static Avatar localPlayer() {
        return forEntity(Minecraft.getInstance().player);
    }
}
