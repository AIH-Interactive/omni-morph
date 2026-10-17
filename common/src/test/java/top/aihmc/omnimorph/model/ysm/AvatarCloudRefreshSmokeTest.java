package top.aihmc.omnimorph.model.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.protocol.CloudConnection;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.config.Configs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses actual profile/cache/download/UserData publication with a bounded recording cloud transport. */
final class AvatarCloudRefreshSmokeTest {
    @SuppressWarnings("unchecked")
    static void run(Runnable drain) throws Exception {
        var connection = NetworkStuff.class.getDeclaredField("connection");
        connection.setAccessible(true);
        Object previousConnection = connection.get(null);
        var process = NetworkStuff.class.getDeclaredMethod("processRequests");
        process.setAccessible(true);
        var work = NetworkStuff.class.getDeclaredField("tasks");
        work.setAccessible(true);
        var usersField = AvatarManager.class.getDeclaredField("LOADED_USERS");
        usersField.setAccessible(true);
        var users = (Map<UUID, UserData>) usersField.get(null);
        var lastPath = LocalAvatarLoader.class.getDeclaredField("lastLoadedPath");
        lastPath.setAccessible(true);
        Object previousPath = lastPath.get(null);
        int previousStatus = NetworkStuff.backendStatus;
        boolean previousUploaded = AvatarManager.localUploaded;
        boolean previousToasts = Configs.CONNECTION_TOASTS.value;
        UUID player = FiguraMod.getLocalPlayerUUID();
        UUID resourceOwner = UUID.randomUUID();
        UserData previousUser = users.get(player);
        var user = new UserData(player);
        var preview = new Avatar(player);
        user.getAvatars().add(preview);
        String name = "cloud model " + resourceOwner;
        var metadata = new CompoundTag();
        metadata.putString("name", name);
        var nbt = new CompoundTag();
        nbt.put("metadata", metadata);
        var output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(nbt, output);
        byte[] bytes = output.toByteArray();
        String hash = CloudHttp.sha256(bytes);
        var resource = new CloudProtocol.Resource(resourceOwner, "shared_asset", hash, hash, 1, bytes.length, 1, 1);
        var status = new AtomicInteger(200);
        var profiles = new AtomicInteger();
        var downloads = new AtomicInteger();
        var protocol = (CloudProtocol) Proxy.newProxyInstance(CloudProtocol.class.getClassLoader(),
                new Class<?>[]{CloudProtocol.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "connected" -> true;
                    case "close" -> null;
                    case "profile" -> {
                        require(player.equals(arguments[0]), "refresh requests the actual player UUID");
                        profiles.incrementAndGet();
                        yield new CloudProtocol.Reply(status.get(), "{\"equipped\":[{\"owner\":\"" + resourceOwner
                                + "\",\"id\":\"shared_asset\",\"hash\":\"" + hash
                                + "\"}],\"equippedBadges\":{\"pride\":[],\"special\":[]}}");
                    }
                    case "download" -> {
                        require(resourceOwner.equals(arguments[0]) && "shared_asset".equals(arguments[1]),
                                "Figura matching uses the equipped resource owner/id, independent of player/model names");
                        downloads.incrementAndGet();
                        yield new CloudProtocol.Download(resource, new ByteArrayInputStream(bytes));
                    }
                    default -> throw new AssertionError("Refresh must not upload, equip or re-authenticate: " + method.getName());
                });
        var scope = new CloudConnection(NetworkStuff.connectionEpoch(), protocol);
        try {
            users.put(player, user);
            connection.set(null, scope);
            NetworkStuff.backendStatus = 3;
            Configs.CONNECTION_TOASTS.value = true;
            lastPath.set(null, java.nio.file.Path.of("synthetic-local-preview"));
            AvatarManager.localUploaded = false;
            long epoch = NetworkStuff.connectionEpoch();
            status.set(503);
            AvatarManager.reloadCloudAvatar();
            require(connection.get(null) == scope && NetworkStuff.connectionEpoch() == epoch,
                    "connected wardrobe refresh retains the authenticated cloud connection");
            require(LocalAvatarLoader.getLastLoadedPath() == null && user.getMainAvatar() == preview,
                    "refresh leaves local preview mode but retains the old model during the request");
            runRequests(process, work, scope);
            require(user.getMainAvatar() == preview && !AvatarManager.localUploaded,
                    "failed refresh must not advertise the retained local preview as uploaded");
            status.set(200);
            AvatarManager.reloadCloudAvatar();
            runRequests(process, work, scope);
            runRequests(process, work, scope);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while ((user.getMainAvatar() == preview || user.loading.snapshot() != null) && System.nanoTime() < deadline) {
                drain.run();
                Thread.sleep(1);
            }
            require(user.getMainAvatar() != null && user.getMainAvatar().name.equals(name)
                    && user.getMainAvatar().owner.equals(player) && profiles.get() == 2 && downloads.get() == 1
                    && AvatarManager.localUploaded,
                    "equipped cloud bytes publish on the target player after Figura owner/id/hash matching");

            Avatar cloud = user.getMainAvatar();
            status.set(503);
            AvatarManager.reloadCloudAvatar();
            runRequests(process, work, scope);
            require(user.getMainAvatar() == cloud, "backend failure retains the visible cloud model");

            status.set(404);
            AvatarManager.reloadCloudAvatar();
            runRequests(process, work, scope);
            require(user.getMainAvatar() == null && user.loading.snapshot() == null && !AvatarManager.localUploaded,
                    "a missing UUID profile means no equipped model and clears loading without a user-not-found error toast");
            require(profiles.get() == 4 && downloads.get() == 1, "refresh does not match by name or invent another account");
            System.out.println("PASS wardrobe cloud refresh: current UUID, retained authentication/model, Figura resource matching, 404 empty selection and 503 retention; GUI MANUAL/PENDING");
        } finally {
            NetworkStuff.clear(player);
            NetworkStuff.localSelectionChanged();
            user.clear();
            drain.run();
            if (previousUser == null) {
                users.remove(player);
            } else {
                users.put(player, previousUser);
            }
            lastPath.set(null, previousPath);
            AvatarManager.localUploaded = previousUploaded;
            Configs.CONNECTION_TOASTS.value = previousToasts;
            scope.close();
            connection.set(null, previousConnection);
            NetworkStuff.backendStatus = previousStatus;
        }
    }

    private static void runRequests(java.lang.reflect.Method process, java.lang.reflect.Field work,
                                    CloudConnection scope) throws Exception {
        process.invoke(null);
        var task = (CompletableFuture<?>) work.get(null);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!task.isDone() && System.nanoTime() < deadline) {
            scope.drain();
            Thread.sleep(1);
        }
        task.get(1, TimeUnit.SECONDS);
        scope.drain();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
