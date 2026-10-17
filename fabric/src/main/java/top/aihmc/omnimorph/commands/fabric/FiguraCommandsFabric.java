package top.aihmc.omnimorph.commands.fabric;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import top.aihmc.omnimorph.commands.FiguraCommands;
import top.aihmc.omnimorph.utils.FiguraClientCommandSource;

public class FiguraCommandsFabric {

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        // register
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            CommandDispatcher<FiguraClientCommandSource> casted = (CommandDispatcher) dispatcher;
            casted.register(FiguraCommands.getCommandRoot());
        });
    }
}
