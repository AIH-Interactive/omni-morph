package top.aihmc.omnimorph.model.ysm;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Previous complete body/arm inputs remain stable while the current frame is being evaluated. */
public final class YsmBoneQueryRuntime {
    private enum View {
        BODY,
        ARM
    }

    private final YsmModelRuntime runtime;
    private final Map<View, Map<YsmModelPart, float[]>> outputs = new EnumMap<>(View.class);
    private final Map<View, Map<YsmModelPart, float[]>> inputs = new EnumMap<>(View.class);
    private final Map<View, Map<YsmModelPart, float[]>> pivots = new EnumMap<>(View.class);
    private long lastFrame = Long.MIN_VALUE;
    private long generation;
    private int evaluationDepth;

    public YsmBoneQueryRuntime(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public void beginFrame(long frame) {
        if (frame == lastFrame && !inputs.isEmpty()) {
            return;
        }
        lastFrame = frame;
        for (View view : View.values()) {
            Map<YsmModelPart, float[]> output = outputs.get(view);
            inputs.put(view, output == null ? capture(view) : output);
        }
    }

    public void captureOutput(boolean firstPerson) {
        View view = firstPerson ? View.ARM : View.BODY;
        outputs.put(view, capture(view));
    }

    private Map<YsmModelPart, float[]> capture(View view) {
        Map<YsmModelPart, float[]> result = new IdentityHashMap<>();
        for (YsmModelPart part : runtime.uniqueParts(view == View.ARM)) {
            float[] values = new float[9];
            for (YsmBoneQuery.Channel channel : YsmBoneQuery.Channel.values()) {
                if (channel == YsmBoneQuery.Channel.PIVOT_ABS) {
                    continue;
                }
                for (int axis = 0; axis < 3; axis++) {
                    values[channel.ordinal() * 3 + axis] = YsmBoneQuery.read(part, channel, axis);
                }
            }
            result.put(part, values);
        }
        return result;
    }

    float read(YsmModelPart part, YsmBoneQuery.Channel channel, int axis, boolean firstPerson) {
        if (channel == YsmBoneQuery.Channel.PIVOT_ABS) {
            Map<YsmModelPart, float[]> tracked = pivots.get(firstPerson ? View.ARM : View.BODY);
            float[] value = tracked == null ? null : tracked.get(part);
            return value == null ? 0f : value[axis];
        }
        if (evaluationDepth > 0) {
            Map<YsmModelPart, float[]> input = inputs.get(firstPerson ? View.ARM : View.BODY);
            float[] values = input == null ? null : input.get(part);
            if (values != null) {
                return values[channel.ordinal() * 3 + axis];
            }
        }
        return YsmBoneQuery.read(part, channel, axis);
    }

    public void trackPivot(YsmModelPart part, boolean firstPerson) {
        Map<YsmModelPart, float[]> tracked = pivots.computeIfAbsent(firstPerson ? View.ARM : View.BODY,
                ignored -> new IdentityHashMap<>());
        tracked.computeIfAbsent(part, ignored -> new float[3]);
    }

    boolean hasTrackedPivots(boolean firstPerson) {
        Map<YsmModelPart, float[]> tracked = pivots.get(firstPerson ? View.ARM : View.BODY);
        return tracked != null && !tracked.isEmpty();
    }

    void recordPivot(YsmModelPart part, boolean firstPerson, float x, float y, float z) {
        Map<YsmModelPart, float[]> tracked = pivots.get(firstPerson ? View.ARM : View.BODY);
        float[] value = tracked == null ? null : tracked.get(part);
        if (value != null && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
            value[0] = x;
            value[1] = y;
            value[2] = z;
        }
    }

    public EvaluationScope beginEvaluation() {
        return new EvaluationScope();
    }

    public final class EvaluationScope implements AutoCloseable {
        private final long scopeGeneration = generation;
        private boolean closed;

        private EvaluationScope() {
            evaluationDepth++;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (scopeGeneration == generation) {
                    evaluationDepth--;
                }
            }
        }
    }

    public void clear() {
        generation++;
        lastFrame = Long.MIN_VALUE;
        evaluationDepth = 0;
        outputs.clear();
        inputs.clear();
        pivots.clear();
    }
}
