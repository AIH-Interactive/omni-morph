package top.aihmc.omm.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.joml.Vector3d;
import org.luaj.vm2.LuaError;

/** Instance-local particle host for the preview's simulated air, optional water and flat ground. */
public final class PreviewParticles {
    static final Set<String> TYPES =
        Set.of("cherry_leaves", "firework", "campfire_signal_smoke", "bubble_column_up", "dragon_breath", "squid_ink");
    private final List<Particle> active = new ArrayList<>();
    private final Random random;
    private double groundY = Double.NEGATIVE_INFINITY;
    private final boolean water;
    private long spawned;

    PreviewParticles(long seed, boolean water) {
        random = new Random(seed);
        this.water = water;
    }

    void ground(double y) {
        groundY = y;
    }

    public boolean isPresent(String name) {
        return name != null && TYPES.contains(normalize(name));
    }

    private static String normalize(String name) {
        return name != null && name.startsWith("minecraft:") ? name.substring(10) : name;
    }

    public Particle create(String name, double x, double y, double z, double vx, double vy, double vz) {
        String type = normalize(name);
        if (!isPresent(type)) {
            throw new LuaError("Unsupported standalone particle: " + name);
        }
        return new Particle(type, x, y, z, vx, vy, vz);
    }

    public void spawn(Particle particle) {
        if (!particle.spawned) {
            particle.spawned = true;
            active.add(particle);
            spawned++;
        }
    }

    public void clear() {
        active.forEach(Particle::remove);
        active.clear();
    }

    void tick() {
        for (Particle particle : active) {
            particle.tick();
        }
        active.removeIf(particle -> !particle.isAlive());
    }

    List<Particle> active() {
        return active;
    }

    long spawned() {
        return spawned;
    }

    /** Mutable backing for the original Figura LuaParticle wrapper; all velocities are blocks per tick. */
    public final class Particle {
        final String type;
        final Vector3d position = new Vector3d();
        final Vector3d previous = new Vector3d();
        final Vector3d velocity = new Vector3d();
        float red = 1, green = 1, blue = 1, alpha = 1;
        float gravity;
        float friction = 0.98f;
        float quadSize = 0.1f;
        float width = 0.2f;
        int lifetime = 40;
        int age;
        boolean physics = true;
        boolean removed;
        boolean spawned;
        final int sprite;
        final double phase;

        private Particle(String type, double x, double y, double z, double vx, double vy, double vz) {
            this.type = type;
            position.set(x, y, z);
            previous.set(position);
            velocity.set(vx, vy, vz);
            phase = random.nextDouble() * Math.PI * 2;
            sprite = random.nextInt(type.equals("cherry_leaves") ? 12 : type.equals("campfire_signal_smoke") ? 12 : 1);
            switch (type) {
                case "cherry_leaves" -> {
                    gravity = 0.07f;
                    friction = 1;
                    lifetime = 300;
                    quadSize = random.nextBoolean() ? 0.05f : 0.075f;
                }
                case "firework" -> {
                    gravity = 0.1f;
                    friction = 0.91f;
                    quadSize = 0.075f;
                    lifetime = 48 + random.nextInt(12);
                }
                case "campfire_signal_smoke" -> {
                    gravity = 0.000003f;
                    quadSize = 0.3f;
                    width = 0.25f;
                    alpha = 0.95f;
                    lifetime = 280 + random.nextInt(50);
                    velocity.add(0, random.nextFloat() / 500f, 0);
                }
                case "bubble_column_up" -> {
                    gravity = -0.125f;
                    friction = 0.85f;
                    width = 0.02f;
                    quadSize *= random.nextFloat() * 0.6f + 0.2f;
                    velocity.mul(0.2).add((random.nextFloat() * 2 - 1) * 0.02, (random.nextFloat() * 2 - 1) * 0.02,
                        (random.nextFloat() * 2 - 1) * 0.02);
                    lifetime = (int) (40 / (random.nextFloat() * 0.8 + 0.2));
                }
                case "dragon_breath" -> {
                    friction = 0.96f;
                    physics = false;
                    quadSize *= 0.75f;
                    red = 0.717f + random.nextFloat() * 0.157f;
                    green = 0;
                    blue = 0.823f + random.nextFloat() * 0.153f;
                    lifetime = (int) (20 / (random.nextFloat() * 0.8 + 0.2));
                }
                case "squid_ink" -> {
                    friction = 0.92f;
                    physics = false;
                    quadSize = 0.5f;
                    red = green = blue = 0;
                    lifetime = (int) (6 / (random.nextFloat() * 0.8 + 0.2));
                }
                default -> throw new AssertionError(type);
            }
        }

        void tick() {
            previous.set(position);
            if (age++ >= lifetime || (type.equals("bubble_column_up") && !water)) {
                remove();
                return;
            }
            if (type.equals("cherry_leaves")) {
                // A deterministic wind in the standalone world; no live Minecraft biome/weather source.
                velocity.add(Math.cos(phase + age * 0.03) * 0.0005, 0, Math.sin(phase + age * 0.03) * 0.0005);
            }
            if (type.equals("campfire_signal_smoke")) {
                velocity.add((random.nextBoolean() ? 1 : -1) * random.nextFloat() / 5000, 0,
                    (random.nextBoolean() ? 1 : -1) * random.nextFloat() / 5000);
            }
            velocity.y -= 0.04 * gravity;
            position.add(velocity);
            if (physics && position.y <= groundY) {
                position.y = groundY;
                velocity.y = 0;
                velocity.x *= 0.7;
                velocity.z *= 0.7;
                if (type.equals("cherry_leaves")) {
                    remove();
                }
            }
            velocity.mul(friction);
            if (type.equals("squid_ink")) {
                if (age > lifetime / 2) {
                    alpha = 1 - (float) (age - lifetime / 2) / lifetime;
                }
                if (position.y > groundY) {
                    velocity.y -= 0.0074;
                }
            } else if (type.equals("firework") && age > lifetime / 2) {
                alpha = 1 - (float) (age - lifetime / 2) / lifetime;
            } else if (type.equals("campfire_signal_smoke") && age >= lifetime - 60) {
                alpha = Math.max(0, alpha - 0.015f);
            }
        }

        int spriteIndex(int count) {
            return type.equals("cherry_leaves") || type.equals("campfire_signal_smoke")
                ? sprite % count
                : Math.min(count - 1, age * count / Math.max(1, lifetime));
        }

        public void remove() {
            removed = true;
        }
        public boolean isAlive() {
            return !removed;
        }
        public double getX() {
            return position.x;
        }
        public double getY() {
            return position.y;
        }
        public double getZ() {
            return position.z;
        }
        public double getXd() {
            return velocity.x;
        }
        public double getYd() {
            return velocity.y;
        }
        public double getZd() {
            return velocity.z;
        }
        public void setPos(double x, double y, double z) {
            position.set(x, y, z);
        }
        public void setXo(double x) {
            previous.x = x;
        }
        public void setYo(double y) {
            previous.y = y;
        }
        public void setZo(double z) {
            previous.z = z;
        }
        public void setParticleSpeed(double x, double y, double z) {
            velocity.set(x, y, z);
        }
        public float getRCol() {
            return red;
        }
        public float getGCol() {
            return green;
        }
        public float getBCol() {
            return blue;
        }
        public float getAlpha() {
            return alpha;
        }
        public void setColor(float r, float g, float b) {
            red = r;
            green = g;
            blue = b;
        }
        public void setParticleAlpha(float value) {
            alpha = value;
        }
        public int getLifetime() {
            return lifetime;
        }
        public void setLifetime(int value) {
            lifetime = value;
        }
        public float getBbWidth() {
            return width;
        }
        public float getGravity() {
            return gravity;
        }
        public void setGravity(float value) {
            gravity = value;
        }
        public boolean getHasPhysics() {
            return physics;
        }
        public void setHasPhysics(boolean value) {
            physics = value;
        }
        public void fixQuadSize() {
            quadSize = 0.2f;
        }

        public void scale(float value) {
            width *= value;
            quadSize *= value;
        }

        public void setPower(float value) {
            velocity.x *= value;
            velocity.y = (velocity.y - 0.1) * value + 0.1;
            velocity.z *= value;
        }
    }
}
