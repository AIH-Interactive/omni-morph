package org.figuramc.figura.lua.api.sound;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.luaj.vm2.LuaError;
import top.aihmc.omm.preview.PreviewAudio;
import top.aihmc.omm.preview.PreviewSounds;
import top.aihmc.omm.preview.PreviewVectorArguments;

@LuaWhitelist
@LuaTypeDoc(name = "Sound", value = "sound")
public class LuaSound {
    private final PreviewSounds owner;
    private final String id;
    private final PreviewSounds.Clip clip;
    private PreviewAudio.Voice handle;
    private FiguraVec3 pos = FiguraVec3.of();
    private float pitch = 1f;
    private float volume = 1f;
    private float attenuation = 1f;
    private boolean loop;
    private String subtitle;

    public LuaSound(PreviewSounds.Clip clip, String id, PreviewSounds owner) {
        this.clip = clip;
        this.id = id;
        this.owner = owner;
        this.subtitle = clip.subtitle();
    }

    public String getId() {
        return id;
    }

    public PreviewAudio.Voice getHandle() {
        return handle;
    }

    /** Internal YSM event volume is not subject to the ordinary Lua volume ceiling. */
    public LuaSound setEngineVolume(float volume) {
        this.volume = volume;
        if (handle != null)
            handle.volume(volume);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.play")
    public LuaSound play() {
        if (handle != null && handle.playing())
            return this;
        if (handle != null && handle.stopped()) {
            handle.close();
            handle = null;
        }
        if (handle == null) {
            handle = owner.play(this, clip);
            if (handle == null)
                return this;
            handle.position(pos.x, pos.y, pos.z);
            handle.volume(volume);
            handle.pitch(pitch);
            handle.attenuation(attenuation);
            handle.loop(loop);
        }
        handle.play();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.is_playing")
    public boolean isPlaying() {
        return handle != null && handle.playing();
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.pause")
    public LuaSound pause() {
        if (handle != null)
            handle.pause();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.stop")
    public LuaSound stop() {
        if (handle != null)
            handle.close();
        handle = null;
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.get_pos")
    public FiguraVec3 getPos() {
        return pos;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.set_pos")
    public LuaSound setPos(Object x, Double y, Double z) {
        this.pos = PreviewVectorArguments.parseVec3("setPos", x, y, z);
        if (handle != null)
            handle.position(pos.x, pos.y, pos.z);
        return this;
    }

    @LuaWhitelist
    public LuaSound pos(Object x, Double y, Double z) {
        return setPos(x, y, z);
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.get_volume")
    public float getVolume() {
        return volume;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.set_volume")
    public LuaSound setVolume(float volume) {
        this.volume = Math.min(volume, 1);
        if (handle != null)
            handle.volume(this.volume);
        return this;
    }

    @LuaWhitelist
    public LuaSound volume(float volume) {
        return setVolume(volume);
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.get_attenuation")
    public float getAttenuation() {
        return attenuation;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.set_attenuation")
    public LuaSound setAttenuation(float attenuation) {
        this.attenuation = Math.max(attenuation, 1);
        if (handle != null)
            handle.attenuation(this.attenuation);
        return this;
    }

    @LuaWhitelist
    public LuaSound attenuation(float attenuation) {
        return setAttenuation(attenuation);
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.get_pitch")
    public float getPitch() {
        return pitch;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.set_pitch")
    public LuaSound setPitch(float pitch) {
        this.pitch = Math.max(pitch, 0);
        if (handle != null)
            handle.pitch(this.pitch);
        return this;
    }

    @LuaWhitelist
    public LuaSound pitch(float pitch) {
        return setPitch(pitch);
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.is_looping")
    public boolean isLooping() {
        return loop;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.set_loop")
    public LuaSound setLoop(boolean loop) {
        this.loop = loop;
        if (handle != null)
            handle.loop(this.loop);
        return this;
    }

    @LuaWhitelist
    public LuaSound loop(boolean loop) {
        return setLoop(loop);
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.get_subtitle")
    public String getSubtitle() {
        return subtitle;
    }

    @LuaWhitelist
    @LuaMethodDoc("sound.set_subtitle")
    public LuaSound setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        if (subtitle != null && subtitle.length() > 48)
            throw new LuaError("Text length exceeded limit of 48 characters");
        return this;
    }

    @LuaWhitelist
    public LuaSound subtitle(String subtitle) {
        return setSubtitle(subtitle);
    }

    @Override
    public String toString() {
        return id + " (Sound)";
    }
}
