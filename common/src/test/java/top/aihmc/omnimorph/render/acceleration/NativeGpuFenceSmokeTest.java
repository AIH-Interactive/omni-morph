package top.aihmc.omnimorph.render.acceleration;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.backend.opengl.GlFence;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            activeDevice.set(null, device.api);
            AccelerationOptions.setStorageBlocks(true);
            var adapter = new MinecraftGpuDeviceAdapter(device.api, emptySources());
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
        var first = new MinecraftGpuDeviceAdapter(firstDevice.api, emptySources());
        var second = new MinecraftGpuDeviceAdapter(secondDevice.api, emptySources());
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
            Class<?> encoderType = Class.forName("com.mojang.renderpearl.backend.opengl.GlCommandEncoder");
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

    static final class RecordingDevice {
        private final NativeSubmitState submission = new NativeSubmitState();
        final List<RecordingBuffer> buffers = new ArrayList<>();
        private final List<GpuTexture> textures = new ArrayList<>();
        private final List<GpuTextureView> views = new ArrayList<>();
        private final List<Runnable> copies = new ArrayList<>();
        final GpuDevice api;
        boolean failBind;
        boolean failDraw;
        RecordedPass lastPass;
        final List<java.util.concurrent.atomic.AtomicInteger> programCloses = new ArrayList<>();
        private int submits;
        private int mapped;
        private boolean passActive;

        RecordingDevice() throws ReflectiveOperationException {
            api = proxy(GpuDevice.class, (instance, method, args) -> switch (method.getName()) {
                case "getDeviceInfo" -> new DeviceInfo("recording", "fixture", "recording", false,
                        "recording", 0, new DeviceLimits(16, 256, 16384, 64L * 1024 * 1024, 1, 8, 1),
                        null, Set.of(), null, null);
                case "compilePipeline" -> java.util.concurrent.CompletableFuture.completedFuture(recordProgram());
                case "createBuffer" -> createBuffer((int) args[1], args[2]);
                case "createTexture" -> createTexture(args);
                case "createTextureView" -> createView((GpuTexture) args[0]);
                case "createCommandEncoder" -> encoder();
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }

        private CompiledRenderPipeline.Pending recordProgram() {
            var closes = new java.util.concurrent.atomic.AtomicInteger();
            programCloses.add(closes);
            return () -> proxy(CompiledRenderPipeline.class,
                    (program, operation, values) -> switch (operation.getName()) {
                        case "isClosed" -> closes.get() != 0;
                        case "close" -> {
                            closes.incrementAndGet();
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(operation.toString());
                    });
        }

        private GpuBuffer createBuffer(int usage, Object contents) {
            long size = contents instanceof ByteBuffer data ? data.remaining() : (long) contents;
            var buffer = new RecordingBuffer(usage, size, this);
            if (contents instanceof ByteBuffer data) {
                buffer.bytes.put(data.duplicate()).clear();
            }
            buffers.add(buffer);
            return buffer;
        }

        private GpuTexture createTexture(Object[] args) {
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            GpuTexture texture = proxy(GpuTexture.class, (instance, method, values) -> switch (method.getName()) {
                case "usage" -> args[1];
                case "getFormat" -> args[2];
                case "getWidth" -> (int) args[3] >> (int) values[0];
                case "getHeight" -> (int) args[4] >> (int) values[0];
                case "getDepthOrLayers" -> args[5];
                case "getMipLevels" -> args[6];
                case "isClosed" -> closed.get();
                case "close" -> {
                    closed.set(true);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.toString());
            });
            textures.add(texture);
            return texture;
        }

        private GpuTextureView createView(GpuTexture texture) {
            var closed = new java.util.concurrent.atomic.AtomicBoolean();
            GpuTextureView view = proxy(GpuTextureView.class, (instance, method, args) -> switch (method.getName()) {
                case "texture" -> texture;
                case "baseMipLevel" -> 0;
                case "mipLevels" -> 1;
                case "getWidth" -> texture.getWidth((int) args[0]);
                case "getHeight" -> texture.getHeight((int) args[0]);
                case "isClosed" -> closed.get();
                case "close" -> {
                    closed.set(true);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.toString());
            });
            views.add(view);
            return view;
        }

        private CommandEncoder encoder() {
            return proxy(CommandEncoder.class, (instance, method, args) -> switch (method.getName()) {
                case "createRenderPass" -> {
                    yield openPass().api;
                }
                case "copyTextureToBuffer" -> {
                    require(!passActive, "readback follows the closed diagnostic pass");
                    var buffer = (RecordingBuffer) args[1];
                    var completion = (Runnable) args[3];
                    copies.add(() -> {
                        // Fixed readback fixture, not rendered GPU evidence: right green, left black.
                        buffer.bytes.put((4 * 8 + 6) * 4 + 1, (byte) 255);
                        buffer.submitted = true;
                        completion.run();
                    });
                    yield null;
                }
                case "writeToBuffer" -> {
                    require(!passActive, "uploads must occur outside an active render pass");
                    var destination = (GpuBufferSlice) args[0];
                    var buffer = (RecordingBuffer) destination.buffer();
                    buffer.bytes.duplicate().position(Math.toIntExact(destination.offset()))
                            .put(((ByteBuffer) args[1]).duplicate());
                    yield null;
                }
                case "createFence" -> submission.fence();
                case "submit" -> {
                    require(!passActive, "submit cannot occur in a render pass");
                    submission.submit();
                    submits++;
                    var submitted = List.copyOf(copies);
                    copies.clear();
                    submitted.forEach(Runnable::run);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }

        RecordedPass openPass() {
            require(!passActive, "diagnostics do not overlap render passes");
            passActive = true;
            lastPass = new RecordedPass(this);
            return lastPass;
        }
    }

    static final class RecordedPass {
        final java.util.Map<String, GpuBufferSlice> uniforms = new java.util.HashMap<>();
        final RenderPass api;
        boolean closed;
        int firstIndex;
        int indexCount;

        private RecordedPass(RecordingDevice device) {
            api = proxy(RenderPass.class, (instance, method, args) -> {
                require(!closed, "GPU commands require an active diagnostic pass");
                switch (method.getName()) {
                    case "setPipeline", "setVertexBuffer", "setIndexBuffer" -> { }
                    case "setUniform" -> {
                        String name = (String) args[0];
                        if (device.failBind && name.equals("OmniMorphData")) {
                            throw new IllegalStateException("native binding failure");
                        }
                        if (args[1] instanceof GpuBuffer buffer) {
                            uniforms.put(name, buffer.slice());
                        } else if (args[1] instanceof GpuBufferSlice slice) {
                            uniforms.put(name, slice);
                        }
                    }
                    case "drawIndexed" -> {
                        firstIndex = (int) args[2];
                        indexCount = (int) args[0];
                        if (device.failDraw) {
                            throw new IllegalStateException("native draw failure");
                        }
                    }
                    case "close" -> {
                        closed = true;
                        device.passActive = false;
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                }
                return null;
            });
        }
    }

    static final class RecordingBuffer implements GpuBuffer {
        private final int usage;
        final ByteBuffer bytes;
        private final RecordingDevice device;
        private boolean closed;
        private boolean submitted;

        private RecordingBuffer(int usage, long size, RecordingDevice device) {
            this.usage = usage;
            bytes = ByteBuffer.allocate(Math.toIntExact(size)).order(ByteOrder.nativeOrder());
            this.device = device;
        }

        @Override public int usage() { return usage; }
        @Override public long size() { return bytes.capacity(); }
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

    private static ShaderSource emptySources() {
        return new ShaderSource() {
            @Override
            public String getShader(net.minecraft.resources.Identifier id,
                                    com.mojang.renderpearl.api.pipeline.ShaderType type) {
                return null;
            }

            @Override public CachedIncludeSource getInclude(net.minecraft.resources.Identifier id) { return null; }
            @Override public void close() { }
        };
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
