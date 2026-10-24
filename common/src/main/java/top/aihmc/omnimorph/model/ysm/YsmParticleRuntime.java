package top.aihmc.omnimorph.model.ysm;

import com.mojang.brigadier.StringReader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.api.particle.LuaParticle;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.permissions.Permissions;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Owned client particle dispatch, including deferred client execution and model replacement checks. */
public final class YsmParticleRuntime {
    private final YsmModelRuntime runtime;
    private final RandomSource random = RandomSource.create();
    private final DispatchQueue queue = new DispatchQueue(4096);

    public YsmParticleRuntime(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public static boolean isPreview(EntityRenderMode mode) {
        return mode == EntityRenderMode.FIGURA_GUI || mode == EntityRenderMode.PAPERDOLL
                || mode == EntityRenderMode.MINECRAFT_GUI;
    }

    public Object execute(Avatar.MolangContext context, ExecutionContext<?> execution,
                          Function.ArgumentCollection arguments, boolean absolute) {
        if (context == null || context.owner == null || !queue.active()
                || context.owner != runtime.owner() || !runtime.functions().clientEffectsEnabled()
                || context.entity == null || isPreview(context.owner.renderMode)) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        var entity = context.entity;
        if (client == null || client.level == null || entity.level() != client.level) {
            return null;
        }
        YsmParticleRequest request = YsmParticleRequest.parse(execution, arguments, absolute);
        if (request == null) {
            return false;
        }
        var level = client.level;
        final ParticleOptions options;
        try {
            options = ParticleArgument.readParticle(new StringReader(request.id()), level.registryAccess());
        } catch (Exception error) {
            if (runtime.encryptedSource()) {
                throw new IllegalArgumentException("Encrypted YSM operation failed: runtime/particle");
            }
            throw new IllegalArgumentException("Invalid YSM particle parameters", error);
        }
        float bodyYaw = entity instanceof Player player ? player.yBodyRot : entity.getYRot();
        try {
            request.emit(entity.position(), entity instanceof Player, bodyYaw, entity.getYRot(),
                    random::nextGaussian, context.executionBudget(), emission -> queue.submit(client,
                            () -> client.level == level && !entity.isRemoved()
                                    && context.owner.getYsmRuntime() == runtime,
                            () -> spawn(client, context.owner, options, request.id(), emission)));
        } catch (RuntimeException failure) {
            queue.discardPending();
            throw sanitizeFailure(failure);
        }
        return true;
    }

    private void spawn(Minecraft client, Avatar owner, ParticleOptions options, String id,
                       YsmParticleRequest.Emission emission) {
        try {
            if (client.isPaused()) {
                return;
            }
            if (owner.remainingParticles() <= 0) {
                owner.noPermissions.add(Permissions.PARTICLES);
                return;
            }
            var position = emission.position();
            var velocity = emission.velocity();
            var particle = ParticleAPI.getParticleEngine().figura$makeParticle(options,
                    position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
            if (particle != null) {
                // Configure before spawning; preserve YSM lifetime even for WakeParticle.
                particle.setLifetime(emission.lifetime());
                new LuaParticle(id, particle, owner).spawn();
            }
        } catch (RuntimeException failure) {
            queue.discardPending();
            throw sanitizeFailure(failure);
        }
    }

    private RuntimeException sanitizeFailure(RuntimeException failure) {
        if (failure instanceof MolangExecutionBudget.Exceeded || !runtime.encryptedSource()) {
            return failure;
        }
        return new IllegalStateException("Encrypted YSM operation failed: runtime/particle");
    }

    public void discardPending() {
        queue.discardPending();
    }

    public void close() {
        queue.close();
    }

    public static final class DispatchQueue {
        private final int maximumPending;
        private int pending;
        private long generation;
        private boolean closed;

        public DispatchQueue(int maximumPending) {
            if (maximumPending <= 0) {
                throw new IllegalArgumentException("Particle dispatch budget must be positive");
            }
            this.maximumPending = maximumPending;
        }

        public synchronized boolean active() {
            return !closed;
        }

        public synchronized void submit(Executor executor, BooleanSupplier current, Runnable action) {
            if (closed) {
                return;
            }
            if (pending >= maximumPending) {
                throw new IllegalStateException("YSM particle dispatch budget exceeded");
            }
            pending++;
            long ticket = generation;
            try {
                executor.execute(() -> run(ticket, current, action));
            } catch (RuntimeException failure) {
                discardPending();
                throw failure;
            }
        }

        private synchronized void run(long ticket, BooleanSupplier current, Runnable action) {
            if (closed || ticket != generation) {
                return;
            }
            try {
                if (current.getAsBoolean()) {
                    action.run();
                }
            } finally {
                if (ticket == generation) {
                    pending--;
                }
            }
        }

        public synchronized void discardPending() {
            generation++;
            pending = 0;
        }

        public synchronized void close() {
            closed = true;
            discardPending();
        }
    }
}
