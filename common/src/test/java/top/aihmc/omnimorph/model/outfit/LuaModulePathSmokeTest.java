package top.aihmc.omnimorph.model.outfit;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A real ZIP script index must not inherit the host's filename restrictions. */
public final class LuaModulePathSmokeTest {
    public static void main(String[] args) throws Exception {
        Path archive = Files.createTempFile("figura-module-path-", ".zip");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                entry(zip, "avatar.json", "{\"name\":\"virtual modules\",\"autoScripts\":[\"main\"]}");
                entry(zip, "main.lua", """
                        local m = require('feature:variant')
                        assert(m.value == 42 and module_runs == 1)
                        assert(require('/feature:variant') == m)
                        assert(require('./feature:variant') == m)
                        assert(require('nested/../feature:variant') == m)
                        assert(require('special:dir.entry') == 43)
                        local all = listFiles('', true)
                        assert(#all == 5 and #listFiles(nil, true) == 5)
                        assert(#listFiles('/', false) == 2)
                        assert(#listFiles('nested', false) == 1)
                        assert(#listFiles('nested', true) == 2)
                        assert(#listFiles('feature:variant') == 1)
                        local ok, err = pcall(require, 'missing')
                        assert(not ok and tostring(err):find('nonexistent script'))
                        assert(not pcall(require, 'Feature:variant'))
                        assert(not pcall(require, 'C:/outside'))
                        """);
                entry(zip, "feature:variant.lua", "module_runs = (module_runs or 0) + 1; return {value=42}");
                entry(zip, "special:dir/entry.lua", """
                        local directory, filename = ...
                        assert(directory == 'special:dir' and filename == 'entry')
                        assert(#listFiles('.', false) == 1)
                        assert(require('../feature:variant').value == 42)
                        assert(require('../nested.module') == 43)
                        return 43
                        """);
                entry(zip, "nested/module.lua", "return 43");
                entry(zip, "nested/deep/other.lua", "return 44");
            }
            try (var fs = FileSystems.newFileSystem(archive)) {
                YsmMaterialNativeSmokeTest.replayFiguraModel(Path.of(args[0]), ignored -> {
                    for (int instance = 0; instance < 2; instance++) {
                        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "ZIP module fixture");
                        try {
                            var nbt = LocalAvatarLoader.compileFiguraAvatar(fs.getPath("/avatar"), UUID.randomUUID());
                            require(nbt.getCompoundOrEmpty("scripts").contains("feature:variant"),
                                    "formal ZIP compiler preserves the supported module name");
                            avatar.load(nbt);
                            avatar.initializePreviewPlayer(null);
                            require(avatar.luaRuntime != null && !avatar.scriptError,
                                    "formal ZIP module can be required: " + avatar.errorText);
                        } catch (Exception e) {
                            throw new AssertionError("formal ZIP module loading must accept virtual names", e);
                        } finally {
                            avatar.clean();
                        }
                    }
                });
            }
        } finally {
            Files.delete(archive);
        }
        System.out.println("PASS formal ZIP module loading and require");
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry("avatar/" + name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
