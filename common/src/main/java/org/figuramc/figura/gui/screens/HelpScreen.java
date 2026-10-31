package org.figuramc.figura.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.feline.CatSoundVariants;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.IconButton;
import org.figuramc.figura.gui.widgets.Label;
import org.figuramc.figura.gui.widgets.ParticleWidget;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.TextUtils;
import org.figuramc.figura.utils.ui.UIHelper;

public class HelpScreen extends AbstractPanelScreen {

    public static final ResourceLocation ICONS = FiguraIdentifier.of("textures/gui/help_icons.png");
    public static final String LUA_VERSION = "5.2 - OmniMorph";

    public HelpScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.help"));
    }

    @Override
    protected void init() {
        super.init();

        int lineHeight = this.minecraft.font.lineHeight;
        int middle = width / 2;
        int labelWidth = Math.min(width - 8, 420);
        int documentWidth = Math.min(width - 16, 160);
        int linkWidth = Math.min(128, (width - 24) / 3);
        int linkStart = middle - (linkWidth * 3 + 8) / 2;
        int y = 28;
        Style color = FiguraMod.getAccentColor();

        addRenderableWidget(new Label(FiguraText.of("gui.help.title").withStyle(color),
                middle, y, TextUtils.Alignment.CENTER));
        addRenderableWidget(new Title(FiguraText.of("gui.help.docs").withStyle(color),
                middle, y += lineHeight + 6, labelWidth));
        addLink(middle - documentWidth / 2, y += lineHeight + 4, documentWidth,
                20, 0, "ingame_docs", FiguraMod.Links.Docs);
        addLink(middle - documentWidth / 2, y += 28, documentWidth,
                0, 0, "lua_manual", FiguraMod.Links.LuaManual);
        addLink(middle - documentWidth / 2, y += 28, documentWidth,
                40, 0, "molang_guide", FiguraMod.Links.MolangGuide);

        addRenderableWidget(new Title(FiguraText.of("gui.help.links").withStyle(color),
                middle, y += 28, labelWidth));
        y += lineHeight + 4;
        addLink(linkStart, y, linkWidth, 20, 20, "github", FiguraMod.Links.Github);
        addLink(linkStart + linkWidth + 4, y, linkWidth, 40, 0, "credits", FiguraMod.Links.Credits);
        addLink(linkStart + (linkWidth + 4) * 2, y, linkWidth, 20, 20, "upstream", FiguraMod.Links.Upstream);

        addRenderableWidget(new Title(FiguraText.of("gui.help.about").withStyle(color),
                middle, y += 28, labelWidth));
        addRenderableWidget(new Label(FiguraText.of("gui.help.lua_version",
                Component.literal(LUA_VERSION).withStyle(color)),
                middle, y += lineHeight + 4, TextUtils.Alignment.CENTER));
        addRenderableWidget(new Label(FiguraText.of("gui.help.omnimorph_version",
                Component.literal(FiguraMod.DISPLAY_VERSION).withStyle(color)),
                middle, y += lineHeight + 4, TextUtils.Alignment.CENTER));
        addRenderableWidget(new Button(middle - 60, height - 24, 120, 20,
                FiguraText.of("gui.done"), null, button -> onClose()));
    }

    private void addLink(int x, int y, int buttonWidth, int u, int v, String label, FiguraMod.Links link) {
        addRenderableWidget(new IconButton(x, y, buttonWidth, 24, u, v, 20, ICONS, 60, 40,
                FiguraText.of("gui.help." + label), null, button -> UIHelper.openURL(link.url).run()) {
            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (link == FiguraMod.Links.Github && Configs.EASTER_EGGS.value
                        && isHoveredOrFocused() && isMouseOver(event.x(), event.y()) && event.button() == 1) {
                    int size = getTextureSize();
                    int particleX = (int) (Math.random() * size) + getX() + 2;
                    int particleY = (int) (Math.random() * size) + getY() + 2;
                    addRenderableOnly(new ParticleWidget(particleX, particleY, ParticleTypes.HEART));
                    boolean purr = Math.random() < 0.95;
                    var sounds = SoundEvents.CAT_SOUNDS.get(CatSoundVariants.SoundSet.CLASSIC).adultSounds();
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                            purr ? sounds.purrSound() : sounds.ambientSound(), 1f));
                    return false;
                }
                return super.mouseClicked(event, doubleClick);
            }
        });
    }

    private static class Title extends Label {

        private final int width;

        public Title(Object text, int x, int y, int width) {
            super(text, x, y, TextUtils.Alignment.CENTER);
            this.width = width;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
            int x = getRawX();
            int y = getRawY();

            // lines
            int y0 = y + getHeight() / 2;
            int y1 = y0 + 1;

            int x0 = x - width / 2;
            int x1 = x - getWidth() / 2 - 4;
            gui.fill(x0, y0, x1, y1, 0xFFFFFFFF);

            x0 = x + getWidth() / 2 + 4;
            x1 = x + width / 2;
            gui.fill(x0, y0, x1, y1, 0xFFFFFFFF);

            // text
            super.extractRenderState(gui, mouseX, mouseY, delta);
        }
    }
}
