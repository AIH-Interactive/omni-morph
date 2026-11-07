package top.aihmc.omm.preview;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Struct;
import top.aihmc.omnimorph.molang.storage.StringPool;

/** OMP bone-vector live views and previous-complete-frame inputs over actual preview poses. */
final class PreviewYsmBones {
    enum Channel { POSITION, ROTATION, SCALE, PIVOT_ABS }
    private static final int X = StringPool.computeIfAbsent("x");
    private static final int Y = StringPool.computeIfAbsent("y");
    private static final int Z = StringPool.computeIfAbsent("z");
    private final PreviewSkeleton skeleton;
    private final Map<String, Integer> indices = new HashMap<>();
    private final float[][] live;
    private final Map<Integer, float[]> pivots = new HashMap<>();
    private final boolean[] pivotVisible;
    private final Vector3f pivotPosition = new Vector3f();
    private float[][] outputs;
    private float[][] inputs;
    private long lastFrame = Long.MIN_VALUE;
    private int evaluationDepth;

    PreviewYsmBones(PreviewSkeleton skeleton, int count) {
        this.skeleton = skeleton;
        live = new float[count][9];
        pivotVisible = new boolean[count];
        for (int index = 0; index < count; index++) {
            String name = skeleton.names[index];
            indices.put(name, index);
            indices.putIfAbsent(name.toLowerCase(Locale.US), index);
        }
        update(null);
    }

    static Function binding(Channel channel) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                var host = (PreviewMolang) execution.entity();
                if (host.bones == null)
                    throw new UnsupportedOperationException("YSM bone queries require a loaded YSM pose host");
                if (host.is_first_person != 0)
                    throw new UnsupportedOperationException("First-person bone query hosting is not implemented");
                String name = args.getAsString(execution, 0);
                Integer index = host.bones.indices.get(name);
                if (index == null && name != null)
                    index = host.bones.indices.get(name.toLowerCase(Locale.US));
                if (index == null)
                    return null;
                if (channel == Channel.PIVOT_ABS)
                    host.bones.pivots.computeIfAbsent(index, ignored -> new float[3]);
                return host.bones.new VectorView(index, channel);
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size == 1;
            }
        };
    }

    void beginFrame(long frame) {
        if (frame == lastFrame && inputs != null)
            return;
        lastFrame = frame;
        inputs = outputs == null ? snapshot() : outputs;
    }

    void captureOutput(YsmBonePose[] poses) {
        update(poses);
        outputs = snapshot();
    }

    private void update(YsmBonePose[] poses) {
        for (int index = 0; index < live.length; index++) {
            var pose = poses == null ? null : poses[index];
            var rest = skeleton.rotations[index];
            float[] position = pose == null ? null : pose.position();
            float[] rotation = pose == null ? null : pose.rotation();
            float[] scale = pose == null ? null : pose.scale();
            for (int axis = 0; axis < 3; axis++) {
                live[index][axis] = component(position, axis, 0);
                float origin = axis == 0 ? -rest.x : axis == 1 ? -rest.y : rest.z;
                live[index][3 + axis] = origin + component(rotation, axis, 0);
                live[index][6 + axis] = component(scale, axis, 1);
            }
        }
    }

    void recordRenderedPivots(
        Matrix4f[] world, YsmBonePose[] poses, boolean[] visible, float widthScale, float heightScale) {
        if (pivots.isEmpty())
            return;
        for (int index : skeleton.order) {
            if (index >= live.length)
                continue;
            int parent = skeleton.parents[index];
            float[] scale = poses[index] == null ? null : poses[index].scale();
            pivotVisible[index] = visible[index] && (parent < 0 || pivotVisible[parent])
                && (scale == null || scale[0] != 0 && scale[1] != 0 && scale[2] != 0);
            float[] output = pivots.get(index);
            if (!pivotVisible[index] || output == null)
                continue;
            // The render palette shares this matrix. Remove only the root model-size controls;
            // the original tracked query excludes model/entity/camera transforms and flips pixel X.
            world[index].transformPosition(skeleton.pivots[index], pivotPosition);
            float x = -pivotPosition.x * 16 / widthScale;
            float y = pivotPosition.y * 16 / heightScale;
            float z = pivotPosition.z * 16 / widthScale;
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                output[0] = x;
                output[1] = y;
                output[2] = z;
            }
        }
    }

    private static float component(float[] vector, int axis, float fallback) {
        return vector == null ? fallback : vector[axis];
    }

    private float[][] snapshot() {
        float[][] result = new float[live.length][];
        for (int index = 0; index < live.length; index++) result[index] = live[index].clone();
        return result;
    }

    EvaluationScope beginEvaluation() {
        return new EvaluationScope();
    }

    final class EvaluationScope implements AutoCloseable {
        private boolean closed;
        private EvaluationScope() {
            evaluationDepth++;
        }
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                evaluationDepth--;
            }
        }
    }

    private final class VectorView implements Struct {
        private final int bone;
        private final Channel channel;
        VectorView(int bone, Channel channel) {
            this.bone = bone;
            this.channel = channel;
        }
        @Override
        public Object getProperty(int name) {
            int axis = name == X ? 0 : name == Y ? 1 : name == Z ? 2 : -1;
            if (axis < 0)
                return null;
            if (channel == Channel.PIVOT_ABS)
                return pivots.get(bone)[axis];
            float[][] values = evaluationDepth > 0 && inputs != null ? inputs : live;
            return values[bone][channel.ordinal() * 3 + axis];
        }
        @Override
        public void putProperty(int name, Object value) {
            // Original bone vectors are read-only live views, including after a Struct copy.
        }
        @Override
        public Struct copy() {
            return this;
        }
    }
}
