package top.aihmc.omm;

import java.util.ArrayList;
import java.util.List;

/** Keeps original draw semantics alongside the original CPU backend's vertex output. */
final class OmniMorphCpuFrames implements CpuPreviewWindow.FrameSource {
    @FunctionalInterface
    interface Sampler {
        List<Object> sample(int frame) throws Exception;
    }

    private final Sampler sampler;
    private final Object backend;
    private final RenderInputs inputs;
    private final java.lang.reflect.Method offset;
    private boolean offsetReported;
    private List<float[]> colors = List.of();
    private List<Boolean> directionalLighting = List.of();
    private List<Boolean> perFaceLighting = List.of();
    private List<Object> materials = List.of();
    private record Lighting(int light, int overlay, boolean emissive, boolean noOverlay) {}
    private List<Lighting> lighting = List.of();
    private List<Boolean> visibility = List.of();
    private final OmniMorphDrawOrder drawOrder = new OmniMorphDrawOrder();
    private List<OmniMorphDrawOrder.Range> ranges = List.of();

    @Override
    public List<OmniMorphDrawOrder.Range> drawRanges() {
        return ranges;
    }

    OmniMorphCpuFrames(Sampler sampler, Object backend) throws Exception {
        this.sampler = sampler;
        this.backend = backend;
        inputs = RenderInputs.read();
        offset = Class.forName("top.aihmc.omnimorph.render.acceleration.GpuSubmission")
            .getDeclaredMethod("offset", double[].class, double.class);
        offset.setAccessible(true);
        List<Boolean> initialVisibility = new ArrayList<>();
        for (Object batch : sampler.sample(0)) {
            Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
            initialVisibility.add((boolean) semantics.getClass().getMethod("visible").invoke(semantics));
        }
        visibility = initialVisibility;
    }

    @Override
    public List<double[]> sample(int frame) throws Exception {
        List<Object> batches = sampler.sample(frame);
        ranges = drawOrder.ranges(batches);
        List<double[]> vertices = OmniMorphBatches.transform(batches, backend);
        List<float[]> nextColors = new ArrayList<>(batches.size());
        List<Boolean> nextLighting = new ArrayList<>(batches.size());
        List<Boolean> nextPerFaceLighting = new ArrayList<>(batches.size());
        List<Object> nextMaterials = new ArrayList<>(batches.size());
        List<Lighting> nextTextureLighting = new ArrayList<>(batches.size());
        List<Boolean> nextVisibility = new ArrayList<>(batches.size());
        int offsetBatches = 0;
        for (int index = 0; index < batches.size(); index++) {
            Object batch = batches.get(index);
            Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
            Class<?> type = semantics.getClass();
            double vertexOffset = (double) type.getMethod("vertexOffset").invoke(semantics);
            if (vertexOffset != 0) {
                double[] output = vertices.get(index);
                double[] position = new double[4];
                for (int vertex = 0; vertex < output.length; vertex += 9) {
                    System.arraycopy(output, vertex, position, 0, 4);
                    offset.invoke(null, position, vertexOffset);
                    System.arraycopy(position, 0, output, vertex, 4);
                }
                offsetBatches++;
            }
            nextVisibility.add((boolean) type.getMethod("visible").invoke(semantics));
            Object material = type.getMethod("material").invoke(semantics);
            nextMaterials.add(material);
            boolean transformOnly = material == null;
            boolean lit = true;
            boolean perFace = false;
            boolean emissive = false;
            boolean noOverlay = false;
            if (material != null) {
                Object pipeline = material.getClass().getMethod("pipeline").invoke(material);
                Object defines = pipeline.getClass().getMethod("getShaderDefines").invoke(pipeline);
                var flags = (java.util.Set<?>) defines.getClass().getMethod("flags").invoke(defines);
                lit = !flags.contains("NO_CARDINAL_LIGHTING");
                perFace = flags.contains("PER_FACE_LIGHTING");
                emissive = flags.contains("EMISSIVE");
                noOverlay = flags.contains("NO_OVERLAY");
            }
            nextLighting.add(lit);
            nextPerFaceLighting.add(perFace);
            nextTextureLighting.add(new Lighting(
                transformOnly ? inputs.packedLight() : (int) type.getMethod("light").invoke(semantics),
                transformOnly ? inputs.packedOverlay() : (int) type.getMethod("overlay").invoke(semantics),
                emissive, noOverlay));
            float[] rgba = transformOnly
                ? new float[] {inputs.tint().x, inputs.tint().y, inputs.tint().z, inputs.tint().w}
                : new float[] {
                    (float) type.getMethod("red").invoke(semantics),
                    (float) type.getMethod("green").invoke(semantics),
                    (float) type.getMethod("blue").invoke(semantics),
                    (float) type.getMethod("alpha").invoke(semantics)
                };
            float[] batchColors = new float[vertices.get(index).length / 9 * 4];
            for (int vertex = 0; vertex < batchColors.length; vertex += 4) {
                System.arraycopy(rgba, 0, batchColors, vertex, 4);
            }
            nextColors.add(batchColors);
        }
        colors = List.copyOf(nextColors);
        directionalLighting = List.copyOf(nextLighting);
        perFaceLighting = List.copyOf(nextPerFaceLighting);
        materials = nextMaterials;
        lighting = List.copyOf(nextTextureLighting);
        visibility = List.copyOf(nextVisibility);
        if (!offsetReported) {
            System.out.println("OMP ORIGINAL CPU VERTEX OFFSET batches=" + offsetBatches);
            offsetReported = true;
        }
        return vertices;
    }

    @Override
    public List<float[]> colors() {
        return colors;
    }

    @Override
    public boolean directionalLighting(int batch) {
        return directionalLighting.get(batch);
    }

    @Override
    public boolean perFaceLighting(int batch) {
        return perFaceLighting.get(batch);
    }

    @Override
    public void beforeDraw(int batch, int program) {
        OmniMorphCpuMaterial.apply(materials.get(batch), program);
        Lighting input = lighting.get(batch);
        CpuLightTextures.coordinates(program, input.light(), input.overlay(), input.emissive(), input.noOverlay());
    }

    @Override
    public boolean visible(int batch) {
        return visibility.get(batch);
    }
}
