package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.ducks.ParticleEngineAccessor;
import sun.misc.Unsafe;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual UserData preparation/publication with a recording engine, without GPU rendering. */
final class AvatarAsyncLoadSmokeTest {
    static void run(Unsafe unsafe, Minecraft client, Runnable drain) throws Exception {
        run(unsafe, client, drain, null);
    }

    static void run(Unsafe unsafe, Minecraft client, Runnable drain, java.nio.file.Path encrypted) throws Exception {
        var field = Minecraft.class.getDeclaredField("particleEngine");
        field.setAccessible(true);
        Object previous = field.get(client);
        field.set(client, unsafe.allocateInstance(RecordingParticles.class));
        UserData user = new UserData(new UUID(0, 700));
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        var gate = AvatarLoadExecutor.submit(() -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Model worker gate timed out");
                }
            } catch (InterruptedException failure) {
                throw new AssertionError(failure);
            }
            return Thread.currentThread();
        });
        try {
            require(entered.await(5, TimeUnit.SECONDS), "preparation worker started");
            var first = user.loadAvatar(model("first"));
            var latest = user.loadAvatar(model("latest"));
            require(!first.isDone() && !latest.isDone(), "requesting a switch never waits for the worker");
            release.countDown();
            require(gate.get(5, TimeUnit.SECONDS) != Thread.currentThread(), "preparation runs off the game thread");
            await(first, drain);
            await(latest, drain);
            require(!first.join() && latest.join(), "only the most recent requested model publishes");
            Avatar retained = user.getMainAvatar();
            require(retained != null && retained.name.equals("latest"), "actual prepared model becomes resident");
            AtomicBoolean scope = new AtomicBoolean(true);
            var retired = user.loadAvatar(model("retired"), scope::get);
            scope.set(false);
            await(retired, drain);
            require(!retired.join() && user.getMainAvatar() == retained,
                    "retired connection cannot replace or clear the resident model");
            var malformed = model("invalid");
            malformed.getCompoundOrEmpty("metadata").putString("format", "ysm-native");
            malformed.put("ysm", new CompoundTag());
            var failed = user.loadAvatar(malformed);
            await(failed, drain);
            require(!failed.join() && user.getMainAvatar() == retained,
                    "runtime assembly failure keeps the previous model");
            if (encrypted != null) {
                var portable = model("encrypted cache replay");
                portable.getCompoundOrEmpty("metadata").putString("format", "ysm-native");
                try (var source = top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource.read(encrypted, () -> false)) {
                    portable.put("ysm", top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.fromEncrypted(
                            source, top.aihmc.omnimorph.avatar.ysm.YsmAvatarKind.NEW, null));
                }
                var restored = user.loadAvatar(portable);
                require(user.getMainAvatar() == retained, "encrypted cache replay retains old model during decoding");
                await(restored, drain);
                require(restored.join() && user.getMainAvatar().getYsmRuntime() != null
                                && user.getMainAvatar().getYsmRuntime().encryptedSource(),
                        "reported ciphertext reaches a published Avatar runtime through the async cache entry point");
                System.out.println("Reported encrypted model -> portable payload -> Avatar.load -> client publication PASS");
            }
            System.out.println("Actual avatar async preparation: nonblocking switch, newest generation, retired scope and failure retention PASS");
            AvatarBroadcastSmokeTest.run(user, drain);
        } finally {
            release.countDown();
            user.clear();
            drain.run();
            field.set(client, previous);
        }
    }

    private static CompoundTag model(String name) {
        var metadata = new CompoundTag();
        metadata.putString("name", name);
        var model = new CompoundTag();
        model.put("metadata", metadata);
        return model;
    }

    private static void await(CompletableFuture<?> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        future.get(1, TimeUnit.SECONDS);
        drain.run();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingParticles extends ParticleEngine implements ParticleEngineAccessor {
        private RecordingParticles() {
            super(null, null);
        }

        @Override
        public void figura$clearParticles(UUID owner) {
        }

        @Override
        public <T extends ParticleOptions> Particle figura$makeParticle(T options, double x, double y,
                double z, double velocityX, double velocityY, double velocityZ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void figura$spawnParticle(Particle particle, UUID owner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SpriteSet figura$getParticleSprite(Identifier id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void figura$removeParticle(Particle particle) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParticleResources figura$getParticleResources() {
            throw new UnsupportedOperationException();
        }
    }
}
