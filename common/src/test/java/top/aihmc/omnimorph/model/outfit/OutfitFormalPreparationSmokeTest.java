package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.decoration.ArmorStand;
import sun.misc.Unsafe;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Native assembly and resident publication; no Apply request, world rendering, GUI or GPU evidence. */
public final class OutfitFormalPreparationSmokeTest {
    public static void run(CompoundTag figura, CompoundTag encrypted, Runnable drain) throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (Unsafe) field.get(null);
        var wearer = (ArmorStand) unsafe.allocateInstance(TestWearer.class);
        wearer.setUUID(UUID.randomUUID());
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.getUUID().toString());
        for (var input : List.of(figura, plainFixture(encrypted), encrypted)) {
            var format = !input.contains("ysm") ? OutfitConfig.Format.FIGURA
                    : top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.isEncrypted(input.getCompoundOrEmpty("ysm"))
                    ? OutfitConfig.Format.YSM_ENCRYPTED : OutfitConfig.Format.YSM_PLAIN;
            var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, "https://example.invalid",
                    UUID.randomUUID(), 1L, "a".repeat(64), format);
            var probeInstance = instance(resource, 1, false, null);
            var context = new CosmeticInstanceContext(wearer.getUUID(), UUID.randomUUID(), wearer.getUUID(),
                    probeInstance, OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
            Avatar probe = Avatar.createCosmetic(context, permissions, "formal-probe");
            SkeletonIndex definition;
            try {
                probe.load(input.copy());
                definition = probe.isYsmNative()
                        ? new YsmSkeletonAdapter(probe.getYsmRuntime(), resource, probeInstance.instanceId(),
                                new SkeletonIndex.Cache(), OutfitLimits.defaults()).binding().definition()
                        : new FiguraSkeletonAdapter((ImmediateFiguraRenderer) probe.renderer, resource,
                                probeInstance.instanceId(), new SkeletonIndex.Cache(), OutfitLimits.defaults()).binding().definition();
            } finally {
                probe.clean();
            }
            var bone = definition.reference(null, definition.roots().getFirst());
            var original = CosmeticMetadata.read(input.getCompoundOrEmpty("metadata").getStringOr("cosmetic_json", ""));
            var metadata = new CosmeticMetadata(original.common(), original.category(), original.cosmeticAnchor(),
                    true, false, true, "formal-test", definition.hash(), List.of("VANILLA", "FIGURA", "YSM"),
                    "CLIENT_EXTRACTED", List.of(bone), List.of(bone), OutfitConfig.Transform.identity());
            CompoundTag data = input.copy();
            data.getCompoundOrEmpty("metadata").putString("cosmetic_json", OutfitCodec.encodeAccessoryMetadata(metadata).toString());
            var scripts = new CompoundTag();
            scripts.putByteArray("must_wait", "error('instance scripts must wait for activation')".getBytes(StandardCharsets.UTF_8));
            data.put("scripts", scripts);
            var first = instance(resource, 1, true, bone);
            var second = instance(resource, 2, false, null);
            var configuration = new OutfitConfig(1, 0, null, List.of(first, second), List.of(),
                    Map.of(resource.identity(), 2), false);
            var withoutOverrides = new OutfitConfig(1, 0, null, List.of(instance(resource, 1, false, null), second),
                    List.of(), Map.of(resource.identity(), 2), false);
            Predicate<OutfitConfig.ResourceRef> authorized = resource::equals;
            for (var scope : List.of(CosmeticInstanceContext.Scope.OWNER, CosmeticInstanceContext.Scope.OBSERVER)) {
                UUID account = scope == CosmeticInstanceContext.Scope.OWNER ? wearer.getUUID() : UUID.randomUUID();
                try (var composition = OutfitPreparation.prepare(configuration, OutfitLimits.defaults(), account,
                        UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(),
                        () -> false, scope, null, authorized, authorized)) {
                    var snapshots = composition.editorSnapshot(null).instances();
                    require(snapshots.size() == 2 && snapshots.stream().allMatch(value -> value.mayEdit()
                                    && value.exported().size() == 1),
                            "fresh explicit cloud policy permits author-defined exports and internal edits");
                    var avatars = instances(composition);
                    require(avatars.size() == 2 && avatars.values().stream().allMatch(avatar -> avatar.loaded
                                    && avatar.luaRuntime == null && !avatar.scriptError && !avatar.isHost
                                    && avatar.cosmeticContext.scope() == scope),
                            "owner/observer native assembly retains its scope without executing instance Lua");
                    var owners = avatars.values().stream().map(avatar -> avatar.owner).distinct().toList();
                    require(owners.size() == 2 && !owners.contains(wearer.getUUID()), "duplicate execution owners remain private");
                }
            }
            reject(() -> OutfitPreparation.prepare(configuration, OutfitLimits.defaults(), wearer.getUUID(),
                    UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(),
                    () -> false, CosmeticInstanceContext.Scope.OWNER, null, authorized, ref -> false));
            var limitedJson = new Gson().toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
            limitedJson.addProperty("maxTotalNodes", definition.nodes().size() * 2);
            var limited = new Gson().fromJson(limitedJson, OutfitLimits.class);
            reject(() -> OutfitPreparation.prepare(configuration, limited, wearer.getUUID(), UUID.randomUUID(), wearer,
                    permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(), () -> false,
                    CosmeticInstanceContext.Scope.OWNER, null, authorized, authorized));
            for (String invalid : List.of("hash", "path", "base-family", "missing-metadata")) {
                CompoundTag changed = data.copy();
                var declaration = OutfitCodec.encodeAccessoryMetadata(metadata);
                switch (invalid) {
                    case "hash" -> {
                        declaration.addProperty("skeleton_hash", "b".repeat(64));
                        for (String range : List.of("exported_bones", "editable_bones")) {
                            declaration.getAsJsonArray(range).get(0).getAsJsonObject().addProperty("skeleton_hash", "b".repeat(64));
                        }
                    }
                    case "path" -> {
                        var extra = new com.google.gson.JsonObject();
                        extra.addProperty("name", "absent");
                        extra.addProperty("sibling_ordinal", 0);
                        declaration.getAsJsonArray("exported_bones").get(0).getAsJsonObject().getAsJsonArray("path").add(extra);
                    }
                    case "base-family" -> declaration.add("supported_bases", new Gson().toJsonTree(List.of("YSM")));
                    case "missing-metadata" -> changed.getCompoundOrEmpty("metadata").remove("cosmetic_json");
                    default -> throw new AssertionError(invalid);
                }
                if (!invalid.equals("missing-metadata")) {
                    changed.getCompoundOrEmpty("metadata").putString("cosmetic_json", declaration.toString());
                }
                reject(() -> OutfitPreparation.prepare(withoutOverrides, OutfitLimits.defaults(), wearer.getUUID(),
                        UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> changed.copy(),
                        () -> false, CosmeticInstanceContext.Scope.OWNER, null, authorized, authorized));
            }
            var reads = new AtomicInteger();
            reject(() -> OutfitPreparation.prepare(configuration, OutfitLimits.defaults(), UUID.randomUUID(),
                    UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> {
                        reads.incrementAndGet();
                        return data.copy();
                    }, () -> false, CosmeticInstanceContext.Scope.OWNER, null, authorized, authorized));
            require(reads.get() == 0, "wrong owner is rejected before loading dependencies");
            if (format != OutfitConfig.Format.FIGURA) {
                var wrong = new OutfitConfig.ResourceRef(resource.source(), null, resource.origin(), resource.resourceId(),
                        1L, resource.contentHash(), format == OutfitConfig.Format.YSM_PLAIN
                        ? OutfitConfig.Format.YSM_ENCRYPTED : OutfitConfig.Format.YSM_PLAIN);
                var wrongConfig = new OutfitConfig(1, 0, null, List.of(instance(wrong, 1, false, null)),
                        List.of(), Map.of(wrong.identity(), 1), false);
                reject(() -> OutfitPreparation.prepare(wrongConfig, OutfitLimits.defaults(), wearer.getUUID(),
                        UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(),
                        () -> false, CosmeticInstanceContext.Scope.OWNER, null, ref -> true, ref -> true));
            }
            testBorrowedBase(configuration, data, wearer, permissions, resource, authorized);
            var client = net.minecraft.client.Minecraft.getInstance();
            var previousLevel = client.level;
            var cachedWorld = org.figuramc.figura.lua.api.world.WorldAPI.class.getDeclaredField("cached");
            cachedWorld.setAccessible(true);
            var previousWorld = cachedWorld.get(null);
            try {
                client.level = (RegistryLevel) unsafe.allocateInstance(RegistryLevel.class);
                // Item components normally arrive from data packs, absent from this native CPU fixture.
                for (var item : List.of(net.minecraft.world.item.Items.COMPARATOR, net.minecraft.world.item.Items.ARMOR_STAND)) {
                    item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);
                }
                testPublication(configuration, data, wearer, permissions, resource, authorized, drain);
                UUID originalOwner = wearer.getUUID();
                try {
                    wearer.setUUID(org.figuramc.figura.FiguraMod.getLocalPlayerUUID());
                    top.aihmc.omnimorph.model.library.OutfitApplyServiceSmokeTest.run(data, wearer, drain);
                } finally {
                    wearer.setUUID(originalOwner);
                }
            } finally {
                client.level = previousLevel;
                cachedWorld.set(null, previousWorld);
            }
        }
        System.out.println("PASS formal outfit native preparation: owner/observer, explicit cloud policies, author geometry, "
                + "total base nodes, borrowed base identity and no pre-activation instance Lua; full Apply integration PENDING");
    }

    private static CompoundTag plainFixture(CompoundTag encrypted) {
        CompoundTag fixture = encrypted.copy();
        CompoundTag ysm = new CompoundTag();
        ysm.putByteArray("main_model", """
                {"minecraft:geometry":[{"description":{"identifier":"geometry.formal"},
                  "bones":[{"name":"Root"},{"name":"Tip","parent":"Root"}]}]}
                """.getBytes(StandardCharsets.UTF_8));
        ysm.putFloat("width_scale", 1);
        ysm.putFloat("height_scale", 1);
        // Match the identity field supplied by the real plaintext YSM loader.
        ysm.putString("content_sha256", top.aihmc.omnimorph.backend2.protocol.CloudHttp.sha256(
                ysm.getByteArray("main_model").orElseThrow()));
        for (String key : List.of("animations", "animation_controllers", "functions", "action_schemas")) {
            ysm.put(key, new net.minecraft.nbt.ListTag());
        }
        fixture.put("ysm", ysm);
        return fixture;
    }

    private static void testPublication(OutfitConfig configuration, CompoundTag data, ArmorStand wearer,
                                        PermissionPack.PlayerPermissionPack permissions, OutfitConfig.ResourceRef resource,
                                        Predicate<OutfitConfig.ResourceRef> authorized, Runnable drain) throws Exception {
        var user = new org.figuramc.figura.avatar.UserData(wearer.getUUID());
        CompoundTag instanceData = data.copy();
        instanceData.remove("scripts");
        CompoundTag baseData = instanceData.copy();
        baseData.getCompoundOrEmpty("metadata").putString("resource_kind", "AVATAR");
        var scripts = new CompoundTag();
        scripts.putByteArray("counter", "ticks = 0; function events.tick() ticks = ticks + 1 end"
                .getBytes(StandardCharsets.UTF_8));
        baseData.put("scripts", scripts);
        Avatar base = new Avatar(wearer.getUUID());
        base.load(baseData);
        base.bindOutfitSource(resource);
        base.initializePreviewPlayer(wearer);
        require(!base.scriptError && base.luaRuntime != null && base.accelerationOwner().isCurrent()
                        && base.isYsmNative() == (resource.format() != OutfitConfig.Format.FIGURA),
                "publication fixture initialization " + resource.format() + ": scriptError=" + base.scriptError
                        + ", lua=" + (base.luaRuntime != null) + ", native=" + base.isYsmNative()
                        + ", current=" + base.accelerationOwner().isCurrent() + ", error=" + base.errorText);
        var lua = base.luaRuntime;
        var ysm = base.getYsmRuntime();
        var withBase = new OutfitConfig(1, 0, resource, configuration.instances(), List.of(),
                configuration.displayOrdinals(), false);
        java.util.function.Supplier<OutfitComposition> prepare = () -> {
            try {
                return OutfitPreparation.prepare(withBase, OutfitLimits.defaults(), wearer.getUUID(), UUID.randomUUID(),
                        wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> instanceData.copy(), () -> false,
                        CosmeticInstanceContext.Scope.OWNER, base, authorized, authorized);
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        };
        try {
            require(await(user.publishAvatar(base, user.ysmLoads.begin(), () -> true), drain), "initial actual UserData base publication");
            var first = prepare.get();
            require(await(user.publishOutfit(revision(withBase, 1), base, first, user.ysmLoads.begin(), () -> true), drain),
                    "base and outfit enter one resident generation");
            var held = first.retain();
            Avatar retainedInstance = instances(first).values().iterator().next();
            var second = prepare.get();
            require(await(user.publishOutfit(revision(withBase, 2), base, second, user.ysmLoads.begin(), () -> true), drain),
                    "configuration-only publication succeeds with the existing base");
            require(user.getMainAvatar() == base && base.luaRuntime == lua && base.getYsmRuntime() == ysm
                            && base.accelerationOwner().isCurrent(), "same-base publication preserves Lua/YSM/control runtime identity");
            require(retainedInstance.accelerationOwner().isCurrent(), "old composition survives an in-flight native frame");
            held.close();
            require(!retainedInstance.accelerationOwner().isCurrent(), "retired composition releases after its last frame");
            user.tick();
            base.tick.reset(65536);
            require(lua.run(lua.load("one-tick", "assert(ticks == 1)"), base.tick) != null && !base.scriptError,
                    "resident outfit ticks its base once");
            var stale = prepare.get();
            Avatar staleInstance = instances(stale).values().iterator().next();
            long oldGeneration = user.ysmLoads.begin();
            user.ysmLoads.begin();
            require(!await(user.publishOutfit(revision(withBase, 3), base, stale, oldGeneration, () -> true), drain),
                    "retired native preparation cannot publish");
            require(user.getOutfit().composition() == second && base.accelerationOwner().isCurrent()
                            && !staleInstance.accelerationOwner().isCurrent(), "stale rejection only destroys candidate-owned resources");
            var mismatched = prepare.get();
            var changed = new OutfitConfig(1, 3, withBase.base(), withBase.instances(), List.of(), withBase.displayOrdinals(), true);
            require(!await(user.publishOutfit(changed, base, mismatched, user.ysmLoads.begin(), () -> true), drain)
                            && user.getOutfit().composition() == second, "acknowledged content must match the prepared composition");
            Avatar replacement = new Avatar(wearer.getUUID());
            replacement.load(baseData.copy());
            require(await(user.publishAvatar(replacement, user.ysmLoads.begin(), () -> true), drain)
                            && user.getOutfit() == null && !base.accelerationOwner().isCurrent(),
                    "old single-avatar publication retires the entire prior outfit");
            var empty = OutfitConfig.empty();
            var wrongWearer = OutfitPreparation.prepare(empty, OutfitLimits.defaults(), wearer.getUUID(), UUID.randomUUID(),
                    wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> { throw new AssertionError("no dependencies"); },
                    () -> false, CosmeticInstanceContext.Scope.OWNER, null, ref -> false, ref -> false);
            var otherUser = new org.figuramc.figura.avatar.UserData(UUID.randomUUID());
            require(!await(otherUser.publishOutfit(revision(empty, 3), null, wrongWearer,
                            otherUser.ysmLoads.begin(), () -> true), drain) && otherUser.getOutfit() == null,
                    "even vanilla compositions cannot be published under another wearer");
            var vanilla = OutfitPreparation.prepare(empty, OutfitLimits.defaults(), wearer.getUUID(), UUID.randomUUID(),
                    wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> { throw new AssertionError("no dependencies"); },
                    () -> false, CosmeticInstanceContext.Scope.OWNER, null, ref -> false, ref -> false);
            require(await(user.publishOutfit(revision(empty, 3), null, vanilla, user.ysmLoads.begin(), () -> true), drain)
                            && user.getMainAvatar() == null && user.getOutfit() != null,
                    "vanilla outfit does not require a fake empty Avatar");
            user.tick();
            user.clear();
            require(user.getOutfit() == null && user.getMainAvatar() == null
                            && !replacement.accelerationOwner().isCurrent(), "clear retires vanilla/custom outfit state and old bases");
        } finally {
            user.clear();
            base.clean();
            drain.run();
        }
        System.out.println("PASS resident outfit publication " + resource.format()
                + ": atomic pair, same-base identity, one tick, stale/content rejection, retained frames and legacy/vanilla cleanup");
    }

    private static OutfitConfig revision(OutfitConfig configuration, long revision) {
        return new OutfitConfig(1, revision, configuration.base(), configuration.instances(), configuration.virtualAnchors(),
                configuration.displayOrdinals(), configuration.showEquipment());
    }

    private static boolean await(java.util.concurrent.CompletableFuture<Boolean> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void testBorrowedBase(OutfitConfig configuration, CompoundTag data, ArmorStand wearer,
                                         PermissionPack.PlayerPermissionPack permissions, OutfitConfig.ResourceRef resource,
                                         Predicate<OutfitConfig.ResourceRef> authorized) throws Exception {
        Avatar base = Avatar.createLibraryPreview(UUID.randomUUID(), "borrowed-base");
        CompoundTag baseData = data.copy();
        baseData.getCompoundOrEmpty("metadata").putString("resource_kind", "AVATAR");
        baseData.remove("scripts");
        base.load(baseData);
        base.bindOutfitSource(resource);
        var withBase = new OutfitConfig(1, 0, resource, configuration.instances(), List.of(),
                configuration.displayOrdinals(), false);
        try {
            try (var composition = OutfitPreparation.prepare(withBase, OutfitLimits.defaults(), wearer.getUUID(),
                    UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(),
                    () -> false, CosmeticInstanceContext.Scope.OWNER, base, authorized, authorized)) {
                composition.validatePreparedBase(base);
            }
            require(base.loaded && base.nbt != null && base.outfitSource().equals(resource)
                            && base.accelerationOwner().isCurrent(), "composition retirement never destroys its borrowed base");
            base.nbt.getCompoundOrEmpty("metadata").putString("resource_kind", "ACCESSORY");
            reject(() -> OutfitPreparation.prepare(withBase, OutfitLimits.defaults(), wearer.getUUID(),
                    UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(),
                    () -> false, CosmeticInstanceContext.Scope.OWNER, base, authorized, authorized));
            base.nbt.getCompoundOrEmpty("metadata").putString("resource_kind", "AVATAR");
            var other = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, resource.origin(),
                    UUID.randomUUID(), 1L, resource.contentHash(), resource.format());
            base.bindOutfitSource(other);
            reject(() -> OutfitPreparation.prepare(withBase, OutfitLimits.defaults(), wearer.getUUID(),
                    UUID.randomUUID(), wearer, permissions, new SkeletonIndex.Cache(), (ref, cancelled) -> data.copy(),
                    () -> false, CosmeticInstanceContext.Scope.OWNER, base, authorized, authorized));
            require(base.accelerationOwner().isCurrent(), "failed formal preparation keeps the caller's base alive");
        } finally {
            base.clean();
        }
    }

    private static OutfitConfig.Instance instance(OutfitConfig.ResourceRef resource, int ordinal, boolean edit,
                                                   OutfitConfig.BoneRef bone) {
        UUID id = UUID.randomUUID();
        var overrides = edit
                ? List.of(new OutfitConfig.BoneOverride(new OutfitConfig.BoneRef(id, bone.path(), bone.skeletonHash()),
                        OutfitConfig.Transform.identity()))
                : List.<OutfitConfig.BoneOverride>of();
        return new OutfitConfig.Instance(id, resource, ordinal, ordinal == 1,
                OutfitConfig.AnchorRef.authorDefault("unknown_target"), OutfitConfig.Transform.identity(),
                overrides, Map.of(), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Avatar> instances(OutfitComposition composition) throws Exception {
        var field = OutfitComposition.class.getDeclaredField("instances");
        field.setAccessible(true);
        return (Map<UUID, Avatar>) field.get(composition);
    }

    private static void reject(Action action) throws Exception {
        try (var accepted = action.run()) {
            throw new AssertionError("invalid formal preparation accepted");
        } catch (IllegalArgumentException | java.io.IOException expected) {
            // Reject before a server commit or publication; the caller's base remains owned by the caller.
        }
    }

    /** Plain JVM fixtures do not install Minecraft Mixins. Supply the real accessor contract explicitly. */
    private static final class TestWearer extends ArmorStand implements org.figuramc.figura.mixin.EntityAccessor {
        private TestWearer() {
            super(net.minecraft.world.entity.EntityType.ARMOR_STAND, null);
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return net.minecraft.client.Minecraft.getInstance().level;
        }
    }

    private static final class RegistryLevel extends net.minecraft.client.multiplayer.ClientLevel
            implements org.figuramc.figura.mixin.ClientLevelInvoker {
        private RegistryLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public net.minecraft.core.RegistryAccess registryAccess() {
            return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                    net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS;
        }

        @Override
        @SuppressWarnings("unchecked")
        public net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity> getEntityGetter() {
            return (net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity>)
                    java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{net.minecraft.world.level.entity.LevelEntityGetter.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("get")) {
                                    return null;
                                }
                                throw new UnsupportedOperationException("No entity traversal in this fixture");
                            });
        }
    }

    @FunctionalInterface
    private interface Action {
        OutfitComposition run() throws Exception;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
