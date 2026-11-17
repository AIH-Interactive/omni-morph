package top.aihmc.omnimorph.debug;

/** UI identity is separate from the Avatar currently prepared or worn. */
public record PerformanceSelection(String modelId, String name, String type, String format, long generation) {
}
