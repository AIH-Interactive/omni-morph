package top.aihmc.omnimorph.model.outfit;

import com.google.gson.Gson;
import net.minecraft.world.phys.Vec3;

/** Native storage and ordering checks; complete world drawing is validated separately. */
public final class TrailBufferSmokeTest {
    public static void run() {
        var json = new Gson().toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxTrailSamples", 3);
        var limits = new Gson().fromJson(json, OutfitLimits.class);
        var buffer = new TrailBuffer(limits);
        var policy = new TrailBuffer.Policy(.1, 1, 12, 8);
        Object world = new Object();
        require(buffer.sample(1, world, "tip", 0, Vec3.ZERO, policy), "first world sample");
        require(!buffer.sample(2, world, "tip", .2, new Vec3(.01, 0, 0), policy), "small/early movement is not sampled");
        require(buffer.sample(3, world, "tip", 1, new Vec3(.01, 0, 0), policy), "tick interval samples slow movement");
        require(buffer.sample(4, world, "tip", 1.1, new Vec3(.2, 0, 0), policy), "distance samples before the interval");
        require(buffer.sample(5, world, "tip", 1.2, new Vec3(.4, 0, 0), policy), "ring overwrites its oldest element");
        var retained = buffer.snapshot();
        require(retained.size() == 3 && retained.getFirst().timeTicks() == 1,
                "negotiated capacity preserves chronological order");
        require(!buffer.sample(4, world, "tip", .5, new Vec3(999, 0, 0), policy), "late extracted frame cannot sample or teleport newer state");
        buffer.invalidate(4);
        require(buffer.snapshot().size() == 3, "late invalidation cannot erase a newer trajectory");
        buffer.expire(13.2);
        require(buffer.snapshot().isEmpty() && retained.size() == 3, "lifetime expiry does not mutate an extracted snapshot");
        buffer.sample(6, world, "tip", 14, Vec3.ZERO, policy);
        buffer.sample(7, world, "tip", 15, new Vec3(100, 0, 0), policy);
        require(buffer.snapshot().size() == 1 && buffer.snapshot().getFirst().x() == 100,
                "teleport clears the old map-spanning segment");
        buffer.sample(8, world, "hip", 16, new Vec3(100.1, 0, 0), policy);
        require(buffer.snapshot().size() == 1, "target fallback resets the old tip trajectory");
        buffer.sample(9, new Object(), "hip", 17, new Vec3(100.2, 0, 0), policy);
        require(buffer.snapshot().size() == 1, "new world identity resets history even at the same coordinates");
        buffer.advance(world, 18);
        require(buffer.snapshot().isEmpty(), "world switch clears history without requiring another render sample");
        buffer.sample(10, world, "tip", 20, Vec3.ZERO, policy);
        buffer.sample(11, world, "tip", 19, new Vec3(.2, 0, 0), policy);
        require(buffer.snapshot().size() == 1 && buffer.snapshot().getFirst().timeTicks() == 19,
                "a newer frame with a rewound world clock starts a fresh trajectory");
        buffer.invalidate(12);
        require(!buffer.sample(12, world, "tip", 20, Vec3.ZERO, policy), "invalidated frame cannot resurrect cleared history");
        buffer.sample(13, world, "tip", 20, Vec3.ZERO, policy);
        require(buffer.snapshot().size() == 1, "new generation sampling can resume after invalidation");
        try {
            buffer.sample(14, world, "tip", Double.NaN, Vec3.ZERO, policy);
            throw new AssertionError("non-finite clock accepted");
        } catch (IllegalArgumentException expected) {
            require(buffer.snapshot().size() == 1, "rejection keeps existing valid history");
        }
        try {
            new TrailBuffer.Policy(0, 0, 12, 8);
            throw new AssertionError("non-positive sampling interval accepted");
        } catch (IllegalArgumentException expected) {
            // Policy is supplied by Lua; native input checks still enforce finite valid values.
        }
        buffer.clear();
        var slow = new TrailBuffer.Policy(64, 1000, 1000, 8);
        for (int step = 0; step <= 64; step++) {
            buffer.sample(15 + step, world, "tip", step, new Vec3(step, 0, 0), slow);
        }
        require(buffer.snapshot().size() == 2 && buffer.snapshot().getFirst().x() == 0,
                "teleport detection uses consecutive observed positions rather than the previous sparse sample");
        json.addProperty("maxTrailSamples", 1);
        var single = new TrailBuffer(new Gson().fromJson(json, OutfitLimits.class));
        single.sample(1, world, "tip", 0, Vec3.ZERO, policy);
        single.sample(2, world, "tip", 1, new Vec3(1, 0, 0), policy);
        require(single.snapshot().size() == 1 && single.snapshot().getFirst().x() == 1,
                "small negotiated buffers work without exceeding their allocation");
        System.out.println("PASS native trail ring: negotiated capacity, distance/time, lifetime, late frames, teleport/world/target resets and finite policy");
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
