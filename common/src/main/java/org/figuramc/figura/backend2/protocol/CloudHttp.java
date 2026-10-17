package org.figuramc.figura.backend2.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** One connection owns its requests and streams; closing it cancels all remaining work. */
public final class CloudHttp implements AutoCloseable {
    public static final int CONTROL_BYTES = 65536;
    public static final long FILE_BYTES = 64L * 1024 * 1024;
    private final HttpClient client;
    private final CloudEndpoint endpoint;
    private final Set<CompletableFuture<?>> requests = ConcurrentHashMap.newKeySet();
    private final Set<InputStream> streams = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public CloudHttp(HttpClient client) {
        this(client, null);
    }

    public CloudHttp(HttpClient client, CloudEndpoint endpoint) {
        if (client.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("backend.endpoint.redirect_blocked");
        }
        this.client = client;
        this.endpoint = endpoint;
    }

    public HttpResponse<InputStream> send(HttpRequest request) throws Exception {
        requireOpen();
        if (endpoint != null) {
            endpoint.requireRequest(request.uri());
        } else if (!request.uri().getScheme().equals("https")) {
            throw new IOException("backend.endpoint.debug_required");
        }
        CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofInputStream());
        requests.add(future);
        if (closed) {
            future.cancel(true);
        }
        try {
            HttpResponse<InputStream> response = future.get();
            streams.add(response.body());
            if (closed) {
                response.body().close();
                throw new IOException("Connection retired");
            }
            try {
                requireOpen();
            } catch (IOException retired) {
                release(response.body());
                throw retired;
            }
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                release(response.body());
                throw new IOException("backend.endpoint.redirect_blocked");
            }
            return response;
        } finally {
            requests.remove(future);
        }
    }

    public byte[] control(HttpRequest request) throws Exception {
        HttpResponse<InputStream> response = send(request);
        try (InputStream body = response.body()) {
            byte[] bytes = readBounded(body, CONTROL_BYTES);
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return bytes;
        } finally {
            streams.remove(response.body());
        }
    }

    public InputStream managed(InputStream stream) {
        return new java.io.FilterInputStream(stream) {
            @Override
            public void close() throws IOException {
                release(stream);
            }
        };
    }

    public void release(InputStream stream) throws IOException {
        streams.remove(stream);
        stream.close();
    }

    public static byte[] readBounded(InputStream body, int limit) throws IOException {
        byte[] bytes = body.readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw new IOException("Response exceeds byte budget");
        }
        return bytes;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static URI relative(URI origin, String path) throws IOException {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
            throw new IOException("Invalid resource path");
        }
        URI target = origin.resolve(path);
        if (!origin.getScheme().equals(target.getScheme()) || !origin.getRawAuthority().equals(target.getRawAuthority())
                || target.getRawQuery() != null || target.getRawFragment() != null) {
            throw new IOException("Invalid resource origin");
        }
        return target;
    }

    public static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60));
    }

    public void requireOpen() throws IOException {
        if (endpoint != null) {
            endpoint.requireAllowed();
        }
        if (closed) {
            throw new IOException("Connection retired");
        }
    }

    @Override
    public void close() {
        closed = true;
        requests.forEach(request -> request.cancel(true));
        requests.clear();
        streams.forEach(stream -> {
            try {
                stream.close();
            } catch (IOException ignored) {
                // Cancellation is best effort; epoch guards reject every late result.
            }
        });
        streams.clear();
    }
}
