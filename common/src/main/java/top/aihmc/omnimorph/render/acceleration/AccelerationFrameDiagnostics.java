package top.aihmc.omnimorph.render.acceleration;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Enabled;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Map;
import java.util.TreeMap;

/** Per-render-frame counters, emitted only when the custom JFR event is enabled. */
final class AccelerationFrameDiagnostics {
    @Name("omnimorph.AccelerationFrame")
    @Label("OmniMorph acceleration frame")
    @Category({"OmniMorph", "Rendering"})
    @Enabled(false)
    @StackTrace(false)
    static final class FrameEvent extends Event {
        long prepareCalls;
        long groupPrepareCalls;
        long rigidDataCreated;
        long groupGeometryCacheHits;
        long poseUploads;
        long poseBytes;
        long draws;
        long ssboDraws;
        long arenaBytes;
        long arenaUploads;
        long ssboFallbacks;
        String skinJoints;
        String paletteSizes;
        String materials;
        String fallbacks;
    }

    private static final EventType EVENT_TYPE = EventType.getEventType(FrameEvent.class);
    private static FrameEvent frame;
    private static volatile Thread frameThread;
    private static Map<Integer, Long> skinJoints;
    private static Map<Integer, Long> paletteSizes;
    private static Map<String, Long> materials;
    private static Map<String, Long> fallbacks;

    private AccelerationFrameDiagnostics() {
    }

    static void beginFrame() {
        if (frame != null) {
            frame.skinJoints = skinJoints.toString();
            frame.paletteSizes = paletteSizes.toString();
            frame.materials = materials.toString();
            frame.fallbacks = fallbacks.toString();
            frame.commit();
        }
        if (EVENT_TYPE.isEnabled()) {
            frameThread = Thread.currentThread();
            frame = new FrameEvent();
            frame.begin();
            skinJoints = new TreeMap<>();
            paletteSizes = new TreeMap<>();
            materials = new TreeMap<>();
            fallbacks = new TreeMap<>();
        } else {
            frame = null;
            frameThread = null;
            skinJoints = null;
            paletteSizes = null;
            materials = null;
            fallbacks = null;
        }
    }

    static void prepare(DrawBatch batch) {
        if (onFrameThread()) {
            frame.prepareCalls++;
            skinJoints.merge(batch.pose().skinJointCount(), 1L, Long::sum);
        }
    }

    static void prepareGroup(java.util.List<DrawBatch> batches) {
        if (onFrameThread()) {
            frame.groupPrepareCalls++;
            for (DrawBatch batch : batches) {
                skinJoints.merge(batch.pose().skinJointCount(), 1L, Long::sum);
            }
        }
    }

    static void rigidDataCreated() {
        if (onFrameThread()) {
            frame.rigidDataCreated++;
        }
    }

    static void groupGeometryCacheHit() {
        if (onFrameThread()) {
            frame.groupGeometryCacheHits++;
        }
    }

    static void poseUpload(long bytes, int paletteSize, boolean grouped) {
        if (onFrameThread()) {
            frame.poseUploads++;
            frame.poseBytes += bytes;
            if (grouped) {
                paletteSizes.merge(paletteSize, 1L, Long::sum);
            }
        }
    }

    static void draw(RenderType material) {
        if (onFrameThread()) {
            frame.draws++;
            materials.merge(String.valueOf(material), 1L, Long::sum);
        }
    }

    static void ssboDraw() {
        if (onFrameThread()) {
            frame.ssboDraws++;
        }
    }

    static void arenaUpload(long bytes) {
        if (onFrameThread()) {
            frame.arenaUploads++;
            frame.arenaBytes += bytes;
        }
    }

    static void ssboFallback(String reason) {
        if (onFrameThread()) {
            frame.ssboFallbacks++;
            fallbacks.merge("SSBO_" + reason, 1L, Long::sum);
        }
    }

    private static boolean onFrameThread() {
        return Thread.currentThread() == frameThread && frame != null;
    }

    static void fallback(String reason) {
        if (onFrameThread()) {
            fallbacks.merge(reason, 1L, Long::sum);
        }
    }
}
