package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Quaternionf;
import org.figuramc.figura.animation.Animation;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

import org.figuramc.figura.lua.api.particle.ParticleAPI;
/** Original Avatar init queue, authored Lua and native animation markers reach the public particle API. */
final class CosmeticParticleEntrySmokeTest {
    static void run(Runnable drain, IntSupplier worldSubmissions) throws Exception {
        for (boolean ysm : new boolean[]{false, true}) {
            verify(CosmeticInstanceContext.Scope.DRAFT, ysm, drain, worldSubmissions);
            verify(CosmeticInstanceContext.Scope.OWNER, ysm, drain, worldSubmissions);
        }
        System.out.println("PASS authored cosmetic particles: actual NBT/Lua init, owned PNG name, native animation marker, "
                + "next-tick creation, duplicate isolation, preview/world routing, reload and retirement; pixels MANUAL/PENDING");
    }

    private static void verify(CosmeticInstanceContext.Scope mode, boolean ysm,
                                Runnable drain, IntSupplier worldSubmissions) throws Exception {
        UUID wearer = UUID.randomUUID();
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "a".repeat(64), ysm ? OutfitConfig.Format.YSM_PLAIN : OutfitConfig.Format.FIGURA);
        var data = source(ysm);
        Avatar template = Avatar.createParticlePreview(UUID.randomUUID(), "particle entry permissions");
        Avatar first = owner(wearer, resource, mode, template, data);
        Avatar second = owner(wearer, resource, mode, template, data);
        var preview = mode == CosmeticInstanceContext.Scope.DRAFT
                ? new PreviewParticleScope(Minecraft.getInstance().level, OutfitLimits.defaults()) : null;
        com.mojang.blaze3d.textures.GpuTexture cachedTexture = null;
        try {
            if (preview != null) {
                preview.bindAll(List.of(first, second));
            }
            first.initializePreviewPlayer(null);
            second.initializePreviewPlayer(null);
            require(first.luaRuntime != null && second.luaRuntime != null && !first.scriptError && !second.scriptError,
                    "real authored init registers the animation subscriber: " + first.errorText + "/" + second.errorText);
            int submitted = worldSubmissions.getAsInt();
            if (ysm) {
                require(ParticleAPI.resolveTexture(first, "event") == ParticleAPI.resolveTexture(first, "effect.png"),
                        "YSM texture ID and package path aliases reuse one lazy particle texture");
                require(first.getYsmRuntime().setTexture("BoDy"), "the original case-insensitive skin selection remains available");
                require(ParticleAPI.resolveTexture(first, "body") == first.getYsmRuntime().texture(),
                        "YSM particles reuse the current native body texture without another decode");
                cachedTexture = ParticleAPI.resolveTexture(first, "event").getTexture();
            }
            var firstAnimation = marker(first);
            require(count(preview, wearer) == 0, "a native animation marker does not synchronously emit a particle");
            CustomParticleRuntime.beginTick();
            first.tick();
            second.tick();
            require(count(preview, wearer) == 1 && !first.scriptError && !second.scriptError,
                    "the actual next-tick Avatar dispatch invokes the authored particle constructor: " + first.errorText);
            require(worldSubmissions.getAsInt() == submitted + (preview == null ? 1 : 0),
                    "only an owner world instance submits to the native engine");
            if (preview != null) {
                var frame = preview.extract(net.minecraft.world.phys.Vec3.ZERO, new Quaternionf(), 1);
                require(frame.vertexCount() == 4, "an authored animation particle reaches isolated native GUI geometry");
            }
            firstAnimation.run();
            marker(second);
            first.resourceReloadEvent();
            require(count(preview, wearer) == 0, "resource reload removes existing particles from just that author execution");
            CustomParticleRuntime.beginTick();
            first.tick();
            second.tick();
            require(count(preview, wearer) == 1 && !second.scriptError,
                    "reload withdraws one execution's queued event while the same-resource sibling still emits");
            first.clearParticles();
            require(count(preview, wearer) == 1, "clearing the already empty execution preserves its sibling");
            if (preview != null) {
                preview.close();
                marker(second);
                CustomParticleRuntime.beginTick();
                second.tick();
                require(count(preview, wearer) == 0 && worldSubmissions.getAsInt() == submitted,
                        "a retained author callback cannot activate effects after the preview scope closes");
            }
        } finally {
            if (preview != null) {
                preview.close();
            }
            first.clean();
            second.clean();
            template.clean();
            CustomParticleRuntime.finishFrame();
            drain.run();
        }
        require(CustomParticleRuntime.liveCount(wearer) == 0, "author cleanup returns all world capacity");
        require(cachedTexture == null || cachedTexture.isClosed(),
                "YSM named particle texture retires after its runtime, particles and extracted frame release it");
    }

    private static Avatar owner(UUID wearer, OutfitConfig.ResourceRef resource, CosmeticInstanceContext.Scope scope,
                                Avatar template, CompoundTag data) {
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("back_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(wearer, UUID.randomUUID(), wearer, instance, OutfitLimits.defaults(), scope);
        Avatar owner = Avatar.createCosmetic(context, template.permissions, "authored particle entry");
        owner.load(data.copy());
        require(owner.loaded && owner.nbt != null, "the native loader accepts the author fixture");
        return owner;
    }

    private static Runnable marker(Avatar owner) {
        if (owner.isYsmNative()) {
            var player = owner.getYsmRuntime().animations();
            player.registerAnimations(top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser.parse("""
                    {"animations":{"emit":{"animation_length":1,"timeline":{"0":"v.emit=1;"}}}}
                    """, top.aihmc.omnimorph.molang.MolangEngine.fromCustomBinding(owner.getAvatarBindings())));
            Runnable emit = () -> player.play("emit", false, 1);
            emit.run();
            return emit;
        }
        var animation = new Animation(owner, "model", "emit", Animation.LoopMode.LOOP, false, 1, 0, 1, 0, 0);
        animation.newCode(.25f, "local value=1");
        animation.play();
        animation.playCode(0, .5f);
        return () -> animation.playCode(0, .5f);
    }

    private static int count(PreviewParticleScope preview, UUID wearer) {
        return preview == null ? CustomParticleRuntime.liveCount(wearer) : preview.liveCount();
    }

    private static CompoundTag source(boolean ysm) throws Exception {
        var pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        pixel.setRGB(0, 0, 0xffffffff);
        var bytes = new ByteArrayOutputStream();
        require(ImageIO.write(pixel, "png", bytes), "fixture PNG encoder is available");
        var sources = new CompoundTag();
        sources.putByteArray("effect.png", bytes.toByteArray());
        var material = new CompoundTag();
        material.putString("d", "effect.png");
        var materials = new ListTag();
        materials.add(material);
        var textures = new CompoundTag();
        textures.put("src", sources);
        textures.put("data", materials);
        var scripts = new CompoundTag();
        scripts.putByteArray("main", """
                assert(particles ~= nil and cosmetic_animations ~= nil)
                assert(host == nil and net == nil and world == nil and textures == nil)
                cosmetic_animations:onEvent(function(event)
                    if event.type == 'keyframe' or event.type == 'timeline' then
                        local particle = particles:newCustomParticle('effect.png', vectors.vec3(0, 0, 0))
                        particle:setLifetime(2):setEmissive(true)
                    end
                end)
                """.getBytes(StandardCharsets.UTF_8));
        var data = new CompoundTag();
        data.put("textures", textures);
        data.put("scripts", scripts);
        if (ysm) {
            var metadata = new CompoundTag();
            metadata.putString("format", "ysm-native");
            data.put("metadata", metadata);
            var model = new CompoundTag();
            model.putByteArray("main_model", """
                    {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},
                    "bones":[{"name":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]}]}
                    """.getBytes(StandardCharsets.UTF_8));
            model.putByteArray("texture", bytes.toByteArray());
            model.putString("texture_id", "body");
            var entry = new CompoundTag();
            entry.putString("id", "event");
            entry.putString("path", "effect.png");
            entry.putByteArray("data", bytes.toByteArray());
            var entries = new ListTag();
            entries.add(entry);
            model.put("texture_entries", entries);
            data.put("ysm", model);
        }
        return data;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
