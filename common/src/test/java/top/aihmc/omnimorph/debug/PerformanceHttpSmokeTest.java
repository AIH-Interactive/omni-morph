package top.aihmc.omnimorph.debug;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the production loopback listener; no Minecraft window or rendering is involved. */
public final class PerformanceHttpSmokeTest {
    public static void main(String[] args) throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        var captures = new AtomicInteger();
        try (var client = HttpClient.newHttpClient();
             var server = new PerformanceHttpServer(Runnable::run, () -> {
                 var snapshot = new JsonObject();
                 snapshot.addProperty("observed", captures.incrementAndGet());
                 return snapshot;
             })) {
            server.configure(false, port);
            require(server.boundPort() == 0 && captures.get() == 0, "Debug disabled keeps listener closed");
            server.configure(true, port);
            require(server.boundPort() == port && server.error() == null, "Debug opens the loopback listener");
            URI uri = URI.create("http://127.0.0.1:" + port + PerformanceHttpServer.PATH);
            for (int index = 1; index <= 2; index++) {
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                require(response.statusCode() == 200, "GET returns a snapshot");
                var snapshot = JsonParser.parseString(response.body()).getAsJsonObject();
                require(snapshot.get("requestId").getAsInt() == index
                                && snapshot.get("observed").getAsInt() == index
                                && captures.get() == index,
                        "Each request collects exactly one fresh snapshot with its own sequence");
                require(snapshot.get("schemaVersion").getAsInt() == 1
                                && snapshot.has("capturedAt") && snapshot.has("request"),
                        "Response preserves the documented envelope");
            }
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            require(response.statusCode() == 405 && captures.get() == 2, "Listener is read only");
            server.configure(false, port);
            require(server.boundPort() == 0, "Disabling Debug closes the listener");
        }
        System.out.println("PASS production performance HTTP: Debug lifecycle, fresh sequential GET, envelope and read-only requests");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
