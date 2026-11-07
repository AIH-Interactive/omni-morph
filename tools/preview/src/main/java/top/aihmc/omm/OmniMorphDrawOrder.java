package top.aihmc.omm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Adapts the original material-boundary face order without copying its sorting algorithm. */
final class OmniMorphDrawOrder {
    record Range(int batch, int firstIndex, int indexCount) {}

    private static final String PREFIX = "top.aihmc.omnimorph.render.acceleration.";
    private final Object submission;
    private final Constructor<?> queued;
    private final Method faces;
    private final Method faceBatch;
    private final Method faceIndex;

    OmniMorphDrawOrder() throws Exception {
        Class<?> type = Class.forName(PREFIX + "GpuSubmission");
        var global = type.getDeclaredField("GLOBAL");
        global.setAccessible(true);
        submission = global.get(null);
        queued = Class.forName(PREFIX + "GpuSubmission$Queued").getDeclaredConstructors()[0];
        queued.setAccessible(true);
        faces = type.getDeclaredMethod("faces", List.class, boolean.class);
        faces.setAccessible(true);
        Class<?> face = Class.forName(PREFIX + "GpuSubmission$Face");
        faceBatch = face.getDeclaredMethod("batch");
        faceIndex = face.getDeclaredMethod("face");
        faceBatch.setAccessible(true);
        faceIndex.setAccessible(true);
    }

    List<Range> ranges(List<Object> batches) throws Exception {
        var groups = new LinkedHashMap<Object, List<Integer>>();
        for (int index = 0; index < batches.size(); index++) {
            Object batch = batches.get(index);
            Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
            if (!(boolean) semantics.getClass().getMethod("visible").invoke(semantics)) {
                continue;
            }
            Object material = semantics.getClass().getMethod("material").invoke(semantics);
            groups.computeIfAbsent(material, ignored -> new ArrayList<>()).add(index);
        }
        List<Range> result = new ArrayList<>();
        for (var group : groups.entrySet()) {
            Object material = group.getKey();
            boolean sorted = material != null
                && (boolean) material.getClass().getMethod("sortOnUpload").invoke(material);
            List<Object> originalQueue = new ArrayList<>();
            for (int index : group.getValue()) {
                originalQueue.add(queued.newInstance(batches.get(index), null, null));
            }
            for (Object face : (List<?>) faces.invoke(submission, originalQueue, sorted)) {
                int batch = group.getValue().get((int) faceBatch.invoke(face));
                int firstIndex = (int) faceIndex.invoke(face) * 6;
                if (!result.isEmpty()) {
                    Range previous = result.getLast();
                    if (previous.batch() == batch && previous.firstIndex() + previous.indexCount() == firstIndex) {
                        result.set(result.size() - 1,
                            new Range(batch, previous.firstIndex(), previous.indexCount() + 6));
                        continue;
                    }
                }
                result.add(new Range(batch, firstIndex, 6));
            }
        }
        return result;
    }
}
