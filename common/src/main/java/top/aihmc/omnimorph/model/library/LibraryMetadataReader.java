package top.aihmc.omnimorph.model.library;

import net.minecraft.ChatFormatting;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;
import top.aihmc.omnimorph.avatar.ysm.YsmManifest;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import top.aihmc.omnimorph.avatar.ysm.YsmSourceIdentity;
import org.figuramc.figura.parsers.AvatarMetadataParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Source metadata preparation only. Call from a bounded worker, never the render thread. */
public final class LibraryMetadataReader {
    private LibraryMetadataReader() {
    }

    public record Metadata(String modelType, String internalName, List<String> authors,
                           String description, boolean requiresAuthorizationDefault) {
        public Metadata {
            // Library catalog fields are plain text. Keep source/runtime formatting
            // out of the catalog without changing the original model package.
            internalName = plainText(internalName);
            description = plainText(description);
            authors = List.copyOf(LibraryMetadataReader.authors(authors));
        }
    }

    public static Metadata read(Path path, BooleanSupplier cancelled) throws IOException {
        String filename = path.getFileName().toString();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".ysm")) {
            throw new IOException("UNSUPPORTED_FORMAT");
        }
        YsmResourceLimits.checkCancelled(cancelled);
        if (Files.size(path) > YsmResourceLimits.DEFAULT.fileBytes()) {
            throw new IOException("RESOURCE_LIMIT");
        }
        if (YsmEncryptedSource.hasMagic(path)) {
            try (var data = YsmModelData.read(path, cancelled)) {
                return fromYsm(data.manifest(), true);
            }
        }
        try (var source = YsmPackage.open(path)) {
            if (source.exists("ysm.json") || (source.exists("main.json")
                    && source.readString("main.json").contains("minecraft:geometry"))) {
                try (var data = YsmModelData.read(source, YsmSourceIdentity.ContainerKind.ZIP, cancelled)) {
                    return fromYsm(data.manifest(), false);
                }
            }
            String root = figuraRoot(source, cancelled);
            String fallback = filename.substring(0, filename.lastIndexOf('.'));
            var metadata = AvatarMetadataParser.read(source.readString(root + "avatar.json"));
            YsmResourceLimits.checkCancelled(cancelled);
            return fromFigura(metadata, fallback);
        } catch (RuntimeException malformed) {
            throw new IOException("INVALID_METADATA", malformed);
        }
    }

    /** An explicit root manifest wins; otherwise require one unambiguous nested Figura model. */
    static String figuraRoot(YsmPackage source, BooleanSupplier cancelled) throws IOException {
        if (source.exists("avatar.json")) {
            return "";
        }
        String root = null;
        for (String name : source.resourcePaths()) {
            YsmResourceLimits.checkCancelled(cancelled);
            if (!name.toLowerCase(Locale.ROOT).endsWith("/avatar.json")) {
                continue;
            }
            if (root != null) {
                throw new IOException("AMBIGUOUS_FIGURA_SOURCE");
            }
            root = name.substring(0, name.length() - "avatar.json".length());
        }
        if (root == null) {
            throw new IOException("UNRECOGNIZED_SOURCE");
        }
        return root;
    }

    public static Metadata fromFigura(AvatarMetadataParser.Metadata metadata, String fallback) {
        List<String> authors = metadata.authors == null
                ? Arrays.asList(metadata.author) : Arrays.asList(metadata.authors);
        return new Metadata("FIGURA", name(metadata.name, fallback), authors,
                metadata.description == null ? "" : metadata.description, !Boolean.TRUE.equals(metadata.free));
    }

    public static Metadata fromYsm(YsmManifest manifest, boolean encrypted) {
        return new Metadata(encrypted ? "YSM_ENCRYPTED" : "YSM_PLAIN", manifest.name(),
                Arrays.asList(manifest.authors()), manifest.description(), !manifest.free());
    }

    private static String name(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String plainText(String value) {
        return value == null ? "" : ChatFormatting.stripFormatting(value).replace("§", "");
    }

    private static List<String> authors(List<String> values) {
        var result = new ArrayList<String>();
        for (String author : values) {
            String plain = plainText(author).strip();
            if (!plain.isEmpty() && !result.contains(plain)) {
                result.add(plain);
            }
        }
        return result;
    }
}
