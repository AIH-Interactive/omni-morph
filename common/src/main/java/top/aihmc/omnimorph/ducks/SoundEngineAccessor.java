package top.aihmc.omnimorph.ducks;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import top.aihmc.omnimorph.lua.api.sound.LuaSound;

import java.util.UUID;

public interface SoundEngineAccessor {

    void figura$addSound(LuaSound sound);
    void figura$releaseBuffer(com.mojang.blaze3d.audio.SoundBuffer buffer);
    void figura$stopSound(UUID owner, String name);
    void figura$stopAllSounds();
    ChannelAccess.ChannelHandle figura$createHandle(UUID owner, String name, Library.Pool pool);
    float figura$getVolume(SoundSource category);
    SoundBufferLibrary figura$getSoundBuffers();
    boolean figura$isPlaying(UUID owner);
    boolean figura$isEngineActive();
}
