package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.FeatureRendererType;

import java.util.IdentityHashMap;
import java.util.Map;

/** Completes callback-generated native submissions before finishPrepare and the shared vertex-buffer upload. */
public final class SameFrameFeaturePreparation {
    private SameFrameFeaturePreparation() {
    }

    public static void prepareLateSubmissions(SubmitNodeStorage storage,
                                              FeatureRenderDispatcher.PreparedFrame frame,
                                              FeatureFrameContext context, FeatureRendererMap renderers) {
        Map<FeatureRendererType<?>, Integer> preparedCounts = new IdentityHashMap<>();
        for (var entry : frame.groupsByFeature.entrySet()) {
            preparedCounts.put(entry.getKey(), entry.getValue().size());
        }

        while (drainPendingPhases(storage, frame)) {
            for (var entry : frame.groupsByFeature.entrySet()) {
                var groups = entry.getValue();
                int prepared = preparedCounts.getOrDefault(entry.getKey(), 0);
                int count = groups.size();
                for (int index = prepared; index < count; index++) {
                    groups.get(index).prepare(context, renderers, frame.allSubmits);
                }
                preparedCounts.put(entry.getKey(), count);
            }
        }
    }

    private static boolean drainPendingPhases(SubmitNodeStorage storage, FeatureRenderDispatcher.PreparedFrame frame) {
        boolean drained = false;
        // drainPhases removes empty collections. Their identities are still needed by this frame's execution.
        for (var collection : storage.getSubmitsPerOrder().values()) {
            for (var phase : collection.allPhases()) {
                if (!phase.isEmpty()) {
                    phase.sortInto(new FeatureRenderDispatcher.PhaseSubmitGrouper(frame, phase));
                    drained = true;
                }
            }
        }
        return drained;
    }
}
