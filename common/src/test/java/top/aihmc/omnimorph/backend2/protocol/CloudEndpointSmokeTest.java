package top.aihmc.omnimorph.backend2.protocol;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual HTTP/TLS listeners and the production endpoint, HTTP and ordered WS transports. */
public final class CloudEndpointSmokeTest {
    public static void main(String[] args) throws Exception {
        origins();
        transports();
        queuedWrites();
        tls();
        System.out.println("B7 endpoint matrix, HTTP/WS redirects, live revocation, queued writes and TLS identity PASS");
    }

    private static void origins() throws Exception {
        String[][] accepted = {
                {"example.test:443", "https://example.test"},
                {"HTTPS://Example.test/", "https://example.test"},
                {"wss://example.test", "https://example.test"},
                {"example.test:8443", "https://example.test:8443"},
                {"192.168.1.10:6665", "https://192.168.1.10:6665"},
                {"[::1]:6665", "https://[::1]:6665"},
                {"http://127.0.0.1:6665", "http://127.0.0.1:6665"},
                {"ws://[::1]:6665", "http://[::1]:6665"},
                {"http://192.168.1.10:6665/", "http://192.168.1.10:6665"},
                {"http://example.test:80", "http://example.test"}
        };
        for (String[] row : accepted) {
            for (boolean debug : new boolean[] {false, true}) {
                var endpoint = CloudEndpoint.parse(row[0], () -> debug);
                require(endpoint.origin().toString().equals(row[1]), "Normalized origin");
                boolean permitted = debug || !endpoint.plaintext();
                require(CloudEndpoint.valid(row[0], debug) == permitted, "Field and transport agree");
                if (permitted) {
                    require(endpoint.http("/api//auth/key").toString().equals(row[1] + "/api//auth/key"),
                            "Historical auth double slash preserved");
                    require(endpoint.websocket("/ws").toString().equals(row[1].replaceFirst("^http", "ws") + "/ws"),
                            "Paired WS transport retains authority and TLS");
                } else {
                    rejects(() -> endpoint.websocket("/ws"), "Plaintext requires Debug");
                }
            }
        }
        for (String address : new String[] {"", "https://", "ftp://example.test", "file:///tmp", "http://user:secret@example.test",
                "https://example.test?token=secret", "https://example.test#secret", "https://example.test/api", "ws://example.test/ws",
                "http://example.test:0", "http://example.test:65536", "http://example.test:-1", "http://example.test:",
                "http://example.test:bad", "http://::1:6665", "http://example.test\\@evil.test", "https://example.test//"}) {
            rejects(() -> CloudEndpoint.parse(address, () -> true), "Ambiguous or credential-bearing base rejected");
        }
        AtomicBoolean current = new AtomicBoolean(true);
        var endpoint = CloudEndpoint.parse("https://example.test", () -> true, current::get);
        rejects(() -> endpoint.http("//evil.test/api"), "Path cannot replace selected origin");
        current.set(false);
        rejects(endpoint::requireAllowed, "Retired authentication cannot deliver its token");
    }

    private static void transports() throws Exception {
        AtomicInteger redirected = new AtomicInteger();
        HttpServer target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        target.createContext("/", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        target.start();
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            if (exchange.getRequestURI().getPath().equals("/slow")) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + target.getAddress().getPort() + "/stolen");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            for (CloudProtocol.Kind kind : CloudProtocol.Kind.values()) {
                var disabled = CloudEndpoint.parse(origin, () -> false);
                try (CloudProtocol protocol = CloudProtocol.create(kind, disabled, "synthetic-secret", "test", 1, client)) {
                    rejects(() -> protocol.connect(null), "Both adapters reject before discovery/auth/WS");
                }
            }
            require(requests.get() == 0, "No plaintext requests before Debug permission");
            AtomicBoolean debug = new AtomicBoolean(true);
            var endpoint = CloudEndpoint.parse(origin, debug::get);
            try (CloudHttp http = new CloudHttp(client, endpoint)) {
                rejects(() -> http.control(CloudHttp.request(endpoint.http("/redirect"))
                        .header("Authorization", "Bearer synthetic-secret").header("Cookie", "private-cookie").build()),
                        "HTTP redirect rejected without forwarding credentials");
                rejects(() -> client.newWebSocketBuilder().header("Authorization", "Bearer synthetic-secret")
                        .buildAsync(endpoint.websocket("/ws"), new WebSocket.Listener() {}).get(5, TimeUnit.SECONDS),
                        "WS redirect rejected without forwarding credentials");
                require(requests.get() == 2 && redirected.get() == 0, "Both redirects stopped at selected origin");
                var slow = CloudHttp.request(endpoint.http("/slow")).build();
                CompletableFuture<Boolean> pending = CompletableFuture.supplyAsync(() -> {
                    try {
                        http.control(slow);
                        return false;
                    } catch (Exception cancelled) {
                        return true;
                    }
                });
                require(entered.await(5, TimeUnit.SECONDS), "Request actually entered listener");
                debug.set(false);
                http.close();
                require(pending.get(5, TimeUnit.SECONDS), "Revocation cancels in-flight HTTP");
                int before = requests.get();
                rejects(() -> http.control(slow), "Retry denied after revocation");
                require(requests.get() == before, "No revoked retry sent");
            }
            try (HttpClient redirects = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
                rejects(() -> new CloudHttp(redirects, endpoint), "Redirect-enabled HTTP client rejected at construction");
            }
        } finally {
            release.countDown();
            server.stop(0);
            target.stop(0);
        }
        // Bind IPv6 explicitly: parsing alone does not establish that the JDK preserves its authority on the wire.
        HttpServer ipv6 = HttpServer.create(new InetSocketAddress(InetAddress.getByName("::1"), 0), 0);
        ipv6.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 1);
            exchange.getResponseBody().write(7);
            exchange.close();
        });
        ipv6.start();
        try (HttpClient client = HttpClient.newHttpClient();
             CloudHttp http = new CloudHttp(client, CloudEndpoint.parse("ws://[::1]:" + ipv6.getAddress().getPort(), () -> true))) {
            byte[] response = http.control(CloudHttp.request(URI.create("http://[::1]:" + ipv6.getAddress().getPort() + "/")).build());
            require(response.length == 1 && response[0] == 7, "Actual bracketed IPv6 HTTP");
        } finally {
            ipv6.stop(0);
        }
    }

    private static void queuedWrites() throws Exception {
        AtomicBoolean debug = new AtomicBoolean(true);
        AtomicInteger received = new AtomicInteger();
        var endpoint = CloudEndpoint.parse("ws://127.0.0.1:6665", debug::get);
        RecordingSocket wire = new RecordingSocket();
        try (CloudSocket socket = new CloudSocket(65536, bytes -> received.incrementAndGet(), ignored -> {}, endpoint)) {
            socket.attach(wire);
            socket.send(new byte[] {1});
            socket.send(new byte[] {2});
            require(wire.sent.get() == 1, "Second WS write really queued behind first");
            debug.set(false);
            wire.first.complete(wire);
            require(wire.sent.get() == 1, "Queued write checks current permission before sending");
            rejects(() -> socket.send(new byte[] {3}), "New WS request denied");
            socket.onBinary(wire, ByteBuffer.wrap(new byte[] {4}), true);
            require(received.get() == 0, "Late inbound WS frame rejected after revocation");
        }
        RecordingSocket late = new RecordingSocket();
        try (CloudSocket socket = new CloudSocket(65536, ignored -> {}, ignored -> {}, endpoint)) {
            rejects(() -> socket.attach(late), "Late handshake cannot attach revoked scope");
            require(late.aborted, "Revoked handshake is aborted");
        }
    }

    private static void tls() throws Exception {
        Path directory = Files.createTempDirectory("omnimorph-b7-tls-");
        char[] password = "synthetic-test-password".toCharArray();
        HttpsServer server = null;
        HttpServer downgrade = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger downgraded = new AtomicInteger();
        downgrade.createContext("/", exchange -> {
            downgraded.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        downgrade.start();
        try {
            Path store = directory.resolve("localhost.p12");
            Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool.exe").toString(),
                    "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                    "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore",
                    store.toString(), "-storepass", new String(password), "-noprompt").inheritIO().start();
            require(keytool.waitFor(30, TimeUnit.SECONDS) && keytool.exitValue() == 0, "Temporary TLS certificate generation");
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(store)) {
                keys.load(input, password);
            }
            var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(keys, password);
            SSLContext serverContext = SSLContext.getInstance("TLS");
            serverContext.init(managers.getKeyManagers(), null, null);
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            trust.setCertificateEntry("localhost", keys.getCertificate("localhost"));
            var trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trusts.init(trust);
            SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(null, trusts.getTrustManagers(), null);
            server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
            server.createContext("/", exchange -> {
                if (exchange.getRequestURI().getPath().equals("/ok")) {
                    exchange.sendResponseHeaders(200, 1);
                    exchange.getResponseBody().write(9);
                } else {
                    exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + downgrade.getAddress().getPort() + "/stolen");
                    exchange.sendResponseHeaders(302, -1);
                }
                exchange.close();
            });
            server.start();
            int port = server.getAddress().getPort();
            try (HttpClient client = HttpClient.newBuilder().sslContext(clientContext).connectTimeout(Duration.ofSeconds(3)).build()) {
                for (boolean debug : new boolean[] {false, true}) {
                    var endpoint = CloudEndpoint.parse("wss://localhost:" + port, () -> debug);
                    try (CloudHttp http = new CloudHttp(client, endpoint)) {
                        require(http.control(CloudHttp.request(endpoint.http("/ok")).build())[0] == 9,
                                "Valid certificate uses HTTPS even with Debug enabled");
                        rejects(() -> http.control(CloudHttp.request(endpoint.http("/redirect"))
                                .header("token", "synthetic-secret").build()), "HTTPS cannot redirect credentials to plaintext");
                        rejects(() -> client.newWebSocketBuilder().header("Authorization", "Bearer synthetic-secret")
                                .buildAsync(endpoint.websocket("/ws"), new WebSocket.Listener() {}).get(5, TimeUnit.SECONDS),
                                "WSS cannot redirect credentials to plaintext");
                    }
                }
                var mismatch = CloudEndpoint.parse("https://127.0.0.1:" + port, () -> true);
                try (CloudHttp http = new CloudHttp(client, mismatch)) {
                    rejects(() -> http.control(CloudHttp.request(mismatch.http("/ok")).build()),
                            "Debug preserves TLS hostname validation");
                }
                rejects(() -> client.newWebSocketBuilder().buildAsync(mismatch.websocket("/ws"), new WebSocket.Listener() {})
                        .get(5, TimeUnit.SECONDS), "Debug preserves WSS hostname validation");
            }
            try (HttpClient untrusted = HttpClient.newHttpClient();
                 CloudHttp http = new CloudHttp(untrusted, CloudEndpoint.parse("https://localhost:" + port, () -> true))) {
                rejects(() -> http.control(CloudHttp.request(URI.create("https://localhost:" + port + "/ok")).build()),
                        "Debug does not trust arbitrary certificates");
            }
            require(downgraded.get() == 0, "No redirected plaintext request or credential reached target");
        } finally {
            if (server != null) {
                server.stop(0);
            }
            downgrade.stop(0);
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static final class RecordingSocket implements WebSocket {
        final AtomicInteger sent = new AtomicInteger();
        final CompletableFuture<WebSocket> first = new CompletableFuture<>();
        volatile boolean aborted;

        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return sent.incrementAndGet() == 1 ? first : CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer data) { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer data) { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<WebSocket> sendClose(int code, String reason) { return CompletableFuture.completedFuture(this); }
        @Override public void request(long count) {}
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return aborted; }
        @Override public boolean isInputClosed() { return aborted; }
        @Override public void abort() { aborted = true; }
    }

    private static void rejects(Checked action, String message) throws Exception {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
