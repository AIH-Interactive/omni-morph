package org.figuramc.figura.config;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import org.figuramc.figura.backend2.protocol.ProtobufCloudProtocol;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual EnumConfig/ConfigManager round trips, without a replacement configuration UI. */
public final class CloudProtocolConfigSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("omnimorph-config-b6-");
        System.setProperty("figura.test.directory", directory.toString());
        try {
            var category = new ConfigType.Category("cloud-test");
            AtomicInteger changes = new AtomicInteger();
            var protocol = new CloudProtocolConfig("cloud_protocol", category, changes::incrementAndGet);
            var address = new ConfigType.StringConfig("server_ip", category, "original.test:443");
            Path file = directory.resolve("figura.json");
            require(protocol.value == 1 && protocol.tempValue == 1 && protocol.defaultValue == 1,
                    "Fresh install/reset/missing use Protobuf");
            require(protocol.enumList.size() == 2 && protocol.enumTooltip.size() == 2,
                    "Original enum widget has exactly two choices");
            ConfigManager.saveConfig();
            require(!read(file).has("cloud_protocol"), "Default omission reloads Protobuf");
            protocol.setValue("0");
            require(changes.get() == 1 && protocol.value == 0, "Explicit change invokes reconnect callback once");
            ConfigManager.saveConfig();
            require(read(file).get("cloud_protocol").getAsString().equals("FIGURA_CLOUD"), "Disk uses stable enum name");
            protocol.prepareLoad();
            ConfigManager.loadConfig();
            require(protocol.value == 0 && changes.get() == 1, "Explicit old choice survives load without startup reconnect");

            Files.writeString(file, "{\"CONFIG_VERSION\":1,\"server_ip\":\"kept.test:6543\"}");
            ConfigManager.loadConfig();
            require(protocol.value == 1 && address.value.equals("kept.test:6543"),
                    "Old config missing new field migrates to Protobuf and preserves address");
            for (JsonElement value : new JsonElement[] {new JsonPrimitive("FIGURA_CLOUD"), new JsonPrimitive(0),
                    new JsonPrimitive("PROTOBUF"), new JsonPrimitive(1)}) {
                JsonObject json = new JsonObject();
                json.addProperty("CONFIG_VERSION", 1);
                json.addProperty("server_ip", "kept.test:6543");
                json.add("cloud_protocol", value);
                Files.writeString(file, json.toString());
                ConfigManager.loadConfig();
                int expected = value.getAsString().equals("0") || value.getAsString().equals("FIGURA_CLOUD") ? 0 : 1;
                require(protocol.value == expected && protocol.tempValue == expected, "Stable/numeric migration: " + value);
                ConfigManager.saveConfig();
                if (expected == 0) {
                    require(read(file).get("cloud_protocol").getAsString().equals("FIGURA_CLOUD"), "Numeric migration writes stable name");
                }
            }
            for (JsonElement unknown : new JsonElement[] {new JsonPrimitive("future-mode"), new JsonPrimitive(-1),
                    new JsonPrimitive(2), JsonNull.INSTANCE, new JsonObject()}) {
                var json = new JsonObject();
                json.addProperty("CONFIG_VERSION", 1);
                json.addProperty("server_ip", "kept.test:6543");
                json.add("cloud_protocol", unknown);
                Files.writeString(file, json.toString());
                ConfigManager.loadConfig();
                require(protocol.value == 1 && protocol.tempValue == 1 && address.value.equals("kept.test:6543"),
                        "Unknown protocol falls back locally without resetting other settings");
            }
            protocol.setTempValue("0");
            require(protocol.value == 1 && protocol.tempValue == 0, "Editing does not reconnect before apply");
            protocol.discardConfig();
            require(protocol.tempValue == 1, "Undo preserves active selection");
            protocol.setValue("0");
            int beforeReset = changes.get();
            protocol.resetTemp();
            protocol.setValue(String.valueOf(protocol.tempValue));
            require(protocol.value == 1 && changes.get() == beforeReset + 1, "Original reset/apply switches to Protobuf once");
            protocol.setValue("0");
            protocol.setDefault();
            require(protocol.value == 1 && protocol.tempValue == 1, "Programmatic default keeps value and UI consistent");
            for (String language : new String[] {"en_us", "zh_cn"}) {
                try (var stream = CloudProtocolConfigSmokeTest.class.getClassLoader()
                        .getResourceAsStream("assets/figura/lang/" + language + ".json")) {
                    require(stream != null, "Bundled language " + language);
                    var text = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    require(text.get("figura.config.cloud_protocol.1").getAsString().equals("Figura Cloud")
                                    && text.get("figura.config.cloud_protocol.2").getAsString().equals("Protobuf")
                                    && text.has("figura.backend.protobuf.unsupported"),
                            "Existing settings labels and manual fallback hint: " + language);
                }
            }
            unsupportedBackend();
            System.out.println("PASS B6 configuration: fresh/missing/stable/numeric/unknown/save/reset/undo, address preservation, existing EnumConfig and unsupported-backend no fallback");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
            System.clearProperty("figura.test.directory");
        }
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void unsupportedBackend() throws Exception {
        AtomicInteger capabilities = new AtomicInteger();
        AtomicInteger otherRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/api/protobuf/v1/capabilities")) {
                capabilities.incrementAndGet();
                require(exchange.getRequestHeaders().getFirst("Authorization") == null, "Discovery carries no credential");
            } else {
                otherRequests.incrementAndGet();
            }
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            try (var protocol = new ProtobufCloudProtocol(origin, "synthetic-unused", "test", 1, HttpClient.newHttpClient())) {
                try {
                    protocol.connect(null);
                    throw new AssertionError("Unsupported backend accepted");
                } catch (ProtobufCloudProtocol.Unsupported expected) {
                    require(expected.getMessage().equals("backend.protobuf.unsupported"), "Localized manual selection hint");
                }
            }
            require(capabilities.get() == 1 && otherRequests.get() == 0,
                    "Unsupported endpoint never authenticates or falls back to legacy routes");
        } finally {
            server.stop(0);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
