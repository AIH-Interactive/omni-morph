package top.aihmc.omnimorph.molang.runtime;

/** Shared execution allowance for nested expressions, functions and context switches. */
public final class MolangExecutionBudget {
    public static final int DEFAULT_STEPS = 100_000;
    // One model frame includes events, controllers and all sampled bone channels.
    public static final int DEFAULT_FRAME_STEPS = 500_000;

    public interface Owner {
        MolangExecutionBudget executionBudget();
    }

    public static final class Exceeded extends RuntimeException {
        public Exceeded() {
            super("Molang execution budget exceeded");
        }
    }

    private final int maximumSteps;
    private final int maximumFrameSteps;
    private int remaining;
    private int depth;
    private long frame = Long.MIN_VALUE;
    private int frameRemaining;

    public MolangExecutionBudget() {
        this(DEFAULT_STEPS, DEFAULT_FRAME_STEPS);
    }

    public MolangExecutionBudget(int maximumSteps) {
        this(maximumSteps, maximumSteps);
    }

    private MolangExecutionBudget(int maximumSteps, int maximumFrameSteps) {
        if (maximumSteps <= 0 || maximumFrameSteps <= 0) {
            throw new IllegalArgumentException("Execution budget must be positive");
        }
        this.maximumSteps = maximumSteps;
        this.maximumFrameSteps = maximumFrameSteps;
        remaining = maximumSteps;
        frameRemaining = maximumFrameSteps;
    }

    public Scope begin() {
        if (depth == 0) {
            remaining = maximumSteps;
        }
        depth++;
        return new Scope(false);
    }

    public Scope beginFrame(long identity) {
        boolean root = depth == 0;
        if (root) {
            if (frame != identity) {
                frame = identity;
                frameRemaining = maximumFrameSteps;
            }
            remaining = frameRemaining;
        }
        depth++;
        return new Scope(root);
    }

    public void consume() {
        if (remaining <= 0) {
            throw new Exceeded();
        }
        remaining--;
    }

    public final class Scope implements AutoCloseable {
        private final boolean frameRoot;
        private boolean closed;

        private Scope(boolean frameRoot) {
            this.frameRoot = frameRoot;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (frameRoot) {
                    frameRemaining = remaining;
                }
                depth--;
            }
        }
    }
}
