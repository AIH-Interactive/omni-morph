package top.aihmc.omnimorph.backend2.library;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** A committed receipt and a current-state conflict have different activation semantics. */
public sealed interface OutfitApplyResult {
    int JSON_BYTES = OutfitConfig.MAX_JSON_BYTES + 4096;

    long currentRevision();

    record Accepted(UUID operationId, OutfitConfig configuration, long equipmentRevision,
                    long currentRevision) implements OutfitApplyResult {
        public Accepted {
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(configuration);
            require(configuration.revision() > 0 && equipmentRevision >= 0
                    && currentRevision >= configuration.revision());
        }

        /** Receipt guard only; the caller must also retain its valid prepared generation. */
        public boolean canActivate(UUID operation, long expectedRevision) {
            return operationId.equals(operation) && expectedRevision >= 0 && expectedRevision < Long.MAX_VALUE
                    && configuration.revision() == expectedRevision + 1
                    && currentRevision == configuration.revision();
        }
    }

    record Conflict(OutfitConfig configuration, UUID requestId, long currentRevision) implements OutfitApplyResult {
        public Conflict {
            Objects.requireNonNull(configuration);
            Objects.requireNonNull(requestId);
            require(currentRevision == configuration.revision());
        }
    }

    static JsonObject request(UUID operation, OutfitConfig configuration, OutfitLimits limits) {
        Objects.requireNonNull(operation);
        require(configuration.revision() < Long.MAX_VALUE);
        var body = new JsonObject();
        body.addProperty("operation_id", operation.toString());
        body.addProperty("expected_revision", configuration.revision());
        JsonObject wire = wireConfiguration(configuration, limits);
        JsonObject committed = wire.deepCopy();
        committed.addProperty("revision", configuration.revision() + 1);
        require(committed.toString().getBytes(StandardCharsets.UTF_8).length <= limits.maxJsonBytes());
        body.add("configuration", wire);
        return body;
    }

    private static JsonObject wireConfiguration(OutfitConfig configuration, OutfitLimits limits) {
        JsonObject wire = JsonParser.parseString(OutfitCodec.encode(configuration, limits)).getAsJsonObject();
        // Include only nullable fields belonging to the selected Rust tagged variant.
        // Global Gson serializeNulls would also add invalid inactive union fields.
        nullable(wire, "base");
        for (var value : wire.getAsJsonArray("instances")) {
            JsonObject instance = value.getAsJsonObject();
            JsonObject anchor = instance.getAsJsonObject("anchor");
            if (text(anchor, "type").equals("AUTO_TRAIL")) {
                nullable(anchor, "preferred_instance_id");
            }
            if (anchor.has("bone")) {
                nullable(anchor.getAsJsonObject("bone"), "instance_id");
            }
            for (var override : instance.getAsJsonArray("bone_overrides")) {
                nullable(override.getAsJsonObject().getAsJsonObject("bone"), "instance_id");
            }
        }
        for (var anchor : wire.getAsJsonArray("virtual_anchors")) {
            nullable(anchor.getAsJsonObject().getAsJsonObject("parent"), "instance_id");
        }
        return wire;
    }

    private static void nullable(JsonObject object, String field) {
        if (!object.has(field)) {
            object.add(field, JsonNull.INSTANCE);
        }
    }

    static OutfitApplyResult decode(int status, String json, UUID operation,
                                    OutfitConfig prepared, OutfitLimits limits) {
        JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        long current = integer(body, "current_revision");
        if (status == 409) {
            require(text(body, "code").equals("CONFLICT"));
            require(body.getAsJsonPrimitive("retryable").isBoolean() && !body.get("retryable").getAsBoolean());
            // A lowered budget may make the stored configuration unsuitable for activation.
            // Preserve it for inspection/rebinding under the hard contract, not the new budget.
            var configuration = OutfitCodec.decode(body.getAsJsonObject("configuration").toString());
            require(integer(body.getAsJsonObject("configuration"), "revision") == configuration.revision());
            return new Conflict(configuration, uuid(body, "request_id"), current);
        }
        require(status == 200 && integer(body, "schema_version") == 1);
        UUID acknowledged = uuid(body, "operation_id");
        require(acknowledged.equals(operation) && prepared.revision() < Long.MAX_VALUE);
        JsonObject applied = body.getAsJsonObject("applied");
        JsonObject rawConfiguration = applied.getAsJsonObject("configuration");
        require(integer(rawConfiguration, "revision") == prepared.revision() + 1);
        OutfitConfig configuration = OutfitCodec.decode(rawConfiguration.toString(), limits);
        JsonObject expected = wireConfiguration(prepared, limits);
        expected.addProperty("revision", prepared.revision() + 1);
        require(expected.equals(rawConfiguration));
        // Tree comparison preserves wire types; typed comparison preserves exact long
        // resource revisions which JsonPrimitive numeric equality may round to doubles.
        require(OutfitCodec.decode(expected.toString(), limits).equals(configuration));
        return new Accepted(acknowledged, configuration, integer(applied, "equipment_revision"), current);
    }

    private static long integer(JsonObject object, String field) {
        var value = object.getAsJsonPrimitive(field);
        require(value != null && value.isNumber());
        long result = value.getAsBigDecimal().longValueExact();
        require(result >= 0);
        return result;
    }

    private static String text(JsonObject object, String field) {
        var value = object.getAsJsonPrimitive(field);
        require(value != null && value.isString());
        return value.getAsString();
    }

    private static UUID uuid(JsonObject object, String field) {
        String value = text(object, field);
        UUID uuid = UUID.fromString(value);
        require(uuid.toString().equals(value));
        return uuid;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("INVALID_OUTFIT_APPLY_RESPONSE");
        }
    }
}
