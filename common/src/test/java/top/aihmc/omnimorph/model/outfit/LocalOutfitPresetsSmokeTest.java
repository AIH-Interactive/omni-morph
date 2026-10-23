package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Actual document persistence and fixed dependency/instance identity round trips. */
public final class LocalOutfitPresetsSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("outfit-presets-");
        try {
            var limits = OutfitLimits.defaults();
            var store = new LocalOutfitPresets(root);
            require(store.list(limits).isEmpty() && !Files.exists(root.resolve("outfit-presets")),
                    "reading an empty preset store creates no files");
            var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(),
                    null, null, null, "e".repeat(64), OutfitConfig.Format.FIGURA);
            var anchor = OutfitConfig.AnchorRef.authorDefault("head_anchor");
            var first = new OutfitConfig.Instance(UUID.randomUUID(), resource, 2, true, anchor,
                    OutfitConfig.Transform.identity(), List.of(), Map.of("amplitude", 0.25d), Map.of("mode", "fixed"));
            var second = new OutfitConfig.Instance(UUID.randomUUID(), resource, 7, false, anchor,
                    OutfitConfig.Transform.identity(), List.of(), Map.of("amplitude", 0.75d), Map.of());
            var configuration = new OutfitConfig(1, 19, null, List.of(first, second), List.of(),
                    Map.of(resource.identity(), 8), false);
            UUID id = UUID.randomUUID();
            var saved = store.save(id, 0, "  星翼双副本  ", configuration, limits);
            require(saved.revision() == 1 && saved.name().equals("星翼双副本")
                            && saved.configuration().revision() == 19,
                    "preset revision and outfit revision have independent meanings");
            var reloaded = new LocalOutfitPresets(root).read(id, limits);
            require(reloaded.equals(saved) && store.list(limits).getFirst().id().equals(id),
                    "source refs, UUIDs, stable ordinals, disabled copies, controls and Lua scalar state survive a real file reload");
            String json = Files.readString(root.resolve("outfit-presets").resolve(id + ".json"));
            require(!json.contains(root.toString()) && !json.contains("file:") && !json.contains("scripts"),
                    "preset files contain configuration only, without source paths or model bodies");
            var imported = new OutfitDraft(new OutfitConfig(1, 0, null, List.of(), List.of(), Map.of(), false), limits);
            var remap = imported.importPreset(reloaded.configuration());
            require(!remap.get(first.instanceId()).equals(first.instanceId())
                            && imported.configuration().instances().getFirst().controls().equals(first.controls())
                            && imported.configuration().instances().getFirst().displayOrdinal() == 2
                            && imported.configuration().instances().getLast().displayOrdinal() == 7,
                    "a saved document still uses the existing import path with new execution IDs and preserved controls/suffixes");
            rejected(() -> store.save(id, 0, "stale", configuration, limits), "OUTFIT_PRESET_REVISION_CONFLICT");
            require(store.read(id, limits).equals(saved), "a stale save leaves the old document intact");
            var updated = store.save(id, 1, "更新预设", configuration, limits);
            require(updated.revision() == 2 && store.read(id, limits).equals(updated), "an explicit matching revision updates atomically");
            var jsonLimits = new Gson().toJsonTree(limits).getAsJsonObject();
            jsonLimits.addProperty("maxPresets", 1);
            var one = new Gson().fromJson(jsonLimits, OutfitLimits.class);
            rejected(() -> store.save(UUID.randomUUID(), 0, "over budget", configuration, one), "OUTFIT_PRESET_COUNT_LIMIT");
            require(store.list(limits).size() == 1, "count-limit rejection creates no extra preset");
            jsonLimits.addProperty("maxPresets", 0);
            var zero = new Gson().fromJson(jsonLimits, OutfitLimits.class);
            rejected(() -> store.save(id, 2, "disabled", configuration, zero), "OUTFIT_PRESET_COUNT_LIMIT");
            rejected(() -> store.save(UUID.randomUUID(), 0, " ", configuration, limits), "INVALID_OUTFIT_PRESET_NAME");
            UUID corrupt = UUID.randomUUID();
            Files.writeString(root.resolve("outfit-presets").resolve(corrupt + ".json"), "{broken");
            require(store.list(limits).stream().anyMatch(item -> item.id().equals(corrupt) && !item.error().isEmpty())
                            && store.list(limits).stream().anyMatch(item -> item.id().equals(id) && item.error().isEmpty()),
                    "one invalid document remains visible as a failure without hiding other presets");
            rejected(() -> store.read(corrupt, limits), "INVALID_OUTFIT_PRESET");
            try (var files = Files.list(root.resolve("outfit-presets"))) {
                require(files.noneMatch(path -> path.toString().endsWith(".part")), "atomic saves and rejections leave no partial documents");
            }
            System.out.println("PASS local outfit presets: actual atomic files, version conflicts, fixed refs/identities/state, import remapping, negotiated count and visible malformed documents");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private static void rejected(Checked action, String code) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + code);
        } catch (IOException expected) {
            require(expected.getMessage().equals(code), "specific preset error: " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
