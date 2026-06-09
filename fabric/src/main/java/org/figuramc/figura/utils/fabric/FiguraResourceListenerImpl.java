package org.figuramc.figura.utils.fabric;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraResourceListener;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FiguraResourceListenerImpl extends FiguraResourceListener implements PreparableReloadListener {
    public FiguraResourceListenerImpl(String id, Consumer<ResourceManager> reloadConsumer) {
        super(id, reloadConsumer);
    }

    public static FiguraResourceListener createResourceListener(String id, Consumer<ResourceManager> reloadConsumer) {
        return new FiguraResourceListenerImpl(id, reloadConsumer);
    }

    public Identifier getFabricId() {
        return new FiguraIdentifier(this.id());
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> reload(PreparableReloadListener.SharedState sharedState, java.util.concurrent.Executor prepareExecutor, PreparableReloadListener.PreparationBarrier barrier, java.util.concurrent.Executor applyExecutor) {
        return CompletableFuture.supplyAsync(() -> null, prepareExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(unused -> reloadConsumer().accept(sharedState.resourceManager()), applyExecutor);
    }
}
