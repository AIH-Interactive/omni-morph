package top.aihmc.omm.preview;

import org.figuramc.figura.lua.ReadOnlyLuaTable;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import top.aihmc.omm.preview.data.PreviewTags.*;

/** OMP AvatarAPI/NbtToLua semantics over the actual retained bbmodel parser data. */
final class PreviewAvatarData {
    private PreviewAvatarData() {}

    static LuaValue create(PreviewFiguraParts definition) {
        var root = new CompoundTag();
        var models = new CompoundTag();
        var children = new ListTag();
        children.addAll(definition.modelRoots);
        models.put("chld", children);
        root.put("models", models);
        var animations = new ListTag();
        animations.addAll(definition.animations);
        root.put("animations", animations);
        var avatar = new LuaTable();
        avatar.set("getNBT", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return convert(root);
            }
        });
        return new ReadOnlyLuaTable(avatar);
    }

    private static LuaValue convert(Tag tag) {
        return switch (tag) {
            case null -> LuaValue.NIL;
            case NumericTag number -> LuaValue.valueOf(number.number().doubleValue());
            case StringTag text -> LuaValue.valueOf(text.value());
            case CompoundTag compound -> {
                var table = new LuaTable();
                compound.entries().forEach((key, value) -> table.set(key, convert(value)));
                yield table;
            }
            case ListTag list -> {
                var table = new LuaTable();
                for (int index = 0; index < list.size(); index++) {
                    table.set(index + 1, convert(list.get(index)));
                }
                yield table;
            }
            case ByteArrayTag array -> {
                var table = new LuaTable();
                byte[] values = array.value();
                for (int index = 0; index < values.length; index++) {
                    table.set(index + 1, LuaValue.valueOf(values[index]));
                }
                yield table;
            }
            case IntArrayTag array -> {
                var table = new LuaTable();
                int[] values = array.value();
                for (int index = 0; index < values.length; index++) {
                    table.set(index + 1, LuaValue.valueOf(values[index]));
                }
                yield table;
            }
            default -> throw new IllegalArgumentException("Unsupported parser tag: " + tag.getClass().getName());
        };
    }
}
