package top.aihmc.omm.preview;

public final class PreviewMemoryTest {
    public static void main(String[] args) {
        try (var memory = new PreviewMemory()) {
            long before = memory.peakRssBytes();
            byte[] touched = new byte[32 * 1024 * 1024];
            for (int offset = 0; offset < touched.length; offset += 4096) touched[offset] = 1;
            long after = memory.peakRssBytes();
            if (before <= 0 || after < before || after < touched.length || touched[4096] != 1)
                throw new AssertionError("Invalid process resident-memory high-water mark: " + before + "/" + after);
            System.out.println(
                "Peak RSS PASS: Windows process bytes, monotonic high-water mark, touched heap included: " + after);
        }
    }
}
