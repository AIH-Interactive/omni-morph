package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.test.NativeAllocationProbe;

/** Real world extraction/BufferBuilder submission; final GL shader drawing is replaced by vertex inspection. */
public final class ParticleWorldBufferMemorySmokeTest {
    public static void run(Avatar owner, FiguraTexture texture) throws Exception {
        var particle = new CustomParticle(owner, Minecraft.getInstance().level, texture, new Vec3(2, 3, 4), Vec3.ZERO);
        if (!particle.activate()) {
            throw new AssertionError("world memory fixture particle activates");
        }
        try (var memory = new NativeAllocationProbe()) {
            var buffers = new RecordingBuffers();
            var poses = new PoseStack();
            var camera = new Camera();
            draw(owner, poses, camera, buffers);
            long warmBytes = memory.liveBytes();
            long warmAllocations = memory.allocations();
            for (int frame = 0; frame < 1024; frame++) {
                draw(owner, poses, camera, buffers);
            }
            if (memory.allocations() != warmAllocations || memory.liveBytes() != warmBytes) {
                throw new AssertionError("World particles allocate unmanaged native memory after warmup: "
                        + warmBytes + " -> " + memory.liveBytes() + ", allocations="
                        + warmAllocations + " -> " + memory.allocations());
            }
            if (buffers.vertices != 4 * 1025 || buffers.builder.building()) {
                throw new AssertionError("World particles must keep drawing every quad and end the borrowed batch");
            }
            System.out.println("PASS 1024 actual world particle render submissions: native allocation/bytes plateau=" + warmBytes
                    + "; all 4100 vertices preserved");
        } finally {
            particle.remove();
            CustomParticleRuntime.finishFrame();
        }
    }

    private static void draw(Avatar owner, PoseStack poses, Camera camera, RecordingBuffers buffers) {
        owner.complexity.reset(65536);
        YsmRenderFrameClock.beginFrame();
        try {
            CustomParticleRuntime.renderWorld(poses, null, camera, .5f, buffers);
        } finally {
            CustomParticleRuntime.finishFrame();
        }
    }

    private static final class RecordingBuffers extends MultiBufferSource.BufferSource {
        private final BufferBuilder builder = new BufferBuilder(8);
        private int vertices;

        RecordingBuffers() {
            super(null, new java.util.HashMap<>());
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            builder.begin(type.mode(), type.format());
            return builder;
        }

        @Override
        public void endBatch() {
            if (builder.building()) {
                var rendered = builder.end();
                try {
                    vertices += rendered.drawState().vertexCount();
                } finally {
                    rendered.release();
                }
            }
        }

        @Override
        public void endBatch(RenderType type) {
            endBatch();
        }
    }
}
