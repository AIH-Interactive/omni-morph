package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.joml.Vector4fc;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Exercises the production Minecraft adapter with native API command recorders, never a graphics backend. */
public final class NativeStorageBindingSmokeTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        var device = new RecordingDevice();
        var adapter = new MinecraftGpuDeviceAdapter(device, (id, type) -> null);
        RenderPipeline original = RenderPipelines.ENTITY_CUTOUT_CULL;
        var pipeline = GpuShaderProgram.pipeline(original, Identifier.parse("omni-morph:native-storage-binding"), false);
        require(pipeline.getBindGroupLayouts().stream().flatMap(layout -> layout.getUniforms().stream())
                        .anyMatch(uniform -> uniform.name().equals("OmniMorphData")
                                && uniform.gpuFormat() == GpuFormat.RGBA32_UINT),
                "production pipeline declares the raw 16-byte texel format");
        var material = new GpuDeviceAdapter.Material(original, pipeline);
        var color = new GpuTexture(1, "recorded target", GpuFormat.RGBA8_UNORM, 4, 4, 1, 1) {
            @Override public void close() { }
            @Override public boolean isClosed() { return false; }
        };
        var view = new GpuTextureView(color, 0, 1) {
            @Override public void close() { }
            @Override public boolean isClosed() { return false; }
        };
        try (var mesh = adapter.uploadGeometry(ByteBuffer.allocateDirect(64), ByteBuffer.allocateDirect(24));
             var storage = adapter.createStorage(65536);
             var dynamic = new RecordingBuffer(GpuBuffer.USAGE_UNIFORM, 64)) {
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
            var context = new GpuDrawContext(view, null, List.of(), dynamic.slice(), false, 0, 0, 0, 0);
            var issued = new AtomicBoolean();
            adapter.draw(material, mesh, storedPose, context, 3, 12, () -> issued.set(true));
            RecordingPass pass = device.lastPass;
            GpuBufferSlice data = pass.uniforms.get("OmniMorphData");
            GpuBufferSlice metadata = pass.uniforms.get("OmniMorphOffsets");
            require(issued.get() && pass.closed && pass.firstIndex == 3 && pass.indexCount == 12,
                    "native adapter marks issue and preserves the requested index range, then closes its pass");
            require(data.buffer() == buffer && data.offset() == 0 && data.length() == 65536,
                    "both backends receive the entire arena with zero texel-buffer offset");
            require(metadata.buffer() == buffer && metadata.offset() == 512 && metadata.length() == 16
                            && ((RecordingBuffer) buffer).bytes.getInt(512) == 16,
                    "the offset UBO selects the authored pose at texel 16 within that same arena");
            require(!buffer.isClosed(), "closing a draw pass does not retire its frame-owned arena");

            device.failBind = true;
            issued.set(false);
            try {
                adapter.draw(material, mesh, storedPose, context, 0, 6, () -> issued.set(true));
                throw new AssertionError("injected native binding failure must propagate");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().equals("native binding failure") && !issued.get()
                                && device.lastPass.closed && !buffer.isClosed(),
                        "a binding failure before draw must remain unissued so the ordinary fallback can render it");
            }
            device.failBind = false;
            device.failDraw = true;
            issued.set(false);
            try {
                adapter.draw(material, mesh, storedPose, context, 0, 6, () -> issued.set(true));
                throw new AssertionError("injected native draw failure must propagate");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().equals("native draw failure") && issued.get()
                                && device.lastPass.closed && !buffer.isClosed(),
                        "an issued failure propagates, closes the pass and leaves arena retirement to its owner");
            }
        }
        require(device.buffers.stream().allMatch(GpuBuffer::isClosed),
                "the owning resource scope closes every uploaded mesh and arena buffer");
        System.out.println("PASS production native storage adapter: whole arena, offset UBO, index range, issued failure and ownership; no GPU execution");
    }

    private static final class RecordingDevice extends GpuDevice {
        private final List<RecordingBuffer> buffers = new ArrayList<>();
        private RecordingPass lastPass;
        private boolean failDraw;
        private boolean failBind;

        private RecordingDevice() {
            super(null, () -> {});
        }

        @Override
        public GpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
            var buffer = new RecordingBuffer(usage, size);
            buffers.add(buffer);
            return buffer;
        }

        @Override
        public GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data) {
            var buffer = (RecordingBuffer) createBuffer(label, usage, data.remaining());
            buffer.bytes.put(data.duplicate()).clear();
            return buffer;
        }

        @Override
        public CommandEncoder createCommandEncoder() {
            return new CommandEncoder(null, null, null) {
                @Override
                public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
                    require(lastPass == null || lastPass.closed, "uploads must occur outside an active render pass");
                    var buffer = (RecordingBuffer) destination.buffer();
                    buffer.bytes.duplicate().position(Math.toIntExact(destination.offset())).put(data.duplicate());
                }

                @Override
                public RenderPass createRenderPass(Supplier<String> label, GpuTextureView color, Optional<Vector4fc> clear,
                                                   GpuTextureView depth, OptionalDouble clearDepth) {
                    lastPass = new RecordingPass(RecordingDevice.this);
                    return lastPass;
                }
            };
        }
    }

    private static final class RecordingPass extends RenderPass {
        private final RecordingDevice device;
        private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
        private boolean closed;
        private int firstIndex;
        private int indexCount;

        private RecordingPass(RecordingDevice device) {
            super(null, metadataBackend(), List.of(), () -> {}, null);
            this.device = device;
        }

        @Override public void setPipeline(RenderPipeline pipeline) { }
        @Override public void setVertexBuffer(int slot, GpuBufferSlice buffer) { }
        @Override public void setIndexBuffer(GpuBuffer buffer, IndexType type) { }
        @Override
        public void setUniform(String name, GpuBuffer buffer) {
            if (device.failBind && name.equals("OmniMorphData")) {
                throw new IllegalStateException("native binding failure");
            }
            uniforms.put(name, buffer.slice());
        }
        @Override public void setUniform(String name, GpuBufferSlice slice) { uniforms.put(name, slice); }
        @Override public void close() { closed = true; }

        @Override
        public void drawIndexed(int count, int instances, int first, int vertexOffset, int firstInstance) {
            require(!closed && uniforms.containsKey("OmniMorphData") && uniforms.containsKey("OmniMorphOffsets"),
                    "native storage uniforms must be bound before issuing the draw");
            firstIndex = first;
            indexCount = count;
            if (device.failDraw) {
                throw new IllegalStateException("native draw failure");
            }
        }
    }

    private static GpuDeviceBackend metadataBackend() {
        DeviceInfo info = new DeviceInfo("recording", "fixture", "recording", false, "recording", 0,
                new DeviceLimits(16, 256, 16384, 64L * 1024 * 1024, 1, 8), null, Set.of(), null, null);
        return (GpuDeviceBackend) Proxy.newProxyInstance(GpuDeviceBackend.class.getClassLoader(),
                new Class<?>[]{GpuDeviceBackend.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getDeviceInfo")) {
                        return info;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class RecordingBuffer extends GpuBuffer {
        private final ByteBuffer bytes;
        private boolean closed;

        private RecordingBuffer(int usage, long size) {
            super(usage, size);
            bytes = ByteBuffer.allocate(Math.toIntExact(size)).order(ByteOrder.nativeOrder());
        }

        @Override public void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }

        @Override
        public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
            throw new UnsupportedOperationException("No readback in this fixture");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
