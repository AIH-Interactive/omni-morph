package top.aihmc.omm.preview;

import static java.lang.foreign.ValueLayout.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/** Windows process resident-memory high-water mark, including JVM/native/driver host allocations. */
final class PreviewMemory implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private final MethodHandle query;
    private final MemorySegment process;
    private final MemorySegment counters;

    PreviewMemory() {
        var kernel = SymbolLookup.libraryLookup("kernel32.dll", arena);
        var linker = Linker.nativeLinker();
        query = linker.downcallHandle(kernel.find("K32GetProcessMemoryInfo").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        var currentProcess =
            linker.downcallHandle(kernel.find("GetCurrentProcess").orElseThrow(), FunctionDescriptor.of(ADDRESS));
        try {
            process = (MemorySegment) currentProcess.invokeExact();
        } catch (Throwable failure) {
            arena.close();
            throw new IllegalStateException("GetCurrentProcess failed", failure);
        }
        // 64-bit PROCESS_MEMORY_COUNTERS_EX: DWORD cb/faults, then SIZE_T memory counters.
        counters = arena.allocate(80, 8);
        counters.set(JAVA_INT, 0, 80);
    }

    long peakRssBytes() {
        try {
            int success = (int) query.invokeExact(process, counters, 80);
            if (success == 0)
                throw new IllegalStateException("K32GetProcessMemoryInfo failed");
            return counters.get(JAVA_LONG, 8); // PeakWorkingSetSize, bytes since process creation.
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not read Windows peak working set", failure);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}
