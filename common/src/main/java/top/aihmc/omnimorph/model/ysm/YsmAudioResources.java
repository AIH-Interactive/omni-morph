package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import top.aihmc.omnimorph.lua.api.sound.SoundAPI;
import top.aihmc.omnimorph.lua.api.sound.LuaSound;
import top.aihmc.omnimorph.permissions.Permissions;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Owns decoded model sounds independently from ordinary Figura custom sounds. */
public final class YsmAudioResources implements AutoCloseable {
    private final YsmModelRuntime runtime;
    private final Map<String, SoundBuffer> buffers = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    // Idle Lua objects may be retained by scripts; the model must not keep unused objects alive.
    private final Set<LuaSound> sounds = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private long pcmBytes;
    private boolean initialized;
    private volatile boolean closed;

    public YsmAudioResources(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public void load(Iterable<Tag> entries) throws IOException {
        if (closed || initialized) {
            throw new IOException("INVALID_STATE: model audio resources already initialized");
        }
        initialized = true;
        for (Tag value : entries) {
            if (!(value instanceof CompoundTag entry)) {
                continue;
            }
            String path = entry.getStringOr("path", "");
            String name = soundName(path);
            if (name.isBlank() || name.contains(":")) {
                recordFailure(path);
                continue;
            }
            try {
                long available = Math.min(YsmResourceLimits.DEFAULT.singleResourceBytes(),
                        YsmResourceLimits.DEFAULT.totalResourceBytes() - pcmBytes);
                var decoded = YsmAudioDecoder.decode(entry.getByteArray("data").orElse(new byte[0]), (int) available);
                SoundBuffer buffer = new SoundBuffer(decoded.samples(), decoded.format());
                SoundBuffer previous = buffers.put(name, buffer);
                pcmBytes += buffer.size();
                if (previous != null) {
                    pcmBytes -= previous.size();
                    SoundAPI.getSoundEngine().figura$releaseBuffer(previous);
                }
            } catch (InterruptedIOException cancelled) {
                throw cancelled;
            } catch (IOException | RuntimeException failure) {
                recordFailure(path);
            }
        }
    }

    private void recordFailure(String path) {
        String diagnostic = runtime.encryptedSource() ? "INVALID_AUDIO: encrypted model resource"
                : "INVALID_AUDIO: " + path;
        diagnostics.add(diagnostic);
        FiguraMod.LOGGER.warn(diagnostic);
    }

    private static String soundName(String path) {
        String normalized = path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        int extension = name.lastIndexOf('.');
        return extension >= 0 ? name.substring(0, extension) : name;
    }

    public synchronized SoundBuffer get(String name) {
        return closed || name == null || name.contains(":") ? null : buffers.get(name);
    }

    public synchronized Set<String> names() {
        return Set.copyOf(buffers.keySet());
    }

    public synchronized LuaSound createSound(String name) {
        return createSound(name, () -> true);
    }

    /** Shared PCM may outlive an individual consumer; both lifetimes guard retained Lua sound objects. */
    public synchronized LuaSound createSound(String name, BooleanSupplier consumerActive) {
        SoundBuffer buffer = get(name);
        if (buffer == null) {
            return null;
        }
        var owner = runtime.owner();
        if (owner.permissions.get(Permissions.CUSTOM_SOUNDS) != 1) {
            owner.noPermissions.add(Permissions.CUSTOM_SOUNDS);
            return null;
        }
        LuaSound sound = new LuaSound(buffer, name, owner, () -> !closed && consumerActive.getAsBoolean());
        sounds.add(sound);
        return sound;
    }

    public List<String> diagnostics() {
        return List.copyOf(diagnostics);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (LuaSound sound : new ArrayList<>(sounds)) {
            sound.invalidateModelSource();
        }
        sounds.clear();
        List<SoundBuffer> released = new ArrayList<>(buffers.values());
        buffers.clear();
        pcmBytes = 0;
        for (SoundBuffer buffer : released) {
            SoundAPI.getSoundEngine().figura$releaseBuffer(buffer);
        }
    }
}
