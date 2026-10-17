package top.aihmc.omnimorph.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.utils.ColorUtils;
import top.aihmc.omnimorph.utils.FiguraClientCommandSource;
import top.aihmc.omnimorph.utils.FiguraText;

import java.net.URI;
import java.util.List;

class LinkCommand {

    private static final List<FiguraMod.Links> LINKS = List.of(FiguraMod.Links.Github);

    public static LiteralArgumentBuilder<FiguraClientCommandSource> getCommand() {
        // get links
        LiteralArgumentBuilder<FiguraClientCommandSource> links = LiteralArgumentBuilder.literal("links");
        links.executes(context -> {
            // header
            MutableComponent message = Component.empty().withStyle(ColorUtils.Colors.AWESOME_BLUE.style)
                    .append(Component.literal("•*+•* ")
                            .append(FiguraText.of())
                            .append(" Links *•+*•").withStyle(ChatFormatting.UNDERLINE))
                    .append("\n");

            // add links
            for (FiguraMod.Links link : LINKS) {
                message.append("\n");

                if (link == null)
                    continue;

                message.append(Component.literal("• [" + link.name() + "]")
                        .withStyle(link.style)
                        .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(link.url))))
                        .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(link.url)))));
            }

            FiguraMod.sendChatMessage(message);
            return 1;
        });

        return links;
    }
}
