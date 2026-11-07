package top.aihmc.omm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipFile;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.VersionOverrides;

/** Supplies only the local path and original metadata queried by the MOD compiler. */
final class FabricRuntimeInputs {
    private FabricRuntimeInputs() {
    }

    static void configure(Path configDirectory, Path modJar) throws Exception {
        Files.createDirectories(configDirectory);
        var gameDirectory = FabricLoaderImpl.class.getDeclaredMethod("setGameDir", Path.class);
        gameDirectory.setAccessible(true);
        gameDirectory.invoke(FabricLoaderImpl.INSTANCE, configDirectory.getParent());
        var config = FabricLoaderImpl.class.getDeclaredField("configDir");
        config.setAccessible(true);
        config.set(FabricLoaderImpl.INSTANCE, configDirectory);
        LoaderModMetadata metadata;
        try (var zip = new ZipFile(modJar.toFile());
             var input = zip.getInputStream(zip.getEntry("fabric.mod.json"))) {
            metadata = ModMetadataParser.parseMetadata(input, modJar.toString(), List.of(),
                new VersionOverrides(), new DependencyOverrides(configDirectory), false);
        }
        var create = ModCandidateImpl.class.getDeclaredMethod("createPlain",
            List.class, LoaderModMetadata.class, boolean.class, Collection.class);
        create.setAccessible(true);
        Object candidate = create.invoke(null, List.of(modJar), metadata, false, List.of());
        var add = FabricLoaderImpl.class.getDeclaredMethod("addMod", ModCandidateImpl.class);
        add.setAccessible(true);
        add.invoke(FabricLoaderImpl.INSTANCE, candidate);
        System.out.println("FABRIC METADATA " + metadata.getId() + " version=" + metadata.getVersion());
    }
}
