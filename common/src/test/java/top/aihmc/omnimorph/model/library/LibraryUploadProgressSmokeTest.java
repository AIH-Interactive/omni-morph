package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Server-controlled stage boundaries: no timers simulate validation or review in the client. */
public final class LibraryUploadProgressSmokeTest {
    public static void main(String[] args) throws Exception {
        var validation = new CountDownLatch(1);
        var review = new CountDownLatch(1);
        var allowReview = new CountDownLatch(1);
        var allowResult = new CountDownLatch(1);
        var timeline = new LibraryUploadProgress();
        timeline.advance(LibraryUploadProgress.Stage.UPLOADING);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/library/v1/uploads/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean legacy = exchange.getRequestURI().getPath().endsWith("legacy");
            exchange.getResponseHeaders().set("Content-Type", legacy ? "application/json" : "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                if (legacy) {
                    output.write("{\"published\":true}".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                output.write("{\"stage\":\"VALIDATING\"}\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (exchange.getRequestURI().getPath().endsWith("failure")) {
                    output.write("{\"error\":{\"code\":\"INVALID_REQUEST\",\"status\":400}}\n".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                if (!allowReview.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("review gate timed out");
                }
                output.write("{\"stage\":\"REVIEWING\"}\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (!allowResult.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("result gate timed out");
                }
                output.write(("{\"result\":{\"published\":true,\"visibility\":\"PRIVATE\","
                        + "\"moderation_status\":\"BLOCKED\"}}\n").getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                builder -> builder.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(origin, new UUID(0, 1), 1, () -> true), Runnable::run)) {
            var request = client.submit(LibraryClient.Lane.TRANSFER, exchange -> exchange.uploadJson(
                    "/api/library/v1/uploads/stream", Map.of(), JsonObject.class, stage -> {
                        timeline.advance(LibraryUploadProgress.Stage.valueOf(stage));
                        if (stage.equals("VALIDATING")) {
                            validation.countDown();
                        } else {
                            review.countDown();
                        }
                    }));
            require(validation.await(5, TimeUnit.SECONDS) && !request.future().isDone(),
                    "validation is visible while the actual server is still processing");
            require(timeline.snapshot().stage() == LibraryUploadProgress.Stage.VALIDATING,
                    "client does not advance to review without a server event");
            allowReview.countDown();
            require(review.await(5, TimeUnit.SECONDS) && !request.future().isDone(),
                    "automatic review is visible before the final response");
            require(timeline.snapshot().completed().contains(LibraryUploadProgress.Stage.VALIDATING),
                    "fast completed stages stay in the timeline");
            allowResult.countDown();
            var result = request.future().get(5, TimeUnit.SECONDS);
            require(result.get("moderation_status").getAsString().equals("BLOCKED")
                            && result.get("visibility").getAsString().equals("PRIVATE"),
                    "the result preserves actual moderation rather than treating any publication as approval");
            timeline.advance(LibraryUploadProgress.Stage.RESULT);
            require(timeline.snapshot().completed().contains(LibraryUploadProgress.Stage.REVIEWING), "review completion retained");
            timeline.begin();
            var failed = client.submit(LibraryClient.Lane.TRANSFER, exchange -> exchange.uploadJson(
                    "/api/library/v1/uploads/failure", Map.of(), JsonObject.class,
                    stage -> timeline.advance(LibraryUploadProgress.Stage.valueOf(stage))));
            try {
                failed.future().get(5, TimeUnit.SECONDS);
                throw new AssertionError("validation failure accepted");
            } catch (ExecutionException expected) {
                require(expected.getCause() instanceof LibraryClient.Failure failure && failure.status == 400,
                        "streamed errors retain their real status code");
                timeline.fail();
            }
            require(timeline.snapshot().failed()
                            && !timeline.snapshot().completed().contains(LibraryUploadProgress.Stage.REVIEWING),
                    "failure cannot mark automatic review completed");
            var legacy = client.submit(LibraryClient.Lane.TRANSFER, exchange -> exchange.uploadJson(
                    "/api/library/v1/uploads/legacy", Map.of(), JsonObject.class,
                    stage -> { throw new AssertionError("legacy JSON must not fabricate server stages"); }));
            require(legacy.future().get(5, TimeUnit.SECONDS).get("published").getAsBoolean(), "existing JSON server compatibility");
        } finally {
            allowReview.countDown();
            allowResult.countDown();
            server.stop(0);
        }
        System.out.println("PASS upload progress: real streaming boundaries, retained history, moderation results, failure and JSON compatibility");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
