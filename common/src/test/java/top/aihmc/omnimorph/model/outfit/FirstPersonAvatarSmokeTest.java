package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;
import top.aihmc.omnimorph.model.rendering.FirstPersonItemRender;
import top.aihmc.omnimorph.model.rendering.nodeRenderer.FiguraSubmission;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.lang.reflect.Proxy;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

/** Opt-in local corpus replay: never copies the author's package into the repository. */
public final class FirstPersonAvatarSmokeTest {
    public static void main(String[] args) throws Exception {
        var setting = org.figuramc.figura.config.Configs.FIRST_PERSON_ITEM_REPLACEMENT;
        boolean previous = setting.value;
        setting.setValue("true");
        try {
            replay(args);
        } finally {
            setting.setValue(Boolean.toString(previous));
        }
    }

    private static void replay(String[] args) throws Exception {
        try (var fs = FileSystems.newFileSystem(Path.of(args[1]))) {
            YsmMaterialNativeSmokeTest.replayFiguraModel(Path.of(args[0]), ignored -> {
                Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "first-person corpus");
                try {
                    var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    field.setAccessible(true);
                    var unsafe = (sun.misc.Unsafe) field.get(null);
                    Minecraft.getInstance().level = (FirstPersonItemRenderSmokeTest.RegistryLevel)
                            unsafe.allocateInstance(FirstPersonItemRenderSmokeTest.RegistryLevel.class);
                    var nbt = LocalAvatarLoader.compileFiguraAvatar(fs.getPath("/youling"), UUID.randomUUID());
                    avatar.permissions.insert(org.figuramc.figura.permissions.Permissions.COMPLEXITY,
                            Integer.MAX_VALUE, "corpus replay");
                    avatar.load(nbt);
                    avatar.initializePreviewPlayer(null);
                    require(avatar.luaRuntime != null && !avatar.scriptError, "author scripts load: " + avatar.errorText);
                    var submissions = new ArrayList<FiguraSubmission>();
                    var collector = (SubmitNodeCollector) Proxy.newProxyInstance(
                            SubmitNodeCollector.class.getClassLoader(),
                            new Class<?>[]{SubmitNodeCollector.class, NodeCollectorExtension.class},
                            (proxy, method, values) -> {
                                if (method.getName().equals("submitFiguraModel")) {
                                    @SuppressWarnings("unchecked")
                                    var draw = (org.apache.commons.lang3.function.TriFunction<Avatar,
                                            net.minecraft.client.renderer.entity.state.EntityRenderState,
                                            top.aihmc.omnimorph.model.rendering.FiguraVertexSource, Void>) values[2];
                                    submissions.add(new FiguraSubmission(avatar, null, draw));
                                }
                                return method.getName().equals("getFiguraSubmissions") ? submissions : null;
                            });
                    Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(
                            net.minecraft.core.component.DataComponentMap.EMPTY);
                    FirstPersonItemRender.withAvatar(avatar, () -> require(FirstPersonItemRender.submit(
                            new ItemStack(Items.DIAMOND_SWORD), ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            ItemTransform.NO_TRANSFORM, new PoseStack(), collector, 0xf000f0, 0),
                            "author diamond sword reaches first-person replacement"));
                    var positions = new ArrayList<float[]>();
                    var sink = OutfitNativeRenderSmokeTest.vertexSink(positions);
                    for (var submission : submissions) {
                        submission.renderer().apply(avatar, null, type -> sink);
                    }
                    require(!positions.isEmpty(), "author Item geometry emits actual vertices");
                    System.out.println("PASS author ZIP diamond sword replacement: " + positions.size() + " vertices");
                } catch (Exception failure) {
                    throw new AssertionError("author ZIP replay failed", failure);
                } finally {
                    avatar.clean();
                }
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
