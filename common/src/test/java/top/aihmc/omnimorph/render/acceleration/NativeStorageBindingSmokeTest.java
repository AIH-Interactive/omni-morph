package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Production storage/pass contracts with recorded GPU commands; never graphics execution. */
public final class NativeStorageBindingSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        var device = new NativeGpuFenceSmokeTest.RecordingDevice();
        var activeDevice = RenderSystem.class.getDeclaredField("DEVICE");
        activeDevice.setAccessible(true);
        Object previousDevice = activeDevice.get(null);
        boolean previousStorage = AccelerationOptions.storageBlocks();
        var adapter = new MinecraftGpuDeviceAdapter(device.api, entitySources());
        try {
            activeDevice.set(null, device.api);
            AccelerationOptions.setStorageBlocks(true);
            adapter.selfTest();
            var original = RenderPipelines.ENTITY_CUTOUT_CULL;
            var material = adapter.compileStorage(original, false);
            require(material.accelerated().getBindGroupLayouts().stream()
                            .flatMap(layout -> layout.uniforms().stream())
                            .anyMatch(uniform -> uniform.name().equals("OmniMorphData")
                                    && uniform.gpuFormat() == GpuFormat.RGBA32_UINT),
                    "production pipeline declares the raw 16-byte texel format");
            try (var mesh = adapter.uploadGeometry(ByteBuffer.allocateDirect(64), ByteBuffer.allocateDirect(24));
                 var storage = adapter.createStorage(65536);
                 var dynamic = device.api.createBuffer(() -> "dynamic fixture", GpuBuffer.USAGE_UNIFORM, 64)) {
                var buffer = ((MinecraftGpuDeviceAdapter.BufferStorage) storage).buffer();
                require((buffer.usage() & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0
                                && (buffer.usage() & GpuBuffer.USAGE_UNIFORM) != 0,
                        "the same arena supports the native data channel and offset UBO");
                var pose = new GpuPoseArena.Slice(256, 208);
                var offsets = new GpuPoseArena.Slice(512, 16);
                ByteBuffer bytes = ByteBuffer.allocateDirect(528).order(ByteOrder.nativeOrder());
                bytes.putFloat(256 + 48 + 48, 7);
                bytes.position(512);
                GpuStorageSupport.writeOffsets(bytes, pose, null, null);
                bytes.flip();
                adapter.writeStorage(storage, 0, bytes);
                var storedPose = new GpuDeviceAdapter.StoragePose(pose, null, null, offsets);
                storedPose.storage = storage;
                var pass = device.openPass();
                try (var callerOwnedPass = pass.api) {
                    var context = new GpuDrawContext(callerOwnedPass, original, List.of(),
                            dynamic.slice(), false, 0, 0, 0, 0);
                    var issued = new AtomicBoolean();
                    adapter.draw(material, mesh, storedPose, context, 3, 12, () -> issued.set(true));
                    var data = pass.uniforms.get("OmniMorphData");
                    var metadata = pass.uniforms.get("OmniMorphOffsets");
                    require(issued.get() && !pass.closed && pass.firstIndex == 3 && pass.indexCount == 12,
                            "adapter preserves the index range and leaves its caller's pass open");
                    require(data.buffer() == buffer && data.offset() == 0 && data.length() == 65536,
                            "both backends receive the whole arena with zero texel-buffer offset");
                    require(metadata.buffer() == buffer && metadata.offset() == 512 && metadata.length() == 16
                                    && ((NativeGpuFenceSmokeTest.RecordingBuffer) buffer).bytes.getInt(512) == 16,
                            "offset UBO selects the authored pose at texel 16 in that same arena");
                    require(!buffer.isClosed(), "draw completion does not retire the frame-owned arena");
                    device.failBind = true;
                    issued.set(false);
                    try {
                        adapter.draw(material, mesh, storedPose, context, 0, 6, () -> issued.set(true));
                        throw new AssertionError("injected native binding failure must propagate");
                    } catch (IllegalStateException expected) {
                        require(expected.getMessage().equals("native binding failure") && !issued.get()
                                        && !pass.closed && !buffer.isClosed(),
                                "pre-draw failure remains unissued for fallback and preserves caller ownership");
                    }
                    device.failBind = false;
                    device.failDraw = true;
                    issued.set(false);
                    try {
                        adapter.draw(material, mesh, storedPose, context, 0, 6, () -> issued.set(true));
                        throw new AssertionError("injected native draw failure must propagate");
                    } catch (IllegalStateException expected) {
                        require(expected.getMessage().equals("native draw failure") && issued.get()
                                        && !pass.closed && !buffer.isClosed(),
                                "issued failure propagates without closing caller-owned pass or arena");
                    }
                }
                require(pass.closed, "the caller closes its pass after success and failures");
            }
            require(device.buffers.stream().allMatch(GpuBuffer::isClosed),
                    "owning scopes close every diagnostic, mesh, arena and dynamic buffer");
        } finally {
            adapter.closePrograms();
            AccelerationOptions.setStorageBlocks(previousStorage);
            activeDevice.set(null, previousDevice);
        }
        System.out.println("PASS storage adapter: whole arena, offset UBO, index range, issued failures, borrowed pass; no GPU execution");
    }

    private static ShaderSource entitySources() {
        return new ShaderSource() {
            @Override
            public String getShader(Identifier id, ShaderType type) {
                String suffix = type == ShaderType.VERTEX ? ".vsh" : ".fsh";
                String path = "/assets/" + id.getNamespace() + "/shaders/" + id.getPath() + suffix;
                try (var stream = NativeStorageBindingSmokeTest.class.getResourceAsStream(path)) {
                    require(stream != null, "resolved Minecraft shader is available: " + path);
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }

            @Override public CachedIncludeSource getInclude(Identifier id) { return null; }
            @Override public void close() { }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
