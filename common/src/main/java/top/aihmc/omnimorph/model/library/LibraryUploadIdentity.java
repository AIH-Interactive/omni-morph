package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Worker-only YSM inventory preparation. No ticking, Lua startup, GUI rendering or equipment registration. */
public record LibraryUploadIdentity(String contentHash, String schemaHash) {
    public static LibraryUploadIdentity prepare(LibraryPackagePreparation.Prepared source,
                                                BooleanSupplier cancelled) throws Exception {
        if (source.metadata().modelType().equals("FIGURA")) {
            return new LibraryUploadIdentity("", "");
        }
        Avatar owner = Avatar.createLibraryPreview(UUID.randomUUID(), "LibraryUpload");
        YsmModelRuntime runtime = null;
        try {
            var payload = source.nbt().getCompoundOrEmpty("ysm");
            runtime = owner.createYsmPreviewRuntime(preview -> {
                try {
                    return YsmModelRuntime.fromNbt(preview, payload, cancelled);
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            });
            var inventory = new YsmSyncModel(runtime);
            return new LibraryUploadIdentity(inventory.contentHash(), inventory.schemaHash());
        } finally {
            YsmModelRuntime retired = runtime;
            Minecraft.getInstance().execute(() -> {
                if (retired != null) {
                    retired.owner().clean();
                }
                owner.clean();
            });
        }
    }
}
