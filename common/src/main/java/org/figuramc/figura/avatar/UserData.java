package org.figuramc.figura.avatar;

import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.local.CacheAvatarLoader;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.avatar.ysm.YsmLoadGeneration;
import org.figuramc.figura.avatar.ysm.YsmModelData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UserData {

    public final UUID id;
    public final YsmLoadGeneration ysmLoads = new YsmLoadGeneration();
    private final Queue<Avatar> avatars = new ConcurrentLinkedQueue<>();
    private Pair<BitSet, BitSet> badges;

    public UserData(UUID id) {
        this.id = id;
    }

    public void loadData(ArrayList<Pair<String, Pair<String, UUID>>> avatars, Pair<BitSet,BitSet> badges) {
        loadData(avatars, badges, Runnable::run);
    }

    public void loadData(ArrayList<Pair<String, Pair<String, UUID>>> avatars, Pair<BitSet,BitSet> badges,
                         java.util.function.Consumer<Runnable> publish) {
        loadBadges(badges);
        clear();
        for (Pair<String, Pair<String, UUID>> avatar : avatars) {
            if (!CacheAvatarLoader.checkAndLoad(avatar.getFirst(), this, publish)) {
                Pair<String, UUID> pair = avatar.getSecond();
                NetworkStuff.getAvatar(this, pair.getSecond(), pair.getFirst(), avatar.getFirst());
            }
        }
    }

    public void loadAvatar(CompoundTag nbt) {
        ysmLoads.begin();
        FiguraMod.debug("--- avatar loading: " + id + " ---");
        Avatar avatar = new Avatar(id);
        this.avatars.add(avatar);
        avatar.load(nbt);
        FiguraMod.debug("--- loaded " + id + " ---");
    }

    public boolean loadYsmAvatar(CompoundTag nbt, YsmModelData data,
                                  long generation) throws IOException {
        if (!ysmLoads.current(generation)) {
            return false;
        }
        Avatar prepared = new Avatar(id);
        try {
            prepared.load(nbt, data);
            if (!prepared.loaded || prepared.getYsmRuntime() == null) {
                throw new IOException("RUNTIME_ASSEMBLY_FAILED: YSM avatar was not prepared");
            }
            if (!ysmLoads.publish(generation, () -> {
                for (Avatar previous : avatars) {
                    previous.clean();
                }
                avatars.clear();
                avatars.add(prepared);
            })) {
                prepared.clean();
                return false;
            }
            return true;
        } catch (Exception e) {
            prepared.clean();
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("RUNTIME_ASSEMBLY_FAILED: YSM avatar preparation failed", e);
        }
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
