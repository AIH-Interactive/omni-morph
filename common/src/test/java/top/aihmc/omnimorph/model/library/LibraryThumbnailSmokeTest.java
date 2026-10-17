package top.aihmc.omnimorph.model.library;

import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP and CPU image decode only. Actual texture upload/rendering remains MANUAL/PENDING. */
public final class LibraryThumbnailSmokeTest {
    private static final UUID MODEL = new UUID(0, 11);
    private static final UUID VIEWER = new UUID(0, 1);

    public static void main(String[] args) throws Exception {
        byte[] png = image("png", 800, 400);
        var pixels = LibraryThumbnailService.decode(png);
        require(pixels.width() <= 256 && pixels.height() <= 256 && pixels.width() > pixels.height(),
                "bounded thumbnail preserves aspect ratio");
        require((pixels.argb()[0] & 0xffffff) == 0x123456, "ARGB color retained");
        require(LibraryThumbnailService.decode(image("jpeg", 64, 32)).width() == 64, "JPEG accepted");
        rejectDecode(new byte[0]);
        rejectDecode(new byte[2 * 1024 * 1024 + 1]);
        rejectDecode(image("png", 2049, 1));
        rejectDecode("not an image".getBytes(StandardCharsets.UTF_8));
        Path directory = Files.createTempDirectory("library-thumbnail-test-");
        var details = new AtomicInteger();
        var images = new AtomicInteger();
        var allowed = new AtomicBoolean(true);
        var changed = new AtomicBoolean();
        var invalid = new AtomicBoolean();
        var live = new AtomicBoolean(true);
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/library/v1/models/" + MODEL, exchange -> {
            byte[] body;
            int status = 200;
            if (!allowed.get()) {
                status = 404;
                body = "{\"code\":\"MODEL_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
            } else if (exchange.getRequestURI().getPath().endsWith("/image")) {
                images.incrementAndGet();
                body = invalid.get() ? new byte[]{1, 2, 3} : png;
                exchange.getResponseHeaders().set("Content-Type", "image/png");
            } else {
                int number = details.incrementAndGet();
                int revision = changed.get() && number % 2 == 0 ? 2 : 1;
                body = ("{\"model_id\":\"" + MODEL + "\",\"revision\":1,\"policy_revision\":1,"
                        + "\"display_name\":\"model\",\"internal_name\":\"model\",\"authors\":[],"
                        + "\"uploader\":{\"uuid\":\"" + VIEWER + "\",\"name\":\"owner\"},"
                        + "\"model_type\":\"FIGURA\",\"visibility\":\"PUBLIC\",\"image_available\":true,"
                        + "\"image_revision\":" + revision + ",\"allowed_actions\":[]}")
                        .getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
            exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                request -> request.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(origin, VIEWER, 1, live::get), callbacks::add)) {
            var thumbnails = new LibraryThumbnailService(client, directory);
            // Do not call acquire/close GPU methods in this CPU-only test.
            require(await(thumbnails.load(MODEL).future(), callbacks).width() == pixels.width(), "real image stream");
            await(thumbnails.load(MODEL).future(), callbacks);
            require(images.get() == 1 && details.get() == 3, "cache hit still rechecks current backend policy");
            allowed.set(false);
            reject(thumbnails.load(MODEL).future(), callbacks);
            require(images.get() == 1, "revoked cached image not returned");
            allowed.set(true);
            // A distinct cache root ensures the following requests actually fetch bytes.
            var uncached = new LibraryThumbnailService(client, directory.resolve("changed"));
            details.set(0);
            changed.set(true);
            reject(uncached.load(MODEL).future(), callbacks);
            changed.set(false);
            invalid.set(true);
            reject(uncached.load(MODEL).future(), callbacks);
            invalid.set(false);
            var retired = uncached.load(MODEL);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (callbacks.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            require(!callbacks.isEmpty(), "result queued for client delivery");
            live.set(false);
            try {
                await(retired.future(), callbacks);
                throw new AssertionError("retired image published");
            } catch (java.util.concurrent.CancellationException expected) {
                // No native/GPU allocation exists in a discarded worker result.
            }
            System.out.println("PASS thumbnails: PNG/JPEG, bounded decode, fresh cache authority, revision race and retirement");
        } finally {
            server.stop(0);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xff123456);
            }
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        image.flush();
        return output.toByteArray();
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) {
                callback.run();
            }
            Thread.sleep(5);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void reject(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("invalid/unauthorized image accepted");
        } catch (ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure, "controlled thumbnail failure");
        }
    }

    private static void rejectDecode(byte[] bytes) throws Exception {
        try {
            LibraryThumbnailService.decode(bytes);
            throw new AssertionError("invalid image decoded");
        } catch (IOException expected) {
            // Decoder rejects before GPU allocation.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
