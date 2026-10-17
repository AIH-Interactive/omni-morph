package top.aihmc.omnimorph.render.acceleration;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.utils.FiguraText;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import static top.aihmc.omnimorph.render.acceleration.CapabilityReport.*;

/** Client settings/chat bridge. Model engines only use the shared numerical service. */
public final class HardwareAcceleration {
    private static final AccelerationController CONTROLLER = new AccelerationController(AccelerationService.INSTANCE,
            HardwareAcceleration::probe, report -> FiguraMod.LOGGER.info(
                    "Hardware acceleration: requested={}, state={}, effective={}, reason={}, nativeCapabilities={}",
                    report.requested(), report.state(), report.effective(), report.reason(), report.nativeCapabilities()));

    private HardwareAcceleration() {
    }

    public static void applySettings() {
        CONTROLLER.request(Boolean.TRUE.equals(Configs.HARDWARE_ACCELERATION_ENABLED.value),
                Mode.values()[Configs.HARDWARE_ACCELERATION_MODE.value]);
    }

    public static void beginFrame() {
        CONTROLLER.beginFrame();
        flushChat();
    }

    public static void retryAfterResourceReload() {
        CONTROLLER.retryAfterResourceReload();
    }

    public static CapabilityReport report() {
        return CONTROLLER.report();
    }

    public static Component statusText() {
        CapabilityReport report = report();
        return switch (report.state()) {
            case DISABLED -> FiguraText.of("config.hardware_acceleration.state.disabled");
            case PROBING -> FiguraText.of("config.hardware_acceleration.state.probing");
            case READY -> FiguraText.of("config.hardware_acceleration.state.ready", report.effective().name());
            case FALLBACK_JAVA -> FiguraText.of("config.hardware_acceleration.state.fallback", reasonText(report.reason()));
        };
    }

    public static void flushChat() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread()) {
            return;
        }
        CONTROLLER.flushChat(client.player != null && client.level != null && client.gui != null,
                report -> FiguraMod.sendChatMessage(FiguraText.of("config.hardware_acceleration.chat.fallback", reasonText(report.reason()))));
    }

    private static Component reasonText(FallbackReason reason) {
        return FiguraText.of("config.hardware_acceleration.reason." + reason.name().toLowerCase(Locale.ROOT));
    }

    static AccelerationController.Activation probe(Mode mode) throws AccelerationController.ProbeFailure {
        // H4 supplies the actual device-backed GPU probe. Never advertise SIMD-only as automatic/GPU success.
        if (mode != Mode.SIMD) {
            throw new AccelerationController.ProbeFailure(FallbackReason.GPU_NOT_READY);
        }
        try {
            NativeLibraryLoader.Loaded loaded = NativeLibraryLoader.load();
            int capabilities = loaded.capabilities();
            if ((capabilities & 6) == 0) {
                throw new AccelerationController.ProbeFailure(FallbackReason.UNSUPPORTED_CPU);
            }
            nativeSelfTest();
            return new AccelerationController.Activation(new NativeSimdBackend(),
                    (capabilities & 4) != 0 ? Effective.AVX2 : Effective.SSE2, capabilities);
        } catch (NativeLibraryLoader.LoadFailure failure) {
            throw new AccelerationController.ProbeFailure(failure.reason());
        } catch (IllegalCallerException | SecurityException failure) {
            throw new AccelerationController.ProbeFailure(FallbackReason.NATIVE_ACCESS_DENIED);
        } catch (LinkageError failure) {
            throw new AccelerationController.ProbeFailure(FallbackReason.NATIVE_LINK_FAILURE);
        } catch (IOException | RuntimeException failure) {
            throw new AccelerationController.ProbeFailure(FallbackReason.NATIVE_INITIALIZATION_FAILURE);
        }
    }

    private static void nativeSelfTest() throws AccelerationController.ProbeFailure {
        ByteBuffer vertices = ByteBuffer.allocateDirect(3 * 36).order(ByteOrder.nativeOrder());
        ByteBuffer output = ByteBuffer.allocateDirect(3 * 36).order(ByteOrder.nativeOrder());
        ByteBuffer matrices = ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
        for (int index : new int[]{0, 5, 10, 15, 16, 21, 26, 28, 33, 38}) {
            matrices.putFloat(index * 4, 1);
        }
        matrices.putFloat(12 * 4, 2);
        for (int vertex = 0; vertex < 3; vertex++) {
            vertices.putFloat(vertex * 36, vertex + 1).putFloat(vertex * 36 + 12, 1);
        }
        int status = NativeBindings.transform(vertices, 0, 36, matrices, 0, output, 0, 36, 3, -1, 0);
        if (status != 0 || output.getFloat(0) != 3 || output.getFloat(36) != 4 || output.getFloat(72) != 5) {
            throw new AccelerationController.ProbeFailure(FallbackReason.NATIVE_INITIALIZATION_FAILURE);
        }
    }
}
