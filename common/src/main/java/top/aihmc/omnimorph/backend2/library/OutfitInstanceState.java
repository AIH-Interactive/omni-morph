package top.aihmc.omnimorph.backend2.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.util.Map;
import java.util.UUID;

/** One exact instance's durable baseline. Animation bytes use the existing bounded YSM codec. */
public record OutfitInstanceState(long outfitRevision, UUID instanceId, OutfitConfig.ResourceRef resource,
                                  long revision, State state) {
    private static final com.google.gson.Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).serializeNulls().create();

    public record State(UUID generation, long sequence, Map<String, Object> controls,
                        Map<String, Object> luaState, String animation, String templateVersion, String templateSignature) {
        public State {
            if (generation == null || sequence < 0 || templateVersion == null || templateSignature == null
                    || templateVersion.length() > 64
                    || !(templateVersion.isEmpty() && templateSignature.isEmpty()
                    || !templateVersion.isEmpty() && templateSignature.matches("[0-9a-f]{64}"))) {
                throw new IllegalArgumentException("INVALID_INSTANCE_STATE");
            }
            controls = Map.copyOf(controls);
            luaState = Map.copyOf(luaState);
        }
    }

    public OutfitInstanceState {
        if (outfitRevision <= 0 || instanceId == null || resource == null || revision < 0 || state == null) {
            throw new IllegalArgumentException("INVALID_INSTANCE_STATE");
        }
    }

    public static OutfitInstanceState decode(JsonObject input) {
        for (String key : java.util.List.of("outfit_revision", "revision")) {
            var value = input.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("INVALID_INSTANCE_STATE");
            }
            value.getAsBigDecimal().longValueExact();
        }
        var encoded = input.getAsJsonObject("state");
        if (!input.keySet().equals(java.util.Set.of("outfit_revision", "instance_id", "resource", "revision", "state"))
                || !encoded.keySet().equals(java.util.Set.of("generation", "sequence", "controls", "lua_state", "animation",
                        "template_version", "template_signature"))) {
            throw new IllegalArgumentException("INVALID_INSTANCE_STATE");
        }
        var sequence = encoded.get("sequence");
        if (!sequence.isJsonPrimitive() || !sequence.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("INVALID_INSTANCE_STATE");
        }
        sequence.getAsBigDecimal().longValueExact();
        return JSON.fromJson(input, OutfitInstanceState.class);
    }

    public JsonObject encode() {
        JsonObject result = JSON.toJsonTree(this).getAsJsonObject();
        // Rust's tagged reference only accepts the selected variant's fields.
        result.getAsJsonObject("resource").entrySet().removeIf(entry -> entry.getValue().isJsonNull());
        return result;
    }
}
