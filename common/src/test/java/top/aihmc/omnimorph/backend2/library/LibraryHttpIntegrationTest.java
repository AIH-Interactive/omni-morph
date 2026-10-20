package top.aihmc.omnimorph.backend2.library;

import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Runs production library HTTP/DTO/transfer code against the Rust listener, with synthetic identities only. */
public final class LibraryHttpIntegrationTest {
    public static void main(String[] args) throws Exception {
        // Establish lifecycle ownership only. This fixture creates no window, device, texture or framebuffer.
        com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        String origin = args[0];
        UUID model = UUID.fromString(args[1]);
        String instance = args[2];
        Path fixture = Path.of(args[3]);
        UUID owner = new UUID(0, 1);
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        Path directory = Files.createTempDirectory("omnimorph-library-rust-java-");
        var endpoint = CloudEndpoint.parse(origin, () -> true);
        try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request
                .header("Authorization", "Bearer synthetic-token-1").header("x-omnimorph-auth-mode", "figura"));
             var client = new LibraryClient(access, new LibrarySession(instance, owner, 1, () -> true), callbacks::add)) {
            require(await(client.capabilities().future(), callbacks).enabled(), "real server capabilities");
            require(await(client.sessionInfo().future(), callbacks).uuid().equals(owner), "real authenticated session UUID");
            var page = await(client.list(Map.of("scope", "mine", "page", "1", "page_size", "8")).future(), callbacks);
            require(page.items().stream().anyMatch(item -> item.modelId().equals(model)), "actual Rust catalog DTO");
            var detail = await(client.detail(model).future(), callbacks);
            require(detail.requiresAuthorization() && detail.uploader().uuid().equals(owner), "real authorization metadata");
            var grants = new LibraryAuthorization(client, model, () -> "");
            require(await(grants.list(1).future(), callbacks).grants().items().stream().anyMatch(item -> item.uuid().equals(owner)),
                    "uploader's automatic grant is present on the real backend");
            var candidates = await(grants.resolve("Player2").future(), callbacks);
            require(candidates.items().getFirst().uuid().equals(new UUID(0, 2)), "real UUID resolution");
            await(grants.change(new LibraryAuthorization.Change(new UUID(0, 2), true, UUID.randomUUID(), detail.policyRevision()))
                    .future(), callbacks);
            long imagePolicy = await(client.detail(model).future(), callbacks).policyRevision();
            var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xff123456);
            var imageBytes = new java.io.ByteArrayOutputStream();
            require(javax.imageio.ImageIO.write(image, "png", imageBytes), "synthetic PNG fixture");
            await(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.binaryJson("PUT",
                    "/api/library/v1/models/" + model + "/image", imageBytes.toByteArray(), "image/png",
                    Map.of("x-omnimorph-operation", UUID.randomUUID().toString(),
                            "x-omnimorph-expected-revision", Long.toString(imagePolicy)), Map.class)).future(), callbacks);
            long thumbnailMicros;
            long cachedThumbnailMicros;
            try (var thumbnails = new LibraryThumbnailService(client, directory)) {
                long started = System.nanoTime();
                require(await(thumbnails.load(model).future(), callbacks).sourceWidth() == 4, "real PNG stream and decode");
                thumbnailMicros = (System.nanoTime() - started) / 1000;
                started = System.nanoTime();
                require(await(thumbnails.load(model).future(), callbacks).width() == 4, "real authorized thumbnail cache");
                cachedThumbnailMicros = (System.nanoTime() - started) / 1000;
            }
            var transfers = new LibraryTransferService(client, directory);
            long started = System.nanoTime();
            var preview = await(transfers.preview(model).request().future(), callbacks);
            long previewMicros = (System.nanoTime() - started) / 1000;
            require(preview.rawHash().equals(CloudHttp.sha256(Files.readAllBytes(fixture))), "actual Rust stream digest/header contract");
            require(await(client.detail(model).future(), callbacks).downloads() == 0, "preview does not count a download");
            started = System.nanoTime();
            await(transfers.preview(model).request().future(), callbacks);
            long cachedPreviewMicros = (System.nanoTime() - started) / 1000;
            started = System.nanoTime();
            var downloaded = await(transfers.download(model).request().future(), callbacks);
            long formalMicros = (System.nanoTime() - started) / 1000;
            require(downloaded.purpose().equals("download") && downloaded.nbt().contains("metadata"), "real formal NBT transfer");
            require(await(client.detail(model).future(), callbacks).downloads() == 1, "real formal receipt counts once");
            require(await(client.collection(1).future(), callbacks).items().stream()
                    .anyMatch(entry -> entry.modelId().equals(model) && !entry.inOutfit()),
                    "published submission belongs to the account without becoming an outfit candidate");
            try (var viewerAccess = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request
                    .header("Authorization", "Bearer synthetic-token-2").header("x-omnimorph-auth-mode", "figura"));
                 var viewer = new LibraryClient(viewerAccess,
                         new LibrarySession(instance, new UUID(0, 2), 1, () -> true), callbacks::add)) {
                var viewerTransfers = new LibraryTransferService(viewer, directory.resolve("device-one"));
                await(viewerTransfers.add(model).request().future(), callbacks);
                await(viewerTransfers.add(model).request().future(), callbacks);
                require(await(viewer.collection(1).future(), callbacks).total() == 1,
                        "formal addition uses actual server membership and is idempotent");
                require(await(viewer.sessionInfo().future(), callbacks).equipmentModelId() == null,
                        "addition never equips the model");
                await(viewer.collectionOutfit(model, true).future(), callbacks);
            }
            try (var secondAccess = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request
                    .header("Authorization", "Bearer synthetic-token-2").header("x-omnimorph-auth-mode", "figura"));
                 var secondDevice = new LibraryClient(secondAccess,
                         new LibrarySession(instance, new UUID(0, 2), 2, () -> true), callbacks::add)) {
                require(await(secondDevice.collection(1).future(), callbacks).items().stream()
                        .anyMatch(entry -> entry.modelId().equals(model) && entry.inOutfit()),
                        "an independent client sees account membership and outfit preference without local cache");
            }
            System.out.printf("M7 synthetic Rust/Java transfer timing: thumbnail=%d us, thumbnail_cache=%d us, "
                            + "preview=%d us, preview_cache=%d us, formal_download=%d us, heap_snapshot=%d bytes%n",
                    thumbnailMicros, cachedThumbnailMicros, previewMicros, cachedPreviewMicros, formalMicros,
                    java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            UUID favorite = UUID.randomUUID();
            for (int i = 0; i < 2; i++) {
                await(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("PUT",
                        "/api/library/v1/models/" + model + "/favorite", Map.of("operation_id", favorite.toString()), Map.class))
                        .future(), callbacks);
            }
            detail = await(client.detail(model).future(), callbacks);
            require(detail.favorited() && detail.favorites() == 1, "real favorite journal replay");
            long revision = detail.policyRevision();
            await(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("PATCH",
                    "/api/library/v1/models/" + model, Map.of("operation_id", UUID.randomUUID().toString(),
                            "expected_revision", revision, "visibility", "PRIVATE"), Map.class)).future(), callbacks);
            try (var otherAccess = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request
                    .header("Authorization", "Bearer synthetic-token-2").header("x-omnimorph-auth-mode", "figura"));
                 var other = new LibraryClient(otherAccess, new LibrarySession(instance, new UUID(0, 2), 1, () -> true), callbacks::add)) {
                try {
                    await(other.detail(model).future(), callbacks);
                    throw new AssertionError("private model became accessible through a grant");
                } catch (ExecutionException expected) {
                    require(expected.getCause() instanceof LibraryClient.Failure failure && failure.status == 404,
                            "real private policy denies a granted player's catalog access");
                }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("PASS production Java library client against actual Rust: DTO/auth, grants, stream/hash/NBT, preview/download receipts, favorite replay and private policy");
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) {
                callback.run();
            }
            Thread.sleep(5);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
