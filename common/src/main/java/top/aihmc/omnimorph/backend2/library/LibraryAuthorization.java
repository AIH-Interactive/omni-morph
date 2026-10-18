package top.aihmc.omnimorph.backend2.library;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Rechecks model policy before every authorization read or change. */
public final class LibraryAuthorization {
    public record Player(UUID uuid, String name, long createdAt) {
        public Player {
            if (uuid == null || name == null) {
                throw new IllegalArgumentException("INVALID_PLAYER");
            }
        }
    }

    public record Candidates(List<Player> items) {
        public Candidates {
            items = List.copyOf(items);
            if (items.size() > 1) {
                throw new IllegalArgumentException("AMBIGUOUS_PLAYER");
            }
        }
    }

    public record Page(List<Player> items, long total, int page, int pageSize, boolean hasNext) {
        public Page {
            items = List.copyOf(items);
            if (items.size() > 8 || page < 1 || pageSize != 8 || total < items.size()) {
                throw new IllegalArgumentException("INVALID_GRANTS_PAGE");
            }
        }
    }

    public record Listing(LibraryModels.Model model, Page grants) {}
    public record Change(UUID player, boolean grant, UUID operation, long expectedRevision) {}

    private final LibraryClient client;
    private final UUID model;
    private final Supplier<String> admin;

    public LibraryAuthorization(LibraryClient client, UUID model, Supplier<String> admin) {
        this.client = client;
        this.model = model;
        this.admin = admin;
    }

    public static boolean available(LibraryModels.Model model) {
        return model != null && model.requiresAuthorization() && model.allows("grants");
    }

    private LibraryModels.Model authorize(LibraryClient.Exchange exchange) throws Exception {
        exchange.adminToken(admin.get());
        var detail = exchange.json("GET", path(), null, LibraryModels.Model.class);
        if (!available(detail)) {
            throw new LibraryClient.Failure(0, "AUTHORIZATION_NOT_REQUIRED");
        }
        return detail;
    }

    public LibraryClient.Request<Listing> list(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("INVALID_PAGE");
        }
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            var detail = authorize(exchange);
            return new Listing(detail, exchange.json("GET", path() + "/grants?page=" + page, null, Page.class));
        });
    }

    public LibraryClient.Request<Candidates> resolve(String name) {
        String encoded = URLEncoder.encode(name.trim(), StandardCharsets.UTF_8);
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            authorize(exchange);
            return exchange.json("GET", "/api/library/v1/players/resolve?model_id=" + model + "&name=" + encoded,
                    null, Candidates.class);
        });
    }

    /** A retry uses the original operation and CAS, even if the preceding response was lost. */
    public LibraryClient.Request<Map> change(Change change) {
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            var detail = authorize(exchange);
            if (!change.grant() && detail.uploader().uuid().equals(change.player())) {
                throw new LibraryClient.Failure(0, "UPLOADER_GRANT_REQUIRED");
            }
            return exchange.json(change.grant() ? "PUT" : "DELETE", path() + "/grants/" + change.player(),
                    Map.of("operation_id", change.operation().toString(), "expected_revision", change.expectedRevision()),
                    Map.class);
        });
    }

    private String path() {
        return "/api/library/v1/models/" + model;
    }
}
