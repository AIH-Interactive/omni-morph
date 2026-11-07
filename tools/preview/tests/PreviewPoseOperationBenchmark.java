package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/** Windowless operation timing only: no animation, rendering or FPS claims. */
public final class PreviewPoseOperationBenchmark {
    private static volatile float consumed;
    private static final int WARMUP = 10000;
    private static final int ITERATIONS = 2000;
    private static final int ROUNDS = 5;

    private static final class Work implements AutoCloseable {
        final PreviewSkeleton skeleton;
        final Matrix4f[] local;
        final Matrix4f[] world;
        final Matrix4f[] palette;
        final boolean[] visible;
        final FloatBuffer output;
        final FloatBuffer publishedWorld;
        final ByteBuffer publishedVisibility;
        final PreviewNativeVertices bridge;
        final PreviewPreparedPoseCandidate prepared;
        final Matrix4f paletteScratch = new Matrix4f();

        Work(PreviewMesh mesh, PreviewConfig config) {
            skeleton = mesh.skeleton;
            int count = skeleton.names.length;
            local = new Matrix4f[count];
            world = new Matrix4f[count];
            palette = new Matrix4f[count];
            visible = new boolean[count];
            output = MemoryUtil.memAllocFloat(count * 16);
            publishedWorld = MemoryUtil.memAllocFloat(count * 16);
            publishedVisibility = MemoryUtil.memAlloc(count);
            bridge = new PreviewNativeVertices(mesh, config);
            prepared = new PreviewPreparedPoseCandidate(skeleton);
            for (int bone = 0; bone < count; bone++) {
                // Real hierarchy and inverse binds; controlled local poses isolate this operation.
                local[bone] = new Matrix4f().translation(0.001f * (bone % 7), 0.002f, 0)
                    .rotateZ(0.003f * (bone % 5));
                world[bone] = new Matrix4f();
                palette[bone] = new Matrix4f();
            }
        }

        void compose(int implementation) {
            for (int bone = 0; bone < local.length; bone++) {
                world[bone].set(local[bone]);
                visible[bone] = skeleton.restVisible[bone];
            }
            if (implementation >= 2) {
                prepared.compose(world, palette, visible, implementation == 3);
            } else if (implementation == 1) {
                bridge.compose(0, local.length, world, palette, visible);
            } else {
                for (int bone : skeleton.order) {
                    int parent = skeleton.parents[bone];
                    if (parent >= 0) {
                        world[bone].set(world[parent]).mul(local[bone]);
                        visible[bone] &= visible[parent];
                    }
                    palette[bone].set(world[bone]).mul(skeleton.inverseBindWorld[bone]);
                    if (!visible[bone]) {
                        palette[bone].scaling(0);
                    }
                }
            }
            consumed = world[world.length - 1].m30();
        }

        void pack(boolean bulk) {
            output.clear();
            if (bulk) {
                for (int bone = 0; bone < palette.length; bone++) {
                    palette[bone].get(bone * 16, output);
                }
            } else {
                for (Matrix4f matrix : palette) {
                    output.put(matrix.m00()).put(matrix.m01()).put(matrix.m02()).put(matrix.m03());
                    output.put(matrix.m10()).put(matrix.m11()).put(matrix.m12()).put(matrix.m13());
                    output.put(matrix.m20()).put(matrix.m21()).put(matrix.m22()).put(matrix.m23());
                    output.put(matrix.m30()).put(matrix.m31()).put(matrix.m32()).put(matrix.m33());
                }
            }
            consumed = output.get(output.capacity() - 4);
        }

        void publish(int implementation) {
            if (implementation > 0) {
                prepared.publish(local, skeleton.restVisible, implementation);
                consumed = prepared.worldX(local.length - 1);
                return;
            }
            // Same result contract as native: retained world, palette and visibility buffers.
            // Do not add the older cycle's redundant input-reset pass to this Java baseline.
            for (int bone : skeleton.order) {
                int parent = skeleton.parents[bone];
                if (parent < 0) {
                    world[bone].set(local[bone]);
                } else {
                    world[bone].set(world[parent]).mul(local[bone]);
                }
                visible[bone] = skeleton.restVisible[bone] && (parent < 0 || visible[parent]);
                palette[bone].set(world[bone]).mul(skeleton.inverseBindWorld[bone]);
                if (!visible[bone]) {
                    palette[bone].scaling(0);
                }
                world[bone].get(bone * 16, publishedWorld);
                palette[bone].get(bone * 16, output);
                publishedVisibility.put(bone, (byte) (visible[bone] ? 1 : 0));
            }
            consumed = publishedWorld.get((local.length - 1) * 16 + 12);
        }

        void run(int operation) {
            switch (operation) {
                case 0 -> compose(0);
                case 1 -> compose(1);
                case 2 -> pack(false);
                case 3 -> pack(true);
                case 4 -> compose(2);
                case 5 -> compose(3);
                case 6 -> publish(0);
                case 7 -> publish(1);
                case 8 -> publish(2);
                case 9 -> publish(3);
                case 10 -> publish(4);
                case 11 -> publish(5);
                case 12 -> {
                    compose(0);
                    pack(true);
                }
                case 13 -> {
                    bridge.beginPublication(output);
                    try {
                        compose(1);
                    } finally {
                        bridge.endPublication();
                    }
                    consumed = output.get(output.capacity() - 4);
                }
                case 14 -> publishWorldAndPalette();
                case 15 -> {
                    bridge.beginPublication(output, false);
                    try {
                        compose(1);
                    } finally {
                        bridge.endPublication();
                    }
                    consumed = output.get(output.capacity() - 4);
                }
                default -> throw new IllegalArgumentException("Unknown operation");
            }
        }

        void publishWorldAndPalette() {
            for (int bone : skeleton.order) {
                int parent = skeleton.parents[bone];
                if (parent < 0) {
                    world[bone].set(local[bone]);
                } else {
                    world[bone].set(world[parent]).mul(local[bone]);
                }
                visible[bone] = skeleton.restVisible[bone] && (parent < 0 || visible[parent]);
                paletteScratch.set(world[bone]).mul(skeleton.inverseBindWorld[bone]);
                if (!visible[bone]) {
                    paletteScratch.scaling(0);
                }
                paletteScratch.get(bone * 16, output);
            }
            consumed = output.get(output.capacity() - 4);
        }

        @Override
        public void close() {
            bridge.close();
            prepared.close();
            MemoryUtil.memFree(output);
            MemoryUtil.memFree(publishedWorld);
            MemoryUtil.memFree(publishedVisibility);
        }
    }

    public static void main(String[] args) throws Exception {
        Path manifest = Path.of(args[0]).toAbsolutePath();
        var sample = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        System.load(Path.of(args[3]).toAbsolutePath().toString());
        String[] operations = {"java_compose_cycle", "native_compose_cycle", "java_nio_pack",
            "candidate_direct_pack", "candidate_prepared_cycle", "candidate_simd_cycle",
            "java_publish", "candidate_direct_publish", "candidate_affine_publish",
            "candidate_checked_simd_publish", "candidate_avx_publish", "candidate_batched_avx_publish",
            "java_objects_and_palette", "native_objects_and_palette",
            "java_world_and_palette_buffer", "native_world_and_palette_buffer"};
        try (var report = new PrintWriter(Files.newBufferedWriter(Path.of(args[2])))) {
            report.println("kind,bones,operation,round,iterations,ns_per_operation");
            report.flush();
            for (var entry : sample.getAsJsonArray("selected")) {
                var item = entry.getAsJsonObject();
                String kind = item.get("kind").getAsString();
                var config = new PreviewConfig();
                config.configFile = manifest;
                config.scene.models = List.of(item.get("path").getAsString());
                config.vanilla.enabled = false;
                config.settings.enabled = false;
                config.execution.nativeLibrary = Path.of(args[1]).toAbsolutePath().toString();
                config.execution.threads = 1;
                var mesh = PreviewMesh.load(config);
                try (var work = new Work(mesh, config)) {
                    for (int iteration = 0; iteration < WARMUP; iteration++) {
                        for (int operation = 0; operation < operations.length; operation++) {
                            work.run(operation);
                        }
                    }
                    for (int round = 0; round < ROUNDS; round++) {
                        for (int step = 0; step < operations.length; step++) {
                            int operation = (round + step) % operations.length;
                            long start = System.nanoTime();
                            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                                work.run(operation);
                            }
                            double ns = (System.nanoTime() - start) / (double) ITERATIONS;
                            report.printf(Locale.ROOT, "%s,%d,%s,%d,%d,%.3f%n", kind,
                                work.local.length, operations[operation], round, ITERATIONS, ns);
                            report.flush();
                        }
                    }
                    System.out.println("Saved operation timings: " + kind + ", bones=" + work.local.length);
                }
            }
        }
    }
}
