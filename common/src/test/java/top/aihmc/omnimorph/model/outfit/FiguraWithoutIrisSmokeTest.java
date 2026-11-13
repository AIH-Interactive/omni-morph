package top.aihmc.omnimorph.model.outfit;

import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.file.Path;

/** Runs with Iris deliberately absent from the process classpath. */
public final class FiguraWithoutIrisSmokeTest {
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            throw new AssertionError("Iris must be absent for this process");
        } catch (ClassNotFoundException expected) {
            // The public model and material path must load without this optional integration.
        }
        YsmMaterialNativeSmokeTest.replayFiguraModel(Path.of(args[0]), avatar -> {
            var base = new FiguraTexture(avatar, "without-iris", 1, 1);
            var normal = new FiguraTexture(avatar, "without-iris_n", 1, 1);
            var set = new FiguraTextureSet("without-iris", base, null, null, normal);
            try {
                set.uploadIfNeeded();
                if (base.getTexture() == null || base.pbrNormalTexture().getTexture() != normal.getTexture()) {
                    throw new AssertionError("ordinary upload and paired channels work without Iris");
                }
            } finally {
                set.clean();
            }
        });
        System.out.println("PASS Figura material without Iris: formal model load and ordinary texture upload");
    }
}
