package top.aihmc.omnimorph.model.library;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.avatar.Avatar;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scoped player/model lookup and ownership retained by deferred GUI frames. */
public final class LibraryPreviewBinding implements AutoCloseable {
    private static final Map<Entity, LibraryPreviewBinding> ENTITIES = new IdentityHashMap<>();
    private static final Map<UUID, LibraryPreviewBinding> PLAYERS = new HashMap<>();
    private static final Map<EntityRenderState, LibraryPreviewBinding> FRAMES = new IdentityHashMap<>();
    private final LivingEntity entity;
    private final Avatar avatar;
    private int frames;
    private boolean closed;

    LibraryPreviewBinding(LivingEntity entity, Avatar avatar) {
        this(entity, avatar, true);
    }

    LibraryPreviewBinding(LivingEntity entity, Avatar avatar, boolean persistent) {
        this.entity = entity;
        this.avatar = avatar;
        if (!persistent) {
            return;
        }
        if (ENTITIES.containsKey(entity) || PLAYERS.containsKey(avatar.owner)) {
            throw new IllegalStateException("PREVIEW_ALREADY_BOUND");
        }
        ENTITIES.put(entity, this);
        PLAYERS.put(avatar.owner, this);
    }

    /** Limit current-player model lookup to preview work; deferred frames retain their own binding. */
    public void withPlayerContext(Runnable action) {
        LibraryPreviewBinding previousEntity = ENTITIES.put(entity, this);
        LibraryPreviewBinding previousPlayer = PLAYERS.put(avatar.owner, this);
        try {
            action.run();
        } finally {
            if (previousEntity == null) {
                ENTITIES.remove(entity, this);
            } else {
                ENTITIES.put(entity, previousEntity);
            }
            if (previousPlayer == null) {
                PLAYERS.remove(avatar.owner, this);
            } else {
                PLAYERS.put(avatar.owner, previousPlayer);
            }
        }
    }

    public static Avatar avatar(Entity entity) {
        LibraryPreviewBinding binding = ENTITIES.get(entity);
        return binding == null ? null : binding.avatar;
    }

    public static Avatar avatar(UUID player) {
        LibraryPreviewBinding binding = PLAYERS.get(player);
        return binding == null ? null : binding.avatar;
    }

    public static Avatar avatar(EntityRenderState frame) {
        LibraryPreviewBinding binding = FRAMES.get(frame);
        return binding == null ? null : binding.avatar;
    }

    public static Entity entity(EntityRenderState frame) {
        LibraryPreviewBinding binding = FRAMES.get(frame);
        return binding == null ? null : binding.entity;
    }

    public static void capture(Entity entity, EntityRenderState frame) {
        LibraryPreviewBinding binding = ENTITIES.get(entity);
        if (binding == null || FRAMES.containsKey(frame)) {
            return;
        }
        FRAMES.put(frame, binding);
        binding.frames++;
    }

    /** Runs after actual GUI drawing, including exceptional completion and renderer shutdown. */
    public static void finishFrame() {
        LibraryThumbnailService.finishFrame();
        List<LibraryPreviewBinding> retained = List.copyOf(FRAMES.values());
        FRAMES.clear();
        for (LibraryPreviewBinding binding : retained) {
            binding.frames--;
            if (binding.closed && binding.frames == 0) {
                binding.avatar.clean();
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        ENTITIES.remove(entity, this);
        PLAYERS.remove(avatar.owner, this);
        if (frames == 0) {
            avatar.clean();
        }
    }
}
