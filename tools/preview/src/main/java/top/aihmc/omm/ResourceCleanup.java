package top.aihmc.omm;

import java.util.ArrayList;
import java.util.List;

/** Runs every release action in order, preserving the original rendering failure. */
final class ResourceCleanup implements AutoCloseable {
    private final List<AutoCloseable> actions = new ArrayList<>();

    void add(AutoCloseable action) {
        actions.add(action);
    }

    @Override
    public void close() throws Exception {
        Throwable first = null;
        for (AutoCloseable action : actions) {
            try {
                action.close();
            } catch (Exception | Error failure) {
                if (first == null) {
                    first = failure;
                } else if (first != failure) {
                    first.addSuppressed(failure);
                }
            }
        }
        actions.clear();
        if (first instanceof Exception exception) {
            throw exception;
        }
        if (first instanceof Error error) {
            throw error;
        }
    }
}
