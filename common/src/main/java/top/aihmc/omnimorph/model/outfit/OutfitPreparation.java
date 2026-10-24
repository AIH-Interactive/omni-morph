package top.aihmc.omnimorph.model.outfit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.permissions.PermissionPack;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Worker preparation only. The caller publishes the returned generation on the client thread. */
public final class OutfitPreparation {
    @FunctionalInterface
    public interface Resources {
        /** Existing source/cache readers verify the exact reference and preserve encrypted input bytes. */
        CompoundTag read(OutfitConfig.ResourceRef reference, BooleanSupplier cancelled) throws Exception;
    }

    private OutfitPreparation() {}

    public static OutfitComposition prepare(OutfitConfig configuration, OutfitLimits limits,
                                             UUID account, UUID outfit, LivingEntity wearer,
                                             PermissionPack.PlayerPermissionPack wearerPermissions, SkeletonIndex.Cache indexes,
                                             Resources resources, BooleanSupplier cancelled) throws Exception {
        return prepare(configuration, limits, account, outfit, wearer, wearerPermissions, indexes,
                resources, cancelled, CosmeticInstanceContext.Scope.DRAFT, null,
                resource -> resource.source() == OutfitConfig.Source.LOCAL,
                resource -> resource.source() == OutfitConfig.Source.LOCAL);
    }

    /** Formal callers retain the base and supply fresh resource policies; this method never publishes either. */
    public static OutfitComposition prepare(OutfitConfig configuration, OutfitLimits limits,
                                             UUID account, UUID outfit, LivingEntity wearer,
                                             PermissionPack.PlayerPermissionPack wearerPermissions, SkeletonIndex.Cache indexes,
                                             Resources resources, BooleanSupplier cancelled,
                                             CosmeticInstanceContext.Scope scope, Avatar preparedBase,
                                             Predicate<OutfitConfig.ResourceRef> mayUseExportedBones,
                                             Predicate<OutfitConfig.ResourceRef> mayEditBones) throws Exception {
        java.util.Objects.requireNonNull(scope);
        java.util.Objects.requireNonNull(mayUseExportedBones);
        java.util.Objects.requireNonNull(mayEditBones);
        if (scope == CosmeticInstanceContext.Scope.OWNER && !account.equals(wearer.getUUID())) {
            throw new IllegalArgumentException("OUTFIT_OWNER_MISMATCH");
        }
        configuration.validateLimits(limits);
        var avatars = new LinkedHashMap<UUID, Avatar>();
        OutfitComposition composition = null;
        boolean transferred = false;
        try {
            for (var declaration : configuration.instances()) {
                requireCurrent(cancelled);
                CompoundTag data = resources.read(declaration.resource(), cancelled);
                requireCurrent(cancelled);
                if (data == null || !"ACCESSORY".equals(
                        data.getCompoundOrEmpty("metadata").getStringOr("resource_kind", ""))) {
                    throw new IOException("COSMETIC_RESOURCE_KIND_REQUIRED");
                }
                var context = new CosmeticInstanceContext(account, outfit, wearer.getUUID(), declaration,
                        limits, scope);
                Avatar avatar = Avatar.createCosmetic(context, wearerPermissions, "CosmeticPreview");
                avatars.put(declaration.instanceId(), avatar);
                avatar.load(data.copy(), null, cancelled);
                requireCurrent(cancelled);
                if (!avatar.loaded || avatar.nbt == null || avatar.isYsmNative()
                        != (declaration.resource().format() != OutfitConfig.Format.FIGURA)) {
                    throw new IOException("COSMETIC_PREPARATION_FAILED");
                }
                if (scope != CosmeticInstanceContext.Scope.DRAFT
                        && YsmSourcePayload.isEncrypted(avatar.nbt.getCompoundOrEmpty("ysm"))
                        != (declaration.resource().format() == OutfitConfig.Format.YSM_ENCRYPTED)) {
                    throw new IOException("COSMETIC_PREPARATION_FAILED");
                }
            }
            requireCurrent(cancelled);
            composition = new OutfitComposition(configuration, limits, wearer, avatars, indexes,
                    mayUseExportedBones, mayEditBones);
            if (scope != CosmeticInstanceContext.Scope.DRAFT) {
                composition.validatePreparedBase(preparedBase);
            }
            transferred = true;
            if (cancelled.getAsBoolean()) {
                composition.close();
                throw new CancellationException("COSMETIC_PREPARATION_RETIRED");
            }
            return composition;
        } finally {
            if (!transferred) {
                if (composition != null) {
                    composition.close();
                } else {
                    for (Avatar avatar : avatars.values()) {
                        avatar.clean();
                    }
                }
            }
        }
    }

    private static void requireCurrent(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("COSMETIC_PREPARATION_RETIRED");
        }
    }
}
