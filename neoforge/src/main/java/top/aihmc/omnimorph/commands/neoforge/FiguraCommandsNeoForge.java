package top.aihmc.omnimorph.commands.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import top.aihmc.omnimorph.neoforge.FiguraModForge;
import org.figuramc.figura.commands.FiguraCommands;
import org.figuramc.figura.utils.FiguraClientCommandSource;

@EventBusSubscriber(modid = FiguraModForge.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FiguraCommandsNeoForge {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandDispatcher<FiguraClientCommandSource> casted = (CommandDispatcher) dispatcher;
        casted.register(FiguraCommands.getCommandRoot());
    }
}
