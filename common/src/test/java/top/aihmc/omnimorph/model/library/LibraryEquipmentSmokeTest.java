package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.avatar.local.CacheAvatarLoader;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudConnection;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Actual formal orchestration/Avatar publication with HTTP fixtures and a recording profile adapter. */
public final class LibraryEquipmentSmokeTest {
    @SuppressWarnings("unchecked")
    public static void run(Runnable drain) throws Exception {
        UUID player = FiguraMod.getLocalPlayerUUID();
        var connection = field(NetworkStuff.class, "connection");
        Object previousConnection = connection.get(null);
        int previousStatus = NetworkStuff.backendStatus;
        boolean previousUploaded = AvatarManager.localUploaded;
        var users = (Map<UUID, UserData>) field(AvatarManager.class, "LOADED_USERS").get(null);
        UserData previousUser = users.get(player);
        var user = new UserData(player);
        try (var server = new Server(player)) {
            var protocol = (CloudProtocol) Proxy.newProxyInstance(CloudProtocol.class.getClassLoader(),
                    new Class<?>[]{CloudProtocol.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "connected" -> true;
                        case "close", "localSelectionChanged" -> null;
                        case "profile" -> {
                            require(player.equals(arguments[0]), "profile refresh belongs to the authenticated player");
                            String id = server.wrongProfile ? "unrelated_asset" : server.alias();
                            yield new CloudProtocol.Reply(200, "{\"equipped\":[{\"owner\":\"" + player
                                    + "\",\"id\":\"" + id + "\",\"hash\":\"" + server.hash() + "\"}]}");
                        }
                        default -> throw new AssertionError("Formal source must not call wardrobe upload/equip/download: " + method.getName());
                    });
            var scope = new CloudConnection(NetworkStuff.connectionEpoch(), protocol);
            connection.set(null, scope);
            NetworkStuff.backendStatus = 3;
            users.put(player, user);
            await(user.loadAvatar(model("resident")), drain);
            var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
            try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(),
                    request -> request.header("Authorization", "Bearer synthetic-player"));
                 var client = new LibraryClient(access,
                         new LibrarySession(server.origin(), player, scope.epoch, scope::active), Minecraft.getInstance()::execute);
                 var service = new LibraryEquipmentService(client, new LibraryTransferService(client, FiguraMod.getCacheDirectory()))) {
                var first = service.enable(server.model);
                var equipped = await(first.future(), drain);
                require(equipped.equipmentRevision() == 1 && server.commits == 1 && server.files == 1
                                && user.getMainAvatar().name.equals("source model") && AvatarManager.localUploaded,
                        "verified formal data prepares, commits and becomes the actual resident avatar");
                require(!server.lastInput.get("show_private_equipment").getAsBoolean(), "private rendering defaults closed");
                Path directory = CacheAvatarLoader.getAvatarCacheDirectory();
                String cacheKey = server.hash();
                require(Files.isRegularFile(directory.resolve(cacheKey + ".oma")), "official wrapped formal cache is populated");
                try (var mappings = Files.list(directory)) {
                    Path mapping = mappings.filter(path -> path.getFileName().toString().startsWith(cacheKey + ".")
                            && path.toString().endsWith(".library-source.json")).findFirst().orElseThrow();
                    var source = JsonParser.parseString(Files.readString(mapping)).getAsJsonObject();
                    require(source.get("viewer").getAsString().equals(player.toString())
                                    && source.get("modelId").getAsString().equals(server.model.toString())
                                    && source.get("visibility").getAsString().equals("PRIVATE"),
                            "source mapping retains authenticated viewer and library provenance");
                }

                Avatar retained = user.getMainAvatar();
                server.broken = true;
                var invalid = service.enable(server.model);
                failure(invalid.future(), drain, "MODEL_PREPARATION_FAILED");
                require(server.commits == 1 && user.getMainAvatar() == retained,
                        "runtime preparation failure cannot commit or replace the resident model");
                server.broken = false;

                server.loseResponse = true;
                var retry = service.enable(server.model, true);
                UUID operation = retry.operationId();
                failure(retry.future(), drain, "BUSY");
                require(server.commits == 2 && user.getMainAvatar() == retained,
                        "a committed response failure retains the old local avatar");
                int transferred = server.files;
                var replay = await(retry.retry(), drain);
                require(retry.operationId().equals(operation) && server.commits == 2 && server.replays == 1
                                && server.files == transferred && replay.showPrivateEquipment(),
                        "retry rechecks authority and replays the exact operation/CAS instead of committing a second time");

                retained = user.getMainAvatar();
                server.wrongProfile = true;
                var wrong = service.enable(server.model);
                failure(wrong.future(), drain, "LIBRARY_REQUEST_FAILED");
                require(user.getMainAvatar() == retained, "an unrelated profile identity cannot publish formal bytes");
                server.wrongProfile = false;
                await(wrong.retry(), drain);

                retained = user.getMainAvatar();
                // Exercise cancellation on a cache miss rather than bypassing the stream with a valid hit.
                String formalIdentity = client.session().instance() + "\n" + player + "\n" + server.model;
                String formalName = CloudHttp.sha256(formalIdentity.getBytes(StandardCharsets.UTF_8));
                Files.deleteIfExists(FiguraMod.getCacheDirectory().resolve("avatars")
                        .resolve("resource-" + formalName + ".oma"));
                server.blockBody = true;
                var cancelled = service.enable(server.model);
                Path partials = FiguraMod.getCacheDirectory().resolve("avatars/model-library-downloads");
                waitFor(() -> server.bodyStarted.getCount() == 0 && hasFile(partials), drain);
                cancelled.close();
                server.bodyRelease.countDown();
                waitFor(() -> !hasFile(partials), drain);
                require(cancelled.future().isCancelled() && user.getMainAvatar() == retained,
                        "cancelling an actual blocked file stream cleans partials and retains equipment");
                require(server.badReplay == 0, "every replay preserves the same model, intent and expected revision");
                System.out.println("PASS formal library use: real HTTP download, runtime-before-equip, cache provenance, lost-response replay, identity guard and cancellation; GUI MANUAL/PENDING");
            } finally {
                scope.close();
            }
        } finally {
            NetworkStuff.localSelectionChanged();
            NetworkStuff.clear(player);
            user.clear();
            drain.run();
            if (previousUser == null) {
                users.remove(player);
            } else {
                users.put(player, previousUser);
            }
            connection.set(null, previousConnection);
            NetworkStuff.backendStatus = previousStatus;
            AvatarManager.localUploaded = previousUploaded;
        }
    }

    private static boolean hasFile(Path directory) {
        try (var files = Files.list(directory)) {
            return files.findAny().isPresent();
        } catch (java.io.IOException missing) {
            return false;
        }
    }

    private static CompoundTag model(String name) {
        var metadata = new CompoundTag();
        metadata.putString("name", name);
        var tag = new CompoundTag();
        tag.put("metadata", metadata);
        return tag;
    }

    private static java.lang.reflect.Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T await(CompletableFuture<T> future, Runnable drain) throws Exception {
        waitFor(future::isDone, drain);
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, Runnable drain, String code) throws Exception {
        try {
            await(future, drain);
            throw new AssertionError("Expected formal-use failure");
        } catch (ExecutionException error) {
            require(error.getCause() instanceof LibraryClient.Failure failure && failure.code.equals(code),
                    "formal failure code: " + error.getCause());
        }
    }

    private static void waitFor(BooleanSupplier condition, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        require(condition.getAsBoolean(), "bounded formal-use condition completes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final UUID model = new UUID(0, 11);
        private final UUID player;
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(4);
        private final Map<String, JsonObject> inputs = new HashMap<>();
        private final Map<String, JsonObject> results = new HashMap<>();
        private final CountDownLatch bodyStarted = new CountDownLatch(1);
        private final CountDownLatch bodyRelease = new CountDownLatch(1);
        private volatile boolean broken, loseResponse, wrongProfile, blockBody;
        private volatile long revision;
        private volatile int commits, files, replays, badReplay;
        private volatile JsonObject lastInput;

        private Server(UUID player) throws Exception {
            this.player = player;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::handle);
            server.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private String alias() {
            return "lib_" + model.toString().replace("-", "") + "_1";
        }

        private byte[] bytes() throws java.io.IOException {
            var tag = LibraryEquipmentSmokeTest.model("source model");
            if (broken) {
                tag.getCompoundOrEmpty("metadata").putString("format", "ysm-native");
                tag.put("ysm", new CompoundTag());
            }
            var output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            return output.toByteArray();
        }

        private String hash() throws java.io.IOException {
            return CloudHttp.sha256(bytes());
        }

        private JsonObject object(String text) {
            return JsonParser.parseString(text).getAsJsonObject();
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                JsonObject response;
                int status = 200;
                if (path.equals("/api/library/v1/session")) {
                    response = object("{\"uuid\":\"" + player + "\",\"name\":\"Synthetic\",\"admin\":false,\"equipment_revision\":" + revision + "}");
                } else if (path.equals("/api/library/v1/models/" + model)) {
                    response = object("{\"model_id\":\"" + model + "\",\"revision\":1,\"policy_revision\":1,"
                            + "\"display_name\":\"Synthetic\",\"internal_name\":\"Synthetic\",\"authors\":[],"
                            + "\"uploader\":{\"uuid\":\"" + player + "\",\"name\":\"Synthetic\"},"
                            + "\"model_type\":\"" + (broken ? "YSM_PLAIN" : "FIGURA") + "\",\"visibility\":\"PRIVATE\","
                            + "\"requires_authorization\":true,\"byte_length\":" + bytes().length + "}");
                    JsonArray actions = new JsonArray();
                    for (String action : new String[]{"preview", "download", "equip"}) {
                        actions.add(action);
                    }
                    response.add("allowed_actions", actions);
                } else if (path.endsWith("/transfers") && path.startsWith("/api/library/v1/models/")) {
                    UUID transfer = UUID.randomUUID();
                    response = object("{\"transfer_id\":\"" + transfer + "\",\"purpose\":\"download\",\"model_revision\":1,"
                            + "\"policy_revision\":1,\"relative_path\":\"/api/library/v1/transfers/" + transfer + "\",\"expires_at\":2000000000}");
                } else if (path.startsWith("/api/library/v1/transfers/") && !path.endsWith("/complete")) {
                    files++;
                    byte[] bytes = bytes();
                    exchange.getResponseHeaders().add("x-omnimorph-sha256", CloudHttp.sha256(bytes));
                    exchange.sendResponseHeaders(200, bytes.length);
                    if (blockBody) {
                        // Deliver a real partial body before waiting for client-side
                        // cancellation; otherwise some HTTP clients await the first
                        // body byte before returning the streaming response.
                        exchange.getResponseBody().write(bytes, 0, 1);
                        exchange.getResponseBody().flush();
                        bodyStarted.countDown();
                        try {
                            bodyRelease.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                        return;
                    }
                    exchange.getResponseBody().write(bytes);
                    return;
                } else if (path.endsWith("/complete")) {
                    response = new JsonObject();
                } else if (path.equals("/api/library/v1/models/" + model + "/equip")) {
                    JsonObject input = object(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    lastInput = input;
                    String operation = input.get("operation_id").getAsString();
                    synchronized (results) {
                        if (results.containsKey(operation)) {
                            replays++;
                            if (!inputs.get(operation).equals(input)) {
                                badReplay++;
                            }
                            response = results.get(operation);
                        } else if (input.get("expected_equipment_revision").getAsLong() != revision) {
                            status = 409;
                            response = object("{\"code\":\"CONFLICT\"}");
                        } else {
                            commits++;
                            revision++;
                            response = object("{\"model_id\":\"" + model + "\",\"model_revision\":1,\"equipment_revision\":" + revision
                                    + ",\"owner\":\"" + player + "\",\"asset_id\":\"" + alias() + "\",\"asset_revision\":1,"
                                    + "\"show_private_equipment\":" + input.get("show_private_equipment").getAsBoolean() + "}");
                            inputs.put(operation, input.deepCopy());
                            results.put(operation, response.deepCopy());
                            if (loseResponse) {
                                loseResponse = false;
                                status = 503;
                                response = object("{\"code\":\"BUSY\"}");
                            }
                        }
                    }
                } else {
                    status = 404;
                    response = object("{\"code\":\"NOT_FOUND\"}");
                }
                byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            }
        }

        @Override
        public void close() {
            bodyRelease.countDown();
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
