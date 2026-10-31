package org.figuramc.figura.utils;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.FiguraMod;

/**
 * Factory for resource locations in the mod namespace.
 * <p>
 * MC 1.21.1 declares {@link ResourceLocation} as a final class, so this helper
 * can no longer subclass it.
 */
public final class FiguraIdentifier {
    private FiguraIdentifier() {
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(FiguraMod.MOD_ID, path);
    }

    public static String formatPath(String path) {
        return Util.sanitizeName(path, ResourceLocation::validPathChar);
    }
}
