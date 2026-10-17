package top.aihmc.omnimorph.backend2;

import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.config.Configs;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;

public class HttpAPI {

    private final String token;
    private final CloudEndpoint endpoint;

    public HttpAPI(String token) {
        this.token = token;
        this.endpoint = endpoint();
    }


    // -- builders -- // 


    public static CloudEndpoint endpoint() {
        return endpoint(NetworkStuff.connectionEpoch());
    }

    public static CloudEndpoint endpoint(long epoch) {
        String address = Configs.SERVER_IP.value;
        var kind = Configs.CLOUD_PROTOCOL.kind();
        return CloudEndpoint.parse(address, () -> Configs.DEBUG_MODE.value,
                () -> epoch == NetworkStuff.connectionEpoch() && address.equals(Configs.SERVER_IP.value)
                        && kind == Configs.CLOUD_PROTOCOL.kind());
    }

    protected static URI getUri(String path) {
        try {
            return endpoint().http("/api/" + path);
        } catch (java.io.IOException rejected) {
            throw new IllegalArgumentException(rejected.getMessage());
        }
    }

    protected static String getBackendAddress() {
        return endpoint().origin() + "/api";
    }

    protected HttpRequest.Builder header(String url) {
        try {
            endpoint.requireAllowed();
        } catch (java.io.IOException rejected) {
            throw new IllegalArgumentException(rejected.getMessage());
        }
        return HttpRequest
                .newBuilder(endpoint.origin().resolve("/api/" + url))
                .header("user-agent", FiguraMod.MOD_NAME + "/" + FiguraMod.VERSION)
                .header("token", token);
    }


    // -- runners -- // 


    protected static void runString(HttpRequest request, BiConsumer<Integer, String> consumer) {
        try {
            requestDebug(request);
            try (CloudHttp transport = new CloudHttp(NetworkStuff.client, endpoint())) {
                var response = transport.send(request);
                try (var body = response.body()) {
                    int code = response.statusCode();
                    if (code == 401) NetworkStuff.reAuth();
                    consumer.accept(code, new String(CloudHttp.readBounded(body, 65536), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Cloud HTTP request failed: {}", e.getClass().getSimpleName());
        }
    }

    protected static void runStream(HttpRequest request, BiConsumer<Integer, InputStream> consumer) {
        try {
            requestDebug(request);
            try (CloudHttp transport = new CloudHttp(NetworkStuff.client, endpoint())) {
                var response = transport.send(request);
                try (var body = response.body()) {
                    int code = response.statusCode();
                    if (code == 401) NetworkStuff.reAuth();
                    consumer.accept(code, body);
                }
            }
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Cloud HTTP request failed: {}", e.getClass().getSimpleName());
        }
    }


    // -- feedback -- // 


    private static void requestDebug(HttpRequest msg) {
        if (NetworkStuff.debug)
            FiguraMod.debug("Cloud HTTP request: method={}, origin={}", msg.method(), endpoint().diagnostic());
    }


    // -- accessors -- // 


    // will return 200 OK if token is valid
    public HttpRequest checkAuth() {
        return header("").build();
    }

    public HttpRequest getUser(UUID id) {
        return header(id.toString()).build();
    }

    public HttpRequest getLimits() {
        return header("limits").build();
    }

    public HttpRequest getVersion() {
        return header("version").build();
    }

    public HttpRequest getMotd() {
        return header("motd").build();
    }

    public HttpRequest getAvatar(UUID owner, String id) {
        return header(owner.toString() + '/' + id).build();
    }

    public HttpRequest uploadAvatar(String id, byte[] bytes) {
        return header(id).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).header("Content-Type", "application/octet-stream").build();
    }

    public HttpRequest deleteAvatar(String id) {
        return header(id).DELETE().build();
    }

    public HttpRequest setEquipped(String json) {
        return header("equip").POST(HttpRequest.BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    }
}
