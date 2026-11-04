package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.ArrayList;
import java.util.List;

/** Native phase/group preparation and execution only; emits no vertices and needs no GPU or game window. */
public final class NativeFeaturePreparationSmokeTest {
    private static final FeatureRendererType<Emitter> EMITTER = FeatureRendererType.create("OMP late-submit regression");

    public static void main(String[] arguments) {
        SharedConstants.tryDetectVersion();
        try (var buffers = new RenderBuffers(1);
             var fixedBufferPack = buffers.fixedBufferPack();
             var dispatcher = new FeatureRenderDispatcher(buffers, null, null, null, null);
             var renderers = new FeatureRendererMap()) {
            var storage = new SubmitNodeStorage();
            var frame = dispatcher.new PreparedFrame();
            var context = new FeatureFrameContext(null, null, null, null, null, null, null,
                    buffers.stagedVertexBuffer());
            var items = new RecordingItems();
            var emitters = new EmittingRenderer(storage);
            renderers.put(EMITTER, emitters);
            renderers.put(ItemFeatureRenderer.TYPE, items);
            storage.order(0).solid.submit(new Emitter(0));

            // The native dispatcher drains phases before any feature builds its geometry.
            storage.drainPhases(phase -> phase.sortInto(new FeatureRenderDispatcher.PhaseSubmitGrouper(frame, phase)));
            for (var groups : frame.groupsByFeature.values()) {
                for (var group : groups) {
                    group.prepare(context, renderers, frame.allSubmits);
                }
            }

            SameFrameFeaturePreparation.prepareLateSubmissions(storage, frame, context, renderers);
            require(items.prepared.stream().sorted().toList().equals(List.of(11, 22, 33)),
                    "all callback-generated items must prepare exactly once in this frame: " + items.prepared);
            require(emitters.prepared.equals(List.of(0, 1, 2)), "nested callbacks prepare exactly once");
            require(storage.getSubmitsPerOrder().keySet().containsAll(List.of(0, 1, 2)),
                    "drained collections remain available for this frame's phase execution");
            for (var collection : storage.getSubmitsPerOrder().values()) {
                for (var phase : collection.allPhases()) {
                    require(phase.isEmpty(), "no late submissions leak into the next frame");
                }
            }
            for (var groups : frame.groupsByFeature.values()) {
                for (var group : groups) {
                    // Empty item geometry never touches the pass; this checks native group indices.
                    group.execute(context, null, null, renderers, frame.allSubmits);
                }
            }
            require(items.executed.stream().sorted().toList().equals(List.of(11, 22, 33)),
                    "native item group indices execute every prepared item exactly once");
            SameFrameFeaturePreparation.prepareLateSubmissions(storage, frame, context, renderers);
            require(items.prepared.size() == 3 && emitters.prepared.size() == 3,
                    "a completed frame cannot replay callbacks during an empty preparation pass");
        }
        System.out.println("PASS native same-frame submissions: nested callbacks, native item preparation/execution, retained orders; no GPU pixels");
    }

    private record Emitter(int depth) implements SubmitNode {
        @Override
        public FeatureRendererType<Emitter> featureType() {
            return EMITTER;
        }
    }

    private static final class EmittingRenderer implements FeatureRenderer<Emitter> {
        private final SubmitNodeStorage storage;
        private final List<Integer> prepared = new ArrayList<>();

        private EmittingRenderer(SubmitNodeStorage storage) {
            this.storage = storage;
        }

        @Override
        public void prepareGroup(FeatureFrameContext context, List<Emitter> submits, boolean strictlyOrdered) {
            for (Emitter submit : submits) {
                prepared.add(submit.depth());
                storage.order(submit.depth()).solid.submit(new ItemFeatureRenderer.Submit(
                        new PoseStack().last().copy(), ItemDisplayContext.NONE, 11 * (submit.depth() + 1),
                        0, 0, new int[0], List.of(), ItemStackRenderState.FoilType.NONE));
                if (submit.depth() < 2) {
                    storage.order(submit.depth() + 1).solid.submit(new Emitter(submit.depth() + 1));
                }
            }
        }

        @Override
        public void executeGroup(FeatureFrameContext context, OitStage stage, RenderPass renderPass,
                                 int groupIndex, List<Emitter> submits, boolean strictlyOrdered) {
        }
    }

    private static final class RecordingItems extends ItemFeatureRenderer {
        private final List<Integer> prepared = new ArrayList<>();
        private final List<Integer> executed = new ArrayList<>();

        @Override
        protected void buildGroup(FeatureFrameContext context, List<Submit> submits) {
            super.buildGroup(context, submits);
            for (Submit submit : submits) {
                prepared.add(submit.lightCoords());
            }
        }

        @Override
        public void executeGroup(FeatureFrameContext context, OitStage stage, RenderPass renderPass,
                                 int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
            super.executeGroup(context, stage, renderPass, groupIndex, submits, strictlyOrdered);
            for (Submit submit : submits) {
                executed.add(submit.lightCoords());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
