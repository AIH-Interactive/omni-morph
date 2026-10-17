package top.aihmc.omnimorph.model.ysm;

import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class YsmParticleRequestSmokeTest {
    public static void main(String[] args) throws Exception {
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        AtomicReference<YsmParticleRequest> captured = new AtomicReference<>();
        MolangBindings bindings = new MolangBindings();
        bindings.register("v", context.variables);
        bindings.register("particle_request", (ObjectBinding) name -> (Function) (ctx, arguments) -> {
            YsmParticleRequest request = YsmParticleRequest.parse(ctx, arguments, "absolute".equals(name));
            captured.set(request);
            return request != null;
        });
        MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
        parse(engine, context, "particle_request.relative('minecraft:flame');");
        YsmParticleRequest defaults = captured.get();
        require(defaults.count() == 0 && defaults.lifetime() == 20 && defaults.speed() == 0d,
                "reference defaults");
        List<YsmParticleRequest.Emission> emissions = new ArrayList<>();
        defaults.emit(new Vec3(10d, 20d, 30d), true, 90f, 0f,
                () -> { throw new AssertionError("single emission must not draw random numbers"); }, null, emissions::add);
        require(emissions.size() == 1, "count=0 emits one particle");
        vector(emissions.getFirst().position(), 10d, 20d, 30d, "default position");

        parse(engine, context, "particle_request.relative('flame',1,2,3,4,5,6,2,-9,0,v.extra=1);");
        YsmParticleRequest single = captured.get();
        require(single.count() == 0 && single.lifetime() == 1, "negative count/zero lifetime reference clamp");
        require(context.variables.getPath("extra") == null, "extra arguments ignored rather than evaluated");
        emissions.clear();
        single.emit(new Vec3(10d, 20d, 30d), true, 90f, 0f,
                () -> { throw new AssertionError("single has exact velocity"); }, null, emissions::add);
        vector(emissions.getFirst().position(), 7d, 22d, 31d, "player single uses body yaw");
        vector(emissions.getFirst().velocity(), 8d, 10d, 12d, "single delta times speed, unrotated");
        emissions.clear();
        single.emit(new Vec3(10d, 20d, 30d), false, 90f, 0f, () -> 0d, null, emissions::add);
        vector(emissions.getFirst().position(), 11d, 22d, 33d, "nonplayer single uses entity yaw");

        parse(engine, context, "particle_request.absolute('flame',1,2,3,4,5,6,2,2,40);");
        YsmParticleRequest batch = captured.get();
        double[] sequence = {1d, 2d, 3d, 4d, 5d, 6d, -1d, -2d, -3d, -4d, -5d, -6d};
        AtomicInteger cursor = new AtomicInteger();
        emissions.clear();
        batch.emit(new Vec3(10d, 20d, 30d), true, 90f, 0f,
                () -> sequence[cursor.getAndIncrement()], null, emissions::add);
        require(emissions.size() == 2 && cursor.get() == 12, "batch uses six Gaussian samples per particle");
        vector(emissions.getFirst().position(), 15d, 32d, 51d, "absolute offset still adds entity position");
        vector(emissions.getFirst().velocity(), 8d, 10d, 12d, "batch Gaussian velocity");
        vector(emissions.get(1).position(), 7d, 12d, 15d, "second independent batch sample");
        require(emissions.getFirst().lifetime() == 40, "captured lifetime retained");
        YsmParticleRequest relativeBatch = new YsmParticleRequest("flame", new Vec3(1d, 2d, 3d),
                Vec3.ZERO, 0d, 1, 20, false);
        emissions.clear();
        relativeBatch.emit(new Vec3(10d, 20d, 30d), true, 90f, 0f, () -> 0d, null, emissions::add);
        vector(emissions.getFirst().position(), 11d, 22d, 33d, "player batch deliberately uses entity yaw");

        require(!parse(engine, context, "particle_request.relative('',v.blank=1);"), "blank ID rejected");
        require(!parse(engine, context, "particle_request.relative(123,v.blank=1);"), "non-string ID rejected");
        require(context.variables.getPath("blank") == null, "invalid ID does not evaluate remaining parameters");
        try {
            parse(engine, context, "particle_request.relative('flame',0,0,0,0,0,0,0,1025);");
            throw new AssertionError("count budget must reject before emission");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("budget"), "bounded count failure");
        }
        MolangExecutionBudget budget = new MolangExecutionBudget(1);
        AtomicInteger emitted = new AtomicInteger();
        try (var scope = budget.beginFrame(100)) {
            try {
                batch.emit(Vec3.ZERO, false, 0f, 0f, () -> 0d, budget, value -> emitted.incrementAndGet());
                throw new AssertionError("batch must use shared budget");
            } catch (MolangExecutionBudget.Exceeded expected) {
                require(emitted.get() == 1, "exhaustion stops before next emission");
            }
        }
        System.out.println("M4 particle request parameters, reference coordinates/random order, count/lifetime defaults and bounded emission planning passed.");
    }

    private static boolean parse(MolangEngine engine, Avatar.MolangContext context, String expression) throws Exception {
        return ExpressionEvaluator.evaluator(context).evalAsBoolean(engine.parse(expression).getFirst());
    }

    private static void vector(Vec3 actual, double x, double y, double z, String message) {
        require(Math.abs(actual.x - x) < 0.00002d && Math.abs(actual.y - y) < 0.00002d
                && Math.abs(actual.z - z) < 0.00002d, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
