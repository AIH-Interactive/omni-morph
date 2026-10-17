package org.figuramc.figura.backend2.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Bounded reassembly and ordered writes for a single immutable connection scope. */
public final class CloudSocket implements WebSocket.Listener, AutoCloseable {
    private final Consumer<byte[]> receive;
    private final Consumer<String> failure;
    private final int limit;
    private final ByteArrayOutputStream incoming = new ByteArrayOutputStream();
    private CompletableFuture<WebSocket> writes;
    private WebSocket socket;
    private boolean closed;
    private int queuedBytes;
    private int queuedMessages;

    public CloudSocket(int limit, Consumer<byte[]> receive, Consumer<String> failure) {
        this.limit = limit;
        this.receive = receive;
        this.failure = failure;
    }

    public synchronized void attach(WebSocket socket) throws IOException {
        if (closed) {
            socket.abort();
            throw new IOException("Connection retired");
        }
        this.socket = socket;
        writes = CompletableFuture.completedFuture(socket);
    }

    public synchronized boolean send(byte[] bytes) throws IOException {
        if (closed || socket == null || bytes.length > limit) {
            throw new IOException("Socket unavailable or message exceeds budget");
        }
        if (queuedMessages >= 32 || queuedBytes + bytes.length > 262144) {
            throw new IOException("Socket queue requires recovery");
        }
        queuedMessages++;
        queuedBytes += bytes.length;
        byte[] copy = bytes.clone();
        writes = writes.thenCompose(current -> current.sendBinary(ByteBuffer.wrap(copy), true))
                .orTimeout(5, TimeUnit.SECONDS);
        writes.whenComplete((ignored, error) -> {
            synchronized (this) {
                queuedMessages--;
                queuedBytes -= copy.length;
            }
            if (error != null) {
                CompletableFuture.runAsync(() -> fail("Socket write failed"));
            }
        });
        return true;
    }

    @Override
    public void onOpen(WebSocket socket) {
        socket.request(1);
    }

    @Override
    public CompletableFuture<?> onBinary(WebSocket socket, ByteBuffer bytes, boolean last) {
        try {
            byte[] complete = null;
            synchronized (this) {
                if (closed) {
                    return CompletableFuture.completedFuture(null);
                }
                if (bytes.remaining() > limit - incoming.size()) {
                    throw new IOException("Socket message exceeds budget");
                }
                byte[] fragment = new byte[bytes.remaining()];
                bytes.get(fragment);
                incoming.write(fragment);
                if (last) {
                    complete = incoming.toByteArray();
                    incoming.reset();
                }
            }
            if (complete != null) {
                receive.accept(complete);
            }
        } catch (Exception exception) {
            fail("Invalid socket message");
        }
        socket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> onText(WebSocket socket, CharSequence text, boolean last) {
        fail("Unexpected text message");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> onClose(WebSocket socket, int statusCode, String reason) {
        fail("Socket closed: " + statusCode);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
        fail("Socket connection failed");
    }

    private void fail(String reason) {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        close();
        failure.accept(reason);
    }

    @Override
    public synchronized void close() {
        closed = true;
        incoming.reset();
        if (socket != null) {
            socket.abort();
        }
        if (writes != null) {
            writes.cancel(true);
        }
    }
}
