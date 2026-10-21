package top.aihmc.omnimorph.model.outfit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.permissions.PermissionPack;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

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
        configuration.validateLimits(limits);
        var avatars = new LinkedHashMap<UUID, Avatar>();
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
                        limits, CosmeticInstanceContext.Scope.DRAFT);
                Avatar avatar = Avatar.createCosmetic(context, wearerPermissions, "CosmeticPreview");
                avatars.put(declaration.instanceId(), avatar);
                avatar.load(data.copy(), null, cancelled);
                requireCurrent(cancelled);
                if (!avatar.loaded || avatar.nbt == null || avatar.isYsmNative()
                        != (declaration.resource().format() != OutfitConfig.Format.FIGURA)) {
                    throw new IOException("COSMETIC_PREPARATION_FAILED");
                }
            }
            requireCurrent(cancelled);
            var composition = new OutfitComposition(configuration, limits, wearer, avatars, indexes);
            transferred = true;
            if (cancelled.getAsBoolean()) {
                composition.close();
                throw new CancellationException("COSMETIC_PREPARATION_RETIRED");
            }
            return composition;
        } finally {
            if (!transferred) {
                for (Avatar avatar : avatars.values()) {
                    avatar.clean();
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
