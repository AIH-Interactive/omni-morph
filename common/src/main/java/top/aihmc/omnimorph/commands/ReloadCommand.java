package top.aihmc.omnimorph.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.utils.FiguraClientCommandSource;
import top.aihmc.omnimorph.utils.FiguraText;

class ReloadCommand {

    public static LiteralArgumentBuilder<FiguraClientCommandSource> getCommand() {
        LiteralArgumentBuilder<FiguraClientCommandSource> cmd = LiteralArgumentBuilder.literal("reload");
        cmd.executes(context -> {
            AvatarManager.reloadAvatar(FiguraMod.getLocalPlayerUUID());
            FiguraToast.sendToast(FiguraText.of("toast.reload"));
            return 1;
        });
        return cmd;
    }
}
