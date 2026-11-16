package top.aihmc.omnimorph.model.outfit;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.figuramc.figura.animation.Animation;
import org.figuramc.figura.animation.Interpolation;
import org.figuramc.figura.animation.Keyframe;
import org.figuramc.figura.animation.TransformType;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.PartCustomization;
import org.figuramc.figura.permissions.Permissions;
import org.joml.Matrix4f;

/** Generic native sampling and actual nested Lua execution, independent of converter data. */
public final class AnimationSampleSmokeTest {
    public static void verify() {
        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "Generic pose sampling");
        try {
            avatar.load(new CompoundTag());
            var runtime = new FiguraLuaRuntime(avatar, Map.of());
            require(runtime.init(new ListTag()), "Initialize actual Lua VM");
            var customization = new PartCustomization();
            customization.setPos(3, 4, 5);
            customization.setPivot(1, 2, 3);
            customization.setRot(10, 20, 30);
            customization.setScale(1.2, .8, 2);
            customization.setAnimPos(100, 200, 300);
            var part = new FiguraModelPart(avatar, "sampled", customization, Map.of(), List.of());
            var clip = new Animation(avatar, "generic", "motion", Animation.LoopMode.LOOP,
                    false, 1, 0, .5f, 0, 0);
            clip.addAnimation(part, new Animation.AnimationChannel(TransformType.POSITION,
                    key(avatar, clip, 0, FiguraVec3.of(), null),
                    key(avatar, clip, 1, FiguraVec3.of(4, 6, 8), null)));
            clip.addAnimation(part, new Animation.AnimationChannel(TransformType.ROTATION,
                    key(avatar, clip, 0, FiguraVec3.of(), null),
                    key(avatar, clip, 1, FiguraVec3.of(0, 0, 90), null)));
            clip.addAnimation(part, new Animation.AnimationChannel(TransformType.SCALE,
                    key(avatar, clip, 0, FiguraVec3.of(1, 1, 1), null),
                    key(avatar, clip, 1, FiguraVec3.of(2, 3, 4), null)));
            clip.newCode(0, "error('sample must not dispatch timeline code')");
            var saved = new PartCustomization();
            customization.copyRenderPoseTo(saved);
            var actual = clip.sample(part, .25).toMatrix4f();
            var expected = new Matrix4f().translation(3.5f, 6.75f, 9)
                    .rotateZYX(rad(41.25f), rad(20), rad(10))
                    .scale(1.2f * 1.125f, .8f * 1.25f, 2 * 1.375f).translate(-1, -2, -3);
            require(actual.equals(expected, .00001f), "Independent JOML pivot/TRS/blend expectation");
            require(customization.matchesRenderPose(saved) && clip.getTime() == 0 && clip.isStopped(),
                    "Sampling cannot replace live channels or start playback");
            var unrelated = new FiguraModelPart(avatar, "unbound", new PartCustomization(), Map.of(), List.of());
            require(clip.sample(unrelated, 0) == null, "Unbound parts return nil");
            try {
                clip.sample(part, Double.NaN);
                throw new AssertionError("Non-finite sample time was accepted");
            } catch (org.luaj.vm2.LuaError expectedError) {
                require(expectedError.getMessage().contains("finite"), "Invalid sample time is explicit");
            }

            var luaClip = new Animation(avatar, "generic", "lua", Animation.LoopMode.HOLD,
                    false, 1, 0, 1, 0, 0);
            luaClip.addAnimation(part, new Animation.AnimationChannel(TransformType.POSITION,
                    key(avatar, luaClip, 0, null, new String[]{"return 7", "0", "0"})));
            runtime.setGlobal("sample_clip", luaClip);
            runtime.setGlobal("sample_part", part);
            avatar.tick.reset(4096);
            var result = runtime.run(runtime.load("sample inherits caller accounting",
                    "local before=avatar:getCurrentInstructions(); "
                            + "for i=1,12 do assert(sample_clip:sample(sample_part,.5)) end; "
                            + "return avatar:getCurrentInstructions()-before"), avatar.tick);
            require(result != null && !avatar.scriptError && result.arg1().checkint() > 40,
                    "Lua keyframes execute inside the caller's monotonically increasing VM counter");
            var runaway = new Animation(avatar, "generic", "bounded", Animation.LoopMode.HOLD,
                    false, 1, 0, 1, 0, 0);
            runaway.addAnimation(part, new Animation.AnimationChannel(TransformType.POSITION,
                    key(avatar, runaway, 0, null, new String[]{"while true do end", "0", "0"})));
            runtime.setGlobal("sample_clip", runaway);
            avatar.tick.reset(4096);
            runtime.run(runtime.load("sample cannot reset the caller budget",
                    "sample_clip:sample(sample_part,.5)"), avatar.tick);
            require(avatar.scriptError && avatar.noPermissions.contains(Permissions.INIT_INST),
                    "A runaway sampled Lua keyframe is stopped by the actual caller VM guard");
        } finally {
            avatar.clean();
        }
        System.out.println("OMM_NATIVE_ANIMATION_SAMPLE_OK matrix+blend+isolation+Lua_budget");
    }

    private static Keyframe key(Avatar owner, Animation clip, float time, FiguraVec3 vector, String[] code) {
        var value = Pair.of(vector, code);
        return new Keyframe(owner, clip, time, Interpolation.LINEAR, value, value,
                FiguraVec3.of(), FiguraVec3.of(), FiguraVec3.of(), FiguraVec3.of());
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
