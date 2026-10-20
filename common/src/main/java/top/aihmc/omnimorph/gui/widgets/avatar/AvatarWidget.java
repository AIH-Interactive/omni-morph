package top.aihmc.omnimorph.gui.widgets.avatar;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.font.Emojis;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.lists.AvatarList;
import top.aihmc.omnimorph.gui.widgets.ModelResourceBadges;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.FileTexture;
import top.aihmc.omnimorph.utils.TextUtils;
import top.aihmc.omnimorph.utils.ui.UIHelper;

public class AvatarWidget extends AbstractAvatarWidget {

    private boolean outfitMenuAdded;

    public static final Identifier MISSING_ICON = new FiguraIdentifier("textures/gui/unknown_icon.png");

    public AvatarWidget(int depth, int width, LocalAvatarFetcher.AvatarPath avatar, AvatarList parent) {
        super(depth, width, 24, avatar, parent);

        AvatarWidget instance = this;
        Component description = Emojis.applyEmojis(Component.literal(avatar.getDescription()));
        this.button = new Button(getX(), getY(), width, 24, getName(), null, button -> {
            parent.selectAvatar(avatar);
        }) {
            @Override
            public void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
                super.extractContents(gui, mouseX, mouseY, delta);

                // selected border
                if (parent.isSelected(avatar))
                    UIHelper.fillOutline(gui, getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
            }

            @Override
            protected void renderText(GuiGraphicsExtractor gui, float delta) {
                // variables
                Font font = Minecraft.getInstance().font;

                int space = SPACING * depth;
                int width = this.getWidth() - 26 - space;
                int x = getX() + 2 + space;
                int y = getY() + (getHeight() - 20) / 2;

                // icon
                FileTexture texture = avatar.getIcon();
                String format = avatar.isEncryptedYsm() ? "YSM_ENCRYPTED" : avatar.isYsm() ? "YSM_PLAIN" : "FIGURA";
                Identifier icon = texture == null ? MISSING_ICON : texture.getLocation();
                UIHelper.blit(gui, x, y, 20, 20, icon);
                if (parent.showsSourceBadge()) {
                    ModelResourceBadges.drawType(gui, x, y, format);
                }

                // name
                Component parsedName = TextUtils.trimToWidthEllipsis(font, getMessage(), width, TextUtils.ELLIPSIS.copy().withStyle(getMessage().getStyle()));
                gui.text(font, parsedName, x + 22, y, UIHelper.adjustColor(-1));

                // description
                Component parsedDescription = TextUtils.trimToWidthEllipsis(font, description, width, TextUtils.ELLIPSIS.copy().withStyle(description.getStyle()));
                gui.text(font, parsedDescription, x + 22, y + font.lineHeight + 1, UIHelper.adjustColor(ChatFormatting.GRAY.getColor()));

                // tooltip
                if (parent.showsSourceBadge() || parsedName != getMessage() || parsedDescription != description) {
                    Component tooltip = instance.getName();
                    if (!description.getString().isBlank())
                        tooltip = tooltip.copy().append("\n\n").append(description);
                    if (parent.showsSourceBadge()) {
                        tooltip = ModelResourceBadges.tooltip(tooltip, false, format);
                    }
                    setTooltip(tooltip);
                }
            }

            @Override
            public boolean isMouseOver(double mouseX, double mouseY) {
                return parent.isInsideScissors(mouseX, mouseY) && super.isMouseOver(mouseX, mouseY);
            }

            @Override
            public void setHovered(boolean hovered) {
                if (!hovered && UIHelper.getContext() == context && context.isVisible())
                    hovered = true;

                super.setHovered(hovered);
            }
        };

        this.button.shouldHaveBackground(false);
        children.add(this.button);
        updateRowHeight();
    }

    public void updateRowHeight() {
        int height = parent.showsSourceBadge() ? 28 : 24;
        setHeight(height);
        button.setHeight(height);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && isMouseOver(event.x(), event.y())
                && parent.canRemoveFromOutfit() && !outfitMenuAdded) {
            context.addDivisor();
            context.addAction(FiguraText.of("gui.wardrobe.remove_outfit"), null, ignored -> {
                parent.removeFromOutfit(avatar);
                context.setVisible(false);
            });
            outfitMenuAdded = true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
