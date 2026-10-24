package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.avatar.local.LocalAvatarSource;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits;
import top.aihmc.omnimorph.backend2.library.OutfitPrivateUpload;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Worker-only local dependency preparation. Does not depend on the currently enabled outfit snapshot. */
public final class OutfitPrivatePreparation {
    public record Prepared(OutfitPrivateUpload upload, CompoundTag nbt) {
        public Prepared {
            nbt = nbt.copy();
        }

        @Override
        public CompoundTag nbt() {
            return nbt.copy();
        }
    }

    private OutfitPrivatePreparation() {
    }

    public static Prepared prepare(UUID operation, OutfitConfig.ResourceRef reference, OutfitConfig.ResourceKind kind,
                                   UUID account, Path directory, OutfitLimits limits, long uploadLimit,
                                   BooleanSupplier cancelled) throws Exception {
        YsmResourceLimits.checkCancelled(cancelled);
        if (reference.source() != OutfitConfig.Source.LOCAL || kind == OutfitConfig.ResourceKind.OUTFIT) {
            throw new IOException("INVALID_OUTFIT_PRIVATE_SOURCE");
        }
        CompoundTag data;
        if (kind == OutfitConfig.ResourceKind.AVATAR) {
            data = new LocalAvatarSource(directory).prepare(reference, account, cancelled);
        } else {
            try (var inventory = new LocalCosmeticInventory(directory)) {
                var entry = inventory.scan(cancelled).stream()
                        .filter(candidate -> candidate.localId().equals(reference.localId()))
                        .findFirst().orElseThrow(() -> new IOException("OUTFIT_PRIVATE_SOURCE_MISSING"));
                if (entry.source() == null || !reference.equals(entry.reference())) {
                    throw new IOException("OUTFIT_PRIVATE_SOURCE_CHANGED");
                }
                entry.source().metadata().validateLimits(limits);
                data = LocalCosmeticSource.prepare(entry.path(), account, reference.contentHash(), cancelled).nbt();
            }
        }
        var distribution = LibraryPackagePreparation.fromNative(data, reference.format(), uploadLimit, cancelled);
        if (!kind.name().equals(distribution.resourceKind())) {
            throw new IOException("OUTFIT_PRIVATE_SOURCE_KIND_MISMATCH");
        }
        CosmeticMetadata metadata = kind == OutfitConfig.ResourceKind.ACCESSORY
                ? CosmeticMetadata.read(distribution.accessoryMetadata().toString()) : null;
        if (metadata != null) {
            metadata.validateLimits(limits);
        }
        var identity = LibraryUploadIdentity.prepare(distribution, cancelled);
        YsmResourceLimits.checkCancelled(cancelled);
        var upload = new OutfitPrivateUpload(operation, reference, kind, metadata, distribution.compressed(),
                identity.contentHash(), identity.schemaHash());
        return new Prepared(upload, distribution.nbt());
    }
}
