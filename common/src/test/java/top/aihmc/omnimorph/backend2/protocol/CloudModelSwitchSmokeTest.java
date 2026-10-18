package top.aihmc.omnimorph.backend2.protocol;

import com.google.protobuf.ByteString;
import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Replays actual protobuf queue/receive callbacks; no live server or GUI claim. */
public final class CloudModelSwitchSmokeTest {
    public static void main(String[] args) throws Exception {
        for (var code : new Cloud.ErrorCode[]{Cloud.ErrorCode.UNSUPPORTED_CAPABILITY,
                Cloud.ErrorCode.CONFLICT, Cloud.ErrorCode.NOT_FOUND, Cloud.ErrorCode.UNAUTHENTICATED}) {
            try (var client = HttpClient.newHttpClient();
                 var protocol = new ProtobufCloudProtocol(URI.create("https://127.0.0.1:1"), "synthetic", "test", 7, client)) {
                UUID owner = new UUID(0, 1);
                var recovery = new AtomicInteger();
                var closed = new AtomicInteger();
                var listener = Proxy.newProxyInstance(CloudProtocol.Listener.class.getClassLoader(),
                        new Class<?>[]{CloudProtocol.Listener.class}, (proxy, method, values) -> {
                            if (method.getName().equals("snapshotRequested")) {
                                recovery.incrementAndGet();
                            } else if (method.getName().equals("closed")) {
                                closed.incrementAndGet();
                            }
                            return null;
                        });
                set(protocol, "owner", owner);
                set(protocol, "session", "fixture");
                set(protocol, "ready", true);
                set(protocol, "listener", listener);
                set(protocol, "capabilities", Cloud.HelloAck.newBuilder()
                        .setLimits(Cloud.Limits.newBuilder().setEnvelopeBytes(65536).setQueueBytes(262144)).build());
                var old = Cloud.AvatarDescriptor.newBuilder().setOwner(owner.toString()).setAssetId("avatar")
                        .setRevision(1).setModelTypeValue(2).setDistributionFormatValue(2)
                        .setRawSha256(ByteString.copyFrom(new byte[32])).setContentSha256(ByteString.copyFrom(new byte[32]))
                        .setSchemaSha256(ByteString.copyFrom(new byte[32])).build();
                invoke(protocol, "rememberEquipment", Cloud.EquippedState.class,
                        Cloud.EquippedState.newBuilder().setOwner(owner.toString()).setRevision(1).setPrimary(old).build());
                invoke(protocol, "send", Cloud.Envelope.Builder.class,
                        Cloud.Envelope.newBuilder().setYsmSnapshot(Cloud.YsmSnapshot.newBuilder()));
                long request = ((AtomicLong) get(protocol, "requestIds")).get();
                var replacement = old.toBuilder().setRevision(2).setModelTypeValue(1).setDistributionFormatValue(1).build();
                invoke(protocol, "rememberEquipment", Cloud.EquippedState.class,
                        Cloud.EquippedState.newBuilder().setOwner(owner.toString()).setRevision(2).setPrimary(replacement).build());
                var error = Cloud.Envelope.newBuilder().setMajor(1).setConnectionEpoch(7).setSessionId("fixture")
                        .setRequestId(request).setError(Cloud.Error.newBuilder().setCode(code)).build();
                invoke(protocol, "receive", byte[].class, error.toByteArray());
                if (code == Cloud.ErrorCode.UNAUTHENTICATED) {
                    require(!protocol.connected() && closed.get() == 1, "authentication failure remains fatal");
                } else {
                    require(protocol.connected() && closed.get() == 0 && recovery.get() == 1,
                            "late old-model " + code + " must not disconnect the current model");
                    require(((Deque<?>) get(protocol, "pending")).isEmpty(), "old queued model states are retired");
                    var uncorrelated = error.toBuilder().setRequestId(request + 1000).build();
                    invoke(protocol, "receive", byte[].class, uncorrelated.toByteArray());
                    require(!protocol.connected(), "uncorrelated protocol errors remain fatal");
                }
            }
        }
        System.out.println("PASS model switch: stale YSM queue retirement, correlated recovery and fatal authentication/protocol errors");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name, Class<?> type, Object value) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
