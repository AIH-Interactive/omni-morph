package top.aihmc.omnimorph.model.outfit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.figuramc.figura.animation.Animation;
import org.figuramc.figura.animation.AnimationPlayer;
import org.figuramc.figura.model.FiguraModelPartReader;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.file.Path;
import java.util.List;

/** Error observed through the ordinary animation reader/player and Avatar error output. */
public final class KeyframeDiagnosticSmokeTest {
    public static void main(String[] args) throws Exception {
        for (int version : new int[]{0x50, 0x40, 0}) {
            check(Path.of(args[0]), version, ")", true, 0);
        }
        check(Path.of(args[0]), 0x50, "return 7", false, -7);
        check(Path.of(args[0]), 0x40, "1 + 2", false, -3);
        check(Path.of(args[0]), 0x50, "math.sin(30)", false, -0.5);
        System.out.println("PASS animation diagnostics: actual reader/player, version hints and Lua/Molang results; display pending");
    }

    private static void check(Path path, int version, String code, boolean invalid, double expected) throws Exception {
        YsmMaterialNativeSmokeTest.replayFiguraModel(path, avatar -> {
            var animation = new Animation(avatar, "model", "walk", Animation.LoopMode.LOOP,
                    false, 2, 0, 1, 0, 0);
            avatar.animations.put(0, animation);
            var root = new CompoundTag();
            root.putString("name", "model");
            root.putByte("_v", (byte) version);
            var part = new CompoundTag();
            part.putString("name", "腕骨");
            var keyframe = new CompoundTag();
            keyframe.putFloat("time", 1.25f);
            keyframe.putString("int", "linear");
            var pre = new ListTag();
            pre.add(StringTag.valueOf(code));
            pre.add(StringTag.valueOf("0"));
            pre.add(StringTag.valueOf("0"));
            keyframe.put("pre", pre);
            var frames = new ListTag();
            frames.add(keyframe);
            var channels = new CompoundTag();
            channels.put("rot", frames);
            var attachment = new CompoundTag();
            attachment.putInt("id", 0);
            attachment.put("data", channels);
            var animations = new ListTag();
            animations.add(attachment);
            part.put("anim", animations);
            var children = new ListTag();
            children.add(part);
            root.put("chld", children);
            var parsed = FiguraModelPartReader.read(avatar, root, List.of(), false);
            var bone = parsed.children.get(0);
            require(bone.formatVersion == version && bone.copy("copy").formatVersion == version
                    && bone.newPart("child", null).formatVersion == version,
                    "format metadata survives inherited reading, copy and new parts");
            animation.playState = Animation.PlayState.PAUSED;
            AnimationPlayer.tick(animation, 1000);
            String error = avatar.errorText == null ? "" : avatar.errorText.getString();
            if (invalid) {
                require(error.contains("腕骨") && error.contains("ROTATION X") && error.contains("1.25"),
                        "animation errors identify part, time, channel and axis: " + error);
                String hint = org.figuramc.figura.utils.FiguraText.of("animation.keyframe_migration_hint").getString();
                require(error.contains(hint) == (version >= 0x50), "migration hint only applies to known v5 syntax failures");
            } else {
                require(!avatar.scriptError && Math.abs(bone.getAnimRot().x - expected) < 0.0001,
                        "valid Lua/Molang keyframes retain authored results: " + bone.getAnimRot());
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
