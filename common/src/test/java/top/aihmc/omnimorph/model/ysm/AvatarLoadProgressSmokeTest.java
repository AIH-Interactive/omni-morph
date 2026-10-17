package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.gui.AvatarLoadingRenderer;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class AvatarLoadProgressSmokeTest {
    static void run() throws Exception {
        var progress = new AvatarLoadProgress();
        long old = progress.begin(AvatarLoadProgress.Stage.DOWNLOADING);
        progress.downloaded(old, 25, 100);
        require(progress.snapshot().fraction() == 0.25, "download progress uses actual received bytes");
        require(AvatarLoadingRenderer.label(progress.snapshot(), 0).getString().contains("25%"), "known length has a percentage");
        long latest = progress.begin(AvatarLoadProgress.Stage.DECODING);
        progress.downloaded(old, 100, 100);
        progress.finish(old);
        require(progress.snapshot().request() == latest && progress.snapshot().fraction() < 0,
                "old progress/finish cannot overwrite a newer decoding request");
        require(!AvatarLoadingRenderer.label(progress.snapshot(), 0).getString().contains("%"), "decoding has no invented percentage");
        require(!AvatarLoadingRenderer.label(progress.snapshot(), 0).equals(AvatarLoadingRenderer.label(progress.snapshot(), 15)),
                "indeterminate bar moves while decoding");
        progress.clearNetwork();
        require(progress.snapshot() != null, "disconnect does not hide local CPU preparation");
        progress.finish(latest);
        require(progress.snapshot() == null, "completed request removes the bar");
        progress.begin(AvatarLoadProgress.Stage.DOWNLOADING);
        progress.clearNetwork();
        require(progress.snapshot() == null, "disconnect removes cancelled transfer progress");

        byte[] payload = new byte[20000];
        AtomicLong received = new AtomicLong();
        byte[] read = CloudHttp.readBounded(new ByteArrayInputStream(payload), payload.length, received::set);
        require(Arrays.equals(read, payload) && received.get() == payload.length, "bounded HTTP read reports exact transfer bytes");
        try {
            CloudHttp.readBounded(new ByteArrayInputStream(payload), payload.length - 1, received::set);
            throw new AssertionError("Progress bypassed HTTP byte limit");
        } catch (java.io.IOException expected) {
            require(expected.getMessage().equals("Response exceeds byte budget"), "byte limit retained");
        }

        var state = new AvatarRenderState();
        state.nameTagAttachment = new Vec3(0, 2, 0);
        state.distanceToCameraSq = 16;
        var submit = AvatarLoadingRenderer.class.getDeclaredMethod("submitProgress", AvatarLoadProgress.Snapshot.class,
                LivingEntityRenderState.class, PoseStack.class, SubmitNodeCollector.class, CameraRenderState.class);
        submit.setAccessible(true);
        AtomicInteger submissions = new AtomicInteger();
        var collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    require(method.getName().equals("submitNameTag"), "loading uses native name-tag submission");
                    require(((Vec3) args[1]).y == 2.45, "loading bar is placed above the player's name tag");
                    require(((net.minecraft.network.chat.Component) args[3]).getString().contains("50%"), "submitted text carries real progress");
                    require(Boolean.TRUE.equals(args[4]), "loading label uses discrete native depth behavior");
                    submissions.incrementAndGet();
                    return null;
                });
        submit.invoke(null, new AvatarLoadProgress.Snapshot(1, AvatarLoadProgress.Stage.DOWNLOADING, 50, 100),
                state, new PoseStack(), collector, new CameraRenderState());
        require(submissions.get() == 1, "exactly one loading overlay is submitted");
        System.out.println("Player loading progress: real byte counts, indeterminate stages, stale cancellation, cleanup and native head-label submission PASS; visual GUI pending");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
