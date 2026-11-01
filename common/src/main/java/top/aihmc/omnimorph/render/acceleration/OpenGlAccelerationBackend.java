package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Render-thread-only GL 3.2 backend shared by YSM and Figura. No vertex readback is used for drawing. */
final class OpenGlAccelerationBackend implements AccelerationBackend {
    private static final int POSE_BINDING = 13;
    private static final long MAX_GEOMETRY_BYTES = 32L * 1024 * 1024;
    private final Map<MeshKey, Geometry> meshes = new LinkedHashMap<>(16, .75f, true);
    private final Map<Integer, Program> programs = new HashMap<>();
    private final JavaAccelerationBackend javaBackend = new JavaAccelerationBackend();
    private int poseBuffer;
    private long geometryBytes;
    private long frame;
    private long uploads;
    private long draws;
    private final Thread renderThread = Thread.currentThread();
    private volatile boolean invalidated;

    private record MeshKey(MeshHandle mesh, int joints, boolean floatPose) {}

    private record Geometry(int vao, int vertices, int indices, int count, long bytes, long frame) {
        void delete() {
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(vertices);
            GL15.glDeleteBuffers(indices);
        }
    }

    private record Program(int id, int source) {}

    static final class SubmittedDrawFailure extends RuntimeException {
        SubmittedDrawFailure(Throwable cause) {
            super("GPU failure after submission; this group must not be replayed", cause);
        }
    }

    static boolean available() {
        try {
            return GL.getCapabilities().OpenGL32;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    @Override
    public boolean supports(Operation operation, DrawBatch batch) {
        if (operation != Operation.GPU_DRAW || batch.mesh().verticesPerFace() != 4
                || !GpuBatchData.supports(batch)) {
            return false;
        }
        DrawSemantics draw = batch.semantics();
        RenderType material = draw.material();
        return draw.visible() && material != null && !draw.isOutline()
                && material.format() == DefaultVertexFormat.NEW_ENTITY
                && material.mode() == VertexFormat.Mode.QUADS
                && material instanceof RenderType.CompositeRenderType composite
                && (composite.state.transparencyState.toString().equals("no_transparency")
                    || material.sortOnUpload());
    }

    @Override
    public double[] transform(DrawBatch batch) {
        return javaBackend.transform(batch);
    }

    @Override
    public boolean drawBatches(List<DrawBatch> batches) {
        if (org.figuramc.figura.lua.api.ClientAPI.hasShaderPack()) {
            AccelerationFrameDiagnostics.fallback("INCOMPATIBLE_PIPELINE");
            return false;
        }
        DrawBatch batch = batches.getFirst();
        RenderType material = batch.semantics().material();
        material.setupRenderState();
        try {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader == null || !supportedShader(shader.getName())) {
                return false;
            }
            shader.setDefaultUniforms(material.mode(), RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
            shader.apply();
            try {
                return drawActive(batches, shader.getId());
            } finally {
                shader.clear();
            }
        } finally {
            material.clearRenderState();
        }
    }

    private static boolean supportedShader(String name) {
        return name.equals("rendertype_entity_solid") || name.equals("rendertype_entity_cutout")
                || name.equals("rendertype_entity_cutout_no_cull")
                || name.equals("rendertype_entity_cutout_no_cull_z_offset")
                || name.equals("rendertype_entity_translucent")
                || name.equals("rendertype_entity_translucent_cull");
    }

    /** The original program must already have its vanilla uniforms/samplers applied. Also used by GL tests. */
    boolean drawActive(DrawBatch batch, int original) {
        return drawActive(List.of(batch), original);
    }

    boolean drawActive(List<DrawBatch> batches, int original) {
        boolean submitted = false;
        try (GlBindings bindings = new GlBindings()) {
            long needed = geometryBytes;
            for (DrawBatch batch : batches) {
                MeshKey key = new MeshKey(batch.mesh(), batch.pose().skinJointCount(), batch.pose().usesFloatPrecision());
                if (!meshes.containsKey(key)) {
                    needed += (long) batch.mesh().vertexCount() * GpuBatchData.VERTEX_BYTES
                            + (long) batch.mesh().faceCount() * 6 * Integer.BYTES;
                }
            }
            if (needed > MAX_GEOMETRY_BYTES) {
                AccelerationFrameDiagnostics.fallback("GEOMETRY_BUDGET");
                return false;
            }
            Program program = programs.computeIfAbsent(original, OpenGlAccelerationBackend::wrapProgram);
            if (program.id() == 0) {
                AccelerationFrameDiagnostics.fallback("SHADER_CONTRACT");
                return false;
            }
            GL20.glUseProgram(program.id());
            copyUniforms(original, program.id());
            Geometry[] geometry = new Geometry[batches.size()];
            ByteBuffer[] poses = new ByteBuffer[batches.size()];
            for (int index = 0; index < batches.size(); index++) {
                geometry[index] = geometry(batches.get(index));
                poses[index] = GpuBatchData.pose(batches.get(index));
            }
            long[] order = faceOrder(batches);
            if (poseBuffer == 0) {
                poseBuffer = GL15.glGenBuffers();
            }
            // Consecutive faces sharing a pose use one multi-draw, even when their index offsets are unsorted.
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer counts = stack.mallocInt(256);
                var offsets = stack.mallocPointer(256);
                for (int first = 0; first < order.length;) {
                    int batchIndex = (int) (order[first] >>> 32);
                    int end = first + 1;
                    while (end < order.length && end - first < 256
                            && (int) (order[end] >>> 32) == batchIndex) {
                        end++;
                    }
                    counts.clear().limit(end - first);
                    offsets.clear().limit(end - first);
                    for (int face = first; face < end; face++) {
                        counts.put(6);
                        offsets.put((order[face] & 0xffffffffL) * 6 * Integer.BYTES);
                    }
                    counts.flip();
                    offsets.flip();
                    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, poseBuffer);
                    // Orphaning leaves in-flight draws owning their old storage.
                    GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, poses[batchIndex], GL15.GL_STREAM_DRAW);
                    GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, POSE_BINDING, poseBuffer);
                    GL30.glBindVertexArray(geometry[batchIndex].vao());
                    GL14.glMultiDrawElements(GL11.GL_TRIANGLES, counts, GL11.GL_UNSIGNED_INT, offsets);
                    submitted = true;
                    draws++;
                    AccelerationFrameDiagnostics.poseUpload(poses[batchIndex].remaining(), 1, false);
                    AccelerationFrameDiagnostics.draw(batches.get(batchIndex).semantics().material());
                    first = end;
                }
            }
            batches.forEach(AccelerationFrameDiagnostics::prepare);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (submitted) {
                throw new SubmittedDrawFailure(failure);
            }
            throw failure;
        }
    }

    static long[] faceOrder(List<DrawBatch> batches) {
        int faces = batches.stream().mapToInt(batch -> batch.mesh().faceCount()).sum();
        long[] authored = new long[faces];
        Vector3f[] centers = new Vector3f[faces];
        int index = 0;
        double[] first = new double[4];
        double[] third = new double[4];
        boolean sorted = batches.getFirst().semantics().sortOnUpload();
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            DrawBatch batch = batches.get(batchIndex);
            for (int face = 0; face < batch.mesh().faceCount(); face++) {
                authored[index] = ((long) batchIndex << 32) | face;
                if (sorted) {
                    batch.pose().transformedPosition(batch.mesh(), face * 4, first);
                    batch.pose().transformedPosition(batch.mesh(), face * 4 + 2, third);
                    offsetPosition(first, batch.semantics().vertexOffset());
                    offsetPosition(third, batch.semantics().vertexOffset());
                    // Match MeshData's float vertex storage and diagonal midpoint, including tie order.
                    centers[index] = new Vector3f(((float) first[0] + (float) third[0]) * .5f,
                            ((float) first[1] + (float) third[1]) * .5f,
                            ((float) first[2] + (float) third[2]) * .5f);
                }
                index++;
            }
        }
        if (!sorted) {
            return authored;
        }
        int[] permutation = RenderSystem.getVertexSorting().sort(centers);
        long[] result = new long[faces];
        for (int face = 0; face < faces; face++) {
            result[face] = authored[permutation[face]];
        }
        return result;
    }

    private static void offsetPosition(double[] position, double offset) {
        if (offset == 0) {
            return;
        }
        double length = Math.sqrt(position[0] * position[0] + position[1] * position[1]
                + position[2] * position[2] + position[3] * position[3]);
        for (int component = 0; component < 3; component++) {
            position[component] += position[component] / length * offset;
        }
    }

    private Geometry geometry(DrawBatch batch) {
        MeshKey key = new MeshKey(batch.mesh(), batch.pose().skinJointCount(), batch.pose().usesFloatPrecision());
        Geometry cached = meshes.get(key);
        if (cached != null) {
            Geometry touched = new Geometry(cached.vao(), cached.vertices(), cached.indices(), cached.count(),
                    cached.bytes(), frame);
            meshes.put(key, touched);
            return touched;
        }
        ByteBuffer vertices = GpuBatchData.vertices(batch.mesh(), key.joints(), key.floatPose());
        ByteBuffer indices = GpuBatchData.indices(batch.mesh());
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        int ibo = GL15.glGenBuffers();
        Geometry geometry = new Geometry(vao, vbo, ibo, indices.remaining() / 4,
                (long) vertices.remaining() + indices.remaining(), frame);
        try {
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
            int[] sizes = {3, 3, 2, 4};
            int[] offsets = {0, 12, 24, 32};
            for (int index = 0; index < sizes.length; index++) {
                GL20.glEnableVertexAttribArray(index);
                GL20.glVertexAttribPointer(index, sizes[index], GL11.GL_FLOAT, false,
                        GpuBatchData.VERTEX_BYTES, offsets[index]);
            }
            GL20.glEnableVertexAttribArray(4);
            GL30.glVertexAttribIPointer(4, 4, GL11.GL_INT, GpuBatchData.VERTEX_BYTES, 48L);
            meshes.put(key, geometry);
            geometryBytes += geometry.bytes();
            uploads++;
            trim(false);
            return geometry;
        } catch (RuntimeException | LinkageError failure) {
            geometry.delete();
            throw failure;
        }
    }

    private static Program wrapProgram(int original) {
        int vertex = 0;
        int fragment = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer shaders = stack.mallocInt(GL20.glGetProgrami(original, GL20.GL_ATTACHED_SHADERS));
            GL20.glGetAttachedShaders(original, null, shaders);
            while (shaders.hasRemaining()) {
                int shader = shaders.get();
                int type = GL20.glGetShaderi(shader, GL20.GL_SHADER_TYPE);
                if (type == GL20.GL_VERTEX_SHADER) {
                    vertex = shader;
                } else if (type == GL20.GL_FRAGMENT_SHADER) {
                    fragment = shader;
                } else {
                    return new Program(0, original);
                }
            }
        }
        if (vertex == 0 || fragment == 0 || !uniformsSupported(original)) {
            return new Program(0, original);
        }
        String wrapped;
        try {
            wrapped = GpuShaderProgram.wrapEntitySource(GL20.glGetShaderSource(vertex));
        } catch (IllegalArgumentException incompatible) {
            return new Program(0, original);
        }
        int shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        int program = GL20.glCreateProgram();
        try {
            GL20.glShaderSource(shader, wrapped);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                throw new IllegalArgumentException(GL20.glGetShaderInfoLog(shader));
            }
            GL20.glAttachShader(program, shader);
            GL20.glAttachShader(program, fragment);
            String[] attributes = {"OmniPosition", "OmniNormal", "OmniUV", "OmniWeights", "OmniJoints"};
            for (int index = 0; index < attributes.length; index++) {
                GL20.glBindAttribLocation(program, index, attributes[index]);
            }
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalArgumentException(GL20.glGetProgramInfoLog(program));
            }
            GL31.glUniformBlockBinding(program, GL31.glGetUniformBlockIndex(program, GpuShaderProgram.UNIFORM),
                    POSE_BINDING);
            return new Program(program, original);
        } catch (IllegalArgumentException incompatible) {
            GL20.glDeleteProgram(program);
            org.figuramc.figura.FiguraMod.LOGGER.warn("GPU shader rejected; keeping Java material: {}",
                    incompatible.getMessage());
            return new Program(0, original);
        } finally {
            GL20.glDeleteShader(shader);
        }
    }

    private static boolean uniformsSupported(int program) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            for (int index = 0; index < GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS); index++) {
                GL20.glGetActiveUniform(program, index, size, type);
                if (size.get(0) != 1 || !supportedUniform(type.get(0))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean supportedUniform(int type) {
        return switch (type) {
            case GL11.GL_FLOAT, GL20.GL_FLOAT_VEC2, GL20.GL_FLOAT_VEC3, GL20.GL_FLOAT_VEC4,
                    GL20.GL_FLOAT_MAT3, GL20.GL_FLOAT_MAT4, GL11.GL_INT, GL20.GL_BOOL,
                    GL20.GL_SAMPLER_2D -> true;
            default -> false;
        };
    }

    private static void copyUniforms(int from, int to) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            FloatBuffer values = stack.mallocFloat(16);
            for (int index = 0; index < GL20.glGetProgrami(from, GL20.GL_ACTIVE_UNIFORMS); index++) {
                String name = GL20.glGetActiveUniform(from, index, size, type);
                int source = GL20.glGetUniformLocation(from, name);
                int target = GL20.glGetUniformLocation(to, name);
                if (source < 0 || target < 0) {
                    continue;
                }
                if (type.get(0) == GL11.GL_INT || type.get(0) == GL20.GL_BOOL
                        || type.get(0) == GL20.GL_SAMPLER_2D) {
                    GL20.glUniform1i(target, GL20.glGetUniformi(from, source));
                    continue;
                }
                values.clear();
                GL20.glGetUniformfv(from, source, values);
                switch (type.get(0)) {
                    case GL11.GL_FLOAT -> GL20.glUniform1f(target, values.get(0));
                    case GL20.GL_FLOAT_VEC2 -> GL20.glUniform2f(target, values.get(0), values.get(1));
                    case GL20.GL_FLOAT_VEC3 -> GL20.glUniform3f(target, values.get(0), values.get(1), values.get(2));
                    case GL20.GL_FLOAT_VEC4 -> GL20.glUniform4f(target, values.get(0), values.get(1), values.get(2), values.get(3));
                    case GL20.GL_FLOAT_MAT3 -> GL20.glUniformMatrix3fv(target, false, values.limit(9));
                    case GL20.GL_FLOAT_MAT4 -> GL20.glUniformMatrix4fv(target, false, values);
                    default -> throw new IllegalArgumentException("Unsupported material uniform");
                }
            }
        }
    }

    @Override
    public void beginFrame() {
        if (invalidated) {
            deleteResources();
        }
        frame++;
        trim(true);
    }

    private void trim(boolean expire) {
        Iterator<Geometry> iterator = meshes.values().iterator();
        while (iterator.hasNext()) {
            Geometry geometry = iterator.next();
            if (geometryBytes <= MAX_GEOMETRY_BYTES && (!expire || frame - geometry.frame() < 120)) {
                continue;
            }
            // Never evict the geometry being prepared in this frame.
            if (geometry.frame() == frame) {
                continue;
            }
            geometry.delete();
            geometryBytes -= geometry.bytes();
            iterator.remove();
        }
    }

    long uploads() {
        return uploads;
    }

    long draws() {
        return draws;
    }

    long geometryBytes() {
        return geometryBytes;
    }

    @Override
    public void invalidateResources() {
        invalidated = true;
        if (Thread.currentThread() == renderThread) {
            deleteResources();
        }
    }

    private void deleteResources() {
        meshes.values().forEach(Geometry::delete);
        meshes.clear();
        geometryBytes = 0;
        programs.values().stream().filter(program -> program.id() != 0)
                .forEach(program -> GL20.glDeleteProgram(program.id()));
        programs.clear();
        if (poseBuffer != 0) {
            GL15.glDeleteBuffers(poseBuffer);
            poseBuffer = 0;
        }
        invalidated = false;
    }

    @Override
    public void close() {
        if (Thread.currentThread() == renderThread) {
            deleteResources();
        } else {
            RenderSystem.recordRenderCall(this::deleteResources);
        }
    }

    /** Raw GL bindings are restored exactly; invalidate Minecraft's cached immediate VAO binding too. */
    private static final class GlBindings implements AutoCloseable {
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int uniform = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        private final int pose = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, POSE_BINDING);
        private final long start = GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_START, POSE_BINDING);
        private final long size = GL32.glGetInteger64i(GL31.GL_UNIFORM_BUFFER_SIZE, POSE_BINDING);

        @Override
        public void close() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
            if (pose != 0 && size > 0) {
                GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, POSE_BINDING, pose, start, size);
            } else {
                GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, POSE_BINDING, pose);
            }
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniform);
            BufferUploader.invalidate();
        }
    }
}
