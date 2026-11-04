package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.opengl.GlFence;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
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
import net.minecraft.server.Bootstrap;
import org.joml.Vector4fc;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

/** Production adapter + real GlFence/awaitSubmit checks; GPU commands and pixels are recorded fixtures. */
public final class NativeGpuFenceSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        var device = new RecordingDevice();
        Field activeDevice = RenderSystem.class.getDeclaredField("DEVICE");
        activeDevice.setAccessible(true);
        Object previousDevice = activeDevice.get(null);
        boolean previousStorage = AccelerationOptions.storageBlocks();
        try {
            activeDevice.set(null, device);
            AccelerationOptions.setStorageBlocks(true);
            var adapter = new MinecraftGpuDeviceAdapter(device, (id, type) -> null);
            adapter.selfTest();
            require(adapter.storageLimits() != null, "all three optional storage diagnostics completed");
            require(device.submits == 5 && device.mapped == 5,
                    "two uniform and three storage probes submit before waiting and mapping");
            require(device.buffers.stream().allMatch(GpuBuffer::isClosed)
                            && device.textures.stream().allMatch(GpuTexture::isClosed)
                            && device.views.stream().allMatch(GpuTextureView::isClosed),
                    "all successful diagnostic resources close after readback");
        } finally {
            AccelerationOptions.setStorageBlocks(previousStorage);
            activeDevice.set(null, previousDevice);
        }
        retirement();
        System.out.println("PASS production GPU probes/retirement: actual GlFence ordering, five readbacks and resource cleanup; no GPU pixels/window");
    }

    private static void retirement() throws ReflectiveOperationException {
        var firstDevice = new RecordingDevice();
        var secondDevice = new RecordingDevice();
        var first = new MinecraftGpuDeviceAdapter(firstDevice, (id, type) -> null);
        var second = new MinecraftGpuDeviceAdapter(secondDevice, (id, type) -> null);
        var retired = new GpuRetirement();
        var a = new RetirementResource();
        var b = new RetirementResource();
        var c = new RetirementResource();
        retired.retire(first, List.of(a));
        retired.poll();
        require(a.closes == 0 && firstDevice.submits == 0 && retired.batches() == 1,
                "normal polling leaves the current submit pending without submitting or blocking");
        retired.retire(first, List.of(b));
        retired.retire(second, List.of(c));
        require(retired.awaitRemaining(1_000_000_000L), "shutdown completes submitted retirement batches");
        require(firstDevice.submits == 1 && secondDevice.submits == 1,
                "shutdown captures all fences then submits once per owning device");
        require(a.closes == 1 && b.closes == 1 && c.closes == 1
                        && retired.batches() == 0 && retired.bytes() == 0,
                "shutdown closes old and freshly fenced resources exactly once after completion");
        require(retired.awaitRemaining(1_000_000L) && firstDevice.submits == 1 && secondDevice.submits == 1,
                "empty retirement does not submit again");
    }

    private static final class RetirementResource implements GpuDeviceAdapter.Resource {
        private int closes;

        @Override public long bytes() { return 16; }
        @Override public void close() { closes++; }
    }

    /** Only submit progression is recorded; the Minecraft fence and its current-submit rejection are real. */
    private static final class NativeSubmitState {
        private final Object encoder;
        private final Field index;
        private final Constructor<?> fenceConstructor;

        private NativeSubmitState() throws ReflectiveOperationException {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            Class<?> encoderType = Class.forName("com.mojang.blaze3d.opengl.GlCommandEncoder");
            encoder = unsafe.allocateInstance(encoderType);
            index = encoderType.getDeclaredField("currentSubmitIndex");
            index.setAccessible(true);
            index.setLong(encoder, 2L);
            Field fences = encoderType.getDeclaredField("fences");
            fences.setAccessible(true);
            // Submitted fixture batches are already complete: the native zero-sync branch needs no GL calls.
            fences.set(encoder, new long[2]);
            fenceConstructor = GlFence.class.getDeclaredConstructor(encoderType);
            fenceConstructor.setAccessible(true);
        }

        private GpuFence fence() {
            try {
                return (GpuFence) fenceConstructor.newInstance(encoder);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        private void submit() {
            try {
                index.setLong(encoder, index.getLong(encoder) + 1);
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class RecordingDevice extends GpuDevice {
        private final NativeSubmitState submission = new NativeSubmitState();
        private final List<RecordingBuffer> buffers = new ArrayList<>();
        private final List<GpuTexture> textures = new ArrayList<>();
        private final List<GpuTextureView> views = new ArrayList<>();
        private final List<Runnable> copies = new ArrayList<>();
        private int submits;
        private int mapped;
        private boolean passActive;

        private RecordingDevice() throws ReflectiveOperationException {
            super(metadataBackend(), () -> {});
        }

        @Override
        public CompiledRenderPipeline precompilePipeline(RenderPipeline pipeline, ShaderSource source) {
            return () -> true;
        }

        @Override
        public GpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
            var buffer = new RecordingBuffer(usage, size, this);
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
        public GpuTexture createTexture(String label, int usage, GpuFormat format,
                                        int width, int height, int layers, int levels) {
            return createTexture(() -> label, usage, format, width, height, layers, levels);
        }

        @Override
        public GpuTexture createTexture(Supplier<String> label, int usage, GpuFormat format,
                                        int width, int height, int layers, int levels) {
            var texture = new GpuTexture(usage, label.get(), format, width, height, layers, levels) {
                private boolean closed;
                @Override public void close() { closed = true; }
                @Override public boolean isClosed() { return closed; }
            };
            textures.add(texture);
            return texture;
        }

        @Override
        public GpuTextureView createTextureView(GpuTexture texture) {
            var view = new GpuTextureView(texture, 0, 1) {
                private boolean closed;
                @Override public void close() { closed = true; }
                @Override public boolean isClosed() { return closed; }
            };
            views.add(view);
            return view;
        }

        @Override
        public CommandEncoder createCommandEncoder() {
            return new CommandEncoder(null, null, null) {
                @Override
                public RenderPass createRenderPass(Supplier<String> label, GpuTextureView color,
                                                   Optional<Vector4fc> clear, GpuTextureView depth,
                                                   OptionalDouble clearDepth) {
                    require(!passActive, "diagnostics do not overlap render passes");
                    passActive = true;
                    return new RecordingPass(RecordingDevice.this);
                }

                @Override
                public void copyTextureToBuffer(GpuTexture source, GpuBuffer destination,
                                                long offset, Runnable completion, int mipLevel) {
                    require(!passActive, "readback follows the closed diagnostic pass");
                    var buffer = (RecordingBuffer) destination;
                    copies.add(() -> {
                        // Fixed readback fixture, not rendered GPU evidence: right green, left black.
                        buffer.bytes.put((4 * 8 + 6) * 4 + 1, (byte) 255);
                        buffer.submitted = true;
                        completion.run();
                    });
                }

                @Override
                public GpuFence createFence() {
                    return submission.fence();
                }

                @Override
                public void submit() {
                    require(!passActive, "submit cannot occur in a render pass");
                    submission.submit();
                    submits++;
                    List<Runnable> submitted = List.copyOf(copies);
                    copies.clear();
                    submitted.forEach(Runnable::run);
                }
            };
        }
    }

    private static final class RecordingPass extends RenderPass {
        private final RecordingDevice device;

        private RecordingPass(RecordingDevice device) {
            super(null, metadataBackend(), List.of(), () -> {}, null);
            this.device = device;
        }

        @Override public void setPipeline(RenderPipeline pipeline) { }
        @Override public void setUniform(String name, GpuBuffer buffer) { }
        @Override public void setUniform(String name, GpuBufferSlice buffer) { }
        @Override public void setVertexBuffer(int slot, GpuBufferSlice buffer) { }
        @Override public void setIndexBuffer(GpuBuffer buffer, IndexType type) { }
        @Override public void drawIndexed(int count, int instances, int first, int offset, int firstInstance) { }
        @Override public void close() { device.passActive = false; }
    }

    private static final class RecordingBuffer extends GpuBuffer {
        private final ByteBuffer bytes;
        private final RecordingDevice device;
        private boolean closed;
        private boolean submitted;

        private RecordingBuffer(int usage, long size, RecordingDevice device) {
            super(usage, size);
            bytes = ByteBuffer.allocate(Math.toIntExact(size)).order(ByteOrder.nativeOrder());
            this.device = device;
        }

        @Override public void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }

        @Override
        public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
            require(submitted && !closed, "readback maps only after submission and before resource closure");
            device.mapped++;
            return new GpuBufferSlice.MappedView(slice(offset, length),
                    bytes.slice(Math.toIntExact(offset), Math.toIntExact(length)).order(bytes.order()), () -> {});
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
