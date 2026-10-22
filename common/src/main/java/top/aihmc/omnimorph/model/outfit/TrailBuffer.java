package top.aihmc.omnimorph.model.outfit;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native bounded storage only. Lua chooses the target, sampling policy and effect lifecycle. */
public final class TrailBuffer {
    public record Snapshot(Object world, double timeTicks, List<PathSample> path) {
        public Snapshot {
            path = List.copyOf(path);
        }
    }

    public record Policy(double minDistance, double intervalTicks, double lifetimeTicks, double teleportDistance) {
        public Policy {
            OutfitConfig.require(Double.isFinite(minDistance) && minDistance >= 0
                    && Double.isFinite(intervalTicks) && intervalTicks > 0
                    && Double.isFinite(lifetimeTicks) && lifetimeTicks > 0
                    && Double.isFinite(teleportDistance) && teleportDistance > 0);
        }
    }

    public record Sample(double x, double y, double z, double timeTicks) {}

    public record PathSample(Sample sample, double distance) {
        public PathSample {
            OutfitConfig.require(sample != null && Double.isFinite(distance) && distance >= 0);
        }
    }

    private final Sample[] samples;
    private final double[] distances;
    private double totalDistance;
    private Vec3 lastAccepted;
    private int start;
    private int size;
    private long sequence = -1;
    private Object world;
    private Object target;
    private Vec3 observed;
    private double observedTime;
    private Policy policy;

    public TrailBuffer(OutfitLimits limits) {
        samples = new Sample[limits.maxTrailSamples()];
        distances = new double[samples.length];
    }

    public boolean sample(long frameSequence, Object world, Object target, double now, Vec3 position, Policy policy) {
        OutfitConfig.require(frameSequence >= 0 && world != null && target != null && Double.isFinite(now)
                && position != null && Double.isFinite(position.x) && Double.isFinite(position.y)
                && Double.isFinite(position.z) && policy != null);
        if (frameSequence <= sequence) {
            return false;
        }
        sequence = frameSequence;
        if (this.world != world || !Objects.equals(this.target, target) || now < observedTime
                || observed != null && observed.distanceTo(position) > policy.teleportDistance()) {
            clear();
        }
        this.world = world;
        this.target = target;
        this.policy = policy;
        observed = position;
        observedTime = now;
        expire(now);
        Sample previous = size == 0 ? null : samples[(start + size - 1) % samples.length];
        if (previous != null) {
            double distance = position.distanceTo(new Vec3(previous.x(), previous.y(), previous.z()));
            if ((distance == 0 || distance < policy.minDistance()) && now - previous.timeTicks() < policy.intervalTicks()) {
                return false;
            }
        }
        double distance = totalDistance + (lastAccepted == null ? 0 : position.distanceTo(lastAccepted));
        OutfitConfig.require(Double.isFinite(distance));
        if (size == samples.length) {
            samples[start] = null;
            start = (start + 1) % samples.length;
            size--;
        }
        samples[(start + size) % samples.length] = new Sample(position.x, position.y, position.z, now);
        distances[(start + size) % samples.length] = distance;
        totalDistance = distance;
        lastAccepted = position;
        size++;
        return true;
    }

    public void expire(double now) {
        OutfitConfig.require(Double.isFinite(now));
        while (size > 0 && policy != null && now - samples[start].timeTicks() >= policy.lifetimeTicks()) {
            samples[start] = null;
            start = (start + 1) % samples.length;
            size--;
        }
    }

    public void advance(Object currentWorld, double now) {
        if (world != null && world != currentWorld) {
            clear();
        } else {
            expire(now);
        }
    }

    /** Old extracted frames may draw, but cannot clear a newer trajectory. */
    public void invalidate(long frameSequence) {
        if (frameSequence >= sequence) {
            sequence = frameSequence;
            clear();
        }
    }

    public List<Sample> snapshot() {
        var result = new ArrayList<Sample>(size);
        for (int index = 0; index < size; index++) {
            result.add(samples[(start + index) % samples.length]);
        }
        return List.copyOf(result);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Read-only extraction: rendering never advances or recreates controller history. */
    public Snapshot snapshotAt(Object currentWorld, double now) {
        if (world == null || world != currentWorld || size == 0 || !Double.isFinite(now)
                || now - observedTime >= policy.lifetimeTicks()) {
            return null;
        }
        return new Snapshot(world, now, snapshotPath());
    }

    public void clear() {
        java.util.Arrays.fill(samples, null);
        start = 0;
        size = 0;
        world = null;
        target = null;
        observed = null;
        observedTime = 0;
        policy = null;
        totalDistance = 0;
        lastAccepted = null;
    }

    /** Absolute accepted-path distances survive ring eviction and expiry, so UV phase does not jump. */
    public List<PathSample> snapshotPath() {
        var result = new ArrayList<PathSample>(size);
        for (int index = 0; index < size; index++) {
            int slot = (start + index) % samples.length;
            result.add(new PathSample(samples[slot], distances[slot]));
        }
        return List.copyOf(result);
    }
}
