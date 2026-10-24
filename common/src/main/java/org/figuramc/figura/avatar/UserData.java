package org.figuramc.figura.avatar;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.local.CacheAvatarLoader;
import org.figuramc.figura.backend2.NetworkStuff;
import top.aihmc.omnimorph.avatar.ysm.YsmLoadGeneration;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
public class UserData {

    public final UUID id;
    public final YsmLoadGeneration ysmLoads = new YsmLoadGeneration();
    public final AvatarLoadProgress loading = new AvatarLoadProgress();
    private final Queue<Avatar> avatars = new ConcurrentLinkedQueue<>();
    private Pair<BitSet, BitSet> badges;
    public record OutfitState(OutfitConfig configuration, Avatar base, OutfitComposition composition) {}
    private volatile OutfitState outfit;

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
            Pair<String, UUID> pair = avatar.getSecond();
            Runnable download = () -> NetworkStuff.getAvatar(this, pair.getSecond(), pair.getFirst(), avatar.getFirst());
            if (!CacheAvatarLoader.checkAndLoad(avatar.getFirst(), this, current, download)) {
                download.run();
            }
        }
    }

    public CompletableFuture<Boolean> loadAvatar(CompoundTag nbt) {
        return loadAvatar(nbt, () -> true);
    }

    public CompletableFuture<Boolean> loadAvatar(
            CompoundTag nbt, BooleanSupplier current) {
        return loadAvatar(nbt, current, null);
    }

    public CompletableFuture<Boolean> loadAvatar(
            CompoundTag nbt, BooleanSupplier current, OutfitConfig.ResourceRef source) {
        long generation = ysmLoads.begin();
        long progress = loading.begin(AvatarLoadProgress.Stage.PREPARING);
        Avatar prepared = new Avatar(id);
        return AvatarLoadExecutor.submit(() -> {
            if (!current.getAsBoolean() || !ysmLoads.current(generation)) {
                return false;
            }
            prepared.load(nbt, null, () -> !current.getAsBoolean() || !ysmLoads.current(generation),
                    stage -> loading.stage(progress, stage));
            if (prepared.loaded && prepared.nbt != null && source != null) {
                prepared.bindOutfitSource(source);
            }
            return prepared.nbt != null && prepared.loaded;
        }).handle((success, failure) -> {
            if (failure != null) {
                FiguraMod.LOGGER.error("Model preparation failed for {}", id, failure);
            }
            return failure == null && success;
        }).thenCompose(success -> {
            loading.stage(progress, AvatarLoadProgress.Stage.APPLYING);
            return publishAvatar(prepared, generation, () -> success && current.getAsBoolean());
        }).whenComplete((published, failure) -> loading.finish(progress));
    }

    public CompletableFuture<Boolean> loadYsmAvatar(
            Avatar prepared, CompoundTag nbt, YsmModelData data, long generation) throws IOException {
        return loadYsmAvatar(prepared, nbt, data, generation, null);
    }

    public CompletableFuture<Boolean> loadYsmAvatar(
            Avatar prepared, CompoundTag nbt, YsmModelData data, long generation,
            OutfitConfig.ResourceRef source) throws IOException {
        if (!ysmLoads.current(generation)) {
            return publishAvatar(prepared, generation, () -> false);
        }
        prepared.load(nbt, data, () -> !ysmLoads.current(generation));
        if (!prepared.loaded || prepared.getYsmRuntime() == null) {
            Minecraft.getInstance().execute(prepared::clean);
            throw new IOException("RUNTIME_ASSEMBLY_FAILED: YSM avatar was not prepared");
        }
        if (source != null) {
            prepared.bindOutfitSource(source);
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
                    retireOutfit();
                    for (Avatar previous : avatars) {
                        top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.retireBase(previous);
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

    public OutfitState getOutfit() {
        return outfit;
    }

    /** Caller has verified current server authority and native preparation; only the pair swap runs here. */
    public CompletableFuture<Boolean> publishOutfit(OutfitConfig committed, Avatar base,
                                                     OutfitComposition prepared, long generation,
                                                     BooleanSupplier current) {
        java.util.Objects.requireNonNull(committed);
        java.util.Objects.requireNonNull(prepared);
        var result = new CompletableFuture<Boolean>();
        var candidate = new OutfitState(committed, base, prepared);
        Minecraft.getInstance().execute(() -> {
            try {
                boolean valid = current.getAsBoolean() && prepared.matchesConfiguration(committed, id)
                        && (outfit == null || committed.revision() >= outfit.configuration().revision())
                        && ((committed.base() == null && base == null)
                        || committed.base() != null && base != null && id.equals(base.owner) && base.loaded && base.nbt != null
                        && base.accelerationOwner().isCurrent() && committed.base().equals(base.outfitSource()));
                OutfitState previous = outfit;
                var previousAvatars = new ArrayList<>(avatars);
                boolean published = valid && ysmLoads.publish(generation, () -> {
                    avatars.clear();
                    if (base != null) {
                        avatars.add(base);
                    }
                    outfit = candidate;
                });
                if (published) {
                    if (previous != null && previous.composition() != prepared) {
                        previous.composition().close();
                    }
                    for (Avatar previousAvatar : previousAvatars) {
                        if (previousAvatar != base) {
                            top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.retireBase(previousAvatar);
                        }
                    }
                } else {
                    discardOutfitCandidate(base, prepared);
                }
                result.complete(published);
            } catch (Exception failure) {
                if (outfit == candidate) {
                    FiguraMod.LOGGER.error("Previous outfit retirement failed for {}", id, failure);
                    result.complete(true);
                } else {
                    discardOutfitCandidate(base, prepared);
                    result.completeExceptionally(failure);
                }
            }
        });
        return result;
    }

    private void discardOutfitCandidate(Avatar base, OutfitComposition prepared) {
        if (outfit == null || outfit.composition() != prepared) {
            prepared.close();
        }
        if (base != null && !avatars.contains(base)) {
            base.clean();
        }
    }

    private void retireOutfit() {
        OutfitState previous = outfit;
        outfit = null;
        if (previous != null) {
            previous.composition().close();
        }
    }

    public void tick() {
        OutfitState current = outfit;
        if (current != null) {
            current.composition().tick(current.base());
        } else {
            Avatar base = getMainAvatar();
            if (base != null) {
                base.tick();
            }
        }
    }

    public void clear() {
        loading.clear();
        long generation = ysmLoads.begin();
        ysmLoads.publish(generation, () -> {
            retireOutfit();
            for (Avatar avatar : avatars) {
                top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.retireBase(avatar);
            }
            avatars.clear();
        });
    }
}
