package top.aihmc.omnimorph.model.ysm;

import net.minecraft.world.entity.Entity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.lua.api.sound.SoundAPI;
import org.figuramc.figura.math.vector.FiguraVec3;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal binding adapter over existing Figura sounds; model audio assembly belongs to M5. */
public final class YsmSoundRuntime implements AutoCloseable {
    private static final Object GLOBAL = new Object();
    private final YsmModelRuntime runtime;
    private final Map<Object, Bank> banks = new HashMap<>();
    private boolean closed;

    public YsmSoundRuntime(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public Object execute(Avatar.MolangContext context, ExecutionContext<?> execution,
                          Function.ArgumentCollection arguments, String operation) {
        if (context.entity == null) {
            return null;
        }
        if (closed || runtime.isPreviewRuntime() || context.owner != runtime.owner() || !runtime.functions().clientEffectsEnabled()) {
            return false;
        }
        if (operation.equals("stop_all_sounds")) {
            boolean global = arguments.size() > 0 && arguments.getAsBoolean(execution, 0);
            bank(context, global).stopAll();
            return true;
        }
        Object value = arguments.getValue(execution, 0);
        int id;
        if (value instanceof Number number) {
            id = -number.intValue();
            if (id > 0) {
                return false;
            }
        } else {
            id = ValueConversions.asStringId(value);
        }
        if (operation.equals("stop_sound")) {
            boolean global = arguments.size() == 2 && arguments.getAsBoolean(execution, 1);
            return bank(context, global).stop(id);
        }
        String name = arguments.getAsString(execution, 1);
        if (name == null || name.isBlank()) {
            return false;
        }
        int flags = arguments.size() >= 3 ? arguments.getAsInt(execution, 2) : 0;
        if (flags < 0 || flags > 7) {
            return false;
        }
        LuaSound sound = createSound(name);
        if (sound == null) {
            return false;
        }
        sound.setLoop((flags & 4) != 0);
        if (arguments.size() >= 4) {
            sound.setEngineVolume(clamp(arguments.getAsFloat(execution, 3)));
        }
        if (arguments.size() >= 5) {
            sound.setPitch(clamp(arguments.getAsFloat(execution, 4)));
        }
        return bank(context, (flags & 2) != 0).play(id, (flags & 1) != 0,
                new Playback(sound, context.entity, runtime.functions().clientEffectOwner()));
    }

    public LuaSound createSound(String name) {
        if (closed) {
            return null;
        }
        if (runtime.audio().get(name) != null) {
            return runtime.audio().createSound(name, () -> !closed && !runtime.isClosed());
        }
        SoundAPI api = new SoundAPI(runtime.owner());
        return api.isPresent(name) ? api.__index(name) : null;
    }

    private static float clamp(float value) {
        return Math.max(0.001f, Math.min(1000f, value));
    }

    private Bank bank(Avatar.MolangContext context, boolean global) {
        String controller = ValueConversions.asString(context.controller.getPath("controller"));
        Object scope = runtime.functions().clientEffectOwner();
        Object key = GLOBAL;
        if (!global) {
            if (controller != null && !controller.isBlank()) {
                key = controller;
            } else if (scope != null) {
                key = scope;
            }
        }
        return banks.computeIfAbsent(key, ignored -> new Bank());
    }

    public void stopController(String controller) {
        Bank bank = banks.remove(controller);
        if (bank != null) {
            bank.stopAll();
        }
    }

    /** Retires only voices started by this playback, including globally indexed voices. */
    public void stopPlayback(Object owner) {
        if (owner == null) {
            return;
        }
        banks.values().forEach(bank -> bank.stopPlayback(owner));
        banks.values().removeIf(Bank::empty);
    }

    /** A remote snapshot may replace the playback object while the same clip keeps playing. */
    public void transferPlayback(Object previous, Object next) {
        banks.values().forEach(bank -> bank.transferPlayback(previous, next));
        Bank local = banks.remove(previous);
        if (local != null) {
            banks.put(next, local);
        }
    }

    public void tick() {
        banks.values().forEach(Bank::tick);
        banks.values().removeIf(Bank::empty);
    }

    @Override
    public void close() {
        closed = true;
        stopAll();
    }

    public void stopAll() {
        banks.values().forEach(Bank::stopAll);
        banks.clear();
    }

    private record Playback(LuaSound sound, Entity entity, Object owner) {
        boolean stopped() {
            return sound.getHandle() == null || sound.getHandle().isStopped();
        }

        void stop() {
            sound.stop();
        }

        void tick() {
            if (entity.isRemoved()) {
                stop();
            } else {
                sound.setPos(FiguraVec3.of(entity.getX(), entity.getY(), entity.getZ()), null, null);
            }
        }
    }

    private static final class Bank {
        private final Map<Integer, Playback> indexed = new HashMap<>();
        private final List<Playback> anonymous = new ArrayList<>();

        boolean play(int id, boolean replace, Playback next) {
            Playback previous = indexed.get(id);
            if (id != 0 && previous != null && !previous.stopped()) {
                if (!replace) {
                    return false;
                }
                previous.stop();
            }
            next.tick();
            if (next.entity().isRemoved()) {
                return false;
            }
            next.sound().play();
            if (next.stopped()) {
                return false;
            }
            if (id == 0) {
                anonymous.add(next);
            } else {
                indexed.put(id, next);
            }
            return true;
        }

        void transferPlayback(Object previous, Object next) {
            indexed.replaceAll((id, playback) -> transfer(playback, previous, next));
            anonymous.replaceAll(playback -> transfer(playback, previous, next));
        }

        private static Playback transfer(Playback playback, Object previous, Object next) {
            return playback.owner() == previous ? new Playback(playback.sound(), playback.entity(), next) : playback;
        }

        void stopPlayback(Object owner) {
            indexed.values().removeIf(playback -> stopIfOwned(playback, owner));
            anonymous.removeIf(playback -> stopIfOwned(playback, owner));
        }

        private static boolean stopIfOwned(Playback playback, Object owner) {
            if (playback.owner() != owner) {
                return false;
            }
            playback.stop();
            return true;
        }

        boolean stop(int id) {
            Playback playback = id == 0 ? null : indexed.remove(id);
            if (playback == null) {
                return false;
            }
            playback.stop();
            return true;
        }

        void stopAll() {
            List<Playback> playbacks = new ArrayList<>(indexed.values());
            playbacks.addAll(anonymous);
            indexed.clear();
            anonymous.clear();
            playbacks.forEach(Playback::stop);
        }

        void tick() {
            indexed.values().forEach(Playback::tick);
            anonymous.forEach(Playback::tick);
            indexed.values().removeIf(Playback::stopped);
            anonymous.removeIf(Playback::stopped);
        }

        boolean empty() {
            return indexed.isEmpty() && anonymous.isEmpty();
        }
    }
}
