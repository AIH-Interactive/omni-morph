package org.figuramc.figura.lua.api.sound;

import java.util.Base64;
import java.util.Set;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import top.aihmc.omm.preview.PreviewSounds;
import top.aihmc.omm.preview.PreviewVectorArguments;

@LuaWhitelist
@LuaTypeDoc(name = "SoundAPI", value = "sounds")
public class SoundAPI {
    private final PreviewSounds owner;

    public SoundAPI(PreviewSounds owner) {
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("sounds.play_sound")
    public LuaSound playSound(@LuaNotNil String id, Object x, Double y, Double z, Object w, Double t, boolean loop) {
        LuaSound sound = __index(id);
        FiguraVec3 pos;
        float volume = 1f;
        float pitch = 1f;

        if (x instanceof FiguraVec3) {
            pos = ((FiguraVec3) x).copy();
            if (y != null)
                volume = y.floatValue();
            if (z != null)
                pitch = z.floatValue();
            if (w != null) {
                if (!(w instanceof Boolean))
                    throw new LuaError("Illegal argument to playSound(): " + w);
                loop = (boolean) w;
            }
        } else if (x == null || x instanceof Number) {
            pos = PreviewVectorArguments.parseVec3("playSound", x, y, z);
            if (w != null) {
                if (!(w instanceof Double))
                    throw new LuaError("Illegal argument to playSound(): " + w);
                volume = ((Double) w).floatValue();
            }
            if (t != null)
                pitch = t.floatValue();
        } else {
            throw new LuaError("Illegal argument to playSound(): " + x);
        }

        sound.pos(pos, null, null);
        sound.volume(volume);
        sound.pitch(pitch);
        sound.loop(loop);
        sound.play();

        return sound;
    }

    @LuaWhitelist
    @LuaMethodDoc("sounds.stop_sound")
    public SoundAPI stopSound(String id) {
        owner.stopSound(id);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("sounds.new_sound")
    public SoundAPI newSound(@LuaNotNil String name, @LuaNotNil Object object) {
        byte[] bytes;
        if (object instanceof LuaTable table) {
            bytes = new byte[table.length()];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) table.get(i + 1).checkint();
        } else if (object instanceof String s) {
            bytes = Base64.getDecoder().decode(s);
        } else {
            throw new LuaError("Invalid type for newSound \"" + object.getClass().getSimpleName() + "\"");
        }

        try {
            owner.loadSound(name, bytes);
            return this;
        } catch (Exception e) {
            throw new LuaError("Failed to add custom sound \"" + name + "\"");
        }
    }

    @LuaWhitelist
    @LuaMethodDoc("sounds.is_present")
    public boolean isPresent(String id) {
        return owner.isPresent(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("sounds.get_custom_sounds")
    public Set<String> getCustomSounds() {
        return owner.customNames();
    }

    @LuaWhitelist
    public LuaSound __index(String id) {
        return new LuaSound(owner.resolve(id), id, owner);
    }

    @Override
    public String toString() {
        return "SoundAPI";
    }
}
