package top.aihmc.omnimorph.model.outfit;

import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.debug.PerformanceHttpServer;
import top.aihmc.omnimorph.debug.PerformanceSnapshot;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real production HTTP requests collect a loaded Avatar on the test's client thread. */
final class PerformanceHttpSmokeTest {
    private PerformanceHttpSmokeTest() {
    }

    static void verify(Avatar avatar) throws Exception {
        int port;
        try (var reservation = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            port = reservation.getLocalPort();
        }
        var queued = new LinkedBlockingQueue<Runnable>();
        var captures = new AtomicInteger();
        try (var http = HttpClient.newHttpClient();
             var server = new PerformanceHttpServer(queued::add, () -> {
                 captures.incrementAndGet();
                 return PerformanceSnapshot.captureObserved(null, avatar, null, "world");
             })) {
            server.configure(false, port);
            require(server.boundPort() == 0, "Debug disabled does not listen");
            server.configure(true, port);
            require(server.boundPort() == port && server.error() == null, "Debug binds the requested loopback port");
            require(captures.get() == 0, "Starting HTTP does not sample or replay scripts");
            long instructions = (long) avatar.render.pre + avatar.render.post;
            var uri = URI.create("http://127.0.0.1:" + port + PerformanceHttpServer.PATH);
            for (int index = 1; index <= 2; index++) {
                var response = http.sendAsync(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(),
                        HttpResponse.BodyHandlers.ofString());
                Runnable capture = queued.poll(3, TimeUnit.SECONDS);
                require(capture != null && !response.isDone(), "HTTP waits for the client-thread capture");
                capture.run();
                var result = response.get(3, TimeUnit.SECONDS);
                require(result.statusCode() == 200, "The native snapshot request succeeds");
                var json = JsonParser.parseString(result.body()).getAsJsonObject();
                require(json.get("requestId").getAsInt() == index && captures.get() == index,
                        "Every request collects exactly one fresh snapshot");
                require(json.getAsJsonObject("activeModel").get("loaded").getAsBoolean(),
                        "The response describes the actual loaded Avatar");
                require(json.getAsJsonObject("metrics").has("lua.render.instructions")
                                && json.has("capturedFrame") && json.has("capturedAt"),
                        "The response carries measured values, frame identity and request time");
            }
            require((long) avatar.render.pre + avatar.render.post == instructions,
                    "Snapshot collection does not execute or charge rendering scripts");
            var post = http.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            require(post.statusCode() == 405 && captures.get() == 2, "Writes are rejected without sampling");
            var missing = http.send(HttpRequest.newBuilder(uri.resolve("/missing")).build(),
                    HttpResponse.BodyHandlers.ofString());
            require(missing.statusCode() == 404 && captures.get() == 2, "Unknown routes do not capture");
            server.configure(false, port);
            require(server.boundPort() == 0 && queued.isEmpty(), "Disabling Debug releases the listener");
        }
        System.out.println("PASS native performance HTTP: loaded Avatar, fresh client-thread snapshots, no replay and Debug lifecycle");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
