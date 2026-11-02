package top.aihmc.omnimorph.backend2.protocol;

import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;

import java.lang.reflect.Proxy;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the actual listener boundary on an unrelated worker class-loader context. */
public final class CloudSocketContextSmokeTest {
    public static void main(String[] args) {
        CompletableFuture.runAsync(CloudSocketContextSmokeTest::checkWorker).join();
        System.out.println("PASS socket binary/text/failure callback loader scopes, ACK parsing and restoration");
        System.out.println("Not covered: full ModLauncher transformation or a live backend connection");
    }

    private static void checkWorker() {
        Thread worker = Thread.currentThread();
        ClassLoader previous = worker.getContextClassLoader();
        ClassLoader unrelated = new ClassLoader(null) {};
        WebSocket wire = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(),
                new Class<?>[] {WebSocket.class}, (proxy, method, values) -> null);
        worker.setContextClassLoader(unrelated);
        try {
            AtomicInteger received = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            byte[] ack = Cloud.Envelope.newBuilder().setMajor(1)
                    .setAck(Cloud.Ack.newBuilder()).build().toByteArray();
            try (CloudSocket socket = new CloudSocket(65536, bytes -> {
                checkContext();
                try {
                    require(CloudWire.decode(bytes).hasAck(), "ACK parsed at listener boundary");
                } catch (java.io.IOException error) {
                    throw new AssertionError(error);
                }
                received.incrementAndGet();
            }, reason -> { throw new AssertionError(reason); })) {
                socket.onBinary(wire, ByteBuffer.wrap(ack), false);
                require(received.get() == 0, "fragment stays buffered");
                socket.onBinary(wire, ByteBuffer.allocate(0), true);
                require(received.get() == 1, "complete ACK delivered exactly once");
            }
            require(worker.getContextClassLoader() == unrelated, "binary restores context");
            try (CloudSocket socket = new CloudSocket(65536, bytes -> {
                checkContext();
                require(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).equals("hello"), "text reassembly");
                throw new IllegalStateException("expected callback failure");
            }, reason -> {
                checkContext();
                failed.incrementAndGet();
            }, null, true)) {
                socket.onText(wire, "hel", false);
                socket.onText(wire, "lo", true);
            }
            require(failed.get() == 1, "receive failure follows existing failure path");
            require(worker.getContextClassLoader() == unrelated, "text failure restores context");
            Error expected = new AssertionError("expected fatal callback failure");
            try (CloudSocket socket = new CloudSocket(65536, bytes -> { throw expected; }, reason -> {})) {
                try {
                    socket.onBinary(wire, ByteBuffer.wrap(ack), true);
                    throw new AssertionError("fatal error swallowed");
                } catch (Error actual) {
                    require(actual == expected, "fatal error propagates unchanged");
                }
            }
            require(worker.getContextClassLoader() == unrelated, "fatal error restores context");
            try (CloudSocket socket = new CloudSocket(65536, bytes -> {}, reason -> {
                checkContext();
                throw expected;
            })) {
                try {
                    socket.onError(wire, new Exception("connection failed"));
                    throw new AssertionError("failure callback error swallowed");
                } catch (Error actual) {
                    require(actual == expected, "failure callback error propagates");
                }
            }
            require(worker.getContextClassLoader() == unrelated, "failure callback restores context");
        } finally {
            worker.setContextClassLoader(previous);
        }
    }

    private static void checkContext() {
        require(Thread.currentThread().getContextClassLoader() == CloudSocket.class.getClassLoader(),
                "callback uses mod loader");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
