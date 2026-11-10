package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Collection opening plus twelve uncached covers against the service's 32-message/second budget. */
public final class LibraryCollectionBurstSmokeTest {
    public static void main(String[] args) throws Exception {
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", png);
        var limited = new AtomicInteger();
        var successfulImages = new AtomicInteger();
        var workers = Executors.newCachedThreadPool();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        long[] window = {0, 0};
        server.createContext("/api/library/v1/", exchange -> {
            boolean reject;
            synchronized (window) {
                long now = System.nanoTime();
                if (now - window[0] >= TimeUnit.SECONDS.toNanos(1)) {
                    window[0] = now;
                    window[1] = 0;
                }
                reject = window[1] >= 32;
                if (!reject) {
                    window[1]++;
                }
            }
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if (reject) {
                limited.incrementAndGet();
                body = "{\"code\":\"RATE_LIMITED\"}".getBytes(StandardCharsets.UTF_8);
            } else if (path.endsWith("/image")) {
                successfulImages.incrementAndGet();
                body = png.toByteArray();
            } else if (path.endsWith("/collection")) {
                var items = new JsonArray();
                for (int index = 1; index <= 12; index++) {
                    var entry = new JsonObject();
                    UUID id = new UUID(0, index);
                    entry.addProperty("model_id", id.toString());
                    entry.addProperty("joined_at", 1);
                    entry.addProperty("join_source", "DOWNLOAD");
                    entry.addProperty("in_outfit", false);
                    entry.add("model", model(id));
                    items.add(entry);
                }
                var page = new JsonObject();
                page.add("items", items);
                page.addProperty("total", 12);
                page.addProperty("page", 1);
                page.addProperty("page_size", 64);
                page.addProperty("has_next", false);
                body = page.toString().getBytes(StandardCharsets.UTF_8);
            } else {
                body = model(UUID.fromString(path.substring(path.lastIndexOf('/') + 1)))
                        .toString().getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", !reject && path.endsWith("/image")
                    ? "image/png" : "application/json");
            exchange.sendResponseHeaders(reject ? 429 : 200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var cache = Files.createTempDirectory("collection-burst-");
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                builder -> builder.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(origin, new UUID(0, 1), 1, () -> true), Runnable::run)) {
            LibraryModels.CollectionPage collection = client.collection(1).future().get(10, TimeUnit.SECONDS);
            var thumbnails = new LibraryThumbnailService(client, cache);
            var covers = new ArrayList<CompletableFuture<LibraryThumbnailService.Pixels>>();
            for (var entry : collection.items()) {
                covers.add(thumbnails.load(entry.modelId()).future());
            }
            for (var cover : covers) {
                try {
                    cover.get(15, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException expected) {
                    // Report the precise burst symptom after all workers have finished.
                }
            }
            if (limited.get() != 0) {
                throw new AssertionError("Opening collection triggered RATE_LIMITED: " + limited.get() + " rejected requests");
            }
            if (successfulImages.get() != 12 || covers.stream().anyMatch(CompletableFuture::isCompletedExceptionally)) {
                throw new AssertionError("All twelve collection covers must remain loadable");
            }
            System.out.println("PASS collection burst: twelve uncached covers, no RATE_LIMITED, all images loaded");
        } finally {
            server.stop(0);
            workers.shutdownNow();
            try (var paths = Files.walk(cache)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static JsonObject model(UUID id) {
        var model = new JsonObject();
        model.addProperty("model_id", id.toString());
        model.addProperty("revision", 1);
        model.addProperty("policy_revision", 1);
        model.addProperty("display_name", "Cover fixture");
        model.addProperty("internal_name", "fixture");
        model.add("authors", new JsonArray());
        var uploader = new JsonObject();
        uploader.addProperty("uuid", new UUID(0, 1).toString());
        uploader.addProperty("name", "Owner");
        model.add("uploader", uploader);
        model.addProperty("model_type", "FIGURA");
        model.addProperty("visibility", "PUBLIC");
        model.addProperty("image_available", true);
        model.addProperty("image_revision", 1);
        model.add("allowed_actions", new JsonArray());
        return model;
    }
}
