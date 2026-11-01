package top.aihmc.omnimorph.render.acceleration;

/**
 * Headless configuration and numerical contracts. Actual GL execution is checked by gpuOpenGlSmokeTest.
 */
public final class GpuAccelerationSmokeTest {
    private GpuAccelerationSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        HardwareAccelerationSmokeTest.main(new String[0]);
        AccelerationContractSmokeTest.main(new String[0]);
        GpuRigidDataSmokeTest.main(new String[0]);
        GpuSubmissionSmokeTest.main(new String[0]);
        if (AccelerationService.INSTANCE.gpuDrawEnabled()) {
            throw new AssertionError("Headless contracts must leave acceleration disabled");
        }
        System.out.println("PASS headless configuration, CPU numerical/layout and native fallback contracts.");
        System.out.println("GPU execution is tested separately by gpuOpenGlSmokeTest; SSBO/whole-model paths remain disabled.");
    }
}
