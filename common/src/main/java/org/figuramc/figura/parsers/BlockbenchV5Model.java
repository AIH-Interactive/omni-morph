package org.figuramc.figura.parsers;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.parsers.BlockbenchCommonTypes.*;
import org.figuramc.figura.parsers.BlockbenchParser2.Intermediary.AnimationRepresentation;
import org.figuramc.figura.parsers.BlockbenchV5Model.OutlinerItem.Container;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.*;
import java.util.regex.Pattern;

public class BlockbenchV5Model extends ModelFormat {
    Vector2i resolution;

    List<Element> elements;

    List<Group> groups;

    List<OutlinerItem> outliner;

    List<Texture> textures;

    List<Animation> animations;
    public static final IllegalStateException WRONG_FORMAT =
            new IllegalStateException("Tried to execute the v5 parser on a model file of a different version");
    public static final JsonDeserializer<BlockbenchV5Model> MODEL_DESERIALIZER = (json, typeOfT, context) -> {
        JsonObject obj = json.getAsJsonObject();
        BlockbenchV5Model instance = new BlockbenchV5Model();

        JsonObject meta = obj.getAsJsonObject("meta");
        instance.formatVersion = meta.get("format_version").getAsString();
        instance.modelFormat = meta.get("model_format").getAsString();

        if (!instance.formatVersion.startsWith("5.")) throw WRONG_FORMAT;

        JsonObject resolution = obj.getAsJsonObject("resolution");
        instance.resolution = new Vector2i(
                resolution.get("width").getAsInt(),
                resolution.get("height").getAsInt()
        );

        instance.elements = new ArrayList<>();
        if (obj.has("elements"))
            for (JsonElement item : obj.getAsJsonArray("elements")) {
                instance.elements.add(context.deserialize(item, Element.class));
            }

        instance.groups = new ArrayList<>();
        if (obj.has("groups"))
            for (JsonElement item : obj.getAsJsonArray("groups")) {
                instance.groups.add(context.deserialize(item, Group.class));
            }

        instance.outliner = new ArrayList<>();
        if (obj.has("outliner"))
            for (JsonElement item : obj.getAsJsonArray("outliner")) {
                instance.outliner.add(context.deserialize(item, OutlinerItem.class));
            }

        instance.textures = new ArrayList<>();
        if (obj.has("textures"))
            for (JsonElement item : obj.getAsJsonArray("textures")) {
                instance.textures.add(context.deserialize(item, Texture.class));
            }

        instance.animations = new ArrayList<>();
        if (obj.has("animations"))
            for (JsonElement item : obj.getAsJsonArray("animations")) {
                instance.animations.add(context.deserialize(item, Animation.class));
            }

        return instance;
    };

    @Override
    public Map<String, UUIDReferable> getAllReferences() {
        Map<String, UUIDReferable> refs = new HashMap<>();
        for (Element element : elements)
            refs.put(element.uuid, element);
        for (Group group : groups)
            refs.put(group.uuid, group);
        return refs;
    }

    public static final Pattern DEFINITELY_STMT = Pattern.compile("^\\s*([:;]|break|goto|do|while|repeat|if|for|function\\s*[^\\s(]|local)");

    public static String negateLua(String source) {
        if (source.contains("return") || DEFINITELY_STMT.matcher(source).find()) {
            // don't bother
            return source;
        }
        // if it is truly an expression, then this will work
        // otherwise this is definitely invalid syntax, so we put a label on it
        // in case it breaks
        return String.format("-(%s)--v5", source);
    }

    @Override
    public CompoundTag convert(BlockbenchParser2.Intermediary target) {
        target.defaultRes = resolution;
        target.loadTextures(textures);
        target.loadAnimations(animations);
        for (Element element : elements) {
            target.elements.put(element.uuid, element);
        }
        target.referents.putAll(getAllReferences());

        processVertexWeights();

        CompoundTag tag = new CompoundTag();

        tag.putString("name", target.name);
        BlockbenchCommonTypes.parseParent(target.name, tag);

        // TODO: collections

        ListTag chld = new ListTag();
        for (OutlinerItem item : outliner) {
            CompoundTag itemTag = item.toNBT(target);
            if (itemTag != null)
                chld.add(itemTag);
        }
        tag.put("chld", chld);

        return tag;
    }

    private void processVertexWeights() {
        for (Element element : elements) {
            if (!(element instanceof ArmatureBoneElement bone))
                continue;
            if (bone.vertex_weights == null || bone.vertex_weights.isEmpty())
                continue;

            String boneName = bone.name;

            for (Map.Entry<String, Float> entry : bone.vertex_weights.entrySet()) {
                String key = entry.getKey();
                float weight = entry.getValue();
                if (weight <= 0f)
                    continue;

                int lastColon = key.lastIndexOf(':');
                if (lastColon < 0 || lastColon >= key.length() - 1)
                    continue;

                String meshUUIDPrefix = key.substring(0, lastColon);
                String vertexKey = key.substring(lastColon + 1);

                MeshElement mesh = null;
                Map<String, Vector3f> meshVerts = null;
                for (Element candidate : elements) {
                    if (candidate instanceof MeshElement m && candidate.uuid != null && candidate.uuid.startsWith(meshUUIDPrefix)) {
                        mesh = m;
                        meshVerts = m.vertices;
                        break;
                    }
                }
                if (mesh == null || meshVerts == null || !meshVerts.containsKey(vertexKey))
                    continue;

                int vertexIndex = 0;
                for (String vk : meshVerts.keySet()) {
                    if (vk.equals(vertexKey))
                        break;
                    vertexIndex++;
                }

                if (mesh.skinData == null)
                    mesh.skinData = new HashMap<>();

                mesh.skinData.compute(vertexIndex, (vi, list) -> {
                    if (list == null)
                        list = new ArrayList<>();
                    list.add(Map.entry(boneName, weight));
                    return list;
                });
            }
        }

        for (Element element : elements) {
            if (!(element instanceof MeshElement mesh) || mesh.skinData == null)
                continue;

            for (Map.Entry<Integer, List<Map.Entry<String, Float>>> entry : mesh.skinData.entrySet()) {
                List<Map.Entry<String, Float>> weights = entry.getValue();
                float sum = 0f;
                for (Map.Entry<String, Float> w : weights)
                    sum += w.getValue();
                if (sum > 1f) {
                    for (int i = 0; i < weights.size(); i++) {
                        Map.Entry<String, Float> w = weights.get(i);
                        weights.set(i, Map.entry(w.getKey(), w.getValue() / sum));
                    }
                }
            }
        }
    }

    public static class Group implements UUIDReferable {
        String name;
        String uuid;

        @Nullable Boolean visibility;
        @Nullable Boolean export;

        @Nullable Vector3f origin;
        @Nullable Vector3f rotation;

        @Override
        public String getUUID() {
            return uuid;
        }
    }

    public static abstract class OutlinerItem implements NBTRepresentation<CompoundTag> {
        @Override
        public abstract @Nullable CompoundTag toNBT(BlockbenchParser2.Intermediary context);

        public static final JsonDeserializer<OutlinerItem> DESERIALIZER = (json, typeOfT, context) -> {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                return new Element(json.getAsString());
            }
            if (json.isJsonObject()) {
                JsonObject theObject = json.getAsJsonObject();
                Container instance = new Container(theObject.get("uuid").getAsString());
                for (JsonElement child : theObject.getAsJsonArray("children")) {
                    instance.children.add(context.deserialize(child, OutlinerItem.class));
                }
                return instance;
            }
            throw new JsonParseException("Expecting an outliner item (string or object), but found " + json.getClass()
                    .getSimpleName() + " instead");
        };

        String uuid;

        public OutlinerItem(String uuid) {
            this.uuid = uuid;
        }

        public static final class Element extends OutlinerItem {
            public Element(String uuid) {
                super(uuid);
            }

            @Override
            public CompoundTag toNBT(BlockbenchParser2.Intermediary context) {
                return context.elements.get(uuid).toNBT(context);
            }
        }

        public static final class Container extends OutlinerItem {
            List<OutlinerItem> children = new ArrayList<>();

            public Container(String uuid) {
                super(uuid);
            }

            @Override
            public @Nullable CompoundTag toNBT(BlockbenchParser2.Intermediary context) {
                UUIDReferable referent = context.referents.get(uuid);
                if (referent == null) {
                    FiguraMod.LOGGER.warn(
                            "Broken reference (in model '{}'): no element with UUID {}",
                            context.name,
                            uuid
                    );
                    return null;
                }

                String name;
                Boolean export;
                Boolean visibility;
                Vector3f origin;
                Vector3f rotation;

                if (referent instanceof Group group) {
                    name = group.name;
                    export = group.export;
                    visibility = group.visibility;
                    origin = group.origin;
                    rotation = group.rotation;
                } else if (referent instanceof BlockbenchCommonTypes.Element element) {
                    name = element.name;
                    export = element.export;
                    visibility = element.visibility;
                    origin = element.origin;
                    rotation = element.rotation;
                } else {
                    FiguraMod.LOGGER.warn(
                            "Broken reference (in model '{}'): expected a group or container element at UUID {} but found {} instead",
                            context.name,
                            uuid,
                            referent.getClass().getSimpleName()
                    );
                    return null;
                }

                if (Boolean.FALSE.equals(export)) return null;

                CompoundTag tag = new CompoundTag();
                tag.putString("name", name);

                if (Boolean.FALSE.equals(visibility))
                    tag.putBoolean("vsb", false);

                if (origin != null && !origin.equals(0, 0, 0))
                    tag.put("piv", BlockbenchCommonTypes.vecToList(origin));
                if (rotation != null && !rotation.equals(0, 0, 0))
                    tag.put("rot", BlockbenchCommonTypes.vecToList(rotation));

                BlockbenchCommonTypes.parseParent(name, tag);

                // TODO: collections?!

                ListTag chld = new ListTag();
                for (OutlinerItem child : children) {
                    CompoundTag childTag = child.toNBT(context);
                    if (childTag != null)
                        chld.add(childTag);
                }
                tag.put("chld", chld);

                Set<AnimationRepresentation> animations = context.animationsByElement.get(uuid);
                if (animations != null) {
                    ListTag anim = new ListTag();
                    for (AnimationRepresentation animation : animations) {
                        Animator animator = animation.partAnimators.get(uuid);
                        if (animator == null) throw new RuntimeException("inconsistent state!! animationsByElement indicated an animator, but none actually present");
                        CompoundTag attachment = animator.getNBT(animation, true);
                        if (attachment != null) anim.add(attachment);
                    }
                    tag.put("anim", anim);
                }

                return tag;
            }
        }
    }
}
