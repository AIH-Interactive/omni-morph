package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

/** Exercises independent original Figura animation state without a game or copied model parser. */
public final class FiguraInstanceIsolationProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        FabricRuntimeInputs.configure(output.resolve("figura-config"), Path.of(System.getProperty("omm.preview.modJar")));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        Class.forName("com.mojang.blaze3d.systems.RenderSystem").getMethod("initRenderThread").invoke(null);
        Path model = Path.of(args[0]);
        Object compiled = FiguraWorker.compileModel(model);
        Object first = FiguraWorker.createRuntime(compiled.getClass().getMethod("copy").invoke(compiled), model);
        try (var firstOwner = new PreviewAvatarResources(first)) {
            Object second = FiguraWorker.createRuntime(compiled.getClass().getMethod("copy").invoke(compiled), model);
            try (var secondOwner = new PreviewAvatarResources(second)) {
                verify(first, second, args[1]);
            }
        }
    }

    private static void verify(Object first, Object second, String clip) throws Exception {
        Object firstRenderer = first.getClass().getField("renderer").get(first);
        Object secondRenderer = second.getClass().getField("renderer").get(second);
        if (first == second || firstRenderer == secondRenderer
            || first.getClass().getField("animations").get(first) == second.getClass().getField("animations").get(second)) {
            throw new AssertionError("Figura instances share mutable renderer/animation state");
        }
        var configuration = new JsonObject();
        configuration.addProperty("name", clip);
        var firstAnimation = new FiguraAnimationInput(first, configuration);
        var secondAnimation = new FiguraAnimationInput(second, configuration);
        firstAnimation.sample(0.1f);
        secondAnimation.sample(0.1f);
        try (var firstBatches = new FiguraBatches(firstRenderer);
             var secondBatches = new FiguraBatches(secondRenderer)) {
            List<double[]> firstSnapshot = firstBatches.transform(false);
            secondAnimation.sample(0.3f);
            secondBatches.refresh();
            List<double[]> secondSnapshot = secondBatches.transform(false);
            firstBatches.refresh();
            if (YsmInstanceIsolationProbe.difference(firstSnapshot, firstBatches.transform(false)) != 0) {
                throw new AssertionError("Second Figura animation mutated first instance");
            }
            double change = YsmInstanceIsolationProbe.difference(firstSnapshot, secondSnapshot);
            if (change <= 1e-6) {
                throw new AssertionError("Samples did not exercise original Figura animation changes");
            }
            firstAnimation.sample(0.2f);
            firstBatches.refresh();
            secondBatches.refresh();
            if (YsmInstanceIsolationProbe.difference(secondSnapshot, secondBatches.transform(false)) != 0) {
                throw new AssertionError("First Figura animation mutated second instance");
            }
            System.out.println("FIGURA INSTANCE ISOLATION PASS animationChange=" + change
                + " batches=" + firstSnapshot.size() + " bidirectional=true");
            verifyVisibility(firstRenderer, firstBatches);
            verifyDrawOrder(firstBatches.drawBatches());
            verifyCustomTexture(first, firstRenderer);
            verifyVanillaPlayer(first);
        }
    }

    private static void verifyVanillaPlayer(Object owner) throws Exception {
        Object api = Class.forName("org.figuramc.figura.lua.api.vanilla_model.VanillaModelAPI")
            .getConstructor(owner.getClass()).newInstance(owner);
        var steve = new VanillaPlayerGeometry(false);
        var alex = new VanillaPlayerGeometry(true);
        double[] regular = steve.vertices(api);
        double[] slim = alex.vertices(api);
        if (regular.length == 0 || regular.length != slim.length || java.util.Arrays.equals(regular, slim)) {
            throw new AssertionError("Original player models did not expose distinct, complete geometry");
        }
        Object player = api.getClass().getField("PLAYER").get(api);
        var setVisible = player.getClass().getMethod("setVisible", Boolean.class);
        setVisible.invoke(player, false);
        if (steve.vertices(api).length != 0 || alex.vertices(api).length != 0) {
            throw new AssertionError("Original PLAYER group hide did not hide the entire player mesh");
        }
        setVisible.invoke(player, new Object[]{null});
        if (!java.util.Arrays.equals(regular, steve.vertices(api))) {
            throw new AssertionError("Original player visibility did not restore to default");
        }
        Object arm = api.getClass().getField("LEFT_ARM").get(api);
        arm.getClass().getMethod("setVisible", Boolean.class).invoke(arm, false);
        double[] partial = steve.vertices(api);
        if (partial.length <= 0 || partial.length >= regular.length) {
            throw new AssertionError("Original LEFT_ARM hide did not preserve other player parts");
        }
        if (!java.util.Arrays.equals(regular, steve.vertices(null))) {
            throw new AssertionError("Figura customization leaked into the underlying Minecraft model");
        }
        System.out.println("VANILLA PLAYER GEOMETRY PASS regularVertices=" + regular.length / 9
            + " partialVertices=" + partial.length / 9 + " slimDistinct=true groupVisibility=true restored=true");
    }

    private static void verifyCustomTexture(Object owner, Object renderer) throws Exception {
        var setsField = renderer.getClass().getSuperclass().getDeclaredField("textureSets");
        setsField.setAccessible(true);
        Object textureSet = ((List<?>) setsField.get(renderer)).getFirst();
        Class<?> textureType = Class.forName("org.figuramc.figura.model.rendering.texture.FiguraTexture");
        Object custom = textureType.getConstructor(owner.getClass(), String.class, int.class, int.class)
            .newInstance(owner, "preview-custom-probe", 2, 2);
        try {
            var materials = new FiguraMaterials(renderer);
            Object settings = materials.defaults();
            Class<?> overrideType = Class.forName("org.figuramc.figura.model.rendering.texture.FiguraTextureSet$OverrideType");
            Class<?> customization = Class.forName("org.figuramc.figura.model.TextureCustomization");
            settings.getClass().getField("primaryTexture").set(settings,
                customization.getConstructor(overrideType, Object.class)
                    .newInstance(overrideType.getField("CUSTOM").get(null), custom));
            var material = materials.resolve(settings, textureSet, true, 1, true);
            if (material == null || material.texture() != custom) {
                throw new AssertionError("Original CUSTOM texture object was not preserved");
            }
            Object location = textureType.getMethod("getLocation").invoke(custom);
            @SuppressWarnings("unchecked")
            var registered = (java.util.Map<String, Object>) renderer.getClass().getField("customTextures").get(renderer);
            registered.put("preview-custom-probe", custom);
            try {
                settings.getClass().getField("primaryTexture").set(settings,
                    customization.getConstructor(overrideType, Object.class)
                        .newInstance(overrideType.getField("RESOURCE").get(null), location.toString()));
                if (materials.resolve(settings, textureSet, true, 1, true).texture() != custom) {
                    throw new AssertionError("Registered original custom texture was not resolved by identifier");
                }
            } finally {
                registered.remove("preview-custom-probe");
            }
            Path output = Path.of(System.getProperty("omm.preview.output"), "custom-texture-probe.png");
            settings.getClass().getField("primaryTexture").set(settings,
                customization.getConstructor(overrideType, Object.class)
                    .newInstance(overrideType.getField("RESOURCE").get(null), "minecraft:textures/block/grass_block_top.png"));
            Object selected = materials.resolve(settings, textureSet, true, 1, true).texture();
            var config = RuntimeConfiguration.json(output.getParent().resolve("effective-config.json"));
            try (var zip = new java.util.zip.ZipFile(config.get("textureJar").getAsString());
                 var stream = zip.getInputStream(zip.getEntry("assets/minecraft/textures/block/grass_block_top.png"))) {
                if (!(selected instanceof byte[] bytes) || !java.util.Arrays.equals(bytes, stream.readAllBytes())) {
                    throw new AssertionError("Original RESOURCE binding did not preserve configured Minecraft texture bytes");
                }
            }
            System.out.println("FIGURA MINECRAFT RESOURCE PASS identifier=minecraft:textures/block/grass_block_top.png bytesExact=true");
            Object skinOverride = customization.getConstructor(overrideType, Object.class)
                .newInstance(overrideType.getField("SKIN").get(null), null);
            settings.getClass().getField("primaryTexture").set(settings, skinOverride);
            Object skin = materials.resolve(settings, textureSet, true, 1, true).texture();
            if (!(skin instanceof byte[] skinBytes) || !java.util.Arrays.equals(skinBytes,
                    java.nio.file.Files.readAllBytes(Path.of(config.get("skinPath").getAsString())))) {
                throw new AssertionError("Original SKIN material did not use configured skin bytes");
            }
            if (settings.getClass().getField("primaryTexture").get(settings) != skinOverride) {
                throw new AssertionError("Skin resource substitution changed the original customization");
            }
            System.out.println("FIGURA CONFIGURED SKIN PASS bytesExact=true customizationRestored=true");
            textureType.getMethod("writeTexture", Path.class).invoke(custom, output);
            var image = javax.imageio.ImageIO.read(output.toFile());
            if (image == null || image.getWidth() != 2 || image.getHeight() != 2) {
                throw new AssertionError("Original custom texture could not be encoded for the window");
            }
            System.out.println("FIGURA CUSTOM TEXTURE PASS directAndRegistered=true size=2x2");
        } finally {
            textureType.getMethod("close").invoke(custom);
        }
    }

    private static void verifyDrawOrder(List<Object> batches) throws Exception {
        var seen = new java.util.HashSet<Long>();
        var previousDistances = new java.util.HashMap<Object, Float>();
        int expectedFaces = 0;
        int sortedFaces = 0;
        for (Object batch : batches) {
            Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
            if ((boolean) semantics.getClass().getMethod("visible").invoke(semantics)) {
                Object mesh = batch.getClass().getMethod("mesh").invoke(batch);
                expectedFaces += (int) mesh.getClass().getMethod("faceCount").invoke(mesh);
            }
        }
        List<OmniMorphDrawOrder.Range> ranges = new OmniMorphDrawOrder().ranges(batches);
        for (var range : ranges) {
            Object batch = batches.get(range.batch());
            Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
            Object material = semantics.getClass().getMethod("material").invoke(semantics);
            Object mesh = batch.getClass().getMethod("mesh").invoke(batch);
            Object pose = batch.getClass().getMethod("pose").invoke(batch);
            int faceCount = (int) mesh.getClass().getMethod("faceCount").invoke(mesh);
            if (!(boolean) semantics.getClass().getMethod("visible").invoke(semantics)
                    || range.firstIndex() % 6 != 0 || range.indexCount() % 6 != 0
                    || range.firstIndex() < 0 || range.firstIndex() + range.indexCount() > faceCount * 6) {
                throw new AssertionError("Invalid or hidden face range: " + range);
            }
            boolean sorted = (boolean) material.getClass().getMethod("sortOnUpload").invoke(material);
            for (int index = range.firstIndex(); index < range.firstIndex() + range.indexCount(); index += 6) {
                int face = index / 6;
                if (!seen.add((long) range.batch() << 32 | Integer.toUnsignedLong(face))) {
                    throw new AssertionError("Face submitted twice");
                }
                if (sorted) {
                    if ((double) semantics.getClass().getMethod("vertexOffset").invoke(semantics) != 0) {
                        throw new AssertionError("This distance probe requires an unoffset fixture");
                    }
                    double[] first = new double[4];
                    double[] third = new double[4];
                    var transform = pose.getClass().getDeclaredMethod("transformedPosition", mesh.getClass(), int.class, double[].class);
                    transform.setAccessible(true);
                    transform.invoke(pose, mesh, face * 4, first);
                    transform.invoke(pose, mesh, face * 4 + 2, third);
                    float x = ((float) first[0] + (float) third[0]) / 2;
                    float y = ((float) first[1] + (float) third[1]) / 2;
                    float z = ((float) first[2] + (float) third[2]) / 2;
                    float distance = new org.joml.Vector3f().distanceSquared(x, y, z);
                    Float previous = previousDistances.put(material, distance);
                    if (previous != null && Float.compare(previous, distance) < 0) {
                        throw new AssertionError("Transparent faces not in descending original-space distance order");
                    }
                    sortedFaces++;
                }
            }
        }
        if (seen.size() != expectedFaces || sortedFaces == 0) {
            throw new AssertionError("Missing faces or fixture did not exercise transparent sorting");
        }
        System.out.println("FIGURA ORIGINAL SORT PASS faces=" + expectedFaces
            + " sortedFaces=" + sortedFaces + " ranges=" + ranges.size());
    }

    private static void verifyVisibility(Object renderer, FiguraBatches batches) throws Exception {
        List<Object> before = batches.drawBatches();
        Object root = renderer.getClass().getField("root").get(renderer);
        Object customization = root.getClass().getField("customization").get(root);
        var visible = customization.getClass().getField("visible");
        boolean original = visible.getBoolean(customization);
        try {
            visible.setBoolean(customization, false);
            batches.refresh();
            List<Object> hidden = batches.drawBatches();
            if (hidden.size() != before.size()) {
                throw new AssertionError("Figura visibility changed fixed mesh/texture slot count");
            }
            for (int index = 0; index < hidden.size(); index++) {
                Object batch = hidden.get(index);
                Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
                if ((boolean) semantics.getClass().getMethod("visible").invoke(semantics)) {
                    throw new AssertionError("Hidden Figura parent left a visible child batch");
                }
                Object oldMesh = before.get(index).getClass().getMethod("mesh").invoke(before.get(index));
                Object newMesh = batch.getClass().getMethod("mesh").invoke(batch);
                if (oldMesh != newMesh) {
                    throw new AssertionError("Figura visibility changed mesh slot identity");
                }
            }
        } finally {
            visible.setBoolean(customization, original);
            batches.refresh();
        }
        for (int index = 0; index < before.size(); index++) {
            Object oldState = before.get(index).getClass().getMethod("semantics").invoke(before.get(index));
            Object batch = batches.drawBatches().get(index);
            Object restored = batch.getClass().getMethod("semantics").invoke(batch);
            if (!oldState.getClass().getMethod("visible").invoke(oldState)
                    .equals(restored.getClass().getMethod("visible").invoke(restored))) {
                throw new AssertionError("Figura visibility was not restored");
            }
        }
        System.out.println("FIGURA VISIBILITY PASS slots=" + before.size() + " hiddenAndRestored=true");
    }
}
