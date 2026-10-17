package top.aihmc.omnimorph.avatar;

/** Immutable snapshots may be read by rendering while a worker advances the current request. */
public final class AvatarLoadProgress {
    public enum Stage {
        PROFILE, DOWNLOADING, READING, PREPARING, DECODING, BUILDING, APPLYING
    }

    public record Snapshot(long request, Stage stage, long received, long total) {
        public double fraction() {
            return total > 0 ? Math.min(1d, (double) received / total) : -1d;
        }
    }

    private long requests;
    private volatile Snapshot snapshot;

    public synchronized long begin(Stage stage) {
        long request = ++requests;
        snapshot = new Snapshot(request, stage, 0, -1);
        return request;
    }

    public synchronized void stage(long request, Stage stage) {
        if (snapshot != null && snapshot.request() == request) {
            snapshot = new Snapshot(request, stage, 0, -1);
        }
    }

    public synchronized void downloaded(long request, long received, long total) {
        if (snapshot != null && snapshot.request() == request) {
            snapshot = new Snapshot(request, Stage.DOWNLOADING, Math.max(0, received), total);
        }
    }

    public synchronized void finish(long request) {
        if (snapshot != null && snapshot.request() == request) {
            snapshot = null;
        }
    }

    public synchronized void clear() {
        requests++;
        snapshot = null;
    }

    public synchronized void clearNetwork() {
        if (snapshot != null && (snapshot.stage() == Stage.PROFILE || snapshot.stage() == Stage.DOWNLOADING)) {
            clear();
        }
    }

    public Snapshot snapshot() {
        return snapshot;
    }
}
