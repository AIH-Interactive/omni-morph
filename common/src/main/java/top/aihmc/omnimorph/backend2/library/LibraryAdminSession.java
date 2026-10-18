package top.aihmc.omnimorph.backend2.library;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Access credentials live only in memory and are attached only to explicitly managed requests. */
public final class LibraryAdminSession implements AutoCloseable {
    private static final String ROOT = "/api/library/v1/admin/";
    private static final class Credentials {
        String accessToken;
        String refreshToken;
        long expiresAt;
    }

    private final LibraryClient client;
    private final LibraryAdminStore store;
    private volatile String access = "";
    private volatile long expiresAt;
    private volatile boolean closed;
    private volatile boolean verified;
    private LibraryClient.Request<?> pending;

    public LibraryAdminSession(LibraryClient client, Path cache) {
        this.client = client;
        LibraryAdminStore candidate;
        try {
            candidate = new LibraryAdminStore(cache, client);
        } catch (Exception unavailable) {
            candidate = null;
        }
        store = candidate;
    }

    public boolean active() {
        return !closed && client.current() && verified && expiresAt > Instant.now().getEpochSecond();
    }

    public String token() throws IllegalStateException {
        if (!active()) {
            throw new IllegalStateException("ADMIN_SESSION_EXPIRED");
        }
        return access;
    }

    public LibraryClient.Request<Boolean> rememberAvailable() {
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> store != null && store.available());
    }

    public LibraryClient.Request<Boolean> login(String name, String password, boolean remember, UUID operation) {
        return authenticate("login", Map.of("operation_id", operation.toString(), "player_name", name,
                "password", password, "remember", remember), remember);
    }

    public LibraryClient.Request<Boolean> restore() {
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (store == null) {
                return false;
            }
            var saved = store.read();
            if (saved == null) {
                return false;
            }
            try {
                var credentials = exchange.json("POST", ROOT + "refresh", Map.of(
                        "operation_id", saved.operation.toString(), "refresh_token", saved.refreshToken), Credentials.class);
                return install(exchange, credentials, true);
            } catch (LibraryClient.Failure failure) {
                // Keep the original refresh operation on transient/lost responses for journal replay.
                if (failure.status == 400 || failure.status == 401 || failure.status == 403 || failure.status == 404) {
                    store.clear();
                }
                throw failure;
            }
        });
        pending = request;
        return request;
    }

    private LibraryClient.Request<Boolean> authenticate(String route, Object input, boolean remember) {
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (remember && (store == null || !store.available())) {
                throw new LibraryClient.Failure(0, "ADMIN_REMEMBER_UNAVAILABLE");
            }
            var credentials = exchange.json("POST", ROOT + route, input, Credentials.class);
            return install(exchange, credentials, remember);
        });
        pending = request;
        return request;
    }

    private boolean install(LibraryClient.Exchange exchange, Credentials credentials,
                                         boolean remember) throws Exception {
        exchange.requireCurrent();
        if (closed || credentials.accessToken == null || !credentials.accessToken.matches("[A-Za-z0-9_-]{32,256}")
                || credentials.expiresAt <= Instant.now().getEpochSecond()) {
            throw new LibraryClient.Failure(0, "INVALID_ADMIN_SESSION");
        }
        exchange.adminToken(credentials.accessToken);
        var info = exchange.json("GET", "/api/library/v1/session", null, LibraryModels.SessionInfo.class);
        if (!info.admin() || !info.uuid().equals(client.session().player())) {
            throw new LibraryClient.Failure(0, "INVALID_ADMIN_SESSION");
        }
        if (remember) {
            if (credentials.refreshToken == null || !credentials.refreshToken.matches("[A-Za-z0-9_-]{32,256}")) {
                throw new LibraryClient.Failure(0, "INVALID_ADMIN_SESSION");
            }
            store.write(credentials.refreshToken);
        } else if (store != null) {
            store.clear();
        }
        exchange.requireCurrent();
        synchronized (this) {
            if (closed) {
                return false;
            }
            access = credentials.accessToken;
            expiresAt = credentials.expiresAt;
            verified = true;
        }
        return true;
    }

    public LibraryClient.Request<Boolean> logout() {
        String token = access;
        invalidate();
        UUID operation = UUID.randomUUID();
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (store != null) {
                store.clear();
            }
            if (!token.isEmpty()) {
                exchange.adminToken(token);
                exchange.json("POST", ROOT + "logout", Map.of("operation_id", operation.toString()), Map.class);
            }
            return true;
        });
    }

    public void invalidate() {
        verified = false;
        access = "";
        expiresAt = 0;
    }

    @Override
    public synchronized void close() {
        closed = true;
        invalidate();
        if (pending != null) {
            pending.cancel();
        }
    }
}
