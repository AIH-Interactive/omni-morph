package top.aihmc.omnimorph.model.ysm;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.protocol.CloudConnection;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AvatarBroadcastSmokeTest {
    @SuppressWarnings("unchecked")
    static void run(UserData resident, Runnable drain) throws Exception {
        var connection = NetworkStuff.class.getDeclaredField("connection");
        connection.setAccessible(true);
        Object previousConnection = connection.get(null);
        int previousStatus = NetworkStuff.backendStatus;
        var process = NetworkStuff.class.getDeclaredMethod("processRequests");
        process.setAccessible(true);
        var tasks = NetworkStuff.class.getDeclaredField("tasks");
        tasks.setAccessible(true);
        var retry = NetworkStuff.class.getDeclaredMethod("broadcastLocalAvatar");
        retry.setAccessible(true);
        var usersField = AvatarManager.class.getDeclaredField("LOADED_USERS");
        usersField.setAccessible(true);
        var users = (Map<UUID, UserData>) usersField.get(null);
        UUID local = top.aihmc.omnimorph.FiguraMod.getLocalPlayerUUID();
        UserData previousUser = users.get(local);
        var lastPath = LocalAvatarLoader.class.getDeclaredField("lastLoadedPath");
        lastPath.setAccessible(true);
        Object previousPath = lastPath.get(null);
        boolean uploaded = AvatarManager.localUploaded;
        Thread clientThread = Thread.currentThread();
        List<String> uploads = new ArrayList<>();
        List<Integer> equipment = new ArrayList<>();
        AtomicBoolean switchDuringUpload = new AtomicBoolean();
        var protocol = (CloudProtocol) Proxy.newProxyInstance(CloudProtocol.class.getClassLoader(),
                new Class<?>[]{CloudProtocol.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "connected" -> true;
                        case "close" -> null;
                        case "upload" -> {
                            require(Thread.currentThread() != clientThread, "compression/upload run off the game thread");
                            var nbt = NbtIo.readCompressed(new ByteArrayInputStream((byte[]) args[1]), NbtAccounter.unlimitedHeap());
                            uploads.add(nbt.getCompoundOrEmpty("metadata").getStringOr("name", ""));
                            if (switchDuringUpload.getAndSet(false)) {
                                NetworkStuff.localSelectionChanged();
                            }
                            yield new CloudProtocol.Reply(200, "");
                        }
                        case "equip" -> {
                            equipment.add(((List<?>) args[0]).size());
                            yield new CloudProtocol.Reply(200, "");
                        }
                        default -> throw new AssertionError("Unexpected cloud operation: " + method.getName());
                    };
                });
        var scope = new CloudConnection(NetworkStuff.connectionEpoch(), protocol);
        java.nio.file.Path folder = Files.createTempDirectory("omnimorph-reload-all-");
        try {
            connection.set(null, scope);
            NetworkStuff.backendStatus = 3;
            Avatar avatar = resident.getMainAvatar();
            NetworkStuff.localSelectionChanged();
            NetworkStuff.localAvatarReady(avatar);
            NetworkStuff.localSelectionChanged();
            NetworkStuff.localAvatarReady(avatar);
            process.invoke(null);
            ((CompletableFuture<?>) tasks.get(null)).get(10, TimeUnit.SECONDS);
            scope.drain();
            require(uploads.size() == 1 && equipment.equals(List.of(1)), "superseded switch is never uploaded/equipped");
            switchDuringUpload.set(true);
            NetworkStuff.localAvatarReady(avatar);
            process.invoke(null);
            ((CompletableFuture<?>) tasks.get(null)).get(10, TimeUnit.SECONDS);
            scope.drain();
            require(uploads.size() == 2 && equipment.size() == 1, "switch during upload cannot equip the old model");
            NetworkStuff.backendStatus = 1;
            NetworkStuff.localAvatarReady(avatar);
            NetworkStuff.backendStatus = 3;
            retry.invoke(null);
            process.invoke(null);
            ((CompletableFuture<?>) tasks.get(null)).get(10, TimeUnit.SECONDS);
            scope.drain();
            require(uploads.size() == 3 && equipment.size() == 2, "offline selection broadcasts when connection becomes ready");
            NetworkStuff.localSelectionChanged();
            NetworkStuff.localAvatarReady(null);
            process.invoke(null);
            ((CompletableFuture<?>) tasks.get(null)).get(10, TimeUnit.SECONDS);
            scope.drain();
            require(equipment.getLast() == 0, "clearing a selection publishes unequip");

            Files.writeString(folder.resolve("avatar.json"), "{\"name\":\"Reload All restored\"}");
            UserData localUser = new UserData(local);
            users.put(local, localUser);
            lastPath.set(null, folder);
            AvatarManager.localUploaded = true;
            AvatarManager.reloadAllAvatars();
            require(LocalAvatarLoader.getLastLoadedPath().equals(folder), "Reload All retains an uploaded local source path");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (localUser.getMainAvatar() == null && System.nanoTime() < deadline) {
                drain.run();
                Thread.sleep(1);
            }
            require(localUser.getMainAvatar() != null && localUser.getMainAvatar().name.equals("Reload All restored"),
                    "Reload All actually recompiles and republishes the local selection");
            localUser.clear();
            System.out.println("Actual model broadcast: latest-only upload/equip, mid-upload switch, offline replay, unequip and Reload All source restoration PASS");
        } finally {
            NetworkStuff.localSelectionChanged();
            LocalAvatarLoader.loadAvatar(null, null);
            lastPath.set(null, previousPath);
            if (previousUser == null) {
                users.remove(local);
            } else {
                users.put(local, previousUser);
            }
            AvatarManager.localUploaded = uploaded;
            scope.close();
            connection.set(null, previousConnection);
            NetworkStuff.backendStatus = previousStatus;
            Files.deleteIfExists(folder.resolve("avatar.json"));
            Files.delete(folder);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
