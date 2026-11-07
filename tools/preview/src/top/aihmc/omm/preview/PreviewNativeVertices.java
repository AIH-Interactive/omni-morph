package top.aihmc.omm.preview;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import top.aihmc.omm.NativeApi;

/** Uses the core's immutable mesh, SIMD vertex kernels and persistent CPU worker budget. */
final class PreviewNativeVertices implements AutoCloseable {
    private long runtime;
    private boolean ownsRuntime;
    private long model;
    private long instance;
    final int threads;
    final String buildId;
    private final ArrayList<Buffer> poseBuffers = new ArrayList<>();
    private FloatBuffer localValues, worldValues, paletteValues, inverseBind;
    private IntBuffer parents, order;
    private ByteBuffer visibility;
    private FloatBuffer publication;
    private boolean published;
    private boolean readPaletteObjects = true;
    private long profileCalls, profilePack, profileNative, profileRead;
    private record PreparedRange(int offset, int count) {}
    private final ArrayList<PreparedRange> preparedRanges = new ArrayList<>();

    PreviewNativeVertices(PreviewMesh mesh, PreviewConfig config) {
        this(mesh, config, null);
    }

    PreviewNativeVertices(PreviewMesh mesh, PreviewConfig config, PreviewNativeVertices sharedRuntime) {
        NativeApi.load(config.configFile.getParent().resolve(config.execution.nativeLibrary).normalize());
        int[] version = NativeApi.omm_get_version();
        ownsRuntime = sharedRuntime == null;
        runtime = ownsRuntime ? NativeApi.omm_runtime_create(
                                    version[0], version[1], NativeApi.CPU_AUTO, config.execution.threads, 0, 0, 0)
                              : sharedRuntime.runtime;
        int[] caps = NativeApi.omm_runtime_get_caps(runtime);
        threads = caps[3];
        buildId = NativeApi.omm_get_build_id();
        int bones = mesh.skeleton == null ? 1 : mesh.skeleton.names.length;
        ByteBuffer parts = MemoryUtil.memCalloc(bones * NativeApi.PART_BYTES);
        ByteBuffer vertices = MemoryUtil.memCalloc(mesh.owners.length * NativeApi.VERTEX_BYTES);
        ByteBuffer indices = MemoryUtil.memAlloc(mesh.indices.length * 4);
        ByteBuffer surfaces = MemoryUtil.memCalloc(NativeApi.SURFACE_BYTES);
        ByteBuffer materials = MemoryUtil.memCalloc(NativeApi.MATERIAL_BYTES);
        try {
            localValues = poseFloats(bones * 16);
            worldValues = poseFloats(bones * 16);
            paletteValues = poseFloats(bones * 16);
            inverseBind = poseFloats(bones * 16);
            parents = MemoryUtil.memAllocInt(bones);
            poseBuffers.add(parents);
            order = MemoryUtil.memAllocInt(bones);
            poseBuffers.add(order);
            visibility = MemoryUtil.memAlloc(bones);
            poseBuffers.add(visibility);
            for (int bone = 0; bone < bones; bone++) {
                parents.put(bone, mesh.skeleton == null ? -1 : mesh.skeleton.parents[bone]);
                order.put(bone, mesh.skeleton == null ? 0 : mesh.skeleton.order[bone]);
                (mesh.skeleton == null ? new Matrix4f() : mesh.skeleton.inverseBindWorld[bone])
                    .get(bone * 16, inverseBind);
            }
            for (int bone = 0; bone < bones; bone++) {
                int offset = bone * NativeApi.PART_BYTES;
                parts.putInt(offset, -1);
                for (int axis = 0; axis < 4; axis++) parts.putDouble(offset + 8 + axis * 5 * 8, 1);
            }
            for (int vertex = 0; vertex < mesh.owners.length; vertex++) {
                int source = vertex * 5, target = vertex * NativeApi.VERTEX_BYTES;
                for (int axis = 0; axis < 3; axis++) vertices.putFloat(target + axis * 4, mesh.vertices[source + axis]);
                vertices.putFloat(target + 24, mesh.vertices[source + 3]);
                vertices.putFloat(target + 28, mesh.vertices[source + 4]);
                vertices.putInt(target + 32, mesh.owners[vertex]);
            }
            indices.asIntBuffer().put(mesh.indices);
            surfaces.putInt(4, mesh.indices.length);
            materials.putFloat(16, 1);
            // The preview loader already supplies canonical block coordinates for both source formats.
            model = NativeApi.omm_model_create(runtime, NativeApi.GEOMETRY_YSM, bones, parts, mesh.owners.length,
                vertices, mesh.indices.length, indices, 1, surfaces, 1, materials);
            instance = NativeApi.omm_instance_create(model);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        } finally {
            MemoryUtil.memFree(parts);
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(surfaces);
            MemoryUtil.memFree(materials);
        }
    }

    private FloatBuffer poseFloats(int count) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(count);
        poseBuffers.add(buffer);
        return buffer;
    }

    void compose(int offset, int count, Matrix4f[] localWorld, Matrix4f[] palette, boolean[] visible) {
        long profileStart = System.nanoTime();
        if (threads == 1) {
            prepareRange(offset, count);
        }
        for (int bone = 0; bone < count; bone++) {
            localWorld[bone].get(bone * 16, localValues);
            visibility.put(bone, (byte) (visible[bone] ? 1 : 0));
        }
        FloatBuffer result = publication == null ? paletteValues : publication.slice(offset * 16, count * 16);
        long profilePacked = System.nanoTime();
        compose(instance, offset, count, parents, order, inverseBind, localValues, visibility, worldValues, result,
            threads == 1);
        long profileComputed = System.nanoTime();
        for (int bone = 0; bone < count; bone++) {
            localWorld[bone].set(bone * 16, worldValues);
            if (readPaletteObjects) {
                palette[bone].set(bone * 16, result);
            }
            visible[bone] = visibility.get(bone) != 0;
        }
        published = true;
        if (count == 3500) {
            profileCalls++;
            if (profileCalls > 120) {
                profilePack += profilePacked - profileStart;
                profileNative += profileComputed - profilePacked;
                profileRead += System.nanoTime() - profileComputed;
            }
            if (profileCalls == 3120) {
                System.out.println("BRIDGE_PROFILE pack_us=" + profilePack / 3000000.0
                    + " native_us=" + profileNative / 3000000.0 + " read_us=" + profileRead / 3000000.0);
            }
        }
    }

    private void prepareRange(int offset, int count) {
        for (int index = 0; index < preparedRanges.size(); index++) {
            PreparedRange range = preparedRanges.get(index);
            if (range.offset() == offset && range.count() == count) {
                return;
            }
        }
        // These private metadata buffers are filled once in the constructor and never mutated.
        prepare(instance, offset, count, parents, order);
        preparedRanges.add(new PreparedRange(offset, count));
    }

    private static native void prepare(long instance, int offset, int count, IntBuffer parents, IntBuffer order);

    void beginPublication(FloatBuffer output) {
        beginPublication(output, true);
    }

    void beginPublication(FloatBuffer output, boolean readObjects) {
        if (publication != null) {
            throw new IllegalStateException("A preview pose publication is already active");
        }
        publication = output;
        published = false;
        readPaletteObjects = readObjects;
    }

    boolean endPublication() {
        publication = null;
        readPaletteObjects = true;
        return published;
    }

    private static native void compose(long instance, int offset, int count, IntBuffer parents, IntBuffer order,
        FloatBuffer inverseBind, FloatBuffer local, ByteBuffer visible, FloatBuffer world, FloatBuffer palette,
        boolean prepared);

    void transform(FloatBuffer palette, FloatBuffer output) {
        transform(instance, palette, output);
        output.position(0).limit(output.capacity());
    }

    private static native void transform(long instance, FloatBuffer palette, FloatBuffer output);

    @Override
    public void close() {
        for (Buffer buffer : poseBuffers) MemoryUtil.memFree(buffer);
        poseBuffers.clear();
        if (instance != 0) {
            NativeApi.omm_instance_release(instance);
            instance = 0;
        }
        if (model != 0) {
            NativeApi.omm_model_release(model);
            model = 0;
        }
        if (runtime != 0) {
            if (ownsRuntime)
                NativeApi.omm_runtime_destroy(runtime);
            runtime = 0;
        }
    }
}
