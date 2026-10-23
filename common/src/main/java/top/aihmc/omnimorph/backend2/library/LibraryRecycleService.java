package top.aihmc.omnimorph.backend2.library;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** A shared administrator view of deleted library and wardrobe resources. */
public final class LibraryRecycleService {
    public record Item(String kind, UUID id, UUID owner, String uploaderName, String name, List<String> authors,
                       String modelType, String status, long policyRevision, long assetRevision,
                       long equipmentRevision, String assetId, long downloads, boolean canRestore, String resourceKind) {
        public Item(String kind, UUID id, UUID owner, String uploaderName, String name, List<String> authors,
                    String modelType, String status, long policyRevision, long assetRevision,
                    long equipmentRevision, String assetId, long downloads, boolean canRestore) {
            this(kind, id, owner, uploaderName, name, authors, modelType, status, policyRevision, assetRevision,
                    equipmentRevision, assetId, downloads, canRestore, "AVATAR");
        }

        public Item {
            resourceKind = resourceKind == null ? "AVATAR" : resourceKind;
            authors = List.copyOf(authors);
            if (!List.of("LIBRARY", "WARDROBE").contains(kind) || id == null || owner == null
                    || name == null || uploaderName == null || !"DELETED".equals(status)
                    || !List.of("AVATAR", "ACCESSORY", "OUTFIT").contains(resourceKind)
                    || (resourceKind.equals("OUTFIT") ? modelType != null || !kind.equals("LIBRARY")
                        : modelType == null || !List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED").contains(modelType))
                    || assetRevision < 1 || downloads < 0
                    || kind.equals("LIBRARY") && policyRevision < 1
                    || kind.equals("WARDROBE") && (equipmentRevision < 1 || assetId == null)) {
                throw new IllegalArgumentException("INVALID_RECYCLE_ITEM");
            }
        }
    }

    public record Page(List<Item> items, int page, int pageSize, long total, boolean hasNext) {
        public Page {
            items = List.copyOf(items);
            if (page < 1 || pageSize != 8 || items.size() > 8 || total < items.size()) {
                throw new IllegalArgumentException("INVALID_RECYCLE_PAGE");
            }
        }
    }

    private final LibraryClient client;
    private final Supplier<String> administrator;

    public LibraryRecycleService(LibraryClient client, Supplier<String> administrator) {
        this.client = client;
        this.administrator = administrator;
    }

    public LibraryClient.Request<Page> list(int page, String query, String type, String source) {
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(token());
            String path = "/api/library/v1/admin/recycle?page=" + page + "&q=" + encode(query)
                    + "&type=" + encode(type) + "&source=" + encode(source);
            return exchange.json("GET", path, null, Page.class);
        });
    }

    public LibraryClient.Request<Map> change(Item item, boolean purge, UUID operation) {
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(token());
            if (!purge && !item.canRestore()) {
                throw new LibraryClient.Failure(409, "RESTORE_CONFLICT");
            }
            String action = purge ? "purge" : "restore";
            String root = item.kind().equals("LIBRARY") ? "/api/library/v1/admin/models/" + item.id()
                    : "/api/library/v1/admin/wardrobe-recycle/" + item.owner() + "/" + item.id();
            String revision = item.kind().equals("LIBRARY") ? "expected_revision" : "expected_equipment_revision";
            long expected = item.kind().equals("LIBRARY") ? item.policyRevision() : item.equipmentRevision();
            return exchange.json("POST", root + "/" + action,
                    Map.of("operation_id", operation.toString(), revision, expected), Map.class);
        });
    }

    private String token() throws LibraryClient.Failure {
        String token = administrator.get();
        if (token == null || token.isBlank()) {
            throw new LibraryClient.Failure(403, "FORBIDDEN");
        }
        return token;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
