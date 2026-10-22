package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import top.aihmc.omnimorph.animation.Animation;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.lua.api.CosmeticAnimationsAPI;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;
import top.aihmc.omnimorph.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real Lua subscriptions, native Figura keyframe production and shared callback accounting. */
public final class CosmeticAnimationEventSmokeTest {
    public static void run() throws Exception {
        try (var fixture = new Fixture()) {
            UUID wearer = UUID.randomUUID();
            Avatar first = fixture.owner(wearer, 2);
            Avatar second = fixture.owner(wearer, 2);
            var firstEvents = listen(first, "hit(e)");
            var secondEvents = listen(second, "hit(e)");
            var animation = new Animation(first, "model", "sparkle", Animation.LoopMode.LOOP,
                    false, 1, 0, 1, 0, 0);
            animation.newCode(.25f, "local x=1");
            animation.play();
            animation.playCode(0, .5f);
            first.cosmeticAnimations.dispatch();
            require(firstEvents.isEmpty(), "a native marker never invokes Lua in its producing tick");
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            require(firstEvents.size() == 1 && secondEvents.isEmpty(), "native marker reaches only its execution instance: "
                    + firstEvents.size() + "/" + secondEvents.size() + ", error=" + first.cosmeticAnimations.getEventError()
                    + ", script=" + first.scriptError + ", remaining=" + first.tick.remaining);
            LuaTable event = firstEvents.getFirst();
            require(event.get("animation").tojstring().equals("model.sparkle")
                            && event.get("type").tojstring().equals("keyframe") && event.get("marker").tojstring().isEmpty()
                            && event.get("time").todouble() == .25 && event.get("generation").todouble() > 0,
                    "notification contains identity, kind, time and playback generation without source text");
            animation.playCode(0, .5f);
            animation.restart();
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            require(firstEvents.size() == 1, "restart invalidates queued notifications from an older playback");
            var valid = new AtomicBoolean(true);
            offer(first, "one", valid);
            offer(first, "two", valid);
            offer(first, "overflow", valid);
            require("ANIMATION_EVENT_QUEUE_LIMIT".equals(first.cosmeticAnimations.getEventError()),
                    "negotiated callback capacity bounds pending notifications");
            offer(second, "sibling", valid);
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            second.cosmeticAnimations.dispatch();
            require(firstEvents.size() == 3 && secondEvents.isEmpty()
                            && "ANIMATION_EVENT_CALLBACK_LIMIT".equals(second.cosmeticAnimations.getEventError()),
                    "duplicate executions share the wearer tick callback quota");
            listen(first, "hit(e); again()");
            first.luaRuntime.setGlobal("again", new org.luaj.vm2.lib.ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    offer(first, "reentrant", valid);
                    first.cosmeticAnimations.dispatch();
                    return LuaValue.NIL;
                }
            });
            offer(first, "outer", valid);
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            int remaining = first.tick.remaining;
            first.cosmeticAnimations.dispatch();
            require(first.tick.remaining == remaining && remaining < first.tick.max,
                    "reentrant dispatch waits for the next tick and actual Lua instructions are billed");
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            require(first.tick.remaining < first.tick.max, "a callback-produced marker can run in a later tick");
            first.cosmeticAnimations.onEvent(null);
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            require(first.tick.remaining == first.tick.max, "nil registration drops pending work and its closure");
            Avatar disabled = fixture.owner(UUID.randomUUID(), 0);
            try {
                listen(disabled, "hit(e)");
                throw new AssertionError("zero animation callback capacity accepted registration");
            } catch (LuaError expected) {
                // Registration through a direct API call below must preserve the disabled setting as well.
            }
            var goodEvents = listen(second, "hit(e)");
            offer(second, "revoked", valid);
            valid.set(false);
            fixture.tick();
            second.cosmeticAnimations.dispatch();
            require(goodEvents.isEmpty(), "source invalidation drops queued callbacks");
            valid.set(true);
            offer(second, "reload", valid);
            second.resourceReloadEvent();
            fixture.tick();
            second.cosmeticAnimations.dispatch();
            require(goodEvents.isEmpty(), "actual resource reload drops pending notifications");
            var completedEvents = listen(first, "hit(e)");
            var once = new Animation(first, "model", "once", Animation.LoopMode.ONCE,
                    false, 1, 0, 1, 0, 0);
            once.play();
            var time = Animation.class.getDeclaredField("time");
            time.setAccessible(true);
            time.setFloat(once, 2);
            var pause = Minecraft.class.getDeclaredField("pause");
            pause.setAccessible(true);
            boolean previousPause = pause.getBoolean(Minecraft.getInstance());
            try {
                // Freeze wall-clock advancement while executing the real native completion branch.
                pause.setBoolean(Minecraft.getInstance(), true);
                once.tick();
            } finally {
                pause.setBoolean(Minecraft.getInstance(), previousPause);
            }
            fixture.tick();
            first.cosmeticAnimations.dispatch();
            require(once.isStopped() && completedEvents.size() == 1
                            && completedEvents.getFirst().get("type").tojstring().equals("end")
                            && completedEvents.getFirst().get("time").todouble() == 1,
                    "native one-shot completion queues an end notification from the same playback");
        }
        try (var fixture = new Fixture()) {
            UUID wearer = UUID.randomUUID();
            Avatar runaway = fixture.owner(wearer, 2);
            Avatar healthy = fixture.owner(wearer, 2);
            for (Avatar owner : List.of(runaway, healthy)) {
                owner.permissions.insert(Permissions.TICK_INST, 64, top.aihmc.omnimorph.FiguraMod.MOD_ID);
            }
            listen(runaway, "while true do end");
            var hits = listen(healthy, "hit(e)");
            var valid = new AtomicBoolean(true);
            offer(runaway, "runaway", valid);
            offer(healthy, "healthy", valid);
            fixture.tick();
            runaway.cosmeticAnimations.dispatch();
            healthy.cosmeticAnimations.dispatch();
            require(runaway.scriptError && !healthy.scriptError && hits.isEmpty()
                            && runaway.tick.remaining == 0
                            && "ANIMATION_EVENT_INSTRUCTION_LIMIT".equals(healthy.cosmeticAnimations.getEventError()),
                    "infinite event Lua consumes the shared tick allowance and disables only its own execution");
            offer(healthy, "next", valid);
            fixture.tick();
            healthy.cosmeticAnimations.dispatch();
            require(hits.size() == 1, "healthy sibling resumes with next-tick instructions");
        }
        ysmSource();
        System.out.println("PASS cosmetic animation events: real Lua registration, native Figura/YSM sources, next-tick queue, "
                + "generation/instance isolation, shared count/instructions, bounded reentry and reload; game timing MANUAL/PENDING");
    }

    private static void ysmSource() throws Exception {
        try (var fixture = new Fixture()) {
            var data = new CompoundTag();
            var metadata = new CompoundTag();
            metadata.putString("format", "ysm-native");
            data.put("metadata", metadata);
            var model = new CompoundTag();
            model.putByteArray("main_model", """
                    {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},
                    "bones":[{"name":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]}]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            data.put("ysm", model);
            Avatar owner = fixture.owner(UUID.randomUUID(), 64, data);
            require(owner.isYsmNative(), "event source uses the actual YSM Avatar load/assembly path");
            var runtime = owner.getYsmRuntime();
            var player = runtime.animations();
            var events = listen(owner, "hit(e)");
            var engine = top.aihmc.omnimorph.molang.MolangEngine.fromCustomBinding(owner.getAvatarBindings());
            player.registerAnimations(top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser.parse("""
                    {"animations":{"notify":{"animation_length":1,"loop":false,
                      "timeline":{"0":"v.started=v.started+1;",
                        "0.25":{"event":"v.marked=v.marked+1;","marker":"sparkle"}},
                      "particle_effects":{"0.5":{"effect":"minecraft:smoke","marker":"puff"}}
                    }}}
                    """, engine));
            var evaluator = top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(owner.getMolangContext());
            var advance = player.getClass().getDeclaredMethod("advanceAnimationTimeline", float.class,
                    top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.class);
            advance.setAccessible(true);
            player.play("notify", false, 1);
            owner.cosmeticAnimations.dispatch();
            require(events.isEmpty(), "YSM initial timeline never invokes Lua from inside playback traversal");
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == 1 && events.getFirst().get("type").tojstring().equals("timeline")
                            && events.getFirst().get("marker").tojstring().isEmpty(),
                    "YSM initial event is delivered once without exposing its Molang expression");
            double generation = events.getFirst().get("generation").todouble();
            advance.invoke(player, .6f, evaluator);
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == 3 && events.get(1).get("marker").tojstring().equals("sparkle")
                            && events.get(2).get("type").tojstring().equals("particle")
                            && events.get(2).get("marker").tojstring().equals("puff")
                            && events.get(1).get("generation").todouble() == generation,
                    "real YSM crossing window delivers authored marker and particle event in order");
            require(evaluator.evalAsFloat(engine.parse("v.marked").getFirst()) == 1,
                    "the original Molang timeline still executes in the owning instance");
            advance.invoke(player, .6f, evaluator);
            advance.invoke(player, .3f, evaluator);
            require(player.getActiveAnimation("notify") == null, "native completed playback fades out before Lua delivery");
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == 4 && events.getLast().get("type").tojstring().equals("end")
                            && events.getLast().get("generation").todouble() == generation,
                    "natural removal preserves the one-shot end notification from the same playback");
            int delivered = events.size();
            player.play("notify", false, 1);
            player.stop("notify");
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == delivered, "explicit stop rejects queued events from a removed playback");
            player.play("notify", false, 1);
            player.play("notify", false, 1);
            advance.invoke(player, .3f, evaluator);
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == delivered + 1 && events.getLast().get("marker").tojstring().equals("sparkle")
                            && events.getLast().get("generation").todouble() > generation,
                    "reusing the native playback object invalidates old tickets and keeps the new generation");
            player.stop("notify");
            delivered = events.size();
            player.play("notify", false, 1);
            player.fadeOut("notify", .5f);
            advance.invoke(player, .3f, evaluator);
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == delivered, "explicit fade cancellation withdraws notifications while native pose fades");
            player.stop("notify");
            player.play("notify", top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME, 1, 0);
            advance.invoke(player, 1.2f, evaluator);
            advance.invoke(player, .3f, evaluator);
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            long ends = events.subList(delivered, events.size()).stream()
                    .filter(value -> value.get("type").tojstring().equals("end")).count();
            require(ends == 1, "holding the last YSM frame emits only one end notification");
            player.stop("notify");
            delivered = events.size();
            var hold = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME;
            player.playControllerAnimation("event_controller", "notify", hold, 1, 0);
            player.playControllerAnimation("event_controller", "notify", hold, 1, 0);
            advance.invoke(player, 1.2f, evaluator);
            player.playControllerAnimation("event_controller", "notify", hold, 1, 0);
            advance.invoke(player, .3f, evaluator);
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            var controllerEvents = events.subList(delivered, events.size());
            require(controllerEvents.size() == 4
                            && controllerEvents.stream().filter(value -> value.get("type").tojstring().equals("end")).count() == 1
                            && controllerEvents.stream().map(value -> value.get("generation").todouble()).distinct().count() == 1,
                    "continuous controller weight refresh preserves markers and emits only one hold completion");
            player.resetControllerAnimations("event_controller");
            delivered = events.size();
            player.playControllerAnimation("event_controller", "notify", hold, 1, 0);
            player.resetControllerAnimations("event_controller");
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == delivered, "controller reset invalidates its queued initial notification");
            delivered = events.size();
            player.play("notify", false, 1);
            player.applyRemotePlaybacks(List.of(), List.of(), List.of());
            fixture.tick();
            owner.cosmeticAnimations.dispatch();
            require(events.size() == delivered, "observer projection does not replay unapproved local source events");
        }
    }

    private static List<LuaTable> listen(Avatar owner, String body) {
        var events = new ArrayList<LuaTable>();
        owner.luaRuntime.setGlobal("hit", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                events.add(arg.checktable());
                return LuaValue.NIL;
            }
        });
        var callback = owner.luaRuntime.load("animation callback", "return function(e) " + body + " end").call().checkfunction();
        var wrapper = owner.luaRuntime.typeManager.javaToLua(owner.cosmeticAnimations).arg1();
        wrapper.get("onEvent").call(wrapper, callback);
        return events;
    }

    private static void offer(Avatar owner, String marker, AtomicBoolean valid) {
        CosmeticAnimationsAPI.offer(owner, "animation.test", "marker", marker, .5, 1, valid::get);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final List<Avatar> owners = new ArrayList<>();
        private final OutfitInstructionBudget budget = new OutfitInstructionBudget(() -> null);

        private Avatar owner(UUID wearer, int callbacks) {
            return owner(wearer, callbacks, new CompoundTag());
        }

        private Avatar owner(UUID wearer, int callbacks, CompoundTag data) {
            var gson = new Gson();
            var limitsJson = gson.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
            limitsJson.addProperty("maxParticleCallbacksPerTick", callbacks);
            var limits = gson.fromJson(limitsJson, OutfitLimits.class);
            var format = data.contains("ysm") ? OutfitConfig.Format.YSM_PLAIN : OutfitConfig.Format.FIGURA;
            var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                    null, null, "0".repeat(64), format);
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                    OutfitConfig.AnchorRef.authorDefault("back_anchor"), OutfitConfig.Transform.identity(),
                    List.of(), Map.of(), Map.of());
            var context = new CosmeticInstanceContext(wearer, UUID.randomUUID(), wearer, instance, limits,
                    CosmeticInstanceContext.Scope.OWNER);
            context.bindInstructionBudget(budget);
            Avatar template = Avatar.createLibraryPreview(UUID.randomUUID(), "animation event permissions");
            owners.add(template);
            Avatar owner = Avatar.createCosmetic(context, template.permissions, "animation events");
            owners.add(owner);
            owner.load(data);
            owner.initializePreviewPlayer(null);
            owner.permissions.insert(Permissions.TICK_INST, 4096, top.aihmc.omnimorph.FiguraMod.MOD_ID);
            var runtime = new FiguraLuaRuntime(owner, Map.of());
            require(runtime.init(new ListTag()), "actual event Lua runtime initializes");
            return owner;
        }

        private void tick() {
            CustomParticleRuntime.beginTick();
            budget.beginTick();
            owners.forEach(owner -> owner.tick.reset(owner.permissions.get(Permissions.TICK_INST)));
        }

        @Override
        public void close() {
            owners.forEach(Avatar::clean);
        }
    }
}
