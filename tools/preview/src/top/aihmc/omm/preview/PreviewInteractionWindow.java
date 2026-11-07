package top.aihmc.omm.preview;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Real Windows Swing controls; only immutable snapshots cross from the rendering thread. */
final class PreviewInteractionWindow implements AutoCloseable {
    private final Consumer<PreviewInteraction.Operation> submit;
    private final List<Consumer<PreviewInteraction.Snapshot>> refreshers = new ArrayList<>();
    private JFrame frame;
    private JTabbedPane tabs;
    private Wheel wheel;
    private JPanel settings;
    private JComboBox<String> pages;
    private volatile boolean visible;
    private volatile boolean wheelTab = true;
    private volatile int selected = -1;
    private boolean refreshing;
    private long revision = Long.MIN_VALUE;
    private long pageRequest;
    private String settingsPage = "root";
    private PreviewInteraction.Snapshot snapshot;

    PreviewInteractionWindow(String title, Consumer<PreviewInteraction.Operation> submit) throws Exception {
        this.submit = submit;
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame(title + " - Model controls");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    visible = false;
                }
            });
            tabs = new JTabbedPane();
            wheel = new Wheel();
            tabs.addTab("Animation wheel", wheel);
            JPanel controls = new JPanel(new BorderLayout(8, 8));
            pages = new JComboBox<>();
            pages.addActionListener(event -> {
                if (!refreshing && pages.getSelectedItem() != null) {
                    var operation = operation("open-page");
                    operation.page = pages.getSelectedItem().toString();
                    submit.accept(operation);
                }
            });
            controls.add(pages, BorderLayout.NORTH);
            settings = new JPanel();
            settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
            controls.add(new JScrollPane(settings), BorderLayout.CENTER);
            tabs.addTab("Model settings", controls);
            tabs.addChangeListener(event -> wheelTab = tabs.getSelectedIndex() == 0);
            frame.add(tabs);
            frame.setSize(640, 700);
            frame.setMinimumSize(new Dimension(480, 500));
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            visible = true;
        });
    }

    boolean wheelEnabled() {
        return visible && wheelTab;
    }

    int selectedSlot() {
        return selected;
    }

    void toggle() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(!frame.isVisible());
            visible = frame.isVisible();
        });
    }

    void showSnapshot(PreviewInteraction.Snapshot state) {
        SwingUtilities.invokeLater(() -> update(state));
    }

    private void update(PreviewInteraction.Snapshot state) {
        snapshot = state;
        wheel.repaint();
        refreshing = true;
        try {
            boolean pageChanged = !settingsPage.equals(state.settingsPage());
            if (pageChanged || revision != state.revision()) {
                settingsPage = state.settingsPage();
                revision = state.revision();
                rebuildSettings(state);
            }
            if (state.pageRequest() != pageRequest) {
                pageRequest = state.pageRequest();
                tabs.setSelectedIndex(1);
            }
            for (var refresher : refreshers) {
                refresher.accept(state);
            }
        } finally {
            refreshing = false;
        }
    }

    private void rebuildSettings(PreviewInteraction.Snapshot state) {
        pages.removeAllItems();
        var names = new LinkedHashSet<String>();
        names.add("root");
        names.add(state.settingsPage());
        for (var control : state.controls()) {
            names.add(control.page());
        }
        names.forEach(pages::addItem);
        pages.setSelectedItem(state.settingsPage());
        settings.removeAll();
        refreshers.clear();
        for (var control : state.controls()) {
            if (!java.util.Objects.equals(control.page(), state.settingsPage())) {
                continue;
            }
            JPanel row = new JPanel(new BorderLayout(10, 5));
            row.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
            row.add(new JLabel(control.title()), BorderLayout.NORTH);
            row.setToolTipText(control.description());
            JComponent editor = editor(control);
            row.add(editor, BorderLayout.CENTER);
            if (control.type() != top.aihmc.omnimorph.avatar.control.AvatarControlType.BUTTON
                && control.type() != top.aihmc.omnimorph.avatar.control.AvatarControlType.LABEL
                && control.type() != top.aihmc.omnimorph.avatar.control.AvatarControlType.SEPARATOR) {
                JButton reset = new JButton("Reset");
                reset.addActionListener(event -> {
                    var operation = operation("reset-control");
                    operation.id = control.id();
                    submit.accept(operation);
                });
                row.add(reset, BorderLayout.EAST);
            }
            settings.add(row);
        }
        if (settings.getComponentCount() == 0) {
            settings.add(new JLabel("No controls defined for this page"));
        }
        settings.revalidate();
        settings.repaint();
    }

    private JComponent editor(PreviewInteraction.Control control) {
        return switch (control.type()) {
            case TOGGLE -> toggle(control);
            case SLIDER -> slider(control);
            case ENUM -> options(control);
            case BUTTON -> {
                JButton button = new JButton(control.targetPage() == null ? "Run" : "Open page");
                button.addActionListener(event -> {
                    var operation = operation(control.targetPage() == null ? "press-control" : "open-page");
                    operation.id = control.id();
                    operation.page = control.targetPage();
                    submit.accept(operation);
                });
                yield button;
            }
            case LABEL -> new JLabel(control.value() == null ? "" : control.value().toString());
            case SEPARATOR -> new JSeparator();
            case TEXT, COLOR, KEYBIND, NUMBER -> text(control);
        };
    }

    private JComponent toggle(PreviewInteraction.Control control) {
        JCheckBox box = new JCheckBox("Enabled", Boolean.TRUE.equals(control.value()));
        box.addActionListener(event -> set(control.id(), box.isSelected()));
        refreshers.add(state -> box.setSelected(Boolean.TRUE.equals(value(state, control.id()))));
        return box;
    }

    private JComponent slider(PreviewInteraction.Control control) {
        JSlider slider = new JSlider(0, 1000);
        slider.setToolTipText(control.min() + " .. " + control.max() + ", step " + control.step());
        slider.addChangeListener(event -> {
            if (refreshing || slider.getValueIsAdjusting()) {
                return;
            }
            double value = control.min() + (control.max() - control.min()) * slider.getValue() / 1000.0;
            if (control.step() > 0) {
                value = control.min() + Math.round((value - control.min()) / control.step()) * control.step();
            }
            set(control.id(), value);
        });
        refreshers.add(state -> {
            Object value = value(state, control.id());
            if (!slider.getValueIsAdjusting() && value instanceof Number number) {
                double span = control.max() - control.min();
                slider.setValue(span == 0 ? 0 : (int) Math.round((number.doubleValue() - control.min()) / span * 1000));
            }
        });
        return slider;
    }

    private JComponent options(PreviewInteraction.Control control) {
        JComboBox<Choice> options = new JComboBox<>();
        for (int index = 0; index < control.options().size(); index++) {
            options.addItem(new Choice(control.options().get(index), control.optionLabels().get(index)));
        }
        options.addActionListener(event -> {
            if (!refreshing && options.getSelectedItem() != null) {
                set(control.id(), ((Choice) options.getSelectedItem()).value());
            }
        });
        refreshers.add(state -> {
            if (!options.isPopupVisible()) {
                String value = String.valueOf(value(state, control.id()));
                int selected = control.options().indexOf(value);
                if (selected < 0) {
                    try {
                        selected = Math.round(Float.parseFloat(value));
                    } catch (NumberFormatException namedValue) {
                        selected = -1;
                    }
                }
                options.setSelectedIndex(selected >= 0 && selected < options.getItemCount() ? selected : -1);
            }
        });
        return options;
    }

    private record Choice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private JComponent text(PreviewInteraction.Control control) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        JTextField field = new JTextField(control.value() == null ? "" : control.value().toString());
        JButton apply = new JButton("Apply");
        Runnable commit = () -> {
            try {
                Object value = control.type() == top.aihmc.omnimorph.avatar.control.AvatarControlType.NUMBER
                    ? Double.parseDouble(field.getText())
                    : field.getText();
                set(control.id(), value);
                field.setBackground(UIManager.getColor("TextField.background"));
            } catch (NumberFormatException invalid) {
                field.setBackground(new Color(255, 210, 210));
                field.setToolTipText("Enter a valid number");
            }
        };
        apply.addActionListener(event -> commit.run());
        field.addActionListener(event -> commit.run());
        panel.add(field, BorderLayout.CENTER);
        panel.add(apply, BorderLayout.EAST);
        refreshers.add(state -> {
            if (!field.hasFocus()) {
                Object value = value(state, control.id());
                field.setText(value == null ? "" : value.toString());
            }
        });
        return panel;
    }

    private static Object value(PreviewInteraction.Snapshot state, String id) {
        return state.controls().stream().filter(control -> control.id().equals(id)).findFirst().orElseThrow().value();
    }

    private void set(String id, Object value) {
        if (refreshing) {
            return;
        }
        var operation = operation("set-control");
        operation.id = id;
        operation.value = PreviewConfig.JSON.toJsonTree(value);
        submit.accept(operation);
    }

    private static PreviewInteraction.Operation operation(String type) {
        var operation = new PreviewInteraction.Operation();
        operation.type = type;
        return operation;
    }

    private final class Wheel extends JPanel {
        private static final long serialVersionUID = 1;

        Wheel() {
            setBackground(new Color(24, 29, 35));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    selected = hit(event.getX(), event.getY());
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    selected = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    int slot = hit(event.getX(), event.getY());
                    if (slot >= 0) {
                        var operation = operation("wheel-click");
                        operation.slot = slot + 1;
                        operation.right = SwingUtilities.isRightMouseButton(event);
                        submit.accept(operation);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    var operation = operation("wheel-release");
                    operation.slot = hit(event.getX(), event.getY()) + 1;
                    submit.accept(operation);
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    var operation = operation("wheel-scroll");
                    operation.slot = hit(event.getX(), event.getY()) + 1;
                    operation.delta = -event.getPreciseWheelRotation();
                    submit.accept(operation);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        private double radius() {
            return Math.min(getWidth(), getHeight() - 100) * 0.43;
        }

        private int hit(int x, int y) {
            double dx = x - getWidth() / 2.0, dy = y - getHeight() / 2.0;
            double distance = Math.hypot(dx, dy);
            if (distance < 55 || distance > radius()) {
                return -1;
            }
            double angle = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / 8;
            return (int) Math.floor((angle + 2 * Math.PI) % (2 * Math.PI) / (Math.PI / 4));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D canvas = (Graphics2D) graphics.create();
            try {
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setFont(new Font(Font.DIALOG, Font.PLAIN, 15));
                canvas.setColor(Color.WHITE);
                canvas.drawString("Left click: action/toggle   Right click: alternate   Scroll: action/page", 16, 28);
                if (snapshot == null) {
                    canvas.drawString("Loading model actions...", 20, 60);
                    return;
                }
                double radius = radius(), cx = getWidth() / 2.0, cy = getHeight() / 2.0;
                for (int index = 0; index < 8; index++) {
                    var slot = snapshot.slots().get(index);
                    canvas.setColor(new Color(index == selected ? slot.hoverRgb() : slot.rgb()));
                    canvas.fill(new Arc2D.Double(
                        cx - radius, cy - radius, radius * 2, radius * 2, 111.5 - index * 45, -43, Arc2D.PIE));
                    double angle = -Math.PI / 2 + index * Math.PI / 4;
                    int x = (int) (cx + Math.cos(angle) * radius * 0.68);
                    int y = (int) (cy + Math.sin(angle) * radius * 0.68);
                    canvas.setColor(Color.WHITE);
                    String title = (index + 1) + ". " + (slot.present() ? slot.title() : "Empty");
                    centered(canvas, title, x, y);
                }
                canvas.setColor(getBackground());
                canvas.fillOval((int) cx - 55, (int) cy - 55, 110, 110);
                canvas.setColor(Color.WHITE);
                centered(canvas, snapshot.wheelPage() == null ? "Wheel" : snapshot.wheelPage(), (int) cx, (int) cy - 4);
                centered(canvas, snapshot.group() + " / " + snapshot.groups(), (int) cx, (int) cy + 18);
                if (selected >= 0) {
                    var slot = snapshot.slots().get(selected);
                    centered(canvas, slot.title(), getWidth() / 2, getHeight() - 46);
                    if (slot.item() != null) {
                        centered(canvas, slot.item(), getWidth() / 2, getHeight() - 24);
                    }
                }
            } finally {
                canvas.dispose();
            }
        }

        private void centered(Graphics2D canvas, String text, int x, int y) {
            canvas.drawString(text, x - canvas.getFontMetrics().stringWidth(text) / 2, y);
        }
    }

    @Override
    public void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.dispose();
            visible = false;
        });
    }
}
