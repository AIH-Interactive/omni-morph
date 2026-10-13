package org.figuramc.figura.avatar.ysm.binary;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;

import java.io.IOException;
import java.util.Map;

/** Maps binary metadata to the same schema consumed by folder models, without embedding image bytes. */
public final class YsmBinaryMetadataMapper {
    private YsmBinaryMetadataMapper() {
    }

    public static JsonObject map(YsmBinaryModel model, ImageSink images,
                                  YsmDecodeBudget budget) throws IOException {
        budget.check("mapping/metadata", 0);
        JsonObject result = new JsonObject();
        result.addProperty("spec", 2);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", model.metadata.name);
        metadata.addProperty("tips", model.metadata.tips);
        JsonObject license = new JsonObject();
        license.addProperty("type", model.metadata.licenseType);
        license.addProperty("description", model.metadata.licenseDescription);
        metadata.add("license", license);
        metadata.add("link", strings(model.metadata.links));
        JsonArray authors = new JsonArray();
        JsonArray imageDescriptors = new JsonArray();
        for (var raw : model.metadata.authors) {
            budget.check("mapping/author", 0);
            JsonObject author = new JsonObject();
            author.addProperty("name", raw.name);
            author.addProperty("role", raw.role);
            author.addProperty("comment", raw.comment);
            author.add("contact", strings(raw.contacts));
            String avatar = raw.avatar;
            if (raw.avatarImage != null && raw.avatarImage.data != null && raw.avatarImage.data.length != 0) {
                avatar = image(raw.avatarImage, images, imageDescriptors, budget);
            }
            author.addProperty("avatar", avatar);
            authors.add(author);
        }
        metadata.add("authors", authors);
        JsonArray extraAvatars = new JsonArray();
        for (var raw : model.metadata.extraAvatars) {
            extraAvatars.add(image(raw, images, imageDescriptors, budget));
        }
        metadata.add("ysm_extra_avatars", extraAvatars);
        result.add("metadata", metadata);

        var raw = model.properties;
        JsonObject properties = new JsonObject();
        properties.addProperty("width_scale", raw.widthScale);
        properties.addProperty("height_scale", raw.heightScale);
        properties.addProperty("default_texture", raw.defaultTexture);
        properties.addProperty("preview_animation", raw.previewAnimation);
        properties.addProperty("free", raw.isFree);
        properties.addProperty("render_layers_first", raw.renderLayersFirst);
        properties.addProperty("all_cutout", raw.allCutout);
        properties.addProperty("disable_preview_rotation", raw.disablePreviewRotation);
        properties.addProperty("gui_no_lighting", raw.guiNoLighting);
        properties.addProperty("merge_multiline_expr", raw.mergeMultilineExpr);
        properties.addProperty("gui_foreground", raw.guiForeground);
        properties.addProperty("gui_background", raw.guiBackground);
        properties.add("extra_animation", strings(raw.extraAnimations));
        JsonArray classifications = new JsonArray();
        for (var classification : raw.extraAnimationClassifies) {
            JsonObject object = new JsonObject();
            object.addProperty("id", classification.id);
            object.add("extra_animation", strings(classification.extras));
            classifications.add(object);
        }
        properties.add("extra_animation_classify", classifications);
        JsonArray buttons = new JsonArray();
        for (var button : raw.extraAnimationButtons) {
            JsonObject object = new JsonObject();
            object.addProperty("id", button.id);
            object.addProperty("name", button.name);
            object.addProperty("description", button.description);
            JsonArray forms = new JsonArray();
            for (var form : button.forms) {
                JsonObject config = new JsonObject();
                config.addProperty("type", form.type);
                config.addProperty("title", form.title);
                config.addProperty("description", form.description);
                // The reference field's name is misleading: this is the Molang value expression.
                config.addProperty("value", form.defaultValue);
                config.addProperty("step", form.step);
                config.addProperty("min", form.min);
                config.addProperty("max", form.max);
                config.add("labels", strings(form.labels));
                forms.add(config);
            }
            object.add("config_forms", forms);
            buttons.add(object);
        }
        properties.add("extra_animation_buttons", buttons);
        JsonObject backgrounds = new JsonObject();
        for (var background : raw.backgroundImages) {
            String path = image(background, images, imageDescriptors, budget);
            if (backgrounds.has(background.name)) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA,
                        "mapping/duplicate-background", 0);
            }
            backgrounds.addProperty(background.name, path);
        }
        properties.add("ysm_background_images", backgrounds);
        result.add("properties", properties);
        result.add("ysm_binary_images", imageDescriptors);

        JsonObject footer = new JsonObject();
        footer.addProperty("format_version", model.formatVersion);
        footer.addProperty("resource_hash", raw.sha256);
        footer.addProperty("version", model.footer.version);
        footer.addProperty("unknown_1", model.footer.unkInt1);
        footer.addProperty("unknown_2", model.footer.unkInt2);
        footer.addProperty("random", model.footer.rand);
        footer.addProperty("time", model.footer.time);
        footer.addProperty("extra", model.footer.extra);
        result.add("ysm_binary_footer", footer);
        return result;
    }

    private static String image(YsmBinaryModel.RawImage raw, ImageSink images,
                                  JsonArray descriptors, YsmDecodeBudget budget) throws IOException {
        budget.check("mapping/metadata-image", 0);
        if (raw.data == null || raw.data.length == 0) {
            return "";
        }
        String path = images.write(raw);
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("name", raw.name);
        descriptor.addProperty("path", path);
        descriptor.addProperty("width", raw.width);
        descriptor.addProperty("height", raw.height);
        descriptor.addProperty("encoding", raw.format);
        descriptor.addProperty("unknown_flag", raw.unknownFlag);
        descriptors.add(descriptor);
        return path;
    }

    private static JsonObject strings(Map<String, String> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }

    @FunctionalInterface
    public interface ImageSink {
        String write(YsmBinaryModel.RawImage image) throws IOException;
    }
}
