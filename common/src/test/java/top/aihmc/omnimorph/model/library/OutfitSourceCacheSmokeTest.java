package top.aihmc.omnimorph.model.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.cache.ModelCacheLayout;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Production cache/NBT reader with native fixture bytes over real HTTP; no renderer or Rust-listener claim. */
public final class OutfitSourceCacheSmokeTest {
    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    public static void run(Path directory, LibraryPackagePreparation.Prepared source) throws Exception {
        try (var server = new Server(source)) {
            var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
            var session = new LibrarySession(server.origin(), new UUID(0, 1), 1, () -> true);
            Path root = Files.createDirectory(directory.resolve("private-cache-" + UUID.randomUUID()));
            try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request
                    .header("authorization", "Bearer cache-test").header("x-omnimorph-auth-mode", "figura"));
                 var client = new LibraryClient(access, session, Runnable::run)) {
                var service = new OutfitSourceService(client, root);
                var first = service.read(server.reference, server.kind).future().get(5, TimeUnit.SECONDS);
                require(first.nbt().equals(source.nbt()), "fixed distributed native input is prepared unchanged");
                first.nbt().putString("mutable", "copy");
                var second = service.read(server.reference, server.kind).future().get(5, TimeUnit.SECONDS);
                require(!second.nbt().contains("mutable") && first.binding().sameSource(second.binding())
                                && server.bodies.get() == 1 && server.lookups.get() == 4,
                        "cache hit skips body but repeats current admission before and after native parsing");
                try (var restarted = new LibraryClient(access, session, Runnable::run)) {
                    new OutfitSourceService(restarted, root).read(server.reference, server.kind)
                            .future().get(5, TimeUnit.SECONDS);
                }
                require(server.bodies.get() == 1, "a new client reuses authenticated encrypted cache bytes");
                Path cache;
                try (var files = Files.walk(root)) {
                    cache = files.filter(path -> path.getFileName().toString().startsWith("outfit-source-")
                            && !path.toString().endsWith(".part")).findFirst().orElseThrow();
                }
                require(ModelCacheCodec.isWrapped(cache) && cache.toString().endsWith(ModelCacheLayout.extension(server.kind.name())),
                        "native sources use existing protected avatar/cosmetic cache formats");
                Files.write(cache, new byte[40]);
                service.read(server.reference, server.kind).future().get(5, TimeUnit.SECONDS);
                require(server.bodies.get() == 2 && ModelCacheCodec.isWrapped(cache), "corrupt cache is fetched and replaced");
                server.mode = "revoked";
                failure(service, server, 403, "FORBIDDEN");
                require(server.bodies.get() == 2, "cached bytes cannot bypass current authorization");
                Files.delete(cache);
                for (String mode : List.of("bad-header", "bad-bytes", "race")) {
                    server.mode = mode;
                    server.lookups.set(0);
                    failure(service, server, mode.equals("race") ? 403 : 0,
                            mode.equals("race") ? "FORBIDDEN" : mode.equals("bad-header")
                                    ? "INVALID_OUTFIT_SOURCE_BODY" : "DISTRIBUTION_HASH_MISMATCH");
                    require(!Files.exists(cache), "failed body or final admission cannot publish a cache: " + mode);
                }
                server.mode = "normal";
                service.read(server.reference, server.kind).future().get(5, TimeUnit.SECONDS);
                require(ModelCacheCodec.isWrapped(cache), "normal source remains recoverable after rejected transfers");
                testObserverSources(root, client, server, source);
                testApplyDependencies(directory, root, client, server, source);
                if (source.metadata().modelType().equals("FIGURA") && server.kind == OutfitConfig.ResourceKind.AVATAR) {
                    testFirstPrivateUpload(directory, root, client, server);
                }
            }
            System.out.println("PASS fixed private source cache " + source.metadata().modelType() + "/" + source.resourceKind()
                    + ": native bytes, protected storage, fresh admission, restart, corruption, hash/header and revoke races");
        }
    }

    private static void testObserverSources(Path root, LibraryClient client, Server server,
                                             LibraryPackagePreparation.Prepared source) throws Exception {
        var reader = new OutfitObserverSources(client, root);
        var snapshot = client.observerOutfit(server.observedOwner).future().get(5, TimeUnit.SECONDS);
        var resource = snapshot.resources().getFirst();
        require(reader.read(snapshot, resource).future().get(5, TimeUnit.SECONDS).equals(source.nbt()),
                "observer scoped source prepares the exact native NBT");
        reader.read(snapshot, resource).future().get(5, TimeUnit.SECONDS);
        require(server.observerBodies.get() == 1 && server.observerLookups.get() == 5,
                "observer cache hits freshly authorize before and after native parsing");
        Path cache;
        try (var files = Files.walk(root)) {
            cache = files.filter(path -> path.getFileName().toString().startsWith("outfit-observer-")
                    && !path.toString().endsWith(".part")).findFirst().orElseThrow();
        }
        require(ModelCacheCodec.isWrapped(cache) && cache.toString().endsWith(ModelCacheLayout.extension(server.kind.name())),
                "observer sources use the original protected avatar/cosmetic formats");
        server.observerRevision++;
        var next = client.observerOutfit(server.observedOwner).future().get(5, TimeUnit.SECONDS);
        reader.read(next, next.resources().getFirst()).future().get(5, TimeUnit.SECONDS);
        require(server.observerBodies.get() == 1, "a configuration-only revision change reuses the same authorized native bytes");
        Files.write(cache, new byte[40]);
        reader.read(next, next.resources().getFirst()).future().get(5, TimeUnit.SECONDS);
        require(server.observerBodies.get() == 2 && ModelCacheCodec.isWrapped(cache), "corrupt observer cache is fetched and replaced");
        server.observerMode = "hidden";
        observerFailure(reader.read(next, next.resources().getFirst()).future());
        require(server.observerBodies.get() == 2, "hidden equipment cannot read cached observer bytes");
        Files.delete(cache);
        server.observerMode = "race";
        server.observerLookups.set(0);
        observerFailure(reader.read(next, next.resources().getFirst()).future());
        require(!Files.exists(cache), "revocation after the body prevents observer cache publication");
        server.observerMode = "normal";
        reader.read(next, next.resources().getFirst()).future().get(5, TimeUnit.SECONDS);
        require(ModelCacheCodec.isWrapped(cache), "observer source recovers after a rejected generation");
        if (server.kind == OutfitConfig.ResourceKind.ACCESSORY) {
            server.observerMode = "versions";
            var versions = client.observerOutfit(server.observedOwner).future().get(5, TimeUnit.SECONDS);
            require(versions.configuration().instances().size() == 2 && versions.resources().size() == 2
                            && versions.resources().getFirst().reference().identity().equals(versions.resources().getLast().reference().identity())
                            && !versions.resources().getFirst().reference().revision().equals(versions.resources().getLast().reference().revision()),
                    "two revisions of the same resource retain separate exact source declarations");
            server.observerMode = "normal";
        }
        System.out.println("PASS observer native source cache " + source.metadata().modelType() + "/" + source.resourceKind()
                + ": protected bytes, fresh projection, configuration-only cache reuse, corruption and revoke race; resident activation pending");
    }

    private static void observerFailure(java.util.concurrent.CompletableFuture<?> future) throws Exception {
        try {
            future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("Observer cache bypassed current projection");
        } catch (ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure failure && failure.code.equals("OUTFIT_CHANGED"),
                    "observer admission failure: " + expected.getCause());
        }
    }

    private static void testApplyDependencies(Path directory, Path cacheRoot, LibraryClient client, Server server,
                                              LibraryPackagePreparation.Prepared source) throws Exception {
        List<OutfitConfig.Instance> instances = server.kind == OutfitConfig.ResourceKind.AVATAR ? List.of()
                : java.util.stream.IntStream.rangeClosed(1, 2).mapToObj(ordinal -> new OutfitConfig.Instance(
                        UUID.randomUUID(), server.reference, ordinal, ordinal == 2,
                        OutfitConfig.AnchorRef.authorDefault("unknown_target"), OutfitConfig.Transform.identity(),
                        List.of(), java.util.Map.of(), java.util.Map.of())).toList();
        var configuration = new OutfitConfig(1, 0, server.kind == OutfitConfig.ResourceKind.AVATAR ? server.reference : null,
                instances, List.of(), instances.isEmpty() ? java.util.Map.of() : java.util.Map.of(server.reference.identity(), 2), false);
        int bodies = server.bodies.get();
        try (var operation = new OutfitApplyResources(configuration, client, null,
                directory.resolve("unavailable-local-data"), cacheRoot, Runnable::run, () -> true)) {
            var prepared = operation.future().get(5, TimeUnit.SECONDS);
            require(prepared.ready() && prepared.resources().issues().isEmpty()
                            && prepared.resources().read(server.reference, () -> false).equals(source.nbt()),
                    "formal aggregation prefers existing fixed input without the original local files");
            require(server.bodies.get() == bodies, "duplicate/disabled references share authorized cached input");
            server.mode = "unbound";
            var failed = operation.retry().get(5, TimeUnit.SECONDS);
            require(!failed.ready() && failed.resources().issues().size() == 1
                            && failed.resources().issues().getFirst().instances().size() == instances.size(),
                    "one failed local source identifies every affected instance and cannot become ready");
            require(!prepared.ready(), "retry retires the preceding prepared source generation");
            server.mode = "normal";
            var recovered = operation.retry().get(5, TimeUnit.SECONDS);
            require(recovered.ready() && server.bodies.get() == bodies,
                    "explicit retry freshly resolves and reuses the fixed cache after source preparation failure");
        }
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        var queued = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        try (var operation = new OutfitApplyResources(configuration, client, null,
                directory.resolve("unavailable-local-data"), cacheRoot, queued::add, active::get)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (queued.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            require(!queued.isEmpty(), "capability completion is queued before draft retirement");
            active.set(false);
            queued.remove().run();
            require(operation.future().isCancelled() && server.bodies.get() == bodies,
                    "retired draft cancels aggregation instead of publishing or leaving a pending future");
        }
    }

    private static void testFirstPrivateUpload(Path directory, Path cacheRoot, LibraryClient client, Server server)
            throws Exception {
        Path data = Files.createDirectory(directory.resolve("apply-private-" + UUID.randomUUID()));
        Path model = Files.createDirectory(data.resolve("model"));
        Files.writeString(model.resolve("avatar.json"), "{\"name\":\"Frozen source\",\"free\":true}");
        Files.writeString(model.resolve("script.lua"), "return 1");
        server.reference = new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(data).describe(model, () -> false);
        server.privateMode = true;
        server.failCommit = true;
        var configuration = new OutfitConfig(1, 0, server.reference, List.of(), List.of(), java.util.Map.of(), false);
        try (var operation = new OutfitApplyResources(configuration, client, null, data, cacheRoot, Runnable::run, () -> true)) {
            var failed = operation.future().get(5, TimeUnit.SECONDS);
            require(!failed.ready() && failed.resources().issues().getFirst().code().equals("INTERNAL"),
                    "failed private commit is a dependency issue and cannot activate a partial configuration");
            Files.writeString(model.resolve("avatar.json"), "{\"name\":\"Changed after failed commit\"}");
            server.failCommit = false;
            var recovered = operation.retry().get(5, TimeUnit.SECONDS);
            require(recovered.ready() && server.uploadReservations.get() == 2 && server.uploadBodies.get() == 2,
                    "first private upload retries its frozen operation after local source edits");
            require(recovered.resources().base().getCompoundOrEmpty("metadata").getStringOr("name", "")
                            .equals("Frozen source"), "the acknowledged fixed native bytes are used instead of the edited local source");
            var again = operation.retry().get(5, TimeUnit.SECONDS);
            require(again.ready() && server.uploadReservations.get() == 2,
                    "a completed binding prevents another private upload on subsequent preparation");
        }
        System.out.println("PASS formal source aggregation: actual private preparation/upload/readback, "
                + "frozen operation retry after source edits, completed reuse and draft retirement");
    }

    private static void failure(OutfitSourceService service, Server server, int status, String code) throws Exception {
        try {
            service.read(server.reference, server.kind).future().get(5, TimeUnit.SECONDS);
            throw new AssertionError("Expected " + code);
        } catch (ExecutionException error) {
            require(error.getCause() instanceof LibraryClient.Failure failure
                    && failure.status == status && failure.code.equals(code), "safe source failure: " + error.getCause());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(3);
        private final LibraryPackagePreparation.Prepared source;
        private volatile OutfitConfig.ResourceRef reference;
        private final OutfitConfig.ResourceKind kind;
        private final AtomicInteger bodies = new AtomicInteger();
        private final AtomicInteger lookups = new AtomicInteger();
        private final UUID observedOwner = new UUID(0, 9);
        private final AtomicInteger observerBodies = new AtomicInteger();
        private final AtomicInteger observerLookups = new AtomicInteger();
        private volatile long observerRevision = 7;
        private volatile String observerMode = "normal";
        private volatile String mode = "normal";
        private volatile boolean privateMode;
        private volatile boolean privateCompleted;
        private volatile boolean failCommit;
        private volatile JsonObject uploadInput;
        private volatile byte[] uploaded;
        private final AtomicInteger uploadReservations = new AtomicInteger();
        private final AtomicInteger uploadBodies = new AtomicInteger();

        private Server(LibraryPackagePreparation.Prepared source) throws Exception {
            this.source = source;
            reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                    null, "a".repeat(64), OutfitConfig.Format.valueOf(source.metadata().modelType()));
            kind = OutfitConfig.ResourceKind.valueOf(source.resourceKind());
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::handle);
            server.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                require("Bearer cache-test".equals(exchange.getRequestHeaders().getFirst("authorization"))
                                && origin().equals(exchange.getRequestHeaders().getFirst("x-omnimorph-origin")),
                        "source reads retain authenticated endpoint scope");
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/capabilities")) {
                    var limits = OutfitLimits.defaults();
                    var outfits = new LibraryModels.OutfitCapabilities(1, true, true, true, false,
                            limits.maxInstances(), limits.maxVirtualAnchors(), limits.maxDepth(), limits.maxResourceNodes(),
                            limits.maxTotalNodes(), limits.maxBoneOverrides(), limits.maxJsonBytes(), limits.maxTrails(),
                            limits.maxTrailSamples(), limits);
                    json(exchange, 200, JSON.toJson(new LibraryModels.Capabilities(true, 1, 67108864,
                            8, 2097152, 2048, 2048, List.of("zip", "ysm"), List.of(reference.format().name()),
                            List.of("general"), false, outfits)));
                    return;
                }
                if (path.contains("/outfits/players/")) {
                    if (path.contains("/resources/")) {
                        observerBodies.incrementAndGet();
                        require(exchange.getRequestURI().getQuery().equals("revision=" + observerRevision),
                                "observer body is scoped to the captured configuration revision");
                        byte[] bytes = source.compressed();
                        exchange.getResponseHeaders().add("content-type", "application/octet-stream");
                        exchange.getResponseHeaders().add("x-omnimorph-sha256", source.sha256());
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } else {
                        int lookup = observerLookups.incrementAndGet();
                        json(exchange, 200, observerSnapshot(observerMode.equals("hidden")
                                || observerMode.equals("race") && lookup == 2).toString());
                    }
                    return;
                }
                if (privateMode && path.endsWith("/uploads")) {
                    uploadReservations.incrementAndGet();
                    var input = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    if (uploadInput == null) {
                        uploadInput = input;
                    } else {
                        require(uploadInput.equals(input), "retry keeps operation, source reference, metadata and digests frozen");
                    }
                    var result = new JsonObject();
                    result.add("operation_id", input.get("operation_id"));
                    result.addProperty("completed", false);
                    result.addProperty("relative_path", path + "/" + input.get("operation_id").getAsString() + "/body");
                    result.addProperty("expires_at", 1900000000);
                    result.addProperty("max_bytes", 67108864);
                    json(exchange, 200, result.toString());
                    return;
                }
                if (privateMode && path.contains("/uploads/") && path.endsWith("/body")) {
                    uploadBodies.incrementAndGet();
                    byte[] bytes = exchange.getRequestBody().readAllBytes();
                    require(top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(bytes)
                            .equals(uploadInput.get("raw_sha256").getAsString()), "native upload matches its frozen digest");
                    if (uploaded != null) {
                        require(java.util.Arrays.equals(uploaded, bytes), "retry does not recompile the edited source");
                    }
                    uploaded = bytes;
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                if (privateMode && path.endsWith("/commit")) {
                    if (failCommit) {
                        json(exchange, 503, "{\"code\":\"INTERNAL\"}");
                    } else {
                        privateCompleted = true;
                        json(exchange, 200, privateBinding().toString());
                    }
                    return;
                }
                var query = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                require(query.getAsJsonObject("reference").get("local_id").getAsString().equals(reference.localId().toString()),
                        "body and admission use the requested fixed source");
                if (path.endsWith("/resolve")) {
                    int lookup = lookups.incrementAndGet();
                    if (privateMode) {
                        var result = new JsonObject();
                        result.add("resource", privateCompleted ? privateBinding() : JsonNull.INSTANCE);
                        json(exchange, 200, result.toString());
                        return;
                    }
                    if (mode.equals("unbound")) {
                        json(exchange, 200, "{\"resource\":null}");
                        return;
                    }
                    if (mode.equals("revoked") || mode.equals("race") && lookup == 2) {
                        json(exchange, 403, "{\"code\":\"FORBIDDEN\"}");
                        return;
                    }
                    var binding = new JsonObject();
                    binding.add("reference", query.get("reference"));
                    binding.addProperty("asset_id", "fixed_source");
                    binding.addProperty("asset_revision", 1);
                    binding.addProperty("raw_sha256", source.sha256());
                    binding.addProperty("resource_kind", kind.name());
                    binding.add("accessory_metadata", kind == OutfitConfig.ResourceKind.ACCESSORY
                            ? OutfitCodec.encodeAccessoryMetadata(CosmeticMetadata.read(source.accessoryMetadata().toString()))
                            : JsonNull.INSTANCE);
                    var result = new JsonObject();
                    result.add("resource", binding);
                    json(exchange, 200, result.toString());
                } else if (path.endsWith("/body")) {
                    bodies.incrementAndGet();
                    String hash = privateCompleted ? uploadInput.get("raw_sha256").getAsString() : source.sha256();
                    require(hash.equals(query.get("raw_sha256").getAsString()), "body is pinned to admitted raw hash");
                    byte[] bytes = privateCompleted ? uploaded.clone() : source.compressed();
                    if (mode.equals("bad-bytes")) {
                        bytes[bytes.length - 1] ^= 1;
                    }
                    exchange.getResponseHeaders().add("content-type", "application/octet-stream");
                    exchange.getResponseHeaders().add("x-omnimorph-sha256", mode.equals("bad-header")
                            ? "b".repeat(64) : hash);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else {
                    throw new AssertionError("Unexpected source route " + path);
                }
            }
        }

        private JsonObject privateBinding() {
            var binding = new JsonObject();
            for (String field : List.of("reference", "resource_kind", "raw_sha256", "accessory_metadata")) {
                binding.add(field, uploadInput.get(field));
            }
            binding.addProperty("asset_id", "outfit_" + uploadInput.get("operation_id").getAsString().replace("-", ""));
            binding.addProperty("asset_revision", 1);
            return binding;
        }

        private JsonObject observerSnapshot(boolean hidden) {
            var instances = kind == OutfitConfig.ResourceKind.AVATAR ? List.<OutfitConfig.Instance>of()
                    : List.of(new OutfitConfig.Instance(new UUID(0, 11), reference, 1, true,
                    OutfitConfig.AnchorRef.authorDefault("unknown_target"), OutfitConfig.Transform.identity(),
                    List.of(), java.util.Map.of(), java.util.Map.of()));
            var configuration = new OutfitConfig(1, observerRevision, kind == OutfitConfig.ResourceKind.AVATAR ? reference : null,
                    instances, List.of(), instances.isEmpty() ? java.util.Map.of() : java.util.Map.of(reference.identity(), 1), true);
            var result = new JsonObject();
            result.addProperty("schema_version", 1);
            result.addProperty("owner", observedOwner.toString());
            result.addProperty("revision", observerRevision);
            result.addProperty("equipment_revision", 4);
            result.add("configuration", hidden ? JsonNull.INSTANCE : JsonParser.parseString(OutfitCodec.encode(configuration)));
            var resources = new com.google.gson.JsonArray();
            if (!hidden) {
                var item = new JsonObject();
                item.addProperty("id", "1".repeat(64));
                var referenceJson = new JsonObject();
                referenceJson.addProperty("source", "LOCAL");
                referenceJson.addProperty("local_id", reference.localId().toString());
                referenceJson.addProperty("content_hash", reference.contentHash());
                referenceJson.addProperty("format", reference.format().name());
                item.add("reference", referenceJson);
                item.addProperty("resource_kind", kind.name());
                item.add("accessory_metadata", kind == OutfitConfig.ResourceKind.ACCESSORY
                        ? OutfitCodec.encodeAccessoryMetadata(CosmeticMetadata.read(source.accessoryMetadata().toString())) : JsonNull.INSTANCE);
                item.addProperty("distribution_format", reference.format() == OutfitConfig.Format.FIGURA ? 1 : 2);
                item.addProperty("byte_length", source.compressed().length);
                item.addProperty("raw_sha256", source.sha256());
                item.addProperty("content_sha256", "0".repeat(64));
                item.addProperty("schema_sha256", "0".repeat(64));
                item.addProperty("relative_path", top.aihmc.omnimorph.backend2.library.OutfitObserverSnapshot
                        .resourcePath(observedOwner, observerRevision, "1".repeat(64)));
                resources.add(item);
            }
            result.add("resources", resources);
            if (!hidden && observerMode.equals("versions")) {
                var copies = new com.google.gson.JsonArray();
                var versioned = new com.google.gson.JsonArray();
                var watermarks = new JsonObject();
                for (int revision = 1; revision <= 2; revision++) {
                    var fixed = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, origin(), new UUID(0, 21),
                            (long) revision, reference.contentHash(), reference.format());
                    var fixedJson = JsonParser.parseString(OutfitCodec.encode(new OutfitConfig(1, 0, fixed,
                            List.of(), List.of(), java.util.Map.of(), false))).getAsJsonObject().getAsJsonObject("base");
                    var copy = result.getAsJsonObject("configuration").getAsJsonArray("instances").get(0).getAsJsonObject().deepCopy();
                    copy.addProperty("instance_id", new UUID(0, 30 + revision).toString());
                    copy.addProperty("display_ordinal", revision);
                    copy.add("resource", fixedJson.deepCopy());
                    copies.add(copy);
                    var item = resources.get(0).getAsJsonObject().deepCopy();
                    String id = Integer.toString(revision).repeat(64);
                    item.addProperty("id", id);
                    item.add("reference", fixedJson.deepCopy());
                    item.addProperty("relative_path", top.aihmc.omnimorph.backend2.library.OutfitObserverSnapshot
                            .resourcePath(observedOwner, observerRevision, id));
                    versioned.add(item);
                    watermarks.addProperty(fixed.identity(), revision);
                }
                result.getAsJsonObject("configuration").add("instances", copies);
                result.getAsJsonObject("configuration").add("display_ordinals", watermarks);
                result.add("resources", versioned);
            }
            return result;
        }

        private void json(HttpExchange exchange, int status, String json) throws java.io.IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
