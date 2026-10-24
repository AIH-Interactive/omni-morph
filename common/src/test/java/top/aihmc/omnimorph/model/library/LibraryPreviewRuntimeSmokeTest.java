package top.aihmc.omnimorph.model.library;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarKind;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.permissions.PermissionManager;
import top.aihmc.omnimorph.permissions.Permissions;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.gui.screens.library.ModelFilePickerScreen;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import sun.misc.Unsafe;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses the existing native-loader harness; no GUI/rendered-pixel assertions. */
public final class LibraryPreviewRuntimeSmokeTest {
    public static void run(Unsafe unsafe, Runnable drain, Path encrypted) throws Exception {
        Object users = field(AvatarManager.class, "LOADED_USERS").get(null);
        int userCount = ((Map<?, ?>) users).size();
        int permissions = ((Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null)).size();
        Path localPath = LocalAvatarLoader.getLastLoadedPath();
        boolean uploaded = AvatarManager.localUploaded;
        long epoch = NetworkStuff.connectionEpoch();
        var controlsLoaded = field(top.aihmc.omnimorph.avatar.control.AvatarControlValueStore.class, "loaded");
        boolean previousControlsLoaded = controlsLoaded.getBoolean(null);
        var live = new AtomicBoolean(true);
        var endpoint = CloudEndpoint.parse("https://example.invalid", () -> false);
        try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> {
            throw new AssertionError("Detached draft preview must not perform an HTTP operation");
        }); var client = new LibraryClient(access,
                new LibrarySession("synthetic-preview", FiguraMod.getLocalPlayerUUID(), epoch, live::get),
                Minecraft.getInstance()::execute);
             var preview = new LibraryPreviewSession(client, true)) {
            testModelFilePicker(client, drain);
            testReportInput(client);
            testCollectionActions(client);
            testWardrobeVisibilityTicks(unsafe);
            testWardrobeDefaultPreview(unsafe);
            testPreviewAppearance(unsafe);
            testOtherPlayerRouting(unsafe, client);
            testOtherPlayerDraft(unsafe, drain);
            testVanillaSelection(unsafe, drain);
            testLegacyCubeType();
            testProvidedCosmetics(unsafe, drain);
            testPackagedYsmDraftBases(unsafe, drain);
            testPackagedFiguraDraftAccessory(unsafe, drain);
            testCatalogOutfitPreview(unsafe, drain);
            testWardrobeDraftActions(unsafe, drain);
            testOutfitTabShowsLoadedOutfit(unsafe, drain);
            testWardrobeCloudCosmetic(unsafe, drain, encrypted);
            OutfitPresetResourcesSmokeTest.run(drain, encrypted);
            testLocalPresetScreen(unsafe, drain, client);
            testIndependentFigura(unsafe, drain);
            // Only the entity allocation is replaced: actual Avatar loading, permissions,
            // generation guards, production lookup bindings and resource cleanup all run.
            var entity = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
            UUID owner = (UUID) field(LibraryPreviewSession.class, "owner").get(preview);
            entity.setUUID(owner);
            field(LibraryPreviewSession.class, "entity").set(preview, entity);
            // Row action buttons select again before invoking the second confirmation click.
            var screenType = top.aihmc.omnimorph.gui.screens.library.ModelLibraryRecycleScreen.class;
            var screen = unsafe.allocateInstance(screenType);
            var item = new top.aihmc.omnimorph.backend2.library.LibraryRecycleService.Item("WARDROBE",
                    UUID.randomUUID(), owner, "player", "model", java.util.List.of(), "FIGURA", "DELETED",
                    0, 1, 2, "avatar", 0, true);
            UUID recovery = UUID.randomUUID();
            field(screenType, "selected").set(screen, item);
            field(screenType, "armed").set(screen, "restore");
            field(screenType, "operation").set(screen, recovery);
            var select = screenType.getDeclaredMethod("select", item.getClass());
            select.setAccessible(true);
            select.invoke(screen, item);
            require("restore".equals(field(screenType, "armed").get(screen))
                            && recovery.equals(field(screenType, "operation").get(screen)),
                    "repeated row selection preserves restore confirmation and retry identity");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var gate = AvatarLoadExecutor.submit(() -> {
                entered.countDown();
                try {
                    require(release.await(5, TimeUnit.SECONDS), "preparation gate is bounded");
                } catch (InterruptedException failure) {
                    throw new AssertionError(failure);
                }
                return true;
            });
            try {
                require(entered.await(5, TimeUnit.SECONDS), "actual model worker is occupied");
                var first = preview.selectDraft(model("old"));
                var latestData = model("latest");
                var treeRoot = new CompoundTag();
                var treeChildren = new net.minecraft.nbt.ListTag();
                var treeChild = new CompoundTag();
                treeChild.putString("name", "actual-bone");
                treeChildren.add(treeChild);
                treeRoot.put("chld", treeChildren);
                latestData.put("models", treeRoot);
                var latest = preview.selectDraft(latestData);
                require(!first.isDone() && !latest.isDone(), "preview selection never waits for the model worker");
                release.countDown();
                gate.get(5, TimeUnit.SECONDS);
                require(!await(first, drain) && await(latest, drain), "only the newest preview generation publishes");
            } finally {
                release.countDown();
            }
            Avatar avatar = AvatarManager.getAvatar(entity);
            require(preview.hierarchy().nodes().stream().anyMatch(node -> node.name().equals("actual-bone")),
                    "ready preview snapshots the actual loaded Figura part hierarchy");
            var snapshot = preview.hierarchy();
            var addedPart = new top.aihmc.omnimorph.model.FiguraModelPart(avatar, "later-script-part",
                    new top.aihmc.omnimorph.model.PartCustomization(), Map.of(), new java.util.ArrayList<>());
            avatar.renderer.root.children.add(addedPart);
            preview.tick();
            require(preview.hierarchy() == snapshot && snapshot.nodes().stream()
                    .noneMatch(node -> node.name().equals("later-script-part")), "hierarchy is not rebuilt every frame");
            preview.refreshHierarchy();
            require(preview.hierarchy().nodes().stream().anyMatch(node -> node.name().equals("later-script-part")),
                    "explicit refresh includes runtime structure changes");
            avatar.renderer.root.children.remove(addedPart);
            require(preview.showsVanillaPlayer(), "Figura parts retain the vanilla player unless the model hides it");
            assertVanillaBody(avatar, true);
            require(avatar != null && avatar.name.equals("latest") && !avatar.isHost,
                    "preview is a detached, non-host model");
            var control = new top.aihmc.omnimorph.avatar.control.AvatarControlDefinition("preview-toggle",
                    top.aihmc.omnimorph.avatar.control.AvatarControlType.TOGGLE);
            top.aihmc.omnimorph.avatar.control.AvatarControlValueStore.apply(avatar, control);
            top.aihmc.omnimorph.avatar.control.AvatarControlValueStore.save(avatar, control);
            Avatar previewOwner = avatar;
            avatar.createYsmPreviewRuntime(child -> {
                require(child.isLibraryPreview && child.permissions == previewOwner.permissions,
                        "nested YSM preview owners retain the detached permission/persistence scope");
                return null;
            });
            require(AvatarManager.getAvatarForPlayer(owner) == avatar, "player renderer lookup uses the detached binding");
            require(avatar.usesPreviewParticles() && avatar.permissions.get(Permissions.PARTICLES)
                            == new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT).get(Permissions.PARTICLES),
                    "owned model preview enables the default particle rate while retaining its preview identity");
            for (Permissions permission : new Permissions[]{Permissions.NETWORKING,
                    Permissions.SOUNDS, Permissions.NAMEPLATE_EDIT, Permissions.CANCEL_SOUNDS}) {
                require(avatar.permissions.get(permission) == 0, "preview does not inherit world/network side-effect permissions");
            }
            var firstFrame = new LivingEntityRenderState();
            var secondFrame = new LivingEntityRenderState();
            LibraryPreviewBinding.capture(entity, firstFrame);
            LibraryPreviewBinding.capture(entity, secondFrame);
            LibraryPreviewBinding.capture(entity, secondFrame);
            var armorFrame = new AvatarRenderState();
            LibraryPreviewBinding.capture(entity, armorFrame);
            var lifetime = avatar.accelerationOwner();
            preview.clear();
            require(preview.hierarchy().nodes().isEmpty(), "retiring a preview clears its old hierarchy immediately");
            require(AvatarManager.getAvatar(firstFrame) == avatar && AvatarManager.getEntity(secondFrame) == entity,
                    "queued render states retain their detached entity and model after selection clears");
            require(lifetime.isCurrent(), "clearing a preview cannot destroy resources before deferred GUI drawing");
            LibraryPreviewBinding.finishFrame();
            require(!lifetime.isCurrent(), "finishing the GUI frame retires the model after its final state releases");
            LibraryPreviewBinding.finishFrame();

            require(await(preview.selectDraft(new CompoundTag()), drain) && preview.showsVanillaPlayer(),
                    "an empty wardrobe selection can preview the default player without activating an avatar");
            CompoundTag visible = model("Figura accessory with vanilla player");
            var visibleScripts = new CompoundTag();
            visibleScripts.putByteArray("script", "vanilla_model.PLAYER:setVisible(true)"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            visible.put("scripts", visibleScripts);
            require(await(preview.selectDraft(visible), drain) && preview.showsVanillaPlayer(),
                    "an explicit visible Figura player keeps the native body alongside the accessory");
            assertVanillaBody(AvatarManager.getAvatar(entity), true);

            CompoundTag hidden = model("full Figura replacement");
            var scripts = new CompoundTag();
            scripts.putByteArray("script", """
                    vanilla_model.PLAYER:setVisible(false)
                    local ticks = 0
                    function events.tick()
                      ticks = ticks + 1
                      models:setRot(0, ticks * 5, 0)
                    end
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hidden.put("scripts", scripts);
            require(await(preview.selectDraft(hidden), drain), "actual Figura script initializes in the detached preview");
            require(!preview.showsVanillaPlayer(), "a Figura model hiding the vanilla player keeps the complete-model default");
            assertVanillaBody(AvatarManager.getAvatar(entity), false);
            Avatar idleAvatar = AvatarManager.getAvatar(entity);
            int previousTicks = entity.tickCount;
            preview.tick();
            preview.beforeExtract(0.5f);
            require(entity.tickCount == previousTicks + 1 && !preview.showsVanillaPlayer(),
                    "standing idle clock preserves the model's initialized vanilla visibility");
            require(idleAvatar.renderer.root.getRot().y == 5d,
                    "Figura preview executes the model's normal tick script and changes its pose");
            preview.tick();
            preview.beforeExtract(0.5f);
            require(idleAvatar.renderer.root.getRot().y == 10d
                            && idleAvatar.renderer.root.getAnimScale().y == 1d,
                    "Figura preview advances model animation without an artificial breathing scale");
            try (var local = new LibraryPreviewSession(null, true)) {
                var localEntity = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
                localEntity.setUUID((UUID) field(LibraryPreviewSession.class, "owner").get(local));
                field(LibraryPreviewSession.class, "entity").set(local, localEntity);
                require(await(local.selectDraft(hidden), drain), "local wardrobe preview loads without a backend session");
                require(local.entity() != entity && AvatarManager.getAvatar(local.entity()) != idleAvatar,
                        "local previews retain independent entities and script state");
            }

            if (encrypted != null) {
                CompoundTag portable = model("encrypted preview");
                portable.getCompoundOrEmpty("metadata").putString("format", "ysm-native");
                try (var source = YsmEncryptedSource.read(encrypted, () -> false)) {
                    portable.put("ysm", YsmSourcePayload.fromEncrypted(source, YsmAvatarKind.NEW, null));
                }
                require(await(preview.selectDraft(portable), drain), "actual encrypted payload prepares in the detached preview");
                avatar = AvatarManager.getAvatar(entity);
                require(avatar.getYsmRuntime() != null && avatar.getYsmRuntime().encryptedSource(),
                        "encrypted preview retains the existing runtime/source boundary");
                require(!preview.hierarchy().nodes().isEmpty()
                                && preview.hierarchy().roots().size() == avatar.getYsmRuntime().rootParts().size(),
                        "YSM preview hierarchy comes from the actual loaded runtime roots");
                avatar.renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
                var encryptedPreview = avatar.getYsmRuntime().forRenderPass();
                require(encryptedPreview != null && encryptedPreview.isPreviewRuntime()
                                && !encryptedPreview.geometry().roots.isEmpty(),
                        "encrypted library model must retain renderable geometry in the actual GUI pass");
                require(!preview.showsVanillaPlayer(), "YSM replacement models do not select the decorative default");
            }
            var resource = client.submit(LibraryClient.Lane.TRANSFER, exchange -> model("administrator resource"));
            require(await(preview.selectResource(resource), drain), "administrator resource uses detached preparation");
            require(AvatarManager.getAvatar(entity).name.equals("administrator resource"),
                    "the selected resource replaces the preceding preview");
            var blockedRead = new CountDownLatch(1);
            var releaseRead = new CountDownLatch(1);
            var oldResource = client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
                blockedRead.countDown();
                require(releaseRead.await(5, TimeUnit.SECONDS), "resource gate is bounded");
                return model("retired administrator resource");
            });
            var oldPreview = preview.selectResource(oldResource);
            try {
                require(blockedRead.await(5, TimeUnit.SECONDS), "resource read starts asynchronously");
                preview.clear();
                require(oldResource.future().isCancelled(), "clearing selection cancels the administrator read");
            } finally {
                releaseRead.countDown();
            }
            require(!await(oldPreview, drain) && preview.entity() == null,
                    "a retired administrator response cannot republish an old model");
            live.set(false);
            preview.tick();
            require(preview.status().stage() == LibraryPreviewSession.Stage.CLOSED && preview.entity() == null,
                    "session retirement clears the detached preview");
            require(((Map<?, ?>) users).size() == userCount
                            && ((Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null)).size() == permissions,
                    "preview never registers a player model or persistent permissions");
            require(controlsLoaded.getBoolean(null) == previousControlsLoaded,
                    "preview control restoration and edits do not read or write the formal control store");
            require(java.util.Objects.equals(LocalAvatarLoader.getLastLoadedPath(), localPath)
                            && AvatarManager.localUploaded == uploaded && NetworkStuff.connectionEpoch() == epoch,
                    "preview leaves wardrobe selection and cloud publication state unchanged");
            System.out.println("PASS detached library preview: actual async Avatar loading, newest selection, restricted permissions, frame lifetime and encrypted runtime; GUI MANUAL/PENDING");
            testCurrentPlayerFigura(unsafe, drain, encrypted);
        } finally {
            LibraryPreviewBinding.finishFrame();
            drain.run();
        }
    }

    private static void testOtherPlayerRouting(Unsafe unsafe, LibraryClient library) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var previousLevel = minecraft.level;
        var local = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        var remote = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
        UUID target = UUID.randomUUID();
        remote.setUUID(target);
        remote.tickCount = 73;
        var level = (OtherPlayerLevel) unsafe.allocateInstance(OtherPlayerLevel.class);
        level.target = remote;
        var connection = (net.minecraft.client.multiplayer.ClientPacketListener)
                unsafe.allocateInstance(net.minecraft.client.multiplayer.ClientPacketListener.class);
        var players = new java.util.HashMap<UUID, net.minecraft.client.multiplayer.PlayerInfo>();
        players.put(target, (net.minecraft.client.multiplayer.PlayerInfo)
                unsafe.allocateInstance(net.minecraft.client.multiplayer.PlayerInfo.class));
        field(connection.getClass(), "playerInfoMap").set(connection, players);
        field(net.minecraft.client.player.LocalPlayer.class, "connection").set(local, connection);
        minecraft.player = local;
        minecraft.level = level;
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var pending = new java.util.concurrent.atomic.AtomicReference<LibraryClient.Request<CompoundTag>>();
        var release = new CountDownLatch(1);
        try (var preview = new LibraryPreviewSession(library)) {
            preview.selectPlayerResource(target, () -> {
                reads.incrementAndGet();
                var request = library.submit(LibraryClient.Lane.CONTROL, exchange -> {
                    release.await(5, TimeUnit.SECONDS);
                    return model("offline copy");
                });
                pending.set(request);
                return request;
            });
            require(preview.entity() == remote && reads.get() == 0,
                    "online player with its entity borrows the original preview without downloading a copy");
            var invoked = new AtomicBoolean();
            preview.withPlayerContext(() -> {
                invoked.set(true);
                require(LibraryPreviewBinding.avatar(remote) == null,
                        "borrowed player never replaces the original Avatar lookup");
            });
            preview.tick();
            preview.beforeExtract(0.5f);
            require(invoked.get() && remote.tickCount == 73,
                    "borrowed preview uses native context without independently ticking the player");
            level.target = null;
            preview.tick();
            require(reads.get() == 1 && preview.entity() == null,
                    "online player without an entity starts the isolated fallback");
            level.target = remote;
            preview.tick();
            require(preview.entity() == remote && pending.get().future().isCancelled(),
                    "entity arrival cancels the pending isolated generation");
            players.clear();
            preview.tick();
            require(reads.get() == 2 && preview.entity() == null,
                    "an entity without an online player entry also requires isolation");
            preview.close();
            require(pending.get().future().isCancelled() && !remote.isRemoved(),
                    "closing cancels fallback work and preserves the borrowed entity");
            System.out.println("PASS management/permissions player routing: online entity, missing entity, offline, cancellation and borrowed lifetime; GUI MANUAL/PENDING");
        } finally {
            release.countDown();
            minecraft.player = previousPlayer;
            minecraft.level = previousLevel;
        }
    }

    private static void testOtherPlayerDraft(Unsafe unsafe, Runnable drain) throws Exception {
        UUID target = UUID.randomUUID();
        var source = new java.util.concurrent.atomic.AtomicReference<>(model("offline player"));
        Avatar copy;
        try (var preview = new LibraryPreviewSession(null, true)) {
            var entity = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
            UUID owner = (UUID) field(LibraryPreviewSession.class, "owner").get(preview);
            entity.setUUID(owner);
            field(LibraryPreviewSession.class, "entity").set(preview, entity);
            field(LibraryPreviewSession.class, "detachedEntity").set(preview, entity);
            preview.selectPlayerDraft(target, source::get);
            waitReady(preview, drain);
            copy = (Avatar) field(LibraryPreviewSession.class, "avatar").get(preview);
            require(preview.entity() == entity && copy.isLibraryPreview && !copy.owner.equals(target),
                    "offline permissions preview loads an isolated native avatar with a private identity");
            source.set(model("updated offline player"));
            preview.tick();
            waitReady(preview, drain);
            Avatar latest = (Avatar) field(LibraryPreviewSession.class, "avatar").get(preview);
            require(latest != copy && !copy.accelerationOwner().isCurrent(),
                    "late or refreshed player data retires the previous independent copy");
            require(latest.nbt.getCompoundOrEmpty("metadata").getStringOr("name", "")
                            .equals("updated offline player"),
                    "permissions fallback uses the refreshed player model");
            copy = latest;
        }
        require(!copy.accelerationOwner().isCurrent(), "closing permissions preview releases only its own copy");
        System.out.println("PASS offline permissions copy: native load, private identity, refreshed NBT and cleanup; GUI MANUAL/PENDING");
    }

    private static void waitReady(LibraryPreviewSession preview, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (preview.status().stage() == LibraryPreviewSession.Stage.LOADING && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(5);
        }
        require(preview.status().stage() == LibraryPreviewSession.Stage.READY,
                "player fallback preparation completes: " + preview.status());
    }

    private static void testCatalogOutfitPreview(Unsafe unsafe, Runnable drain) throws Exception {
        var minecraft = Minecraft.getInstance();
        var originalPlayer = minecraft.player;
        var wearer = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        wearer.setUUID(FiguraMod.getLocalPlayerUUID());
        minecraft.player = wearer;
        var isolated = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        isolated.setUUID(UUID.randomUUID());
        Path cosmetics = Files.createDirectories(FiguraMod.getFiguraDirectory().resolve("cosmetics"));
        Path accessory = Files.createTempDirectory(cosmetics, "catalog-outfit-");
        Files.writeString(accessory.resolve("cosmetic.json"), CloudCosmeticSourceSmokeTest.MANIFEST);
        Files.writeString(accessory.resolve("script.lua"), "return 1");
        top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry entry;
        try (var inventory = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory(FiguraMod.getFiguraDirectory())) {
            entry = inventory.scan(() -> false).stream().filter(value -> value.path().equals(accessory))
                    .findFirst().orElseThrow();
        }
        var sourceDraft = new top.aihmc.omnimorph.model.outfit.OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
        sourceDraft.add(entry.reference(), entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
        sourceDraft.add(entry.reference(), entry.source().metadata().defaultAnchor(), OutfitConfig.Transform.identity());
        var configuration = new java.util.concurrent.atomic.AtomicReference<>(sourceDraft.configuration());
        var configurationRevision = new java.util.concurrent.atomic.AtomicInteger(1);
        var cloud = new CatalogCloudDependencies(model("Catalog cloud base"),
                top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.prepare(accessory, wearer.getUUID(),
                        entry.source().contentHash(), () -> false).nbt(), entry.source().metadata(), wearer.getUUID());
        UUID modelId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        var gson = new com.google.gson.GsonBuilder()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        var extension = new top.aihmc.omnimorph.backend2.library.LibraryModels.OutfitCapabilities(1, true, true,
                false, false, 32, 128, 64, 8192, 32768, 1024, 65536, 8, 256, OutfitLimits.defaults(), true);
        var capabilities = new top.aihmc.omnimorph.backend2.library.LibraryModels.Capabilities(true, 1,
                1024 * 1024, 64, 1024 * 1024, 1024, 1024, java.util.List.of("zip", "ysm"),
                java.util.List.of("FIGURA"), java.util.List.of("general"), false, extension);
        var unexpected = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", request -> {
            try (request) {
                if (cloud.handle(request)) {
                    return;
                }
                String path = request.getRequestURI().getPath();
                byte[] configurationBytes = top.aihmc.omnimorph.model.outfit.OutfitCodec.encode(configuration.get())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                String body;
                if (path.equals("/api/library/v1/capabilities")) {
                    body = gson.toJson(capabilities);
                } else if (path.equals("/api/library/v1/admin/wardrobe-preview/LIBRARY_RECYCLE/" + modelId)) {
                    require("synthetic-admin".equals(request.getRequestHeaders().getFirst("x-omnimorph-admin")),
                            "recycled configuration preview retains explicit administrator authority");
                    request.getResponseHeaders().add("Content-Type", "application/json");
                    request.getResponseHeaders().add("x-omnimorph-sha256",
                            top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(configurationBytes));
                    if (request.getRequestMethod().equals("HEAD")) {
                        request.getResponseHeaders().add("Content-Length", Integer.toString(configurationBytes.length));
                        request.sendResponseHeaders(200, -1);
                    } else {
                        request.sendResponseHeaders(200, configurationBytes.length);
                        request.getResponseBody().write(configurationBytes);
                    }
                    return;
                } else if (path.equals("/api/library/v1/models/" + modelId)) {
                    body = """
                            {"model_id":"%s","revision":%d,"policy_revision":1,"display_name":"preset",
                             "internal_name":"preset","authors":[],"uploader":{"uuid":"%s","name":"owner"},
                             "model_type":null,"resource_kind":"OUTFIT","visibility":"PUBLIC",
                             "allowed_actions":["preview"],"byte_length":%d}
                            """.formatted(modelId, configurationRevision.get(), wearer.getUUID(), configurationBytes.length);
                } else if (path.equals("/api/library/v1/models/" + modelId + "/transfers")) {
                    body = """
                            {"transfer_id":"%s","purpose":"preview","model_revision":%d,"policy_revision":1,
                             "relative_path":"/api/library/v1/transfers/%s","expires_at":2000000000}
                            """.formatted(transferId, configurationRevision.get(), transferId);
                } else if (path.equals("/api/library/v1/transfers/" + transferId)) {
                    request.getResponseHeaders().add("Content-Type", "application/json");
                    request.getResponseHeaders().add("x-omnimorph-sha256",
                            top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(configurationBytes));
                    request.sendResponseHeaders(200, configurationBytes.length);
                    request.getResponseBody().write(configurationBytes);
                    return;
                } else {
                    unexpected.incrementAndGet();
                    body = "{}";
                }
                byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                request.sendResponseHeaders(200, bytes.length);
                request.getResponseBody().write(bytes);
            }
        });
        server.start();
        Path cache = Files.createTempDirectory("outfit-catalog-preview-");
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                request -> request.header("Authorization", "Bearer synthetic"));
             var client = new LibraryClient(access, new LibrarySession(origin, wearer.getUUID(), 1, () -> true),
                     minecraft::execute);
             var preview = new LibraryPreviewSession(client)) {
            field(LibraryPreviewSession.class, "detachedEntity").set(preview, isolated);
            require(await(preview.select(modelId, new LibraryTransferService(client, cache)), drain)
                            && preview.status().stage() == LibraryPreviewSession.Stage.READY
                            && preview.outfitComposition() != null && preview.entity() == isolated
                            && preview.outfitBase() == null,
                    "catalog selection fetches real JSON and prepares a private vanilla composition without NBT");
            testCollectionOutfitImport(unsafe, drain, client, cache.resolve("collection"), modelId);
            try (var management = new top.aihmc.omnimorph.gui.widgets.library.LibraryManagementPreview(
                    client, () -> "synthetic-admin")) {
                var recycled = (LibraryPreviewSession) field(management.getClass(), "session").get(management);
                var detached = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
                detached.setUUID(UUID.randomUUID());
                field(LibraryPreviewSession.class, "detachedEntity").set(recycled, detached);
                var item = new top.aihmc.omnimorph.backend2.library.LibraryRecycleService.Item("LIBRARY", modelId,
                        wearer.getUUID(), "owner", "Deleted preset", java.util.List.of(), null,
                        "DELETED", 3, 1, 0, "", 0, true, "OUTFIT");
                management.recycled(item, net.minecraft.network.chat.Component.literal(item.name()));
                awaitCondition(() -> recycled.configurationPreview() != null, drain);
                require(recycled.entity() == detached && recycled.outfitBase() == null
                                && recycled.configurationPreview().configuration().instances().size() == 2,
                        "actual management preview routes a formatless recycled Outfit through JSON and private native dependencies");
                management.clear();
                require(recycled.configurationPreview() == null && recycled.outfitComposition() == null,
                        "clearing recycled JSON retires its prepared resources and private composition");
            }
            configuration.set(cloud.configuration(origin));
            configurationRevision.set(2);
            var cloudTransfers = new LibraryTransferService(client, cache.resolve("cloud-composition"));
            require(await(preview.select(modelId, cloudTransfers), drain)
                            && preview.outfitBase() != null && preview.outfitBase().isLibraryPreview
                            && preview.outfitComposition().editorSnapshot(preview.outfitBase()).configuration().instances().size() == 2
                            && cloud.files.get() == 2,
                    "one cloud Outfit resolves its exact cloud base and shared accessory into two private instances");
            int details = cloud.details.get();
            require(await(preview.select(modelId, cloudTransfers), drain)
                            && cloud.files.get() == 2 && cloud.details.get() == details + 2,
                    "repeated complete cloud Outfit preview rechecks both policies without downloading either native body");
            cloud.revoked.set(true);
            require(!await(preview.select(modelId, cloudTransfers), drain)
                            && preview.configurationIssues().size() == 1
                            && preview.configurationIssues().getFirst().instances().size() == 2
                            && preview.configurationIssues().getFirst().code().equals("FORBIDDEN")
                            && preview.outfitComposition() == null && cloud.files.get() == 2,
                    "revoking one dependency rejects the complete cached preview and identifies both affected copies");
            cloud.revoked.set(false);
            var draft = new top.aihmc.omnimorph.model.outfit.OutfitDraft(OutfitConfig.empty(), OutfitLimits.defaults());
            var missing = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null, null,
                    "0".repeat(64), OutfitConfig.Format.FIGURA);
            draft.add(missing, OutfitConfig.AnchorRef.authorDefault("hip"), OutfitConfig.Transform.identity());
            configuration.set(draft.configuration());
            configurationRevision.set(3);
            require(!await(preview.select(modelId, new LibraryTransferService(client, cache.resolve("missing"))), drain)
                            && preview.status().stage() == LibraryPreviewSession.Stage.FAILED
                            && preview.configurationIssues().size() == 1
                            && preview.configurationIssues().getFirst().resource().equals(missing)
                            && preview.outfitComposition() == null,
                    "catalog dependency failure retains its exact resource and affected instances without partial publication");
            var widget = new top.aihmc.omnimorph.gui.widgets.EntityPreview(10, 20, 240, 180,
                    40, -15, 30, null, null);
            widget.setLibraryPreview(preview);
            widget.tick();
            var issueList = (LibraryRowList) field(widget.getClass(), "dependencyIssues").get(widget);
            @SuppressWarnings("unchecked")
            var issueRows = (java.util.List<LibraryRowList.Row>) field(LibraryRowList.class, "rows").get(issueList);
            require(issueList.isVisible() && issueRows.size() == 1
                            && issueRows.getFirst().tooltip().getString().contains(missing.localId().toString())
                            && issueRows.getFirst().text().getString().contains(draft.configuration().instances()
                                    .getFirst().instanceId().toString()),
                    "actual shared preview widget exposes the failed resource and its affected copy in scrollable rows");
            widget.setDependencyIssues(java.util.List.of(new OutfitPresetResources.Issue(missing, true,
                    java.util.List.of(UUID.randomUUID(), UUID.randomUUID()), "FORBIDDEN")));
            @SuppressWarnings("unchecked")
            var applyRows = (java.util.List<LibraryRowList.Row>) field(LibraryRowList.class, "rows").get(issueList);
            require(applyRows.size() == 3 && issueList.isVisible(),
                    "formal Apply failures retain separate base and duplicate-instance rows without replacing the preview");
            widget.setDependencyIssues(java.util.List.of());
            var pending = preview.select(modelId, new LibraryTransferService(client, cache.resolve("cancelled")));
            preview.clear();
            widget.tick();
            require(!await(pending, drain) && preview.status().stage() == LibraryPreviewSession.Stage.IDLE
                            && preview.configurationIssues().isEmpty() && preview.outfitComposition() == null
                            && !issueList.isVisible() && unexpected.get() == 0,
                    "cleared catalog work cannot publish late dependency errors, equip, join or upload");
        } finally {
            server.stop(0);
            minecraft.player = originalPlayer;
            try (var files = Files.walk(cache)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
            try (var files = Files.walk(accessory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        System.out.println("PASS catalog Outfit preview: HTTP JSON selection, independent composition, dependency issues and generation retirement; GUI MANUAL/PENDING");
    }

    private static final class CatalogCloudDependencies {
        private record Resource(UUID id, UUID transfer, String kind, byte[] bytes) {}

        private final java.util.List<Resource> resources;
        private final top.aihmc.omnimorph.model.outfit.CosmeticMetadata metadata;
        private final UUID owner;
        private final java.util.concurrent.atomic.AtomicInteger files = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger details = new java.util.concurrent.atomic.AtomicInteger();
        private final AtomicBoolean revoked = new AtomicBoolean();

        private CatalogCloudDependencies(CompoundTag base, CompoundTag accessory,
                                         top.aihmc.omnimorph.model.outfit.CosmeticMetadata metadata, UUID owner) throws Exception {
            this.metadata = metadata;
            this.owner = owner;
            var baseBytes = new java.io.ByteArrayOutputStream();
            var accessoryBytes = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(base, baseBytes);
            net.minecraft.nbt.NbtIo.writeCompressed(accessory, accessoryBytes);
            resources = java.util.List.of(new Resource(UUID.randomUUID(), UUID.randomUUID(), "AVATAR", baseBytes.toByteArray()),
                    new Resource(UUID.randomUUID(), UUID.randomUUID(), "ACCESSORY", accessoryBytes.toByteArray()));
        }

        private OutfitConfig configuration(String origin) {
            var base = reference(resources.getFirst(), origin);
            var accessory = reference(resources.getLast(), origin);
            var draft = new top.aihmc.omnimorph.model.outfit.OutfitDraft(new OutfitConfig(1, 0, base,
                    java.util.List.of(), java.util.List.of(), Map.of(), false), OutfitLimits.defaults());
            draft.add(accessory, metadata.defaultAnchor(), OutfitConfig.Transform.identity());
            draft.add(accessory, metadata.defaultAnchor(), OutfitConfig.Transform.identity());
            return draft.configuration();
        }

        private static OutfitConfig.ResourceRef reference(Resource resource, String origin) {
            return new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, origin, resource.id(), 1L,
                    top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(resource.bytes()), OutfitConfig.Format.FIGURA);
        }

        private boolean handle(com.sun.net.httpserver.HttpExchange request) throws java.io.IOException {
            String path = request.getRequestURI().getPath();
            for (var resource : resources) {
                String modelPath = "/api/library/v1/models/" + resource.id();
                if (path.equals(modelPath)) {
                    details.incrementAndGet();
                    if (resource.kind().equals("ACCESSORY") && revoked.get()) {
                        send(request, 403, "{\"code\":\"FORBIDDEN\"}");
                        return true;
                    }
                    var value = com.google.gson.JsonParser.parseString("""
                            {"model_id":"%s","revision":1,"policy_revision":1,"display_name":"dependency",
                             "internal_name":"dependency","authors":[],"uploader":{"uuid":"%s","name":"owner"},
                             "model_type":"FIGURA","resource_kind":"%s","visibility":"PRIVATE",
                             "allowed_actions":["preview"],"byte_length":%d}
                            """.formatted(resource.id(), owner, resource.kind(), resource.bytes().length)).getAsJsonObject();
                    if (resource.kind().equals("ACCESSORY")) {
                        value.add("accessory_metadata", top.aihmc.omnimorph.model.outfit.OutfitCodec.encodeAccessoryMetadata(metadata));
                    }
                    send(request, 200, value.toString());
                    return true;
                }
                if (path.equals(modelPath + "/transfers")) {
                    send(request, 200, """
                            {"transfer_id":"%s","purpose":"preview","model_revision":1,"policy_revision":1,
                             "relative_path":"/api/library/v1/transfers/%s","expires_at":2000000000}
                            """.formatted(resource.transfer(), resource.transfer()));
                    return true;
                }
                if (path.equals("/api/library/v1/transfers/" + resource.transfer())) {
                    files.incrementAndGet();
                    request.getResponseHeaders().add("x-omnimorph-sha256",
                            top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(resource.bytes()));
                    request.sendResponseHeaders(200, resource.bytes().length);
                    request.getResponseBody().write(resource.bytes());
                    return true;
                }
            }
            return false;
        }

        private static void send(com.sun.net.httpserver.HttpExchange request, int status, String body) throws java.io.IOException {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            request.sendResponseHeaders(status, bytes.length);
            request.getResponseBody().write(bytes);
        }
    }

    private static void testCollectionOutfitImport(Unsafe unsafe, Runnable drain, LibraryClient client,
                                                   Path cache, UUID modelId) throws Exception {
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        UUID account = FiguraMod.getLocalPlayerUUID();
        boolean hadPermissions = packs.containsKey(account);
        var input = field(Minecraft.class, "lastInputType");
        var previousInput = input.get(Minecraft.getInstance());
        input.set(Minecraft.getInstance(), net.minecraft.client.InputType.MOUSE);
        var type = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
        // Screen rebuilding is suppressed; real selection/import/cancel handlers and native preparation run.
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null) {
            @Override
            protected void init() {
            }
        };
        try (var collection = new LibraryCollectionService(client, cache)) {
            var model = await(client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                    "/api/library/v1/models/" + modelId, null,
                    top.aihmc.omnimorph.backend2.library.LibraryModels.Model.class)).future(), drain);
            var entry = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(
                    modelId, 1, "DOWNLOAD", false, model);
            field(collection.getClass(), "entries").set(collection, java.util.List.of(entry));
            field(collection.getClass(), "authoritative").setBoolean(collection, true);
            var preview = new LibraryPreviewSession(client);
            var isolated = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
            isolated.setUUID(UUID.randomUUID());
            field(LibraryPreviewSession.class, "detachedEntity").set(preview, isolated);
            field(type, "client").set(screen, client);
            field(type, "collection").set(screen, collection);
            field(type, "transfers").set(screen, new LibraryTransferService(client, cache));
            field(type, "preview").set(screen, preview);
            field(type, "previewClient").set(screen, client);
            var capabilities = client.capabilities();
            await(capabilities.future(), drain);
            field(type, "outfitCapabilitiesRequest").set(screen, capabilities);
            field(type, "tab").set(screen, java.util.Arrays.stream(field(type, "tab").getType().getEnumConstants())
                    .filter(value -> value.toString().equals("COLLECTION")).findFirst().orElseThrow());
            var select = type.getDeclaredMethod("selectCloud", entry.getClass());
            select.setAccessible(true);
            select.invoke(screen, entry);
            awaitCondition(() -> preview.configurationPreview() != null, drain);
            var sourceConfiguration = preview.configurationPreview().configuration();
            require(field(type, "outfitDraft").get(screen) == null,
                    "selecting a collection Outfit only previews and does not create or modify a draft");
            var importSelected = type.getDeclaredMethod("changeCandidate");
            importSelected.setAccessible(true);
            importSelected.invoke(screen);
            var imported = (CompletableFuture<?>) field(type, "collectionChange").get(screen);
            require(Boolean.TRUE.equals(await(imported, drain)), "collection Outfit uses explicit import rather than candidate membership");
            var draft = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(type, "outfitDraft").get(screen);
            require(draft.configuration().instances().size() == 2 && draft.dirty()
                            && draft.configuration().instances().stream().noneMatch(copy -> sourceConfiguration.instances()
                                    .stream().anyMatch(original -> original.instanceId().equals(copy.instanceId())))
                            && field(type, "tab").get(screen).toString().equals("OUTFIT")
                            && field(type, "selected").get(screen) == null,
                    "import selects the outfit editor and transfers configuration into its existing draft");
            var cancel = type.getDeclaredMethod("cancelOutfitDraft");
            cancel.setAccessible(true);
            cancel.invoke(screen);
            require(!draft.dirty() && draft.configuration().instances().isEmpty()
                            && field(type, "draftBase").get(screen) == field(type, "committedDraftBase").get(screen),
                    "cancelling imported configuration restores the original draft and original base snapshot");
            var previousPreview = (LibraryPreviewSession) field(type, "preview").get(screen);
            previousPreview.close();
            var cancelledPreview = new LibraryPreviewSession(client);
            field(LibraryPreviewSession.class, "detachedEntity").set(cancelledPreview, isolated);
            field(type, "preview").set(screen, cancelledPreview);
            field(type, "previewClient").set(screen, client);
            field(type, "tab").set(screen, java.util.Arrays.stream(field(type, "tab").getType().getEnumConstants())
                    .filter(value -> value.toString().equals("COLLECTION")).findFirst().orElseThrow());
            select.invoke(screen, entry);
            awaitCondition(() -> cancelledPreview.configurationPreview() != null, drain);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var occupied = AvatarLoadExecutor.submit(() -> {
                entered.countDown();
                try {
                    require(release.await(10, TimeUnit.SECONDS), "bounded model worker release");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return true;
            });
            CompletableFuture<?> retired;
            try {
                require(entered.await(5, TimeUnit.SECONDS), "actual model worker is occupied before stale import");
                importSelected.invoke(screen);
                retired = (CompletableFuture<?>) field(type, "collectionChange").get(screen);
                cancelledPreview.clear();
            } finally {
                release.countDown();
            }
            await(occupied, drain);
            require(!Boolean.TRUE.equals(await(retired, drain)) && !draft.dirty()
                            && draft.configuration().instances().isEmpty(),
                    "retiring the exact preview before import publication rejects stale prepared resources");
        } finally {
            screen.removed();
            input.set(Minecraft.getInstance(), previousInput);
            if (!hadPermissions) {
                packs.remove(account);
            }
        }
        System.out.println("PASS collection Outfit workflow: authorized selection, explicit draft import, editor routing and cancellation; GUI MANUAL/PENDING");
    }

    private static void testLegacyCubeType() {
        var gson = top.aihmc.omnimorph.parsers.BlockbenchCommonTypes.getGson();
        var element = top.aihmc.omnimorph.parsers.BlockbenchCommonTypes.Element.class;
        require(gson.fromJson("{}", element) instanceof
                        top.aihmc.omnimorph.parsers.BlockbenchCommonTypes.CubeElement,
                "legacy elements without a type retain cube geometry");
        require(gson.fromJson("{\"type\":null}", element) instanceof
                        top.aihmc.omnimorph.parsers.BlockbenchCommonTypes.CubeElement,
                "legacy null cube types retain their original default");
        require(gson.fromJson("{\"type\":\"mesh\"}", element) instanceof
                        top.aihmc.omnimorph.parsers.BlockbenchCommonTypes.MeshElement,
                "explicit mesh types keep their existing decoder");
        require(gson.fromJson("{\"type\":\"unsupported\"}", element) instanceof
                        top.aihmc.omnimorph.parsers.BlockbenchCommonTypes.UnknownElement,
                "unknown explicit types do not become cubes");
        var generated = top.aihmc.omnimorph.wizards.CosmeticTemplate.LEGS.model("").build();
        require(generated.getAsJsonArray("elements").get(0).getAsJsonObject()
                        .get("type").getAsString().equals("cube"),
                "new exported templates declare cube types explicitly");
    }

    private static void testProvidedCosmetics(Unsafe unsafe, Runnable drain) throws Exception {
        String directory = System.getenv("OMNIMORPH_TEST_COSMETICS");
        if (directory == null || directory.isBlank()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        minecraft.player = player;
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), player.getUUID().toString());
        var previousDebug = minecraft.debugEntries;
        var debug = (net.minecraft.client.gui.components.debug.DebugScreenEntryList) unsafe.allocateInstance(
                net.minecraft.client.gui.components.debug.DebugScreenEntryList.class);
        field(debug.getClass(), "allStatuses").set(debug, new java.util.HashMap<>());
        field(debug.getClass(), "currentlyEnabled").set(debug, new java.util.ArrayList<>());
        field(Minecraft.class, "debugEntries").set(minecraft, debug);
        var previousRenderer = minecraft.gameRenderer;
        var renderer = (net.minecraft.client.renderer.GameRenderer) unsafe.allocateInstance(
                net.minecraft.client.renderer.GameRenderer.class);
        field(renderer.getClass(), "mainCamera").set(renderer, new net.minecraft.client.Camera());
        field(Minecraft.class, "gameRenderer").set(minecraft, renderer);
        boolean previousPaperdoll = top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll;
        try (var device = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingTextureDevice();
             var directories = Files.list(Path.of(directory))) {
            for (Path path : directories.filter(Files::isDirectory).sorted().toList()) {
                var description = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(path, () -> false);
                var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                        null, description.contentHash(), description.format());
                var declaration = new OutfitConfig.Instance(UUID.randomUUID(), reference, 1, true,
                        description.metadata().category().equals("Trail")
                                ? new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null)
                                : description.metadata().defaultAnchor(), description.metadata().defaultTransform(),
                        java.util.List.of(), Map.of(), Map.of());
                var config = new OutfitConfig(1, 0, null, java.util.List.of(declaration), java.util.List.of(),
                        Map.of(reference.identity(), 1), false);
                try (var preview = new LibraryPreviewSession()) {
                    require(preview.selectVanilla(), "actual-resource vanilla preview starts");
                    require(await(preview.prepareComposition(config, OutfitLimits.defaults(), player.getUUID(),
                            UUID.randomUUID(), permissions, (resource, cancelled) ->
                                    top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.prepare(path,
                                            player.getUUID(), description.contentHash(), cancelled).nbt()), drain),
                            "actual accessory prepares: " + path.getFileName());
                    preview.tick();
                    var composition = preview.outfitComposition();
                    require(composition.runtimeInstances().size() == 1,
                            "actual accessory remains script-error free: " + path.getFileName());
                    var vertices = new java.util.concurrent.atomic.AtomicInteger();
                    var sink = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                            LibraryPreviewRuntimeSmokeTest.class.getClassLoader(),
                            new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.isDefault()) {
                            return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        if (method.getName().equals("addVertex")) {
                            vertices.incrementAndGet();
                        }
                        return method.getReturnType() == void.class ? null : proxy;
                    });
                    top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = true;
                    try (var frame = composition.retain()) {
                        var matrix = new org.joml.Matrix4f();
                        top.aihmc.omnimorph.model.outfit.OutfitNativeRenderer.drawVanilla(frame,
                                LibraryFiguraPreviewRenderer.createModel(false), new AvatarRenderState(),
                                .5f, matrix, matrix, material -> sink,
                                top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI);
                    }
                    System.out.println("Actual accessory " + path.getFileName() + ": vertices=" + vertices.get());
                    require(description.metadata().category().equals("Trail") || vertices.get() > 0,
                            "actual non-particle accessory emits native geometry: " + path.getFileName());
                }
                LibraryPreviewBinding.finishFrame();
            }
        } finally {
            minecraft.player = previousPlayer;
            field(Minecraft.class, "debugEntries").set(minecraft, previousDebug);
            field(Minecraft.class, "gameRenderer").set(minecraft, previousRenderer);
            top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = previousPaperdoll;
        }
    }

    /** Actual distributed YSM packages as a draft base: the composed body must not stop at the detached face budget. */
    private static void testPackagedYsmDraftBases(Unsafe unsafe, Runnable drain) throws Exception {
        String directory = System.getenv("OMNIMORPH_TEST_YSM_PACKAGES");
        if (directory == null || directory.isBlank()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        minecraft.player = player;
        // The wardrobe passes the local wearer's own pack, whose trust is normally above the detached default.
        var wearer = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.MAX), player.getUUID().toString());
        java.util.List<Path> packages;
        try (var files = Files.list(Path.of(directory))) {
            packages = files.filter(Files::isRegularFile).sorted().toList();
        }
        try (var device = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingTextureDevice()) {
            for (Path path : packages) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(path,
                        net.minecraft.nbt.NbtAccounter.create(256L * 1024 * 1024));
                if (!nbt.getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native")) {
                    continue;
                }
                try (var preview = new LibraryPreviewSession()) {
                    require(await(preview.selectDraft(nbt), drain), "actual packaged YSM draft base loads: " + path.getFileName());
                    var base = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, "https://source.example",
                            UUID.randomUUID(), 1L, "5".repeat(64), OutfitConfig.Format.YSM_ENCRYPTED);
                    var config = new OutfitConfig(1, 0, base, java.util.List.of(), java.util.List.of(), Map.of(), false);
                    require(await(preview.prepareComposition(config, OutfitLimits.defaults(), player.getUUID(),
                                    UUID.randomUUID(), wearer, (resource, cancelled) -> {
                                        throw new java.io.IOException("NO_INSTANCES");
                                    }), drain),
                            "actual packaged YSM draft composition prepares: " + path.getFileName());
                    var avatar = (Avatar) field(LibraryPreviewSession.class, "avatar").get(preview);
                    avatar.renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.RENDER;
                    var runtime = avatar.getYsmRuntime().forRenderPass();
                    var direct = new java.util.concurrent.atomic.AtomicInteger();
                    var composed = new java.util.concurrent.atomic.AtomicInteger();
                    var directPose = runtime.captureRenderPose(false);
                    top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                    runtime.renderer().renderWithAttachments(new com.mojang.blaze3d.vertex.PoseStack(),
                            type -> countingSink(direct), 0x00f000f0, directPose);
                    try (var frame = preview.outfitComposition().retain()) {
                        var framePose = runtime.captureRenderPose(false);
                        frame.captureYsmBase(runtime, new org.joml.Matrix4f(), framePose);
                        top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                        frame.drawYsmBase(runtime.renderBudgetOwner(), () -> runtime.renderer().renderWithAttachments(
                                new com.mojang.blaze3d.vertex.PoseStack(), type -> countingSink(composed),
                                0x00f000f0, framePose));
                    }
                    System.out.println("Packaged YSM draft base " + path.getFileName() + ": direct=" + direct.get()
                            + " composed=" + composed.get());
                    require(direct.get() > 0 && composed.get() == direct.get(),
                            "draft outfit base draws the whole packaged YSM body: " + path.getFileName()
                                    + " direct=" + direct.get() + " composed=" + composed.get());
                }
                LibraryPreviewBinding.finishFrame();
            }
        } finally {
            minecraft.player = previousPlayer;
        }
        System.out.println("PASS actual packaged YSM draft bases keep every native body vertex under the wearer face budget; GUI/GPU MANUAL/PENDING");
    }

    /** Actual distributed Figura base plus actual local accessory: the draft frame must leave faces for the accessory. */
    private static void testPackagedFiguraDraftAccessory(Unsafe unsafe, Runnable drain) throws Exception {
        String basePackage = System.getenv("OMNIMORPH_TEST_FIGURA_BASE");
        String accessoryDirectory = System.getenv("OMNIMORPH_TEST_ACCESSORY");
        if (basePackage == null || basePackage.isBlank() || accessoryDirectory == null || accessoryDirectory.isBlank()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var previousDebug = minecraft.debugEntries;
        var previousRenderer = minecraft.gameRenderer;
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        minecraft.player = player;
        var debug = (net.minecraft.client.gui.components.debug.DebugScreenEntryList) unsafe.allocateInstance(
                net.minecraft.client.gui.components.debug.DebugScreenEntryList.class);
        field(debug.getClass(), "allStatuses").set(debug, new java.util.HashMap<>());
        field(debug.getClass(), "currentlyEnabled").set(debug, new java.util.ArrayList<>());
        field(Minecraft.class, "debugEntries").set(minecraft, debug);
        var renderer = (net.minecraft.client.renderer.GameRenderer) unsafe.allocateInstance(
                net.minecraft.client.renderer.GameRenderer.class);
        field(renderer.getClass(), "mainCamera").set(renderer, new net.minecraft.client.Camera());
        field(Minecraft.class, "gameRenderer").set(minecraft, renderer);
        // The local wearer's own pack is MAX; the detached preview default would stop at 2048 faces.
        var wearer = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.MAX), player.getUUID().toString());
        CompoundTag baseData = net.minecraft.nbt.NbtIo.readCompressed(Path.of(basePackage),
                net.minecraft.nbt.NbtAccounter.create(256L * 1024 * 1024));
        Path source = Path.of(accessoryDirectory);
        var description = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(source, () -> false);
        var accessory = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null, null,
                description.contentHash(), description.format());
        var base = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, "https://source.example",
                UUID.randomUUID(), 1L, "6".repeat(64), OutfitConfig.Format.FIGURA);
        try (var device = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingTextureDevice();
             var preview = new LibraryPreviewSession(null, true)) {
            var detachedEntity = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
            detachedEntity.setUUID((UUID) field(LibraryPreviewSession.class, "owner").get(preview));
            field(LibraryPreviewSession.class, "entity").set(preview, detachedEntity);
            require(await(preview.selectDraft(baseData), drain), "actual packaged Figura draft base loads");
            var avatar = (Avatar) field(LibraryPreviewSession.class, "avatar").get(preview);
            var definition = new top.aihmc.omnimorph.model.outfit.FiguraSkeletonAdapter(
                    (top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer) avatar.renderer, base, null,
                    new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache(), OutfitLimits.defaults())
                    .binding().definition();
            var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                    definition.reference(null, definition.uniqueNamed("Body").orElseThrow()), null, null, null);
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), accessory, 1, true, anchor,
                    OutfitConfig.Transform.identity(), java.util.List.of(), Map.of(), Map.of());
            var config = new OutfitConfig(1, 0, base, java.util.List.of(instance), java.util.List.of(),
                    Map.of(accessory.identity(), 1), false);
            require(await(preview.prepareComposition(config, OutfitLimits.defaults(), player.getUUID(),
                            UUID.randomUUID(), wearer, (resource, cancelled) ->
                                    top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.prepare(source,
                                            player.getUUID(), description.contentHash(), cancelled).nbt()), drain),
                    "actual Figura draft composition prepares with the actual accessory");
            preview.tick();
            var state = new AvatarRenderState();
            state.lightCoords = 0x00f000f0;
            var baseOnly = new java.util.concurrent.atomic.AtomicInteger();
            avatar.render.reset(65536);
            avatar.render(null, 0, .5f, 1, new com.mojang.blaze3d.vertex.PoseStack(), type -> countingSink(baseOnly),
                    state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null,
                    top.aihmc.omnimorph.model.rendering.PartFilterScheme.MODEL, false, false);
            var composed = new java.util.concurrent.atomic.AtomicInteger();
            try (var frame = preview.outfitComposition().retain()) {
                avatar.render.reset(65536);
                frame.prepareFiguraBase(avatar);
                frame.drawFiguraBase(avatar, () -> {
                    var pose = frame.captureFiguraBase(avatar, new org.joml.Matrix4f(), () -> avatar.render(
                            null, 0, .5f, 1, new com.mojang.blaze3d.vertex.PoseStack(), type -> countingSink(composed),
                            state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null,
                            top.aihmc.omnimorph.model.rendering.PartFilterScheme.MODEL, false, false));
                    top.aihmc.omnimorph.model.outfit.OutfitNativeRenderer.draw(frame, pose, state, .5f,
                            new org.joml.Matrix4f(), type -> countingSink(composed),
                            top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI);
                });
            }
            System.out.println("Packaged Figura draft base with actual accessory: base=" + baseOnly.get()
                    + " composed=" + composed.get());
            require(composed.get() > baseOnly.get(),
                    "the draft frame draws the actual accessory after the whole packaged Figura base: base="
                            + baseOnly.get() + " composed=" + composed.get());
        } finally {
            minecraft.player = previousPlayer;
            field(Minecraft.class, "debugEntries").set(minecraft, previousDebug);
            field(Minecraft.class, "gameRenderer").set(minecraft, previousRenderer);
        }
        System.out.println("PASS actual packaged Figura draft base keeps faces for its actual accessory; GUI/GPU MANUAL/PENDING");
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer countingSink(java.util.concurrent.atomic.AtomicInteger vertices) {
        return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                LibraryPreviewRuntimeSmokeTest.class.getClassLoader(),
                new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (method.getName().equals("addVertex")) {
                        vertices.incrementAndGet();
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }

    private static void testVanillaSelection(Unsafe unsafe, Runnable drain) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        player.tickCount = 64;
        minecraft.player = player;
        Avatar equipped = new Avatar(player.getUUID());
        equipped.load(model("equipped fixture"));
        try (var original = new LibraryPreviewBinding(player, equipped);
             var preview = new LibraryPreviewSession()) {
            require(preview.selectVanilla() && preview.entity() == player && preview.showsVanillaPlayer(),
                    "vanilla selection uses the actual player and its native skin");
            require(field(LibraryPreviewSession.class, "avatar").get(preview) == null,
                    "vanilla preview owns no empty Avatar placeholder");
            var state = new AvatarRenderState();
            preview.withPlayerContext(() -> {
                require(AvatarManager.getAvatar(player) == null
                                && AvatarManager.getAvatarForPlayer(player.getUUID()) == null,
                        "vanilla scope masks the equipped model for entity and player lookups");
                LibraryPreviewBinding.capture(player, state);
            });
            require(AvatarManager.getAvatar(player) == equipped && AvatarManager.getAvatar(state) == null
                            && AvatarManager.getEntity(state) == player && LibraryPreviewBinding.isVanilla(state),
                    "deferred vanilla frame stays isolated while outside lookups retain equipment");
            var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                    null, "6".repeat(64), OutfitConfig.Format.FIGURA);
            var declaration = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                    OutfitConfig.AnchorRef.authorDefault("unknown"), OutfitConfig.Transform.identity(),
                    java.util.List.of(), Map.of(), Map.of());
            var config = new OutfitConfig(1, 0, null, java.util.List.of(declaration), java.util.List.of(),
                    Map.of(resource.identity(), 1), false);
            CompoundTag source = model("vanilla accessory");
            source.getCompoundOrEmpty("metadata").putString("resource_kind", "ACCESSORY");
            var wrongBase = new OutfitConfig(1, 0, resource, config.instances(), config.virtualAnchors(),
                    config.displayOrdinals(), false);
            try {
                preview.prepareComposition(wrongBase, OutfitLimits.defaults(), player.getUUID(), UUID.randomUUID(),
                        equipped.permissions, (reference, cancelled) -> source);
                throw new AssertionError("custom-base draft published over a vanilla preview");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().equals("PREVIEW_BASE_MISMATCH"),
                        "draft base kind must agree with the prepared native preview");
            }
            require(await(preview.prepareComposition(config, OutfitLimits.defaults(), player.getUUID(), UUID.randomUUID(),
                    equipped.permissions, (reference, cancelled) -> source), drain),
                    "vanilla base accepts independently prepared accessory instances");
            var outfitFrame = new AvatarRenderState();
            preview.withPlayerContext(() -> LibraryPreviewBinding.capture(player, outfitFrame));
            require(LibraryPreviewBinding.composition(outfitFrame) != null,
                    "native vanilla extraction retains its complete accessory generation");
            preview.tick();
            require(player.tickCount == 64, "vanilla composition does not advance the actual entity clock");
            preview.clear();
            require(LibraryPreviewBinding.isVanilla(outfitFrame)
                            && LibraryPreviewBinding.composition(outfitFrame) != null
                            && equipped.accelerationOwner().isCurrent(),
                    "retiring vanilla preview preserves extracted frames and equipped resources");
            LibraryPreviewBinding.finishFrame();
            require(!LibraryPreviewBinding.isVanilla(state) && AvatarManager.getAvatar(player) == equipped,
                    "frame cleanup releases vanilla bindings without destroying outside equipment");
            testWardrobeCosmeticSelection(equipped, drain);
            System.out.println("PASS vanilla composition preview: no placeholder Avatar, scoped equipment masking, async accessory preparation and deferred cleanup; GUI MANUAL/PENDING");
        } finally {
            LibraryPreviewBinding.finishFrame();
            minecraft.player = previousPlayer;
            ((Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null)).remove(player.getUUID());
        }
    }

    private static void testWardrobeCosmeticSelection(Avatar equipped, Runnable drain) throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "cosmetic-selection-");
        Path manifest = root.resolve("cosmetic.json");
        String declaration = "{\"name\":\"星翼\",\"category\":\"Head\",\"cosmetic_anchor\":\"head_anchor\"}";
        Files.writeString(manifest, declaration);
        Files.writeString(root.resolve("script.lua"), "return 1");
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        boolean hadPermissions = packs.containsKey(owner);
        Path loadedPath = LocalAvatarLoader.getLastLoadedPath();
        Path selectedPath = top.aihmc.omnimorph.gui.widgets.lists.AvatarList.selectedEntry;
        var screenType = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        try (var preview = new LibraryPreviewSession()) {
            field(screenType, "preview").set(screen, preview);
            var source = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(root, () -> false);
            var entry = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(root, UUID.randomUUID(), source, "");
            var choose = screenType.getDeclaredMethod("selectCosmetic", entry.getClass());
            choose.setAccessible(true);
            choose.invoke(screen, entry);
            var add = screenType.getDeclaredMethod("addSelectedInstance");
            add.setAccessible(true);
            add.invoke(screen);
            awaitDraftComposition(screen, preview, drain);
            var frame = new AvatarRenderState();
            preview.withPlayerContext(() -> LibraryPreviewBinding.capture(Minecraft.getInstance().player, frame));
            require(LibraryPreviewBinding.composition(frame) != null && preview.outfitBase() != null
                            && preview.outfitBase().nbt.equals(equipped.nbt),
                    "accessory editing uses the enabled model instead of replacing it with vanilla");
            require(AvatarManager.getAvatar(Minecraft.getInstance().player) == equipped
                            && java.util.Objects.equals(LocalAvatarLoader.getLastLoadedPath(), loadedPath)
                            && java.util.Objects.equals(top.aihmc.omnimorph.gui.widgets.lists.AvatarList.selectedEntry, selectedPath)
                            && Files.readString(manifest).equals(declaration),
                    "selection preserves equipment, formal selection and source files");
            var publish = screenType.getDeclaredMethod("enableSelection");
            publish.setAccessible(true);
            publish.invoke(screen);
            require(java.util.Objects.equals(LocalAvatarLoader.getLastLoadedPath(), loadedPath),
                    "legacy Avatar publication cannot equip an accessory even under the All purpose filter");
            preview.clear();
            require(!LibraryPreviewBinding.hasComposition(Minecraft.getInstance().player)
                            && LibraryPreviewBinding.composition(frame) != null
                            && AvatarManager.getAvatar(Minecraft.getInstance().player) == equipped,
                    "selection retirement preserves extracted frames and outside equipment");
            LibraryPreviewBinding.finishFrame();
            var unavailable = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(root,
                    entry.localId(), null, "INVALID_COSMETIC_SOURCE");
            choose.invoke(screen, unavailable);
            require(preview.status().stage() == LibraryPreviewSession.Stage.IDLE
                            && AvatarManager.getAvatarForPlayer(owner) == equipped,
                    "an unavailable candidate does not publish or replace the equipped model");
            System.out.println("PASS wardrobe accessory selection: actual cosmetic source, private async composition, no base load/publication, deferred retirement and unavailable entry; GUI MANUAL/PENDING");
        } finally {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear();
            LibraryPreviewBinding.finishFrame();
            if (!hadPermissions) {
                packs.remove(owner);
            }
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    /** After login restore, opening the outfit tab must show the committed accessory without selecting an entry. */
    private static void testOutfitTabShowsLoadedOutfit(Unsafe unsafe, Runnable drain) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(owner);
        minecraft.player = player;
        @SuppressWarnings("unchecked")
        var users = (Map<UUID, top.aihmc.omnimorph.avatar.UserData>) field(AvatarManager.class, "LOADED_USERS").get(null);
        var previousUser = users.get(owner);
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        boolean hadPermissions = packs.containsKey(owner);
        var limits = OutfitLimits.defaults();
        Path root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "restored-outfit-");
        Files.writeString(root.resolve("cosmetic.json"), "{\"name\":\"restored accessory\",\"cosmetic_anchor\":\"head_anchor\"}");
        Files.writeString(root.resolve("script.lua"), "return 1");
        Avatar base = new Avatar(owner);
        Avatar accessory = null;
        top.aihmc.omnimorph.model.outfit.OutfitComposition composition = null;
        var type = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
        // Only the tab switch itself runs: widget rebuilding is suppressed, and a concrete input type keeps the
        // real focus pass working in the headless fixture.
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null) {
            @Override
            protected void init() {
            }
        };
        var input = field(Minecraft.class, "lastInputType");
        var previousInput = input.get(minecraft);
        input.set(minecraft, net.minecraft.client.InputType.MOUSE);
        try (var preview = new LibraryPreviewSession()) {
            var baseData = model("restored base");
            var baseRoot = new CompoundTag();
            var baseChildren = new net.minecraft.nbt.ListTag();
            var head = new CompoundTag();
            head.putString("name", "Head");
            baseChildren.add(head);
            baseRoot.put("chld", baseChildren);
            baseData.put("models", baseRoot);
            base.load(baseData);
            var baseReference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                    null, null, "7".repeat(64), OutfitConfig.Format.FIGURA);
            base.bindOutfitSource(baseReference);
            var definition = new top.aihmc.omnimorph.model.outfit.FiguraSkeletonAdapter(
                    (top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer) base.renderer, baseReference, null,
                    new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache(), limits).binding().definition();
            var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                    definition.reference(null, definition.uniqueNamed("Head").orElseThrow()), null, null, null);
            var description = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(root, () -> false);
            var entry = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(root, UUID.randomUUID(),
                    description, "");
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), entry.reference(), 1, true, anchor,
                    OutfitConfig.Transform.identity(), java.util.List.of(), Map.of(), Map.of());
            var config = new OutfitConfig(1, 9, baseReference, java.util.List.of(instance), java.util.List.of(),
                    Map.of(entry.reference().identity(), 1), false);
            var context = new top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext(owner, UUID.randomUUID(), owner,
                    instance, limits, top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.OWNER);
            accessory = Avatar.createCosmetic(context, base.permissions, "restored accessory");
            accessory.load(top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.prepare(root, owner,
                    description.contentHash(), () -> false).nbt());
            composition = new top.aihmc.omnimorph.model.outfit.OutfitComposition(config, limits, player,
                    Map.of(instance.instanceId(), accessory), new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache());
            // The state login restore publishes: committed configuration, resident base and OWNER instances.
            var user = new top.aihmc.omnimorph.avatar.UserData(owner);
            field(user.getClass(), "outfit").set(user,
                    new top.aihmc.omnimorph.avatar.UserData.OutfitState(config, base, composition));
            users.put(owner, user);
            field(type, "preview").set(screen, preview);
            field(type, "observedCosmetics").set(screen, java.util.List.of(entry));
            var tabType = field(type, "tab").getType();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object collectionTab = Enum.valueOf((Class) tabType, "COLLECTION");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object outfitTab = Enum.valueOf((Class) tabType, "OUTFIT");
            field(type, "tab").set(screen, collectionTab);
            var switchTab = type.getDeclaredMethod("switchTab", tabType);
            switchTab.setAccessible(true);
            switchTab.invoke(screen, outfitTab);
            var start = (CompletableFuture<?>) field(type, "draftStart").get(screen);
            require(start != null, "opening the outfit tab starts a draft from the restored outfit");
            await(start, drain);
            awaitDraftComposition(screen, preview, drain);
            var draft = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(type, "outfitDraft").get(screen);
            require(draft != null && draft.configuration().equals(config),
                    "the outfit tab draft is the committed restored configuration");
            var tree = preview.outfitHierarchy();
            boolean listed = false;
            for (var node : tree.hierarchy().nodes()) {
                var target = tree.target(node.id());
                if (target != null && instance.instanceId().equals(target.instanceId())) {
                    listed = true;
                    break;
                }
            }
            require(listed && preview.outfitComposition().runtimeInstances().containsKey(instance.instanceId()),
                    "the restored accessory appears in the bone tree and preview without selecting an entry");
            System.out.println("PASS outfit tab opens on the restored outfit: draft, bone tree instance and preview; GUI MANUAL/PENDING");
        } finally {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear();
            LibraryPreviewBinding.finishFrame();
            if (previousUser == null) {
                users.remove(owner);
            } else {
                users.put(owner, previousUser);
            }
            if (composition != null) {
                composition.close();
            }
            if (accessory != null) {
                accessory.clean();
            }
            base.clean();
            minecraft.player = previousPlayer;
            input.set(minecraft, previousInput);
            if (!hadPermissions) {
                packs.remove(owner);
            }
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static void testWardrobeDraftActions(Unsafe unsafe, Runnable drain) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        boolean hadPermissions = packs.containsKey(owner);
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(owner);
        minecraft.player = player;
        Avatar equipped = new Avatar(owner);
        var baseData = model("draft base fixture");
        var baseRoot = new CompoundTag();
        var baseChildren = new net.minecraft.nbt.ListTag();
        var baseHead = new CompoundTag();
        baseHead.putString("name", "Head");
        baseChildren.add(baseHead);
        baseRoot.put("chld", baseChildren);
        baseData.put("models", baseRoot);
        equipped.load(baseData);
        int fixturePermissions = packs.size();
        Path root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "outfit-edit-");
        String declaration = "{\"name\":\"星翼\",\"cosmetic_anchor\":\"head_anchor\","
                + "\"default_transform\":{\"position\":[1,2,3],\"rotation\":[0,90,0],\"scale\":[1,2,1]}}";
        Files.writeString(root.resolve("cosmetic.json"), declaration);
        Files.writeString(root.resolve("script.lua"), "return 1");
        var type = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        try (var original = new LibraryPreviewBinding(player, equipped);
             var preview = new LibraryPreviewSession()) {
            field(type, "preview").set(screen, preview);
            var source = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(root, () -> false);
            var entry = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(root, UUID.randomUUID(), source, "");
            var choose = type.getDeclaredMethod("selectCosmetic", entry.getClass());
            choose.setAccessible(true);
            choose.invoke(screen, entry);
            var selectedDraft = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(type, "outfitDraft").get(screen);
            require(selectedDraft == null || selectedDraft.configuration().instances().isEmpty(),
                    "resource selection creates no outfit instance");
            var add = type.getDeclaredMethod("addSelectedInstance");
            add.setAccessible(true);
            add.invoke(screen);
            @SuppressWarnings("unchecked")
            var starting = (CompletableFuture<top.aihmc.omnimorph.model.outfit.OutfitDraft>) field(type, "draftStart").get(screen);
            var draft = await(starting, drain);
            require(draft.committed().base() != null && draft.committed().instances().isEmpty(),
                    "draft creation captures the equipped custom base instead of silently selecting vanilla");
            awaitDraftComposition(screen, preview, drain);
            require(top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.active(),
                    "editing automatically previews the local player in the world without a toggle");
            var first = draft.configuration().instances().getFirst();
            require(first.transform().equals(source.metadata().defaultTransform()), "instance starts from the author's local transform");
            add.invoke(screen);
            awaitDraftComposition(screen, preview, drain);
            var second = draft.configuration().instances().getLast();
            var residentDraft = preview.outfitComposition();
            choose.invoke(screen, entry);
            require(field(type, "draftPreviewActive").getBoolean(screen)
                            && preview.outfitComposition() == residentDraft,
                    "selecting an accessory must not replace the active outfit preview or its loaded bone tree");
            // A draft rebuilt from the restored outfit has the local reference but no inventory entry yet.
            @SuppressWarnings("unchecked")
            var boundResources = (Map<OutfitConfig.ResourceRef, top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry>)
                    field(type, "draftResources").get(screen);
            boundResources.clear();
            var nameOf = type.getDeclaredMethod("instanceName", OutfitConfig.Instance.class);
            nameOf.setAccessible(true);
            String unavailable = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.unavailable").getString();
            require(((net.minecraft.network.chat.Component) nameOf.invoke(screen, first)).getString().startsWith(unavailable),
                    "fixture reproduces a restored local instance without its inventory entry");
            field(type, "observedCosmetics").set(screen, java.util.List.of(entry));
            var bind = type.getDeclaredMethod("bindLocalDraftResources");
            bind.setAccessible(true);
            require((boolean) bind.invoke(screen) && boundResources.get(first.resource()) == entry
                            && !((net.minecraft.network.chat.Component) nameOf.invoke(screen, first)).getString().startsWith(unavailable),
                    "restored local instances rebind their exact inventory source for names and preview reads");
            require(!(boolean) bind.invoke(screen), "rebinding is idempotent once every local source is resolved");
            testLocalDraftPreview(unsafe, drain, screen, preview, player);
            var mountMenu = new top.aihmc.omnimorph.gui.widgets.ContextMenu();
            var quickMenu = type.getDeclaredMethod("addQuickMountMenu",
                    top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry.class,
                    top.aihmc.omnimorph.gui.widgets.ContextMenu.class);
            quickMenu.setAccessible(true);
            long preparedGeneration = field(type, "draftPreviewGeneration").getLong(screen);
            var previousFont = minecraft.font;
            field(Minecraft.class, "font").set(minecraft, new net.minecraft.client.gui.Font(null) {
                @Override
                public int width(net.minecraft.network.chat.FormattedText text) {
                    return text.getString().length() * 6;
                }
            });
            try {
                quickMenu.invoke(screen, new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(
                        null, null, entry), mountMenu);
            } finally {
                field(Minecraft.class, "font").set(minecraft, previousFont);
            }
            require(!mountMenu.getEntries().isEmpty() && preview.outfitComposition() == residentDraft
                            && field(type, "draftPreviewGeneration").getLong(screen) == preparedGeneration,
                    "quick mount menu reuses the loaded hierarchy without preparing or requesting the base again");
            var tree = preview.outfitHierarchy();
            require(tree.targets().values().stream().filter(target -> target.kind() == OutfitHierarchy.Kind.INSTANCE).count() == 2
                            && tree.targets().values().stream().anyMatch(target -> first.instanceId().equals(target.instanceId()))
                            && tree.targets().values().stream().anyMatch(target -> second.instanceId().equals(target.instanceId())),
                    "Preparing the actual draft publishes both instance-owned entries in the preview hierarchy");
            var sync = type.getDeclaredMethod("syncHierarchy");
            sync.setAccessible(true);
            sync.invoke(screen);
            var treeState = (BoneTreeState) field(type, "bones").get(screen);
            require(treeState.selectedTarget() != null && treeState.selectedTarget().kind() == OutfitHierarchy.Kind.STANDARD
                            && treeState.selectedTarget().standard().equals(source.metadata().defaultAnchor().name()),
                    "newly added instances focus their authored attachment after the draft snapshot is published");
            treeState.view(BoneTreeState.View.ALL);
            int firstHeader = tree.targets().entrySet().stream().filter(node -> node.getValue().kind() == OutfitHierarchy.Kind.INSTANCE
                    && first.instanceId().equals(node.getValue().instanceId())).findFirst().orElseThrow().getKey();
            treeState.select(firstHeader);
            require(treeState.selectedTarget().instanceId().equals(first.instanceId()) && treeState.selectedPart() == null,
                    "Production hierarchy synchronization retains typed identity and filters non-bone headers");
            var chooseTarget = type.getDeclaredMethod("selectTreeTarget", OutfitHierarchy.Target.class);
            chooseTarget.setAccessible(true);
            chooseTarget.invoke(screen, treeState.selectedTarget());
            require(draft.selectedInstance().equals(first.instanceId()), "Tree selection changes only the intended draft instance");
            require(draft.configuration().instances().size() == 2 && first.displayOrdinal() == 1 && second.displayOrdinal() == 2
                            && !first.instanceId().equals(second.instanceId()),
                    "the production Add action creates independent numbered copies of the same source");
            testInstanceTransformFields(unsafe, screen, draft, first.instanceId(), second.instanceId(), drain);
            awaitDraftComposition(screen, preview, drain);
            var remove = type.getDeclaredMethod("removeDraftInstance", UUID.class);
            remove.setAccessible(true);
            remove.invoke(screen, first.instanceId());
            awaitDraftComposition(screen, preview, drain);
            add.invoke(screen);
            awaitDraftComposition(screen, preview, drain);
            require(draft.configuration().instances().getFirst().displayOrdinal() == 2
                            && draft.configuration().instances().getLast().displayOrdinal() == 3,
                    "removing a copy never renumbers its sibling or reuses a suffix");
            var reset = type.getDeclaredMethod("resetOutfitDraft");
            reset.setAccessible(true);
            reset.invoke(screen);
            awaitDraftComposition(screen, preview, drain);
            require(draft.configuration().base() == null && draft.configuration().instances().isEmpty()
                            && AvatarManager.getAvatarForPlayer(owner) == equipped,
                    "whole-draft reset selects vanilla only in the private editor");
            var cancel = type.getDeclaredMethod("cancelOutfitDraft");
            cancel.setAccessible(true);
            cancel.invoke(screen);
            require(!draft.dirty() && draft.configuration().equals(draft.committed())
                            && !field(type, "draftPreviewActive").getBoolean(screen)
                            && preview.status().stage() == LibraryPreviewSession.Stage.IDLE
                            && AvatarManager.getAvatarForPlayer(owner) == equipped
                            && Files.readString(root.resolve("cosmetic.json")).equals(declaration),
                    "Cancel restores the baseline and actual equipment without publishing or changing source files");
            require(packs.size() == fixturePermissions, "draft previews add no player permission entries beyond the equipped fixture");
            field(type, "draftAccount").set(screen, UUID.randomUUID());
            var discard = type.getDeclaredMethod("discardOldAccountDraft");
            discard.setAccessible(true);
            require(Boolean.TRUE.equals(discard.invoke(screen)) && field(type, "outfitDraft").get(screen) == null
                            && ((Map<?, ?>) field(type, "draftResources").get(screen)).isEmpty()
                            && preview.status().stage() == LibraryPreviewSession.Stage.CLOSED
                            && AvatarManager.getAvatarForPlayer(owner) == equipped,
                    "account mismatch discards private editor state and its preview without touching actual equipment");
            testStaleDraftStart(entry, drain);
            testSelectedOutfitBase(root, entry, equipped, drain);
            System.out.println("PASS wardrobe draft actions: actual base snapshot/source loading, independent copies, author transform, stable suffixes, remove/reset/cancel and unchanged equipment; GUI MANUAL/PENDING");
        } finally {
            LibraryPreviewBinding.finishFrame();
            minecraft.player = previousPlayer;
            if (!hadPermissions) {
                packs.remove(owner);
            }
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void testSelectedOutfitBase(Path root,
            top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry accessory,
            Avatar equipped, Runnable drain) throws Exception {
        top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear();
        Path selectedBase = Files.createTempDirectory(root.getParent(), "selected-avatar-");
        Files.writeString(selectedBase.resolve("avatar.json"), "{\"name\":\"Selected outfit base\"}");
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        var type = screen.getClass();
        try (var preview = new LibraryPreviewSession()) {
            field(type, "preview").set(screen, preview);
            var chooseBase = type.getDeclaredMethod("selectLocal",
                    top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath.class);
            chooseBase.setAccessible(true);
            chooseBase.invoke(screen, new top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath(selectedBase, root));
            var chooseAccessory = type.getDeclaredMethod("selectCosmetic", accessory.getClass());
            chooseAccessory.setAccessible(true);
            chooseAccessory.invoke(screen, accessory);
            var add = type.getDeclaredMethod("addSelectedInstance");
            add.setAccessible(true);
            add.invoke(screen);
            await((CompletableFuture<?>) field(type, "changingDraftBase").get(screen), drain);
            awaitDraftComposition(screen, preview, drain);
            var draft = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(type, "outfitDraft").get(screen);
            var reference = new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(FiguraMod.getFiguraDirectory())
                    .describe(selectedBase, () -> false);
            require(draft.configuration().base().equals(reference)
                            && draft.configuration().instances().size() == 1
                            && AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID()) == equipped,
                    "selecting a new avatar then immediately choosing an accessory edits the selected base locally");
            var hierarchy = preview.outfitHierarchy();
            long revision = preview.hierarchyRevision();
            chooseAccessory.invoke(screen, accessory);
            require(preview.outfitHierarchy() == hierarchy && preview.hierarchyRevision() == revision
                            && top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.active(),
                    "switching accessory candidates retains the current base and instance bone tree without rebuilding it");
        } finally {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear();
            Files.deleteIfExists(selectedBase.resolve("avatar.json"));
            Files.deleteIfExists(selectedBase);
        }
    }

    private static void testLocalDraftPreview(Unsafe unsafe, Runnable drain, Object editor,
                                             LibraryPreviewSession preview, CurrentPreviewPlayer player) throws Exception {
        var local = top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.class;
        var entities = (Map<?, ?>) field(LibraryPreviewBinding.class, "ENTITIES").get(null);
        Object original = entities.remove(player);
        var formal = AvatarManager.getLoadedOutfit(player.getUUID());
        try {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.show(editor, preview);
            var worldFrame = new AvatarRenderState();
            top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.capture(player, worldFrame);
            require(AvatarManager.getAvatar(worldFrame) == preview.outfitState().base()
                            && top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.composition(worldFrame) != null,
                    "local draft replaces only the current player's extracted render frame");
            var other = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
            other.setUUID(UUID.randomUUID());
            require(top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.state(other) == null
                            && AvatarManager.getLoadedOutfit(player.getUUID()) == formal,
                    "world preview does not alter other players or committed/synchronized equipment");
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear(editor);
            require(top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.state(player) == null
                            && preview.status().stage() == LibraryPreviewSession.Stage.READY,
                    "disabling local world preview restores equipment without closing the editor");
            top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
            try (var owned = new LibraryPreviewSession()) {
                require(owned.selectVanilla(), "world preview lifetime fixture starts");
                var empty = new OutfitConfig(1, 0, null, java.util.List.of(), java.util.List.of(), Map.of(), false);
                require(await(owned.prepareComposition(empty, OutfitLimits.defaults(), player.getUUID(), UUID.randomUUID(),
                        preview.outfitState().base().permissions, (reference, cancelled) -> {
                            throw new AssertionError("empty draft must not load sources");
                        }), drain), "world preview lifetime fixture is prepared");
                top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.show(editor, owned);
                top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.detach(editor);
                var retained = new AvatarRenderState();
                top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.capture(player, retained);
                top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear(editor);
                require(owned.status().stage() == LibraryPreviewSession.Stage.READY,
                        "closing a world preview preserves already extracted frames");
                top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
                require(owned.status().stage() == LibraryPreviewSession.Stage.CLOSED,
                        "the detached session closes after its final world frame releases");
            }
        } finally {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear(editor);
            top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
            if (original != null) {
                @SuppressWarnings("unchecked")
                var writable = (Map<Object, Object>) entities;
                writable.put(player, original);
            }
        }
    }

    private static void testWardrobeCloudCosmetic(Unsafe unsafe, Runnable drain, Path encrypted) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        boolean hadPermissions = packs.containsKey(owner);
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(owner);
        minecraft.player = player;
        var baseReference = new top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef(
                top.aihmc.omnimorph.model.outfit.OutfitConfig.Source.CLOUD, null, "https://source.example",
                UUID.randomUUID(), 7L, "b".repeat(64), top.aihmc.omnimorph.model.outfit.OutfitConfig.Format.FIGURA);
        Path root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "cloud-outfit-edit-");
        try {
            for (boolean ysm : new boolean[]{false, true}) {
                Avatar equipped = new Avatar(owner);
                equipped.load(model("cloud draft base"));
                equipped.bindOutfitSource(baseReference);
                Path source = Files.createDirectories(root.resolve(ysm ? "YSM" : "Figura"));
                Files.writeString(source.resolve("cosmetic.json"), CloudCosmeticSourceSmokeTest.MANIFEST);
                Files.writeString(source.resolve("script.lua"), "return 1");
                if (ysm) {
                    Files.copy(encrypted, source.resolve("original.ysm"));
                }
                var description = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(source, () -> false);
                CompoundTag input = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.prepare(source, owner,
                        description.contentHash(), () -> false).nbt();
                Path cache = Files.createDirectories(root.resolve(ysm ? "ysm-cache" : "figura-cache"));
                var live = new AtomicBoolean(true);
                try (var server = new CloudCosmeticSourceSmokeTest.Server(input, CloudCosmeticSourceSmokeTest.MANIFEST);
                     var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                             request -> request.header("Authorization", "Bearer synthetic-player"));
                     var client = new LibraryClient(access, new LibrarySession(server.origin(), owner, 1, live::get), minecraft::execute);
                     var collection = new LibraryCollectionService(client, cache);
                     var original = new LibraryPreviewBinding(player, equipped);
                     var preview = new LibraryPreviewSession()) {
                    var capabilities = client.capabilities();
                    await(capabilities.future(), drain);
                    var entry = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(
                            CloudCosmeticSourceSmokeTest.Server.MODEL, 1, "UPLOAD", true, server.model());
                    field(collection.getClass(), "entries").set(collection, java.util.List.of(entry));
                    field(collection.getClass(), "authoritative").setBoolean(collection, true);
                    var type = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
                    var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
                    field(type, "client").set(screen, client);
                    field(type, "collection").set(screen, collection);
                    field(type, "transfers").set(screen, new LibraryTransferService(client, cache));
                    field(type, "outfitCapabilitiesRequest").set(screen, capabilities);
                    field(type, "preview").set(screen, preview);
                    // Acquiring a resource is separate from private outfit editing.
                    var transfers = (LibraryTransferService) field(type, "transfers").get(screen);
                    require(await(new CloudCosmeticSource(client, transfers, minecraft::execute)
                            .selectCached(entry.model()).future().handle((value, failure) -> failure), drain) != null
                                    && server.details.get() == 0 && server.files.get() == 0,
                            "an uncached edit reports a cache miss without requesting the cloud");
                    var acquired = transfers.preview(entry.modelId());
                    await(acquired.request().future(), drain);
                    int acquiredDetails = server.details.get();
                    // Collection summaries omit byte_length; acquired sources must still serve local edits.
                    var detail = server.model();
                    var summary = new top.aihmc.omnimorph.backend2.library.LibraryModels.Model(detail.modelId(),
                            detail.revision(), detail.policyRevision(), detail.displayName(), detail.internalName(),
                            detail.authors(), detail.uploader(), detail.modelType(), detail.imageRevision(),
                            detail.imageAvailable(), detail.downloads(), detail.favorites(), detail.favorited(),
                            detail.visibility(), detail.requiresAuthorization(), detail.allowedActions(),
                            detail.description(), detail.categoryId(), detail.tags(), detail.moderationStatus(),
                            0L, 0L, detail.metadataSource(), detail.metadataValidation(), detail.resourceKind(),
                            detail.accessoryMetadata());
                    require(await(new CloudCosmeticSource(client, transfers, minecraft::execute)
                                    .selectCached(summary).future().handle((value, failure) -> failure), drain) == null
                                    && server.details.get() == acquiredDetails && server.files.get() == 1,
                            "a collection summary without byte length reuses the acquired source offline");
                    byte[] formalBytes = new byte[(int) detail.byteLength()];
                    java.util.Arrays.fill(formalBytes, (byte) 7);
                    var formalCache = new LibraryResourceCache(cache, client.session());
                    Path encryptedFormal = Files.createTempFile(cache, "formal-", ".part");
                    new top.aihmc.omnimorph.model.cache.ModelCacheCodec(cache).write(encryptedFormal, formalBytes);
                    UUID formalTransferId = UUID.randomUUID();
                    var formalTransfer = new top.aihmc.omnimorph.backend2.library.LibraryModels.Transfer(formalTransferId,
                            "download", detail.revision(), detail.policyRevision(),
                            "/api/library/v1/transfers/" + formalTransferId,
                            java.time.Instant.now().getEpochSecond() + 600);
                    formalCache.publish(detail, formalCache.stage(detail, encryptedFormal, formalBytes,
                            top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(formalBytes), formalTransfer));
                    require(java.util.Arrays.equals(formalCache.read(summary), formalBytes)
                                    && formalCache.present(summary),
                            "a formally cached resource is readable from a collection summary without byte length");
                    Path mismatchedFormal = Files.createTempFile(cache, "formal-", ".part");
                    new top.aihmc.omnimorph.model.cache.ModelCacheCodec(cache).write(mismatchedFormal, formalBytes);
                    formalCache.publish(detail, formalCache.stage(detail, mismatchedFormal, formalBytes,
                            "0".repeat(64), formalTransfer));
                    require(formalCache.read(summary) == null,
                            "a summary without byte length still rejects a package whose hash differs from its index");
                    var select = type.getDeclaredMethod("selectCloud", entry.getClass());
                    select.setAccessible(true);
                    select.invoke(screen, entry);
                    var selection = (CloudCosmeticSource.Operation) field(type, "cloudCosmeticSelection").get(screen);
                    var authorized = await(selection.future(), drain);
                    long selectionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    while (!hasPrivateComposition(preview) && System.nanoTime() < selectionDeadline) {
                        drain.run();
                        Thread.sleep(1);
                    }
                    var selectedFrame = new AvatarRenderState();
                    preview.withPlayerContext(() -> LibraryPreviewBinding.capture(player, selectedFrame));
                    require(field(type, "outfitDraft").get(screen) != null && LibraryPreviewBinding.composition(selectedFrame) != null
                                    && field(LibraryPreviewSession.class, "avatar").get(preview) != null
                                    && server.details.get() == acquiredDetails,
                            "cached accessory selection retains the equipped base and only prepares a local draft: "
                                    + preview.status() + ", " + field(type, "message").get(screen));
                    var add = type.getDeclaredMethod("addSelectedInstance");
                    add.setAccessible(true);
                    add.invoke(screen);
                    @SuppressWarnings("unchecked")
                    var starting = (CompletableFuture<top.aihmc.omnimorph.model.outfit.OutfitDraft>) field(type, "draftStart").get(screen);
                    var draft = await(starting, drain);
                    require(baseReference.equals(draft.configuration().base()),
                            "wardrobe draft captures the loaded Avatar's exact source rather than creating a random local identity");
                    awaitDraftComposition(screen, preview, drain);
                    int detailsAfterFirstAdd = server.details.get();
                    add.invoke(screen);
                    awaitDraftComposition(screen, preview, drain);
                    var reference = authorized.descriptor().reference();
                    require(draft.configuration().instances().size() == 2
                                    && draft.configuration().instances().stream().allMatch(instance -> instance.resource().equals(reference))
                                    && !draft.configuration().instances().getFirst().instanceId().equals(draft.configuration().instances().getLast().instanceId())
                                    && server.files.get() == 1 && detailsAfterFirstAdd > 0
                                    && server.details.get() == detailsAfterFirstAdd,
                            "cloud draft edits preserve authorized source identity and reuse the loaded body and bone data");
                    Object binding = field(LibraryPreviewSession.class, "binding").get(preview);
                    Object composition = field(LibraryPreviewBinding.class, "composition").get(binding);
                    @SuppressWarnings("unchecked")
                    var instances = (Map<UUID, Avatar>) field(composition.getClass(), "instances").get(composition);
                    require(instances.size() == 2 && instances.values().stream().allMatch(avatar -> avatar.isYsmNative() == ysm
                                    && avatar.cosmeticContext.resource().equals(reference)),
                            "authorized cloud packages reach the correct native Figura/encrypted YSM instance runtime");
                    server.revoked.set(true);
                    int beforeEdit = server.details.get();
                    var refresh = type.getDeclaredMethod("previewOutfitDraft");
                    refresh.setAccessible(true);
                    refresh.invoke(screen);
                    awaitDraftComposition(screen, preview, drain);
                    require(server.details.get() == beforeEdit && server.files.get() == 1
                                    && AvatarManager.getAvatarForPlayer(owner) == equipped,
                            "private edits do not seek cloud admission; formal Apply performs the fresh permission check");
                    var reload = type.getDeclaredMethod("reloadOutfitDraft");
                    reload.setAccessible(true);
                    reload.invoke(screen);
                    await((CompletableFuture<?>) field(type, "draftStart").get(screen), drain);
                    awaitDraftComposition(screen, preview, drain);
                    var reloaded = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(type, "outfitDraft").get(screen);
                    require(reloaded.configuration().base().equals(baseReference)
                                    && reloaded.configuration().instances().isEmpty()
                                    && server.details.get() == beforeEdit && server.files.get() == 1,
                            "refresh before submission restores the enabled local outfit without a cloud read");
                    // Clicking an uncached cloud entry acquires it once through the collection instead of only failing.
                    var cacheThenRetry = type.getDeclaredMethod("cacheThenRetry", Throwable.class, UUID.class,
                            java.util.function.BooleanSupplier.class, Runnable.class);
                    cacheThenRetry.setAccessible(true);
                    var miss = new java.util.concurrent.CompletionException(new java.io.IOException("LOCAL_PREVIEW_CACHE_MISS"));
                    int detailsBeforeAcquire = server.details.get();
                    require((boolean) cacheThenRetry.invoke(screen, miss, entry.modelId(),
                                    (java.util.function.BooleanSupplier) () -> false, (Runnable) () -> { }),
                            "an uncached cloud selection starts one automatic acquisition");
                    long acquireDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (server.details.get() == detailsBeforeAcquire && System.nanoTime() < acquireDeadline) {
                        drain.run();
                        Thread.sleep(1);
                    }
                    require(server.details.get() > detailsBeforeAcquire,
                            "automatic acquisition requests the authorized cloud resource");
                    require(!(boolean) cacheThenRetry.invoke(screen, miss, entry.modelId(),
                                    (java.util.function.BooleanSupplier) () -> false, (Runnable) () -> { }),
                            "a repeated miss after one acquisition is reported instead of looping");
                    require(!(boolean) cacheThenRetry.invoke(screen, new java.io.IOException("OTHER"), UUID.randomUUID(),
                                    (java.util.function.BooleanSupplier) () -> false, (Runnable) () -> { }),
                            "only cache misses trigger acquisition");
                }
            }
            System.out.println("PASS wardrobe cloud accessories: actual HTTP/portable Figura and encrypted YSM, scoped selection/Add, fixed references, shared cached body, revocation and stale selection cleanup; GUI MANUAL/PENDING");
        } finally {
            LibraryPreviewBinding.finishFrame();
            minecraft.player = previousPlayer;
            if (!hadPermissions) {
                packs.remove(owner);
            }
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void testLocalPresetScreen(Unsafe unsafe, Runnable drain, LibraryClient client) throws Exception {
        var minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var fontField = field(Minecraft.class, "font");
        var previousFont = fontField.get(minecraft);
        // Native routing needs deterministic metrics; this does not validate real fonts or rendered text.
        var font = new net.minecraft.client.gui.Font(null) {
            private final net.minecraft.client.StringSplitter splitter =
                    new net.minecraft.client.StringSplitter((codepoint, style) -> 6f);

            @Override
            public net.minecraft.client.StringSplitter getSplitter() {
                return splitter;
            }

            @Override
            public int width(String text) {
                return text.codePointCount(0, text.length()) * 6;
            }

            @Override
            public int width(net.minecraft.network.chat.FormattedText text) {
                return width(text.getString());
            }

            @Override
            public int width(net.minecraft.util.FormattedCharSequence text) {
                int[] count = {0};
                text.accept((index, style, codepoint) -> {
                    count[0]++;
                    return true;
                });
                return count[0] * 6;
            }

            @Override
            public String plainSubstrByWidth(String text, int width) {
                return splitter.plainHeadByWidth(text, width, net.minecraft.network.chat.Style.EMPTY);
            }
        };
        fontField.set(minecraft, font);
        var window = field(Minecraft.class, "window");
        var previousWindow = window.get(minecraft);
        var viewport = (com.mojang.blaze3d.platform.Window) unsafe.allocateInstance(com.mojang.blaze3d.platform.Window.class);
        field(viewport.getClass(), "guiScaledWidth").setInt(viewport, 640);
        field(viewport.getClass(), "guiScaledHeight").setInt(viewport, 360);
        window.set(minecraft, viewport);
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(owner);
        minecraft.player = player;
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        boolean hadPermissions = packs.containsKey(owner);
        Path data = FiguraMod.getFiguraDirectory();
        Path root = Files.createTempDirectory(data, "preset-ui-");
        Path accessory = Files.createDirectories(data.resolve("cosmetics/preset-ui-" + UUID.randomUUID()));
        var presetFiles = new java.util.HashSet<UUID>();
        var wardrobe = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        var type = wardrobe.getClass();
        Avatar equipped = new Avatar(owner);
        try (var screens = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingScreens();
             var parentPreview = new LibraryPreviewSession()) {
            testAccessoryUploadScreen(client, player, drain);
            testOutfitUploadScreen(client, player, drain);
            Files.writeString(root.resolve("avatar.json"), "{\"name\":\"Original preset base\"}");
            var base = new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(data).describe(root, () -> false);
            equipped.load(new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(data).prepare(base, owner, () -> false));
            equipped.bindOutfitSource(base);
            Files.writeString(accessory.resolve("cosmetic.json"), CloudCosmeticSourceSmokeTest.MANIFEST);
            Files.writeString(accessory.resolve("script.lua"), "return 1");
            top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry entry;
            try (var inventory = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory(data)) {
                entry = inventory.scan(() -> false).stream().filter(value -> value.path().equals(accessory)).findFirst().orElseThrow();
            }
            field(type, "preview").set(wardrobe, parentPreview);
            wardrobe.width = 640;
            wardrobe.height = 360;
            var open = type.getDeclaredMethod("openOutfitPresets");
            open.setAccessible(true);
            var store = new top.aihmc.omnimorph.model.outfit.LocalOutfitPresets(data);
            try (var original = new LibraryPreviewBinding(player, equipped)) {
                minecraft.screen = wardrobe;
                open.invoke(wardrobe);
                awaitCondition(() -> minecraft.screen instanceof top.aihmc.omnimorph.gui.screens.OutfitPresetsScreen, drain);
                var screen = (top.aihmc.omnimorph.gui.screens.OutfitPresetsScreen) minecraft.screen;
                var screenType = screen.getClass();
                screen.width = 640;
                screen.height = 360;
                var init = screenType.getDeclaredMethod("init");
                init.setAccessible(true);
                init.invoke(screen);
                awaitCondition(() -> !field(screenType, "busy").getBoolean(screen), drain);
                var draft = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(type, "outfitDraft").get(wardrobe);
                long generation = draft.generation();
                require(!field(type, "removed").getBoolean(wardrobe) && field(type, "outfitPresetsOpen").getBoolean(wardrobe)
                                && draft.configuration().base().equals(base),
                        "opening the actual preset action retains the wardrobe draft and its real base provenance");
                var name = (top.aihmc.omnimorph.gui.widgets.TextField) field(screenType, "nameField").get(screen);
                name.getField().setValue("中文 本地预设");
                var save = (top.aihmc.omnimorph.gui.widgets.Button) field(screenType, "saveNew").get(screen);
                require(save.isActive(), "resolved base and name enable local Save");
                save.onPress(null);
                awaitCondition(() -> field(screenType, "previewReady").getBoolean(screen), drain);
                var saved = (top.aihmc.omnimorph.model.outfit.LocalOutfitPresets.Preset) field(screenType, "selected").get(screen);
                presetFiles.add(saved.id());
                require(store.read(saved.id(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults()).configuration()
                                .equals(draft.configuration()) && draft.generation() == generation
                                && AvatarManager.getAvatarForPlayer(owner) == equipped,
                        "native Save persists the current draft, reloads its private preview and never changes actual equipment");
                var update = (top.aihmc.omnimorph.gui.widgets.Button) field(screenType, "update").get(screen);
                name.getField().setValue("更新名称");
                update.onPress(null);
                awaitCondition(() -> field(screenType, "previewReady").getBoolean(screen), drain);
                var updated = (top.aihmc.omnimorph.model.outfit.LocalOutfitPresets.Preset) field(screenType, "selected").get(screen);
                require(updated.revision() == 2 && updated.name().equals("更新名称"), "native Update uses the selected preset revision");
                store.save(updated.id(), 2, "External update", updated.configuration(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults());
                update.onPress(null);
                awaitCondition(() -> !field(screenType, "busy").getBoolean(screen), drain);
                require(field(screenType, "message").get(screen).toString().contains("OUTFIT_PRESET_REVISION_CONFLICT")
                                && store.read(updated.id(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults()).name().equals("External update"),
                        "native Update exposes a conflict and preserves the newer file: " + field(screenType, "message").get(screen));
                var seed = new top.aihmc.omnimorph.model.outfit.OutfitDraft(new top.aihmc.omnimorph.model.outfit.OutfitConfig(
                        1, 0, null, java.util.List.of(), java.util.List.of(), Map.of(), false),
                        top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults());
                UUID first = seed.add(entry.reference(), entry.source().metadata().defaultAnchor(), entry.source().metadata().defaultTransform());
                UUID second = seed.add(entry.reference(), entry.source().metadata().defaultAnchor(), entry.source().metadata().defaultTransform());
                seed.setControls(first, Map.of("amplitude", .25d));
                seed.setControls(second, Map.of("amplitude", .75d));
                UUID copiesId = UUID.randomUUID();
                presetFiles.add(copiesId);
                store.save(copiesId, 0, "双副本预设", seed.configuration(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults());
                ((top.aihmc.omnimorph.gui.widgets.Button) field(screenType, "refresh").get(screen)).onPress(null);
                awaitCondition(() -> !field(screenType, "busy").getBoolean(screen), drain);
                var selector = (top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown) field(screenType, "selector").get(screen);
                selector.onPress(null);
                var menu = (top.aihmc.omnimorph.gui.widgets.ContextMenu) top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
                var presetChoice = (top.aihmc.omnimorph.gui.widgets.Button) menu.getEntries().stream()
                        .filter(button -> button.getMessage().getString().equals("双副本预设")).findFirst().orElseThrow();
                presetChoice.onPress(null);
                awaitCondition(() -> field(screenType, "previewReady").getBoolean(screen), drain);
                var privatePreview = (LibraryPreviewSession) field(screenType, "preview").get(screen);
                require(privatePreview.outfitHierarchy().targets().values().stream()
                                .filter(target -> target.kind() == OutfitHierarchy.Kind.INSTANCE).count() == 2
                                && draft.generation() == generation && AvatarManager.getAvatarForPlayer(owner) == equipped,
                        "selecting a preset through the real dropdown prepares both copies without importing or equipping them");
                var importButton = (top.aihmc.omnimorph.gui.widgets.Button) field(screenType, "importPreset").get(screen);
                require(importButton.isActive(), "Import becomes available only after complete native preparation");
                importButton.onPress(null);
                awaitDraftComposition(wardrobe, parentPreview, drain);
                require(minecraft.screen == wardrobe && draft.configuration().base() == null
                                && draft.configuration().instances().size() == 2
                                && !draft.configuration().instances().getFirst().instanceId().equals(first)
                                && !draft.configuration().instances().getLast().instanceId().equals(second)
                                && draft.configuration().instances().getFirst().controls().equals(seed.instance(first).controls())
                                && AvatarManager.getAvatarForPlayer(owner) == equipped,
                        "Import edits only the retained draft, rewrites instance IDs and preserves each copy's control values");
                long importedGeneration = draft.generation();
                save.onPress(null);
                importButton.onPress(null);
                require(draft.generation() == importedGeneration, "closed preset controls cannot save or import into the parent");
                var cancel = type.getDeclaredMethod("cancelOutfitDraft");
                cancel.setAccessible(true);
                cancel.invoke(wardrobe);
                require(draft.configuration().equals(draft.committed())
                                && field(type, "draftBase").get(wardrobe) == field(type, "committedDraftBase").get(wardrobe)
                                && equipped.outfitSource().equals(base),
                        "cancelling an imported draft restores the original base snapshot and provenance");
                field(type, "draftBasePersistent").setBoolean(wardrobe, false);
                open.invoke(wardrobe);
                var blocked = (top.aihmc.omnimorph.gui.screens.OutfitPresetsScreen) minecraft.screen;
                blocked.width = 640;
                blocked.height = 360;
                init.invoke(blocked);
                awaitCondition(() -> !field(screenType, "busy").getBoolean(blocked), drain);
                ((top.aihmc.omnimorph.gui.widgets.TextField) field(screenType, "nameField").get(blocked)).getField().setValue("Unknown source");
                var blockedSave = (top.aihmc.omnimorph.gui.widgets.Button) field(screenType, "saveNew").get(blocked);
                require(!blockedSave.isActive(), "an unresolved captured base cannot be saved as a reloadable preset");
                blockedSave.onPress(null);
                var missing = new top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef(
                        top.aihmc.omnimorph.model.outfit.OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null, null,
                        "f".repeat(64), top.aihmc.omnimorph.model.outfit.OutfitConfig.Format.FIGURA);
                var broken = new top.aihmc.omnimorph.model.outfit.OutfitDraft(seed.committed(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults());
                broken.add(missing, entry.source().metadata().defaultAnchor(), entry.source().metadata().defaultTransform());
                broken.add(missing, entry.source().metadata().defaultAnchor(), entry.source().metadata().defaultTransform());
                UUID brokenId = UUID.randomUUID();
                presetFiles.add(brokenId);
                store.save(brokenId, 0, "Missing copies", broken.configuration(), top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults());
                var load = screenType.getDeclaredMethod("load", UUID.class);
                load.setAccessible(true);
                load.invoke(blocked, brokenId);
                awaitCondition(() -> !field(screenType, "busy").getBoolean(blocked), drain);
                var issues = (top.aihmc.omnimorph.gui.widgets.library.LibraryRowList) field(screenType, "issues").get(blocked);
                require(((java.util.List<?>) field(issues.getClass(), "rows").get(issues)).size() == 2
                                && !((top.aihmc.omnimorph.gui.widgets.Button) field(screenType, "importPreset").get(blocked)).isActive()
                                && draft.configuration().equals(draft.committed()) && store.read(brokenId,
                                top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults()).configuration().equals(broken.configuration()),
                        "the actual failure list identifies both missing copies and preserves the original preset/draft");
                var entered = new java.util.concurrent.CountDownLatch(1);
                var release = new java.util.concurrent.CountDownLatch(1);
                var gate = top.aihmc.omnimorph.avatar.AvatarLoadExecutor.submit(() -> {
                    entered.countDown();
                    try {
                        require(release.await(10, TimeUnit.SECONDS), "preset worker gate releases promptly");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new java.util.concurrent.CompletionException(interrupted);
                    }
                    return true;
                });
                try {
                    require(entered.await(5, TimeUnit.SECONDS), "preset screen reaches the bounded worker gate");
                    load.invoke(blocked, copiesId);
                    blocked.onClose();
                    require(minecraft.screen == wardrobe && field(screenType, "closed").getBoolean(blocked),
                            "closing never waits behind queued preset reads");
                } finally {
                    release.countDown();
                }
                await(gate, drain);
                await((CompletableFuture<?>) field(screenType, "work").get(blocked), drain);
                require(draft.configuration().equals(draft.committed()), "late preset callbacks cannot import after the modal closes");
                require(parentPreview.status().stage() != LibraryPreviewSession.Stage.CLOSED
                                && AvatarManager.getAvatarForPlayer(owner) == equipped,
                        "closing preset previews preserves the parent session and real equipment");
                field(type, "draftBasePersistent").setBoolean(wardrobe, true);
                testCloudPersonalPresetControls(wardrobe, draft, data, equipped, parentPreview, drain);
            }
            System.out.println("PASS local preset screen: native entry/name/save/update/conflict/dropdown/private composition/import/cancel, source guards and visible per-copy failures; recording screen dispatch, real GUI/GPU MANUAL/PENDING");
        } finally {
            field(type, "outfitPresetsOpen").setBoolean(wardrobe, false);
            wardrobe.removed();
            LibraryPreviewBinding.finishFrame();
            minecraft.player = previousPlayer;
            fontField.set(minecraft, previousFont);
            window.set(minecraft, previousWindow);
            if (!hadPermissions) {
                packs.remove(owner);
            }
            for (UUID id : presetFiles) {
                Files.deleteIfExists(data.resolve("outfit-presets").resolve(id + ".json"));
            }
            for (Path directory : java.util.List.of(root, accessory)) {
                try (var files = Files.walk(directory)) {
                    for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface PresetCondition {
        boolean ready() throws Exception;
    }

    private static void testCloudPersonalPresetControls(top.aihmc.omnimorph.gui.screens.WardrobeScreen wardrobe,
                                                         top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                         Path data, Avatar equipped, LibraryPreviewSession parentPreview,
                                                         Runnable drain) throws Exception {
        var minecraft = Minecraft.getInstance();
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var live = new AtomicBoolean(true);
        var limits = top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults();
        var local = new top.aihmc.omnimorph.model.outfit.LocalOutfitPresets(data);
        int localCount = local.list(limits).size();
        try (var server = new CloudOutfitPresetsSmokeTest.Server();
             var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                     request -> request.header("Authorization", "Bearer synthetic-owner"));
             var client = new LibraryClient(access, new LibrarySession(server.origin(), owner, 1, live::get), minecraft::execute)) {
            field(wardrobe.getClass(), "outfitPresetsOpen").setBoolean(wardrobe, true);
            var screen = new top.aihmc.omnimorph.gui.screens.OutfitPresetsScreen(wardrobe, draft, owner, data, limits,
                    client, new LibraryTransferService(client, data.resolve("cache")));
            minecraft.setScreen(screen);
            screen.width = 640;
            screen.height = 360;
            var type = screen.getClass();
            var init = type.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(screen);
            awaitCondition(() -> !field(type, "busy").getBoolean(screen), drain);
            var storage = (top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown) field(type, "storage").get(screen);
            storage.onPress(null);
            String cloudLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.preset_personal_cloud").getString();
            var menu = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
            var cloudChoice = (top.aihmc.omnimorph.gui.widgets.Button) menu.getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(cloudLabel)).findFirst().orElseThrow();
            cloudChoice.onPress(null);
            awaitCondition(() -> !field(type, "busy").getBoolean(screen), drain);
            require(field(type, "cloud").getBoolean(screen) && ((java.util.List<?>) field(type, "items").get(screen)).isEmpty(),
                    "the native storage selector loads the authenticated owner's separate cloud list");
            var name = (top.aihmc.omnimorph.gui.widgets.TextField) field(type, "nameField").get(screen);
            name.getField().setValue("个人云端预设");
            var save = (top.aihmc.omnimorph.gui.widgets.Button) field(type, "saveNew").get(screen);
            long generation = draft.generation();
            server.loseNext.set(true);
            save.onPress(null);
            awaitCondition(() -> !field(type, "busy").getBoolean(screen), drain);
            require(field(type, "message").get(screen).toString().contains("BUSY"), "uncertain cloud Save remains visibly failed");
            var operation = (CloudOutfitPresets.Save) field(type, "cloudSave").get(screen);
            UUID operationId = operation.operationId();
            name.getField().setValue("This later edit is not part of the retry");
            var retry = (top.aihmc.omnimorph.gui.widgets.Button) field(type, "retry").get(screen);
            require(retry.isActive(), "an uncertain save has an explicit retry action");
            retry.onPress(null);
            awaitCondition(() -> field(type, "previewReady").getBoolean(screen), drain);
            var selected = (top.aihmc.omnimorph.model.outfit.LocalOutfitPresets.Preset) field(type, "selected").get(screen);
            require(selected.name().equals("个人云端预设") && selected.revision() == 1
                            && operation.operationId().equals(operationId) && server.commits.get() == 1 && server.replays.get() == 1
                            && local.list(limits).size() == localCount && draft.generation() == generation
                            && AvatarManager.getAvatarForPlayer(owner) == equipped,
                    "native cloud retry retains the original intent/operation, creates one private document and never writes the local store or equipment");
            name.getField().setValue("云端更新");
            ((top.aihmc.omnimorph.gui.widgets.Button) field(type, "update").get(screen)).onPress(null);
            awaitCondition(() -> field(type, "previewReady").getBoolean(screen), drain);
            selected = (top.aihmc.omnimorph.model.outfit.LocalOutfitPresets.Preset) field(type, "selected").get(screen);
            require(selected.revision() == 2 && selected.name().equals("云端更新"), "native cloud Update uses the owner's selected document revision");
            var presets = new CloudOutfitPresets(client, limits);
            var vanilla = new top.aihmc.omnimorph.model.outfit.OutfitConfig(1, 0, null, java.util.List.of(),
                    java.util.List.of(), Map.of(), false);
            UUID vanillaId = UUID.randomUUID();
            try (var seed = presets.save(vanillaId, 0, "Cloud vanilla import", vanilla)) {
                await(seed.future(), drain);
            }
            var load = type.getDeclaredMethod("load", UUID.class);
            load.setAccessible(true);
            load.invoke(screen, vanillaId);
            awaitCondition(() -> field(type, "previewReady").getBoolean(screen), drain);
            var importButton = (top.aihmc.omnimorph.gui.widgets.Button) field(type, "importPreset").get(screen);
            importButton.onPress(null);
            awaitDraftComposition(wardrobe, parentPreview, drain);
            require(minecraft.screen == wardrobe && draft.configuration().base() == null && draft.dirty()
                            && AvatarManager.getAvatarForPlayer(owner) == equipped && server.unexpected.get() == 0,
                    "a cloud-owned preset imports only into the retained draft and never publishes or equips it");
            var cancel = wardrobe.getClass().getDeclaredMethod("cancelOutfitDraft");
            cancel.setAccessible(true);
            cancel.invoke(wardrobe);
            field(wardrobe.getClass(), "outfitPresetsOpen").setBoolean(wardrobe, true);
            var retired = new top.aihmc.omnimorph.gui.screens.OutfitPresetsScreen(wardrobe, draft, owner, data, limits,
                    client, new LibraryTransferService(client, data.resolve("cache")));
            minecraft.setScreen(retired);
            retired.width = 640;
            retired.height = 360;
            init.invoke(retired);
            awaitCondition(() -> !field(type, "busy").getBoolean(retired), drain);
            var switchStorage = type.getDeclaredMethod("changeStorage", int.class);
            switchStorage.setAccessible(true);
            switchStorage.invoke(retired, 1);
            awaitCondition(() -> !field(type, "busy").getBoolean(retired), drain);
            load.invoke(retired, vanillaId);
            awaitCondition(() -> field(type, "previewReady").getBoolean(retired), drain);
            live.set(false);
            ((top.aihmc.omnimorph.gui.widgets.Button) field(type, "importPreset").get(retired)).onPress(null);
            require(draft.configuration().equals(draft.committed()), "retired cloud sessions cannot import even a previously prepared all-local preset");
            retired.onClose();
            System.out.println("PASS personal cloud preset controls: actual storage selector/authenticated HTTP, lost-response retry with frozen intent, update/private preview/import, separate local files and retired callbacks; real GUI/GPU MANUAL/PENDING");
        }
    }

    private static void awaitCondition(PresetCondition condition, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!condition.ready() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        require(condition.ready(), "native preset screen operation completed within the bounded wait");
        drain.run();
    }

    private static boolean hasPrivateComposition(LibraryPreviewSession preview) throws Exception {
        Object binding = field(LibraryPreviewSession.class, "binding").get(preview);
        return binding != null && field(LibraryPreviewBinding.class, "composition").get(binding) != null;
    }

    private static void testStaleDraftStart(top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry entry,
                                            Runnable drain) throws Exception {
        var type = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var preview = new LibraryPreviewSession()) {
            field(type, "preview").set(screen, preview);
            var choose = type.getDeclaredMethod("selectCosmetic", entry.getClass());
            choose.setAccessible(true);
            choose.invoke(screen, entry);
            var blocker = top.aihmc.omnimorph.avatar.AvatarLoadExecutor.submit(() -> {
                started.countDown();
                try {
                    require(release.await(5, TimeUnit.SECONDS), "test worker gate releases promptly");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CompletionException(interrupted);
                }
                return true;
            });
            try {
                require(started.await(5, TimeUnit.SECONDS), "bounded model worker reaches the test gate");
                var add = type.getDeclaredMethod("addSelectedInstance");
                add.setAccessible(true);
                add.invoke(screen);
                var unavailable = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(entry.path(),
                        entry.localId(), null, "INVALID_COSMETIC_SOURCE");
                choose.invoke(screen, unavailable);
            } finally {
                release.countDown();
            }
            await(blocker, drain);
            @SuppressWarnings("unchecked")
            var pending = (CompletableFuture<top.aihmc.omnimorph.model.outfit.OutfitDraft>) field(type, "draftStart").get(screen);
            var draft = await(pending, drain);
            awaitDraftComposition(screen, preview, drain);
            require(draft.configuration().instances().isEmpty() && preview.outfitBase() != null,
                    "late preparation cannot add an old resource; candidate changes retain the current base");
        }
    }

    private static void testInstanceTransformFields(Unsafe unsafe, top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                                     top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                     UUID first, UUID sibling, Runnable drain) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        Object previousFont = field(Minecraft.class, "font").get(minecraft);
        var font = new net.minecraft.client.gui.Font(null) {
            private final net.minecraft.client.StringSplitter splitter = new net.minecraft.client.StringSplitter((codepoint, style) -> 6f);

            @Override
            public net.minecraft.client.StringSplitter getSplitter() {
                return splitter;
            }

            @Override
            public int width(String value) {
                return value.codePointCount(0, value.length()) * 6;
            }

            @Override
            public int width(net.minecraft.network.chat.FormattedText value) {
                return width(value.getString());
            }

            @Override
            public int width(net.minecraft.util.FormattedCharSequence value) {
                int[] count = {0};
                value.accept((index, style, codepoint) -> {
                    count[0]++;
                    return true;
                });
                return count[0] * 6;
            }

            @Override
            public java.util.List<net.minecraft.util.FormattedCharSequence> split(net.minecraft.network.chat.FormattedText value, int width) {
                return java.util.List.of(net.minecraft.util.FormattedCharSequence.forward(value.getString(), net.minecraft.network.chat.Style.EMPTY));
            }

            @Override
            public String plainSubstrByWidth(String value, int width) {
                return splitter.plainHeadByWidth(value, width, net.minecraft.network.chat.Style.EMPTY);
            }

            @Override
            public String plainSubstrByWidth(String value, int width, boolean tail) {
                return tail ? splitter.plainTailByWidth(value, width, net.minecraft.network.chat.Style.EMPTY)
                        : plainSubstrByWidth(value, width);
            }
        };
        field(Minecraft.class, "font").set(minecraft, font);
        var unchangedSibling = draft.instance(sibling);
        try {
            draft.select(first);
            field(screen.getClass(), "layout").set(screen,
                    top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
            var build = screen.getClass().getDeclaredMethod("buildInstanceTransform");
            build.setAccessible(true);
            build.invoke(screen);
            var selector = screen.children().stream()
                    .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance)
                    .map(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::cast)
                    .filter(control -> {
                        try {
                            var choices = (java.util.function.Supplier<?>) field(control.getClass(), "choices").get(control);
                            return ((java.util.List<?>) choices.get()).stream()
                                    .anyMatch(label -> ((net.minecraft.network.chat.Component) label).getString().contains(" / "));
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    }).findFirst().orElseThrow();
            var labels = (java.util.function.Supplier<?>) field(selector.getClass(), "choices").get(selector);
            var options = (java.util.List<?>) labels.get();
            int manual = -1;
            for (int index = 0; index < options.size(); index++) {
                if (((net.minecraft.network.chat.Component) options.get(index)).getString().contains(" / ")) {
                    manual = index;
                    break;
                }
            }
            var selectAnchor = (java.util.function.IntConsumer) field(selector.getClass(), "changed").get(selector);
            var previousScreen = minecraft.screen;
            var windowField = field(Minecraft.class, "window");
            Object previousWindow = windowField.get(minecraft);
            var viewport = (com.mojang.blaze3d.platform.Window) unsafe.allocateInstance(com.mojang.blaze3d.platform.Window.class);
            field(viewport.getClass(), "guiScaledWidth").setInt(viewport, 640);
            field(viewport.getClass(), "guiScaledHeight").setInt(viewport, 360);
            windowField.set(minecraft, viewport);
            minecraft.screen = screen;
            try {
                selector.onPress(null);
                var menu = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
                require(menu != null && menu.getEntries().size() <= 8,
                        "attachment menus are bounded even when the hierarchy exceeds one page");
                var nextLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.anchor_next").getString();
                var next = (top.aihmc.omnimorph.gui.widgets.Button) menu.getEntries().stream()
                        .filter(button -> button.getMessage().getString().equals(nextLabel)).findFirst().orElseThrow();
                next.onPress(null);
                var following = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
                require(following != menu && following.getEntries().size() <= 8,
                        "next page opens a bounded menu without discarding remaining targets");
            } finally {
                screen.contextMenu = null;
                minecraft.screen = previousScreen;
                windowField.set(minecraft, previousWindow);
            }
            selectAnchor.accept(manual);
            require(draft.instance(first).anchor().type() == top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.BASE_BONE
                            && draft.instance(first).anchor().bone().instanceId() == null
                            && draft.instance(sibling).equals(unchangedSibling),
                    "actual anchor control binds a stable base bone on the selected copy only");
            var attached = draft.configuration();
            draft.select(sibling);
            selectAnchor.accept(0);
            require(draft.configuration().equals(attached), "stale anchor menus cannot edit a newly selected instance");
            draft.select(first);
            var layoutBeforeFocus = field(screen.getClass(), "layout").get(screen);
            field(screen.getClass(), "layout").set(screen, null);
            long generationBeforeFocus = field(screen.getClass(), "draftPreviewGeneration").getLong(screen);
            var selectInstance = screen.getClass().getDeclaredMethod("selectDraftInstance", UUID.class);
            selectInstance.setAccessible(true);
            selectInstance.invoke(screen, first);
            var focused = (BoneTreeState) field(screen.getClass(), "bones").get(screen);
            require(focused.selectedTarget().kind() == OutfitHierarchy.Kind.BASE_BONE
                            && focused.selectedTarget().bone().equals(draft.instance(first).anchor().bone())
                            && first.equals(draft.selectedInstance())
                            && field(screen.getClass(), "draftPreviewGeneration").getLong(screen) == generationBeforeFocus,
                    "selecting an existing draft instance focuses its attachment while preserving instance ownership and preparation generation");
            field(screen.getClass(), "layout").set(screen, layoutBeforeFocus);
            var panel = (top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel) screen.children().stream()
                    .filter(top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel.class::isInstance).findFirst().orElseThrow();
            var inputs = panel.children().stream().filter(top.aihmc.omnimorph.gui.widgets.TextField.class::isInstance)
                    .map(top.aihmc.omnimorph.gui.widgets.TextField.class::cast).toList();
            require(inputs.size() == 9, "instance pane exposes all position/rotation/scale components");
            inputs.get(0).getField().setValue("10");
            inputs.get(5).getField().setValue("45");
            inputs.get(7).getField().setValue("1.5");
            require(draft.instance(first).transform().position().x() == 10
                            && draft.instance(first).transform().rotation().z() == 45
                            && draft.instance(first).transform().scale().y() == 1.5
                            && draft.instance(sibling).equals(unchangedSibling),
                    "actual field callbacks edit the selected instance and preserve its sibling");
            var valid = draft.configuration();
            inputs.get(6).getField().setValue("0");
            require(draft.configuration().equals(valid) && inputs.get(6).getBorderColour() == 0xffff5555,
                    "invalid scale stays in the input with an error and preserves the prior valid draft");
            inputs.get(6).getField().setValue("NaN");
            require(draft.configuration().equals(valid), "nonfinite input cannot enter the draft");
            inputs.get(6).getField().setValue("1");
            require(inputs.get(6).getBorderColour() == 0xffffffff, "valid replacement clears input validation state");
            panel.setHeight(36);
            panel.tick();
            var scroll = (top.aihmc.omnimorph.gui.widgets.ScrollBarWidget) field(
                    top.aihmc.omnimorph.gui.widgets.lists.AbstractList.class, "scrollBar").get(panel);
            require(scroll.isVisible(), "short instance panes expose scrolling instead of overlapping the toolbar");
            // Geometry/field routing only: deterministic font metrics do not prove real CJK pixels or layout.
            field(screen.getClass(), "layout").set(screen, null);
            testVirtualAnchorEditor(screen, draft, first, sibling, drain);
            testBoneOverrideEditor(unsafe, screen, draft, first, drain);
            testAutomaticAnchorFocus(screen, draft, first, drain);
            testTreeAttachmentActions(unsafe, screen, draft, first, drain);
        } finally {
            field(Minecraft.class, "font").set(minecraft, previousFont);
        }
    }

    private static void testTreeAttachmentActions(Unsafe unsafe, top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                                   top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                   UUID previousSelection, Runnable drain) throws Exception {
        var type = screen.getClass();
        var preview = (LibraryPreviewSession) field(type, "preview").get(screen);
        var root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "tree-attach-");
        var directory = top.aihmc.omnimorph.wizards.CosmeticCreator.create(root, "插槽尾巴", "Fixture", "",
                top.aihmc.omnimorph.wizards.CosmeticTemplate.TAIL, "hip",
                top.aihmc.omnimorph.model.outfit.OutfitConfig.Transform.identity());
        String authored = Files.readString(directory.resolve("cosmetic.json"));
        var source = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(directory, () -> false);
        var entry = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(directory, UUID.randomUUID(), source, "");
        @SuppressWarnings("unchecked")
        var resources = (Map<top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef,
                top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry>) field(type, "draftResources").get(screen);
        resources.put(entry.reference(), entry);
        var base = preview.outfitHierarchy().anchors(null).stream()
                .filter(choice -> choice.anchor().type() == top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.BASE_BONE)
                .findFirst().orElseThrow().anchor();
        UUID first = draft.add(entry.reference(), base, source.metadata().defaultTransform());
        UUID second = draft.add(entry.reference(), base, source.metadata().defaultTransform());
        var refresh = type.getDeclaredMethod("previewOutfitDraft");
        refresh.setAccessible(true);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        testCosmeticControls(screen, draft, first, second);
        draft.select(first);
        field(type, "layout").set(screen, top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        var build = type.getDeclaredMethod("buildInstanceTransform");
        build.setAccessible(true);
        build.invoke(screen);
        var pickLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.anchor_pick").getString();
        var pick = screen.children().stream().filter(top.aihmc.omnimorph.gui.widgets.Button.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(pickLabel)).toList().getLast();
        field(type, "layout").set(screen, null);
        var before = draft.configuration();
        long generation = field(type, "draftPreviewGeneration").getLong(screen);
        pick.onPress(null);
        require(first.equals(field(type, "attachingInstance").get(screen)) && before.equals(draft.configuration())
                        && field(type, "draftPreviewGeneration").getLong(screen) == generation,
                "actual instance editor button enters tree targeting without changing the outfit or preparing sources");
        var tree = (BoneTreeState) field(type, "bones").get(screen);
        var firstTip = preview.outfitHierarchy().targets().values().stream()
                .filter(target -> first.equals(target.instanceId()) && target.mountable() && target.bone() != null)
                .findFirst().orElseThrow();
        var secondTip = preview.outfitHierarchy().targets().values().stream()
                .filter(target -> second.equals(target.instanceId()) && target.mountable() && target.bone() != null)
                .findFirst().orElseThrow();
        var choose = type.getDeclaredMethod("selectTreeTarget", OutfitHierarchy.Target.class);
        choose.setAccessible(true);
        require(tree.locate(new top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorRef(
                top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.ACCESSORY_BONE,
                null, secondTip.bone(), null, null, null)), "the target slot exists in the second copy's tree");
        choose.invoke(screen, tree.selectedTarget());
        require(first.equals(draft.selectedInstance()) && before.equals(draft.configuration()),
                "choosing another accessory's slot retains the pending child instead of switching to its parent");
        var minecraft = Minecraft.getInstance();
        var previousScreen = minecraft.screen;
        var windowField = field(Minecraft.class, "window");
        Object previousWindow = windowField.get(minecraft);
        var viewport = (com.mojang.blaze3d.platform.Window) unsafe.allocateInstance(com.mojang.blaze3d.platform.Window.class);
        field(viewport.getClass(), "guiScaledWidth").setInt(viewport, 640);
        field(viewport.getClass(), "guiScaledHeight").setInt(viewport, 360);
        windowField.set(minecraft, viewport);
        minecraft.screen = screen;
        try {
            var open = type.getDeclaredMethod("openAnchorActions", net.minecraft.client.gui.components.Button.class);
            open.setAccessible(true);
            var trigger = new top.aihmc.omnimorph.gui.widgets.Button(0, 0, 120, 20,
                    net.minecraft.network.chat.Component.literal("Targets"), null, ignored -> {});
            open.invoke(screen, trigger);
            var label = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.anchor_bind_accessory").getString();
            var bind = top.aihmc.omnimorph.utils.ui.UIHelper.getContext().getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(label))
                    .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast).findFirst().orElseThrow();
            var parentBefore = draft.instance(second);
            bind.onPress(null);
            require(draft.instance(first).anchor().type() == top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.ACCESSORY_BONE
                            && draft.instance(first).anchor().bone().equals(secondTip.bone())
                            && draft.instance(second).equals(parentBefore) && first.equals(draft.selectedInstance())
                            && field(type, "attachingInstance").get(screen) == null,
                    "explicit accessory socket menu stores the target's UUID/path/hash on the child only");
            awaitDraftComposition(screen, preview, drain);
            var begin = type.getDeclaredMethod("beginAnchorSelection", UUID.class);
            begin.setAccessible(true);
            draft.select(second);
            begin.invoke(screen, second);
            long epoch = field(type, "attachmentSelection").getLong(screen);
            var apply = type.getDeclaredMethod("bindTreeTarget", UUID.class, long.class, OutfitHierarchy.Target.class);
            apply.setAccessible(true);
            var valid = draft.configuration();
            apply.invoke(screen, second, epoch, firstTip);
            require(draft.configuration().equals(valid), "reverse accessory binding is rejected atomically when it closes a cycle");
            apply.invoke(screen, second, epoch, secondTip);
            require(draft.configuration().equals(valid), "self-binding is rejected without changing either instance");
            var internal = preview.outfitHierarchy().targets().values().stream()
                    .filter(target -> first.equals(target.instanceId()) && target.bone() != null && !target.mountable())
                    .findFirst().orElseThrow();
            apply.invoke(screen, second, epoch, internal);
            require(draft.configuration().equals(valid), "unexported bones cannot be used as tree attachment targets");
            draft.setEnabled(first, false);
            var disabled = draft.configuration();
            apply.invoke(screen, second, epoch, firstTip);
            require(draft.configuration().equals(disabled), "a stale menu cannot bind to a newly disabled owner");
            draft.setEnabled(first, true);
            valid = draft.configuration();
            bind.onPress(null);
            require(draft.configuration().equals(valid) && second.equals(field(type, "attachingInstance").get(screen)),
                    "an old successful menu cannot mutate a later target-selection session");
            open.invoke(screen, trigger);
            var cancelLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.anchor_pick_cancel").getString();
            var cancel = top.aihmc.omnimorph.utils.ui.UIHelper.getContext().getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(cancelLabel))
                    .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast).findFirst().orElseThrow();
            cancel.onPress(null);
            require(draft.configuration().equals(valid) && field(type, "attachingInstance").get(screen) == null,
                    "canceling tree targeting preserves the outfit configuration");
            var context = type.getDeclaredMethod("openBoneContext", OutfitHierarchy.Target.class, double.class, double.class);
            context.setAccessible(true);
            context.invoke(screen, firstTip, 100d, 100d);
            var unmount = top.aihmc.omnimorph.utils.ui.UIHelper.getContext().getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(
                            top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.instance_unmount").getString()))
                    .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast).findFirst().orElseThrow();
            unmount.onPress(null);
            awaitDraftComposition(screen, preview, drain);
            require(draft.configuration().instances().stream().noneMatch(value -> value.instanceId().equals(first))
                            && draft.instance(second).equals(parentBefore),
                    "bone context unmount removes its owning instance and preserves the other copy");
            var afterUnmount = draft.configuration();
            unmount.onPress(null);
            require(draft.configuration().equals(afterUnmount), "a stale unmount menu cannot remove another instance");
        } finally {
            screen.contextMenu = null;
            minecraft.screen = previousScreen;
            windowField.set(minecraft, previousWindow);
        }
        draft.remove(second);
        resources.remove(entry.reference());
        draft.select(previousSelection);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        require(Files.readString(directory.resolve("cosmetic.json")).equals(authored), "tree binding never rewrites author metadata");
        System.out.println("PASS tree attachment menus: actual picker/socket actions, duplicate ownership, native preparation, "
                + "cycle/self/private/disabled/stale rejection, cancel and unchanged source; GUI MANUAL/PENDING");
    }

    private static void testCosmeticControls(top.aihmc.omnimorph.gui.screens.WardrobeScreen wardrobe,
                                             top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                             UUID first, UUID second) throws Exception {
        var wardrobeType = wardrobe.getClass();
        var session = (LibraryPreviewSession) field(wardrobeType, "preview").get(wardrobe);
        var composition = session.outfitComposition();
        var base = (Avatar) field(LibraryPreviewSession.class, "avatar").get(session);
        @SuppressWarnings("unchecked")
        var skeleton = (top.aihmc.omnimorph.model.outfit.SkeletonIndex.Binding<Object>) composition.editorSnapshot(base).base();
        var matrices = new java.util.IdentityHashMap<Object, org.joml.Matrix4f>();
        for (Object part : skeleton.parts()) {
            matrices.put(part, new org.joml.Matrix4f().scaling(1f / 16));
        }
        var pose = top.aihmc.omnimorph.model.outfit.SkeletonPose.fromNative(skeleton, matrices, new org.joml.Matrix4f(),
                top.aihmc.omnimorph.model.outfit.SkeletonPose.NativeUnits.MODEL_UNITS);
        try (var frame = composition.retain()) {
            frame.draw(pose, new net.minecraft.client.renderer.entity.state.AvatarRenderState(), 0, ignored -> {});
        }
        var groups = composition.controlGroups();
        @SuppressWarnings("unchecked")
        var actualInstances = (Map<UUID, Avatar>) field(composition.getClass(), "instances").get(composition);
        require(groups.stream().anyMatch(group -> group.instance().instanceId().equals(first)),
                "generated tail registers controls through native preparation: " + actualInstances.entrySet().stream()
                        .map(entry -> entry.getKey() + " loaded=" + entry.getValue().loaded + " scriptError="
                                + entry.getValue().scriptError + " lua=" + (entry.getValue().luaRuntime != null)
                                + " controls=" + entry.getValue().controls.asMap().keySet()).toList());
        var firstGroup = groups.stream().filter(group -> group.instance().instanceId().equals(first)).findFirst().orElseThrow();
        var secondGroup = groups.stream().filter(group -> group.instance().instanceId().equals(second)).findFirst().orElseThrow();
        require(groups == composition.controlGroups() && firstGroup.avatar() != secondGroup.avatar(),
                "unchanged registration versions reuse the group snapshot and duplicate resources retain separate owners");
        field(wardrobeType, "cosmeticControlsOpen").setBoolean(wardrobe, true);
        var settings = new top.aihmc.omnimorph.gui.screens.CosmeticControlsScreen(wardrobe, session,
                composition, draft, FiguraMod.getLocalPlayerUUID());
        settings.width = 640;
        settings.height = 360;
        var build = top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.class.getDeclaredMethod("rebuild");
        build.setAccessible(true);
        var before = draft.configuration();
        long generation = field(wardrobeType, "draftPreviewGeneration").getLong(wardrobe);
        build.invoke(settings);
        var select = settings.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::cast).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var labels = (java.util.function.Supplier<java.util.List<net.minecraft.network.chat.Component>>)
                field(select.getClass(), "choices").get(select);
        require(labels.get().stream().anyMatch(label -> label.getString().contains("插槽尾巴 #1"))
                        && labels.get().stream().anyMatch(label -> label.getString().contains("插槽尾巴 #2")),
                "the real group selector preserves the ordered duplicate names");
        var choose = (java.util.function.IntConsumer) field(select.getClass(), "changed").get(select);
        choose.accept(groups.indexOf(firstGroup));
        require(draft.configuration().equals(before), "opening and switching groups do not persist factory defaults");
        var firstSwitch = (top.aihmc.omnimorph.gui.widgets.SwitchButton) settings.children().stream()
                .filter(widget -> widget.getClass().getSimpleName().equals("ControlSwitch")).findFirst().orElseThrow();
        firstSwitch.onPress(null);
        require(Boolean.FALSE.equals(draft.instance(first).controls().get("visible"))
                        && !draft.instance(second).controls().containsKey("visible")
                        && Boolean.FALSE.equals(firstGroup.avatar().controls.getValue("visible"))
                        && Boolean.TRUE.equals(secondGroup.avatar().controls.getValue("visible")),
                "native control callbacks save only the chosen instance in the draft and its actual runtime");
        choose.accept(groups.indexOf(secondGroup));
        var valid = draft.configuration();
        firstSwitch.onPress(null);
        require(draft.configuration().equals(valid), "a retained old control cannot edit another group's owner after switching");
        var secondSwitch = (top.aihmc.omnimorph.gui.widgets.SwitchButton) settings.children().stream()
                .filter(widget -> widget.getClass().getSimpleName().equals("ControlSwitch")).findFirst().orElseThrow();
        secondSwitch.onPress(null);
        require(Boolean.FALSE.equals(draft.instance(second).controls().get("visible"))
                        && firstGroup.avatar().cosmeticContext != secondGroup.avatar().cosmeticContext,
                "the second copy edits its own execution context and persistent draft values");
        var scene = (top.aihmc.omnimorph.gui.widgets.EntityPreview) settings.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.EntityPreview.class::isInstance).findFirst().orElseThrow();
        require(field(scene.getClass(), "libraryPreview").get(scene) == session,
                "settings reuse the complete private outfit preview rather than the equipped base model");
        wardrobe.removed();
        require(session.outfitComposition() == composition && !field(wardrobeType, "removed").getBoolean(wardrobe),
                "opening the retained settings child does not retire the draft generation");
        session.setExpanded(true);
        settings.removed();
        require(field(wardrobeType, "cosmeticControlsOpen").getBoolean(wardrobe)
                        && session.outfitComposition() == composition,
                "opening the real expanded preview path retains the settings owner and draft session");
        session.setExpanded(false);
        var controls = secondGroup.avatar().controls;
        long revision = controls.definitionRevision();
        var added = new top.aihmc.omnimorph.avatar.control.AvatarControlDefinition("late_control",
                top.aihmc.omnimorph.avatar.control.AvatarControlType.TEXT).setDefault("late");
        controls.register(added);
        require(controls.definitionRevision() > revision && composition.controlGroups() != groups,
                "late control registration invalidates groups through definition versions");
        build.invoke(settings);
        var text = (top.aihmc.omnimorph.gui.widgets.TextField) settings.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.TextField.class::isInstance).findFirst().orElseThrow();
        valid = draft.configuration();
        text.getField().setValue("x".repeat(secondGroup.avatar().cosmeticContext.limits().maxStateStringBytes() + 1));
        require(draft.configuration().equals(valid) && controls.getValue("late_control").equals("late")
                        && text.getBorderColour() == 0xffff5555,
                "backend string budget rejection preserves runtime and draft values and marks the native field");
        text.getField().setValue("valid");
        require("valid".equals(draft.instance(second).controls().get("late_control"))
                        && text.getBorderColour() == 0xffffffff,
                "valid text replacement updates only the selected copy and clears the validation error");
        revision = controls.definitionRevision();
        added.setPage("options");
        require(controls.definitionRevision() > revision, "changing an existing definition notifies the settings page");
        controls.remove("late_control");
        revision = controls.definitionRevision();
        added.setTitle("removed definition");
        require(controls.definitionRevision() == revision, "removed definitions cannot invalidate their former owner");
        draft.setEnabled(second, false);
        valid = draft.configuration();
        secondSwitch.onPress(null);
        require(draft.configuration().equals(valid), "disabled instance callbacks are rejected immediately");
        draft.setEnabled(second, true);
        var close = wardrobeType.getDeclaredMethod("closeCosmeticControls", boolean.class);
        close.setAccessible(true);
        close.invoke(wardrobe, true);
        valid = draft.configuration();
        secondSwitch.onPress(null);
        require(draft.configuration().equals(valid)
                        && field(wardrobeType, "draftPreviewGeneration").getLong(wardrobe) == generation,
                "closed settings reject retained callbacks and value edits do not prepare or publish equipment");
        testCosmeticWheelCallbacks(wardrobe, draft, composition, firstGroup.avatar(), secondGroup.avatar());
        draft.setControls(first, before.instances().stream().filter(instance -> instance.instanceId().equals(first))
                .findFirst().orElseThrow().controls());
        draft.setControls(second, before.instances().stream().filter(instance -> instance.instanceId().equals(second))
                .findFirst().orElseThrow().controls());
        System.out.println("PASS cosmetic settings: native reused controls/group selector, two-copy values, draft persistence, "
                + "retained preview, definition versions and stale/disabled/closed callback rejection; GUI MANUAL/PENDING");
    }

    private static void testCosmeticWheelCallbacks(top.aihmc.omnimorph.gui.screens.WardrobeScreen wardrobe,
                                                    top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                    top.aihmc.omnimorph.model.outfit.OutfitComposition composition,
                                                    Avatar first, Avatar second) throws Exception {
        var wheel = composition.actionWheel();
        require(!wheel.available(), "geometry/animation resources alone do not invent author wheel actions");
        String script = """
                wheel_clicks, wheel_scrolls, wheel_intercepts = 0, 0, 0
                local root = action_wheel:newPage("author-root")
                local child = action_wheel:newPage("author-child")
                root:newAction():title("Author callback")
                  :onLeftClick(function(action)
                    wheel_clicks = wheel_clicks + 1
                    cosmetic_controls:set("amplitude", 10 + wheel_clicks)
                  end)
                  :onScroll(function(delta, action)
                    wheel_scrolls = wheel_scrolls + delta
                    cosmetic_controls:set("speed", wheel_scrolls / 10)
                  end)
                root:newAction():title("Author child"):onLeftClick(function() action_wheel:setPage(child) end)
                child:newAction():title("Author back"):onLeftClick(function() action_wheel:setPage(root) end)
                action_wheel.leftClick = function() wheel_intercepts = wheel_intercepts + 1 return false end
                action_wheel:setPage(root)
                cosmetic_animations:setPage(root):set_page("author-root")
                """;
        for (Avatar owner : java.util.List.of(first, second)) {
            var globals = (org.luaj.vm2.Globals) field(owner.luaRuntime.getClass(), "userGlobals").get(owner.luaRuntime);
            require(owner.run(globals.load(script, "wheel author fixture"), owner.tick) != null
                            && !owner.scriptError && !owner.isHost,
                    "real cosmetic Lua runtime registers its author wheel without changing host privileges");
        }
        var source = first.luaRuntime.action_wheel.getCurrentPage();
        var firstGlobals = (org.luaj.vm2.Globals) field(first.luaRuntime.getClass(), "userGlobals").get(first.luaRuntime);
        var secondGlobals = (org.luaj.vm2.Globals) field(second.luaRuntime.getClass(), "userGlobals").get(second.luaRuntime);
        var sourceAction = source.getAction(1);
        Object original = source.getActions(null);
        require(wheel.available() && wheel.page(null).slots()[0] != null,
                "registered author pages create a no-base host entry");
        wheel.execute(null, 0, true);
        wheel.execute(null, 1, true);
        var duplicates = wheel.page(null);
        require(duplicates.slots()[1].getTitle().equals("插槽尾巴 #1")
                        && duplicates.slots()[2].getTitle().equals("插槽尾巴 #2"),
                "composition metadata and ordered identities supply the actual duplicate wheel labels");
        wheel.execute(null, 1, true);
        require(wheel.page(null).slots()[0] == sourceAction, "presentation retains the exact author Action object");
        wheel.execute(null, 0, true);
        wheel.scroll(null, 0, 2);
        require(firstGlobals.get("wheel_clicks").toint() == 1
                        && secondGlobals.get("wheel_clicks").toint() == 0
                        && firstGlobals.get("wheel_intercepts").toint() == 1
                        && firstGlobals.get("wheel_scrolls").toint() == 2,
                "real Lua action/global/scroll callbacks execute solely in the selected cosmetic sandbox");
        wheel.execute(null, 1, true);
        require(wheel.page(null).getTitle().equals("author-child"), "author-driven subpage navigation stays within its actual wheel API");
        wheel.execute(null, 0, true);
        require(wheel.page(null).getTitle().equals("author-root"), "the author's own Back callback is preserved");
        wheel.execute(null, 2, true);
        wheel.execute(null, 2, true);
        wheel.execute(null, 0, true);
        require(secondGlobals.get("wheel_clicks").toint() == 1
                        && firstGlobals.get("wheel_clicks").toint() == 1,
                "selecting the second copy does not fan out callbacks to the first");
        require(source.getActions(null).equals(original), "host navigation never writes into author action slots");
        var explicit = first.cosmeticAnimations.animationPage();
        try {
            first.cosmeticAnimations.setPage("missing page");
            throw new AssertionError("unknown explicit page accepted");
        } catch (org.luaj.vm2.LuaError expected) {
            require(first.cosmeticAnimations.animationPage() == explicit, "invalid registration preserves the prior entry");
        }
        first.cosmeticAnimations.setPage(null);
        require(first.cosmeticAnimations.animationPage() == first.luaRuntime.action_wheel.getCurrentPage(),
                "nil registration restores the instance's existing wheel entry");
        testCosmeticWheelPreview(wardrobe, draft, composition, first, second);
        wheel.close();
        require(wheel.page(null) == null && !wheel.available(), "closing the private wheel removes all callback routes");
        System.out.println("PASS cosmetic wheel/real Lua: generated packages, typed page registration, exact author actions, "
                + "duplicate sandbox ownership, global/scroll callbacks and original subpage navigation; HUD/input/GPU MANUAL/PENDING");
    }

    private static void testCosmeticWheelPreview(top.aihmc.omnimorph.gui.screens.WardrobeScreen wardrobe,
                                                 top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                 top.aihmc.omnimorph.model.outfit.OutfitComposition composition,
                                                 Avatar first, Avatar second) throws Exception {
        var minecraft = Minecraft.getInstance();
        var session = (LibraryPreviewSession) field(wardrobe.getClass(), "preview").get(wardrobe);
        var windowField = field(Minecraft.class, "window");
        var previousWindow = windowField.get(minecraft);
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var viewport = (com.mojang.blaze3d.platform.Window) ((Unsafe) unsafeField.get(null))
                .allocateInstance(com.mojang.blaze3d.platform.Window.class);
        field(viewport.getClass(), "guiScaledWidth").setInt(viewport, 640);
        field(viewport.getClass(), "guiScaledHeight").setInt(viewport, 360);
        windowField.set(minecraft, viewport);
        int previousWidth = wardrobe.width;
        int previousHeight = wardrobe.height;
        boolean enabled = top.aihmc.omnimorph.gui.ActionWheel.isEnabled();
        int selection = top.aihmc.omnimorph.gui.ActionWheel.getSelected();
        long generation = field(wardrobe.getClass(), "draftPreviewGeneration").getLong(wardrobe);
        var firstGlobals = (org.luaj.vm2.Globals) field(first.luaRuntime.getClass(), "userGlobals").get(first.luaRuntime);
        var beforeSibling = draft.instance(second.cosmeticContext.instanceId());
        try (var screens = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingScreens()) {
            minecraft.screen = wardrobe;
            wardrobe.width = 640;
            wardrobe.height = 360;
            var menuMethod = wardrobe.getClass().getDeclaredMethod("openInstances", net.minecraft.client.gui.components.Button.class);
            menuMethod.setAccessible(true);
            menuMethod.invoke(wardrobe, new top.aihmc.omnimorph.gui.widgets.Button(0, 0, 20, 20,
                    net.minecraft.network.chat.Component.literal("instances"), null, ignored -> {}));
            var menu = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
            String label = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.cosmetic_animations").getString();
            var open = (top.aihmc.omnimorph.gui.widgets.Button) menu.getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
            open.onPress(null);
            require(minecraft.screen instanceof top.aihmc.omnimorph.gui.screens.CosmeticAnimationScreen
                            && session.outfitComposition() == composition && !menu.isVisible(),
                    "the actual instance-menu action retains the current draft composition and opens its wheel preview");
            var preview = (top.aihmc.omnimorph.gui.screens.CosmeticAnimationScreen) minecraft.screen;
            preview.width = 640;
            preview.height = 360;
            var initialize = preview.getClass().getDeclaredMethod("init");
            initialize.setAccessible(true);
            initialize.invoke(preview);
            var model = (top.aihmc.omnimorph.gui.widgets.EntityPreview) preview.children().stream()
                    .filter(top.aihmc.omnimorph.gui.widgets.EntityPreview.class::isInstance).findFirst().orElseThrow();
            require(field(model.getClass(), "libraryPreview").get(model) == session,
                    "the preview widget renders the same private complete outfit rather than real equipment");
            java.util.function.IntConsumer press = key -> {
                require(preview.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, 0)), "numeric wheel input handled");
                require(preview.keyReleased(new net.minecraft.client.input.KeyEvent(key, 0, 0)), "matching wheel release handled");
            };
            press.accept(49);
            press.accept(50);
            press.accept(50);
            int previousClicks = firstGlobals.get("wheel_clicks").toint();
            preview.keyPressed(new net.minecraft.client.input.KeyEvent(49, 0, 0));
            preview.keyPressed(new net.minecraft.client.input.KeyEvent(49, 0, 0));
            require(firstGlobals.get("wheel_clicks").toint() == previousClicks + 1,
                    "a held numeric key does not replay the press action on key repeat");
            preview.keyReleased(new net.minecraft.client.input.KeyEvent(50, 0, 0));
            require(field(preview.getClass(), "heldInput").getInt(preview) == 49,
                    "releasing another key does not release the active input");
            preview.keyReleased(new net.minecraft.client.input.KeyEvent(49, 0, 0));
            require(field(preview.getClass(), "heldInput").getInt(preview) == -1
                            && draft.instance(first.cosmeticContext.instanceId()).controls().get("amplitude")
                                    .equals(first.controls.getValue("amplitude"))
                            && draft.instance(second.cosmeticContext.instanceId()).equals(beforeSibling),
                    "actual key input saves the selected cosmetic's callback values to its own draft only");
            int centerX = 213 + (640 - 213) / 2;
            double pointerX = centerX + 50;
            double pointerY = 130;
            var left = new net.minecraft.client.input.MouseButtonEvent(pointerX, pointerY,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0));
            preview.mouseClicked(left, false);
            require(firstGlobals.get("wheel_clicks").toint() == previousClicks + 2,
                    "native pointer hit testing routes to the original author callback");
            preview.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(pointerX, pointerY,
                    new net.minecraft.client.input.MouseButtonInfo(1, 0)));
            require(field(preview.getClass(), "heldInput").getInt(preview) == 0,
                    "right-button release does not end a left-button input");
            preview.mouseReleased(left);
            require(field(preview.getClass(), "heldInput").getInt(preview) == -1,
                    "matching native mouse release clears the held action input");
            preview.mouseScrolled(pointerX, pointerY, 2, 0);
            require(draft.instance(first.cosmeticContext.instanceId()).controls().get("speed")
                            .equals(first.controls.getValue("speed"))
                            && top.aihmc.omnimorph.gui.ActionWheel.isEnabled() == enabled
                            && top.aihmc.omnimorph.gui.ActionWheel.getSelected() == selection,
                    "wheel scrolling saves its owner's values without activating or changing the equipped wheel selection");
            int allowedClicks = firstGlobals.get("wheel_clicks").toint();
            draft.setEnabled(first.cosmeticContext.instanceId(), false);
            preview.keyPressed(new net.minecraft.client.input.KeyEvent(49, 0, 0));
            require(firstGlobals.get("wheel_clicks").toint() == allowedClicks,
                    "an instance disabled in the draft cannot execute through a still-retained prepared composition");
            draft.setEnabled(first.cosmeticContext.instanceId(), true);
            top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.open(first, "root");
            require(minecraft.screen instanceof top.aihmc.omnimorph.gui.screens.CosmeticControlsScreen
                            && session.outfitComposition() == composition,
                    "an actual wheel controls-page action opens the scoped owner settings without retiring the preview");
            var controls = (top.aihmc.omnimorph.gui.screens.CosmeticControlsScreen) minecraft.screen;
            controls.width = 640;
            controls.height = 360;
            var build = top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.class.getDeclaredMethod("rebuild");
            build.setAccessible(true);
            build.invoke(controls);
            var toggle = (top.aihmc.omnimorph.gui.widgets.SwitchButton) controls.children().stream()
                    .filter(child -> child.getClass().getSimpleName().equals("ControlSwitch")).findFirst().orElseThrow();
            toggle.onPress(null);
            controls.onClose();
            require(minecraft.screen == preview && session.outfitComposition() == composition,
                    "closing scoped settings returns to the same wheel and complete draft generation");
            var valid = draft.configuration();
            toggle.onPress(null);
            require(draft.configuration().equals(valid), "closed nested settings reject retained controls while the wheel remains open");
            top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.open(session.outfitBase(), "root");
            require(minecraft.screen instanceof top.aihmc.omnimorph.gui.screens.CosmeticControlsScreen,
                    "an original base-wheel settings action also uses the retained preview scope");
            var baseControls = (top.aihmc.omnimorph.gui.screens.CosmeticControlsScreen) minecraft.screen;
            baseControls.width = 640;
            baseControls.height = 360;
            build.invoke(baseControls);
            require(field(top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.class, "avatar").get(baseControls)
                            == session.outfitBase() && field(baseControls.getClass(), "group").get(baseControls) == null,
                    "base controls keep their real preview Avatar instead of editing the first cosmetic");
            baseControls.onClose();
            require(minecraft.screen == preview && session.outfitComposition() == composition,
                    "base settings return without changing the draft composition's lifetime");
            preview.onClose();
            require(minecraft.screen == wardrobe && session.outfitComposition() == composition
                            && !field(wardrobe.getClass(), "cosmeticAnimationsOpen").getBoolean(wardrobe),
                    "Done returns to the wardrobe with the same private draft and no retained input scope");
            valid = draft.configuration();
            preview.keyPressed(new net.minecraft.client.input.KeyEvent(49, 0, 0));
            require(draft.configuration().equals(valid)
                            && field(wardrobe.getClass(), "draftPreviewGeneration").getLong(wardrobe) == generation,
                    "old preview key callbacks cannot mutate a closed scope or prepare/equip resources");
        } finally {
            wardrobe.contextMenu = null;
            wardrobe.width = previousWidth;
            wardrobe.height = previousHeight;
            windowField.set(minecraft, previousWindow);
        }
        System.out.println("PASS wheel preview/native controls: actual menu, pointer/key/repeat/release/scroll routing, private preview, "
                + "per-copy draft values, nested settings return and closed-scope rejection; screen dispatch fixture, real GUI/GPU MANUAL/PENDING");
    }

    private static void testAutomaticAnchorFocus(top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                                  top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                  UUID previousSelection, Runnable drain) throws Exception {
        var type = screen.getClass();
        var preview = (LibraryPreviewSession) field(type, "preview").get(screen);
        var root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "auto-focus-");
        var directory = top.aihmc.omnimorph.wizards.CosmeticCreator.create(root, "自动拖尾", "Fixture", "",
                top.aihmc.omnimorph.wizards.CosmeticTemplate.TRAIL, "tailtip",
                top.aihmc.omnimorph.model.outfit.OutfitConfig.Transform.identity());
        var source = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(directory, () -> false);
        var entry = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(directory, UUID.randomUUID(), source, "");
        @SuppressWarnings("unchecked")
        var resources = (Map<top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef,
                top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry>) field(type, "draftResources").get(screen);
        resources.put(entry.reference(), entry);
        var anchor = new top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorRef(
                top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null);
        UUID id = draft.add(entry.reference(), anchor, source.metadata().defaultTransform());
        var refresh = type.getDeclaredMethod("previewOutfitDraft");
        refresh.setAccessible(true);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        var select = type.getDeclaredMethod("selectDraftInstance", UUID.class);
        select.setAccessible(true);
        select.invoke(screen, id);
        long generation = field(type, "draftPreviewGeneration").getLong(screen);
        require(id.equals(field(type, "automaticAnchorFocus").get(screen)),
                "selecting an automatic instance requests dynamic focus instead of inventing a static target");
        var composition = (top.aihmc.omnimorph.model.outfit.OutfitComposition) field(LibraryPreviewSession.class, "previewComposition").get(preview);
        Avatar base = (Avatar) field(LibraryPreviewSession.class, "avatar").get(preview);
        @SuppressWarnings("unchecked")
        var binding = (top.aihmc.omnimorph.model.outfit.SkeletonIndex.Binding<Object>) composition.editorSnapshot(base).base();
        var matrices = new java.util.IdentityHashMap<Object, org.joml.Matrix4f>();
        for (Object part : binding.parts()) {
            matrices.put(part, new org.joml.Matrix4f().scaling(1f / 16));
        }
        // Controlled native-part matrices isolate GUI routing; native draw/pivot coverage is in the companion test.
        var pose = top.aihmc.omnimorph.model.outfit.SkeletonPose.fromNative(binding, matrices, new org.joml.Matrix4f(),
                top.aihmc.omnimorph.model.outfit.SkeletonPose.NativeUnits.MODEL_UNITS);
        try (var frame = composition.retain()) {
            frame.capturePreviewSelection(new PreviewBoneHighlight.Mount(id));
            frame.draw(pose, new net.minecraft.client.renderer.entity.state.AvatarRenderState(), 0, ignored -> {});
            var target = preview.previewMountTarget(id);
            require(target != null && target.bone() != null, "actual trusted controller reports the custom base root fallback");
            var update = type.getDeclaredMethod("updateAutomaticAnchorFocus");
            update.setAccessible(true);
            update.invoke(screen);
            var tree = (BoneTreeState) field(type, "bones").get(screen);
            require(tree.selectedTarget() != null && target.bone().equals(tree.selectedTarget().bone())
                            && id.equals(draft.selectedInstance())
                            && field(type, "draftPreviewGeneration").getLong(screen) == generation,
                    "automatic GUI focus follows the controller reference without changing the editing instance or loading resources");
            draft.setEnabled(id, false);
            update.invoke(screen);
            require(field(type, "automaticAnchorFocus").get(screen) == null && tree.selectedTarget() == null,
                    "disabling an automatic instance clears dynamic helper focus");
        }
        draft.remove(id);
        resources.remove(entry.reference());
        draft.select(previousSelection);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        System.out.println("PASS automatic GUI focus: generated Trail, actual controller target, owned tree selection, "
                + "unchanged preparation and disabled cleanup; controlled pose, GUI MANUAL/PENDING");
    }

    private static void testBoneOverrideEditor(Unsafe unsafe, top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                               top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                               UUID previousSelection, Runnable drain) throws Exception {
        var type = screen.getClass();
        var preview = (LibraryPreviewSession) field(type, "preview").get(screen);
        var root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "bone-editor-");
        var directory = top.aihmc.omnimorph.wizards.CosmeticCreator.create(root, "发饰", "Fixture", "",
                top.aihmc.omnimorph.wizards.CosmeticTemplate.HEAD, "head_anchor",
                top.aihmc.omnimorph.model.outfit.OutfitConfig.Transform.identity());
        var source = top.aihmc.omnimorph.model.outfit.LocalCosmeticSource.describe(directory, () -> false);
        var entry = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(directory, UUID.randomUUID(), source, "");
        @SuppressWarnings("unchecked")
        var resources = (Map<top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef,
                top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry>) field(type, "draftResources").get(screen);
        resources.put(entry.reference(), entry);
        var authored = Files.readString(directory.resolve("cosmetic.json"));
        var geometry = Files.readString(directory.resolve("model.bbmodel"));
        UUID first = draft.add(entry.reference(), source.metadata().defaultAnchor(), source.metadata().defaultTransform());
        UUID second = draft.add(entry.reference(), source.metadata().defaultAnchor(), source.metadata().defaultTransform());
        var refresh = type.getDeclaredMethod("previewOutfitDraft");
        refresh.setAccessible(true);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        testTreeViewSelector(screen, preview, first);
        var bone = preview.outfitHierarchy().targets().values().stream()
                .filter(target -> first.equals(target.instanceId()) && target.editable() && target.bone() != null)
                .findFirst().orElseThrow().bone();
        var edit = type.getDeclaredMethod("editBoneOverride", bone.getClass());
        edit.setAccessible(true);
        int boneId = preview.outfitHierarchy().targets().entrySet().stream()
                .filter(node -> bone.equals(node.getValue().bone())).findFirst().orElseThrow().getKey();
        Object selectedPart = preview.outfitHierarchy().hierarchy().part(boneId);
        var selectedFrame = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
        var emptyFrame = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
        preview.selectedBone(selectedPart);
        preview.withPlayerContext(() -> LibraryPreviewBinding.capture(preview.entity(), selectedFrame));
        preview.selectedBone(null);
        preview.withPlayerContext(() -> LibraryPreviewBinding.capture(preview.entity(), emptyFrame));
        boolean previousPaperdoll = top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll;
        top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = true;
        try {
            LibraryPreviewBinding.composition(selectedFrame).withPreviewSelection(() -> require(
                    PreviewBoneHighlight.selected(null, selectedPart),
                    "native preview binding captures the selected accessory part before deferring the draw"));
            LibraryPreviewBinding.composition(emptyFrame).withPreviewSelection(() -> require(
                    !PreviewBoneHighlight.selected(null, selectedPart),
                    "later empty preview selection leaves an earlier retained frame intact"));
        } finally {
            top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = previousPaperdoll;
        }
        var baseBone = preview.outfitHierarchy().targets().values().stream()
                .filter(target -> target.kind() == OutfitHierarchy.Kind.BASE_BONE).findFirst().orElseThrow().bone();
        edit.invoke(screen, baseBone);
        require(field(type, "editingBone").get(screen) == null, "base bones never open the accessory override editor");
        edit.invoke(screen, bone);
        require(bone.equals(field(type, "editingBone").get(screen)) && first.equals(draft.selectedInstance()),
                "authorized internal bone selection retains its exact owning instance");
        testBoneHelperControls(unsafe, screen, draft, bone);
        field(type, "layout").set(screen, top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        var build = type.getDeclaredMethod("buildBoneOverrideEditor", bone.getClass());
        build.setAccessible(true);
        build.invoke(screen, bone);
        var panel = screen.children().stream().filter(top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel.class::cast).toList().getLast();
        var inputs = panel.children().stream().filter(top.aihmc.omnimorph.gui.widgets.TextField.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.TextField.class::cast).toList();
        require(inputs.size() == 9, "internal bone editor exposes position, rotation and scale components");
        inputs.getFirst().getField().setValue("3");
        inputs.get(4).getField().setValue("30");
        inputs.get(8).getField().setValue("1.25");
        var override = draft.instance(first).boneOverrides().getFirst();
        require(override.bone().equals(bone) && override.transform().position().x() == 3
                        && override.transform().rotation().y() == 30 && override.transform().scale().z() == 1.25
                        && draft.instance(second).boneOverrides().isEmpty(),
                "actual bone fields edit one stable reference and preserve the same resource's sibling");
        var valid = draft.configuration();
        inputs.get(6).getField().setValue("NaN");
        require(draft.configuration().equals(valid) && inputs.get(6).getBorderColour() == 0xffff5555,
                "nonfinite bone values cannot enter the draft");
        awaitDraftComposition(screen, preview, drain);
        require(preview.outfitHierarchy().targets().values().stream().anyMatch(target -> bone.equals(target.bone()) && target.editable()),
                "native composition preparation accepts the authored editable range and republishes its bone");
        draft.select(second);
        inputs.getFirst().getField().setValue("9");
        require(draft.configuration().equals(valid), "old bone controls cannot edit a different selected copy");
        draft.select(first);
        draft.setEnabled(first, false);
        var disabled = draft.configuration();
        inputs.getFirst().getField().setValue("10");
        require(draft.configuration().equals(disabled), "disabled instances reject old bone field callbacks");
        draft.setEnabled(first, true);
        var resetLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.bone_reset").getString();
        var reset = panel.children().stream().filter(top.aihmc.omnimorph.gui.widgets.Button.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(resetLabel)).findFirst().orElseThrow();
        reset.onPress(null);
        require(draft.instance(first).boneOverrides().isEmpty() && draft.instance(second).boneOverrides().isEmpty()
                        && inputs.getFirst().getField().getValue().equals("0.0") && inputs.get(8).getField().getValue().equals("1.0"),
                "Reset removes the override and restores fields without recreating identity records");
        draft.remove(first);
        draft.remove(second);
        var removed = draft.configuration();
        inputs.getFirst().getField().setValue("11");
        reset.onPress(null);
        require(draft.configuration().equals(removed) && Files.readString(directory.resolve("cosmetic.json")).equals(authored)
                        && Files.readString(directory.resolve("model.bbmodel")).equals(geometry),
                "removed bones reject old input/reset and author metadata/geometry stay unchanged");
        resources.remove(entry.reference());
        field(type, "editingBone").set(screen, null);
        field(type, "layout").set(screen, null);
        draft.select(previousSelection);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        System.out.println("PASS internal bone editor: native generated package, independent instances, actual fields, "
                + "authored bounds, reset, disabled/removed/stale controls and unchanged sources; GUI MANUAL/PENDING");
    }

    private static void testBoneHelperControls(Unsafe unsafe, top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                               top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                               top.aihmc.omnimorph.model.outfit.OutfitConfig.BoneRef bone) throws Exception {
        var type = screen.getClass();
        var preview = (LibraryPreviewSession) field(type, "preview").get(screen);
        var tree = (BoneTreeState) field(type, "bones").get(screen);
        require(tree.locate(new top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorRef(
                top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.ACCESSORY_BONE, null, bone, null, null, null)),
                "helper control fixture selects the actual enabled internal bone");
        var selection = type.getDeclaredMethod("previewBoneSelection");
        selection.setAccessible(true);
        Object part = selection.invoke(screen);
        require(part != null, "enabled helpers expose the selected native part");
        var widget = new top.aihmc.omnimorph.gui.widgets.EntityPreview(0, 0, 160, 160, 12, -15, 30,
                Minecraft.getInstance().player, screen);
        widget.setLibraryPreview(preview);
        widget.setSelectedBone(part);
        Object previousWidget = field(type, "previewWidget").get(screen);
        field(type, "previewWidget").set(screen, widget);
        var minecraft = Minecraft.getInstance();
        var previousScreen = minecraft.screen;
        var windowField = field(Minecraft.class, "window");
        Object previousWindow = windowField.get(minecraft);
        var viewport = (com.mojang.blaze3d.platform.Window) unsafe.allocateInstance(com.mojang.blaze3d.platform.Window.class);
        field(viewport.getClass(), "guiScaledWidth").setInt(viewport, 640);
        field(viewport.getClass(), "guiScaledHeight").setInt(viewport, 360);
        windowField.set(minecraft, viewport);
        minecraft.screen = screen;
        try {
            var configuration = draft.configuration();
            long preparation = field(type, "draftPreviewGeneration").getLong(screen);
            var open = type.getDeclaredMethod("openAnchorActions", net.minecraft.client.gui.components.Button.class);
            open.setAccessible(true);
            var trigger = new top.aihmc.omnimorph.gui.widgets.Button(0, 0, 120, 20,
                    net.minecraft.network.chat.Component.literal("Actions"), null, ignored -> {});
            open.invoke(screen, trigger);
            var hideLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.helpers_hide").getString();
            var hide = top.aihmc.omnimorph.utils.ui.UIHelper.getContext().getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(hideLabel))
                    .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast).findFirst().orElseThrow();
            hide.onPress(null);
            var binding = field(LibraryPreviewSession.class, "binding").get(preview);
            require(selection.invoke(screen) == null && field(widget.getClass(), "selectedBone").get(widget) == null
                            && field(LibraryPreviewBinding.class, "selectedBone").get(binding) == null
                            && tree.selectedTarget().bone().equals(bone)
                            && !top.aihmc.omnimorph.utils.ui.UIHelper.getContext().isVisible(),
                    "actual Hide menu clears widget/binding helpers while retaining the editor's bone selection");
            open.invoke(screen, trigger);
            var showLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.helpers_show").getString();
            var show = top.aihmc.omnimorph.utils.ui.UIHelper.getContext().getEntries().stream()
                    .filter(button -> button.getMessage().getString().equals(showLabel))
                    .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast).findFirst().orElseThrow();
            show.onPress(null);
            require(selection.invoke(screen) == part && field(widget.getClass(), "selectedBone").get(widget) == part
                            && field(LibraryPreviewBinding.class, "selectedBone").get(binding) == part
                            && configuration.equals(draft.configuration())
                            && field(type, "draftPreviewGeneration").getLong(screen) == preparation,
                    "actual Show menu restores only preview helpers without author/configuration changes or model preparation");
            draft.setEnabled(bone.instanceId(), false);
            require(selection.invoke(screen) == null, "disabled bones stop contributing helpers before the next composition publishes");
            draft.setEnabled(bone.instanceId(), true);
        } finally {
            screen.contextMenu = null;
            minecraft.screen = previousScreen;
            windowField.set(minecraft, previousWindow);
            field(type, "previewWidget").set(screen, previousWidget);
        }
        System.out.println("PASS helper visibility menu: native Show/Hide callbacks, widget/binding cleanup, "
                + "retained selection, disabled guard and unchanged draft/preparation; GUI MANUAL/PENDING");
    }

    private static void testTreeViewSelector(top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                             LibraryPreviewSession preview, UUID instance) throws Exception {
        var type = screen.getClass();
        field(type, "layout").set(screen, top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        var build = type.getDeclaredMethod("buildRightPane");
        build.setAccessible(true);
        build.invoke(screen);
        var selector = screen.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::cast).toList().getLast();
        var state = (BoneTreeState) field(type, "bones").get(screen);
        var choices = state.viewChoices();
        int instanceIndex = -1;
        int baseIndex = -1;
        for (int index = 0; index < choices.size(); index++) {
            if (instance.equals(choices.get(index).view().instance())) {
                instanceIndex = index;
            } else if (choices.get(index).view().kind() == OutfitHierarchy.Kind.BASE) {
                baseIndex = index;
            }
        }
        require(instanceIndex >= 0 && baseIndex >= 0, "native tree selector exposes actual base and enabled accessory views");
        long revision = preview.hierarchyRevision();
        long preparation = field(type, "draftPreviewGeneration").getLong(screen);
        var binding = field(LibraryPreviewSession.class, "binding").get(preview);
        var change = (java.util.function.IntConsumer) field(selector.getClass(), "changed").get(selector);
        change.accept(instanceIndex);
        state.search("Headwear");
        state.scroll(.4);
        change.accept(baseIndex);
        state.search("Head");
        change.accept(instanceIndex);
        var widget = screen.children().stream().filter(top.aihmc.omnimorph.gui.widgets.wardrobe.BoneTreeWidget.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.wardrobe.BoneTreeWidget.class::cast).toList().getLast();
        widget.tick();
        require(state.query().equals("Headwear") && state.scroll() == .4
                        && instance.equals(state.view().instance()) && state.rows().stream()
                        .allMatch(row -> instance.equals(preview.outfitHierarchy().target(row.node().id()).instanceId())),
                "actual view control and widget tick restore the accessory's own search/scroll without other instances");
        require(preview.hierarchyRevision() == revision && field(type, "draftPreviewGeneration").getLong(screen) == preparation
                        && field(LibraryPreviewSession.class, "binding").get(preview) == binding,
                "switching/searching trees neither rebuilds the runtime snapshot nor starts source preparation");
        state.view(BoneTreeState.View.ALL);
        field(type, "layout").set(screen, null);
        System.out.println("PASS native tree views: actual selector, per-instance search/scroll, widget restoration "
                + "and unchanged preparation/binding; GUI MANUAL/PENDING");
    }

    private static void testVirtualAnchorEditor(top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                                 top.aihmc.omnimorph.model.outfit.OutfitDraft draft,
                                                 UUID first, UUID sibling, Runnable drain) throws Exception {
        var type = screen.getClass();
        var preview = (LibraryPreviewSession) field(type, "preview").get(screen);
        awaitDraftComposition(screen, preview, drain);
        var parent = preview.outfitHierarchy().anchors(null).stream()
                .filter(choice -> choice.anchor().type() == top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.BASE_BONE)
                .findFirst().orElseThrow().anchor().bone();
        var create = type.getDeclaredMethod("createVirtualAnchor", parent.getClass());
        create.setAccessible(true);
        var deniedParent = new top.aihmc.omnimorph.model.outfit.OutfitConfig.BoneRef(first,
                parent.path(), parent.skeletonHash());
        var beforeCreate = draft.configuration();
        create.invoke(screen, deniedParent);
        require(draft.configuration().equals(beforeCreate), "unavailable accessory bone references cannot parent virtual anchors");
        create.invoke(screen, parent);
        UUID id = (UUID) field(type, "editingVirtualAnchor").get(screen);
        require(id != null && draft.configuration().virtualAnchors().size() == 1,
                "production Create makes an independent virtual anchor in the draft only");
        awaitDraftComposition(screen, preview, drain);
        var anchor = draft.configuration().virtualAnchors().getFirst();
        field(type, "layout").set(screen, top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        var build = type.getDeclaredMethod("buildVirtualAnchorEditor", anchor.getClass());
        build.setAccessible(true);
        build.invoke(screen, anchor);
        var panel = screen.children().stream().filter(top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel.class::cast).toList().getLast();
        var inputs = panel.children().stream().filter(top.aihmc.omnimorph.gui.widgets.TextField.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.TextField.class::cast).toList();
        require(inputs.size() == 10, "virtual anchor editor exposes name and nine local transform components");
        var parentSelector = panel.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::cast).findFirst().orElseThrow();
        var selectedParent = (java.util.function.IntSupplier) field(parentSelector.getClass(), "selected").get(parentSelector);
        var chooseParent = (java.util.function.IntConsumer) field(parentSelector.getClass(), "changed").get(parentSelector);
        int replacement = selectedParent.getAsInt() == 0 ? 1 : 0;
        chooseParent.accept(replacement);
        require(selectedParent.getAsInt() == replacement
                        && !draft.configuration().virtualAnchors().getFirst().parent().equals(parent),
                "native parent selector changes the stable parent reference without nesting a virtual node");
        inputs.getFirst().getField().setValue("共享尾尖");
        inputs.get(1).getField().setValue("4");
        var current = draft.configuration().virtualAnchors().getFirst();
        require(current.name().equals("共享尾尖") && current.transform().position().x() == 4,
                "native field responders rename and move the virtual anchor");
        var valid = draft.configuration();
        inputs.getFirst().getField().setValue("");
        inputs.get(7).getField().setValue("0");
        require(draft.configuration().equals(valid) && inputs.getFirst().getBorderColour() == 0xffff5555
                        && inputs.get(7).getBorderColour() == 0xffff5555,
                "invalid names and scales preserve the last valid anchor and mark their inputs");
        inputs.getFirst().getField().setValue("共享尾尖");
        inputs.get(7).getField().setValue("1");
        var originalFirst = draft.instance(first);
        var originalSibling = draft.instance(sibling);
        var shared = new top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorRef(
                top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.VIRTUAL, null, null, id, null, null);
        draft.setAnchor(first, shared);
        draft.setAnchor(sibling, shared);
        var removeLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.anchor_remove").getString();
        var remove = panel.children().stream().filter(top.aihmc.omnimorph.gui.widgets.Button.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(removeLabel)).findFirst().orElseThrow();
        field(type, "layout").set(screen, null);
        remove.onPress(null);
        require(draft.configuration().virtualAnchors().size() == 1
                        && ((net.minecraft.network.chat.Component) field(type, "message").get(screen)).getString().contains("#1")
                        && ((net.minecraft.network.chat.Component) field(type, "message").get(screen)).getString().contains("#2"),
                "first Delete lists both affected copies and retains the shared anchor");
        draft.setEnabled(first, !originalFirst.enabled());
        remove.onPress(null);
        require(draft.configuration().virtualAnchors().size() == 1,
                "changing the draft invalidates the prior deletion confirmation");
        remove.onPress(null);
        require(draft.configuration().virtualAnchors().isEmpty()
                        && draft.instance(first).anchor().type() == top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.UNRESOLVED
                        && draft.instance(sibling).anchor().type() == top.aihmc.omnimorph.model.outfit.OutfitConfig.AnchorType.UNRESOLVED,
                "confirmed Delete removes the anchor and explicitly unbinds all users without silent fallback");
        var deleted = draft.configuration();
        inputs.getFirst().getField().setValue("obsolete callback");
        require(draft.configuration().equals(deleted), "retired anchor fields cannot resurrect deleted state");
        draft.setEnabled(first, originalFirst.enabled());
        draft.setAnchor(first, originalFirst.anchor());
        draft.setAnchor(sibling, originalSibling.anchor());
        var refresh = type.getDeclaredMethod("previewOutfitDraft");
        refresh.setAccessible(true);
        refresh.invoke(screen);
        awaitDraftComposition(screen, preview, drain);
        System.out.println("PASS virtual anchor native editor: create, name/transform fields, shared dependents, "
                + "invalid input, generation-fenced confirmation and explicit unbinding; GUI MANUAL/PENDING");
    }

    private static void awaitDraftComposition(top.aihmc.omnimorph.gui.screens.WardrobeScreen screen,
                                               LibraryPreviewSession preview, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            drain.run();
            Object binding = field(LibraryPreviewSession.class, "binding").get(preview);
            if (binding != null) {
                Object composition = field(LibraryPreviewBinding.class, "composition").get(binding);
                var draft = (top.aihmc.omnimorph.model.outfit.OutfitDraft) field(
                        top.aihmc.omnimorph.gui.screens.WardrobeScreen.class, "outfitDraft").get(screen);
                if (composition != null && field(composition.getClass(), "configuration").get(composition)
                        .equals(draft.configuration())) {
                    return;
                }
            }
            Thread.sleep(1);
        }
        throw new AssertionError("The current draft composition did not become ready: " + preview.status());
    }

    private static final class OtherPlayerLevel extends net.minecraft.client.multiplayer.ClientLevel {
        private net.minecraft.world.entity.player.Player target;

        private OtherPlayerLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public net.minecraft.world.entity.player.Player getPlayerByUUID(UUID player) {
            return target != null && player.equals(target.getUUID()) ? target : null;
        }
    }

    private static void testCurrentPlayerFigura(Unsafe unsafe, Runnable drain, Path encrypted) throws Exception {
        Minecraft client = Minecraft.getInstance();
        var previousPlayer = client.player;
        var player = (CurrentPreviewPlayer) unsafe.allocateInstance(CurrentPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        player.tickCount = 42;
        client.player = player;
        var packs = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        var controls = field(top.aihmc.omnimorph.avatar.control.AvatarControlValueStore.class, "loaded");
        boolean controlsLoaded = controls.getBoolean(null);
        try (var preview = new LibraryPreviewSession()) {
            field(LibraryPreviewSession.class, "entity").set(preview, player);
            var source = model("current player Figura");
            var scripts = new CompoundTag();
            scripts.putByteArray("script", """
                    function events.tick()
                        models:setRot(0, models:getRot().y + 5, 0)
                    end
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            source.put("scripts", scripts);
            require(await(preview.selectDraft(source), drain), "current-player Figura preview loads");
            require(preview.entity() == player, "normal Figura previews use the actual current player");
            var active = new java.util.concurrent.atomic.AtomicReference<Avatar>();
            var frame = new AvatarRenderState();
            preview.withPlayerContext(() -> {
                active.set(AvatarManager.getAvatar(player));
                LibraryPreviewBinding.capture(player, frame);
            });
            Avatar avatar = active.get();
            require(avatar != null && avatar.isHost && !avatar.isLibraryPreview
                            && avatar.permissions == PermissionManager.get(player.getUUID())
                            && avatar.luaRuntime.getUser() == player,
                    "normal preview retains Figura's local host runtime, permissions and real player API");
            require(LibraryPreviewBinding.avatar(player) == null && LibraryPreviewBinding.avatar(player.getUUID()) == null,
                    "preview lookup cannot replace world equipment outside the GUI scope");
            preview.tick();
            require(player.tickCount == 42 && avatar.renderer.root.getTrueRot().y == 5,
                    "preview scripts tick without advancing the actual player's entity clock");
            require(AvatarManager.getEntity(frame) == player && AvatarManager.getAvatar(frame) == avatar,
                    "deferred native Figura render uses the same real player and selected model");
            preview.clear();
            require(avatar.accelerationOwner().isCurrent(), "queued player preview remains alive until GUI completion");
            LibraryPreviewBinding.finishFrame();
            require(!avatar.accelerationOwner().isCurrent(), "closing player preview retires its own model resources");
            var outfit = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
            field(outfit.getClass(), "preview").set(outfit, preview);
            var widget = new top.aihmc.omnimorph.gui.widgets.EntityPreview(0, 0, 180, 180, 40, -15, 30, player, outfit);
            field(outfit.getClass(), "previewWidget").set(outfit, widget);
            Path selectedDirectory = Files.createDirectories(Path.of(System.getProperty("figura.test.directory")).resolve("outfit-selected"));
            Files.writeString(selectedDirectory.resolve("avatar.json"), "{\"name\":\"selected outfit fixture\"}");
            var selectLocal = outfit.getClass().getDeclaredMethod("selectLocal",
                    top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath.class);
            selectLocal.setAccessible(true);
            Avatar worldAvatar = AvatarManager.getAvatar(player);
            selectLocal.invoke(outfit, new top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath(selectedDirectory, selectedDirectory));
            await((CompletableFuture<?>) field(outfit.getClass(), "changingDraftBase").get(outfit), drain);
            awaitDraftComposition(outfit, preview, drain);
            require(preview.status().stage() == LibraryPreviewSession.Stage.READY && preview.entity() == player,
                    "outfit selection prepares a model through Figura's original real-player preview path");
            require(field(widget.getClass(), "libraryPreview").get(widget) == preview
                            && AvatarManager.getAvatar(player) == worldAvatar,
                    "outfit widget displays the selection without replacing world equipment");
            var syncHierarchy = outfit.getClass().getDeclaredMethod("syncHierarchy");
            syncHierarchy.setAccessible(true);
            syncHierarchy.invoke(outfit);
            require(field(outfit.getClass(), "boneSession").get(outfit) == preview,
                    "outfit bones follow the selected preview rather than the equipped avatar");
            // Supply the preview entity normally constructed from ClientLevel; this harness has no level.
            var ysmEntity = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
            ysmEntity.setUUID((UUID) field(LibraryPreviewSession.class, "owner").get(preview));
            field(LibraryPreviewSession.class, "detachedEntity").set(preview, ysmEntity);
            selectLocal.invoke(outfit, new top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath(encrypted, encrypted.getParent()));
            await((CompletableFuture<?>) field(outfit.getClass(), "changingDraftBase").get(outfit), drain);
            awaitDraftComposition(outfit, preview, drain);
            var ysm = new java.util.concurrent.atomic.AtomicReference<Avatar>();
            preview.withPlayerContext(() -> ysm.set(AvatarManager.getAvatar(preview.entity())));
            require(preview.status().stage() == LibraryPreviewSession.Stage.READY,
                    "encrypted YSM outfit preview finishes loading: " + preview.status());
            require(ysm.get() != null && ysm.get().isYsmNative(),
                    "the outfit entry handler resolves the actual selected YSM runtime");
            require(AvatarManager.getAvatar(player) == worldAvatar,
                    "encrypted YSM preview does not replace world equipment");
            selectLocal.invoke(outfit, new Object[]{null});
            require(field(widget.getClass(), "libraryPreview").get(widget) == null,
                    "clearing the candidate restores the original equipped-player preview");
            System.out.println("PASS outfit entry preview: actual Figura/encrypted YSM loading, widget binding, matching bones and unchanged equipment");
            System.out.println("PASS current-player Figura preview: real player, normal host permissions, Lua tick, scoped GUI lookup and deferred lifetime");
        } finally {
            client.player = previousPlayer;
            packs.remove(player.getUUID());
            controls.setBoolean(null, controlsLoaded);
        }
    }

    private static final class CurrentPreviewPlayer extends net.minecraft.client.player.LocalPlayer
            implements top.aihmc.omnimorph.mixin.EntityAccessor {
        private CurrentPreviewPlayer() {
            super(null, null, null, null, null, null, false, null);
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return Minecraft.getInstance().level;
        }
    }

    private static void assertVanillaBody(Avatar avatar, boolean visible) {
        var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(
                net.minecraft.client.model.geom.builders.CubeDeformation.NONE, false);
        var root = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot();
        var model = new net.minecraft.client.model.player.PlayerModel(root, false);
        if (avatar.luaRuntime != null) {
            avatar.luaRuntime.vanilla_model.PLAYER.save(model);
            avatar.luaRuntime.vanilla_model.PLAYER.posTransform(model);
        }
        for (var part : java.util.List.of(model.head, model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg, model.hat, model.jacket, model.leftSleeve,
                model.rightSleeve, model.leftPants, model.rightPants)) {
            require(part.visible == visible, "native player body and skin layers follow the Figura script's visibility");
        }
        if (avatar.luaRuntime != null) {
            avatar.luaRuntime.vanilla_model.PLAYER.restore(model);
        }
    }

    private static void testIndependentFigura(Unsafe unsafe, Runnable drain) throws Exception {
        Minecraft client = Minecraft.getInstance();
        var oldRenderer = client.gameRenderer;
        var oldDebug = client.debugEntries;
        var renderer = (net.minecraft.client.renderer.GameRenderer) unsafe.allocateInstance(
                net.minecraft.client.renderer.GameRenderer.class);
        field(renderer.getClass(), "mainCamera").set(renderer, new net.minecraft.client.Camera());
        var debug = (net.minecraft.client.gui.components.debug.DebugScreenEntryList) unsafe.allocateInstance(
                net.minecraft.client.gui.components.debug.DebugScreenEntryList.class);
        field(debug.getClass(), "allStatuses").set(debug, new java.util.HashMap<>());
        field(debug.getClass(), "currentlyEnabled").set(debug, new java.util.ArrayList<>());
        field(Minecraft.class, "gameRenderer").set(client, renderer);
        field(Minecraft.class, "debugEntries").set(client, debug);
        try (var session = new LibraryPreviewSession(null, true)) {
            var player = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
            player.setUUID((UUID) field(LibraryPreviewSession.class, "owner").get(session));
            field(LibraryPreviewSession.class, "entity").set(session, player);
            var nbt = model("direct Figura preview");
            var scripts = new CompoundTag();
            scripts.putByteArray("script", """
                    local count = 0
                    function events.render()
                        count = count + 1
                        models:setRot(0, count * 5, 0)
                    end
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            nbt.put("scripts", scripts);
            require(await(session.selectDraft(nbt), drain), "direct Figura fixture loads actual scripts");
            Avatar avatar = AvatarManager.getAvatar(player);
            require(!session.submitIndependentFigura(null, 50, 50, 30, -15, 30, 0.5f, 0, 0, 100, 100),
                    "model library sessions default to Figura's original entity preview path");
            session.setIndependentFigura(true);
            var guiState = new net.minecraft.client.renderer.state.gui.GuiRenderState();
            var gui = (PreviewGraphics) unsafe.allocateInstance(PreviewGraphics.class);
            gui.state = guiState;
            var scissorConstructor = net.minecraft.client.gui.GuiGraphicsExtractor.ScissorStack.class.getDeclaredConstructor();
            scissorConstructor.setAccessible(true);
            gui.scissor = scissorConstructor.newInstance();
            require(session.submitIndependentFigura(gui, 50, 50, 30, -15, 30, 0.5f, 0, 0, 100, 100),
                    "wardrobe management explicitly submits an independent model state");
            var captured = new java.util.ArrayList<net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState>();
            guiState.forEachPictureInPicture(captured::add);
            require(captured.size() == 1 && captured.getFirst() instanceof LibraryFiguraPreviewState,
                    "independent GUI submission bypasses native entity render states");
            session.setIndependentFigura(false);
            require(!session.submitIndependentFigura(null, 50, 50, 30, -15, 30, 0.5f, 0, 0, 100, 100),
                    "leaving wardrobe management restores the original preview path");
            var pose = new AvatarRenderState();
            pose.skin = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
            pose.showHat = pose.showJacket = pose.showLeftSleeve = pose.showRightSleeve = true;
            pose.showLeftPants = pose.showRightPants = true;
            var state = new LibraryFiguraPreviewState(avatar, pose, 0.5f, -15, 30, 0, 0,
                    0, 0, 100, 100, 30, null, null);
            var vertices = new java.util.ArrayList<String>();
            var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    LibraryPreviewRuntimeSmokeTest.class.getClassLoader(),
                    new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("addVertex")) {
                            vertices.add(java.util.Arrays.toString(args));
                        }
                        return method.getReturnType() == void.class ? null : proxy;
                    });
            var model = LibraryFiguraPreviewRenderer.createModel(false);
            session.beforeExtract(0.5f);
            LibraryFiguraPreviewRenderer.draw(state, new com.mojang.blaze3d.vertex.PoseStack(), type -> consumer, model);
            require(!vertices.isEmpty(), "direct preview emits native skinned player vertices without a level or dispatcher");
            require(avatar.renderer.root.getTrueRot().y == 5 && !avatar.scriptError,
                    "direct geometry drawing invokes the real Lua render event");
            require(player.equipmentReads == 0 && player.capeReads == 0,
                    "direct preview never enters entity armor/cape layers");
            avatar.loadScript("hide body", "vanilla_model.PLAYER:setVisible(false)").call();
            vertices.clear();
            session.beforeExtract(0.5f);
            LibraryFiguraPreviewRenderer.draw(state, new com.mojang.blaze3d.vertex.PoseStack(), type -> consumer, model);
            require(vertices.isEmpty() && avatar.renderer.root.getTrueRot().y == 10,
                    "script body visibility remains authoritative while render animation continues");
            require(model.body.visible, "shared native mesh visibility is restored after drawing");
            vertices.clear();
            boolean previousPaperdoll = top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll;
            var previousMode = avatar.renderMode;
            var part = avatar.renderer.root;
            try {
                PreviewBoneHighlight.capture(avatar, part);
                top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = false;
                require(!PreviewBoneHighlight.selected(avatar, part), "bone feedback cannot affect world rendering");
                top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = true;
                avatar.renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
                require(PreviewBoneHighlight.selected(avatar, part), "the GUI targets the selected part identity");
                PreviewBoneHighlight.figura(part, new top.aihmc.omnimorph.model.PartCustomization(), type -> consumer);
                int figuraLines = vertices.size();
                require(figuraLines == 24, "empty Figura groups receive a visible pivot box without model geometry edits");
                var bone = new top.aihmc.omnimorph.model.ysm.YsmGeometry.Bone("selected", null, new float[3], new float[3]);
                PreviewBoneHighlight.ysm(bone, new com.mojang.blaze3d.vertex.PoseStack(), type -> consumer);
                require(vertices.size() == figuraLines + 24 && bone.cubes.isEmpty(),
                        "YSM bone feedback emits an overlay without adding cubes to the source model");
                PreviewBoneHighlight.finishFrame();
                require(!PreviewBoneHighlight.selected(avatar, part), "selection feedback cannot leak into later frames");
            } finally {
                PreviewBoneHighlight.finishFrame();
                top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = previousPaperdoll;
                avatar.renderMode = previousMode;
            }
            top.aihmc.omnimorph.model.outfit.NativeSkeletonSmokeTest.testFigura(avatar);
            top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.run();
            testCompositionPreparation(session, drain, gui);
            System.out.println("PASS independent Figura drawing: real Lua render, native body vertices, script visibility and no entity layers");
        } finally {
            field(Minecraft.class, "gameRenderer").set(client, oldRenderer);
            field(Minecraft.class, "debugEntries").set(client, oldDebug);
        }
    }

    private static void testCompositionPreparation(LibraryPreviewSession session, Runnable drain,
                                                    PreviewGraphics gui) throws Exception {
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "8".repeat(64), OutfitConfig.Format.FIGURA);
        var declaration = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("unknown"), OutfitConfig.Transform.identity(),
                java.util.List.of(), Map.of(), Map.of());
        var baseResource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "7".repeat(64), OutfitConfig.Format.FIGURA);
        var configuration = new OutfitConfig(1, 0, baseResource, java.util.List.of(declaration), java.util.List.of(),
                Map.of(resource.identity(), 1), false);
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new top.aihmc.omnimorph.permissions.PermissionPack.PlayerPermissionPack(
                new top.aihmc.omnimorph.permissions.PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT),
                account.toString());
        var source = model("prepared accessory");
        source.getCompoundOrEmpty("metadata").putString("resource_kind", "ACCESSORY");
        require(await(session.prepareComposition(configuration, OutfitLimits.defaults(), account, outfit, permissions,
                (reference, cancelled) -> source), drain), "asynchronous draft preparation publishes a complete generation");
        var entity = session.entity();
        Avatar nativeAvatar = AvatarManager.getAvatar(entity);
        var nativeRoot = nativeAvatar.renderer.root;
        double submittedX = nativeRoot.customization.getPos().x;
        nativeRoot.preRender = nativeAvatar.loadScript("observe submitted base pose", """
                return function()
                    observed_base_x = models:getPos().x
                end
                """).call().checkfunction();
        session.setIndependentFigura(true);
        gui.state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
        require(session.submitIndependentFigura(gui, 50, 50, 30, 0, -180, .5f, 0, 0, 100, 100),
                "actual independent GUI submission captures a composed base pose");
        var submitted = new java.util.ArrayList<net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState>();
        gui.state.forEachPictureInPicture(submitted::add);
        var nativeState = (LibraryFiguraPreviewState) submitted.getFirst();
        var frame = nativeState.playerPose();
        var previous = LibraryPreviewBinding.composition(frame);
        require(previous != null, "native preview extraction retains the published composition");
        nativeRoot.customization.setPos(123, 456, 789);
        var emissions = new java.util.concurrent.atomic.AtomicInteger();
        var sink = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                LibraryPreviewRuntimeSmokeTest.class.getClassLoader(),
                new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    if (method.getName().equals("addVertex")) {
                        emissions.incrementAndGet();
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
        LibraryFiguraPreviewRenderer.draw(nativeState, new com.mojang.blaze3d.vertex.PoseStack(), type -> sink,
                LibraryFiguraPreviewRenderer.createModel(false));
        require(emissions.get() == 0, "hidden base and unresolved accessory do not emit geometry");
        require(nativeAvatar.loadScript("observed submitted base", "return observed_base_x").call().checkdouble() == submittedX
                        && nativeRoot.customization.getPos().x == 123,
                "actual GUI draw uses submission-time base pose and restores later edits after all callbacks");
        require(field(previous.getClass(), "evaluated").getBoolean(previous),
                "the existing GUI draw path captures the native base pose and evaluates its retained composition");
        var failed = session.prepareComposition(configuration, OutfitLimits.defaults(), account, outfit, permissions,
                (reference, cancelled) -> model("wrong purpose"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!failed.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(5);
        }
        require(failed.isCompletedExceptionally(), "failed resource preparation reports an error");
        var binding = field(LibraryPreviewSession.class, "binding").get(session);
        Object installed = field(LibraryPreviewBinding.class, "composition").get(binding);
        require(installed != null, "preparation failure preserves the visible combination");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var gate = AvatarLoadExecutor.submit(() -> {
            entered.countDown();
            try {
                require(release.await(5, TimeUnit.SECONDS), "composition worker gate is bounded");
            } catch (InterruptedException failure) {
                throw new AssertionError(failure);
            }
            return true;
        });
        try {
            require(entered.await(5, TimeUnit.SECONDS), "actual worker is occupied before rapid selection");
            var staleRead = new AtomicBoolean();
            var stale = session.prepareComposition(configuration, OutfitLimits.defaults(), account, outfit, permissions,
                    (reference, cancelled) -> {
                        staleRead.set(true);
                        return source;
                    });
            var latest = session.prepareComposition(configuration, OutfitLimits.defaults(), account, outfit, permissions,
                    (reference, cancelled) -> source);
            require(stale.isCancelled(), "newer draft cancels the pending result");
            release.countDown();
            require(await(latest, drain) && !staleRead.get(), "retired generation never reads or publishes its resources");
            require(field(LibraryPreviewBinding.class, "composition").get(binding) != installed,
                    "only the newest complete combination replaces the old one");
            require(LibraryPreviewBinding.composition(frame) == previous,
                    "already extracted preview frames retain their original generation");
        } finally {
            release.countDown();
            await(gate, drain);
            session.setComposition(null);
            LibraryPreviewBinding.finishFrame();
        }
        System.out.println("PASS asynchronous outfit preview: purpose validation, atomic replacement, newest draft, retained native frames and cleanup");
    }

    private static final class PreviewGraphics extends net.minecraft.client.gui.GuiGraphicsExtractor
            implements top.aihmc.omnimorph.mixin.gui.GuiGraphicsAccessor {
        private net.minecraft.client.renderer.state.gui.GuiRenderState state;
        private ScissorStack scissor;

        private PreviewGraphics(net.minecraft.client.renderer.state.gui.GuiRenderState state) {
            super(Minecraft.getInstance(), state, 100, 100);
        }

        @Override
        public void nextStratum() {
            state.nextStratum();
        }

        @Override
        public net.minecraft.client.renderer.state.gui.GuiRenderState figura$getRenderState() {
            return state;
        }

        @Override
        public ScissorStack figura$getScissorStack() {
            return scissor;
        }
    }

    private static void testPreviewAppearance(Unsafe unsafe) throws Exception {
        var source = (AppearancePlayer) unsafe.allocateInstance(AppearancePlayer.class);
        source.skin = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
        source.layers = java.util.EnumSet.allOf(net.minecraft.world.entity.player.PlayerModelPart.class);
        var reference = new java.util.concurrent.atomic.AtomicReference<AppearancePlayer>(source);
        var player = (LibraryPreviewPlayer) unsafe.allocateInstance(LibraryPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        field(LibraryPreviewPlayer.class, "appearancePlayer").set(player,
                (java.util.function.Supplier<AppearancePlayer>) reference::get);
        require(player.getSkin() == source.skin, "isolated player uses the current player's native loaded skin");
        for (var layer : net.minecraft.world.entity.player.PlayerModelPart.values()) {
            require(player.isModelPartShown(layer), "isolated player includes enabled skin layers");
        }
        source.layers.remove(net.minecraft.world.entity.player.PlayerModelPart.HAT);
        var previous = source.skin;
        source.skin = new net.minecraft.world.entity.player.PlayerSkin(previous.body(), previous.cape(), previous.elytra(),
                net.minecraft.world.entity.player.PlayerModelType.SLIM, previous.secure());
        require(player.getSkin() == source.skin && !player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT),
                "later skin loading, slim arms and skin-layer preference changes propagate to the preview");
        reference.set(null);
        require(player.getSkin() != null && player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.JACKET),
                "missing source player keeps a native default skin and visible skin layers");
        System.out.println("PASS isolated player appearance: native skin, live loading/model type, skin layers and fallback");
    }

    private static final class AppearancePlayer extends RemotePlayer {
        private net.minecraft.world.entity.player.PlayerSkin skin;
        private java.util.EnumSet<net.minecraft.world.entity.player.PlayerModelPart> layers;

        private AppearancePlayer() {
            super(null, new GameProfile(UUID.randomUUID(), "appearance"));
        }

        @Override
        public net.minecraft.world.entity.player.PlayerSkin getSkin() {
            return skin;
        }

        @Override
        public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) {
            return layers.contains(part);
        }
    }

    private static void testWardrobeDefaultPreview(Unsafe unsafe) throws Exception {
        var player = (ArmorPreviewPlayer) unsafe.allocateInstance(ArmorPreviewPlayer.class);
        player.setUUID(UUID.randomUUID());
        var wardrobe = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        wardrobe.width = 640;
        wardrobe.height = 360;
        field(wardrobe.getClass(), "layout").set(wardrobe,
                top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        try (var selectionPreview = new LibraryPreviewSession()) {
            field(wardrobe.getClass(), "preview").set(wardrobe, selectionPreview);
            var buildPreview = wardrobe.getClass().getDeclaredMethod("buildPreview");
            buildPreview.setAccessible(true);
            buildPreview.invoke(wardrobe);
            var actual = (top.aihmc.omnimorph.gui.widgets.EntityPreview) field(wardrobe.getClass(), "previewWidget").get(wardrobe);
            actual.tick();
            require(field(actual.getClass(), "libraryPreview").get(actual) == null
                            && field(actual.getClass(), "localPreview").get(actual) == null,
                    "the production outfit screen never attaches an isolated preview session");
            var choose = wardrobe.getClass().getDeclaredMethod("previewSelection");
            choose.setAccessible(true);
            choose.invoke(wardrobe);
            require(selectionPreview.status().stage() == LibraryPreviewSession.Stage.IDLE,
                    "outfit selection does not prepare a model copy or publish equipment");
        }
        var widget = new top.aihmc.omnimorph.gui.widgets.EntityPreview(0, 0, 200, 200,
                40, -15, 30, player, wardrobe);
        try {
            widget.tick();
            require(field(widget.getClass(), "localPreview").get(widget) == null
                            && field(widget.getClass(), "entity").get(widget) == player,
                    "wardrobe default preview must retain the actual player without loading an isolated copy");
            var expanded = new top.aihmc.omnimorph.gui.screens.AvatarScreen(40, -15, 30, player, wardrobe);
            expanded.width = 640;
            expanded.height = 360;
            var initialize = expanded.getClass().getDeclaredMethod("init");
            initialize.setAccessible(true);
            initialize.invoke(expanded);
            try {
                var full = (top.aihmc.omnimorph.gui.widgets.EntityPreview) expanded.children().stream()
                        .filter(top.aihmc.omnimorph.gui.widgets.EntityPreview.class::isInstance).findFirst().orElseThrow();
                full.tick();
                require(field(full.getClass(), "localPreview").get(full) == null
                                && field(full.getClass(), "entity").get(full) == player,
                        "expanded wardrobe preview uses the same actual player and model state");
            } finally {
                expanded.removed();
            }
            var controls = new top.aihmc.omnimorph.gui.screens.AvatarControlsScreen(wardrobe,
                    (Avatar) unsafe.allocateInstance(Avatar.class), "root");
            var settings = new top.aihmc.omnimorph.gui.widgets.EntityPreview(0, 0, 200, 200,
                    40, 0, 0, player, controls);
            try {
                settings.tick();
                require(field(settings.getClass(), "localPreview").get(settings) == null
                                && field(settings.getClass(), "entity").get(settings) == player,
                        "model settings must show the live player/model being edited instead of waiting for an unticked copy");
                var fullSettings = new top.aihmc.omnimorph.gui.screens.AvatarScreen(40, 0, 0, player, controls);
                fullSettings.width = 640;
                fullSettings.height = 360;
                initialize.invoke(fullSettings);
                try {
                    var full = (top.aihmc.omnimorph.gui.widgets.EntityPreview) fullSettings.children().stream()
                            .filter(top.aihmc.omnimorph.gui.widgets.EntityPreview.class::isInstance).findFirst().orElseThrow();
                    full.tick();
                    require(field(full.getClass(), "localPreview").get(full) == null
                                    && field(full.getClass(), "entity").get(full) == player,
                            "expanded model settings retain the live preview mechanism");
                } finally {
                    fullSettings.removed();
                }
            } finally {
                settings.closeLocalPreview();
            }
            System.out.println("PASS wardrobe and model settings preview: ordinary/expanded widgets retain the live player without copies");
        } finally {
            widget.closeLocalPreview();
        }
    }

    private static void testReportInput(LibraryClient client) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var fontField = field(Minecraft.class, "font");
        Object previousFont = fontField.get(minecraft);
        // Deterministic text metrics only; this test makes no font/pixel/IME-platform assertions.
        var font = new net.minecraft.client.gui.Font(null) {
            private final net.minecraft.client.StringSplitter splitter =
                    new net.minecraft.client.StringSplitter((codepoint, style) -> 6f);

            @Override
            public net.minecraft.client.StringSplitter getSplitter() {
                return splitter;
            }

            @Override
            public int width(String text) {
                return text.codePointCount(0, text.length()) * 6;
            }

            @Override
            public int width(net.minecraft.network.chat.FormattedText text) {
                return width(text.getString());
            }

            @Override
            public String plainSubstrByWidth(String text, int width) {
                return splitter.plainHeadByWidth(text, width, net.minecraft.network.chat.Style.EMPTY);
            }
        };
        fontField.set(minecraft, font);
        var screen = new top.aihmc.omnimorph.gui.screens.library.ModelReportScreen(null, client, UUID.randomUUID());
        try {
            screen.width = 640;
            screen.height = 360;
            var initialize = screen.getClass().getDeclaredMethod("init");
            initialize.setAccessible(true);
            initialize.invoke(screen);
            var reason = (top.aihmc.omnimorph.gui.widgets.library.LibraryMultilineField)
                    field(screen.getClass(), "reason").get(screen);
            var input = (net.minecraft.client.gui.components.MultiLineEditBox) reason.children().getFirst();
            // Apply the same child coordinates as extraction without rendering a GUI framebuffer.
            input.setX(reason.getX() + 2);
            input.setY(reason.getY() + 2);
            var click = new net.minecraft.client.input.MouseButtonEvent(reason.getX() + 8, reason.getY() + 8,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0));
            require(screen.mouseClicked(click, false), "report reason receives the actual screen click");
            require(screen.charTyped(new net.minecraft.client.input.CharacterEvent('举')),
                    "report keyboard routing and enabled state work before mouse release");
            screen.mouseReleased(click);
            require(screen.charTyped(new net.minecraft.client.input.CharacterEvent('报')),
                    "report reason accepts a character after a complete click and release");
            require(input.getValue().equals("举报") && field(screen.getClass(), "value").get(screen).equals("举报"),
                    "report input updates both native text and the submitted reason value");
            require(screen.keyPressed(new net.minecraft.client.input.KeyEvent(257, 0, 0))
                            && screen.charTyped(new net.minecraft.client.input.CharacterEvent('A'))
                            && screen.keyPressed(new net.minecraft.client.input.KeyEvent(259, 0, 0)),
                    "report reason keeps multiline editing and backspace after release");
            require(input.getValue().equals("举报\n"), "native multiline editing updates the report value");
            reason.setEnabled(false);
            require(!screen.charTyped(new net.minecraft.client.input.CharacterEvent('禁')),
                    "disabled report field does not accept characters");
            reason.setEnabled(true);
            require(screen.mouseClicked(click, false), "enabled report field can regain focus");
            screen.mouseReleased(click);
            screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(1, 1,
                    new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
            require(!screen.charTyped(new net.minecraft.client.input.CharacterEvent('外')),
                    "clicking outside still clears report editing focus");
            System.out.println("PASS report input: actual screen click/release, CJK text, newline/backspace, disabled state and outside focus");
            testCollectionCardsAndTreeScrolling();
            testResourceFilterPanels();
            testAllResourceSources(client);
            testWardrobeActionMenus();
        } finally {
            screen.removed();
            fontField.set(minecraft, previousFont);
        }
    }

    private static void testWardrobeVisibilityTicks(Unsafe unsafe) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var previousLevel = minecraft.level;
        UUID player = FiguraMod.getLocalPlayerUUID();
        UUID model = UUID.randomUUID();
        var permissions = (Map<?, ?>) field(PermissionManager.class, "PLAYERS").get(null);
        boolean hadPermissions = permissions.containsKey(player);
        @SuppressWarnings("unchecked")
        var users = (Map<UUID, top.aihmc.omnimorph.avatar.UserData>) field(AvatarManager.class, "LOADED_USERS").get(null);
        @SuppressWarnings("unchecked")
        var fetched = (java.util.Set<UUID>) field(AvatarManager.class, "FETCHED_USERS").get(null);
        var previousUser = users.get(player);
        boolean alreadyFetched = fetched.contains(player);
        var user = new top.aihmc.omnimorph.avatar.UserData(player);
        Avatar equipped = new Avatar(player);
        equipped.nbt = model("equipped");
        user.getAvatars().add(equipped);
        var callbacks = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        Runnable deliver = () -> {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) {
                callback.run();
            }
        };
        var showing = new AtomicBoolean(true);
        var publicModel = new AtomicBoolean();
        var revision = new java.util.concurrent.atomic.AtomicLong(7);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/library/v1/", exchange -> {
            try {
                String response;
                if (exchange.getRequestURI().getPath().endsWith("/session")) {
                    response = "{\"uuid\":\"" + player + "\",\"name\":\"Player\",\"admin\":false,"
                            + "\"equipment_revision\":" + revision.get() + ",\"equipment_model_id\":\"" + model
                            + "\",\"equipment_model_revision\":1,\"show_private_equipment\":"
                            + (!publicModel.get() && showing.get()) + ",\"show_equipment\":" + showing.get() + "}";
                } else {
                    require(exchange.getRequestMethod().equals("POST")
                                    && exchange.getRequestURI().getPath().equals("/api/library/v1/models/" + model + "/equip"),
                            "visibility targets actual equipment, never the unrelated selected preview");
                    var input = com.google.gson.JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    require(input.get("expected_equipment_revision").getAsLong() == revision.get(), "visibility retains equipment CAS");
                    showing.set(input.get("show_equipment").getAsBoolean());
                    response = "{\"model_id\":\"" + model + "\",\"model_revision\":1,\"equipment_revision\":"
                            + revision.incrementAndGet() + ",\"owner\":\"" + player + "\",\"asset_id\":\"lib_"
                            + model.toString().replace("-", "") + "_1\",\"asset_revision\":1,\"show_private_equipment\":"
                            + showing.get() + "}";
                }
                byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var access = new LibraryAccess(CloudEndpoint.parse(origin, () -> true), HttpClient.newHttpClient(),
                request -> request.header("Authorization", "Bearer synthetic-player"));
             var client = new LibraryClient(access, new LibrarySession(origin, player, 1, () -> true), callbacks::add);
             var preview = new LibraryPreviewSession()) {
            // Only the world container is allocated: real avatar lookup, screen ticks and HTTP callbacks run.
            minecraft.level = (net.minecraft.client.multiplayer.ClientLevel) unsafe.allocateInstance(net.minecraft.client.multiplayer.ClientLevel.class);
            users.put(player, user);
            fetched.add(player);
            var screenType = top.aihmc.omnimorph.gui.screens.WardrobeScreen.class;
            // Supply the vanilla renderable accessor normally installed by the game's mixin runtime.
            var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null) {
                private final java.util.List<net.minecraft.client.gui.components.Renderable> renderables = new java.util.ArrayList<>();

                @Override
                public java.util.List<net.minecraft.client.gui.components.Renderable> renderables() {
                    return renderables;
                }
            };
            field(screenType, "client").set(screen, client);
            field(screenType, "preview").set(screen, preview);
            field(screenType, "visibility").set(screen,
                    new top.aihmc.omnimorph.backend2.library.LibraryEquipmentVisibility(client));
            var constructor = field(screenType, "selected").getType().getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            field(screenType, "selected").set(screen, constructor.newInstance(null, null, false,
                    UUID.randomUUID(), net.minecraft.network.chat.Component.literal("unrelated preview")));
            var button = new top.aihmc.omnimorph.gui.widgets.SwitchButton(0, 0, 24, 24,
                    net.minecraft.network.chat.Component.literal("show"), false);
            field(screenType, "showModel").set(screen, button);
            var refresh = screenType.getDeclaredMethod("refreshVisibility");
            refresh.setAccessible(true);
            var change = screenType.getDeclaredMethod("changeVisibility");
            change.setAccessible(true);
            for (boolean isPublic : new boolean[]{false, true}) {
                publicModel.set(isPublic);
                refresh.invoke(screen);
                screen.tick();
                var pending = (LibraryClient.Request<?>) field(screenType, "visibilityRequest").get(screen);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (callbacks.isEmpty() && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                require(!callbacks.isEmpty(), "session response reaches the client callback queue");
                screen.tick();
                require(field(screenType, "visibilityRequest").get(screen) == pending && !pending.future().isCancelled(),
                        "selected preview must not cancel and restart the visibility request on every tick");
                await(pending.future(), deliver);
                screen.tick();
                require(button.isActive(), "public/private visibility must stay usable across ticks with a selected preview");
                for (boolean value : new boolean[]{false, true}) {
                    button.setToggled(value);
                    change.invoke(screen);
                    var saving = (LibraryClient.Request<?>) field(screenType, "visibilityRequest").get(screen);
                    screen.tick();
                    await(saving.future(), deliver);
                    var reading = (LibraryClient.Request<?>) field(screenType, "visibilityRequest").get(screen);
                    await(reading.future(), deliver);
                    screen.tick();
                    require(button.isActive() && button.isToggled() == value && showing.get() == value,
                            "public/private changes persist and remain usable after readback and further ticks");
                }
            }
            equipped.nbt = model("reloaded equipment");
            screen.tick();
            var reading = (LibraryClient.Request<?>) field(screenType, "visibilityRequest").get(screen);
            screen.tick();
            require(field(screenType, "visibilityRequest").get(screen) == reading,
                    "equipment reload refreshes once without cancelling the request on the next tick");
            await(reading.future(), deliver);
            screen.tick();
            require(button.isActive(), "equipment reload recovers the visibility control");
            System.out.println("PASS wardrobe visibility lifecycle: selected preview, multiple ticks, public/private HTTP saves and equipment reload");
        } finally {
            server.stop(0);
            minecraft.level = previousLevel;
            if (previousUser == null) {
                users.remove(player);
            } else {
                users.put(player, previousUser);
            }
            if (!alreadyFetched) {
                fetched.remove(player);
            }
            if (!hadPermissions) {
                permissions.remove(player);
            }
        }
    }

    private static void testCollectionActions(LibraryClient client) throws Exception {
        require(!Configs.WARDROBE_AUTO_CACHE.defaultValue && Configs.WARDROBE_AUTO_CACHE.parent == Configs.MISC
                        && Configs.MISC.children.contains(Configs.WARDROBE_AUTO_CACHE)
                        && !Configs.UI.children.contains(Configs.WARDROBE_AUTO_CACHE),
                "the existing default-off cache preference appears once in global Misc settings");
        Path cache = Path.of(System.getProperty("figura.test.directory")).resolve("collection-actions");
        try (var collection = new LibraryCollectionService(client, cache)) {
            var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
            field(screen.getClass(), "client").set(screen, client);
            field(screen.getClass(), "collection").set(screen, collection);
            field(collection.getClass(), "authoritative").setBoolean(collection, true);
            UUID id = UUID.randomUUID();
            var modelJson = new com.google.gson.JsonObject();
            modelJson.addProperty("model_id", id.toString());
            modelJson.addProperty("revision", 1);
            modelJson.addProperty("policy_revision", 9);
            modelJson.addProperty("display_name", "Collection owner");
            modelJson.addProperty("internal_name", "collection-owner");
            modelJson.addProperty("model_type", "FIGURA");
            modelJson.addProperty("visibility", "PRIVATE");
            modelJson.addProperty("requires_authorization", true);
            var uploader = new com.google.gson.JsonObject();
            uploader.addProperty("uuid", client.session().player().toString());
            uploader.addProperty("name", "owner");
            modelJson.add("uploader", uploader);
            modelJson.add("allowed_actions", com.google.gson.JsonParser.parseString(
                    "[\"preview\",\"download\",\"equip\",\"patch\",\"delete\",\"grants\"]"));
            var gson = new com.google.gson.GsonBuilder()
                    .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            var model = gson.fromJson(modelJson, top.aihmc.omnimorph.backend2.library.LibraryModels.Model.class);
            var selectionType = field(screen.getClass(), "selected").getType();
            var selection = selectionType.getDeclaredConstructors()[0];
            selection.setAccessible(true);
            field(screen.getClass(), "selected").set(screen,
                    selection.newInstance(null, null, false, id, net.minecraft.network.chat.Component.literal("selected")));
            field(collection.getClass(), "entries").set(collection, java.util.List.of(
                    new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(id, 1, "UPLOAD", false, model)));
            var showModel = new top.aihmc.omnimorph.gui.widgets.SwitchButton(0, 0, 24, 24,
                    net.minecraft.network.chat.Component.literal("show"), false);
            field(screen.getClass(), "showModel").set(screen, showModel);
            UUID equippedModel = UUID.randomUUID();
            field(screen.getClass(), "libraryVisibility").set(screen,
                    new top.aihmc.omnimorph.backend2.library.LibraryEquipmentVisibility.State(equippedModel, 1, 7, true, true));
            var updateVisibility = screen.getClass().getDeclaredMethod("updateVisibilityButton", boolean.class);
            updateVisibility.setAccessible(true);
            updateVisibility.invoke(screen, false);
            require(showModel.isActive(), "actual public equipment remains configurable without collection membership and independently of preview selection");
            field(screen.getClass(), "libraryVisibility").set(screen, null);
            updateVisibility.invoke(screen, false);
            require(!showModel.isActive(), "a preview selection alone cannot enable the equipment visibility switch");
            var actions = new top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar(20, 200, 240, 56);
            var build = screen.getClass().getDeclaredMethod("buildCloudCollectionActions", actions.getClass());
            build.setAccessible(true);
            build.invoke(screen, actions);
            for (String name : java.util.List.of("cacheSelectedButton", "removeCollectionButton",
                    "editCollectionButton", "collectionGrantsButton", "collectionAuthorizationButton", "collectionPublicationButton")) {
                var button = (top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), name).get(screen);
                require(button.isVisible() && button.isActive() && actions.children().contains(button),
                        "authorized personal actions belong to the preview action bar: " + name);
            }
            var remove = (top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), "removeCollectionButton").get(screen);
            remove.onPress(null);
            require(id.equals(field(screen.getClass(), "deleteCollectionArmed").get(screen))
                            && field(screen.getClass(), "collectionMutation").get(screen) == null,
                    "own-upload delete first confirms the exact model without issuing a mutation");
            uploader.addProperty("uuid", UUID.randomUUID().toString());
            modelJson.add("allowed_actions", com.google.gson.JsonParser.parseString("[\"preview\"]"));
            var other = gson.fromJson(modelJson, top.aihmc.omnimorph.backend2.library.LibraryModels.Model.class);
            field(collection.getClass(), "entries").set(collection, java.util.List.of(
                    new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(id, 1, "DOWNLOAD", false, other)));
            var update = screen.getClass().getDeclaredMethod("updateCollectionButtons");
            update.setAccessible(true);
            update.invoke(screen);
            require(remove.isActive(), "another author's model can be removed from the account collection");
            for (String name : java.util.List.of("editCollectionButton", "collectionGrantsButton",
                    "collectionAuthorizationButton", "collectionPublicationButton")) {
                require(!((top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), name).get(screen)).isVisible(),
                        "unavailable personal model actions remain hidden: " + name);
            }
            require(!((top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), "cacheSelectedButton").get(screen)).isActive(),
                    "preview-only resources cannot use the formal cache button");
            field(collection.getClass(), "authoritative").setBoolean(collection, false);
            update.invoke(screen);
            require(!remove.isActive(), "offline metadata cannot authorize collection mutations");
            System.out.println("PASS collection preview actions: ownership, permissions, confirmation, offline state and one global cache preference");
        }
    }

    private static void testCollectionCardsAndTreeScrolling() throws Exception {
        var first = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(UUID.randomUUID(), 1, "DOWNLOAD", false, null);
        var second = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(UUID.randomUUID(), 2, "UPLOAD", false, null);
        var chosen = new java.util.concurrent.atomic.AtomicReference<top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry>();
        var view = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.View();
        try (var grid = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid(0, 0, 460, 240, view,
                null, chosen::set, ignored -> net.minecraft.network.chat.Component.literal("offline"))) {
            var a = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(null, first);
            var b = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(null, second);
            grid.entries(java.util.List.of(a, b), null);
            var click = new net.minecraft.client.input.MouseButtonEvent(8, 62, new net.minecraft.client.input.MouseButtonInfo(0, 0));
            require(grid.mouseClicked(click, false) && chosen.get() == a, "collection pointer hit testing selects the first card");
            require(grid.keyPressed(new net.minecraft.client.input.KeyEvent(262, 0, 0)) && chosen.get() == b,
                    "collection arrow navigation uses the same two-column card layout");
            var search = (top.aihmc.omnimorph.gui.widgets.SearchBar) grid.children().stream()
                    .filter(top.aihmc.omnimorph.gui.widgets.SearchBar.class::isInstance).findFirst().orElseThrow();
            var format = (top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown) grid.children().stream()
                    .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance).findFirst().orElseThrow();
            var toggle = (top.aihmc.omnimorph.gui.widgets.Button) grid.children().stream()
                    .filter(child -> child instanceof top.aihmc.omnimorph.gui.widgets.Button button
                            && button.getMessage().getString().equals("+")).findFirst().orElseThrow();
            require(!format.isVisible() && grid.scissorsY == 30, "filters start collapsed with card space reclaimed");
            toggle.onPress(null);
            require(format.isVisible() && grid.scissorsY == 54, "plus expands the native type dropdown below search");
            view.format = 2;
            toggle.onPress(null);
            require(!format.isVisible() && view.format == 2 && grid.scissorsY == 30,
                    "collapsing filters preserves the selected format and restores card hit geometry");
            view.format = 0;
            search.getField().setValue("no such model");
            require(!grid.mouseClicked(click, false) && chosen.get() == b, "filtered empty space cannot select a hidden card");
        }
        var child = new top.aihmc.omnimorph.model.FiguraModelPart(null, "deep-".repeat(40),
                new top.aihmc.omnimorph.model.PartCustomization(), Map.of(), new java.util.ArrayList<>());
        var root = new top.aihmc.omnimorph.model.FiguraModelPart(null, "root",
                new top.aihmc.omnimorph.model.PartCustomization(), Map.of(), new java.util.ArrayList<>(java.util.List.of(child)));
        var tree = new BoneTreeState();
        tree.replace(PreviewHierarchy.fromFigura(root));
        var widget = new top.aihmc.omnimorph.gui.widgets.wardrobe.BoneTreeWidget(0, 0, 180, 180, tree);
        var update = widget.getClass().getDeclaredMethod("updateScrollbars");
        update.setAccessible(true);
        var bar = (top.aihmc.omnimorph.gui.widgets.ScrollBarWidget) field(widget.getClass(), "horizontalBar").get(widget);
        update.invoke(widget);
        require(!bar.isVisible(), "collapsed short roots need no horizontal scrollbar");
        tree.toggle(0);
        update.invoke(widget);
        require(bar.isVisible() && widget.mouseScrolled(50, 80, -1, 0) && tree.horizontalScroll() > 0,
                "expanded overflowing hierarchy exposes a usable horizontal scrollbar");
        tree.toggle(0);
        update.invoke(widget);
        require(!bar.isVisible() && tree.horizontalScroll() == 0, "collapsing fitting rows hides and resets horizontal overflow");
        System.out.println("PASS collection card pointer/keyboard/filter routing and conditional horizontal bone scrolling; GUI MANUAL/PENDING");
    }

    private static void testAllResourceSources(LibraryClient client) throws Exception {
        var fetcher = top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.class;
        top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.reloadAvatars().get(5, TimeUnit.SECONDS);
        var originals = java.util.List.copyOf(top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.ALL_AVATARS);
        boolean reload = field(fetcher, "requireReload").getBoolean(null);
        Path root = Files.createTempDirectory(Path.of(System.getProperty("figura.test.directory")), "all-sources-");
        Path first = Files.createDirectories(root.resolve("included"));
        Path second = Files.createDirectories(root.resolve("excluded"));
        Files.writeString(first.resolve("avatar.json"), "{\"name\":\"included\"}");
        Files.writeString(second.resolve("avatar.json"), "{\"name\":\"excluded\"}");
        var local = new top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath(first, first);
        var other = new top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath(second, second);
        var member = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(UUID.randomUUID(), 1, "UPLOAD", true, null);
        var notInOutfit = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(UUID.randomUUID(), 1, "DOWNLOAD", false, null);
        Path accessory = Files.createDirectories(root.resolve("cosmetics/星翼"));
        Files.writeString(accessory.resolve("cosmetic.json"), "{\"name\":\"星翼\",\"cosmetic_anchor\":\"wing_anchor\"}");
        var deliveries = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        Runnable deliver = () -> {
            Runnable action;
            while ((action = deliveries.poll()) != null) {
                action.run();
            }
        };
        try (var catalog = new LocalCosmeticCatalog(root, deliveries::add);
             var collection = new LibraryCollectionService(client, root);
             var grid = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid(0, 0, 420, 240,
                     new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.View(), null, ignored -> {},
                     ignored -> net.minecraft.network.chat.Component.empty())) {
            field(fetcher, "requireReload").setBoolean(null, false);
            top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.ALL_AVATARS.clear();
            top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.ALL_AVATARS.addAll(java.util.List.of(local, other));
            field(collection.getClass(), "entries").set(collection, java.util.List.of(member, notInOutfit));
            var candidates = LocalOutfitCandidates.forDirectory(root);
            require(candidates.set(first, true).get(5, TimeUnit.SECONDS), "fixture candidate is persisted");
            require(candidates.set(accessory, true).get(5, TimeUnit.SECONDS), "accessory candidate uses the existing local membership store");
            require(await(catalog.refresh(), deliver).size() == 1, "actual accessory inventory is available for card and row filtering");
            var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
            var type = screen.getClass();
            field(type, "client").set(screen, client);
            field(type, "collection").set(screen, collection);
            field(type, "localCandidates").set(screen, candidates);
            field(type, "cosmetics").set(screen, catalog);
            require(field(type, "source").get(screen).toString().equals("ALL")
                            && field(type, "mode").get(screen).toString().equals("ALL")
                            && !field(type, "parts").getBoolean(screen),
                    "source and use default independently to All without entering the cosmetic placeholder");
            field(type, "tab").set(screen, field(type, "tab").getType().getEnumConstants()[1]);
            field(type, "collectionGrid").set(screen, grid);
            var refresh = type.getDeclaredMethod("refreshCollectionCards");
            refresh.setAccessible(true);
            Object[] sources = field(type, "source").getType().getEnumConstants();
            for (int index = 0; index < sources.length; index++) {
                field(type, "source").set(screen, sources[index]);
                refresh.invoke(screen);
                var entries = (java.util.List<?>) field(grid.getClass(), "entries").get(grid);
                require(entries.size() == (index == 0 ? 5 : index == 1 ? 3 : 2),
                        "All combines local Avatar/accessory/cloud cards while individual sources stay filtered");
            }
            field(type, "source").set(screen, sources[0]);
            Object[] purposes = field(type, "mode").getType().getEnumConstants();
            field(type, "mode").set(screen, purposes[2]);
            refresh.invoke(screen);
            var cosmeticCards = (java.util.List<?>) field(grid.getClass(), "entries").get(grid);
            require(cosmeticCards.size() == 1 && cosmeticCards.contains(
                            new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(null, null, catalog.entries().getFirst())),
                    "accessory purpose filters independently from source and format");
            field(type, "mode").set(screen, purposes[1]);
            refresh.invoke(screen);
            require(((java.util.List<?>) field(grid.getClass(), "entries").get(grid)).size() == 4,
                    "Avatar purpose excludes local accessories without changing the All source");
            field(type, "mode").set(screen, purposes[0]);
            field(type, "tab").set(screen, field(type, "tab").getType().getEnumConstants()[0]);
            field(type, "collectionGrid").set(screen, null);
            screen.width = 640;
            screen.height = 360;
            field(type, "layout").set(screen, top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
            var build = type.getDeclaredMethod("buildResources");
            build.setAccessible(true);
            build.invoke(screen);
            var list = (top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList) field(type, "cloudList").get(screen);
            try (list) {
                var filtered = list.getClass().getDeclaredMethod("filteredEntries");
                filtered.setAccessible(true);
                var entries = (java.util.List<?>) filtered.invoke(list);
                require(entries.size() == 3
                                && entries.contains(new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(local, null))
                                && entries.contains(new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(null, member))
                                && entries.contains(new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(null, null, catalog.entries().getFirst())),
                        "All outfit sources contain local Avatar/accessory/cloud candidates, never other collection entries");
                var selectors = (java.util.List<?>) field(list.getClass(), "resourceFilters").get(list);
                for (Object selector : selectors) {
                    var choices = (java.util.function.Supplier<?>) field(selector.getClass(), "choices").get(selector);
                    var selected = (java.util.function.IntSupplier) field(selector.getClass(), "selected").get(selector);
                    require(((java.util.List<?>) choices.get()).size() == 3 && selected.getAsInt() == 0,
                            "production source/use dropdowns each expose All as the default first option");
                }
            }
            field(type, "mode").set(screen, purposes[2]);
            field(type, "cloudList").set(screen, null);
            build.invoke(screen);
            var cosmeticList = (top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList) field(type, "cloudList").get(screen);
            try (cosmeticList) {
                var filtered = cosmeticList.getClass().getDeclaredMethod("filteredEntries");
                filtered.setAccessible(true);
                require(((java.util.List<?>) filtered.invoke(cosmeticList)).size() == 1,
                        "production accessory purpose builds a real candidate list instead of a placeholder");
            }
            System.out.println("PASS All source/use defaults: actual accessory inventory, independent Avatar/accessory filters, collection cards and candidate-only rows; GUI MANUAL/PENDING");
        } finally {
            top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.ALL_AVATARS.clear();
            top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.ALL_AVATARS.addAll(originals);
            field(fetcher, "requireReload").setBoolean(null, reload);
        }
    }

    private static void testResourceFilterPanels() throws Exception {
        for (int width : new int[]{160, 460}) {
            var local = new top.aihmc.omnimorph.gui.widgets.lists.AvatarList(10, 54, width, 220, null,
                    ignored -> {}, ignored -> true);
            local.setActionsVisible(false);
            try (var cloud = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList(10, 54, width, 220,
                    new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList.View(), ignored -> {},
                    ignored -> net.minecraft.network.chat.Component.empty());
                 var cards = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid(10, 54, width, 220,
                    new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.View(), null, ignored -> {},
                    ignored -> net.minecraft.network.chat.Component.empty())) {
                for (var panel : java.util.List.<top.aihmc.omnimorph.gui.widgets.lists.AbstractList>of(local, cloud, cards)) {
                    var open = new java.util.concurrent.atomic.AtomicBoolean();
                    var source = new java.util.concurrent.atomic.AtomicInteger();
                    var mode = new java.util.concurrent.atomic.AtomicInteger();
                    var sourceControl = new top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown(0, 0, 80,
                            () -> java.util.List.of(net.minecraft.network.chat.Component.literal("local"),
                                    net.minecraft.network.chat.Component.literal("cloud")), source::get, source::set);
                    var modeControl = new top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown(0, 0, 80,
                            () -> java.util.List.of(net.minecraft.network.chat.Component.literal("avatar"),
                                    net.minecraft.network.chat.Component.literal("parts")), mode::get, mode::set);
                    panel.getClass().getMethod("setResourceFilters", sourceControl.getClass(), modeControl.getClass(),
                            boolean.class, java.util.function.Consumer.class).invoke(panel, sourceControl, modeControl,
                            false, (java.util.function.Consumer<Boolean>) open::set);
                    var dropdowns = panel.children().stream()
                            .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance)
                            .map(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::cast).toList();
                    var toggle = (top.aihmc.omnimorph.gui.widgets.Button) panel.children().stream()
                            .filter(child -> child instanceof top.aihmc.omnimorph.gui.widgets.Button button
                                    && button.getMessage().getString().equals("+")).findFirst().orElseThrow();
                    int collapsed = panel.scissorsY;
                    require(dropdowns.size() == 3 && dropdowns.stream().noneMatch(top.aihmc.omnimorph.gui.widgets.Button::isVisible),
                            "source, mode and format are all owned by the collapsed plus panel");
                    toggle.onPress(null);
                    require(open.get() && dropdowns.stream().allMatch(top.aihmc.omnimorph.gui.widgets.Button::isVisible)
                                    && panel.scissorsY == collapsed + 24,
                            "plus reveals all selectors together and reserves one filter row");
                    for (int index = 0; index < dropdowns.size(); index++) {
                        var control = dropdowns.get(index);
                        require(control.getWidth() > 0 && control.getX() >= panel.getX()
                                        && control.getX() + control.getWidth() <= panel.getX() + width
                                        && (index == 0 || dropdowns.get(index - 1).getX()
                                            + dropdowns.get(index - 1).getWidth() < control.getX()),
                                "expanded selectors fit narrow/wide lists without overlapping");
                    }
                    ((java.util.function.IntConsumer) field(sourceControl.getClass(), "changed").get(sourceControl)).accept(1);
                    require(source.get() == 1 && mode.get() == 0, "source selection does not change resource mode");
                    ((java.util.function.IntConsumer) field(modeControl.getClass(), "changed").get(modeControl)).accept(1);
                    toggle.onPress(null);
                    require(!open.get() && source.get() == 1 && mode.get() == 1 && panel.scissorsY == collapsed
                                    && dropdowns.stream().noneMatch(top.aihmc.omnimorph.gui.widgets.Button::isVisible),
                            "collapse retains both selections while reclaiming list space");
                }
            }
        }
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        screen.width = 640;
        screen.height = 360;
        field(screen.getClass(), "layout").set(screen,
                top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        field(screen.getClass(), "parts").setBoolean(screen, true);
        var build = screen.getClass().getDeclaredMethod("buildResources");
        build.setAccessible(true);
        build.invoke(screen);
        require(screen.children().stream().noneMatch(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance),
                "production wardrobe no longer creates standalone source or mode selectors above search");
        var placeholder = (top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList) screen.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList.class::isInstance).findFirst().orElseThrow();
        var toggle = (top.aihmc.omnimorph.gui.widgets.Button) placeholder.children().stream()
                .filter(child -> child instanceof top.aihmc.omnimorph.gui.widgets.Button button
                        && button.getMessage().getString().equals("+")).findFirst().orElseThrow();
        toggle.onPress(null);
        require(field(screen.getClass(), "resourceFiltersExpanded").getBoolean(screen)
                        && placeholder.children().stream().filter(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::isInstance)
                        .map(top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown.class::cast)
                        .allMatch(top.aihmc.omnimorph.gui.widgets.Button::isVisible),
                "2.0 placeholder mode retains access to the collapsed source and mode selectors");
        placeholder.close();
        System.out.println("PASS wardrobe plus panels: all resource selectors, independent values, bounded geometry and parts-mode access; GUI MANUAL/PENDING");
    }

    private static void testWardrobeActionMenus() throws Exception {
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        screen.width = 640;
        screen.height = 360;
        field(screen.getClass(), "layout").set(screen,
                top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout.of(640, 360));
        var build = screen.getClass().getDeclaredMethod("buildPreview");
        build.setAccessible(true);
        try (var preview = new LibraryPreviewSession()) {
            field(screen.getClass(), "preview").set(screen, preview);
            build.invoke(screen);
            var actions = (top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar) screen.children().stream()
                    .filter(top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar.class::isInstance).findFirst().orElseThrow();
            var visible = actions.getClass().getDeclaredMethod("visibleActions");
            visible.setAccessible(true);
            var slots = (java.util.List<?>) visible.invoke(actions);
            require(field(screen.getClass(), "candidateButton").get(screen) == null && slots.size() == 15
                            && slots.get(5) == null && slots.get(9) == null,
                    "avatar actions hide accessory-only controls and retain shared draft operations");
            var expected = java.util.List.of("upload", "reload", "wardrobe_visibility", "wardrobe_appeal", "delete",
                    "sound", "keybind", "unselect", "wardrobe_draft_retry",
                    "wardrobe_draft_refresh", "wardrobe_draft_cancel", "wardrobe_draft_reset", "wardrobe_outfit_presets");
            int index = 0;
            for (Object slot : slots) {
                if (slot != null) {
                    var texture = slot instanceof top.aihmc.omnimorph.gui.widgets.library.LibraryIconButton
                            ? field(slot.getClass(), "icon").get(slot)
                            : field(top.aihmc.omnimorph.gui.widgets.Button.class, "texture").get(slot);
                    require(texture.toString().endsWith("/" + expected.get(index++) + ".png"),
                            "common operations precede sound/keybind/clear, without local creation or tree refresh");
                }
            }
            require(!((top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), "cosmeticSettingsButton")
                    .get(screen)).isActive(), "settings are disabled when no enabled instance registers controls");
            for (String name : java.util.List.of("uploadButton", "retryDraftButton", "refreshDraftButton")) {
                require(!((top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), name).get(screen)).isActive(),
                        "formal outfit actions are disabled without negotiated Apply capability: " + name);
            }
            var groups = screen.getClass().getDeclaredMethod("refreshActionGroups");
            groups.setAccessible(true);
            field(screen.getClass(), "parts").setBoolean(screen, true);
            groups.invoke(screen);
            var accessorySlots = (java.util.List<?>) visible.invoke(actions);
            require(accessorySlots.size() == 11 && accessorySlots.get(0) == field(screen.getClass(), "uploadButton").get(screen),
                    "accessory mode keeps the shared publish action before its own controls and shared draft operations");
            for (Object button : (java.util.List<?>) field(screen.getClass(), "avatarActions").get(screen)) {
                require(!((top.aihmc.omnimorph.gui.widgets.Button) button).isVisible(),
                        "avatar operations are hidden in accessory mode");
            }
            require(((top.aihmc.omnimorph.gui.widgets.Button) field(screen.getClass(), "addInstanceButton")
                    .get(screen)).isVisible(), "accessory mode exposes instance addition");
            field(screen.getClass(), "parts").setBoolean(screen, false);
            groups.invoke(screen);
            require(((java.util.List<?>) visible.invoke(actions)).size() == 15,
                    "returning to avatar mode restores its controls without rebuilding callbacks");
            var selectionType = field(screen.getClass(), "selected").getType();
            var selectionConstructor = selectionType.getDeclaredConstructor(java.nio.file.Path.class,
                    java.nio.file.Path.class, boolean.class, java.util.UUID.class,
                    net.minecraft.network.chat.Component.class,
                    top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry.class);
            selectionConstructor.setAccessible(true);
            var accessory = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory.Entry(
                    java.nio.file.Path.of("accessory"), java.util.UUID.randomUUID(), null, "fixture");
            field(screen.getClass(), "selected").set(screen, selectionConstructor.newInstance(
                    accessory.path(), accessory.path(), false, null,
                    net.minecraft.network.chat.Component.literal("Accessory"), accessory));
            groups.invoke(screen);
            require(((java.util.List<?>) visible.invoke(actions)).size() == 11,
                    "an accessory selected in All switches the actual controls to the accessory group");
            field(screen.getClass(), "selected").set(screen, selectionConstructor.newInstance(
                    java.nio.file.Path.of("avatar"), java.nio.file.Path.of("avatar"), false,
                    null, net.minecraft.network.chat.Component.literal("Avatar"), null));
            field(screen.getClass(), "parts").setBoolean(screen, true);
            groups.invoke(screen);
            require(((java.util.List<?>) visible.invoke(actions)).size() == 15,
                    "the selected avatar takes priority over an accessory filter fallback");
        }
        var refreshed = new java.util.concurrent.atomic.AtomicInteger();
        var tree = new top.aihmc.omnimorph.gui.widgets.wardrobe.BoneTreeWidget(10, 20, 180, 180,
                new BoneTreeState(), refreshed::incrementAndGet);
        var refresh = (top.aihmc.omnimorph.gui.widgets.Button) tree.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.Button.class::isInstance).findFirst().orElseThrow();
        var search = (top.aihmc.omnimorph.gui.widgets.SearchBar) tree.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.SearchBar.class::isInstance).findFirst().orElseThrow();
        refresh.onPress(null);
        require(refreshed.get() == 1 && search.getX() + search.getWidth() < refresh.getX()
                        && refresh.getX() + refresh.getWidth() <= tree.getX() + tree.getWidth(),
                "tree refresh is clickable alongside search without overlapping its input");
        Minecraft minecraft = Minecraft.getInstance();
        var previousScreen = minecraft.screen;
        var windowField = field(Minecraft.class, "window");
        Object previousWindow = windowField.get(minecraft);
        // Context-menu bounds need deterministic viewport metrics, without creating or rendering a window.
        // This fixture verifies callbacks only; real-window menu placement remains MANUAL/PENDING.
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        var viewport = (com.mojang.blaze3d.platform.Window) unsafe.allocateInstance(com.mojang.blaze3d.platform.Window.class);
        field(viewport.getClass(), "guiScaledWidth").setInt(viewport, 640);
        field(viewport.getClass(), "guiScaledHeight").setInt(viewport, 360);
        windowField.set(minecraft, viewport);
        minecraft.screen = screen;
        try {
            var clearingMenu = new top.aihmc.omnimorph.gui.widgets.ContextMenu() {
                @Override
                public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
                    // Replay the action without the native audio engine; the screen dispatch remains real.
                    ((top.aihmc.omnimorph.gui.widgets.Button) getEntries().getFirst()).onPress(null);
                    return true;
                }
            };
            clearingMenu.addAction(net.minecraft.network.chat.Component.literal("Rebuild"), null,
                    ignored -> screen.contextMenu = null);
            clearingMenu.setX(20);
            clearingMenu.setY(20);
            clearingMenu.setVisible(true);
            screen.contextMenu = clearingMenu;
            require(screen.contextMenuClick(new net.minecraft.client.input.MouseButtonEvent(24, 24,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)), false),
                    "menu callback may clear the screen menu during a consumed click");
            require(screen.contextMenu == null && !clearingMenu.isVisible(),
                    "the clicked menu closes after its callback rebuilds the screen");
            testCreationMenuAndForm(screen);
            var selected = new java.util.concurrent.atomic.AtomicInteger();
            var removed = new java.util.concurrent.atomic.AtomicReference<Object>();
            var local = new top.aihmc.omnimorph.gui.widgets.lists.AvatarList(0, 0, 180, 180, screen,
                    ignored -> selected.incrementAndGet(), ignored -> true);
            local.setActionsVisible(false);
            local.showSourceBadge(true);
            local.setOutfitRemoval(removed::set);
            Path path = Files.createDirectories(Path.of(System.getProperty("figura.test.directory")).resolve("outfit-menu-avatar"));
            Files.writeString(path.resolve("avatar.json"), "{\"name\":\"Menu avatar\"}");
            var avatar = new top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher.AvatarPath(path, path);
            var localRow = new top.aihmc.omnimorph.gui.widgets.avatar.AvatarWidget(0, 160, avatar, local);
            localRow.setX(4);
            localRow.setY(30);
            var rightClick = new net.minecraft.client.input.MouseButtonEvent(8, 36,
                    new net.minecraft.client.input.MouseButtonInfo(1, 0));
            require(localRow.getHeight() == 28 && localRow.mouseClicked(rightClick, false),
                    "taller local outfit rows retain their existing right-click context menu");
            var menu = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
            var removeLabel = top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.remove_outfit").getString();
            var remove = (top.aihmc.omnimorph.gui.widgets.Button) menu.children().stream()
                    .filter(child -> child instanceof top.aihmc.omnimorph.gui.widgets.Button button
                            && button.getMessage().getString().equals(removeLabel)).findFirst().orElseThrow();
            remove.onPress(null);
            require(removed.get() == avatar && selected.get() == 0 && !menu.isVisible(),
                    "removal targets the right-clicked local avatar without selecting or equipping it");
            removed.set(null);
            try (var cloud = new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList(0, 0, 180, 180,
                    new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList.View(),
                    ignored -> selected.incrementAndGet(), ignored -> net.minecraft.network.chat.Component.empty())) {
                cloud.setOutfitRemoval(removed::set);
                var entry = new top.aihmc.omnimorph.backend2.library.LibraryModels.CollectionEntry(UUID.randomUUID(), 1,
                        "UPLOAD", true, null);
                var rowType = Class.forName("top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList$Row");
                var constructor = rowType.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                var row = (top.aihmc.omnimorph.gui.widgets.Button) constructor.newInstance(cloud, 4, 30, 160,
                        new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(null, entry),
                        (top.aihmc.omnimorph.gui.widgets.Button.OnPress) ignored -> selected.incrementAndGet());
                require(row.getHeight() == 28 && row.mouseClicked(rightClick, false), "cloud rows share the taller context-menu interaction");
                menu = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
                remove = (top.aihmc.omnimorph.gui.widgets.Button) menu.children().stream()
                        .filter(child -> child instanceof top.aihmc.omnimorph.gui.widgets.Button button
                                && button.getMessage().getString().equals(removeLabel)).findFirst().orElseThrow();
                remove.onPress(null);
                require(removed.get() == entry && selected.get() == 0 && !menu.isVisible(),
                        "cloud removal captures the clicked member without invoking selection/equipment");
                removed.set(null);
                cloud.localEntries(java.util.List.of(avatar), ignored -> selected.incrementAndGet(), removed::set, null);
                var mixedLocalRow = (top.aihmc.omnimorph.gui.widgets.Button) constructor.newInstance(cloud, 4, 30, 160,
                        new top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid.Entry(avatar, null),
                        (top.aihmc.omnimorph.gui.widgets.Button.OnPress) ignored -> selected.incrementAndGet());
                require(mixedLocalRow.mouseClicked(rightClick, false), "All-source local rows expose outfit removal");
                menu = top.aihmc.omnimorph.utils.ui.UIHelper.getContext();
                remove = (top.aihmc.omnimorph.gui.widgets.Button) menu.children().stream()
                        .filter(child -> child instanceof top.aihmc.omnimorph.gui.widgets.Button button
                                && button.getMessage().getString().equals(removeLabel)).findFirst().orElseThrow();
                remove.onPress(null);
                require(removed.get() == avatar && selected.get() == 0 && !menu.isVisible(),
                        "All-source local removal uses the local resource callback without selecting it");
                cloud.entries(java.util.List.of(entry), null);
                require(cloud.keyPressed(new net.minecraft.client.input.KeyEvent(264, 0, 0))
                                && cloud.keyPressed(new net.minecraft.client.input.KeyEvent(264, 0, 0)) && selected.get() == 2,
                        "mixed outfit keyboard navigation selects both local and cloud resources");
            }
        } finally {
            minecraft.screen = previousScreen;
            windowField.set(minecraft, previousWindow);
        }
        for (String format : java.util.List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED")) {
            String expected = switch (format) {
                case "FIGURA" -> "gui.wardrobe.filter.figura";
                case "YSM_PLAIN" -> "gui.wardrobe.filter.ysm_plain";
                default -> "gui.wardrobe.filter.ysm_encrypted";
            };
            var label = (net.minecraft.network.chat.contents.TranslatableContents)
                    top.aihmc.omnimorph.gui.widgets.ModelResourceBadges.typeTooltip(format).getContents();
            var type = (net.minecraft.network.chat.Component) label.getArgs()[0];
            require(type.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated
                            && translated.getKey().equals("omni-morph." + expected),
                    "catalog type tooltip retains the full localized Figura/YSM/encrypted YSM label");
        }
        System.out.println("PASS wardrobe action groups, native local/cloud removal menus, taller rows, tree refresh and type text; GUI MANUAL/PENDING");
    }

    private static void testCreationMenuAndForm(top.aihmc.omnimorph.gui.screens.WardrobeScreen parent)
            throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        var open = parent.getClass().getDeclaredMethod("openCreateMenu", net.minecraft.client.gui.components.Button.class);
        open.setAccessible(true);
        var anchor = new top.aihmc.omnimorph.gui.widgets.Button(20, 20, 24, 24,
                net.minecraft.network.chat.Component.literal("+"), null, ignored -> {});
        open.invoke(parent, anchor);
        var menu = parent.contextMenu;
        require(menu.isVisible() && menu.getX() == anchor.getX()
                        && menu.getY() == anchor.getY() + anchor.getHeight(),
                "Create opens a visible menu anchored below the clicked button");
        var labels = menu.children().stream().filter(top.aihmc.omnimorph.gui.widgets.Button.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.Button.class::cast)
                .map(button -> button.getMessage().getString()).toList();
        require(labels.contains(top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.create_figura").getString())
                        && labels.contains(top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.create_ysm").getString())
                        && labels.contains(top.aihmc.omnimorph.utils.FiguraText.of("gui.wardrobe.create_cosmetic").getString())
                        && labels.size() == 3,
                "Existing create icon opens exactly the three requested resource creation entries");
        parent.contextMenu = null;
        menu.setVisible(false);
        Path root = Path.of(System.getProperty("figura.test.directory")).resolve("uncreated-form-root");
        var ysm = new top.aihmc.omnimorph.wizards.YsmAvatarWizard(root);
        var list = new top.aihmc.omnimorph.gui.widgets.lists.AvatarWizardList(20, 20, 240, 200, ysm);
        var fields = list.children().stream().filter(top.aihmc.omnimorph.gui.widgets.TextField.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.TextField.class::cast).toList();
        require(fields.size() == 3 && list.children().stream()
                .noneMatch(top.aihmc.omnimorph.gui.widgets.SwitchButton.class::isInstance),
                "Actual YSM wizard list contains three metadata fields and no Figura model/script options");
        fields.get(0).getField().setValue("中文/形象");
        fields.get(1).getField().setValue("描述");
        fields.get(2).getField().setValue("作者");
        require(ysm.canBuild() && ysm.targetDirectory().equals(root.resolve("中文_形象")) && !Files.exists(root),
                "Native field callbacks update the displayed target without creating files");
        var cosmetic = new top.aihmc.omnimorph.wizards.CosmeticWizard(root.resolve("cosmetics"));
        cosmetic.template(top.aihmc.omnimorph.wizards.CosmeticTemplate.FEET);
        var cosmeticList = new top.aihmc.omnimorph.gui.widgets.lists.AvatarWizardList(20, 20, 240, 200, cosmetic);
        var cosmeticFields = cosmeticList.children().stream()
                .filter(top.aihmc.omnimorph.gui.widgets.TextField.class::isInstance)
                .map(top.aihmc.omnimorph.gui.widgets.TextField.class::cast).toList();
        require(cosmeticFields.size() == 13 && cosmetic.anchor().equals("left_foot_anchor"),
                "Accessory form exposes metadata, explicit attachment and all nine local transform components");
        cosmeticFields.get(0).getField().setValue("右足饰");
        cosmeticFields.get(3).getField().setValue("right_foot_anchor");
        cosmeticFields.get(4).getField().setValue("2.5");
        require(cosmetic.canBuild() && cosmetic.anchor().equals("right_foot_anchor")
                        && cosmetic.transform().position().x() == 2.5 && !Files.exists(root),
                "Native accessory fields choose a single right-side target and retain resource-local transforms");
        cosmeticFields.get(12).getField().setValue("0");
        require(!cosmetic.canBuild(), "Invalid authored scale blocks Create");
        cosmeticFields.get(12).getField().setValue("1");
        cosmeticFields.get(3).getField().setValue("authored_socket");
        require(cosmetic.canBuild(), "Explicit nonstandard names remain supported");
        cosmetic.template(top.aihmc.omnimorph.wizards.CosmeticTemplate.TRAIL);
        require(cosmetic.anchor().equals("tailtip"), "Trail template selects its declared AUTO_TRAIL preference");

        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var job = new CompletableFuture<Void>();
        var wizard = new top.aihmc.omnimorph.wizards.AvatarWizard() {
            @Override
            public CompletableFuture<Void> buildAsync() {
                calls.incrementAndGet();
                return job;
            }
        };
        wizard.changeEntry(top.aihmc.omnimorph.wizards.WizardEntry.NAME, "保留输入");
        var screenType = top.aihmc.omnimorph.gui.screens.AvatarWizardScreen.class;
        var screen = new top.aihmc.omnimorph.gui.screens.AvatarWizardScreen(parent, wizard) {
            private final java.util.List<net.minecraft.client.gui.components.Renderable> widgets =
                    new java.util.ArrayList<>();

            @Override
            public java.util.List<net.minecraft.client.gui.components.Renderable> renderables() {
                return widgets;
            }
        };
        var build = new top.aihmc.omnimorph.gui.widgets.Button(0, 0, 100, 20,
                net.minecraft.network.chat.Component.empty(), null, ignored -> {});
        var cancel = new top.aihmc.omnimorph.gui.widgets.Button(0, 0, 100, 20,
                net.minecraft.network.chat.Component.empty(), null, ignored -> {});
        field(screenType, "build").set(screen, build);
        field(screenType, "cancel").set(screen, cancel);
        var start = screenType.getDeclaredMethod("startBuild");
        start.setAccessible(true);
        minecraft.screen = screen;
        try {
            start.invoke(screen);
            start.invoke(screen);
            screen.onClose();
            require(calls.get() == 1 && !build.isActive() && !cancel.isActive() && minecraft.screen == screen,
                    "Pending creation rejects repeated submission and closing before file work finishes");
            job.completeExceptionally(new java.io.IOException("Expected creation failure fixture"));
            screen.tick();
            require(minecraft.screen == screen && build.isActive() && cancel.isActive()
                            && wizard.getEntry(top.aihmc.omnimorph.wizards.WizardEntry.NAME, "").equals("保留输入"),
                    "Actual failure callback preserves the form and restores retry/cancel controls");
        } finally {
            minecraft.screen = parent;
        }
        System.out.println("PASS creation menu/YSM form: actual native fields, metadata-only options, no files before Create, "
                + "duplicate/close guards and failed-job retry state; GUI MANUAL/PENDING");
    }

    private static void testOutfitUploadScreen(LibraryClient client, CurrentPreviewPlayer player, Runnable drain) throws Exception {
        var budget = top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults();
        var extension = new top.aihmc.omnimorph.backend2.library.LibraryModels.OutfitCapabilities(1, true, true,
                false, false, 32, 128, 64, 8192, 32768, 1024, 65536, 8, 256, budget, true);
        var capabilities = new top.aihmc.omnimorph.backend2.library.LibraryModels.Capabilities(true, 1,
                1024 * 1024, 64, 1024 * 1024, 1024, 1024, java.util.List.of("zip", "ysm"),
                java.util.List.of("FIGURA"), java.util.List.of("general"), false, extension);
        var screen = new top.aihmc.omnimorph.gui.screens.library.ModelUploadScreen(null, client, capabilities, () -> {});
        var type = screen.getClass();
        field(type, "sessionRequest").set(screen, client.submit(LibraryClient.Lane.CONTROL, exchange -> null));
        var session = (LibraryPreviewSession) field(type, "preview").get(screen);
        field(LibraryPreviewSession.class, "detachedEntity").set(session, player);
        var minecraft = Minecraft.getInstance();
        var input = field(Minecraft.class, "lastInputType");
        var previousInput = input.get(minecraft);
        var parent = minecraft.screen;
        Path root = Files.createTempDirectory(FiguraMod.getFiguraDirectory(), "outfit-upload-ui-");
        try {
            input.set(minecraft, net.minecraft.client.InputType.MOUSE);
            minecraft.screen = screen;
            screen.width = 640;
            screen.height = 360;
            var initialize = type.getDeclaredMethod("init");
            initialize.setAccessible(true);
            initialize.invoke(screen);
            var purpose = type.getDeclaredMethod("changePurpose", int.class);
            purpose.setAccessible(true);
            purpose.invoke(screen, 2);
            require(field(type, "outfitSource").getBoolean(screen), "negotiated third purpose opens the real Outfit form");
            Path source = root.resolve("preset.json");
            Files.writeString(source, "{\"name\":\"Native preset\",\"configuration\":"
                    + top.aihmc.omnimorph.model.outfit.OutfitCodec.encode(OutfitConfig.empty()) + "}");
            var select = type.getDeclaredMethod("selectFile", Path.class);
            select.setAccessible(true);
            select.invoke(screen, source);
            awaitCondition(() -> field(type, "outfitReady").getBoolean(screen), drain);
            require(((top.aihmc.omnimorph.gui.widgets.TextField) field(type, "name").get(screen))
                            .getField().getValue().equals("Native preset")
                            && session.outfitComposition() != null && field(type, "prepared").get(screen) == null
                            && ((top.aihmc.omnimorph.gui.widgets.Button) field(type, "upload").get(screen)).isActive(),
                    "JSON metadata prefill and actual isolated composition enable publication without preparing model bytes");
            var draft = new top.aihmc.omnimorph.model.outfit.OutfitDraft(OutfitConfig.empty(), budget);
            var missing = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null, null,
                    "0".repeat(64), OutfitConfig.Format.FIGURA);
            draft.add(missing, OutfitConfig.AnchorRef.authorDefault("hip"), OutfitConfig.Transform.identity());
            Files.writeString(source, top.aihmc.omnimorph.model.outfit.OutfitCodec.encode(draft.configuration()));
            select.invoke(screen, source);
            awaitCondition(() -> field(type, "outfitResources").get(screen) != null, drain);
            var resources = (OutfitPresetResources.Prepared) field(type, "outfitResources").get(screen);
            require(resources.issues().size() == 1 && !field(type, "outfitReady").getBoolean(screen)
                            && !field(type, "outfitDistributable").getBoolean(screen)
                            && !((top.aihmc.omnimorph.gui.widgets.Button) field(type, "upload").get(screen)).isActive(),
                    "the actual local dependency failure blocks publication and retains its explicit issue");
            purpose.invoke(screen, 0);
            require(field(type, "outfitPrepared").get(screen) == null && session.outfitComposition() == null,
                    "switching back to Avatar retires the JSON draft and its dependency preview");
            screen.removed();
            select.invoke(screen, source);
            require(field(type, "outfitPrepared").get(screen) == null, "closed Outfit source controls cannot restart work");
        } finally {
            screen.removed();
            minecraft.screen = parent;
            input.set(minecraft, previousInput);
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("PASS Outfit upload native form: negotiated JSON purpose, metadata, isolated composition, dependency failure and source retirement; GUI MANUAL/PENDING");
    }

    private static void testAccessoryUploadScreen(LibraryClient client, CurrentPreviewPlayer player, Runnable drain) throws Exception {
        var capabilities = new top.aihmc.omnimorph.backend2.library.LibraryModels.Capabilities(true, 1,
                1024 * 1024, 64, 1024 * 1024, 1024, 1024, java.util.List.of("zip", "ysm"),
                java.util.List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"),
                java.util.List.of("full_model", "decorative_parts"), false);
        var screen = new top.aihmc.omnimorph.gui.screens.library.ModelUploadScreen(null, client, capabilities, () -> {});
        var type = screen.getClass();
        field(type, "sessionRequest").set(screen, client.submit(LibraryClient.Lane.CONTROL, exchange -> null));
        var session = (LibraryPreviewSession) field(type, "preview").get(screen);
        field(LibraryPreviewSession.class, "detachedEntity").set(session, player);
        var minecraft = Minecraft.getInstance();
        var inputField = field(Minecraft.class, "lastInputType");
        var previousInput = inputField.get(minecraft);
        inputField.set(minecraft, net.minecraft.client.InputType.MOUSE);
        var parent = minecraft.screen;
        Path directory = Files.createTempDirectory(FiguraMod.getFiguraDirectory(), "upload-cosmetic-");
        String source = """
                {"name":"Upload accessory","authors":["Author"],"category":"Trail","cosmetic_anchor":"hip"}
                """;
        Files.writeString(directory.resolve("cosmetic.json"), source);
        Files.writeString(directory.resolve("script.lua"), "return 1");
        try {
            minecraft.screen = screen;
            screen.width = 640;
            screen.height = 360;
            var initialize = type.getDeclaredMethod("init");
            initialize.setAccessible(true);
            initialize.invoke(screen);
            var purpose = type.getDeclaredMethod("changePurpose", boolean.class);
            purpose.setAccessible(true);
            purpose.invoke(screen, true);
            var select = type.getDeclaredMethod("selectFile", Path.class);
            select.setAccessible(true);
            select.invoke(screen, directory);
            awaitCondition(() -> field(type, "prepared").get(screen) != null, drain);
            var pending = (java.util.concurrent.CompletableFuture<?>) field(type, "draftPreview").get(screen);
            require(Boolean.TRUE.equals(await(pending, drain)) && session.outfitComposition() != null
                            && field(LibraryPreviewSession.class, "avatar").get(session) == null,
                    "upload previews a real private accessory composition over vanilla, never a base Avatar");
            var composition = session.outfitComposition();
            require(composition.editorSnapshot(null).configuration().instances().size() == 1,
                    "upload accessory preview contains exactly its declared isolated instance");
            @SuppressWarnings("unchecked")
            var fields = (java.util.List<top.aihmc.omnimorph.gui.widgets.TextField>) field(type, "editableFields").get(screen);
            fields.stream().filter(widget -> widget.getField().getValue().equals("hip")).findFirst().orElseThrow()
                    .getField().setValue("head_anchor");
            var declaration = type.getDeclaredMethod("accessoryDeclaration");
            declaration.setAccessible(true);
            var edited = (com.google.gson.JsonObject) declaration.invoke(screen);
            require(edited.get("cosmetic_anchor").getAsString().equals("head_anchor")
                            && edited.getAsJsonArray("authors").get(0).getAsString().equals("Author")
                            && Files.readString(directory.resolve("cosmetic.json")).equals(source),
                    "actual upload fields edit the anchor while preserving readonly authors and original files");
            var preview = type.getDeclaredMethod("previewDraft");
            preview.setAccessible(true);
            require(Boolean.TRUE.equals(await((java.util.concurrent.CompletableFuture<?>) preview.invoke(screen), drain))
                            && session.outfitComposition().editorSnapshot(null).configuration().instances().getFirst().anchor().name().equals("head_anchor"),
                    "Preview mounts the edited anchor using the actual native accessory path");
            purpose.invoke(screen, false);
            require(field(type, "prepared").get(screen) == null && session.outfitComposition() == null,
                    "purpose changes retire the prepared body and private composition instead of relabeling it");
            screen.removed();
            select.invoke(screen, directory);
            require(field(type, "prepared").get(screen) == null, "closed upload controls cannot restart source preparation");
        } finally {
            screen.removed();
            minecraft.screen = parent;
            inputField.set(minecraft, previousInput);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("PASS accessory upload native form: purpose, metadata prefill/edit, isolated composition, source preservation and stale guards; GUI MANUAL/PENDING");
    }

    private static void testModelFilePicker(LibraryClient client, Runnable drain) throws Exception {
        String originalRoot = Configs.MAIN_DIR.value;
        Path root = Path.of(System.getProperty("figura.test.directory")).resolve("configured-model-root");
        Path avatars = Files.createDirectories(root.resolve("avatars"));
        Path outside = Files.createDirectories(root.resolve("outside"));
        Files.writeString(avatars.resolve("a.ZIP"), "model");
        Files.writeString(avatars.resolve("b.ysm"), "model");
        Files.writeString(avatars.resolve("cover.png"), "image");
        Files.writeString(avatars.resolve("notes.txt"), "ignored");
        Files.createDirectory(avatars.resolve("folder.zip"));
        Path dropped = Files.writeString(outside.resolve("dropped.ysm"), "model");
        try {
            Configs.MAIN_DIR.value = root.toString();
            var picker = new ModelFilePickerScreen(null, client, outside, ModelFilePickerScreen.Kind.MODEL, ignored -> {});
            var pickerType = ModelFilePickerScreen.class;
            require(field(pickerType, "directory").get(picker).equals(avatars),
                    "model picker follows the configured avatars directory rather than a previous external file");
            var navigate = pickerType.getDeclaredMethod("navigate", Path.class);
            navigate.setAccessible(true);
            navigate.invoke(picker, outside);
            require(field(pickerType, "directory").get(picker).equals(avatars), "model picker cannot navigate outside avatars");
            field(pickerType, "rows").set(picker, new LibraryRowList(0, 0, 100, 100));
            var load = pickerType.getDeclaredMethod("load");
            load.setAccessible(true);
            load.invoke(picker);
            var request = (LibraryClient.Request<?>) field(pickerType, "request").get(picker);
            Object listing = await(request.future(), drain);
            var items = listing.getClass().getDeclaredMethod("items");
            items.setAccessible(true);
            var entries = (java.util.List<?>) items.invoke(listing);
            var names = new java.util.ArrayList<String>();
            for (Object entry : entries) {
                var path = entry.getClass().getDeclaredMethod("path");
                path.setAccessible(true);
                names.add(((Path) path.invoke(entry)).getFileName().toString());
            }
            require(names.equals(java.util.List.of("a.ZIP", "b.ysm")),
                    "actual asynchronous picker lists only zip/ysm files, excluding folders and other extensions");
            picker.removed();
            Path cosmetics = Files.createDirectories(root.resolve("cosmetics"));
            Path leaf = Files.createDirectory(cosmetics.resolve("装饰"));
            Files.writeString(leaf.resolve("cosmetic.json"), "{}");
            Files.createDirectory(cosmetics.resolve("分组"));
            Files.writeString(cosmetics.resolve("accessory.zip"), "fixture");
            Files.writeString(cosmetics.resolve("raw.ysm"), "wrapper required");
            var accessoryPicker = new ModelFilePickerScreen(null, client, cosmetics,
                    ModelFilePickerScreen.Kind.ACCESSORY, ignored -> {});
            field(pickerType, "rows").set(accessoryPicker, new LibraryRowList(0, 0, 100, 100));
            load.invoke(accessoryPicker);
            var accessoryRequest = (LibraryClient.Request<?>) field(pickerType, "request").get(accessoryPicker);
            Object accessoryListing = await(accessoryRequest.future(), drain);
            var accessoryEntries = (java.util.List<?>) items.invoke(accessoryListing);
            var selectableNames = new java.util.HashSet<String>();
            for (Object entry : accessoryEntries) {
                var selectable = entry.getClass().getDeclaredMethod("selectable");
                var path = entry.getClass().getDeclaredMethod("path");
                selectable.setAccessible(true);
                path.setAccessible(true);
                if ((boolean) selectable.invoke(entry)) {
                    selectableNames.add(((Path) path.invoke(entry)).getFileName().toString());
                }
            }
            require(accessoryEntries.size() == 3 && selectableNames.equals(java.util.Set.of("装饰", "accessory.zip")),
                    "accessory picker selects manifest folders/ZIPs, navigates grouping folders and excludes unwrapped YSM");
            accessoryPicker.removed();
            var images = new ModelFilePickerScreen(null, client, outside, ModelFilePickerScreen.Kind.IMAGE, ignored -> {});
            require(field(pickerType, "directory").get(images).equals(outside), "image picker retains its original directory behavior");
            require(ModelFilePickerScreen.accepts(dropped, ModelFilePickerScreen.Kind.MODEL),
                    "dragged model acceptance remains independent from the fixed chooser directory");
            Path secondRoot = Files.createDirectories(root.resolve("second-root"));
            Configs.MAIN_DIR.value = secondRoot.toString();
            var changed = new ModelFilePickerScreen(null, client, outside, ModelFilePickerScreen.Kind.MODEL, ignored -> {});
            require(field(pickerType, "directory").get(changed).equals(secondRoot.resolve("avatars")),
                    "a newly opened picker uses the current folder setting");
            System.out.println("PASS model picker: configured avatars root, fixed navigation, real file filtering and external drag acceptance");
        } finally {
            Configs.MAIN_DIR.value = originalRoot;
        }
    }

    private static final class ArmorPreviewPlayer extends RemotePlayer implements top.aihmc.omnimorph.mixin.EntityAccessor {
        private int equipmentReads;
        private int capeReads;

        private ArmorPreviewPlayer() {
            super(null, new GameProfile(UUID.randomUUID(), "preview"));
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return Minecraft.getInstance().level;
        }

        @Override
        public net.minecraft.world.entity.player.PlayerSkin getSkin() {
            return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
        }

        @Override
        public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) {
            return true;
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            equipmentReads++;
            return ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.client.entity.ClientAvatarState avatarState() {
            capeReads++;
            throw new CapeStateReached();
        }
    }

    public static final class CapeStateReached extends RuntimeException {
    }

    private static CompoundTag model(String name) {
        var metadata = new CompoundTag();
        metadata.putString("name", name);
        var nbt = new CompoundTag();
        nbt.put("metadata", metadata);
        return nbt;
    }

    private static java.lang.reflect.Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T await(CompletableFuture<T> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
