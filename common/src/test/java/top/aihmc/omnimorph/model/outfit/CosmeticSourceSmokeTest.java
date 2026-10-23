package top.aihmc.omnimorph.model.outfit;

import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.animation.Animation;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.library.LibraryPreviewCache;
import top.aihmc.omnimorph.model.library.LibraryResourceCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Native preparation/data/cache checks only; this does not validate GUI or world rendering. */
public final class CosmeticSourceSmokeTest {
    public static void run(Path workspace) throws Exception {
        Path data = Files.createTempDirectory("cosmetic-source-test-");
        try {
            testSources(data, workspace);
            top.aihmc.omnimorph.wizards.CosmeticCreationSmokeTest.run(data);
            top.aihmc.omnimorph.model.library.LocalCosmeticCatalogSmokeTest.run(data);
            testIndexes();
            testCache(data.resolve("cache"));
            System.out.println("PASS cosmetic sources: native wrappers, stable IDs/index cache, .oms, isolated Lua/Molang/controls and timeout");
        } finally {
            try (var files = Files.walk(data)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static void testSources(Path data, Path workspace) throws Exception {
        Path root = Files.createDirectories(data.resolve("cosmetics"));
        Path figura = Files.createDirectories(root.resolve("星翼"));
        Files.writeString(figura.resolve("cosmetic.json"),
                "{\"name\":\"星翼\",\"authors\":[\"Original\"],\"cosmetic_anchor\":\"wing_anchor\"}");
        Files.writeString(figura.resolve("avatar.json"), "{\"name\":\"Wrong purpose\"}");
        Files.writeString(figura.resolve("script.lua"), "return 1");
        Path zip = root.resolve("配件.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("cosmetic.json"));
            output.write("{\"name\":\"Zipped\",\"cosmetic_anchor\":\"head_anchor\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Path wrapper = Files.createDirectories(root.resolve("encrypted"));
        Files.writeString(wrapper.resolve("cosmetic.json"), "{\"name\":\"Wrapped encrypted\"}");
        byte[] encrypted = Files.readAllBytes(workspace.resolve("common/src/test/resources/ysm/crypto-vectors/crypto1.ysm"));
        Files.write(wrapper.resolve("original.ysm"), encrypted);
        UUID local;
        var loadState = LocalAvatarLoader.getLoadState();
        try (var inventory = new LocalCosmeticInventory(data)) {
            List<LocalCosmeticInventory.Entry> entries = inventory.scan(() -> false);
            require(entries.size() == 3 && entries.stream().allMatch(entry -> entry.error().isEmpty()), "directory/ZIP/wrapper inventory");
            var entry = entries.stream().filter(value -> value.path().equals(figura)).findFirst().orElseThrow();
            local = entry.localId();
            var prepared = LocalCosmeticSource.prepare(figura, UUID.randomUUID(), entry.source().contentHash(), () -> false);
            require(prepared.nbt().contains("scripts"), "shared native Lua compiler");
            var metadata = prepared.nbt().getCompoundOrEmpty("metadata");
            require(metadata.getStringOr("name", "").equals("星翼")
                    && metadata.getStringOr("resource_kind", "").equals("ACCESSORY"), "purpose chooses cosmetic metadata");
            require(metadata.getStringOr("cosmetic_json", "").contains("wing_anchor"), "default anchor travels with runtime data");
            require(LocalAvatarLoader.getLoadState().equals(loadState), "detached preparation does not change wardrobe loading state");
            var cipher = entries.stream().filter(value -> value.path().equals(wrapper)).findFirst().orElseThrow();
            var wrapped = LocalCosmeticSource.prepare(wrapper, UUID.randomUUID(), cipher.source().contentHash(), () -> false);
            require(wrapped.description().format() == OutfitConfig.Format.YSM_ENCRYPTED
                    && wrapped.nbt().getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native"),
                    "encrypted wrapper keeps the original native YSM path");
            require(java.util.Arrays.equals(encrypted, Files.readAllBytes(wrapper.resolve("original.ysm"))), "encrypted source bytes unchanged");
            testInstances(wrapped.nbt(), cipher.reference());
            NativeSkeletonSmokeTest.run(wrapped.nbt());
            OutfitCompositionSmokeTest.run(wrapped.nbt());
            top.aihmc.omnimorph.model.ysm.YsmDefaultAnimationSmokeTest.run(workspace, wrapped.nbt());
            var zipped = entries.stream().filter(value -> value.path().equals(zip)).findFirst().orElseThrow();
            require(LocalCosmeticSource.prepare(zip, UUID.randomUUID(), zipped.source().contentHash(), () -> false)
                    .description().metadata().common().name.equals("Zipped"), "ZIP compiles in its validated filesystem");
            Files.writeString(figura.resolve("script.lua"), "return 2");
            reject(() -> LocalCosmeticSource.prepare(figura, UUID.randomUUID(), entry.source().contentHash(), () -> false));
            reject(() -> inventory.scan(() -> true));
        }
        try (var inventory = new LocalCosmeticInventory(data)) {
            var reloaded = inventory.scan(() -> false).stream().filter(entry -> entry.path().equals(figura)).findFirst().orElseThrow();
            require(local.equals(reloaded.localId()), "local identity survives edits and restart");
            require(!reloaded.reference().toString().contains(data.toString()), "persistent reference does not expose an absolute path");
        }
    }

    private static void testInstances(net.minecraft.nbt.CompoundTag prepared, OutfitConfig.ResourceRef resource) {
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        UUID wearer = UUID.randomUUID();
        var firstContext = context(account, outfit, wearer, resource, CosmeticInstanceContext.Scope.DRAFT);
        var secondContext = context(account, outfit, wearer, resource, CosmeticInstanceContext.Scope.DRAFT);
        var observerContext = context(account, outfit, wearer, resource, CosmeticInstanceContext.Scope.OBSERVER);
        var category = new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT);
        var permissions = new PermissionPack.PlayerPermissionPack(category, wearer.toString());
        permissions.insert(Permissions.NETWORKING, 1, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        permissions.insert(Permissions.VANILLA_MODEL_EDIT, 1, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        Avatar first = Avatar.createCosmetic(firstContext, permissions, "first");
        Avatar second = Avatar.createCosmetic(secondContext, permissions, "second");
        Avatar observer = Avatar.createCosmetic(observerContext, permissions, "observer");
        try {
            for (Avatar avatar : List.of(first, second, observer)) {
                avatar.load(prepared.copy());
                avatar.initializePreviewPlayer(null);
                require(!avatar.scriptError && avatar.luaRuntime != null && avatar.getMolangContext() != null,
                        "original native YSM load creates an independent Lua/Molang runtime");
                require(!avatar.isHost && avatar.permissions.get(Permissions.NETWORKING) == 0,
                        "wearing a resource never grants host or networking");
                runLua(avatar, """
                        assert(host == nil and net == nil and world == nil and avatar == nil)
                        assert(vanilla_model == nil and avatar_controls == nil and config == nil and file == nil)
                        assert(cosmetic_controls ~= nil and ysm_model ~= nil)
                        assert(action_wheel ~= nil and not action_wheel:isEnabled() and action_wheel:getSelected() == 0)
                        local cosmeticPage = action_wheel:newPage('cosmetic-entry')
                        wheelCalls = 0
                        cosmeticPage:newAction():onLeftClick(function() wheelCalls = wheelCalls + 1 end)
                        action_wheel:setPage(cosmeticPage)
                        cosmetic_animations:setPage('cosmetic-entry')
                        action_wheel:execute(1)
                        assert(wheelCalls == 0 and action_wheel:getSelectedAction() == nil)
                        calls = 0
                        cosmetic_controls:newToggle('wing'):setPersistent(true)
                            :setBinding('v.wing'):setOnChange(function(value)
                                calls = calls + 1
                                animations:setMolangVar('wing_value', value and 1 or 0)
                            end)
                        """);
            }
            require(!first.owner.equals(second.owner) && !first.owner.equals(wearer)
                    && first.luaRuntime != second.luaRuntime && first.getMolangContext() != second.getMolangContext()
                    && first.getYsmRuntime() != second.getYsmRuntime(), "same resource has separate execution/effect owners");
            require(first.controls.setValue(first, "wing", true), "draft control accepts a local edit");
            runLua(first, "assert(calls == 1 and animations:getMolangVar('wing_value') == 1)");
            runLua(second, "assert(calls == 0 and animations:getMolangVar('wing_value') == nil)");
            runLua(second, """
                    animations:setMolangVar(' Variable.Wing.Speed ', 2)
                    assert(animations:getMolangVar('v.wing.speed') == 2)
                    animations:setMolangVars({['v.wing.speed'] = 3, ['wing.power'] = 4})
                    assert(animations:getMolangVar('wing.speed') == 3)
                    assert(animations:getMolangVar('variable.wing.power') == 4)
                    assert(not pcall(function() animations:setMolangVar('q.health', 1) end))
                    assert(not pcall(function() animations:setMolangVar('v.invalid', 0/0) end))
                    assert(not pcall(function()
                        animations:setMolangVars({['wing.speed'] = 9, ['v.invalid'] = math.huge})
                    end))
                    assert(animations:getMolangVar('wing.speed') == 3)
                    """);
            var animation = new Animation(second, "cosmetic", "test",
                    Animation.LoopMode.ONCE, false, 1, 0, 1, 0, 0);
            animation.setMolangVar("v.wing.speed", 5);
            require(animation.getMolangVar("variable.wing.speed") == 5f,
                    "per-animation and animation-collection APIs use the same variable rules");
            require(Boolean.TRUE.equals(firstContext.control("wing")) && secondContext.controls().isEmpty(),
                    "persistent draft values remain within their instance and session");
            require(!observer.controls.setValue(observer, "wing", true), "observer rejects a local owner edit");
            require(!observer.controls.press(observer, "wing"), "observer cannot invoke an owner press callback");
            require(observer.controls.applyRemoteValue(observer, "wing", true), "observer may restore an authorized value");
            runLua(observer, "assert(calls == 0)");
            require(observerContext.controls().isEmpty(), "restore does not persist an observer preference");
            Map<String, Object> saved = firstContext.controls();
            firstContext.saveControl("wing", false);
            require(Boolean.TRUE.equals(saved.get("wing")), "configuration snapshots do not retain a mutable control map");
            rejectState(() -> firstContext.saveLuaState("bad", Double.NaN));
            rejectState(() -> firstContext.saveControl("bad", "文".repeat(171)));
            for (int index = observer.controls.all().size(); index < observerContext.limits().maxControls(); index++) {
                observer.controls.register(new AvatarControlDefinition("limit-" + index, AvatarControlType.TOGGLE));
            }
            runLua(observer, "assert(not pcall(function() cosmetic_controls:newToggle('over-budget') end))");

            FiguraLuaRuntime failed = first.luaRuntime;
            first.tick.reset(1000);
            failed.run(failed.load("timeout", "while true do end"), first.tick);
            require(first.scriptError && first.luaRuntime == null && !second.scriptError,
                    "instruction timeout disables only its own instance");
            runLua(second, "assert(calls == 0); cosmetic_controls:set('wing', true); assert(calls == 1)");
            testZeroControlBudget(account, outfit, wearer, resource, permissions);
            testRestoredControl(account, outfit, wearer, resource, permissions);
        } finally {
            first.clean();
            second.clean();
            observer.clean();
        }
    }

    private static void testRestoredControl(UUID account, UUID outfit, UUID wearer,
                                            OutfitConfig.ResourceRef resource,
                                            PermissionPack.PlayerPermissionPack permissions) {
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("head_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of("wing", true), Map.of());
        var context = new CosmeticInstanceContext(account, outfit, wearer, instance, OutfitLimits.defaults(),
                CosmeticInstanceContext.Scope.DRAFT);
        Avatar avatar = Avatar.createCosmetic(context, permissions, "restored-controls");
        try {
            var runtime = new FiguraLuaRuntime(avatar, Map.of());
            require(runtime.init(null), "restored instance initializes");
            runLua(avatar, """
                    calls = 0
                    cosmetic_controls:newToggle('wing'):setDefault(false):setOnChange(function()
                        calls = calls + 1
                    end)
                    assert(cosmetic_controls:get('wing') == true and calls == 0)
                    """);
        } finally {
            avatar.clean();
        }
    }

    private static void testZeroControlBudget(UUID account, UUID outfit, UUID wearer,
                                             OutfitConfig.ResourceRef resource,
                                             PermissionPack.PlayerPermissionPack permissions) {
        var gson = new com.google.gson.Gson();
        var configured = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        configured.addProperty("maxControls", 0);
        OutfitLimits limits = gson.fromJson(configured, OutfitLimits.class);
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("head_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(account, outfit, wearer, instance, limits,
                CosmeticInstanceContext.Scope.DRAFT);
        Avatar avatar = Avatar.createCosmetic(context, permissions, "disabled-controls");
        try {
            var runtime = new FiguraLuaRuntime(avatar, Map.of());
            require(runtime.init(null), "isolated runtime initializes with a configured zero control budget");
            runLua(avatar, "assert(not pcall(function() cosmetic_controls:newToggle('disabled') end))");
            rejectState(() -> context.saveControl("disabled", true));
            require(avatar.controls.all().isEmpty(), "zero negotiated capacity never registers or truncates controls");
        } finally {
            avatar.clean();
        }
    }

    private static void rejectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("invalid cosmetic state accepted");
        } catch (IllegalArgumentException expected) {
            // The production state boundary must reject the value before it can enter a snapshot.
        }
    }

    private static CosmeticInstanceContext context(UUID account, UUID outfit, UUID wearer,
                                                    OutfitConfig.ResourceRef resource,
                                                    CosmeticInstanceContext.Scope scope) {
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("head_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        return new CosmeticInstanceContext(account, outfit, wearer, instance, OutfitLimits.defaults(), scope);
    }

    private static void runLua(Avatar avatar, String source) {
        avatar.tick.reset(65536);
        FiguraLuaRuntime runtime = avatar.luaRuntime;
        require(runtime != null && runtime.run(runtime.load("instance-test", source), avatar.tick) != null
                && !avatar.scriptError, "instance Lua assertion");
    }

    private static final class Part {
        private final String name;
        private final List<Part> children = new ArrayList<>();

        private Part(String name, Part... children) {
            this.name = name;
            this.children.addAll(List.of(children));
        }
    }

    private static void testIndexes() throws Exception {
        var limits = OutfitLimits.defaults();
        var cache = new SkeletonIndex.Cache();
        var ref = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "a".repeat(64), OutfitConfig.Format.FIGURA);
        Part a = new Part("Cosmetic", new Part("wing_anchor"), new Part("wing_anchor"));
        Part b = new Part("Cosmetic", new Part("wing_anchor"), new Part("wing_anchor"));
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        var first = cache.bind(ref, one, List.of(a), part -> part.name, part -> part.children, limits);
        var second = cache.bind(ref, two, List.of(b), part -> part.name, part -> part.children, limits);
        require(cache.parseCount() == 1 && first.definition() == second.definition(), "same revision parses its structural index once");
        var bone = first.definition().reference(one, 2);
        require(bone.path().getLast().siblingOrdinal() == 1, "same-name sibling ordinal survives");
        require(first.resolve(bone).orElseThrow() == a.children.get(1) && second.resolve(bone).isEmpty(), "runtime handles stay instance-owned");
        require(first.definition().uniqueNamed("wing_anchor").isEmpty(), "ambiguous standard names stay unresolved");
        Part cycle = new Part("Cycle");
        cycle.children.add(cycle);
        reject(() -> SkeletonIndex.capture(one, List.of(cycle), part -> part.name, part -> part.children, limits));
        Part deep = new Part("end");
        for (int index = 0; index < 65; index++) {
            deep = new Part("node", deep);
        }
        Part tree = deep;
        reject(() -> SkeletonIndex.capture(one, List.of(tree), part -> part.name, part -> part.children, limits));
    }

    private static void testCache(Path root) throws Exception {
        Files.createDirectories(root);
        var session = new LibrarySession("https://example.invalid", UUID.randomUUID(), 1, () -> true);
        var cache = new LibraryPreviewCache(root);
        require(!Files.exists(root.resolve("cosmetic")), "avatar cache construction is lazy for accessories");
        cache = cache.forResourceKind("ACCESSORY");
        byte[] bytes = "encrypted by the existing container".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = CloudHttp.sha256(bytes);
        UUID id = UUID.randomUUID();
        var scope = new LibraryPreviewCache.Scope(session.instance(), session.player(), id, 1, 1);
        var key = new LibraryPreviewCache.Key(scope, hash);
        require(cache.put(key, LibraryPreviewCache.Kind.MODEL, bytes), "accessory preview publication");
        try (var lease = cache.find(key, LibraryPreviewCache.Kind.MODEL, () -> {}).orElseThrow()) {
            require(java.util.Arrays.equals(bytes, lease.read()), "authenticated cosmetic preview read");
        }
        try (var files = Files.list(root.resolve("cosmetic"))) {
            Path saved = files.filter(path -> path.toString().endsWith(".oms")).findFirst().orElseThrow();
            require(ModelCacheCodec.isWrapped(saved), ".oms uses the existing AES-GCM codec");
        }
        var model = new LibraryModels.Model(id, 1, 1, "Accessory", "Accessory", List.of(),
                new LibraryModels.Uploader(session.player(), "Owner"), "FIGURA", 0, false, 0, 0,
                false, "PRIVATE", true, List.of("preview", "download"), "", "decorative_parts", List.of(),
                "ACTIVE", bytes.length, 1, "cosmetic.json", "CLIENT_EXTRACTED", "ACCESSORY", null);
        var formal = new LibraryResourceCache(root, session);
        Path staged = Files.createTempFile(root.resolve("cosmetic"), "download-", ".part");
        new ModelCacheCodec(root).write(staged, bytes);
        UUID transfer = UUID.randomUUID();
        var receipt = new LibraryModels.Transfer(transfer, "download", 1, 1,
                "/api/library/v1/transfers/" + transfer, Long.MAX_VALUE);
        var pending = formal.stage(model, staged, bytes, hash, receipt);
        formal.publish(model, pending);
        require(java.util.Arrays.equals(bytes, formal.read(model)), "formal accessory read uses its own suffix/directory");
        require(!Files.exists(root.resolve("avatars")), "cosmetic-only reads do not create avatar storage");
        LibraryPreviewCache ownedCache = cache;
        reject(() -> ownedCache.find(key, LibraryPreviewCache.Kind.MODEL, () -> { throw new java.io.IOException("REVOKED"); }));
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }

    private static void reject(Checked action) throws Exception {
        try {
            action.run();
        } catch (IllegalArgumentException | java.io.IOException expected) {
            return;
        }
        throw new AssertionError("invalid source/reference was accepted");
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
