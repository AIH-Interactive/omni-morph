package top.aihmc.omnimorph.wizards;

import com.google.gson.JsonParser;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.jar.JarFile;

import org.figuramc.figura.wizards.WizardEntry;
public final class YsmAvatarCreationSmokeTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("omnimorph-ysm-create");
        try {
            testWizard(root.resolve("wizard"));
            Path first = YsmAvatarCreator.create(root, "测试模型", "新作者, Another", "描述");
            Path staged = YsmAvatarCreator.create(root, "验证后可见", "", "", path -> {
                require(!Files.exists(root.resolve("验证后可见")), "Incomplete package stays outside inventory");
                return YsmAvatarCreationSmokeTest.class.getResourceAsStream(path);
            });
            require(Files.isRegularFile(staged.resolve("ysm.json")), "Validated package appears under final name");
            Path second;
            try (JarFile jar = new JarFile(args[0])) {
                second = YsmAvatarCreator.create(root, "测试模型", "", "", path -> {
                    var entry = jar.getJarEntry(path.substring(1));
                    return entry == null ? null : jar.getInputStream(entry);
                });
            }
            require(second.getFileName().toString().equals("测试模型_1"), "Unique collision suffix");
            try (YsmModelData model = YsmModelData.read(first, () -> false)) {
                require(model.manifest().name().equals("测试模型"), "Actual directory loader metadata");
                require(model.manifest().description().equals("描述"), "Description");
                require(model.manifest().authors().length == 8, "Six template credits plus two authors");
            }
            var manifest = JsonParser.parseString(resource("wizard/ysm-template-manifest.json"))
                    .getAsJsonObject().getAsJsonArray("files");
            require(manifest.size() == 42, "Frozen complete manifest");
            try (var files = Files.walk(first)) {
                require(files.filter(Files::isRegularFile).count() == 42, "All bundled assets copied");
            }
            for (var element : manifest) {
                var entry = element.getAsJsonObject();
                String path = entry.get("path").getAsString();
                if (!path.equals("ysm.json")) {
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(first.resolve(path))));
                    require(hash.equals(entry.get("sha256").getAsString()), "Unmodified asset: " + path);
                }
            }
            var original = JsonParser.parseString(resource("ysm_default/ysm.json")).getAsJsonObject();
            var created = JsonParser.parseString(Files.readString(first.resolve("ysm.json"))).getAsJsonObject();
            require(created.get("properties").equals(original.get("properties")), "Properties preserved");
            require(created.get("files").equals(original.get("files")), "Resource bindings preserved");
            require(created.getAsJsonObject("metadata").get("license")
                    .equals(original.getAsJsonObject("metadata").get("license")), "License preserved");

            expectFailure(root, "missing", path -> path.endsWith("textures/default.png")
                    ? null : YsmAvatarCreationSmokeTest.class.getResourceAsStream(path));
            expectFailure(root, "corrupt", path -> path.endsWith("textures/default.png")
                    ? new ByteArrayInputStream(new byte[] {1, 2})
                    : YsmAvatarCreationSmokeTest.class.getResourceAsStream(path));
            String escaped = "{\"files\":[{\"path\":\"../escape\",\"bytes\":0,\"sha256\":\"\"}]}";
            expectFailure(root, "escape", path -> new ByteArrayInputStream(escaped.getBytes(StandardCharsets.UTF_8)));
            require(!Files.exists(root.resolve("escape")), "No escaped write");
            require(Files.isRegularFile(first.resolve("ysm.json")), "Existing avatar retained after failures");
            require(YsmAvatarCreator.directoryName("NUL.txt").equals("_NUL.txt"), "Windows reserved name");
            require(YsmAvatarCreator.directoryName("模型/名称. ").equals("模型_名称"), "Unicode filename sanitation");
            System.out.println("PASS YSM creation: all 42 bundled assets from built JAR, real package parse, original credits/license, "
                    + "unique Unicode directories and failed-copy cleanup; GUI MANUAL/PENDING");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void expectFailure(Path root, String name, YsmAvatarCreator.Resources resources) throws Exception {
        try {
            YsmAvatarCreator.create(root, name, "", "", resources);
            throw new AssertionError("Expected creation failure: " + name);
        } catch (IOException expected) {
            require(!Files.exists(root.resolve(name)), "Incomplete folder cleaned: " + name);
        }
    }

    private static void testWizard(Path root) throws Exception {
        var wizard = new YsmAvatarWizard(root);
        require(!wizard.canBuild(), "Empty form cannot create");
        require(wizard.entries().equals(java.util.List.of(WizardEntry.Meta, WizardEntry.NAME,
                WizardEntry.DESCRIPTION, WizardEntry.AUTHORS)), "YSM exposes only metadata fields");
        wizard.changeEntry(WizardEntry.NAME, "异步/形象");
        wizard.changeEntry(WizardEntry.AUTHORS, "作者");
        wizard.changeEntry(WizardEntry.DESCRIPTION, "创建描述");
        require(wizard.canBuild(), "Valid form can create");
        require(wizard.targetDirectory().equals(root.resolve("异步_形象")), "Sanitized target displayed");
        require(!Files.exists(root), "Editing/cancelling a form creates no directory");

        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var occupied = top.aihmc.omnimorph.avatar.AvatarLoadExecutor.submit(() -> {
            entered.countDown();
            try {
                if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("Worker release timed out");
                }
            } catch (InterruptedException interrupted) {
                throw new AssertionError(interrupted);
            }
            return null;
        });
        require(entered.await(10, java.util.concurrent.TimeUnit.SECONDS), "Model worker occupied");
        var creating = wizard.buildAsync();
        wizard.changeEntry(WizardEntry.NAME, "随后修改");
        try {
            require(!creating.isDone() && !Files.exists(root), "Creation is queued off the UI thread");
        } finally {
            release.countDown();
        }
        occupied.get(10, java.util.concurrent.TimeUnit.SECONDS);
        creating.get(20, java.util.concurrent.TimeUnit.SECONDS);
        try (var model = YsmModelData.read(root.resolve("异步_形象"), () -> false)) {
            require(model.manifest().name().equals("异步/形象"), "Accepted job captures metadata snapshot");
        }
        require(!Files.exists(root.resolve("随后修改")), "Later UI edits cannot redirect active creation");

        Path invalidRoot = root.resolve("unwritable");
        Files.writeString(invalidRoot, "existing file");
        var failedWizard = new YsmAvatarWizard(invalidRoot);
        failedWizard.changeEntry(WizardEntry.NAME, "保留输入");
        try {
            failedWizard.buildAsync().get(20, java.util.concurrent.TimeUnit.SECONDS);
            throw new AssertionError("Expected IO failure");
        } catch (java.util.concurrent.ExecutionException expected) {
            require(failedWizard.getEntry(WizardEntry.NAME, "").equals("保留输入"), "Failure retains form state");
            require(Files.readString(invalidRoot).equals("existing file"), "Failure preserves existing path");
        }
    }

    private static String resource(String path) throws IOException {
        try (var stream = YsmAvatarCreationSmokeTest.class
                .getResourceAsStream("/assets/omni-morph/" + path)) {
            if (stream == null) {
                throw new IOException("Missing packaged resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
