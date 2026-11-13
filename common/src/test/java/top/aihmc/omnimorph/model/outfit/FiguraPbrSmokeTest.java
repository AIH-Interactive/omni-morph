package top.aihmc.omnimorph.model.outfit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.irisshaders.iris.pbr.loader.PBRTextureLoader;
import net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.compat.IrisCompat;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;

/** Formal model texture parsing to the real Iris loader registry and consumer; GPU pixels remain manual. */
public final class FiguraPbrSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("figura-pbr-test-");
        try {
            Files.copy(Path.of(args[0], "avatar.json"), directory.resolve("avatar.json"));
            JsonObject model = JsonParser.parseString(Files.readString(Path.of(args[0], "model.bbmodel"))).getAsJsonObject();
            JsonArray textures = new JsonArray();
            for (String name : new String[]{"albedo", "albedo_n", "albedo_s", "plain",
                    "normalOnly", "normalOnly_n", "specOnly", "specOnly_s"}) {
                JsonObject texture = new JsonObject();
                texture.addProperty("name", name + ".png");
                texture.addProperty("source", "data:image/png;base64," + png());
                textures.add(texture);
            }
            model.add("textures", textures);
            Files.writeString(directory.resolve("model.bbmodel"), model.toString());
            IrisCompat.registerFiguraPbr();
            var loader = PBRTextureLoaderRegistry.INSTANCE.getLoader(FiguraTexture.class);
            require(loader != null, "Figura dynamic textures have an Iris PBR loader");
            YsmMaterialNativeSmokeTest.replayFiguraModel(directory, avatar -> {
                var base = texture(avatar, "albedo");
                var normal = texture(avatar, "albedo_n");
                var specular = texture(avatar, "albedo_s");
                var consumer = new Channels();
                loader.load(base, null, consumer);
                require(consumer.normal != null && consumer.specular != null, "both declared channels reach Iris");
                require(consumer.normal.getTexture() == normal.getTexture(), "normal pairs with the authored normal image");
                require(consumer.specular.getTexture() == specular.getTexture(), "specular pairs with the authored specular image");
                FirstPersonItemRenderSmokeTest.check(avatar,
                        new org.figuramc.figura.model.rendering.texture.FiguraTextureSet(
                                "combined item material", base, null, specular, normal));
                var afterItem = new Channels();
                loader.load(base, null, afterItem);
                require(afterItem.normal.getTexture() == consumer.normal.getTexture()
                                && afterItem.specular.getTexture() == consumer.specular.getTexture(),
                        "first-person item and FOV keep the authored PBR channels live");
                consumer.normal.close();
                consumer.specular.close();
                normal.fill(0, 0, 1, 1, 0.2, 0.4, 0.6, 1.0).update();
                var reloaded = new Channels();
                loader.load(base, null, reloaded);
                require(reloaded.normal.getTexture() == normal.getTexture(), "Iris reload and authored updates retain live ownership");
                require(reloaded.specular.getTexture() == specular.getTexture(), "Iris cannot close the model's authored channel");
                var plain = new Channels();
                loader.load(texture(avatar, "plain"), null, plain);
                require(plain.normal == null && plain.specular == null, "plain base does not inherit another skin's channels");
                var normalOnly = new Channels();
                loader.load(texture(avatar, "normalOnly"), null, normalOnly);
                require(normalOnly.normal != null && normalOnly.specular == null, "missing specular remains absent");
                var specOnly = new Channels();
                loader.load(texture(avatar, "specOnly"), null, specOnly);
                require(specOnly.normal == null && specOnly.specular != null, "missing normal remains absent");
                var copy = org.figuramc.figura.avatar.Avatar.createLibraryPreview(
                        java.util.UUID.randomUUID(), "independent PBR instance");
                try {
                    copy.load(avatar.nbt.copy());
                    copy.initializePreviewPlayer(null);
                    var other = new Channels();
                    loader.load(texture(copy, "albedo"), null, other);
                    require(other.normal.getTexture() != consumer.normal.getTexture(),
                            "separate avatars never share owned channel allocations");
                    copy.clean();
                    require(texture(avatar, "albedo").pbrNormalTexture() != null,
                            "retiring another instance does not retire this material");
                    require(texture(copy, "albedo").pbrNormalTexture() == null,
                            "retired material cannot supply new PBR channels");
                } finally {
                    copy.clean();
                }
            });
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        System.out.println("PASS Figura PBR: formal parsing, Iris consumer, channel pairing and borrowed lifetime; shader pixels pending");
    }

    private static FiguraTexture texture(org.figuramc.figura.avatar.Avatar avatar, String name) {
        return avatar.renderer.textures.entrySet().stream()
                .filter(entry -> entry.getKey().equals(name) || entry.getKey().endsWith("." + name))
                .findFirst().orElseThrow(() -> new AssertionError("Missing formal texture " + name)).getValue();
    }

    private static String png() throws Exception {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff7f7fff);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private static final class Channels implements PBRTextureLoader.PBRTextureConsumer {
        private AbstractTexture normal;
        private AbstractTexture specular;

        @Override
        public void acceptNormalTexture(AbstractTexture texture) {
            normal = texture;
        }

        @Override
        public void acceptSpecularTexture(AbstractTexture texture) {
            specular = texture;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
