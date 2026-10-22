package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;
import sun.misc.Unsafe;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;
import top.aihmc.omnimorph.lua.api.particle.LuaParticle;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Figura VM hooks and native particle lifecycle; no game-window or GPU assertions. */
public final class OutfitParticleCallbackSmokeTest {
    public static void run(Unsafe unsafe) throws Exception {
        try (var sounds = YsmMaterialNativeSmokeTest.recordingSoundManager(unsafe)) {
            ordinaryCallbacks();
            sharedCounts();
            instructionLimitAndIsolation();
            registrationDuringExecution();
            CosmeticAnimationEventSmokeTest.run();
        }
        System.out.println("PASS particle callbacks: real Lua tick/frame hooks, shared count/instruction quotas, "
                + "epoch/reentry guards, zero limits, removal and VM failure isolation; game MANUAL/PENDING");
    }

    private static void ordinaryCallbacks() throws Exception {
        try (var fixture = new Fixture()) {
            Avatar owner = fixture.ordinary();
            var particle = fixture.particle(owner);
            var hits = new AtomicInteger();
            owner.luaRuntime.setGlobal("hit", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    hits.incrementAndGet();
                    particle.tick();
                    CustomParticleRuntime.runFrameCallbacks(.5f);
                    return LuaValue.NIL;
                }
            });
            require(OutfitInstructionBudget.current(owner) == null, "geometry alone keeps original ordinary Lua behavior");
            var wrapper = owner.luaRuntime.typeManager.javaToLua(new LuaParticle("callback", particle, owner)).arg1();
            wrapper.get("setTickCallback").call(wrapper, function(owner, "hit(); local p,dt=...; p:setRotation(45*dt)"));
            require(OutfitInstructionBudget.current(owner) != null, "explicit active callback adopts a shared instruction ledger");
            CustomParticleRuntime.beginTick();
            particle.tick();
            particle.tick();
            require(hits.get() == 1 && particle.rotation() == 45 && owner.tick.remaining < owner.tick.max,
                    "real Lua executes once per logical tick, bills instructions and blocks native recursive tick");
            CustomParticleRuntime.beginTick();
            particle.tick();
            require(hits.get() == 2, "next logical tick replenishes callback identity");
            wrapper.get("setTickCallback").call(wrapper, LuaValue.NIL);
            wrapper.get("setFrameCallback").call(wrapper, function(owner, "hit(); local p,dt=...; p:setRotation(180*dt)"));
            owner.render.reset(0);
            YsmRenderFrameClock.beginFrame();
            CustomParticleRuntime.runFrameCallbacks(.5f);
            CustomParticleRuntime.runFrameCallbacks(.75f);
            require(hits.get() == 3 && particle.rotation() == 90 && owner.render.remaining > 0,
                    "hidden owner gets a fresh frame budget; repeated extraction and callback reentry do not repeat Lua");
            int used = owner.render.max - owner.render.remaining;
            owner.render.reset(owner.render.max);
            require(OutfitInstructionBudget.current(owner).allowance(owner, owner.render) == owner.render.max - used,
                    "individual reset cannot refund callback instructions");
            particle.remove();
            require(OutfitInstructionBudget.current(owner) != null, "removal retains the spent instruction ledger through frame retirement");
            CustomParticleRuntime.finishFrame();
            require(OutfitInstructionBudget.current(owner) == null, "ordinary owner releases callback ledger after its final frame");
        }
    }

    private static void sharedCounts() throws Exception {
        try (var fixture = new Fixture()) {
            UUID wearer = UUID.randomUUID();
            var budget = new OutfitInstructionBudget(() -> null);
            Avatar first = fixture.cosmetic(wearer, limits(1, 1), budget);
            Avatar second = fixture.cosmetic(wearer, limits(1, 1), budget);
            var a = fixture.particle(first);
            var b = fixture.particle(second);
            var hits = new AtomicInteger();
            probe(first, hits);
            probe(second, hits);
            a.setTickCallback(function(first, "hit()"));
            b.setTickCallback(function(second, "hit()"));
            CustomParticleRuntime.beginTick();
            a.tick();
            b.tick();
            require(hits.get() == 1 && "CALLBACK_TICK_LIMIT".equals(b.getCallbackError()) && b.active(),
                    "two execution instances compete for one wearer tick callback without killing native lifetime");
            a.remove();
            var c = fixture.particle(first);
            c.setTickCallback(function(first, "hit()"));
            c.tick();
            require(hits.get() == 1 && "CALLBACK_TICK_LIMIT".equals(c.getCallbackError()),
                    "new registration after removal cannot refund the same tick's count");
            CustomParticleRuntime.beginTick();
            c.tick();
            require(hits.get() == 2 && c.getCallbackError() == null, "next tick replenishes the shared count");
            b.setFrameCallback(function(second, "hit()"));
            c.setFrameCallback(function(first, "hit()"));
            YsmRenderFrameClock.beginFrame();
            CustomParticleRuntime.runFrameCallbacks(.25f);
            require(hits.get() == 3 && "CALLBACK_FRAME_LIMIT".equals(c.getCallbackError()),
                    "frame callback count is shared independently of the tick counter");
            CustomParticleRuntime.runFrameCallbacks(.75f);
            require(hits.get() == 3, "another extraction in the retained frame has no author effects");
            Avatar disabled = fixture.cosmetic(UUID.randomUUID(), limits(0, 0), new OutfitInstructionBudget(() -> null));
            var off = fixture.particle(disabled);
            reject(() -> off.setTickCallback(function(disabled, "return")));
            reject(() -> off.setFrameCallback(function(disabled, "return")));
            require(!OutfitInstructionBudget.hasParticleCallbacks(disabled), "zero limits reject before binding callback resources");
        }
    }

    private static void instructionLimitAndIsolation() throws Exception {
        try (var fixture = new Fixture()) {
            var budget = new OutfitInstructionBudget(() -> null);
            UUID wearer = UUID.randomUUID();
            Avatar first = fixture.cosmetic(wearer, limits(64, 32), budget);
            Avatar second = fixture.cosmetic(wearer, limits(64, 32), budget);
            for (Avatar owner : List.of(first, second)) {
                owner.permissions.insert(Permissions.RENDER_INST, 64, FiguraMod.MOD_ID);
                owner.render.reset(64);
            }
            var healthy = fixture.particle(first);
            var runaway = fixture.particle(second);
            healthy.setFrameCallback(function(first, "local n=0 for i=1,4 do n=n+i end"));
            runaway.setFrameCallback(function(second, "while true do end"));
            YsmRenderFrameClock.beginFrame();
            CustomParticleRuntime.runFrameCallbacks(.5f);
            require(second.scriptError && !first.scriptError && healthy.active() && !runaway.active(),
                    "runaway actual Lua VM stops and clears only its execution, preserving the healthy instance");
            require((64 - first.render.remaining) + (64 - second.render.remaining) == 64,
                    "failed instructions consume the remaining shared frame allowance");
            first.render.reset(64);
            require(budget.allowance(first, first.render) == 0, "counter reset cannot bypass a sibling's consumed instructions");
            YsmRenderFrameClock.beginFrame();
            CustomParticleRuntime.runFrameCallbacks(.5f);
            require(!first.scriptError && healthy.active() && healthy.getCallbackError() == null,
                    "healthy execution resumes when a new frame replenishes the budget");
        }
    }

    private static void probe(Avatar owner, AtomicInteger hits) {
        owner.luaRuntime.setGlobal("hit", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                hits.incrementAndGet();
                return LuaValue.NIL;
            }
        });
    }

    private static void registrationDuringExecution() throws Exception {
        try (var fixture = new Fixture()) {
            Avatar owner = fixture.ordinary();
            var particle = fixture.particle(owner);
            var runtime = owner.luaRuntime;
            runtime.setGlobal("p", new LuaParticle("callback registration", particle, owner));
            var beforeNested = new AtomicInteger();
            var insideNested = new AtomicInteger();
            runtime.setGlobal("mark", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    insideNested.set(runtime.getInstructions());
                    return LuaValue.NIL;
                }
            });
            var child = function(owner, "mark(); local n=0 for i=1,4 do n=n+i end");
            runtime.setGlobal("nested", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    beforeNested.set(runtime.getInstructions());
                    return runtime.run(child, owner.tick).arg1();
                }
            });
            owner.permissions.insert(Permissions.TICK_INST, 256, FiguraMod.MOD_ID);
            owner.tick.reset(256);
            CustomParticleRuntime.beginTick();
            var parent = function(owner, "local n=0 for i=1,8 do n=n+i end; "
                    + "p:setTickCallback(function() return end); nested()");
            require(runtime.run(parent, owner.tick) != null && insideNested.get() > beforeNested.get(),
                    "first registration inside an ordinary VM run retains the outer hook for nested calls");
            require(owner.tick.remaining == 256 - runtime.getInstructions()
                            && OutfitInstructionBudget.current(owner).allowance(owner, owner.tick) == owner.tick.remaining,
                    "outer work before registration and nested work are charged exactly once");
            Avatar bad = fixture.ordinary();
            var badParticle = fixture.particle(bad);
            bad.luaRuntime.setGlobal("p", new LuaParticle("callback registration runaway", badParticle, bad));
            bad.permissions.insert(Permissions.TICK_INST, 128, FiguraMod.MOD_ID);
            bad.tick.reset(128);
            var runaway = function(bad, "p:setTickCallback(function() return end); while true do end");
            require(bad.luaRuntime.run(runaway, bad.tick) == null && bad.scriptError && !badParticle.active(),
                    "mid-run registration does not let an outer runaway escape the existing VM hook");
            require(bad.tick.remaining == 0 && OutfitInstructionBudget.current(bad).allowance(bad, bad.tick) == 0,
                    "failed outer work after registration is still billed to the callback ledger");
            Avatar initializing = fixture.ordinary();
            var initialParticle = fixture.particle(initializing);
            initializing.permissions.insert(Permissions.INIT_INST, 256, FiguraMod.MOD_ID);
            initializing.init.reset(256);
            var initial = new FiguraLuaRuntime(initializing, Map.of("main",
                    "p:setTickCallback(function() return end); local n=0 for i=1,8 do n=n+i end"));
            initial.setGlobal("p", new LuaParticle("init callback", initialParticle, initializing));
            initial.setInstructionLimit(256);
            require(initial.init(null) && initializing.init.remaining < 256,
                    "first callback registration during initialization charges the outer initialization hook");
            require(OutfitInstructionBudget.current(initializing).allowance(initializing, initializing.init)
                            == initializing.init.remaining,
                    "initialization registration imports no duplicate or missing instructions");
        }
    }

    private static LuaFunction function(Avatar owner, String body) {
        return owner.luaRuntime.load("particle callback fixture", body).checkfunction();
    }

    private static OutfitLimits limits(int tick, int frame) {
        var gson = new Gson();
        var json = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxParticleCallbacksPerTick", tick);
        json.addProperty("maxParticleCallbacksPerFrame", frame);
        return gson.fromJson(json, OutfitLimits.class);
    }

    private static void reject(Runnable call) {
        try {
            call.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected callback registration to fail");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final List<Avatar> owners = new ArrayList<>();
        private final List<FiguraTexture> textures = new ArrayList<>();

        private Avatar ordinary() throws Exception {
            var owner = Avatar.createLibraryPreview(UUID.randomUUID(), "callback owner");
            var preview = Avatar.class.getDeclaredField("isLibraryPreview");
            preview.setAccessible(true);
            preview.setBoolean(owner, false);
            return prepare(owner);
        }

        private Avatar cosmetic(UUID wearer, OutfitLimits limits, OutfitInstructionBudget budget) throws Exception {
            var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                    null, null, "0".repeat(64), OutfitConfig.Format.FIGURA);
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                    OutfitConfig.AnchorRef.authorDefault("back_anchor"), OutfitConfig.Transform.identity(),
                    List.of(), Map.of(), Map.of());
            var context = new CosmeticInstanceContext(wearer, UUID.randomUUID(), wearer, instance, limits,
                    CosmeticInstanceContext.Scope.OWNER);
            context.bindInstructionBudget(budget);
            var template = Avatar.createLibraryPreview(UUID.randomUUID(), "callback permissions");
            owners.add(template);
            return prepare(Avatar.createCosmetic(context, template.permissions, "callback instance"));
        }

        private Avatar prepare(Avatar owner) {
            owners.add(owner);
            owner.load(new CompoundTag());
            owner.permissions.insert(Permissions.TICK_INST, 4096, FiguraMod.MOD_ID);
            owner.permissions.insert(Permissions.RENDER_INST, 4096, FiguraMod.MOD_ID);
            var runtime = new FiguraLuaRuntime(owner, Map.of());
            require(runtime.init(new ListTag()), "actual callback VM initializes");
            return owner;
        }

        private CustomParticle particle(Avatar owner) {
            var texture = new FiguraTexture(owner, "callback", new NativeImage(1, 1, true));
            textures.add(texture);
            var particle = new CustomParticle(owner, Minecraft.getInstance().level, texture, Vec3.ZERO, Vec3.ZERO);
            particle.setLifetime(100);
            require(particle.activate(), "callback fixture activates in the native world");
            return particle;
        }

        @Override
        public void close() {
            for (Avatar owner : owners) {
                CustomParticleRuntime.clearOwner(owner.owner);
                owner.clean();
            }
            CustomParticleRuntime.finishFrame();
            textures.forEach(FiguraTexture::close);
        }
    }
}
