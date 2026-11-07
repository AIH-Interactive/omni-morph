package top.aihmc.omm.preview;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Vanilla geometry, original-model visibility, automatic arm width and persistent URL reuse. */
public final class PreviewVanillaTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-vanilla");
        Files.createDirectories(folder);
        for (String name : List.of("main.bbmodel", "blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            models.main:setVisible(false)
            local page = action_wheel:newPage('原版玩家')
            action_wheel:setPage(page)
            page:newAction(1):title('隐藏玩家'):onLeftClick(function() vanilla_model.PLAYER:setVisible(false) end)
            page:newAction(2):title('显示玩家'):onLeftClick(function() vanilla_model.PLAYER:setVisible(true) end)
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-vanilla.json");
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.window.title = "omm-core vanilla player visibility";
        config.vanilla.skinCacheDirectory = root.resolve("skin-cache-" + java.util.UUID.randomUUID()).toString();
        config.vanilla.enabled = false;
        var base = PreviewMesh.load(config);
        config.vanilla.enabled = true;
        var mesh = PreviewMesh.load(config);
        int offset = base.skeleton.names.length;
        if (mesh.indices.length != base.indices.length + 432 || mesh.skeleton.names.length != offset + 12) {
            throw new AssertionError("Twelve actual skinned vanilla parts were not appended");
        }
        var shared = new PreviewAnimation(mesh, config);
        var state = shared.newState();
        sample(state);
        if (!state.lua().vanillaVisible() || state.palette[offset].m00() != 1)
            throw new AssertionError("Vanilla player was not visible by default");
        state.lua().evaluate("vanilla_model.HEAD:setVisible(false)");
        sample(state);
        if (state.palette[offset].m00() != 0 || state.palette[offset + 1].m00() != 1)
            throw new AssertionError("Per-part visibility did not reach palette");
        state.lua().evaluate(
            "vanilla_model.INNER_LAYER:setVisible(false); vanilla_model.OUTER_LAYER:setVisible(false)");
        sample(state);
        for (int index = offset; index < state.palette.length; index++) {
            if (state.palette[index].m00() != 0)
                throw new AssertionError("Hidden vanilla geometry remains visible");
        }
        if (state.lua().vanillaVisible())
            throw new AssertionError("Fully hidden skin would still be submitted");
        var isolated = shared.newState();
        sample(isolated);
        if (!isolated.lua().vanillaVisible())
            throw new AssertionError("Vanilla visibility leaked across instances");
        BufferedImage steve;
        try (var input = PreviewVanillaTest.class.getResourceAsStream(
                 "/assets/minecraft/textures/entity/player/wide/steve.png")) {
            steve = ImageIO.read(input);
        }
        BufferedImage alex;
        try (var input = PreviewVanillaTest.class.getResourceAsStream(
                 "/assets/minecraft/textures/entity/player/slim/alex.png")) {
            alex = ImageIO.read(input);
        }
        if (PreviewVanillaModel.isSlim(steve) || !PreviewVanillaModel.isSlim(alex))
            throw new AssertionError("Bundled Steve/Alex pixel detection differs");
        byte[] png;
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(alex, "PNG", output);
            png = output.toByteArray();
        }
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/skin", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, png.length);
            try (var output = exchange.getResponseBody()) {
                output.write(png);
            }
        });
        server.start();
        try {
            config.vanilla.skinUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/skin";
            var first = PreviewMesh.load(config);
            if (!first.vanilla.description().contains("vanilla_model=alex"))
                throw new AssertionError("URL skin did not select Alex automatically");
            PreviewMesh.load(config);
            config.vanilla.skinUrl = "";
            PreviewMesh.load(config);
            if (requests.get() != 1)
                throw new AssertionError("Cached skin was requested again");
        } finally {
            server.stop(0);
        }
        // With the HTTP server stopped, omitted URL must still load the persisted last skin.
        mesh = PreviewMesh.load(config);
        if (!mesh.vanilla.description().contains("(cached)"))
            throw new AssertionError("Last skin was not read from disk");
        config.vanilla.model = "steve";
        if (!PreviewMesh.load(config).vanilla.description().contains("vanilla_model=steve"))
            throw new AssertionError("Explicit model override was ignored");
        config.vanilla.model = "auto";
        config.interaction.actions = List.of(click(30, 1), click(60, 2));
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        config.scene.models = List.of(root.resolve("lua-animated-ysm").toString());
        config.animation.mode = "clip";
        config.animation.clip = "script_pose";
        var ysm = new PreviewAnimation(PreviewMesh.load(config), config).newState();
        sample(ysm);
        ysm.lua().evaluate("vanilla_model.ALL:setVisible(false)");
        sample(ysm);
        if (ysm.lua().vanillaVisible())
            throw new AssertionError("YSM Lua did not hide the actual vanilla player");
        System.out.println("Vanilla PASS: actual skin geometry, default/part/group visibility, YSM/Figura, pixel model "
            + "selection and one-request persistent cache");
    }

    private static void sample(PreviewAnimation.State state) {
        state.sample(0, 0);
        state.updateBones();
    }
    private static PreviewInteraction.Operation click(long frame, int slot) {
        var action = new PreviewInteraction.Operation();
        action.frame = frame;
        action.slot = slot;
        action.type = "wheel-click";
        return action;
    }
}
