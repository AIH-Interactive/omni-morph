package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Runs actual screen request/mutation callbacks, without initializing or rendering GUI widgets. */
public final class ModelLibraryCatalogSmokeTest {
    private static final UUID MODEL = new UUID(0, 11);
    private static final UUID VIEWER = new UUID(0, 1);

    public static void run(Runnable drain) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var removed = new AtomicBoolean();
        var mutations = new AtomicInteger();
        var forbiddenRoutes = new AtomicInteger();
        var body = new AtomicReference<JsonObject>();
        var query = new AtomicReference<String>();
        var threads = Executors.newFixedThreadPool(4);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(threads);
        String prefix = "/api/library/v1/models";
        server.createContext(prefix, exchange -> {
            try {
                String result = "{}";
                if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals(prefix)) {
                    String requested = exchange.getRequestURI().getRawQuery();
                    query.set(requested);
                    if (requested.contains("old-query")) {
                        entered.countDown();
                        release.await(10, TimeUnit.SECONDS);
                    }
                    String name = requested.contains("old-query") ? "old" : "new";
                    String item = "{\"model_id\":\"" + MODEL + "\",\"revision\":1,\"policy_revision\":7,"
                            + "\"display_name\":\"" + name + "\",\"internal_name\":\"Internal\",\"authors\":[],"
                            + "\"uploader\":{\"uuid\":\"" + VIEWER + "\",\"name\":\"owner\"},"
                            + "\"model_type\":\"FIGURA\",\"visibility\":\"PUBLIC\","
                            + "\"allowed_actions\":[\"patch\",\"delete\",\"favorite\"]}";
                    result = "{\"items\":[" + (removed.get() ? "" : item) + "],\"total\":"
                            + (removed.get() ? 0 : 1) + ",\"page\":1,\"page_size\":8,\"has_next\":false}";
                } else if (exchange.getRequestURI().getPath().equals(prefix + "/" + MODEL)
                        || exchange.getRequestURI().getPath().equals(prefix + "/" + MODEL + "/favorite")) {
                    body.set(JsonParser.parseString(new String(exchange.getRequestBody().readNBytes(65536),
                            StandardCharsets.UTF_8)).getAsJsonObject());
                    mutations.incrementAndGet();
                    if (exchange.getRequestMethod().equals("DELETE")
                            && !exchange.getRequestURI().getPath().endsWith("/favorite")) {
                        removed.set(true);
                    }
                } else {
                    forbiddenRoutes.incrementAndGet();
                }
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            } catch (IOException cancelled) {
                // The old search body is deliberately cancelled while the handler is blocked.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                request -> request.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(origin, VIEWER, 1, () -> true),
                     Minecraft.getInstance()::execute)) {
            var screen = new ModelLibraryScreen(null);
            screen.width = 960;
            screen.height = 540;
            set(screen, "client", client);
            set(screen, "capabilities", new LibraryModels.Capabilities(true, 1, 1024, 64, 1024, 256, 256,
                    List.of("zip"), List.of("FIGURA"), List.of("general"), false));
            set(screen, "query", "old-query");
            invoke(screen, "refresh");
            require(entered.await(10, TimeUnit.SECONDS), "old search reached server");
            set(screen, "query", "新查询");
            set(screen, "sort", 3);
            set(screen, "scope", 0);
            invoke(screen, "refresh");
            await(() -> get(screen, "results") != null, drain);
            var page = (LibraryModels.Page) get(screen, "results");
            require(page.items().getFirst().displayName().equals("new"), "new search owns visible results");
            require(query.get().contains("scope=mine") && query.get().contains("sort=name")
                    && query.get().contains("page_size=12") && query.get().contains("%E6%96%B0"),
                    "server query owns filtering, page budget and sort");
            release.countDown();
            drain.run();
            require(((LibraryModels.Page) get(screen, "results")).items().getFirst().displayName().equals("new"),
                    "late old response cannot replace the current search");
            select(screen, page.items().getFirst());
            long firstGeneration = (long) get(screen, "generation");
            invoke(screen, "toggleVisibility");
            await(() -> mutations.get() == 1 && (long) get(screen, "generation") > firstGeneration
                    && get(screen, "results") != null, drain);
            require(body.get().get("expected_revision").getAsLong() == 7, "mutation CAS uses policy revision");
            select(screen, page.items().getFirst());
            var favorite = ModelLibraryScreen.class.getDeclaredMethod("favorite", LibraryModels.Model.class);
            favorite.setAccessible(true);
            long secondGeneration = (long) get(screen, "generation");
            favorite.invoke(screen, page.items().getFirst());
            await(() -> mutations.get() == 2 && (long) get(screen, "generation") > secondGeneration
                    && get(screen, "results") != null, drain);
            select(screen, page.items().getFirst());
            // Submit the confirmed-delete path; visible confirmation interaction remains manual.
            set(screen, "deleteArmed", MODEL);
            long thirdGeneration = (long) get(screen, "generation");
            invoke(screen, "delete");
            await(() -> mutations.get() == 3 && (long) get(screen, "generation") > thirdGeneration
                    && get(screen, "results") != null, drain);
            require(get(screen, "selected") == null
                    && ((LibraryModels.Page) get(screen, "results")).items().isEmpty(),
                    "hidden/deleted result retires old client selection");
            require(forbiddenRoutes.get() == 0, "catalog/search/favorite never invokes upload or equip");
            invoke(screen, "closeServices");
            System.out.println("PASS actual catalog callbacks: query generation, filtering, policy CAS, favorite and hidden selection cleanup");
        } finally {
            release.countDown();
            server.stop(0);
            threads.shutdownNow();
        }
    }

    private static void select(ModelLibraryScreen screen, LibraryModels.Model model) throws Exception {
        var method = ModelLibraryScreen.class.getDeclaredMethod("select", LibraryModels.Model.class);
        method.setAccessible(true);
        method.invoke(screen, model);
    }

    private static void invoke(ModelLibraryScreen screen, String name) throws Exception {
        var method = ModelLibraryScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(screen);
    }

    private static void set(ModelLibraryScreen screen, String name, Object value) throws Exception {
        var field = ModelLibraryScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(screen, value);
    }

    private static Object get(ModelLibraryScreen screen, String name) {
        try {
            var field = ModelLibraryScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(screen);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void await(BooleanSupplier complete, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!complete.getAsBoolean() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(5);
        }
        require(complete.getAsBoolean(), "catalog callback completed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
