package top.aihmc.omnimorph.render.acceleration;

/**
 * The 1.21.1 MVP keeps GPU drawing disabled. Reuse the supported contracts instead of
 * emulating the absent GpuDevice/RenderPipeline backend and reporting it as working GPU support.
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
            throw new AssertionError("Minecraft 1.21.1 MVP must not enable GPU drawing");
        }
        System.out.println("PASS 1.21.1 MVP configuration rejection, CPU numerical/layout and native fallback contracts.");
        System.out.println("N/A: GPU execution, shader-device initialization, geometry caches, fences, "
                + "pose arenas and SSBO draw submission require the deferred full GPU backend.");
    }
}
