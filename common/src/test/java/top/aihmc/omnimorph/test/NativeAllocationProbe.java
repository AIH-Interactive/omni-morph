package top.aihmc.omnimorph.test;

import com.mojang.blaze3d.platform.MemoryTracker;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Isolated test JVM only: counts the actual untracked allocator used by 1.20.1 BufferBuilder. */
public final class NativeAllocationProbe implements AutoCloseable {
    private final Unsafe unsafe;
    private final Object base;
    private final long offset;
    private final MemoryUtil.MemoryAllocator original;
    private final Map<Long, Long> live = new HashMap<>();
    private long allocations;

    public NativeAllocationProbe() throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);
        Class.forName(MemoryTracker.class.getName());
        var allocatorField = MemoryTracker.class.getDeclaredField("ALLOCATOR");
        allocatorField.setAccessible(true);
        original = (MemoryUtil.MemoryAllocator) allocatorField.get(null);
        base = unsafe.staticFieldBase(allocatorField);
        offset = unsafe.staticFieldOffset(allocatorField);
        var recording = (MemoryUtil.MemoryAllocator) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {MemoryUtil.MemoryAllocator.class}, (proxy, method, args) -> {
                    Object result = method.invoke(original, args);
                    switch (method.getName()) {
                        case "malloc" -> {
                            allocations++;
                            live.put((Long) result, (Long) args[0]);
                        }
                        case "realloc" -> {
                            allocations++;
                            live.remove((Long) args[0]);
                            live.put((Long) result, (Long) args[1]);
                        }
                        case "free" -> live.remove((Long) args[0]);
                        default -> {
                            // Other allocator entry points are delegated unchanged.
                        }
                    }
                    return result;
                });
        unsafe.putObjectVolatile(base, offset, recording);
    }

    public long allocations() {
        return allocations;
    }

    public long liveBytes() {
        return live.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public void close() {
        unsafe.putObjectVolatile(base, offset, original);
        // These test-only builders are no longer used; also clean allocations made by the red fixture.
        for (long address : live.keySet()) {
            original.free(address);
        }
        live.clear();
    }
}
