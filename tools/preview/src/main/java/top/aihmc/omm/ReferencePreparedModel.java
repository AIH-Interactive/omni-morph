package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Map;

/** Original parsed/baked model with its selected original texture and animation input. */
record ReferencePreparedModel(Object model, Path texture, ReferenceModelAnimation animation) {
    static ReferencePreparedModel load(String backend, Path path, JsonObject inputs, Path texture) throws Exception {
        String prefix = backend.equals("spm") ? "com.micaftic.morpher" : "com.elfmcys.yesstevemodel";
        Object raw = JarEntryProbe.readModel(prefix, backend, path);
        Object entity = raw.getClass().getField("mainEntity").get(raw);
        Object geometry = entity.getClass().getField("mainModel").get(entity);
        if (geometry == null) {
            throw new IllegalArgumentException("Original reader returned no main geometry: " + path);
        }
        var textures = (Map<?, ?>) entity.getClass().getField("textures").get(entity);
        var animations = (Map<?, ?>) entity.getClass().getField("animationFiles").get(entity);
        JarEntryProbe.decodeTexture(prefix, backend, raw, textures, texture);
        Object model = JarEntryProbe.bakeGeometry(prefix, backend, geometry, texture);
        ReferenceModelAnimation animation = inputs.has("animation")
            ? new ReferenceModelAnimation(prefix, model, animations, inputs.getAsJsonObject("animation")) : null;
        return new ReferencePreparedModel(model, texture, animation);
    }

    boolean translucent() throws Exception {
        return (boolean) model.getClass().getMethod("isTranslucentTexture", int.class).invoke(model, 0);
    }
}
