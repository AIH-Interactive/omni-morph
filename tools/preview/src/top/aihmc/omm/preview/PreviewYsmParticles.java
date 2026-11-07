package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.Random;
import org.joml.Vector3d;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;

/** YsmParticleRequest emission rules backed by the standalone particle simulation and renderer. */
final class PreviewYsmParticles implements AutoCloseable {
    final PreviewParticles particles;
    private final PreviewMolang host;
    private final Vector3d position = new Vector3d();
    private final Random random;
    private final float bodyYaw;
    private final float entityYaw;
    private int tick;
    private boolean closed;

    PreviewYsmParticles(PreviewMolang host, JsonObject states, long seed) {
        this.host = host;
        random = new Random(seed);
        var player = states.has("player") ? states.getAsJsonObject("player") : new JsonObject();
        if (player.has("pos")) {
            var coordinates = player.getAsJsonArray("pos");
            if (coordinates.size() != 3)
                throw new IllegalArgumentException("states.player.pos must contain three coordinates");
            position.set(
                coordinates.get(0).getAsDouble(), coordinates.get(1).getAsDouble(), coordinates.get(2).getAsDouble());
        }
        entityYaw = angle(player, "yaw");
        bodyYaw = player.has("bodyYaw") ? angle(player, "bodyYaw") : entityYaw;
        if (!position.isFinite())
            throw new IllegalArgumentException("states.player.pos must be finite");
        boolean water = false;
        if (states.has("particles") && states.getAsJsonObject("particles").has("water")) {
            var value = states.getAsJsonObject("particles").get("water");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("states.particles.water must be boolean");
            water = value.getAsBoolean();
        }
        particles = new PreviewParticles(seed, water);
    }

    private static float angle(JsonObject player, String name) {
        float value = player.has(name) ? player.get(name).getAsFloat() : 0;
        if (!Float.isFinite(value))
            throw new IllegalArgumentException("states.player." + name + " must be finite");
        return value;
    }

    static Function binding(boolean absolute) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> execution, ArgumentCollection args) {
                var host = (PreviewMolang) execution.entity();
                if (host.effects == null)
                    throw new UnsupportedOperationException("YSM particles require a loaded model effects host");
                return host.effects.particles.execute(execution, args, absolute);
            }

            @Override
            public boolean validateArgumentSize(int size) {
                return size >= 1;
            }
        };
    }

    private Object execute(ExecutionContext<?> execution, Function.ArgumentCollection args, boolean absolute) {
        if (closed || !host.functions.clientEffectsEnabled())
            return null;
        String id = args.getAsString(execution, 0);
        if (id == null || id.isBlank())
            return false;
        var offset = new Vector3d(number(execution, args, 1), number(execution, args, 2), number(execution, args, 3));
        var delta = new Vector3d(number(execution, args, 4), number(execution, args, 5), number(execution, args, 6));
        double speed = number(execution, args, 7);
        int count = args.size() > 8 ? Math.max(args.getAsInt(execution, 8), 0) : 0;
        int lifetime = args.size() > 9 ? Math.max(args.getAsInt(execution, 9), 1) : 20;
        if (!offset.isFinite() || !delta.isFinite() || !Double.isFinite(speed))
            throw new IllegalArgumentException("Invalid YSM particle parameters");
        if (count > 1024)
            throw new IllegalArgumentException("YSM particle count/lifetime budget exceeded");
        if (!particles.isPresent(id))
            throw new UnsupportedOperationException("Unsupported standalone YSM particle: " + id);
        for (int index = 0; index < (count == 0 ? 1 : count); index++) {
            host.executionBudget().consume();
            var emittedPosition = new Vector3d(offset);
            Vector3d velocity;
            if (count == 0) {
                velocity = new Vector3d(delta).mul(speed);
            } else {
                emittedPosition.add(
                    random.nextGaussian() * delta.x, random.nextGaussian() * delta.y, random.nextGaussian() * delta.z);
                velocity = new Vector3d(
                    random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
            }
            if (!absolute) {
                // Preserve Vec3.yRot signs: x*cos + z*sin, z*cos - x*sin.
                float radians = -(count == 0 ? bodyYaw : entityYaw) * 0.017453292f;
                float cosine = (float) Math.cos(radians);
                float sine = (float) Math.sin(radians);
                double x = emittedPosition.x * cosine + emittedPosition.z * sine;
                double z = emittedPosition.z * cosine - emittedPosition.x * sine;
                emittedPosition.x = x;
                emittedPosition.z = z;
            }
            emittedPosition.add(position);
            if (!emittedPosition.isFinite() || !velocity.isFinite())
                throw new IllegalArgumentException("Invalid YSM particle emission");
            var particle = particles.create(
                id, emittedPosition.x, emittedPosition.y, emittedPosition.z, velocity.x, velocity.y, velocity.z);
            particle.setLifetime(lifetime);
            particles.spawn(particle);
        }
        return true;
    }

    private static double number(ExecutionContext<?> execution, Function.ArgumentCollection args, int index) {
        return args.size() > index ? args.getAsDouble(execution, index) : 0;
    }

    void advance(float time) {
        int target = (int) Math.floor(time * 20.0);
        while (tick < target) {
            tick++;
            particles.tick();
        }
    }

    @Override
    public void close() {
        closed = true;
        particles.clear();
    }
}
