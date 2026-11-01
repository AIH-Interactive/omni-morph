package top.aihmc.omnimorph.render.acceleration;

import java.nio.file.Files;
import java.nio.file.Path;

/** Records the unavailable device route explicitly for the 1.21.1 MVP. */
public final class AccelerationGpuRouteProbe {
    private AccelerationGpuRouteProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Provide the GPU route report path");
        }
        for (var mode : new CapabilityReport.Mode[]{CapabilityReport.Mode.AUTO, CapabilityReport.Mode.GPU}) {
            try {
                HardwareAcceleration.probe(mode);
                throw new AssertionError("Unsupported GPU route must not activate: " + mode);
            } catch (AccelerationController.ProbeFailure failure) {
                if (failure.reason != FallbackReason.UNSUPPORTED_MINECRAFT_VERSION) {
                    throw new AssertionError("Unexpected GPU rejection: " + failure.reason, failure);
                }
            }
        }
        if (AccelerationService.INSTANCE.gpuDrawEnabled()) {
            throw new AssertionError("GPU drawing must remain disabled on the 1.21.1 MVP");
        }
        Path report = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(report.getParent());
        String result = """
                status=UNSUPPORTED_MINECRAFT_VERSION
                minecraft=1.21.1
                backend=JAVA
                indexedDraws=0
                translatedPixels=NOT_RUN
                route=GpuDevice unavailable; existing Java vertex emission retained
                visual=NOT_RUN; no GPU or GUI validation claimed
                """;
        Files.writeString(report, result);
        System.out.print(result);
    }
}
