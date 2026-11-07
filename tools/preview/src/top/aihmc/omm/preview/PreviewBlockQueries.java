package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;

/** Original relative block tag query over an explicit immutable simulation-world snapshot. */
final class PreviewBlockQueries {
    private record Position(int x, int y, int z) {}
    private final Map<Position, Set<String>> blocks = new HashMap<>();
    private final Set<String> defaultTags;
    private final double[] entityPosition;

    PreviewBlockQueries(JsonObject states) {
        entityPosition = states.has("player") && states.getAsJsonObject("player").has("pos")
            ? coordinates(states.getAsJsonObject("player").get("pos"), "states.player.pos")
            : new double[3];
        var world = states.has("world") ? states.getAsJsonObject("world") : new JsonObject();
        defaultTags =
            world.has("defaultBlockTags") ? tags(world.get("defaultBlockTags"), "states.world.defaultBlockTags") : null;
        if (world.has("blocks")) {
            if (!world.get("blocks").isJsonArray())
                throw new IllegalArgumentException("states.world.blocks must be an array");
            var entries = world.getAsJsonArray("blocks");
            for (int index = 0; index < entries.size(); index++) {
                String path = "states.world.blocks[" + index + "]";
                if (!entries.get(index).isJsonObject())
                    throw new IllegalArgumentException(path + " must be an object");
                var block = entries.get(index).getAsJsonObject();
                double[] position = coordinates(block.get("pos"), path + ".pos");
                for (double coordinate : position) {
                    if (coordinate != Math.rint(coordinate) || coordinate < Integer.MIN_VALUE
                        || coordinate > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException(
                            path + ".pos must contain signed 32-bit integer coordinates");
                    }
                }
                blocks.put(new Position((int) position[0], (int) position[1], (int) position[2]),
                    tags(block.get("tags"), path + ".tags"));
            }
        }
    }

    static Function relativeBlockHasAnyTag() {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                double x = args.getAsDouble(execution, 0);
                double y = args.getAsDouble(execution, 1);
                double z = args.getAsDouble(execution, 2);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 5
                    || Math.abs(y) > 5 || Math.abs(z) > 5)
                    return null;
                var world = ((PreviewMolang) execution.entity()).blockQueries;
                // QueryVariables.relativeQueryPosition rounds world position + offset - 0.5, without yaw rotation.
                var position = new Position((int) Math.round(world.entityPosition[0] + x - 0.5),
                    (int) Math.round(world.entityPosition[1] + y - 0.5),
                    (int) Math.round(world.entityPosition[2] + z - 0.5));
                Set<String> tags = world.blocks.getOrDefault(position, world.defaultTags);
                if (tags == null) {
                    throw new UnsupportedOperationException(
                        "states.world.blocks or states.world.defaultBlockTags must define sampled block " + position);
                }
                for (int index = 3; index < args.size(); index++) {
                    String id = identifier(args.getAsString(execution, index));
                    if (id == null)
                        return null;
                    if (tags.contains(id))
                        return true;
                }
                return false;
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size >= 4;
            }
        };
    }

    private static double[] coordinates(JsonElement value, String path) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException(path + " must contain three numeric coordinates");
        }
        var result = new double[3];
        for (int index = 0; index < 3; index++) {
            JsonElement coordinate = value.getAsJsonArray().get(index);
            if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(coordinate.getAsDouble())) {
                throw new IllegalArgumentException(path + " must contain three finite numeric coordinates");
            }
            result[index] = coordinate.getAsDouble();
        }
        return result;
    }

    private static Set<String> tags(JsonElement value, String path) {
        if (value == null || !value.isJsonArray())
            throw new IllegalArgumentException(path + " must be an array of tag IDs");
        Set<String> result = new HashSet<>();
        for (JsonElement tag : value.getAsJsonArray()) {
            String id =
                tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString() ? identifier(tag.getAsString()) : null;
            if (id == null)
                throw new IllegalArgumentException(path + " contains an invalid tag ID");
            result.add(id);
        }
        return Set.copyOf(result);
    }

    private static String identifier(String value) {
        if (value == null || value.isBlank())
            return null;
        String id = value.contains(":") ? value : "minecraft:" + value;
        return id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") ? id : null;
    }
}
