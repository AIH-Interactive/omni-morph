package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;
import top.aihmc.omnimorph.gui.widgets.navigation.SideNavigationWidget;

import java.util.ArrayList;
import java.util.List;

/** Records actual GUI calls and resources; it does not assert rendered pixels. */
final class NavigationRenderingSmokeTest {
    static void run() throws Exception {
        var minecraft = Minecraft.getInstance();
        var fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        Object previousFont = fontField.get(minecraft);
        fontField.set(minecraft, new Font(null, false) {
            @Override
            public int width(FormattedText text) {
                return text.getString().length() * 6;
            }
        });
        var failures = new ArrayList<String>();
        try {
            for (String name : List.of("LOGO_ICON", "PANEL_ICONS", "LIBRARY_ICON")) {
                var field = SideNavigationWidget.class.getDeclaredField(name);
                field.setAccessible(true);
                var location = (ResourceLocation) field.get(null);
                String path = "/assets/" + location.getNamespace() + "/" + location.getPath();
                try (var stream = NavigationRenderingSmokeTest.class.getResourceAsStream(path)) {
                    if (stream == null) {
                        failures.add("missing navigation texture " + path);
                    }
                }
            }

            var graphics = new RecordingGraphics();
            var icon = SideNavigationWidget.class.getDeclaredMethod("renderIcon", GuiGraphics.class,
                    NavigationRoute.class, int.class, int.class);
            icon.setAccessible(true);
            for (var route : List.of(NavigationRoute.WARDROBE, NavigationRoute.LIBRARY,
                    NavigationRoute.PERMISSIONS, NavigationRoute.SETTINGS, NavigationRoute.HELP)) {
                graphics.blits.clear();
                icon.invoke(null, graphics, route, 6, 30);
                if (graphics.blits.size() != 1 || graphics.blits.getFirst().width() != 16
                        || graphics.blits.getFirst().height() != 16) {
                    failures.add("wrong native blit dimensions for " + route + ": " + graphics.blits);
                }
            }

            var screen = new NavigationScreen();
            screen.initialize();
            graphics.fills.clear();
            graphics.flushDepths.clear();
            screen.render(graphics, 300, 200, 0);
            if (graphics.fills.isEmpty() || graphics.fills.stream().anyMatch(z -> z <= 250 || z >= 500)) {
                failures.add("navigation must be above preview/text depth and below context menus: " + graphics.fills);
            }
            if (graphics.flushDepths.stream().noneMatch(z -> z > 250 && z < 500)) {
                failures.add("navigation must flush before its foreground pose is restored");
            }
            if (graphics.pose().last().pose().m32() != 0) {
                failures.add("navigation must restore the caller pose");
            }
            if (!failures.isEmpty()) {
                throw new AssertionError(String.join("; ", failures));
            }
            System.out.println("PASS navigation rendering: packaged textures, actual native blit dimensions, foreground depth/flush and pose restore; pixels unverified");
        } finally {
            fontField.set(minecraft, previousFont);
        }
    }

    private record Blit(int width, int height) {
    }

    private static final class RecordingGraphics extends GuiGraphics {
        private final List<Blit> blits = new ArrayList<>();
        private final List<Float> fills = new ArrayList<>();
        private final List<Float> flushDepths = new ArrayList<>();

        private RecordingGraphics() {
            super(Minecraft.getInstance(), null);
        }

        @Override
        public void blit(ResourceLocation texture, int x, int y, int width, int height,
                         float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
            blits.add(new Blit(width, height));
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(pose().last().pose().m32());
        }

        @Override
        public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
            return 0;
        }

        @Override
        public void flush() {
            flushDepths.add(pose().last().pose().m32());
        }
    }

    private static final class NavigationScreen extends AbstractPanelScreen {
        private NavigationScreen() {
            super(null, Component.empty());
        }

        private void initialize() {
            minecraft = Minecraft.getInstance();
            width = 640;
            height = 360;
            super.init();
        }

        @Override
        protected boolean usesSideNavigation() {
            return true;
        }

        @Override
        public List<Renderable> renderables() {
            return List.of();
        }

        @Override
        public void renderBackground(GuiGraphics gui, float delta) {
        }

        @Override
        public void renderBackground(GuiGraphics gui, int x, int y, float delta) {
            // World/menu backgrounds are outside the navigation rendering regression.
        }

        @Override
        public void renderOverlays(GuiGraphics gui, int x, int y, float delta) {
        }
    }
}
