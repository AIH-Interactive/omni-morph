package top.aihmc.omm;

import java.nio.file.Path;
import java.util.List;

/** Verifies the host keeps the original post-transform offset, including nonzero and negative values. */
public final class VertexOffsetProbe {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mojang.blaze3d.systems.RenderSystem").getMethod("initRenderThread").invoke(null);
        try (OmniMorphModel model = OmniMorphModel.load(Path.of(args[0]))) {
            Object runtime = model.createRuntime();
            try (var owner = new PreviewAvatarResources(runtime.getClass().getMethod("owner").invoke(runtime));
                 var batches = new OmniMorphBatches(model.geometry(), runtime)) {
                Object original = batches.drawBatches().getFirst();
                Object semantics = original.getClass().getMethod("semantics").invoke(original);
                Object backend = OmniMorphBatches.backend(false);
                var offsetMethod = Class.forName("top.aihmc.omnimorph.render.acceleration.GpuSubmission")
                    .getDeclaredMethod("offset", double[].class, double.class);
                offsetMethod.setAccessible(true);
                for (double offset : new double[]{0, 0.25, -0.25}) {
                    Object changedSemantics = copyRecord(semantics, "vertexOffset", offset);
                    Object changedBatch = copyRecord(original, "semantics", changedSemantics);
                    List<Object> input = List.of(changedBatch);
                    double[] baseline = OmniMorphBatches.transform(input, backend).getFirst();
                    double[] expected = baseline.clone();
                    double[] position = new double[4];
                    for (int vertex = 0; vertex < expected.length; vertex += 9) {
                        System.arraycopy(expected, vertex, position, 0, 4);
                        offsetMethod.invoke(null, position, offset);
                        System.arraycopy(position, 0, expected, vertex, 4);
                    }
                    double[] actual = new OmniMorphCpuFrames(frame -> input, backend).sample(0).getFirst();
                    double difference = YsmInstanceIsolationProbe.difference(List.of(expected), List.of(actual));
                    double displacement = YsmInstanceIsolationProbe.difference(List.of(baseline), List.of(actual));
                    if (difference != 0 || (offset != 0 && displacement <= 1e-6)) {
                        throw new AssertionError("Original offset mismatch: " + offset + " error=" + difference);
                    }
                    System.out.println("ORIGINAL VERTEX OFFSET PASS offset=" + offset
                        + " displacement=" + displacement + " error=" + difference);
                }
            }
        }
    }

    private static Object copyRecord(Object source, String changedName, Object replacement) throws Exception {
        var components = source.getClass().getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        boolean found = false;
        for (int index = 0; index < components.length; index++) {
            var component = components[index];
            types[index] = component.getType();
            boolean changed = component.getName().equals(changedName);
            found |= changed;
            values[index] = changed ? replacement : component.getAccessor().invoke(source);
        }
        if (!found) {
            throw new AssertionError("Missing original record field: " + changedName);
        }
        return source.getClass().getConstructor(types).newInstance(values);
    }
}
