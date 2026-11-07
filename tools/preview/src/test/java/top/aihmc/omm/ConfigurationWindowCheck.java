package top.aihmc.omm;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Robot;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Local-only check of the actual visible configuration dialog. */
public final class ConfigurationWindowCheck {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        Path config = output.resolve("dialog-config.json");
        Files.writeString(config, "{}");
        try {
            var read = new FutureTask<>(() -> RuntimeConfiguration.read(config));
            new Thread(read, "configuration-read").start();
            Dialog dialog = awaitDialog();
            Robot robot = new Robot();
            robot.waitForIdle();
            Thread.sleep(700);
            ImageIO.write(robot.createScreenCapture(dialog.getBounds()), "png", output.resolve("configuration.png").toFile());
            SwingUtilities.invokeAndWait(() -> {
                List<JTextField> fields = descendants(dialog, JTextField.class);
                if (fields.size() != 4) {
                    throw new AssertionError("Expected four configuration fields");
                }
                fields.forEach(field -> field.setText(output.toString()));
                descendants(dialog, JButton.class).getFirst().doClick();
            });
            var saved = read.get(10, TimeUnit.SECONDS);
            if (!saved.get("minecraftDirectory").getAsString().equals(output.toString())
                || !RuntimeConfiguration.json(config).equals(saved)) {
                throw new AssertionError("Visible dialog did not persist the submitted configuration");
            }
            System.out.println("PASS visible configuration dialog saved all fields; screenshot=" + output.resolve("configuration.png"));

            Files.writeString(config, "{}");
            var cancelled = new FutureTask<>(() -> RuntimeConfiguration.read(config));
            new Thread(cancelled, "configuration-cancel").start();
            Dialog cancelDialog = awaitDialog();
            SwingUtilities.invokeAndWait(() -> descendants(cancelDialog, JButton.class).getLast().doClick());
            try {
                cancelled.get(10, TimeUnit.SECONDS);
                throw new AssertionError("Cancel must not return a configuration");
            } catch (ExecutionException expected) {
                Throwable cause = expected;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (!"Minecraft configuration cancelled".equals(cause.getMessage())) {
                    throw expected;
                }
            }
            if (!Files.readString(config).equals("{}")) {
                throw new AssertionError("Cancel changed the configuration file");
            }
            System.out.println("PASS cancellation exits configuration without writing settings");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    window.dispose();
                }
            });
        }
    }

    private static Dialog awaitDialog() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Dialog[] found = new Dialog[1];
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof Dialog dialog && dialog.isShowing()) {
                        found[0] = dialog;
                    }
                }
            });
            if (found[0] != null) {
                return found[0];
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Configuration dialog did not become visible");
    }

    private static <T> List<T> descendants(Container parent, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) {
                matches.add(type.cast(child));
            }
            if (child instanceof Container container) {
                matches.addAll(descendants(container, type));
            }
        }
        return matches;
    }
}
