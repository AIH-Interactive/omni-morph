package top.aihmc.omnimorph.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.backend2.BackendCommands;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.lua.docs.FiguraDocsManager;
import top.aihmc.omnimorph.model.rendering.FiguraRenderer;
import top.aihmc.omnimorph.utils.FiguraClientCommandSource;
import top.aihmc.omnimorph.utils.FiguraText;

public class FiguraCommands {

    public static LiteralArgumentBuilder<FiguraClientCommandSource> getCommandRoot() {
        // root
        LiteralArgumentBuilder<FiguraClientCommandSource> root = LiteralArgumentBuilder.literal(FiguraMod.MOD_ID);

        // docs
        root.then(FiguraDocsManager.getCommand());

        // links
        root.then(LinkCommand.getCommand());

        // run
        root.then(RunCommand.getCommand());

        // load
        root.then(LoadCommand.getCommand());

        // reload
        root.then(ReloadCommand.getCommand());

        // debug
        root.then(DebugCommand.getCommand());

        // export
        root.then(ExportCommand.getCommand());

        if (FiguraMod.debugModeEnabled()) {
            // backend debug
            root.then(BackendCommands.getCommand());

            // set avatar command
            root.then(AvatarManager.getCommand());
        }

        // emoji list
        root.then(EmojiListCommand.getCommand());

        return root;
    }

    protected static Avatar checkAvatar(CommandContext<FiguraClientCommandSource> context) {
        Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
        if (avatar == null) {
            context.getSource().figura$sendError(FiguraText.of("command.no_avatar_error"));
            return null;
        }
        return avatar;
    }

    protected static FiguraLuaRuntime getRuntime(CommandContext<FiguraClientCommandSource> context) {
        Avatar avatar = checkAvatar(context);
        if (avatar == null)
            return null;
        if (avatar.luaRuntime == null || avatar.scriptError) {
            context.getSource().figura$sendError(FiguraText.of("command.no_script_error"));
            return null;
        }
        return avatar.luaRuntime;
    }

    protected static FiguraRenderer getRenderer(CommandContext<FiguraClientCommandSource> context) {
        Avatar avatar = checkAvatar(context);
        if (avatar == null)
            return null;
        if (avatar.renderer == null) {
            context.getSource().figura$sendError(FiguraText.of("command.no_renderer_error"));
            return null;
        }
        return avatar.renderer;
    }
}
