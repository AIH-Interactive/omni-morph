package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.gui.AvatarLoadingRenderer;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
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

        verifyNativeLabel();
        System.out.println("Player loading progress: real byte counts, stale cancellation and immediate native label passes PASS; visual GUI pending");
    }

    private static void verifyNativeLabel() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var client = net.minecraft.client.Minecraft.getInstance();
        var oldFont = client.font;
        var fontField = net.minecraft.client.Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        var oldOptions = client.options;
        var oldLevel = client.level;
        var optionsField = net.minecraft.client.Minecraft.class.getDeclaredField("options");
        optionsField.setAccessible(true);
        var font = new RecordingFont();
        var dispatcher = client.getEntityRenderDispatcher();
        var cameraField = net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredField("camera");
        cameraField.setAccessible(true);
        var oldCamera = cameraField.get(dispatcher);
        var oldOrientation = dispatcher.cameraOrientation();
        var usersField = org.figuramc.figura.avatar.AvatarManager.class.getDeclaredField("LOADED_USERS");
        usersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var users = (java.util.Map<java.util.UUID, org.figuramc.figura.avatar.UserData>) usersField.get(null);
        var id = new java.util.UUID(0, 456789);
        var user = new org.figuramc.figura.avatar.UserData(id);
        var previousUser = users.get(id);
        boolean oldPanic = org.figuramc.figura.avatar.AvatarManager.panic;
        var player = (LabelPlayer) unsafe.allocateInstance(LabelPlayer.class);
        player.id = id;
        var dimensionsField = net.minecraft.world.entity.Entity.class.getDeclaredField("dimensions");
        dimensionsField.setAccessible(true);
        dimensionsField.set(player, net.minecraft.world.entity.EntityDimensions.fixed(1, 2));
        var positionField = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
        positionField.setAccessible(true);
        positionField.set(player, new Vec3(0, 0, 4));
        long request = user.loading.begin(AvatarLoadProgress.Stage.DOWNLOADING);
        user.loading.downloaded(request, 50, 100);
        var poses = new PoseStack();
        var initialPose = new org.joml.Matrix4f(poses.last().pose());
        net.minecraft.client.renderer.MultiBufferSource buffers = type -> {
            throw new AssertionError("Recording font must intercept the native text boundary");
        };
        try {
            users.put(id, user);
            fontField.set(client, font);
            optionsField.set(client, unsafe.allocateInstance(LabelOptions.class));
            client.level = (net.minecraft.client.multiplayer.ClientLevel) unsafe.allocateInstance(
                    net.minecraft.client.multiplayer.ClientLevel.class);
            cameraField.set(dispatcher, new net.minecraft.client.Camera());
            dispatcher.overrideCameraOrientation(new org.joml.Quaternionf());
            org.figuramc.figura.avatar.AvatarManager.panic = false;
            var frame = new top.aihmc.omnimorph.render.EntityFrame(player, 0.25f);
            AvatarLoadingRenderer.render(frame, poses, buffers, 12345);
            require(font.modes.equals(java.util.List.of(net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                            net.minecraft.client.gui.Font.DisplayMode.NORMAL)),
                    "one loading label uses the native see-through and normal text passes exactly once");
            require(poses.last().pose().equals(initialPose), "loading label restores its caller's pose");
            player.hidden = true;
            AvatarLoadingRenderer.render(frame, poses, buffers, 12345);
            player.hidden = false;
            org.figuramc.figura.avatar.AvatarManager.panic = true;
            AvatarLoadingRenderer.render(frame, poses, buffers, 12345);
            org.figuramc.figura.avatar.AvatarManager.panic = false;
            positionField.set(player, new Vec3(0, 0, 65));
            AvatarLoadingRenderer.render(frame, poses, buffers, 12345);
            positionField.set(player, new Vec3(0, 0, 4));
            user.loading.finish(request);
            AvatarLoadingRenderer.render(frame, poses, buffers, 12345);
            require(font.modes.size() == 2, "hidden, panic, distant and completed labels do not draw");
        } finally {
            fontField.set(client, oldFont);
            optionsField.set(client, oldOptions);
            client.level = oldLevel;
            cameraField.set(dispatcher, oldCamera);
            dispatcher.overrideCameraOrientation(oldOrientation);
            org.figuramc.figura.avatar.AvatarManager.panic = oldPanic;
            if (previousUser == null) {
                users.remove(id);
            } else {
                users.put(id, previousUser);
            }
        }
    }

    private static final class LabelOptions extends net.minecraft.client.Options {
        private LabelOptions() {
            super(null, null);
        }

        @Override
        public float getBackgroundOpacity(float defaultValue) {
            return 0.25f;
        }
    }

    private static final class LabelPlayer extends net.minecraft.client.player.RemotePlayer {
        private java.util.UUID id;
        private boolean hidden;

        private LabelPlayer() {
            super(null, new com.mojang.authlib.GameProfile(new java.util.UUID(0, 1), "label-fixture"));
        }

        @Override
        public java.util.UUID getUUID() {
            return id;
        }

        @Override
        public boolean isInvisible() {
            return hidden;
        }

        @Override
        public float getViewYRot(float partialTick) {
            return 0f;
        }

    }

    private static final class RecordingFont extends net.minecraft.client.gui.Font {
        private final java.util.List<DisplayMode> modes = new java.util.ArrayList<>();

        private RecordingFont() {
            super(location -> null, false);
        }

        @Override
        public int width(net.minecraft.network.chat.FormattedText text) {
            return 80;
        }

        @Override
        public int drawInBatch(net.minecraft.network.chat.Component text, float x, float y, int color,
                               boolean shadow, org.joml.Matrix4f pose,
                               net.minecraft.client.renderer.MultiBufferSource buffers, DisplayMode mode,
                               int background, int light) {
            require(text.getString().contains("50%"), "native label carries the actual progress");
            require(x == -40f && y == 0f && !shadow, "label centers the native font width");
            require(Math.abs(pose.m31() - 2.45f) < 0.00001f,
                    "loading bar is placed above the native name-tag attachment");
            require(pose.m00() == 0.025f && pose.m11() == -0.025f && pose.m22() == 0.025f,
                    "label uses the native name-tag scale");
            require(light == 12345, "label preserves supplied world light");
            require(color == (modes.isEmpty() ? 0x20ffffff : -1), "native two-pass text colors");
            require(background == (modes.isEmpty() ? 0x3f000000 : 0), "only see-through pass has background");
            modes.add(mode);
            return 80;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
