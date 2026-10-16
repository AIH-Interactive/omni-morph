package org.figuramc.figura.model.ysm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.audio.Library;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import org.figuramc.figura.avatar.ysm.YsmAvatarDetector;
import org.figuramc.figura.avatar.ysm.YsmAvatarLoader;
import org.figuramc.figura.avatar.ysm.YsmModelData;
import org.figuramc.figura.avatar.ysm.YsmSourcePayload;
import org.figuramc.figura.ducks.SoundEngineAccessor;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.mixin.sound.SoundManagerAccessor;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import net.minecraft.sounds.SoundSource;
import sun.misc.Unsafe;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Corpus CPU/native resource stability. No window, GPU, OpenAL device or frame-time claim. */
final class YsmStabilitySmokeTest {
    private YsmStabilitySmokeTest() {
    }

    private record Candidate(Path container, String archiveRoot, boolean figura) {
    }

    private record Measurement(int iteration, String model, String route, boolean firstLoad,
                               double loadMilliseconds, double sourceReadMilliseconds, List<Double> sampleMilliseconds,
                               int imagesClosed, int pcmBuffersReleased, long pcmBytesReleased) {
    }

    private record Batch(int number, long heapBytes, long directBufferPoolBytes,
                         long retainedRuntimes, long releasedPcmBytes) {
    }

    static SoundManager soundManager(Unsafe unsafe) throws Exception {
        RecordingManager manager = (RecordingManager) unsafe.allocateInstance(RecordingManager.class);
        manager.engine = (RecordingEngine) unsafe.allocateInstance(RecordingEngine.class);
        return manager;
    }

    static void run(Unsafe unsafe, Runnable drain, Path root, Path report, Path limits) throws Exception {
        var frozen = JsonParser.parseString(Files.readString(limits, StandardCharsets.UTF_8)).getAsJsonObject();
        int iterations = frozen.get("switchIterations").getAsInt();
        int batchSize = frozen.get("switchBatchSize").getAsInt();
        require(iterations == 100 && batchSize == 10, "M0 frozen 100 switches in batches of 10");
        List<Candidate> candidates = discover(root);
        List<Candidate> ysm = candidates.stream().filter(candidate -> !candidate.figura()).toList();
        List<Candidate> figura = candidates.stream().filter(Candidate::figura).toList();
        require(ysm.size() == 40 && figura.size() == 5, "complete frozen corpus categories");
        RecordingEngine engine = ((RecordingManager) Minecraft.getInstance().getSoundManager()).engine;
        Method sample = YsmAnimationPlayer.class.getDeclaredMethod("sampleAnimations", ExpressionEvaluator.class, boolean.class);
        sample.setAccessible(true);
        List<Measurement> measurements = new ArrayList<>();
        List<Batch> batches = new ArrayList<>();
        List<WeakReference<YsmModelRuntime>> retired = new ArrayList<>();
        Map<Candidate, Integer> visits = new LinkedHashMap<>();
        int ysmIndex = 0;
        int figuraIndex = 0;
        for (int iteration = 0; iteration < iterations; iteration++) {
            boolean ordinary = iteration % 5 == 4;
            Candidate candidate = ordinary ? figura.get(figuraIndex++ % figura.size()) : ysm.get(ysmIndex++ % ysm.size());
            int previousVisits = visits.getOrDefault(candidate, 0);
            visits.put(candidate, previousVisits + 1);
            measurements.add(cycle(unsafe, drain, engine, sample, candidate, iteration, previousVisits, retired));
            if ((iteration + 1) % batchSize == 0) {
                collectRetired(retired);
                long retained = retired.stream().filter(reference -> reference.get() != null).count();
                Batch batch = new Batch((iteration + 1) / batchSize, usedHeap(), directBufferBytes(), retained, engine.releasedBytes);
                batches.add(batch);
                require(retained == 0, "closed runtime is not retained after bounded collection at batch " + batch.number());
                System.out.printf("M8 batch %d: iterations=%d heapBytes=%d directPoolBytes=%d retained=%d pcmReleased=%d%n",
                        batch.number(), iteration + 1, batch.heapBytes(), batch.directBufferPoolBytes(), retained, engine.releasedBytes);
            }
        }
        require(visits.size() == candidates.size(), "every source participates in the stability run");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("evidence", "CPU/native images and PCM ownership only; GUI/GPU/frame time SKIPPED/MANUAL_BY_USER");
        output.put("baselineCommit", "9c48cea03b26ac52d9f540f637318bcb873041a1");
        output.put("java", System.getProperty("java.runtime.version"));
        output.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        output.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        output.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        output.put("iterations", iterations);
        output.put("batchSize", batchSize);
        output.put("corpusSources", candidates.size());
        output.put("ysmLoads", ysmIndex);
        output.put("figuraCompiles", figuraIndex);
        output.put("coldDefinition", "First observation of a model in this JVM; OS disk caches are not evicted");
        output.put("measurements", measurements);
        output.put("batches", batches);
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(output), StandardCharsets.UTF_8);
        System.out.println("M8 corpus stability PASS: 100 switches, 10 batches, all 45 sources; native images closed, PCM released, no retained runtimes.");
    }

    private static Measurement cycle(Unsafe unsafe, Runnable drain, RecordingEngine engine, Method sample,
                                      Candidate candidate, int iteration, int previousVisits,
                                      List<WeakReference<YsmModelRuntime>> retired) throws Exception {
        long start = System.nanoTime();
        if (candidate.figura()) {
            if (candidate.archiveRoot() == null) {
                compileFigura(candidate.container());
            } else {
                try (var archive = FileSystems.newFileSystem(candidate.container(), (ClassLoader) null)) {
                    compileFigura(archive.getPath(candidate.archiveRoot()));
                }
            }
            return new Measurement(iteration, "figura-" + candidate.container().getFileName(), "figura_compile",
                    previousVisits == 0, milliseconds(start), 0, List.of(), 0, 0, 0);
        }
        YsmModelRuntime runtime = null;
        List<NativeImage> images = new ArrayList<>();
        List<Double> samples = new ArrayList<>();
        int buffers = 0;
        long bytes = 0;
        long releaseCount = 0;
        long releasedBytes = 0;
        double loadMilliseconds;
        double sourceReadMilliseconds;
        try (YsmModelData data = YsmModelData.read(candidate.container(), () -> false)) {
            sourceReadMilliseconds = milliseconds(start);
            Avatar owner = owner(unsafe);
            var selected = data.manifest().textures().getFirst();
            var portable = YsmAvatarLoader.toAvatarNbt(data, selected, owner.owner, candidate.container());
            runtime = previousVisits == 0 ? YsmModelRuntime.fromData(owner, data, portable.getCompoundOrEmpty("ysm"))
                    : YsmModelRuntime.fromNbt(owner, YsmSourcePayload.forOutput(portable).getCompoundOrEmpty("ysm"));
            install(owner, "ysmRuntime", runtime);
            runtime.restoreControls();
            loadMilliseconds = milliseconds(start);
            require(runtime.contentHash().equals(data.identity().contentSha256()), "runtime keeps actual source identity");
            Set<FiguraTexture> textures = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            textures.add(runtime.texture());
            for (YsmSubEntity child : runtime.subEntities()) {
                textures.add(child.texture());
            }
            FiguraTexture foreground = runtime.guiResources().foreground();
            FiguraTexture background = runtime.guiResources().background();
            if (foreground != null) {
                textures.add(foreground);
            }
            if (background != null) {
                textures.add(background);
            }
            for (FiguraTexture texture : List.copyOf(textures)) {
                if (texture instanceof YsmMaterialTexture material) {
                    if (material.normalTexture() != null) {
                        textures.add(material.normalTexture());
                    }
                    if (material.specularTexture() != null) {
                        textures.add(material.specularTexture());
                    }
                }
            }
            Field nativeImage = FiguraTexture.class.getDeclaredField("nativeImageTexture");
            nativeImage.setAccessible(true);
            for (FiguraTexture texture : textures) {
                images.add((NativeImage) nativeImage.get(texture));
            }
            for (String name : runtime.audio().names()) {
                buffers++;
                bytes += runtime.audio().get(name).size();
            }
            List<YsmAnimationClip> clips = runtime.animations().getClips().values().stream().distinct()
                    .sorted(Comparator.comparing(clip -> clip.name)).toList();
            if (!clips.isEmpty()) {
                var playing = runtime.animations().play(clips.getFirst().name, true, 1);
                playing.time = Math.min(0.25f, Math.max(0, playing.clip.length) * 0.5f);
            }
            for (int pass = 0; pass < 5; pass++) {
                long sampled = System.nanoTime();
                sample.invoke(runtime.animations(), ExpressionEvaluator.evaluator(owner.getMolangContext()), false);
                samples.add(milliseconds(sampled));
                for (YsmModelPart part : runtime.uniqueParts()) {
                    require(finite(part.animPosRaw()) && finite(part.animRotRaw()) && finite(part.animScaleRaw()),
                            "CPU sampled pose is finite");
                }
            }
            releaseCount = engine.released;
            releasedBytes = engine.releasedBytes;
            retired.add(new WeakReference<>(runtime));
        } finally {
            if (runtime != null) {
                runtime.close();
                drain.run();
                require(runtime.isClosed() && runtime.audio().names().isEmpty() && runtime.subEntityInstances().size() == 0,
                        "model-owned audio and child instances clear on unload");
            }
        }
        require(images.stream().allMatch(NativeImage::isClosed), "all observed native images close");
        require(engine.released - releaseCount == buffers && engine.releasedBytes - releasedBytes == bytes,
                "every loaded PCM buffer is handed to native release exactly once");
        return new Measurement(iteration, "ysm-" + candidate.container().getFileName(),
                previousVisits == 0 ? "runtime_data" : "portable_nbt", previousVisits == 0,
                loadMilliseconds, sourceReadMilliseconds, List.copyOf(samples), images.size(), buffers, bytes);
    }

    private static void compileFigura(Path path) throws Exception {
        var avatar = LocalAvatarLoader.compileFiguraAvatar(path, new UUID(0, 810));
        require(avatar.contains("models") && !avatar.contains("ysm"), "ordinary Figura compilation route remains intact");
    }

    private static List<Candidate> discover(Path root) throws Exception {
        List<Candidate> candidates = new ArrayList<>();
        try (var files = Files.list(root)) {
            for (Path path : files.sorted().toList()) {
                if (Files.isDirectory(path)) {
                    if (LocalAvatarFetcher.isAvatar(path)) {
                        candidates.add(new Candidate(path, null, true));
                    } else if (YsmAvatarDetector.isYsmAvatar(path)) {
                        candidates.add(new Candidate(path, null, false));
                    }
                } else if (path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ysm")) {
                    candidates.add(new Candidate(path, null, false));
                } else if (path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    try (var archive = FileSystems.newFileSystem(path, (ClassLoader) null);
                         var contents = Files.walk(archive.getPath("/"))) {
                        for (Path metadata : contents.filter(file -> file.getFileName() != null
                                && file.getFileName().toString().equals("avatar.json")).toList()) {
                            candidates.add(new Candidate(path, metadata.getParent().toString(), true));
                        }
                    }
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static Avatar owner(Unsafe unsafe) throws Exception {
        Avatar owner = (Avatar) unsafe.allocateInstance(Avatar.class);
        install(owner, "owner", new UUID(0, 811));
        install(owner, "controls", new AvatarControlRuntime());
        install(owner, "molangContext", new Avatar.MolangContext(owner));
        return owner;
    }

    private static void install(Avatar owner, String name, Object value) throws Exception {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static boolean finite(FiguraVec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static void collectRetired(List<WeakReference<YsmModelRuntime>> retired) throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            System.gc();
            Thread.sleep(20);
            if (retired.stream().noneMatch(reference -> reference.get() != null)) {
                return;
            }
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long directBufferBytes() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .filter(pool -> pool.getName().equals("direct")).mapToLong(BufferPoolMXBean::getMemoryUsed).sum();
    }

    private static double milliseconds(long start) {
        return (System.nanoTime() - start) / 1_000_000d;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingManager extends SoundManager implements SoundManagerAccessor {
        private RecordingEngine engine;

        private RecordingManager() {
            super(null);
        }

        @Override
        public SoundEngine getSoundEngine() {
            return engine;
        }
    }

    private static final class RecordingEngine extends SoundEngine implements SoundEngineAccessor {
        private long released;
        private long releasedBytes;

        private RecordingEngine() {
            super(null, null, null);
        }

        @Override
        public void figura$releaseBuffer(SoundBuffer buffer) {
            released++;
            releasedBytes += buffer.size();
            buffer.discardAlBuffer();
        }

        @Override
        public void figura$addSound(LuaSound sound) {
            throw new AssertionError("CPU sampling must not start native audio");
        }

        @Override
        public ChannelAccess.ChannelHandle figura$createHandle(UUID owner, String name, Library.Pool pool) {
            throw new AssertionError("CPU sampling must not allocate audio channels");
        }

        @Override
        public void figura$stopSound(UUID owner, String name) {
            throw new AssertionError("No playback handles were allocated");
        }

        @Override
        public void figura$stopAllSounds() {
            throw new AssertionError("No playback handles were allocated");
        }

        @Override
        public float figura$getVolume(SoundSource source) {
            return 1;
        }

        @Override
        public SoundBufferLibrary figura$getSoundBuffers() {
            throw new AssertionError("CPU sampling must not load native audio resources");
        }

        @Override
        public boolean figura$isPlaying(UUID owner) {
            return false;
        }

        @Override
        public boolean figura$isEngineActive() {
            return false;
        }
    }
}
