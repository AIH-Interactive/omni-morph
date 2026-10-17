package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.HttpAPI;
import top.aihmc.omnimorph.backend2.protocol.CloudConnection;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.config.Configs;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses the existing native CPU fixture to exercise the actual Configs -> reAuth lifecycle. */
final class CloudProtocolLifecycleSmokeTest {
    @SuppressWarnings("unchecked")
    static void run(YsmModelRuntime local) throws Exception {
        var usersField = AvatarManager.class.getDeclaredField("LOADED_USERS");
        usersField.setAccessible(true);
        var users = (Map<UUID, UserData>) usersField.get(null);
        var connectionField = NetworkStuff.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        require(connectionField.get(null) == null, "Isolated cloud lifecycle fixture");
        var workField = NetworkStuff.class.getDeclaredField("tasks");
        workField.setAccessible(true);
        String previousAddress = Configs.SERVER_IP.value;
        String previousTempAddress = Configs.SERVER_IP.tempValue;
        int previousChoice = Configs.CLOUD_PROTOCOL.value;
        int previousTempChoice = Configs.CLOUD_PROTOCOL.tempValue;
        boolean previousToasts = Configs.CONNECTION_TOASTS.value;
        boolean previousDebug = Configs.DEBUG_MODE.value;
        boolean previousTempDebug = Configs.DEBUG_MODE.tempValue;
        boolean uploaded = AvatarManager.localUploaded;
        UserData resident = new UserData(local.owner().owner);
        resident.getAvatars().add(local.owner());
        UserData previous = users.put(local.owner().owner, resident);
        AtomicInteger staleApplied = new AtomicInteger();
        HttpClient testClient = HttpClient.newHttpClient();
        var scope = new CloudConnection(NetworkStuff.connectionEpoch(),
                new FiguraCloudProtocol(URI.create("https://127.0.0.1:1"), "synthetic-unused", HttpClient.newHttpClient()));
        try {
            require(Configs.CLOUD_PROTOCOL.defaultValue == 1 && Configs.CLOUD_PROTOCOL.enumList.size() == 2,
                    "Production Configs exposes two enum entries and Protobuf default");
            Configs.SERVER_IP.value = Configs.SERVER_IP.tempValue = "127.0.0.1:1";
            Configs.CONNECTION_TOASTS.value = false;
            Configs.CLOUD_PROTOCOL.value = Configs.CLOUD_PROTOCOL.tempValue = 0;
            AvatarManager.localUploaded = false;
            connectionField.set(null, scope);
            scope.post(128, staleApplied::incrementAndGet);
            long before = NetworkStuff.connectionEpoch();
            Configs.CLOUD_PROTOCOL.setValue("PROTOBUF");
            require(NetworkStuff.connectionEpoch() > before && !scope.active(),
                    "Actual protocol setting disconnects and advances epoch immediately");
            scope.drain();
            require(staleApplied.get() == 0, "Actual switch discards old callbacks");
            var authentication = (CompletableFuture<?>) workField.get(null);
            require(authentication != null, "Actual switch schedules authentication");
            authentication.get(10, TimeUnit.SECONDS);
            require(users.get(local.owner().owner) == resident && resident.getMainAvatar() == local.owner()
                            && !local.isClosed() && !AvatarManager.localUploaded,
                    "Unavailable Protobuf keeps the loaded local model and local selection");
            NetworkStuff.disconnect("synthetic-offline");
            require(resident.getMainAvatar() == local.owner() && !local.isClosed(), "Offline disconnect preserves loader output");
            System.out.println("B6 actual Configs lifecycle: switch disconnect/epoch/re-auth, stale callback discard and offline local runtime retention PASS");
            for (CloudProtocol.Kind kind : CloudProtocol.Kind.values()) {
                Configs.SERVER_IP.value = Configs.SERVER_IP.tempValue = "http://127.0.0.1:1";
                Configs.CLOUD_PROTOCOL.value = Configs.CLOUD_PROTOCOL.tempValue = kind == CloudProtocol.Kind.PROTOBUF ? 1 : 0;
                Configs.DEBUG_MODE.value = Configs.DEBUG_MODE.tempValue = true;
                var captured = HttpAPI.endpoint();
                var active = new CloudConnection(NetworkStuff.connectionEpoch(),
                        CloudProtocol.create(kind, captured, "synthetic-unused", "test", NetworkStuff.connectionEpoch(), testClient));
                connectionField.set(null, active);
                AtomicInteger late = new AtomicInteger();
                active.post(128, late::incrementAndGet);
                var authField = NetworkStuff.class.getDeclaredField("authenticationWork");
                authField.setAccessible(true);
                var auth = new CloudHttp(testClient, captured);
                authField.set(null, auth);
                long activeEpoch = NetworkStuff.connectionEpoch();
                Configs.DEBUG_MODE.setTempValue("false");
                require(active.active(), "Temporary Debug edit does not retire active connection");
                Configs.DEBUG_MODE.setValue("false");
                require(!active.active() && NetworkStuff.connectionEpoch() > activeEpoch && authField.get(null) == null,
                        "Actual Debug apply retires both protocols, authentication and epoch immediately");
                active.drain();
                require(late.get() == 0, "Debug revocation discards queued callbacks");
                try {
                    auth.requireOpen();
                    throw new AssertionError("Authentication survived revocation");
                } catch (java.io.IOException expected) {
                    // The registered authentication transport was actually closed.
                }
                NetworkStuff.reAuth();
                require(connectionField.get(null) == null && authField.get(null) == null,
                        "Explicit retry cannot reopen plaintext with Debug disabled");
                Configs.DEBUG_MODE.value = true;
                var lateEndpoint = HttpAPI.endpoint();
                Configs.SERVER_IP.value = "https://different.test";
                try {
                    lateEndpoint.requireAllowed();
                    throw new AssertionError("Old authentication endpoint survived address edit");
                } catch (java.io.IOException expected) {
                    require(expected.getMessage().equals("backend.endpoint.retired"), "Late auth rejected before next tick");
                }
                require(resident.getMainAvatar() == local.owner() && !local.isClosed(), "Debug revocation retains local runtime");
            }
            System.out.println("B7 actual Configs lifecycle: dual-protocol Debug revocation, authentication cancellation, retry/late callback rejection and local retention PASS");
        } finally {
            NetworkStuff.disconnect("fixture-cleanup");
            if (previous == null) {
                users.remove(local.owner().owner);
            } else {
                users.put(local.owner().owner, previous);
            }
            Configs.SERVER_IP.value = previousAddress;
            Configs.SERVER_IP.tempValue = previousTempAddress;
            Configs.CLOUD_PROTOCOL.value = previousChoice;
            Configs.CLOUD_PROTOCOL.tempValue = previousTempChoice;
            Configs.CONNECTION_TOASTS.value = previousToasts;
            Configs.DEBUG_MODE.value = previousDebug;
            Configs.DEBUG_MODE.tempValue = previousTempDebug;
            AvatarManager.localUploaded = uploaded;
            scope.close();
            testClient.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
