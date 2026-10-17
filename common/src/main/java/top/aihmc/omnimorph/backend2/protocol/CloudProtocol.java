package top.aihmc.omnimorph.backend2.protocol;

import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Business operations shared by the two wire adapters. Files remain raw HTTP bodies. */
public interface CloudProtocol extends AutoCloseable {
    enum Kind {
        FIGURA_CLOUD, PROTOBUF
    }

    static CloudProtocol create(Kind kind, java.net.URI origin, String token, String version,
                                long epoch, java.net.http.HttpClient client) {
        return create(kind, CloudEndpoint.parse(origin.toString(), () -> false), token, version, epoch, client);
    }

    static CloudProtocol create(Kind kind, CloudEndpoint endpoint, String token, String version,
                                long epoch, java.net.http.HttpClient client) {
        return switch (kind) {
            case FIGURA_CLOUD -> new FiguraCloudProtocol(endpoint, token, client);
            case PROTOBUF -> new ProtobufCloudProtocol(endpoint, token, version, epoch, client);
        };
    }

    record Reply(int status, String body) {}

    record Metadata(int modelType, String contentHash, String schemaHash) {
        public static Metadata figura() {
            return new Metadata(1, "", "");
        }
    }

    record Resource(UUID owner, String id, String cacheKey, String rawHash, long revision,
                    long length, int modelType, int format) {}

    record Download(Resource resource, InputStream stream) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            stream.close();
        }
    }

    interface Listener {
        void ready();
        void ping(UUID owner, int functionId, boolean echo, byte[] arguments);
        void changed(UUID owner);
        void ysm(YsmSyncMessage message);
        void snapshotRequested();
        void closed(String reason);
        default void notification(int kind, int type, String message, String detail) {}
    }

    void connect(Listener listener) throws Exception;
    boolean connected();
    String instance();
    Reply profile(UUID owner) throws Exception;
    Reply limits() throws Exception;
    Reply version() throws Exception;
    Reply motd() throws Exception;
    Reply checkAuth() throws Exception;
    default Reply diagnostic(String path) throws Exception {
        return new Reply(400, "Raw Figura routes require the Figura Cloud protocol");
    }
    Reply upload(String id, byte[] bytes, Metadata metadata) throws Exception;
    default Reply upload(String id, byte[] bytes, Metadata metadata,
                         java.util.function.LongConsumer progress) throws Exception {
        progress.accept(0);
        Reply result = upload(id, bytes, metadata);
        if (result.status() == 200) {
            progress.accept(bytes.length);
        }
        return result;
    }

    Reply delete(String id) throws Exception;
    Reply equip(List<UUID> owners, List<String> assets) throws Exception;
    Download download(UUID owner, String id) throws Exception;
    void ping(int functionId, boolean echo, byte[] arguments) throws Exception;
    void subscribe(UUID owner) throws Exception;
    void unsubscribe(UUID owner) throws Exception;
    boolean publish(YsmSyncMessage message) throws Exception;
    void tick(long tick) throws Exception;
    @Override
    void close();
}
