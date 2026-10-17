package top.aihmc.omnimorph.avatar;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.local.CacheAvatarLoader;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.avatar.ysm.YsmLoadGeneration;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

public class UserData {

    public final UUID id;
    public final YsmLoadGeneration ysmLoads = new YsmLoadGeneration();
    private final Queue<Avatar> avatars = new ConcurrentLinkedQueue<>();
    private Pair<BitSet, BitSet> badges;

    public UserData(UUID id) {
        this.id = id;
    }

    public void loadData(ArrayList<Pair<String, Pair<String, UUID>>> avatars, Pair<BitSet,BitSet> badges) {
        loadData(avatars, badges, () -> true);
    }

    public void loadData(ArrayList<Pair<String, Pair<String, UUID>>> avatars, Pair<BitSet,BitSet> badges,
                         BooleanSupplier current) {
        loadBadges(badges);
        if (avatars.isEmpty()) {
            clear();
        }
        for (Pair<String, Pair<String, UUID>> avatar : avatars) {
            if (!CacheAvatarLoader.checkAndLoad(avatar.getFirst(), this, current)) {
                Pair<String, UUID> pair = avatar.getSecond();
                NetworkStuff.getAvatar(this, pair.getSecond(), pair.getFirst(), avatar.getFirst());
            }
        }
    }

    public CompletableFuture<Boolean> loadAvatar(CompoundTag nbt) {
        return loadAvatar(nbt, () -> true);
    }

    public CompletableFuture<Boolean> loadAvatar(
            CompoundTag nbt, BooleanSupplier current) {
        long generation = ysmLoads.begin();
        Avatar prepared = new Avatar(id);
        return AvatarLoadExecutor.submit(() -> {
            if (!current.getAsBoolean() || !ysmLoads.current(generation)) {
                return false;
            }
            prepared.load(nbt, null, () -> !current.getAsBoolean() || !ysmLoads.current(generation));
            return prepared.nbt != null && prepared.loaded;
        }).handle((success, failure) -> {
            if (failure != null) {
                FiguraMod.LOGGER.error("Model preparation failed for {}", id, failure);
            }
            return failure == null && success;
        }).thenCompose(success -> publishAvatar(prepared, generation, () -> success && current.getAsBoolean()));
    }

    public CompletableFuture<Boolean> loadYsmAvatar(
            Avatar prepared, CompoundTag nbt, YsmModelData data, long generation) throws IOException {
        if (!ysmLoads.current(generation)) {
            return publishAvatar(prepared, generation, () -> false);
        }
        prepared.load(nbt, data, () -> !ysmLoads.current(generation));
        if (!prepared.loaded || prepared.getYsmRuntime() == null) {
            Minecraft.getInstance().execute(prepared::clean);
            throw new IOException("RUNTIME_ASSEMBLY_FAILED: YSM avatar was not prepared");
        }
        return publishAvatar(prepared, generation, () -> true);
    }

    /** Only the short swap and retirement of native resources run on the client thread. */
    public CompletableFuture<Boolean> publishAvatar(
            Avatar prepared, long generation, BooleanSupplier current) {
        var result = new CompletableFuture<Boolean>();
        Minecraft.getInstance().execute(() -> {
            try {
                boolean published = current.getAsBoolean() && ysmLoads.publish(generation, () -> {
                    for (Avatar previous : avatars) {
                        previous.clean();
                    }
                    avatars.clear();
                    avatars.add(prepared);
                });
                if (!published) {
                    prepared.clean();
                }
                result.complete(published);
            } catch (Exception failure) {
                prepared.clean();
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public void loadBadges(Pair<BitSet, BitSet> pair) {
        this.badges = pair;
    }

    public Pair<BitSet, BitSet> getBadges() {
        return badges;
    }

    public Queue<Avatar> getAvatars() {
        return avatars;
    }

    public Avatar getMainAvatar() {
        return avatars.peek();
    }

    public void clear() {
        long generation = ysmLoads.begin();
        ysmLoads.publish(generation, () -> {
            for (Avatar avatar : avatars) {
                avatar.clean();
            }
            avatars.clear();
        });
    }
}
