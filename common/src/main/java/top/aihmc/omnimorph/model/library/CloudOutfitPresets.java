package top.aihmc.omnimorph.model.library;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Owner-only preset documents through the existing authenticated library transport. No community publication. */
public final class CloudOutfitPresets {
    public record Item(UUID id, long revision, String name) {}
    public record Preset(UUID id, long revision, String name, OutfitConfig configuration) {}

    private static final String ROOT = "/api/library/v1/outfit-presets";
    private final LibraryClient client;
    private final OutfitLimits limits;

    public CloudOutfitPresets(LibraryClient client, OutfitLimits limits) {
        this.client = client;
        this.limits = limits;
    }

    public LibraryClient.Request<List<Item>> list() {
        return client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            try {
                var response = exchange.json("GET", ROOT, null, JsonObject.class);
                var rows = response.getAsJsonArray("items");
                if (rows == null || rows.size() > 64) {
                    throw invalid();
                }
                var result = new ArrayList<Item>();
                var ids = new HashSet<UUID>();
                for (var row : rows) {
                    var value = row.getAsJsonObject();
                    UUID id = UUID.fromString(value.get("preset_id").getAsString());
                    if (!ids.add(id)) {
                        throw invalid();
                    }
                    result.add(new Item(id, revision(value), name(value.get("name").getAsString())));
                }
                return List.copyOf(result);
            } catch (LibraryClient.Failure failure) {
                throw failure;
            } catch (RuntimeException malformed) {
                throw invalid();
            }
        });
    }

    public LibraryClient.Request<Preset> read(UUID id) {
        return client.submit(LibraryClient.Lane.CONTROL, exchange ->
                preset(exchange.json("GET", ROOT + "/" + id, null, JsonObject.class), id));
    }

    public Save save(UUID id, long expectedRevision, String name, OutfitConfig configuration) throws LibraryClient.Failure {
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE || limits.maxPresets() == 0) {
            throw new IllegalArgumentException("OUTFIT_PRESET_SAVE_UNAVAILABLE");
        }
        return new Save(id, expectedRevision, name(name).strip(), configuration);
    }

    private Preset preset(JsonObject response, UUID expected) throws LibraryClient.Failure {
        try {
            UUID id = UUID.fromString(response.get("preset_id").getAsString());
            if (!expected.equals(id)) {
                throw invalid();
            }
            return new Preset(id, revision(response), name(response.get("name").getAsString()),
                    OutfitCodec.decode(response.get("configuration").toString(), limits));
        } catch (LibraryClient.Failure failure) {
            throw failure;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static long revision(JsonObject value) throws LibraryClient.Failure {
        var number = value.get("revision");
        if (number == null || !number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) {
            throw invalid();
        }
        long revision = number.getAsBigDecimal().longValueExact();
        if (revision < 1) {
            throw invalid();
        }
        return revision;
    }

    private static String name(String value) throws LibraryClient.Failure {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 96 || value.indexOf('\0') >= 0) {
            throw invalid();
        }
        return value;
    }

    private static LibraryClient.Failure invalid() {
        return new LibraryClient.Failure(0, "INVALID_OUTFIT_PRESET_RESPONSE");
    }

    /** The operation UUID, complete body and expected revision remain unchanged after an uncertain response. */
    public final class Save implements AutoCloseable {
        private final UUID operationId = UUID.randomUUID();
        private final UUID id;
        private final long expectedRevision;
        private final String name;
        private final JsonElement configuration;
        private final JsonObject input;
        private LibraryClient.Request<Preset> request;
        private boolean closed;

        private Save(UUID id, long expectedRevision, String name, OutfitConfig configuration) {
            this.id = java.util.Objects.requireNonNull(id);
            this.expectedRevision = expectedRevision;
            this.name = name;
            this.configuration = JsonParser.parseString(OutfitCodec.encode(configuration, limits));
            input = new JsonObject();
            input.addProperty("operation_id", operationId.toString());
            input.addProperty("preset_id", id.toString());
            input.addProperty("expected_revision", expectedRevision);
            input.addProperty("name", name);
            input.add("configuration", this.configuration);
            send();
        }

        public UUID operationId() {
            return operationId;
        }

        public CompletableFuture<Preset> future() {
            return request.future();
        }

        public CompletableFuture<Preset> retry() {
            if (closed || !future().isCompletedExceptionally()) {
                throw new IllegalStateException("OUTFIT_PRESET_RETRY_UNAVAILABLE");
            }
            send();
            return future();
        }

        private void send() {
            request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
                var response = exchange.json("POST", ROOT, input, JsonObject.class);
                Preset saved = preset(response, id);
                if (saved.revision() != expectedRevision + 1 || !saved.name().equals(name)
                        || !JsonParser.parseString(OutfitCodec.encode(saved.configuration(), limits)).equals(configuration)) {
                    throw new LibraryClient.Failure(0, "OUTFIT_PRESET_RESPONSE_MISMATCH");
                }
                return saved;
            });
        }

        @Override
        public void close() {
            closed = true;
            request.cancel();
        }
    }
}
