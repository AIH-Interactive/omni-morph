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
            testWardrobeDefaultPreview(unsafe);
            testPreviewAppearance(unsafe);
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
            for (Permissions permission : new Permissions[]{Permissions.NETWORKING, Permissions.PARTICLES,
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
            testCurrentPlayerFigura(unsafe, drain);
        } finally {
            LibraryPreviewBinding.finishFrame();
            drain.run();
        }
    }

    private static void testCurrentPlayerFigura(Unsafe unsafe, Runnable drain) throws Exception {
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
            System.out.println("PASS independent Figura drawing: real Lua render, native body vertices, script visibility and no entity layers");
        } finally {
            field(Minecraft.class, "gameRenderer").set(client, oldRenderer);
            field(Minecraft.class, "debugEntries").set(client, oldDebug);
        }
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
        } finally {
            screen.removed();
            fontField.set(minecraft, previousFont);
        }
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
