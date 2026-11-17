package top.aihmc.omnimorph.debug;

import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real loopback requests collect loaded-avatar state through the client executor exactly on request. */
public final class PerformanceHttpSmokeTest {
    public static void main(String[] args) throws Exception {
        YsmMaterialNativeSmokeTest.replayFiguraModel(Path.of(args[0]), avatar -> {
            try {
                verify(avatar);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static void verify(Avatar avatar) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var captures = new AtomicInteger();
        Thread clientThread = Thread.currentThread();
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (var service = new PerformanceHttpServer(queue::add, () -> {
            require(Thread.currentThread() == clientThread, "Snapshot executes on the client executor");
            captures.incrementAndGet();
            return PerformanceSnapshot.captureObserved(null, avatar, null, "fixture");
        }); var client = HttpClient.newHttpClient()) {
            service.configure(false, port);
            require(service.boundPort() == 0, "Debug off opens no listener");
            service.configure(true, port);
            require(service.boundPort() == port && service.error() == null, "Debug on binds loopback");
            require(queue.isEmpty() && captures.get() == 0, "Binding does not collect a snapshot");
            URI uri = URI.create("http://127.0.0.1:" + port + PerformanceHttpServer.PATH);
            for (int index = 1; index <= 2; index++) {
                var response = client.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).build(),
                        HttpResponse.BodyHandlers.ofString());
                Runnable capture = queue.poll(5, TimeUnit.SECONDS);
                require(capture != null, "Each HTTP request dispatches a fresh client task");
                capture.run();
                var result = response.get(8, TimeUnit.SECONDS);
                require(result.statusCode() == 200, "Snapshot returns HTTP 200");
                var json = JsonParser.parseString(result.body()).getAsJsonObject();
                require(json.get("requestId").getAsInt() == index && captures.get() == index,
                        "One capture and increasing sequence number per request");
                require(json.get("schemaVersion").getAsInt() == 1 && json.has("activeModel")
                                && json.has("metrics") && json.has("references"),
                        "Actual loaded-avatar collector supplies the documented contract");
                require(queue.isEmpty(), "The server does not schedule background sampling");
            }
            var write = client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            require(write.statusCode() == 405 && captures.get() == 2, "POST cannot collect or mutate state");
            service.configure(false, port);
            require(service.boundPort() == 0, "Disabling debug retires the listener");
        }
        System.out.println("PASS performance HTTP: real loopback, loaded-avatar metrics, client-thread capture, "
                + "one request/one snapshot, sequence, Debug lifecycle and read-only method; game unverified");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
