package top.aihmc.omm;

import java.lang.reflect.Method;

/** Resolves material choices through the selected MOD's own renderer. */
final class FiguraMaterials {
    private final Object renderer;
    private final Method resolve;
    private final Class<?> customizationType;
    private final RenderInputs inputs;
    private MinecraftTextureSources externalTextures;
    private PreviewSkin skin;

    FiguraMaterials(Object renderer) throws Exception {
        this.renderer = renderer;
        inputs = RenderInputs.read();
        customizationType = Class.forName("org.figuramc.figura.model.PartCustomization");
        resolve = renderer.getClass().getDeclaredMethod("getTexture", customizationType,
            Class.forName("org.figuramc.figura.model.rendering.texture.FiguraTextureSet"), boolean.class);
        resolve.setAccessible(true);
    }

    Object defaults() throws Exception {
        Object base = customizationType.getConstructor().newInstance();
        Class<?> types = Class.forName("top.aihmc.omnimorph.model.rendering.texture.FiguraRenderTypes");
        customizationType.getMethod("setPrimaryRenderType", types).invoke(base, types.getField("TRANSLUCENT").get(null));
        customizationType.getMethod("setSecondaryRenderType", types).invoke(base, types.getField("EMISSIVE").get(null));
        Class<?> overrides = Class.forName("org.figuramc.figura.model.rendering.texture.FiguraTextureSet$OverrideType");
        Class<?> texture = Class.forName("org.figuramc.figura.model.TextureCustomization");
        customizationType.getField("primaryTexture").set(base,
            texture.getConstructor(overrides, Object.class).newInstance(overrides.getField("PRIMARY").get(null), null));
        customizationType.getField("secondaryTexture").set(base,
            texture.getConstructor(overrides, Object.class).newInstance(overrides.getField("SECONDARY").get(null), null));
        customizationType.getField("alpha").set(base, inputs.tint().w);
        customizationType.getField("light").set(base, inputs.packedLight());
        customizationType.getField("overlay").set(base, inputs.packedOverlay());
        return base;
    }

    record Material(Object semantics, Object texture) {}

    Material resolve(Object customization, Object textureSet, boolean primary, int faces, boolean visible) throws Exception {
        Object data = resolveTexture(customization, textureSet, primary);
        Object renderType = field(data, "renderType");
        if (renderType == null) {
            return null;
        }
        Object identifier = field(data, "texture");
        Object selected = null;
        for (Object texture : (Object[]) textureSet.getClass().getField("textures").get(textureSet)) {
            if (texture != null && texture.getClass().getMethod("getLocation").invoke(texture).equals(identifier)) {
                selected = texture;
                break;
            }
        }
        if (selected == null) {
            Object override = customizationType.getField(primary ? "primaryTexture" : "secondaryTexture")
                .get(customization);
            Object value = override.getClass().getMethod("getValue").invoke(override);
            if (Class.forName("org.figuramc.figura.model.rendering.texture.FiguraTexture").isInstance(value)
                    && value.getClass().getMethod("getLocation").invoke(value).equals(identifier)) {
                selected = value;
            }
        }
        if (selected == null) {
            for (String collection : new String[]{"textures", "customTextures"}) {
                var values = (java.util.Map<?, ?>) renderer.getClass().getField(collection).get(renderer);
                for (Object texture : values.values()) {
                    if (texture.getClass().getMethod("getLocation").invoke(texture).equals(identifier)) {
                        selected = texture;
                        break;
                    }
                }
                if (selected != null) {
                    break;
                }
            }
        }
        if (selected == null) {
            if (identifier != null && identifier.toString().equals(PreviewSkin.IDENTIFIER)) {
                if (skin == null) {
                    skin = new PreviewSkin();
                }
                selected = skin.bytes();
            } else if (identifier != null && identifier.toString().startsWith("minecraft:textures/")) {
                if (externalTextures == null) {
                    externalTextures = new MinecraftTextureSources();
                }
                selected = externalTextures.read(identifier.toString());
            } else {
                throw new UnsupportedOperationException("Unresolved Figura resource texture: " + identifier);
            }
        }
        Object color = field(data, "color");
        Class<?> semantics = Class.forName("top.aihmc.omnimorph.render.acceleration.DrawSemantics");
        Class<?> layer = Class.forName("top.aihmc.omnimorph.render.acceleration.DrawSemantics$Layer");
        int light = (boolean) field(data, "fullBright") ? 0x00f000f0
            : (int) customizationType.getField("light").get(customization);
        Object result = semantics.getConstructors()[0].newInstance(renderType, identifier,
            layer.getField(primary ? "PRIMARY" : "SECONDARY").get(null), "figura-preview", primary ? 0 : 1,
            faces, light, customizationType.getField("overlay").get(customization),
            ((Number) field(color, "x")).floatValue() * inputs.tint().x,
            ((Number) field(color, "y")).floatValue() * inputs.tint().y,
            ((Number) field(color, "z")).floatValue() * inputs.tint().z,
            customizationType.getField("alpha").get(customization),
            ((Number) field(data, "vertexOffset")).doubleValue(), visible);
        return new Material(result, selected);
    }

    private Object resolveTexture(Object customization, Object textureSet, boolean primary) throws Exception {
        var textureField = customizationType.getField(primary ? "primaryTexture" : "secondaryTexture");
        Object original = textureField.get(customization);
        Object override = original == null ? null : original.getClass().getMethod("getOverrideType").invoke(original);
        if (override == null || !override.toString().equals("SKIN")) {
            return resolve.invoke(renderer, customization, textureSet, primary);
        }
        // Only replace the external player resource; keep the original renderer's material selection.
        Object resource = override.getClass().getField("RESOURCE").get(null);
        Object replacement = original.getClass().getConstructor(override.getClass(), Object.class)
            .newInstance(resource, PreviewSkin.IDENTIFIER);
        try {
            textureField.set(customization, replacement);
            return resolve.invoke(renderer, customization, textureSet, primary);
        } finally {
            textureField.set(customization, original);
        }
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
