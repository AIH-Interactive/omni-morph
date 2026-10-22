package top.aihmc.omnimorph.model.outfit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

import java.nio.charset.StandardCharsets;

/** Uses the independent outfit JSON contract, never the frozen Protobuf slots. */
public final class OutfitCodec {
    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(OutfitConfig.Vector.class,
                    (JsonSerializer<OutfitConfig.Vector>) (value, type, context) -> {
                        var array = new JsonArray();
                        array.add(value.x());
                        array.add(value.y());
                        array.add(value.z());
                        return array;
                    })
            .registerTypeAdapter(OutfitConfig.Vector.class,
                    (JsonDeserializer<OutfitConfig.Vector>) (value, type, context) -> {
                        JsonArray array = value.getAsJsonArray();
                        OutfitConfig.require(array.size() == 3);
                        return new OutfitConfig.Vector(array.get(0).getAsDouble(),
                                array.get(1).getAsDouble(), array.get(2).getAsDouble());
                    })
            .create();

    private OutfitCodec() {
    }

    static OutfitConfig.BoneRef decodeBone(com.google.gson.JsonElement value) {
        return JSON.fromJson(value, OutfitConfig.BoneRef.class);
    }

    public static String encode(OutfitConfig configuration) {
        return encode(configuration, OutfitLimits.defaults());
    }

    public static String encode(OutfitConfig configuration, OutfitLimits limits) {
        configuration.validateLimits(limits);
        String json = JSON.toJson(configuration);
        OutfitConfig.require(json.getBytes(StandardCharsets.UTF_8).length <= limits.maxJsonBytes());
        return json;
    }

    public static OutfitConfig decode(String json) {
        return decode(json, OutfitLimits.defaults());
    }

    public static OutfitConfig decode(String json, OutfitLimits limits) {
        OutfitConfig.require(json.getBytes(StandardCharsets.UTF_8).length <= limits.maxJsonBytes());
        OutfitConfig.require(json.getBytes(StandardCharsets.UTF_8).length <= OutfitConfig.MAX_JSON_BYTES);
        OutfitConfig result = JSON.fromJson(json, OutfitConfig.class);
        OutfitConfig.require(result != null);
        result.validateLimits(limits);
        return result;
    }
}
