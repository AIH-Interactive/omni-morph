package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.FiguraSkeletonAdapter;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.SkeletonIndex;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;
import top.aihmc.omnimorph.wizards.CosmeticCreator;
import top.aihmc.omnimorph.wizards.CosmeticTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Real generated source/Lua/particle flow with controlled native matrices; no window/pixel assertions. */
final class CreatedTrailParticleSmokeTest {
    static void run(Runnable drain, Supplier<List<Particle>> submissions) throws Exception {
        Path data = Files.createTempDirectory("created-trail-particle-");
        try {
            Path source = CosmeticCreator.create(data.resolve("cosmetics"), "Created trail", "author", "",
                    CosmeticTemplate.TRAIL, "tailtip", OutfitConfig.Transform.identity());
            var description = LocalCosmeticSource.describe(source, () -> false);
            var prepared = LocalCosmeticSource.prepare(source, UUID.randomUUID(), description.contentHash(), () -> false);
            var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                    null, description.contentHash(), OutfitConfig.Format.FIGURA);
            UUID wearer = UUID.randomUUID();
            UUID account = UUID.randomUUID();
            UUID outfit = UUID.randomUUID();
            Avatar first = owner(account, outfit, wearer, 1, resource, prepared.nbt(), CosmeticInstanceContext.Scope.OWNER);
            Avatar second = owner(account, outfit, wearer, 2, resource, prepared.nbt(), CosmeticInstanceContext.Scope.OWNER);
            try {
                pose(first, resource, 1, 2, 3);
                pose(second, resource, 4, 5, 6);
                advance(first, second, 4, 12);
                var initial = owned(submissions, first);
                require(initial.size() == 1 && owned(submissions, second).size() == 1,
                        "Twelve draws and both world render contexts still emit one particle per four ticks per instance");
                CustomParticle petal = initial.getFirst();
                var particle = new LuaParticle("created petal", petal, first);
                require(particle.getLifetime() == 24 && !particle.isEmissive() && !particle.isDynamicLight(),
                        "Created default is a finite, environment-lit static petal without a world light");
                var staticState = extract(petal);
                require(staticState.x == 1 && staticState.y == 2 && staticState.z == 3,
                        "Actual authored constructor uses its own sampled native matrix in world coordinates");
                require(staticState.layer.textureAtlasLocation().equals(first.renderer.getTexture("model.sakura_petal").getLocation())
                                && staticState.u0 == 0 && staticState.u1 == 1 && staticState.v0 == 0 && staticState.v1 == 1
                                && Math.abs(staticState.size - .07f) < .00001,
                        "Created static preset resolves the owned PNG and full-image native UV");

                lua(first, "cosmetic_controls:set('enabled', false)");
                lua(second, "cosmetic_controls:set('star', true); cosmetic_controls:set('random_start', false)");
                advance(first, second, 4, 1);
                require(owned(submissions, first).size() == 1 && owned(submissions, second).size() == 2,
                        "Changing one copy's controls does not alter its sibling's emitter");
                CustomParticle star = owned(submissions, second).getLast();
                require(star.emissive() && !star.dynamicLight() && star.frame() == 0 && !petal.emissive(),
                        "Switching presets changes only new particles and enables full bright without dynamic light");
                var starState = extract(star);
                require(starState.layer.textureAtlasLocation().equals(second.renderer.getTexture("model.twinkle_star_sheet").getLocation())
                                && starState.u0 == 0 && starState.u1 == .25f && starState.v0 == 0 && starState.v1 == .5f
                                && starState.light == 0xf000f0,
                        "Generated star uses the real 4x2 sheet and native full-bright coordinates");
                for (int tick = 0; tick < 16; tick++) {
                    star.tick();
                }
                require(star.frame() == 0, "Eight two-tick frames complete one sixteen-tick loop");
                second.resourceReloadEvent();
                require(!star.isAlive() && petal.isAlive(), "Resource reload removes only that copy's particles");
                pose(first, resource, 20, 2, 3);
                draw(first, "RENDER");
                require(!petal.isAlive(), "Teleport sampling clears the original execution's particles");
            } finally {
                first.clean();
                second.clean();
                drain.run();
            }

            Avatar preview = owner(account, outfit, wearer, 3, resource, prepared.nbt(), CosmeticInstanceContext.Scope.DRAFT);
            try (var scope = new PreviewParticleScope(Minecraft.getInstance().level, OutfitLimits.defaults())) {
                scope.bind(preview);
                pose(preview, resource, 7, 8, 9);
                int submitted = submissions.get().size();
                for (int tick = 0; tick < 4; tick++) {
                    CustomParticleRuntime.beginTick();
                    event(preview, "TICK");
                    draw(preview, "FIGURA_GUI");
                }
                require(scope.liveCount() == 1 && submissions.get().size() == submitted,
                        "The generated Lua emits inside its actual private preview scope, never the world engine");
                for (int tick = 0; tick < 26; tick++) {
                    CustomParticleRuntime.beginTick();
                    scope.tick();
                }
                require(scope.liveCount() == 0, "Created particle expires and returns preview capacity without drawing");
            } finally {
                preview.clean();
                CustomParticleRuntime.finishFrame();
                drain.run();
            }
            System.out.println("PASS created Trail emission: generated package -> authored Lua -> native particles, "
                    + "static/sprite UV, full bright, frame-independent rate, duplicate controls, reload/teleport/expiry "
                    + "and isolated GUI scope; controlled matrices and test-only scale mixin bridge, "
                    + "actual mixin/mounted draw/pixels MANUAL/PENDING");
        } finally {
            try (var paths = Files.walk(data)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Avatar owner(UUID account, UUID outfit, UUID wearer, int ordinal,
                                 OutfitConfig.ResourceRef resource, net.minecraft.nbt.CompoundTag source,
                                 CosmeticInstanceContext.Scope scope) {
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, ordinal, true,
                new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null),
                OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(account, outfit, wearer, instance,
                OutfitLimits.defaults(), scope);
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.toString());
        Avatar avatar = Avatar.createCosmetic(context, permissions, "created Trail particles");
        avatar.load(source.copy());
        avatar.initializePreviewPlayer(null);
        require(avatar.loaded && !avatar.scriptError && avatar.luaRuntime != null, "Generated Lua initializes");
        installScaleMixinBridge(avatar);
        return avatar;
    }

    private static void installScaleMixinBridge(Avatar avatar) {
        // The standalone JVM has no Mixin transformer. Supply precisely the 0.2f reset from
        // SingleQuadParticleMixin, then call the actual native scale method. Production code
        // and the generated script stay unchanged; this does not prove real mixin application.
        var wrapped = avatar.luaRuntime.typeManager.javaToLua(new LuaParticle("scale fixture", null, avatar)).arg1();
        var methods = wrapped.getmetatable().get("__index").checktable();
        methods.set("setScale", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override
            public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self, org.luaj.vm2.LuaValue argument) {
                try {
                    float scale = (float) argument.checkdouble();
                    if (!Float.isFinite(scale) || scale <= 0) {
                        throw new org.luaj.vm2.LuaError("Invalid custom particle scale");
                    }
                    var field = LuaParticle.class.getDeclaredField("particle");
                    field.setAccessible(true);
                    var particle = (CustomParticle) field.get(self.checkuserdata(LuaParticle.class));
                    var size = SingleQuadParticle.class.getDeclaredField("quadSize");
                    size.setAccessible(true);
                    size.setFloat(particle, .2f);
                    particle.scale(scale);
                    return self;
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
        });
    }

    private static void pose(Avatar avatar, OutfitConfig.ResourceRef resource, float x, float y, float z) {
        var binding = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer, resource,
                avatar.cosmeticContext.instanceId(), new SkeletonIndex.Cache(), OutfitLimits.defaults()).binding();
        int root = binding.definition().uniqueNamed("Cosmetic").orElseThrow();
        binding.parts().get(root).savedPartToWorldMat.set(new Matrix4f().translation(x, y, z));
    }

    private static List<CustomParticle> owned(Supplier<List<Particle>> submissions, Avatar owner) {
        return submissions.get().stream().filter(particle -> particle instanceof CustomParticle custom && custom.owner() == owner)
                .map(CustomParticle.class::cast).toList();
    }

    private static void advance(Avatar first, Avatar second, int count, int frames) {
        for (int tick = 0; tick < count; tick++) {
            CustomParticleRuntime.beginTick();
            event(first, "TICK");
            event(second, "TICK");
            for (int frame = 0; frame < frames; frame++) {
                draw(first, "RENDER");
                draw(first, "WORLD");
                draw(second, "RENDER");
                draw(second, "WORLD");
            }
        }
    }

    private static void event(Avatar avatar, String name) {
        avatar.tick.reset(65536);
        avatar.run(name, avatar.tick);
        require(!avatar.scriptError, "Created event: " + avatar.errorText);
    }

    private static void draw(Avatar avatar, String context) {
        avatar.render.reset(65536);
        avatar.run("POST_RENDER", avatar.render, 1f, context);
        require(!avatar.scriptError, "Created render callback: " + avatar.errorText);
    }

    private static void lua(Avatar avatar, String source) {
        avatar.tick.reset(65536);
        avatar.luaRuntime.run(avatar.luaRuntime.load("created Trail controls", source), avatar.tick);
        require(!avatar.scriptError, "Created controls: " + avatar.errorText);
    }

    private static Quad extract(CustomParticle particle) {
        var state = new Quad();
        particle.extract(state, new Camera() {
            @Override
            public Quaternionf rotation() {
                return new Quaternionf();
            }

            @Override
            public net.minecraft.world.phys.Vec3 position() {
                return net.minecraft.world.phys.Vec3.ZERO;
            }
        }, 1f);
        return state;
    }

    private static final class Quad extends QuadParticleRenderState {
        private SingleQuadParticle.Layer layer;
        private float x, y, z, u0, u1, v0, v1, size;
        private int light;

        @Override
        public void add(SingleQuadParticle.Layer layer, float x, float y, float z,
                        float qx, float qy, float qz, float qw, float size,
                        float u0, float u1, float v0, float v1, int color, int light) {
            super.add(layer, x, y, z, qx, qy, qz, qw, size, u0, u1, v0, v1, color, light);
            this.layer = layer;
            this.x = x;
            this.y = y;
            this.z = z;
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
            this.size = size;
            this.light = light;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
