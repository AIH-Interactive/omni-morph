package top.aihmc.omnimorph.lua.api.particle;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.permissions.Permissions;

import java.util.Map;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/** Actual Lua construction, isolated lifecycle and native panel geometry; pixel validation remains separate. */
final class PreviewParticleNativeSmokeTest {
    static void run(Avatar worldOwner, Runnable drain, IntSupplier worldSubmissions) throws Exception {
        var client = Minecraft.getInstance();
        var world = client.level;
        var limits = limits();
        Avatar first = owner(worldOwner.owner);
        Avatar second = owner(UUID.randomUUID());
        Avatar anotherPreview = owner(worldOwner.owner);
        var texture = new FiguraTexture(first, "preview-a", new NativeImage(1, 1, true));
        var siblingTexture = new FiguraTexture(second, "preview-b", new NativeImage(1, 1, true));
        var otherTexture = new FiguraTexture(anotherPreview, "preview-c", new NativeImage(1, 1, true));
        var worldTexture = new FiguraTexture(worldOwner, "live-world", new NativeImage(1, 1, true));
        var live = new CustomParticle(worldOwner, world, worldTexture, Vec3.ZERO, Vec3.ZERO);
        var cachedBeforeBinding = new CustomParticle(first, world, texture, Vec3.ZERO, Vec3.ZERO);
        try (var scope = new PreviewParticleScope(world, limits);
             var separate = new PreviewParticleScope(world, limits)) {
            require(live.activate(), "ordinary world particle remains active beside the preview");
            preparationFence(worldOwner, live, drain, worldSubmissions);
            try {
                scope.bind(worldOwner);
                throw new AssertionError("preview stole an active world owner");
            } catch (IllegalStateException expected) {
                require(worldOwner.previewParticles == null, "failed binding leaves live ownership unchanged");
            }
            scope.bindAll(java.util.List.of(first, second));
            separate.bind(anotherPreview);
            require(!cachedBeforeBinding.activate(), "pre-binding custom handle cannot escape into the world registry");
            reject(() -> new LuaParticle("cached", cachedBeforeBinding, first).spawn());
            int submitted = worldSubmissions.getAsInt();
            var a = new ParticleAPI(first).newCustomParticle(texture, FiguraVec3.of(1, 2, 3), null);
            var b = new ParticleAPI(second).newCustomParticle(siblingTexture, FiguraVec3.of(4, 5, 6), null);
            require(a.isAlive() && b.isAlive() && scope.liveCount() == 2
                            && worldSubmissions.getAsInt() == submitted && CustomParticleRuntime.liveCount(worldOwner.owner) == 1,
                    "real public creation retains preview particles without submitting them to the native world engine");
            var denied = new ParticleAPI(first).newCustomParticle(texture, FiguraVec3.of(0, 0, 0), null);
            require(!denied.isAlive() && scope.liveCount() == 2, "preview instances share their negotiated live-particle cap");
            var c = new ParticleAPI(anotherPreview).newCustomParticle(otherTexture, FiguraVec3.of(0, 0, 0), null);
            require(c.isAlive() && separate.liveCount() == 1 && scope.liveCount() == 2,
                    "another preview using the same player UUID has an independent budget and registry");
            a.setDynamicLight(true);
            reject(() -> b.setDynamicLight(true));
            require(a.isDynamicLight() && !b.isDynamicLight()
                            && ParticleDynamicLights.liveCount(worldOwner.owner) == 0
                            && ParticleDynamicLights.apply(world, 1, 2, 3, 0) == 0,
                    "preview light slots are bounded locally and publish no world illumination");
            var nativeSuppressed = new ParticleAPI(first).__index("minecraft:smoke");
            nativeSuppressed.spawn().setLifetime(12).setColor(1, 1d, 1d, 1d);
            require(!new ParticleAPI(first).isPresent("minecraft:smoke") && worldSubmissions.getAsInt() == submitted,
                    "preview native factories cannot create world side effects");
            require(!nativeSuppressed.isAlive(), "ordinary native particle chaining stays harmless in a preview");
            YsmRenderFrameClock.beginFrame();
            var transform = new Matrix4f().translation(7, 8, 9);
            var panelFrame = scope.extract(new Vec3(1, 2, 3), new Quaternionf(), 1);
            require(panelFrame.vertexCount() == 8, "both preview owners contribute their native Billboard quads");
            byte[] frozenPanel = vertices(panelFrame, transform);
            var panelBytes = ByteBuffer.wrap(frozenPanel).order(ByteOrder.nativeOrder());
            center(panelBytes, 0, 7, 8, 9);
            center(panelBytes, 4, 10, 11, 12);
            a.setRenderMode("Ribbon");
            require(Arrays.equals(frozenPanel, vertices(panelFrame, transform)),
                    "replaying a frozen preview cannot observe later author render-mode changes");
            var nodes = new SubmitNodeStorage();
            panelFrame.submit(new PoseStack(), nodes);
            require(nodes.getSubmitsPerOrder().containsKey(100), "panel particles use the native custom geometry queue");
            require(worldSubmissions.getAsInt() == submitted && ParticleDynamicLights.liveCount(worldOwner.owner) == 0,
                    "extracting and replaying panel geometry cannot publish world particles or lights");
            require(scope.extract(new Vec3(1, 2, 3), new Quaternionf(), 1).vertexCount() == 4,
                    "the first Ribbon sample emits no invented segment beside the other Billboard");
            a.setVelocity(1, 0d, 0d);
            CustomParticleRuntime.beginTick();
            scope.tick();
            YsmRenderFrameClock.beginFrame();
            var ribbonPanel = scope.extract(new Vec3(1, 2, 3), new Quaternionf(), 1);
            require(ribbonPanel.vertexCount() == 8, "panel extraction includes a moving Ribbon and a native Billboard");
            require(vertices(ribbonPanel, transform).length == 8 * DefaultVertexFormat.PARTICLE.getVertexSize(),
                    "Ribbon GUI replay writes the same complete native vertex format");
            var layers = new LuaTable();
            layers.set(1, new LuaTable());
            var overlay = new LuaTable();
            overlay.set("blend", "additive");
            layers.set(2, overlay);
            b.setLayers(layers);
            var layeredPanel = scope.extract(new Vec3(1, 2, 3), new Quaternionf(), 1);
            require(layeredPanel.vertexCount() == 12, "layered panel quads coexist with the shared Ribbon geometry");
            var layeredNodes = new SubmitNodeStorage();
            layeredPanel.submit(new PoseStack(), layeredNodes);
            require(layeredNodes.getSubmitsPerOrder().containsKey(100)
                            && layeredNodes.getSubmitsPerOrder().containsKey(101),
                    "different blend pipelines preserve their author layer order in the native GUI queue");
            var view = new Quaternionf().rotationZ((float) Math.PI).rotateX(.3f).rotateY(.8f);
            var reflectedPanel = separate.extract(Vec3.ZERO, view, 1);
            var reflectedTransform = new Matrix4f().translation(7, 8, 9).scale(2, 2, -2).rotate(view);
            var reflectedBytes = ByteBuffer.wrap(vertices(reflectedPanel, reflectedTransform)).order(ByteOrder.nativeOrder());
            for (int vertex = 0; vertex < 4; vertex++) {
                require(Math.abs(reflectedBytes.getFloat(vertex * DefaultVertexFormat.PARTICLE.getVertexSize() + 8) - 9) < .0001,
                        "Billboards face the panel after view rotation and the real PiP Z reflection");
            }
            var tickHits = new AtomicInteger();
            var frameHits = new AtomicInteger();
            var aLua = first.luaRuntime.typeManager.javaToLua(a).arg1();
            var bLua = second.luaRuntime.typeManager.javaToLua(b).arg1();
            callback(first, tickHits, "tickHit");
            callback(second, tickHits, "tickHit");
            callback(first, frameHits, "frameHit");
            aLua.get("setTickCallback").call(aLua, first.luaRuntime.load("preview tick", "tickHit()").checkfunction());
            bLua.get("setTickCallback").call(bLua, second.luaRuntime.load("preview tick", "tickHit()").checkfunction());
            aLua.get("setFrameCallback").call(aLua, first.luaRuntime.load("preview frame", "frameHit()").checkfunction());
            YsmRenderFrameClock.beginFrame();
            CustomParticleRuntime.runFrameCallbacks(1);
            require(frameHits.get() == 0, "world extraction never runs preview callbacks");
            scope.prepareFrame(1);
            scope.prepareFrame(.5f);
            require(frameHits.get() == 1, "preview frame callback runs once only through its own scope");
            CustomParticleRuntime.beginTick();
            scope.tick();
            scope.tick();
            require(tickHits.get() == 1 && b.getCallbackError().equals("CALLBACK_TICK_LIMIT"),
                    "preview ticks once per logical tick and shares callback count across its owners");
            a.setRenderMode("Ribbon");
            b.setRenderMode("Ribbon");
            var extracted = new ParticlesRenderState();
            CustomParticleRuntime.extractRibbons(extracted, null, new FixtureCamera(), 1);
            require(extracted.particles.isEmpty(), "world particle extraction excludes both preview ribbons");
            first.resourceReloadEvent();
            require(!a.isAlive() && b.isAlive() && live.active() && scope.liveCount() == 1,
                    "preview reload clears only its execution, preserving sibling and same-UUID world particle");
            require(Arrays.equals(frozenPanel, vertices(panelFrame, transform)),
                    "deferred vertices survive preview particle removal until frame completion");
            b.setLifetime(1);
            CustomParticleRuntime.beginTick();
            scope.tick();
            require(!b.isAlive() && scope.liveCount() == 0, "native lifetime expiry releases preview capacity without a render");
            scope.retireOwner(second);
            var retired = new ParticleAPI(second).newCustomParticle(siblingTexture, FiguraVec3.of(0, 0, 0), null);
            require(!retired.isAlive(), "retired owner cannot reactivate effects from a retained frame");
            client.level = null;
            separate.tick();
            require(!c.isAlive() && separate.liveCount() == 0, "world/session loss retires the independent preview scope");
            client.level = world;
            scope.close();
            require(live.active(), "closing the preview leaves the live world's particle untouched");
        } finally {
            client.level = world;
            live.remove();
            cachedBeforeBinding.remove();
            first.clean();
            second.clean();
            anotherPreview.clean();
            CustomParticleRuntime.finishFrame();
            texture.close();
            siblingTexture.close();
            otherTexture.close();
            worldTexture.close();
            drain.run();
        }
        System.out.println("PASS isolated preview particle lifecycle: public construction, no world engine/light, private budgets, "
                + "tick/frame callbacks, native expiry, reload/retirement/session cleanup, frozen native GUI geometry; pixels MANUAL/PENDING");
    }

    private static OutfitLimits limits() {
        var gson = new Gson();
        var json = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxCustomParticles", 2);
        json.addProperty("maxDynamicLights", 1);
        json.addProperty("maxParticleCallbacksPerTick", 1);
        return gson.fromJson(json, OutfitLimits.class);
    }

    private static void preparationFence(Avatar worldOwner, CustomParticle live, Runnable drain,
                                          IntSupplier worldSubmissions) throws Exception {
        Avatar prepared = Avatar.createPlayerModelPreview(worldOwner.owner);
        var texture = new FiguraTexture(prepared, "preparation-fence", new NativeImage(1, 1, true));
        var pending = new CustomParticle(prepared, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
        try {
            int submitted = worldSubmissions.getAsInt();
            require(prepared.usesPreviewParticles() && !prepared.isLibraryPreview
                            && prepared.permissions == top.aihmc.omnimorph.permissions.PermissionManager.get(worldOwner.owner),
                    "player model copy preserves normal player permissions and fences particles before loading");
            require(!pending.activate(), "an unbound player model copy cannot activate a custom world particle");
            var suppressed = new ParticleAPI(prepared).__index("minecraft:smoke").spawn();
            require(!suppressed.isAlive() && !new ParticleAPI(prepared).isPresent("minecraft:smoke")
                            && worldSubmissions.getAsInt() == submitted,
                    "preparation invokes no native particle factory or world submission");
            prepared.clearParticles();
            prepared.resourceReloadEvent();
            require(live.active(), "unbound preview clear/reload preserves the same-UUID world particle");
            var invalid = new CompoundTag();
            var metadata = new CompoundTag();
            metadata.putString("format", "ysm-native");
            invalid.put("metadata", metadata);
            invalid.put("ysm", new CompoundTag());
            prepared.load(invalid);
            drain.run();
            require(prepared.nbt == null && live.active(),
                    "actual failed Avatar preparation and its queued cleanup preserve world ownership");
        } finally {
            prepared.clean();
            pending.remove();
            texture.close();
            drain.run();
        }
        require(live.active(), "repeated cleanup of an unbound preview cannot clear the world's particle");
    }

    private static Avatar owner(UUID identity) throws Exception {
        Avatar owner = Avatar.createParticlePreview(identity, "isolated particle fixture");
        owner.load(new CompoundTag());
        owner.initializePreviewPlayer(null);
        owner.permissions.insert(Permissions.TICK_INST, 4096, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        owner.permissions.insert(Permissions.RENDER_INST, 4096, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        owner.permissions.insert(Permissions.COMPLEXITY, 10000, top.aihmc.omnimorph.FiguraMod.MOD_ID);
        var runtime = new FiguraLuaRuntime(owner, Map.of());
        require(runtime.init(new ListTag()), "preview fixture Lua initializes through native runtime");
        return owner;
    }

    private static void callback(Avatar owner, AtomicInteger hits, String name) {
        owner.luaRuntime.setGlobal(name, new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                hits.incrementAndGet();
                return LuaValue.NIL;
            }
        });
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected isolated preview capability rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static byte[] vertices(PreviewParticleFrame frame, Matrix4f transform) {
        try (var storage = ByteBufferBuilder.exactlySized(frame.vertexCount() * DefaultVertexFormat.PARTICLE.getVertexSize())) {
            var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            frame.draw(transform, type -> builder);
            try (var mesh = builder.build()) {
                require(mesh != null && mesh.drawState().vertexCount() == frame.vertexCount(),
                        "panel replay emits the complete native PARTICLE vertex format");
                byte[] bytes = new byte[mesh.vertexBuffer().remaining()];
                mesh.vertexBuffer().get(bytes);
                return bytes;
            }
        }
    }

    private static void center(ByteBuffer data, int start, float x, float y, float z) {
        float[] center = new float[3];
        for (int vertex = start; vertex < start + 4; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                center[axis] += data.getFloat(vertex * DefaultVertexFormat.PARTICLE.getVertexSize() + axis * 4) / 4;
            }
        }
        require(Math.abs(center[0] - x) < .0001f && Math.abs(center[1] - y) < .0001f
                        && Math.abs(center[2] - z) < .0001f,
                "world position converts exactly once into the panel model space");
    }

    private static final class FixtureCamera extends Camera {
        @Override
        public Quaternionf rotation() {
            return new Quaternionf();
        }

        @Override
        public Vec3 position() {
            return Vec3.ZERO;
        }
    }
}
