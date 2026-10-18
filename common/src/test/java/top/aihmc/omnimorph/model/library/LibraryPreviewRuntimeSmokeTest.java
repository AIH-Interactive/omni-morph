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
             var preview = new LibraryPreviewSession(client)) {
            testModelFilePicker(client, drain);
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
                var latest = preview.selectDraft(model("latest"));
                require(!first.isDone() && !latest.isDone(), "preview selection never waits for the model worker");
                release.countDown();
                gate.get(5, TimeUnit.SECONDS);
                require(!await(first, drain) && await(latest, drain), "only the newest preview generation publishes");
            } finally {
                release.countDown();
            }
            Avatar avatar = AvatarManager.getAvatar(entity);
            require(preview.showsVanillaPlayer(), "Figura parts retain the vanilla player unless the model hides it");
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
            var armorFrame = new PreviewFrame();
            LibraryPreviewBinding.capture(entity, armorFrame);
            var lifetime = avatar.accelerationOwner();
            preview.clear();
            require(AvatarManager.getAvatar(firstFrame) == avatar && AvatarManager.getEntity(secondFrame) == entity,
                    "queued render states retain their detached entity and model after selection clears");
            require(lifetime.isCurrent(), "clearing a preview cannot destroy resources before deferred GUI drawing");
            if (Boolean.getBoolean("omnimorph.test.armorMixinRegression")) {
                Class.forName("top.aihmc.omnimorph.model.library.LibraryArmorPreviewRegression")
                        .getMethod("run", AvatarRenderState.class).invoke(null, armorFrame);
                require(entity.equipmentReads == 1, "armor rendering reads equipment from the detached preview entity");
                require(entity.capeReads == 1, "cape rendering reads state from the detached preview player");
            }
            LibraryPreviewBinding.finishFrame();
            require(!lifetime.isCurrent(), "finishing the GUI frame retires the model after its final state releases");
            LibraryPreviewBinding.finishFrame();

            CompoundTag hidden = model("full Figura replacement");
            var scripts = new CompoundTag();
            scripts.putByteArray("script", "vanilla_model.PLAYER:setVisible(false)".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            hidden.put("scripts", scripts);
            require(await(preview.selectDraft(hidden), drain), "actual Figura script initializes in the detached preview");
            require(!preview.showsVanillaPlayer(), "a Figura model hiding the vanilla player keeps the complete-model default");

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
        } finally {
            LibraryPreviewBinding.finishFrame();
            drain.run();
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

    private static final class ArmorPreviewPlayer extends RemotePlayer {
        private int equipmentReads;
        private int capeReads;

        private ArmorPreviewPlayer() {
            super(null, new GameProfile(UUID.randomUUID(), "preview"));
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

    private static final class PreviewFrame extends AvatarRenderState
            implements top.aihmc.omnimorph.ducks.FiguraEntityRenderStateExtension {
        private int entityId;
        private float delta;

        public Integer figura$getEntityId() {
            return entityId;
        }

        public void figura$setEntityId(int id) {
            entityId = id;
        }

        public float figura$getTickDelta() {
            return delta;
        }

        public void figura$setTickDelta(float tickDelta) {
            delta = tickDelta;
        }
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
