package top.aihmc.omnimorph.model.library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Actual directory scans with delayed client delivery; no GUI or renderer assertions. */
public final class LocalCosmeticCatalogSmokeTest {
    public static void run(Path data) throws Exception {
        Path root = data.resolve("catalog");
        Path resource = Files.createDirectories(root.resolve("cosmetics/星翼"));
        Path manifest = resource.resolve("cosmetic.json");
        Files.writeString(manifest, "{\"name\":\"星翼\",\"cosmetic_anchor\":\"wing_anchor\"}");
        var callbacks = new LinkedBlockingQueue<Runnable>();
        try (var catalog = new LocalCosmeticCatalog(root, callbacks::add)) {
            var initial = catalog.refresh();
            require(catalog.refresh() == initial, "pending refreshes share one scan");
            Runnable publish = delivery(callbacks);
            require(!initial.isDone() && catalog.entries().isEmpty(), "worker does not publish UI state");
            publish.run();
            var snapshot = initial.get(10, TimeUnit.SECONDS);
            require(snapshot.size() == 1 && snapshot.getFirst().source().metadata().common().name.equals("星翼"),
                    "client delivery publishes actual parsed metadata");
            var previous = snapshot.getFirst();
            Files.writeString(manifest, "{\"name\":\"New wings\",\"cosmetic_anchor\":\"wing_anchor\"}");
            var refresh = catalog.refresh();
            publish = delivery(callbacks);
            require(catalog.entries() == snapshot, "old snapshot remains visible during preparation");
            publish.run();
            var changed = refresh.get(10, TimeUnit.SECONDS).getFirst();
            require(changed.localId().equals(previous.localId())
                            && !changed.source().contentHash().equals(previous.source().contentHash())
                            && snapshot.getFirst().source().metadata().common().name.equals("星翼"),
                    "edits preserve identity, change revision and leave prior metadata intact");

            Path added = Files.createDirectories(root.resolve("cosmetics/星翼_新"));
            Files.writeString(added.resolve("cosmetic.json"), "{\"name\":\"Added wings\",\"cosmetic_anchor\":\"wing_anchor\"}");
            java.util.UUID presetId;
            try (var presetInventory = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory(root)) {
                presetId = presetInventory.scan(() -> false).stream().filter(entry -> entry.path().equals(added))
                        .findFirst().orElseThrow().localId();
            }
            var afterPreset = catalog.refresh();
            delivery(callbacks).run();
            require(afterPreset.get(10, TimeUnit.SECONDS).stream().filter(entry -> entry.path().equals(added))
                            .findFirst().orElseThrow().localId().equals(presetId),
                    "the retained wardrobe inventory preserves IDs assigned by a newer preset dependency scan");

            Files.writeString(manifest, "invalid json");
            var invalid = catalog.refresh();
            delivery(callbacks).run();
            require(invalid.get(10, TimeUnit.SECONDS).getFirst().source() == null,
                    "invalid resources remain visible as unavailable entries");

            var retiring = catalog.refresh();
            Runnable late = delivery(callbacks);
            catalog.close();
            late.run();
            require(retiring.isCancelled() && catalog.entries().isEmpty()
                            && catalog.refresh().isCompletedExceptionally(),
                    "closing before client delivery cannot republish retired entries");
        }

        Path blocked = data.resolve("catalog-file");
        Files.writeString(blocked, "not a directory");
        try (var catalog = new LocalCosmeticCatalog(blocked, callbacks::add)) {
            var failed = catalog.refresh();
            delivery(callbacks).run();
            require(failed.isCompletedExceptionally() && catalog.failure().equals("COSMETIC_INVENTORY_UNAVAILABLE")
                            && catalog.entries().isEmpty(),
                    "filesystem errors complete on the client thread without fabricated inventory");
        }
        System.out.println("PASS local cosmetic catalog: actual scans, shared pending refresh, delayed publication, stable IDs, invalid resources, retirement and IO errors; GUI separate");
    }

    private static Runnable delivery(LinkedBlockingQueue<Runnable> callbacks) throws InterruptedException {
        Runnable action = callbacks.poll(10, TimeUnit.SECONDS);
        require(action != null, "directory scan completes within its bounded test wait");
        return action;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
