package top.aihmc.omnimorph.backend2.protocol;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Real adapter/HTTP replay of the reported COMMIT_UPLOAD 429; no live account or backend required. */
final class ProtobufUploadRetrySmokeTest {
    static void run() throws Exception {
        try (var server = new UploadServer(1, Cloud.ErrorCode.LIMIT_EXCEEDED);
             var protocol = server.protocol()) {
            require(protocol.upload("avatar", server.bytes, CloudProtocol.Metadata.figura()).status() == 200,
                    "a transient commit limit recovers without restarting the upload");
            require(server.reservations.get() == 1 && server.rawUploads.get() == 1 && server.commits.size() == 2,
                    "commit retry reuses the uploaded body instead of consuming another upload slot");
            require(server.commits.getFirst().equals(server.commits.getLast()),
                    "commit retry retains the operation ID, length and digest");
        }
        try (var server = new UploadServer(100, Cloud.ErrorCode.LIMIT_EXCEEDED);
             var protocol = server.protocol()) {
            expectFailure(protocol, server, Cloud.ErrorCode.LIMIT_EXCEEDED);
            require(server.commits.size() == 3 && server.reservations.get() == 1 && server.rawUploads.get() == 1,
                    "persistent limits stop after three commit attempts without leaking new upload grants");
        }
        try (var server = new UploadServer(100, Cloud.ErrorCode.FORBIDDEN);
             var protocol = server.protocol()) {
            expectFailure(protocol, server, Cloud.ErrorCode.FORBIDDEN);
            require(server.commits.size() == 1, "permission failures are not retried");
        }
        System.out.println("PASS Protobuf commit recovery: transient 429, same operation/body, bounded persistent limit and permission failure");
    }

    private static void expectFailure(ProtobufCloudProtocol protocol, UploadServer server, Cloud.ErrorCode code)
            throws Exception {
        try {
            protocol.upload("avatar", server.bytes, CloudProtocol.Metadata.figura());
            throw new AssertionError("expected commit failure " + code);
        } catch (ProtobufCloudProtocol.Failure failure) {
            require(failure.code == code, "commit retains the original protocol error");
        }
    }

    private static final class UploadServer implements AutoCloseable {
        private static final UUID OWNER = UUID.fromString("10000000-0000-4000-8000-000000000998");
        private final HttpServer server;
        private final int rejectedCommits;
        private final Cloud.ErrorCode failure;
        private final byte[] bytes = new byte[200_000];
        private final AtomicInteger reservations = new AtomicInteger();
        private final AtomicInteger rawUploads = new AtomicInteger();
        private final List<Cloud.CommitUpload> commits = new ArrayList<>();
        private Cloud.AvatarDescriptor descriptor;

        private UploadServer(int rejectedCommits, Cloud.ErrorCode failure) throws IOException {
            this.rejectedCommits = rejectedCommits;
            this.failure = failure;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private ProtobufCloudProtocol protocol() throws Exception {
            var endpoint = CloudEndpoint.parse("http://127.0.0.1:" + server.getAddress().getPort(), () -> true);
            var protocol = new ProtobufCloudProtocol(endpoint, "synthetic", "test", 7, HttpClient.newHttpClient());
            // Exercise the actual upload adapter in an already-authenticated scope, without a websocket fixture.
            set(protocol, "owner", OWNER);
            set(protocol, "session", "synthetic-session");
            set(protocol, "capabilities", Cloud.HelloAck.newBuilder().addCapabilities(Cloud.Capability.FIGURA)
                    .setLimits(Cloud.Limits.newBuilder().setUploadBytes(1_000_000).setEnvelopeBytes(65536)).build());
            return protocol;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (exchange.getRequestMethod().equals("PUT")) {
                    if (!Arrays.equals(bytes, exchange.getRequestBody().readAllBytes())) {
                        exchange.sendResponseHeaders(400, -1);
                        return;
                    }
                    rawUploads.incrementAndGet();
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                var request = Cloud.Envelope.parseFrom(exchange.getRequestBody().readAllBytes());
                var response = Cloud.Envelope.newBuilder().setMajor(1).setRequestId(request.getRequestId())
                        .setSessionId(request.getSessionId()).setConnectionEpoch(request.getConnectionEpoch());
                int status = 200;
                switch (request.getBodyCase()) {
                    case UPLOAD_REQUEST -> {
                        reservations.incrementAndGet();
                        descriptor = request.getUploadRequest().getResource().toBuilder().setRevision(1).build();
                        response.setTransferGrant(Cloud.TransferGrant.newBuilder()
                                .setOperationId(request.getUploadRequest().getOperationId())
                                .setRelativePath("/api/protobuf/v1/resources/uploads/test")
                                .setExpiresUnixSeconds(System.currentTimeMillis() / 1000 + 120).setMaxBytes(bytes.length));
                    }
                    case COMMIT_UPLOAD -> {
                        commits.add(request.getCommitUpload());
                        if (commits.size() <= rejectedCommits) {
                            status = failure == Cloud.ErrorCode.LIMIT_EXCEEDED ? 429 : 403;
                            response.setError(Cloud.Error.newBuilder().setCode(failure).setRetryable(status == 429));
                        } else {
                            response.setAvatar(descriptor);
                        }
                    }
                    default -> {
                        status = 400;
                        response.setError(Cloud.Error.newBuilder().setCode(Cloud.ErrorCode.INVALID_MESSAGE));
                    }
                }
                byte[] encoded = response.build().toByteArray();
                exchange.getResponseHeaders().set("Content-Type", ProtobufCloudProtocol.CONTENT_TYPE);
                exchange.sendResponseHeaders(status, encoded.length);
                exchange.getResponseBody().write(encoded);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
