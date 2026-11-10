package top.aihmc.omnimorph.model.library;

import java.util.EnumSet;
import java.util.Set;

/** Actual publication milestones, retained so fast server stages remain visible in the timeline. */
public final class LibraryUploadProgress {
    public enum Stage { PREPARING, UPLOADING, SUBMITTING, VALIDATING, REVIEWING, FINALIZING, RESULT }

    public record Snapshot(Stage stage, Set<Stage> completed, boolean failed) {}

    private volatile Snapshot snapshot = new Snapshot(Stage.PREPARING, Set.of(), false);

    public Snapshot snapshot() {
        return snapshot;
    }

    public synchronized void begin() {
        snapshot = new Snapshot(Stage.PREPARING, Set.of(), false);
    }

    public synchronized void advance(Stage stage) {
        var completed = EnumSet.noneOf(Stage.class);
        completed.addAll(snapshot.completed());
        if (stage != snapshot.stage()) {
            completed.add(snapshot.stage());
        }
        // These server boundaries prove completion of the preceding work, including commit replay.
        if (stage == Stage.VALIDATING || stage == Stage.REVIEWING || stage == Stage.FINALIZING || stage == Stage.RESULT) {
            completed.add(Stage.UPLOADING);
        }
        if (stage == Stage.REVIEWING || stage == Stage.FINALIZING || stage == Stage.RESULT) {
            completed.add(Stage.VALIDATING);
        }
        if (stage == Stage.FINALIZING || stage == Stage.RESULT) {
            completed.add(Stage.REVIEWING);
        }
        snapshot = new Snapshot(stage, Set.copyOf(completed), false);
    }

    public synchronized void fail() {
        snapshot = new Snapshot(snapshot.stage(), snapshot.completed(), true);
    }
}
