package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.UserData;
import top.aihmc.omnimorph.model.library.LibraryPreviewBinding;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import org.figuramc.figura.model.rendering.EntityRenderMode;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client render-thread snapshots. Preview bindings take precedence and retain their existing ownership. */
public final class OutfitRenderBinding {
    private record Captured(Entity entity, UserData.OutfitState state, OutfitComposition.Frame composition) {}

    private static final Map<EntityRenderState, Captured> FRAMES = new IdentityHashMap<>();
    private static final Set<Avatar> BASE_FRAMES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Avatar> RETIRED_BASES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<LibraryPreviewSession> RETIRED_PREVIEWS = Collections.newSetFromMap(new IdentityHashMap<>());

    private OutfitRenderBinding() {}

    public static void capture(Entity entity, EntityRenderState frame, float partialTick) {
        capture(entity, frame, partialTick, false);
    }

    public static void capture(Entity entity, EntityRenderState frame, float partialTick, boolean world) {
        LibraryPreviewBinding.capture(entity, frame, partialTick, world);
        if (entity == null || frame == null || FRAMES.containsKey(frame) || LibraryPreviewBinding.entity(frame) != null) {
            return;
        }
        UserData.OutfitState state = LocalOutfitPreview.state(entity);
        if (state == null) {
            state = AvatarManager.getLoadedOutfit(entity.getUUID());
        }
        if (state == null) {
            return;
        }
        var retained = entity instanceof net.minecraft.world.entity.LivingEntity living
                ? state.composition().retain(living) : state.composition().retain();
        FRAMES.put(frame, new Captured(entity, state, retained));
        if (state.base() != null) {
            BASE_FRAMES.add(state.base());
        }
    }

    public static void capture(Entity entity, EntityRenderState frame) {
        capture(entity, frame, 1);
    }

    public static boolean hasFrame(EntityRenderState frame) {
        return FRAMES.containsKey(frame);
    }

    public static Avatar avatar(EntityRenderState frame) {
        Captured captured = FRAMES.get(frame);
        return captured == null ? null : captured.state().base();
    }

    public static Entity entity(EntityRenderState frame) {
        Captured captured = FRAMES.get(frame);
        return captured == null ? null : captured.entity();
    }

    public static OutfitComposition.Frame composition(EntityRenderState frame) {
        if (LibraryPreviewBinding.entity(frame) != null) {
            return LibraryPreviewBinding.composition(frame);
        }
        Captured captured = FRAMES.get(frame);
        return captured == null ? null : captured.composition();
    }

    public static boolean hasComposition(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (LibraryPreviewBinding.isVanilla(entity) || LibraryPreviewBinding.avatar(entity) != null) {
            return LibraryPreviewBinding.hasComposition(entity);
        }
        return LocalOutfitPreview.state(entity) != null || AvatarManager.getLoadedOutfit(entity.getUUID()) != null;
    }

    public static boolean isVanilla(EntityRenderState frame) {
        return LibraryPreviewBinding.entity(frame) != null ? LibraryPreviewBinding.isVanilla(frame)
                : hasFrame(frame) && avatar(frame) == null;
    }

    public static EntityRenderMode renderMode(EntityRenderState frame) {
        return LibraryPreviewBinding.entity(frame) != null && !LibraryPreviewBinding.worldFrame(frame)
                ? EntityRenderMode.FIGURA_GUI : EntityRenderMode.RENDER;
    }

    /** UserData gives up ownership immediately; native resources remain alive for previously extracted frames. */
    public static void retireBase(Avatar base) {
        if (BASE_FRAMES.contains(base)) {
            RETIRED_BASES.add(base);
        } else {
            base.clean();
        }
    }

    static void retirePreview(LibraryPreviewSession session) {
        if (FRAMES.isEmpty()) {
            session.close();
        } else {
            RETIRED_PREVIEWS.add(session);
        }
    }

    public static void finishFrame() {
        List<Captured> captured = List.copyOf(FRAMES.values());
        FRAMES.clear();
        try {
            for (Captured frame : captured) {
                frame.composition().close();
            }
        } finally {
            BASE_FRAMES.clear();
            List<Avatar> retired = List.copyOf(RETIRED_BASES);
            RETIRED_BASES.clear();
            for (Avatar base : retired) {
                base.clean();
            }
            var previews = List.copyOf(RETIRED_PREVIEWS);
            RETIRED_PREVIEWS.clear();
            previews.forEach(LibraryPreviewSession::close);
        }
    }
}
