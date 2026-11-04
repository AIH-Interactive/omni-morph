package top.aihmc.omnimorph.render.acceleration;

import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Set;

/** Native capability boundary only; no GL context, Vulkan device, window or GPU pixels. */
public final class GpuStorageBackendSmokeTest {
    public static void main(String[] args) throws Exception {
        var deviceField = RenderSystem.class.getDeclaredField("DEVICE");
        deviceField.setAccessible(true);
        Object previousDevice = deviceField.get(null);
        try {
            for (String backend : new String[]{"Vulkan", "OpenGL"}) {
                DeviceLimits nativeLimits = new DeviceLimits(16, 256, 16384, 64L * 1024 * 1024, 1, 8, 1);
                DeviceInfo info = new DeviceInfo("storage fixture", "fixture", "recording", backend.equals("Vulkan"),
                        backend, 0, nativeLimits, null, Set.of(), null, null);
                GpuDevice device = (GpuDevice) java.lang.reflect.Proxy.newProxyInstance(
                        GpuDevice.class.getClassLoader(), new Class<?>[]{GpuDevice.class},
                        (instance, method, arguments) -> {
                            if (method.getName().equals("getDeviceInfo")) {
                                return info;
                            }
                            throw new UnsupportedOperationException(method.toString());
                        });
                deviceField.set(null, device);
                GpuStorageSupport.Limits limits = GpuStorageSupport.probe();
                require(limits != null, backend + " storage capability must use the native device without a GL context: "
                        + GpuStorageSupport.unavailableReason());
                require(limits.offsetAlignment() > 0 && Integer.bitCount(limits.offsetAlignment()) == 1,
                        "storage arena retains a valid alignment");
                require(limits.maxBlockBytes() >= GpuRigidData.POSE_BYTES
                                && limits.maxBlockBytes() <= nativeLimits.maxMemoryAllocationSize(),
                        "storage capacity remains bounded by native allocation limits");
                require(limits.models(), "native data channel preserves model storage capability");
            }
            System.out.println("PASS storage capability boundary for Vulkan/OpenGL device info without a GL context; no GPU evidence");
        } finally {
            deviceField.set(null, previousDevice);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
