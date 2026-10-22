package top.aihmc.omnimorph.model.outfit;

import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;

public final class OutfitContractSmokeTest {
    public static void main(String[] args) throws Exception {
        String fixture = Files.readString(Path.of(args[0], "outfit-v1/duplicate-instances.json"));
        OutfitConfig config = OutfitCodec.decode(fixture);
        OutfitConfig copy = OutfitCodec.decode(OutfitCodec.encode(config));
        require(config.equals(copy), "typed JSON round trip");
        require(config.instances().size() == 2, "two independent copies survive");
        require(!config.instances().get(0).instanceId().equals(config.instances().get(1).instanceId()), "instance identity");
        require(config.instances().get(0).resource().equals(config.instances().get(1).resource()), "same immutable resource");
        var narrow = new OutfitLimits(1, 128, 64, 8192, 32768, 1024, 65536, 0, 256,
                128, 128, 512, 64, 256, 128, 4096, 36000, 0.001, 64, true, 8, 15, 16);
        reject(() -> OutfitCodec.decode(fixture, narrow));
        reject(() -> OutfitCodec.encode(config, narrow));
        config.validateLimits(OutfitLimits.defaults());
        customParticleLimits();
        reject(() -> OutfitCodec.decode(fixture.replace("STANDARD", "UNKNOWN")));
        reject(() -> OutfitCodec.decode(fixture.replace("LOCAL", "UNKNOWN")));
        reject(() -> new OutfitConfig.Vector(Double.NaN, 0, 0));
        reject(() -> new OutfitConfig.Transform(new OutfitConfig.Vector(0, 0, 0),
                new OutfitConfig.Vector(0, 0, 0), new OutfitConfig.Vector(0, 1, 1)));
        reject(() -> new OutfitConfig(1, 0, null,
                java.util.List.of(config.instances().getFirst(), config.instances().getFirst()),
                java.util.List.of(), config.displayOrdinals(), false));

        CosmeticMetadata metadata = CosmeticMetadata.read("{\"name\":\"星翼\",\"authors\":[\"作者\"],\"cosmetic_anchor\":\"wing_anchor\"}");
        require(metadata.common().name.equals("星翼"), "shared common metadata");
        require(metadata.defaultAnchor().type() == OutfitConfig.AnchorType.STANDARD, "typed author default");
        require(CosmeticMetadata.read("{}").defaultAnchor().type() == OutfitConfig.AnchorType.UNRESOLVED,
                "missing default stays unbound");
        require(CosmeticMetadata.read("{\"category\":\"FutureCategory\"}").category().equals("FutureCategory"),
                "unknown categories survive");
        reject(() -> CosmeticMetadata.read("{\"has_bones\":false,\"edit_bones\":true}"));
        reject(() -> CosmeticMetadata.selectManifest(false, false, null));
        reject(() -> CosmeticMetadata.selectManifest(true, true, null));
        require(CosmeticMetadata.selectManifest(true, true, OutfitConfig.ResourceKind.ACCESSORY).equals("cosmetic.json"),
                "explicit purpose chooses the accessory manifest");

        // Non-symmetric axis marker freezes column-vector composition in model units.
        Vector3f marker = new OutfitConfig.Transform(new OutfitConfig.Vector(1, 2, 3),
                new OutfitConfig.Vector(0, 0, 90), new OutfitConfig.Vector(2, 3, 4)).modelMatrix()
                .transformPosition(new Vector3f(1, 2, 3));
        require(marker.distance(new Vector3f(-5, 4, 15)) < 0.00001f, "T * Rx * Ry * Rz * S axis contract");
        System.out.println("Outfit contract smoke test passed (data/matrices only; no GUI validation).");
    }

    private static void customParticleLimits() {
        var json = new com.google.gson.GsonBuilder()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        var advertised = json.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        String[] keys = {"max_custom_particles", "max_particle_layers", "max_ribbon_vertices",
                "max_particle_callbacks_per_tick", "max_particle_callbacks_per_frame"};
        int[] maximums = {4096, 8, 32768, 1024, 512};
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            int maximum = maximums[index];
            for (int invalid : new int[]{-1, maximum + 1}) {
                var input = advertised.deepCopy();
                input.addProperty(key, invalid);
                reject(() -> json.fromJson(input, OutfitLimits.class));
            }
            advertised.addProperty(key, maximum);
        }
        var maximum = json.fromJson(advertised, OutfitLimits.class);
        require(maximum.maxCustomParticles() == 4096 && maximum.maxParticleLayers() == 8
                        && maximum.maxRibbonVertices() == 32768 && maximum.maxParticleCallbacksPerTick() == 1024
                        && maximum.maxParticleCallbacksPerFrame() == 512,
                "backend particle limits reach typed client without truncation");
        for (String key : keys) {
            advertised.addProperty(key, 0);
        }
        var disabled = json.fromJson(advertised, OutfitLimits.class);
        require(disabled.maxCustomParticles() == 0 && disabled.maxParticleLayers() == 0
                        && disabled.maxRibbonVertices() == 0 && disabled.maxParticleCallbacksPerTick() == 0
                        && disabled.maxParticleCallbacksPerFrame() == 0,
                "explicit zero disables instead of selecting defaults");
        for (String key : keys) {
            advertised.remove(key);
        }
        require(json.fromJson(advertised, OutfitLimits.class).equals(OutfitLimits.defaults()),
                "older backend missing extension keys retains conservative defaults");
        require(json.fromJson(json.toJson(maximum), OutfitLimits.class).equals(maximum),
                "negotiated particle limits round trip");
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("invalid configuration accepted");
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
