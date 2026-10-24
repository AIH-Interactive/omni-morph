package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.avatar.local.LocalAvatarSource;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitDraft;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real native source preparation and real HTTP authorization; no equipment endpoints or GUI pixels. */
final class OutfitPresetResourcesSmokeTest {
    static void run(Runnable drain, Path encrypted) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "preset-resources-");
        UUID account = FiguraMod.getLocalPlayerUUID();
        var live = new AtomicBoolean(true);
        try {
            Path avatar = Files.createDirectories(root.resolve("base"));
            Files.writeString(avatar.resolve("avatar.json"), "{\"name\":\"preset base\"}");
            Path accessory = Files.createDirectories(root.resolve("cosmetics/wing"));
            Files.writeString(accessory.resolve("cosmetic.json"), CloudCosmeticSourceSmokeTest.MANIFEST);
            Files.writeString(accessory.resolve("script.lua"), "return 1");
            var registry = new LocalAvatarSource(root);
            var base = registry.describe(avatar, () -> false);
            LocalCosmeticInventory.Entry entry;
            try (var inventory = new LocalCosmeticInventory(root)) {
                entry = inventory.scan(() -> false).getFirst();
            }
            var draft = new OutfitDraft(empty(base), OutfitLimits.defaults());
            draft.add(entry.reference(), entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
            draft.add(entry.reference(), entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
            try (var operation = resolve(draft.configuration(), account, root, null, live)) {
                var prepared = await(operation.future(), drain);
                require(prepared.ready() && prepared.issues().isEmpty() && prepared.localAccessories().size() == 1
                                && prepared.base().getCompoundOrEmpty("metadata").getStringOr("name", "").equals("preset base")
                                && prepared.metadata(entry.reference()).cosmeticAnchor().equals(entry.source().metadata().cosmeticAnchor()),
                        "a reloaded registry resolves the exact local base and one native package for both copies: "
                                + prepared.issues() + ", metadata=" + prepared.metadata(entry.reference()));
                var first = prepared.read(entry.reference(), () -> false);
                first.getCompoundOrEmpty("metadata").putString("name", "mutated caller");
                require(!prepared.read(entry.reference(), () -> false).getCompoundOrEmpty("metadata")
                                .getStringOr("name", "").equals("mutated caller"),
                        "dependency readers never share mutable NBT across prepared runtimes");
            }
            var missingBase = localReference(UUID.randomUUID());
            var missing = localReference(UUID.randomUUID());
            var unavailable = new OutfitDraft(empty(missingBase), OutfitLimits.defaults());
            unavailable.add(missing, entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
            unavailable.add(missing, entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
            unavailable.add(entry.reference(), entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
            try (var operation = resolve(unavailable.configuration(), account, root, null, live)) {
                var prepared = await(operation.future(), drain);
                require(!prepared.ready() && prepared.issues().size() == 2
                                && prepared.issues().stream().anyMatch(issue -> issue.base() && issue.resource().equals(missingBase))
                                && prepared.issues().stream().anyMatch(issue -> !issue.base() && issue.instances().size() == 2)
                                && prepared.metadata(entry.reference()) != null,
                        "all failed dependencies identify the base or every affected copy, while successful resources remain available");
            }
            Files.writeString(avatar.resolve("avatar.json"), "{\"name\":\"changed base\"}");
            try (var operation = resolve(empty(base), account, root, null, live)) {
                require(await(operation.future(), drain).issues().getFirst().code().equals("OUTFIT_BASE_SOURCE_CHANGED"),
                        "preset base loading never silently follows a modified local source");
            }
            Path ysm = root.resolve("base.ysm");
            Files.copy(encrypted, ysm);
            var encryptedBase = registry.describe(ysm, () -> false);
            try (var operation = resolve(empty(encryptedBase), account, root, null, live)) {
                var prepared = await(operation.future(), drain);
                require(prepared.ready() && top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.isEncrypted(
                                prepared.base().getCompoundOrEmpty("ysm")),
                        "fixed encrypted base preparation preserves the portable ciphertext payload");
            }
            CompoundTag accessoryData = LocalCosmeticSource.prepare(accessory, account,
                    entry.reference().contentHash(), () -> false).nbt();
            testCloud(accessoryData, CloudCosmeticSourceSmokeTest.MANIFEST, false, account, root, live, drain);
            CompoundTag baseData = registry.prepare(registry.describe(avatar, () -> false), account, () -> false);
            testCloud(baseData, null, true, account, root, live, drain);
            live.set(false);
            try (var operation = resolve(empty(null), account, root, null, live)) {
                require(operation.future().isCancelled(), "retired editor generations cannot prepare even an empty preset");
            }
            System.out.println("PASS preset dependencies: actual local Figura/encrypted YSM and authorized HTTP/cache, exact refs, all affected copies, no implicit upgrades, cancellation and retired results; GUI separate");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void testCloud(CompoundTag input, String manifest, boolean base, UUID account, Path root,
                                  AtomicBoolean live, Runnable drain) throws Exception {
        try (var server = new CloudCosmeticSourceSmokeTest.Server(input, manifest);
             var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                     request -> request.header("Authorization", "Bearer synthetic-player"));
             var client = new LibraryClient(access, new LibrarySession(server.origin(), account, 1, live::get),
                     Minecraft.getInstance()::execute)) {
            var model = server.model();
            var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, server.origin(), model.modelId(),
                    model.revision(), server.rawHash(), OutfitConfig.Format.valueOf(model.modelType()));
            var draft = new OutfitDraft(empty(base ? reference : null), OutfitLimits.defaults());
            if (!base) {
                draft.add(reference, OutfitConfig.AnchorRef.authorDefault("head_anchor"), OutfitConfig.Transform.identity());
                draft.add(reference, OutfitConfig.AnchorRef.authorDefault("head_anchor"), OutfitConfig.Transform.identity());
            }
            OutfitPresetResources.Prepared prepared;
            try (var operation = resolve(draft.configuration(), account, root, client, live)) {
                prepared = await(operation.future(), drain);
                require(prepared.ready() && server.files.get() == 1 && server.details.get() == 1,
                        "cloud " + (base ? "base" : "accessories") + " resolve one exact authorized body per resource");
            }
            try {
                prepared.read(reference, () -> false);
                throw new AssertionError("closed operation exposed a prepared dependency");
            } catch (java.util.concurrent.CancellationException expected) {
                // Prepared bodies cannot outlive their editor operation.
            }
            server.revoked.set(true);
            try (var operation = resolve(draft.configuration(), account, root, client, live)) {
                var denied = await(operation.future(), drain);
                require(!denied.ready() && denied.issues().getFirst().code().equals("FORBIDDEN")
                                && server.files.get() == 1,
                        "cached preview bytes cannot bypass a fresh dependency revocation check");
            }
            server.revoked.set(false);
            server.revision.set(2);
            try (var operation = resolve(draft.configuration(), account, root, client, live)) {
                var changed = await(operation.future(), drain);
                require(!changed.ready() && changed.issues().getFirst().code().equals("RESOURCE_REVISION_UNAVAILABLE")
                                && server.files.get() == 1,
                        "preset references do not drift to the latest server revision");
            }
            server.revision.set(1);
            server.gate.set(true);
            try (var operation = resolve(draft.configuration(), account, root, client, live)) {
                require(server.entered.await(5, TimeUnit.SECONDS), "dependency request reaches the HTTP cancellation gate");
                operation.close();
                server.release.countDown();
                require(operation.future().isCancelled(), "closing an operation cancels the real in-flight transfer");
            }
            require(server.unexpected.get() == 0, "resolving a preset never equips, uploads or authorizes the resource implicitly");
        }
    }

    private static OutfitPresetResources resolve(OutfitConfig config, UUID account, Path directory,
                                                 LibraryClient client, AtomicBoolean live) {
        return new OutfitPresetResources(config, OutfitLimits.defaults(), account, directory, client,
                client == null ? null : new LibraryTransferService(client, directory.resolve("cache")),
                Minecraft.getInstance()::execute, live::get);
    }

    private static OutfitConfig empty(OutfitConfig.ResourceRef base) {
        return new OutfitConfig(1, 0, base, List.of(), List.of(), Map.of(), false);
    }

    private static OutfitConfig.ResourceRef localReference(UUID id) {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, id, null, null, null,
                "f".repeat(64), OutfitConfig.Format.FIGURA);
    }

    private static <T> T await(CompletableFuture<T> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        T value = future.get(1, TimeUnit.SECONDS);
        drain.run();
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
