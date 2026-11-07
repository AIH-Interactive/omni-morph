package top.aihmc.omm;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Owns the original OmniMorph model data and its resource lifetime. */
final class OmniMorphModel implements AutoCloseable {
    private final Object data;
    private final Class<?> type;

    private OmniMorphModel(Object data, Class<?> type) {
        this.data = data;
        this.type = type;
    }

    static OmniMorphModel load(Path path) throws Exception {
        Class<?> type = Class.forName("top.aihmc.omnimorph.avatar.ysm.YsmModelData");
        System.out.println("SOURCE " + type.getName() + " "
            + type.getProtectionDomain().getCodeSource().getLocation());
        Object data = type.getMethod("read", Path.class, BooleanSupplier.class)
            .invoke(null, path, (BooleanSupplier) () -> false);
        return new OmniMorphModel(data, type);
    }

    Object geometry() throws Exception {
        return type.getMethod("mainGeometry").invoke(data);
    }

    byte[] texture() throws Exception {
        Object option = selectedTexture();
        String path = (String) option.getClass().getMethod("path").invoke(option);
        Object resources = type.getMethod("resources").invoke(data);
        return (byte[]) Class.forName("top.aihmc.omnimorph.avatar.ysm.YsmResourceSource")
            .getMethod("readBytes", String.class).invoke(resources, path);
    }

    Object createRuntime() throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        FabricRuntimeInputs.configure(output.resolve("figura-config"),
            Path.of(System.getProperty("omm.preview.modJar")));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Class<?> avatarType = Class.forName("org.figuramc.figura.avatar.Avatar");
        Object owner = avatarType.getMethod("createLibraryPreview", java.util.UUID.class, String.class)
            .invoke(null, java.util.UUID.randomUUID(), "YSM preview");
        Object option = selectedTexture();
        Object tag = Class.forName("top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader")
            .getMethod("toRuntimeTag", type, option.getClass()).invoke(null, data, option);
        var assemble = Class.forName("top.aihmc.omnimorph.model.ysm.YsmModelRuntime")
            .getMethod("fromData", avatarType, type, tag.getClass());
        // The original preview factory initializes isolated v/c/t stores and the owner/runtime link.
        java.util.function.Function<Object, Object> factory = previewOwner -> {
            try {
                return assemble.invoke(null, previewOwner, data, tag);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Original YSM preview assembly failed", failure);
            }
        };
        try {
            return avatarType.getMethod("createYsmPreviewRuntime", java.util.function.Function.class)
                .invoke(owner, factory);
        } catch (Exception failure) {
            try {
                new PreviewAvatarResources(owner).close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private Object selectedTexture() throws Exception {
        Object manifest = type.getMethod("manifest").invoke(data);
        String selected = (String) manifest.getClass().getMethod("defaultTexture").invoke(manifest);
        var textures = (java.util.List<?>) manifest.getClass().getMethod("textures").invoke(manifest);
        if (textures.isEmpty()) {
            throw new IllegalArgumentException("Original manifest has no textures");
        }
        // YsmModelRuntime.fromData selects the first option when no default id matches.
        Object option = textures.getFirst();
        for (Object texture : textures) {
            if (texture.getClass().getMethod("id").invoke(texture).equals(selected)) {
                option = texture;
                break;
            }
        }
        return option;
    }

    void report() throws Exception {
        Object geometry = geometry();
        Map<?, ?> bones = (Map<?, ?>) geometry.getClass().getField("bones").get(geometry);
        if (bones.isEmpty()) {
            throw new IllegalArgumentException("Original OmniMorph loader returned no model bones");
        }
        System.out.println("OMP MODEL LOADED bones=" + bones.size()
            + " encrypted=" + type.getMethod("encrypted").invoke(data));
        System.out.println("OMP DIAGNOSTICS " + type.getMethod("diagnostics").invoke(data));
    }

    @Override
    public void close() throws Exception {
        ((AutoCloseable) data).close();
    }
}
