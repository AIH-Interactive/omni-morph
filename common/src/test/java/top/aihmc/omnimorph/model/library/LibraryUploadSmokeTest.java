package top.aihmc.omnimorph.model.library;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Production upload pipeline and real HTTP; no GUI widgets, rendering, or wardrobe publication. */
public final class LibraryUploadSmokeTest {
    public static void localOutfitPublication(String origin, Runnable drain) throws Exception {
        var root = org.figuramc.figura.FiguraMod.getFiguraDirectory();
        Path baseDirectory = Files.createDirectories(root.resolve("avatars/publication-base"));
        String baseJson = "{\"name\":\"Local base\",\"authors\":[\"Author\"]}";
        Files.writeString(baseDirectory.resolve("avatar.json"), baseJson);
        Path accessoryDirectory = Files.createDirectories(root.resolve("cosmetics/publication-accessory"));
        String accessoryJson = "{\"name\":\"Local accessory\",\"authors\":[\"Author\"],\"category\":\"Trail\",\"cosmetic_anchor\":\"hip\"}";
        Files.writeString(accessoryDirectory.resolve("cosmetic.json"), accessoryJson);
        var base = new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(root).describe(baseDirectory, () -> false);
        top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef accessory;
        try (var inventory = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory(root)) {
            accessory = inventory.scan(() -> false).stream()
                    .filter(entry -> entry.path().equals(accessoryDirectory)).findFirst().orElseThrow().reference();
        }
        var instance = new top.aihmc.omnimorph.model.outfit.OutfitConfig.Instance(UUID.randomUUID(), accessory,
                1, true, top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorRef.authorDefault("hip"),
                top.aihmc.omnimorph.model.outfit.OutfitConfig.Transform.identity(), java.util.List.of(), Map.of(), Map.of());
        var configuration = new top.aihmc.omnimorph.model.outfit.OutfitConfig(1, 0, base,
                java.util.List.of(instance), java.util.List.of(), Map.of(accessory.identity(), 1), true);
        var endpoint = CloudEndpoint.parse(origin, () -> true);
        try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), builder -> builder
                .header("Authorization", "Bearer synthetic-token-1").header("x-omnimorph-auth-mode", "figura"));
             var client = new LibraryClient(access, new LibrarySession(origin, new UUID(0, 1), 77, () -> true),
                     net.minecraft.client.Minecraft.getInstance()::execute);
             var uploads = new LibraryUploadService(client)) {
            var capabilities = await(client.capabilities().future(), drain);
            var draft = OutfitUploadDraft.snapshot(new OutfitUploadDraft.Prepared("Local outfit", configuration),
                    "Local outfit", "", "general", java.util.List.of(), true, false, capabilities, origin, true);
            var published = await(uploads.uploadOutfit(draft, null).future(), drain);
            JsonObject document = await(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                    "/api/library/v1/models/" + published.modelId() + "/outfit?revision=1", null, JsonObject.class)).future(), drain);
            var converted = top.aihmc.omnimorph.model.outfit.OutfitCodec.decode(document.getAsJsonObject("configuration").toString());
            require(converted.base().source() == top.aihmc.omnimorph.model.outfit.OutfitConfig.Source.CLOUD
                            && converted.instances().getFirst().resource().source()
                            == top.aihmc.omnimorph.model.outfit.OutfitConfig.Source.CLOUD,
                    "both local dependencies become cloud references in the published outfit");
            await(client.applyOutfit(UUID.randomUUID(), converted).future(), drain);
        }
        try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), builder -> builder
                .header("Authorization", "Bearer synthetic-token-2").header("x-omnimorph-auth-mode", "figura"));
             var observer = new LibraryClient(access, new LibrarySession(origin, new UUID(0, 2), 77, () -> true),
                     net.minecraft.client.Minecraft.getInstance()::execute)) {
            var snapshot = await(observer.observerOutfit(new UUID(0, 1)).future(), drain);
            require(snapshot.resources().size() == 2, "observer receives the converted base and accessory");
            for (var resource : snapshot.resources()) {
                await(observer.submit(LibraryClient.Lane.TRANSFER,
                        exchange -> exchange.observerOutfitBody(snapshot, resource)).future(), drain);
            }
        }
        require(Files.readString(baseDirectory.resolve("avatar.json")).equals(baseJson)
                        && Files.readString(accessoryDirectory.resolve("cosmetic.json")).equals(accessoryJson),
                "publication preserves the original local files");
        System.out.println("PASS Java to Rust local outfit: private staging, atomic cloud conversion, publication, Apply and observer asset download");
    }

    private static final UUID VIEWER = new UUID(0, 1);

    public static void run(Runnable drain) throws Exception {
        Path directory = Files.createTempDirectory("library-upload-test-");
        try (var server = new Server()) {
            Path source = directory.resolve("source.zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
                zip.putNextEntry(new ZipEntry("avatar.json"));
                zip.write("{\"name\":\"§b原始模型\",\"authors\":[\"§e作者\"],\"description\":\"§l模型说明§r\",\"free\":true}"
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            var prepared = LibraryPackagePreparation.prepare(source, VIEWER, CloudHttp.FILE_BYTES, () -> false);
            var draft = new LibraryUploadDraft(VIEWER);
            draft.prepared(draft.beginSource(source), prepared);
            draft.category("general");
            var snapshot = draft.snapshot(server.limits());
            require(snapshot.metadata().get("internal_name").equals("原始模型")
                            && snapshot.metadata().get("authors").equals(java.util.List.of("作者"))
                            && snapshot.metadata().get("description").equals("模型说明")
                            && prepared.nbt().getCompoundOrEmpty("metadata").getStringOr("name", "").equals("原始模型"),
                    "formatted source metadata and uploaded NBT agree on plain catalog text");
            try (var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                    builder -> builder.header("Authorization", "Bearer synthetic"));
                 var client = new LibraryClient(access, new LibrarySession(server.origin(), VIEWER, 1, () -> true),
                         net.minecraft.client.Minecraft.getInstance()::execute);
                 var uploads = new LibraryUploadService(client)) {
                server.dropBody.set(true);
                var bodyLost = uploads.upload(snapshot, null);
                reject(bodyLost.future(), drain);
                var publication = await(bodyLost.retry(), drain);
                require(publication.published() && server.published.get() == 1,
                        "lost body acknowledgement retries same draft and commits verified bytes");
                var publicationJson = new Gson().toJsonTree(publication).getAsJsonObject();
                require(publicationJson.has("visibility")
                                && publicationJson.get("visibility").getAsString().equals("PRIVATE")
                                && publicationJson.get("moderationStatus").getAsString().equals("BLOCKED"),
                        "upload result preserves server policy even when a public draft is moderated private");

                server.dropImage.set(true);
                var image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
                var imageBytes = new ByteArrayOutputStream();
                ImageIO.write(image, "png", imageBytes);
                image.flush();
                var photo = new LibraryUploadService.Image(imageBytes.toByteArray(), "image/png");
                var imageLost = uploads.upload(snapshot, photo);
                reject(imageLost.future(), drain);
                require(await(imageLost.retry(), drain).published() && server.published.get() == 2
                        && server.images.get() == 1, "image retry reuses operation and original policy CAS");

                server.dropCommit.set(true);
                var commitLost = uploads.upload(snapshot, null);
                reject(commitLost.future(), drain);
                int reservations = server.reserves.get();
                int bodies = server.bodies.get();
                require(await(commitLost.retry(), drain).published() && server.published.get() == 3
                        && server.reserves.get() == reservations && server.bodies.get() == bodies,
                        "lost publication response replays only original commit without another draft/body");

                server.uploadLimit = 1;
                reject(uploads.upload(snapshot, null).future(), drain);
                require(server.reserves.get() == reservations, "current upload limit checked before reserve");
                server.uploadLimit = CloudHttp.FILE_BYTES;
                server.imageWidth = 8;
                reject(uploads.upload(snapshot, photo).future(), drain);
                require(server.reserves.get() == reservations, "original image dimensions obey fresh backend limits");
                server.imageWidth = 2048;

                server.blockBody.set(true);
                var cancelled = uploads.upload(snapshot, null);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (server.bodyEntered.getCount() != 0 && System.nanoTime() < deadline) {
                    drain.run();
                    Thread.sleep(5);
                }
                require(server.bodyEntered.getCount() == 0, "real body reached blocking handler");
                await(cancelled.cancel(), drain);
                server.bodyRelease.countDown();
                require(cancelled.future().isCancelled() && server.published.get() == 3,
                        "cancel retires upload and removes pending draft without publication");

                var invalidHeaders = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.binaryJson("PUT",
                        "/api/library/v1/models/" + new UUID(0, 1) + "/image", photo.bytes(), photo.contentType(),
                        Map.of("Authorization", "must-not-override-player"), Map.class));
                reject(invalidHeaders.future(), drain);
                require(server.badReplay.get() == 0 && server.unexpected.get() == 0,
                        "stable mutation payloads and no unrelated upload/equip/header override path");
            }

            testAccessoryUpload(directory, drain);
            testOutfitUpload(directory, drain);
            Path encrypted = Path.of("src/test/resources/ysm/crypto-vectors/crypto1.ysm");
            var cipher = LibraryPackagePreparation.prepare(encrypted, VIEWER, CloudHttp.FILE_BYTES, () -> false);
            var identity = await(AvatarLoadExecutor.submit(() -> {
                try {
                    return LibraryUploadIdentity.prepare(cipher, () -> false);
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }), drain);
            Avatar reference = Avatar.createLibraryPreview(UUID.randomUUID(), "InventoryReference");
            try {
                reference.load(cipher.nbt());
                var actual = new YsmSyncModel(reference.getYsmRuntime());
                require(identity.contentHash().equals(actual.contentHash())
                        && identity.schemaHash().equals(actual.schemaHash()),
                        "upload inventory matches actual Avatar YSM synchronization identity");
                require(java.util.Arrays.equals(Files.readAllBytes(encrypted), cipher.nbt()
                        .getCompoundOrEmpty("ysm").getByteArray("source_ciphertext").orElseThrow()),
                        "inventory preparation preserves original ciphertext");
            } finally {
                reference.clean();
                drain.run();
            }
            System.out.println("PASS library upload: lost body/image/commit responses, stable CAS, fresh limits, cancel and actual encrypted inventory");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void testOutfitUpload(Path directory, Runnable drain) throws Exception {
        var configuration = top.aihmc.omnimorph.model.outfit.OutfitConfig.empty();
        Path source = directory.resolve("outfit.json");
        Files.writeString(source, new Gson().toJson(Map.of("name", "JSON preset", "configuration",
                JsonParser.parseString(top.aihmc.omnimorph.model.outfit.OutfitCodec.encode(configuration)))));
        var prepared = OutfitUploadDraft.prepare(source, top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults());
        try (var server = new Server();
             var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                     builder -> builder.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(server.origin(), VIEWER, 1, () -> true),
                     net.minecraft.client.Minecraft.getInstance()::execute);
             var uploads = new LibraryUploadService(client)) {
            var snapshot = OutfitUploadDraft.snapshot(prepared, "Published JSON", "Summary", "general",
                    java.util.List.of("preset"), true, true, server.limits(), server.origin());
            var local = new top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef(
                    top.aihmc.omnimorph.model.outfit.OutfitConfig.Source.LOCAL, UUID.randomUUID(),
                    null, null, null, "d".repeat(64), top.aihmc.omnimorph.model.outfit.OutfitConfig.Format.FIGURA);
            var localConfiguration = new top.aihmc.omnimorph.model.outfit.OutfitConfig(1, 0, local,
                    java.util.List.of(), java.util.List.of(), Map.of(), false);
            var unconfirmed = new OutfitUploadDraft.Snapshot(localConfiguration, snapshot.metadata());
            try {
                unconfirmed.validate(server.limits(), server.origin());
                throw new AssertionError("Local publication requires explicit conversion consent");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().equals("OUTFIT_DEPENDENCY_NOT_DISTRIBUTABLE"),
                        "unconfirmed local resources remain non-distributable");
            }
            var confirmed = new OutfitUploadDraft.Snapshot(localConfiguration, snapshot.metadata(), true);
            confirmed.validate(server.limits(), server.origin());
            require(confirmed.input(UUID.randomUUID(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults())
                            .get("convert_local_dependencies").getAsBoolean(),
                    "confirmed publication transmits explicit conversion consent");
            var pixels = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(pixels, "png", bytes);
            pixels.flush();
            var image = new LibraryUploadService.Image(bytes.toByteArray(), "image/png");
            server.dropOutfit.set(true);
            server.dropImage.set(true);
            var operation = uploads.uploadOutfit(snapshot, image);
            reject(operation.future(), drain);
            require(server.origin().equals(server.lastOutfitOrigin),
                    "Outfit publication transport supplies the canonical origin required by the real backend");
            reject(operation.retry(), drain);
            require(operation.outfitPublished() && server.published.get() == 1 && server.outfitPosts.get() == 2,
                    "lost outfit acknowledgement retries the same JSON publication before the separate cover operation");
            Files.writeString(source, "changed after preparation");
            server.outfitPublication = false;
            require(await(operation.retry(), drain).published() && server.outfitPosts.get() == 2
                            && server.images.get() == 1 && server.badReplay.get() == 0 && server.bodies.get() == 0,
                    "lost cover acknowledgement retries only the original cover CAS without another publication/model body");
            var transaction = server.transactions.values().stream().findFirst().orElseThrow();
            require(transaction.input.getAsJsonObject("metadata").get("resource_kind").getAsString().equals("OUTFIT")
                            && !transaction.input.getAsJsonObject("metadata").has("model_type")
                            && transaction.input.get("configuration").equals(JsonParser.parseString(
                            top.aihmc.omnimorph.model.outfit.OutfitCodec.encode(configuration))),
                    "outfit upload sends an immutable JSON configuration and no invented model format");
            reject(uploads.uploadOutfit(snapshot, null).future(), drain);
            require(server.outfitPosts.get() == 2 && server.unexpected.get() == 0,
                    "fresh capability withdrawal rejects publication without model upload/cancel/equipment fallback");
        }
        System.out.println("PASS Outfit JSON upload: bounded document, immutable publication, lost JSON/cover replies, stable CAS and capability withdrawal");
    }

    private static void testAccessoryUpload(Path directory, Runnable drain) throws Exception {
        for (boolean encrypted : java.util.List.of(false, true)) {
            Path source = Files.createDirectory(directory.resolve(encrypted ? "encrypted-cosmetic" : "figura-cosmetic"));
            String original = """
                    {"name":"Accessory source","authors":["Author"],"category":"Trail","cosmetic_anchor":"hip"}
                    """;
            Files.writeString(source.resolve("cosmetic.json"), original);
            Files.writeString(source.resolve("script.lua"), "return 1");
            byte[] ciphertext = encrypted ? Files.readAllBytes(Path.of("src/test/resources/ysm/crypto-vectors/crypto1.ysm")) : null;
            if (ciphertext != null) {
                Files.write(source.resolve("source.ysm"), ciphertext);
            }
            var prepared = LibraryPackagePreparation.prepareAccessory(source, VIEWER, CloudHttp.FILE_BYTES,
                    () -> false, ignored -> {});
            var draft = new LibraryUploadDraft(VIEWER);
            draft.prepared(draft.beginSource(source), prepared);
            draft.category("general");
            var edited = prepared.accessoryMetadata();
            edited.addProperty("cosmetic_anchor", "head_anchor");
            edited.add("default_transform", JsonParser.parseString("""
                    {"position":[1,0,0],"rotation":[0,0,0],"scale":[1,1,1]}
                    """));
            draft.resourceKind("ACCESSORY", edited);
            try (var server = new Server();
                 var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                         builder -> builder.header("Authorization", "Bearer synthetic"));
                 var client = new LibraryClient(access, new LibrarySession(server.origin(), VIEWER, 1, () -> true),
                         net.minecraft.client.Minecraft.getInstance()::execute);
                 var uploads = new LibraryUploadService(client)) {
                var snapshot = draft.snapshot(server.limits());
                var wireDeclaration = (JsonObject) snapshot.metadata().get("accessory_metadata");
                require(wireDeclaration.keySet().equals(java.util.Set.of("schema_version", "category", "cosmetic_anchor",
                                "default_transform", "supported_bases", "has_bones", "skeleton_revision", "skeleton_hash",
                                "display_bones", "edit_bones", "exported_bones", "editable_bones", "validation_source")),
                        "reserve metadata matches the backend's strict AccessoryMetadata fields, excluding common author fields");
                server.dropCommit.set(true);
                var operation = uploads.upload(snapshot, null);
                reject(operation.future(), drain);
                var transaction = server.transactions.values().stream().findFirst().orElseThrow();
                edited.addProperty("cosmetic_anchor", "back_anchor");
                ((JsonObject) snapshot.metadata().get("accessory_metadata")).addProperty("cosmetic_anchor", "back_anchor");
                int bodyCount = server.bodies.get();
                require(await(operation.retry(), drain).published() && server.published.get() == 1
                                && server.bodies.get() == bodyCount && server.badReplay.get() == 0,
                        "accessory commit retry preserves the original operation and frozen declaration");
                var actual = transaction.body.getCompoundOrEmpty("metadata");
                JsonObject declaration = JsonParser.parseString(actual.getStringOr("cosmetic_json", "")).getAsJsonObject();
                require(top.aihmc.omnimorph.model.outfit.OutfitCodec.encodeAccessoryMetadata(
                                top.aihmc.omnimorph.model.outfit.CosmeticMetadata.read(declaration.toString()))
                                .equals(transaction.input.getAsJsonObject("metadata").getAsJsonObject("accessory_metadata"))
                                && declaration.get("cosmetic_anchor").getAsString().equals("head_anchor")
                                && transaction.input.get("source_filename").getAsString().endsWith(".zip")
                                && !transaction.input.get("raw_sha256").getAsString().equals(prepared.sha256())
                                && Files.readString(source.resolve("cosmetic.json")).equals(original),
                        "HTTP body contains the edited declaration matching reserve metadata/hash, without source changes");
                if (ciphertext != null) {
                    require(java.util.Arrays.equals(ciphertext, transaction.body.getCompoundOrEmpty("ysm")
                                    .getByteArray("source_ciphertext").orElseThrow())
                                    && transaction.input.has("content_sha256") && transaction.input.has("schema_sha256"),
                            "actual encrypted accessory HTTP upload retains ciphertext and native synchronization identity");
                }
                String export = System.getenv("OMNIMORPH_TEST_ACCESSORY_UPLOAD_DIRECTORY");
                if (export != null && !export.isBlank()) {
                    Path output = Files.createDirectories(Path.of(export).resolve(encrypted ? "encrypted" : "figura"));
                    Files.writeString(output.resolve("reserve.json"), transaction.input.toString());
                    Files.write(output.resolve("body.nbt"), transaction.bodyBytes);
                }
                var json = new Gson();
                var budget = json.toJsonTree(server.outfitLimits).getAsJsonObject();
                budget.addProperty("positionLimit", 0);
                server.outfitLimits = json.fromJson(budget, top.aihmc.omnimorph.model.outfit.OutfitLimits.class);
                int reserves = server.reserves.get();
                reject(uploads.upload(snapshot, null).future(), drain);
                require(server.reserves.get() == reserves, "fresh placement budget rejects an already prepared accessory before reserve");
            }
        }
        System.out.println("PASS accessory HTTP upload: actual Figura/encrypted YSM body, edited declaration/hash, frozen retry and fresh outfit budgets");
    }

    private static <T> T await(CompletableFuture<T> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(5);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void reject(CompletableFuture<?> future, Runnable drain) throws Exception {
        try {
            await(future, drain);
            throw new AssertionError("upload unexpectedly accepted");
        } catch (java.util.concurrent.ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure, "typed upload error");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Transaction {
        final UUID model = UUID.randomUUID();
        final JsonObject input;
        volatile boolean uploaded;
        volatile boolean committed;
        volatile JsonObject commit;
        volatile String imageIdentity;
        volatile net.minecraft.nbt.CompoundTag body;
        volatile byte[] bodyBytes;
        volatile long imageBaseRevision = 1;

        Transaction(JsonObject input) {
            this.input = input;
        }
    }

    private static final class Server implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService threads = Executors.newFixedThreadPool(4);
        final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
        final AtomicBoolean dropBody = new AtomicBoolean();
        final AtomicBoolean dropImage = new AtomicBoolean();
        final AtomicBoolean dropCommit = new AtomicBoolean();
        final AtomicBoolean blockBody = new AtomicBoolean();
        final CountDownLatch bodyEntered = new CountDownLatch(1);
        final CountDownLatch bodyRelease = new CountDownLatch(1);
        final AtomicInteger reserves = new AtomicInteger();
        final AtomicInteger bodies = new AtomicInteger();
        final AtomicInteger images = new AtomicInteger();
        final AtomicInteger published = new AtomicInteger();
        final AtomicInteger badReplay = new AtomicInteger();
        final AtomicInteger unexpected = new AtomicInteger();
        final AtomicInteger outfitPosts = new AtomicInteger();
        final AtomicBoolean dropOutfit = new AtomicBoolean();
        volatile boolean outfitPublication = true;
        volatile String lastOutfitOrigin;
        volatile long uploadLimit = CloudHttp.FILE_BYTES;
        volatile int imageWidth = 2048;
        volatile top.aihmc.omnimorph.model.outfit.OutfitLimits outfitLimits =
                top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults();

        Server() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(threads);
            server.createContext("/api/library/v1/", this::handle);
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        LibraryModels.Capabilities limits() {
            return new LibraryModels.Capabilities(true, 1, uploadLimit, 8, 2 * 1024 * 1024, imageWidth, 2048,
                    java.util.List.of("zip", "ysm"), java.util.List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"),
                    java.util.List.of("general"), false, outfitCapabilities());
        }

        LibraryModels.OutfitCapabilities outfitCapabilities() {
            return new LibraryModels.OutfitCapabilities(1, true, true, false, false,
                    32, 128, 64, 8192, 32768, 1024, 65536, 8, 256, outfitLimits, outfitPublication);
        }

        void handle(HttpExchange exchange) throws IOException {
            int status = 200;
            Object result = Map.of();
            try {
                String path = exchange.getRequestURI().getPath().substring("/api/library/v1/".length());
                String method = exchange.getRequestMethod();
                if (path.equals("capabilities")) {
                    result = new JsonObject();
                    var value = (JsonObject) result;
                    value.addProperty("enabled", true);
                    value.addProperty("version", 1);
                    value.addProperty("max_upload_bytes", uploadLimit);
                    value.addProperty("max_page_size", 8);
                    value.addProperty("max_image_bytes", 2 * 1024 * 1024);
                    value.addProperty("max_image_width", imageWidth);
                    value.addProperty("max_image_height", 2048);
                    value.add("categories", JsonParser.parseString("[\"general\"]"));
                    value.add("model_types", JsonParser.parseString("[\"FIGURA\",\"YSM_PLAIN\",\"YSM_ENCRYPTED\"]"));
                    value.add("outfits_v1", new com.google.gson.GsonBuilder()
                            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                            .create().toJsonTree(outfitCapabilities()));
                } else if (path.equals("uploads/outfit") && method.equals("POST")) {
                    lastOutfitOrigin = exchange.getRequestHeaders().getFirst("x-omnimorph-origin");
                    outfitPosts.incrementAndGet();
                    JsonObject input = json(exchange);
                    String operation = input.get("operation_id").getAsString();
                    Transaction transaction = transactions.computeIfAbsent(operation, ignored -> new Transaction(input));
                    if (!transaction.input.equals(input)) {
                        badReplay.incrementAndGet();
                    }
                    if (!transaction.committed) {
                        transaction.committed = true;
                        transaction.imageBaseRevision = 2;
                        published.incrementAndGet();
                    }
                    result = Map.of("model_id", transaction.model, "published", true, "revision", 1, "policy_revision", 2);
                    if (dropOutfit.getAndSet(false)) {
                        status = 503;
                    }
                } else if (path.equals("uploads") && method.equals("POST")) {
                    reserves.incrementAndGet();
                    JsonObject input = json(exchange);
                    String operation = input.get("operation_id").getAsString();
                    Transaction transaction = transactions.computeIfAbsent(operation, ignored -> new Transaction(input));
                    if (!transaction.input.equals(input)) {
                        badReplay.incrementAndGet();
                    }
                    result = Map.of("model_id", transaction.model, "operation_id", operation,
                            "relative_path", "/api/library/v1/uploads/" + operation + "/body",
                            "expires_at", System.currentTimeMillis() / 1000 + 300, "max_bytes", uploadLimit,
                            "policy_revision", transaction.imageIdentity == null ? 1 : 2);
                } else if (path.startsWith("uploads/")) {
                    String[] segments = path.split("/");
                    Transaction transaction = transactions.get(segments[1]);
                    if (method.equals("DELETE")) {
                        if (transaction != null && !transaction.committed) {
                            transactions.remove(segments[1]);
                        }
                    } else if (transaction == null) {
                        status = 404;
                    } else if (segments[2].equals("body")) {
                        bodies.incrementAndGet();
                        byte[] bytes = exchange.getRequestBody().readNBytes(Math.toIntExact(CloudHttp.FILE_BYTES + 1));
                        require(CloudHttp.sha256(bytes).equals(transaction.input.get("raw_sha256").getAsString()),
                                "server verifies actual distribution hash");
                        require(bytes.length == transaction.input.get("byte_length").getAsLong(),
                                "reserve length matches the real HTTP body");
                        transaction.body = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes),
                                net.minecraft.nbt.NbtAccounter.create(256L * 1024 * 1024));
                        transaction.bodyBytes = bytes;
                        if (blockBody.getAndSet(false)) {
                            bodyEntered.countDown();
                            bodyRelease.await(10, TimeUnit.SECONDS);
                        }
                        status = transaction.uploaded ? 409 : 204;
                        transaction.uploaded = true;
                        if (dropBody.getAndSet(false)) {
                            status = 503;
                        }
                    } else if (segments[2].equals("commit")) {
                        JsonObject input = json(exchange);
                        if (transaction.commit != null && !transaction.commit.equals(input)) {
                            badReplay.incrementAndGet();
                        }
                        transaction.commit = input;
                        require(transaction.uploaded && input.get("expected_revision").getAsLong()
                                == (transaction.imageIdentity == null ? 1 : 2), "commit uses current image policy CAS");
                        if (!transaction.committed) {
                            published.incrementAndGet();
                        }
                        transaction.committed = true;
                        result = Map.of("model_id", transaction.model, "published", true,
                                "visibility", "PRIVATE", "moderation_status", "BLOCKED");
                        if (dropCommit.getAndSet(false)) {
                            status = 503;
                        }
                    }
                } else if (path.startsWith("models/") && path.endsWith("/image")) {
                    UUID model = UUID.fromString(path.split("/")[1]);
                    var transaction = transactions.values().stream().filter(value -> value.model.equals(model)).findFirst().orElseThrow();
                    String identity = exchange.getRequestHeaders().getFirst("x-omnimorph-operation") + ":"
                            + exchange.getRequestHeaders().getFirst("x-omnimorph-expected-revision") + ":"
                            + CloudHttp.sha256(exchange.getRequestBody().readNBytes(2 * 1024 * 1024 + 1));
                    require(exchange.getRequestHeaders().getFirst("x-omnimorph-expected-revision")
                            .equals(Long.toString(transaction.imageBaseRevision)), "original image CAS");
                    if (transaction.imageIdentity == null) {
                        images.incrementAndGet();
                    } else if (!transaction.imageIdentity.equals(identity)) {
                        badReplay.incrementAndGet();
                    }
                    transaction.imageIdentity = identity;
                    result = Map.of("model_id", model, "image_revision", transaction.imageBaseRevision + 1,
                            "policy_revision", transaction.imageBaseRevision + 1);
                    if (dropImage.getAndSet(false)) {
                        status = 503;
                    }
                } else {
                    unexpected.incrementAndGet();
                    status = 404;
                }
                if (status >= 400) {
                    result = Map.of("code", status == 503 ? "BUSY" : "CONFLICT");
                }
                byte[] response = new Gson().toJson(result).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, status == 204 ? -1 : response.length);
                if (status != 204) {
                    exchange.getResponseBody().write(response);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        private JsonObject json(HttpExchange exchange) throws IOException {
            return JsonParser.parseString(new String(exchange.getRequestBody().readNBytes(65536),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        }

        @Override
        public void close() {
            bodyRelease.countDown();
            server.stop(0);
            threads.shutdownNow();
        }
    }
}
