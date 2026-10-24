package org.figuramc.figura.utils.fabric;

import net.minecraft.server.packs.resources.ResourceManager;
import org.figuramc.figura.utils.FiguraResourceListener;

import java.util.function.Consumer;

/** Keeps the real reload callback available without registering a game client in parser tests. */
public final class FiguraResourceListenerImpl extends FiguraResourceListener {
    private FiguraResourceListenerImpl(String id, Consumer<ResourceManager> consumer) {
        super(id, consumer);
    }

    public static FiguraResourceListener createResourceListener(String id, Consumer<ResourceManager> consumer) {
        return new FiguraResourceListenerImpl(id, consumer);
    }
}
