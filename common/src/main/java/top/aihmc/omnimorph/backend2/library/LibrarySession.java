package top.aihmc.omnimorph.backend2.library;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Immutable server/player identity plus a live connection-retirement check. No credentials. */
public final class LibrarySession {
    private final String instance;
    private final UUID player;
    private final long epoch;
    private final BooleanSupplier current;

    public LibrarySession(String instance, UUID player, long epoch, BooleanSupplier current) {
        this.instance = Objects.requireNonNull(instance);
        this.player = Objects.requireNonNull(player);
        this.epoch = epoch;
        this.current = Objects.requireNonNull(current);
    }

    public String instance() {
        return instance;
    }

    public UUID player() {
        return player;
    }

    public long epoch() {
        return epoch;
    }

    public boolean current() {
        return current.getAsBoolean();
    }

    public void requireCurrent() {
        if (!current()) {
            throw new CancellationException("LIBRARY_RETIRED");
        }
    }
}
