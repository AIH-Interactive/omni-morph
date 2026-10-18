package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Image HTTP/decoding uses the bounded thumbnail lane; GPU ownership stays on the render thread. */
public final class LibraryThumbnailService implements AutoCloseable {
    private static final String ROOT = "/api/library/v1/models/";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 2048;
    private static final int THUMBNAIL_DIMENSION = 256;
    private static final Set<Handle> FRAMES = new HashSet<>();
    private final LibraryClient client;
    private final LibraryPreviewCache cache;
    private final Supplier<String> administrator;
    private final Set<Handle> handles = new HashSet<>();
    private boolean closed;

    public LibraryThumbnailService(LibraryClient client, Path cacheDirectory) {
        this(client, cacheDirectory, () -> "");
    }

    public LibraryThumbnailService(LibraryClient client, Path cacheDirectory, Supplier<String> administrator) {
        this.client = client;
        this.administrator = administrator;
        cache = new LibraryPreviewCache(cacheDirectory);
    }

    /** Managed heap pixels are safe to discard if cancellation wins before client-thread delivery. */
    public record Pixels(int width, int height, int[] argb, int sourceWidth, int sourceHeight) {
        public Pixels(int width, int height, int[] argb) {
            this(width, height, argb, width, height);
        }

        public Pixels {
            if (width < 1 || height < 1 || width > THUMBNAIL_DIMENSION || height > THUMBNAIL_DIMENSION
                    || argb.length != width * height || sourceWidth < width || sourceHeight < height
                    || sourceWidth > MAX_DIMENSION || sourceHeight > MAX_DIMENSION) {
                throw new IllegalArgumentException("INVALID_THUMBNAIL");
            }
            argb = argb.clone();
        }

        @Override
        public int[] argb() {
            return argb.clone();
        }
    }

    public LibraryClient.Request<Pixels> load(UUID id) {
        return client.submit(LibraryClient.Lane.THUMBNAIL, exchange -> {
            String token = administrator.get();
            if (!token.isBlank()) {
                exchange.adminToken(token);
            }
            LibraryModels.Model model = exchange.json("GET", ROOT + id, null, LibraryModels.Model.class);
            if (!id.equals(model.modelId())) {
                throw new LibraryClient.Failure(0, "INVALID_LIBRARY_RESPONSE");
            }
            if (!model.imageAvailable()) {
                return null;
            }
            var scope = new LibraryPreviewCache.Scope(client.session().instance(), client.session().player(),
                    id, model.imageRevision(), model.policyRevision());
            var existing = cache.find(scope, LibraryPreviewCache.Kind.THUMBNAIL, exchange::requireCurrent);
            if (existing.isPresent()) {
                try (var lease = existing.get()) {
                    return decode(lease.read());
                }
            }
            var response = exchange.send("GET", ROOT + id + "/image", HttpRequest.BodyPublishers.noBody(), null);
            byte[] bytes;
            try (var input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new LibraryClient.Failure(response.statusCode(), "IMAGE_UNAVAILABLE");
                }
                long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                String type = response.headers().firstValue("Content-Type").orElse("");
                if (length < 1 || length > MAX_BYTES || !Set.of("image/png", "image/jpeg").contains(type)) {
                    throw new LibraryClient.Failure(0, "INVALID_IMAGE");
                }
                bytes = CloudHttp.readBounded(input, MAX_BYTES);
                if (bytes.length != length) {
                    throw new LibraryClient.Failure(0, "INVALID_IMAGE");
                }
            }
            exchange.requireCurrent();
            Pixels decoded = decode(bytes);
            // An image replacement or policy change during the stream cannot populate a stale key.
            LibraryModels.Model after = exchange.json("GET", ROOT + id, null, LibraryModels.Model.class);
            if (!id.equals(after.modelId()) || !after.imageAvailable()
                    || after.imageRevision() != model.imageRevision() || after.policyRevision() != model.policyRevision()) {
                throw new LibraryClient.Failure(409, "IMAGE_CHANGED");
            }
            cache.put(new LibraryPreviewCache.Key(scope, CloudHttp.sha256(bytes)),
                    LibraryPreviewCache.Kind.THUMBNAIL, bytes);
            return decoded;
        });
    }

    /** Checks dimensions before allocating decoded pixels and never creates disk-backed ImageIO cache files. */
    public static Pixels decode(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IOException("IMAGE_LIMIT");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("INVALID_IMAGE");
            }
            var reader = readers.next();
            try {
                if (!Set.of("png", "jpeg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) {
                    throw new IOException("UNSUPPORTED_IMAGE");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IOException("IMAGE_DIMENSION_LIMIT");
                }
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + THUMBNAIL_DIMENSION - 1) / THUMBNAIL_DIMENSION);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                var image = reader.read(0, parameters);
                try {
                    int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                    return new Pixels(image.getWidth(), image.getHeight(), argb, width, height);
                } finally {
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    public Handle acquire(UUID id) {
        return acquire(load(id));
    }

    public Handle acquireLocal(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("IMAGE_LIMIT");
        }
        byte[] owned = bytes.clone();
        return acquire(client.submit(LibraryClient.Lane.THUMBNAIL, exchange -> decode(owned)));
    }

    private Handle acquire(LibraryClient.Request<Pixels> request) {
        RenderSystem.assertOnRenderThread();
        if (closed || handles.size() >= 32) {
            request.cancel();
            throw new IllegalStateException("THUMBNAIL_BUDGET");
        }
        var handle = new Handle(request);
        handles.add(handle);
        handle.request.future().whenComplete(handle::publish);
        return handle;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        closed = true;
        for (Handle handle : List.copyOf(handles)) {
            handle.close();
        }
    }

    /** Called after GuiRenderer has consumed the extracted texture views, including exceptional exits. */
    static void finishFrame() {
        for (Handle handle : List.copyOf(FRAMES)) {
            handle.retained = false;
            if (handle.closed) {
                handle.release();
            }
        }
        FRAMES.clear();
    }

    public final class Handle implements AutoCloseable {
        private final LibraryClient.Request<Pixels> request;
        private final CompletableFuture<Boolean> ready = new CompletableFuture<>();
        private Identifier texture;
        private boolean retained;
        private boolean closed;

        private Handle(LibraryClient.Request<Pixels> request) {
            this.request = request;
        }

        public CompletableFuture<Boolean> ready() {
            return ready;
        }

        /** Call while extracting a card. Null means use the packaged ingame_logo placeholder. */
        public Identifier capture() {
            RenderSystem.assertOnRenderThread();
            if (closed || !client.current()) {
                close();
                return null;
            }
            if (texture != null) {
                retained = true;
                FRAMES.add(this);
            }
            return texture;
        }

        private void publish(Pixels pixels, Throwable failure) {
            RenderSystem.assertOnRenderThread();
            if (closed || !client.current()) {
                ready.cancel(false);
                return;
            }
            if (failure != null) {
                ready.completeExceptionally(failure);
                return;
            }
            if (pixels == null) {
                ready.complete(false);
                return;
            }
            var nativeImage = new NativeImage(pixels.width(), pixels.height(), false);
            DynamicTexture uploaded = null;
            try {
                for (int y = 0; y < pixels.height(); y++) {
                    for (int x = 0; x < pixels.width(); x++) {
                        nativeImage.setPixel(x, y, pixels.argb[y * pixels.width() + x]);
                    }
                }
                uploaded = new DynamicTexture(() -> "OmniMorph library thumbnail", nativeImage);
                Identifier identifier = Identifier.fromNamespaceAndPath("omni-morph", "library/" + UUID.randomUUID());
                Minecraft.getInstance().getTextureManager().register(identifier, uploaded);
                texture = identifier;
                ready.complete(true);
            } catch (RuntimeException unavailable) {
                if (uploaded == null) {
                    nativeImage.close();
                } else {
                    uploaded.close();
                }
                ready.completeExceptionally(new LibraryClient.Failure(0, "IMAGE_UPLOAD_FAILED"));
            }
        }

        @Override
        public void close() {
            RenderSystem.assertOnRenderThread();
            if (closed) {
                return;
            }
            closed = true;
            handles.remove(this);
            request.cancel();
            ready.cancel(false);
            if (!retained) {
                release();
            }
        }

        private void release() {
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(texture);
                texture = null;
            }
        }
    }
}
