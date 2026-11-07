package top.aihmc.omm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Reproducible host placements; contains no model or animation implementation. */
record SceneLayout(long seed, List<Placement> instances) {
    record Placement(int index, float x, float y, float z, float animationOffsetSeconds) {
    }

    static SceneLayout read() throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        JsonObject root = JsonParser.parseString(Files.readString(output.resolve("effective-config.json"))).getAsJsonObject();
        JsonObject scene = root.has("scene") ? root.getAsJsonObject("scene") : new JsonObject();
        int modelCount = scene.has("models") ? scene.getAsJsonArray("models").size() : 1;
        int count = scene.has("count") ? scene.get("count").getAsInt() : modelCount;
        long seed = scene.has("seed") ? scene.get("seed").getAsLong() : 0;
        float radius = scene.has("radius") ? scene.get("radius").getAsFloat() : 3;
        float offset = scene.has("animationOffsetSeconds") ? scene.get("animationOffsetSeconds").getAsFloat() : 0;
        if (count < 1 || !Float.isFinite(radius) || radius < 0 || !Float.isFinite(offset) || offset < 0) {
            throw new IllegalArgumentException("scene requires positive count and finite nonnegative radius/animationOffsetSeconds");
        }
        if (count < modelCount) {
            throw new IllegalArgumentException("scene.count must cover every entry in scene.models");
        }
        var random = new SplittableRandom(seed);
        var placements = new ArrayList<Placement>();
        for (int index = 0; index < count; index++) {
            float x = count == 1 ? 0 : (float) ((random.nextDouble() * 2 - 1) * radius);
            float z = count == 1 ? 0 : (float) ((random.nextDouble() * 2 - 1) * radius);
            placements.add(new Placement(index, x, 0, z, index * offset));
        }
        SceneLayout layout = new SceneLayout(seed, List.copyOf(placements));
        Files.writeString(output.resolve("scene-layout.json"), new GsonBuilder().setPrettyPrinting().create().toJson(layout));
        System.out.println("SCENE LAYOUT seed=" + seed + " instances=" + count);
        return layout;
    }
}
