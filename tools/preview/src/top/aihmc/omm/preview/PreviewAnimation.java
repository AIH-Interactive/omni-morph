package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;

/** Shared parsed clip plus separately owned playback state; uses the original YSM sampler. */
final class PreviewAnimation implements AutoCloseable {
    final PreviewAudio audio = new PreviewAudio();
    final PreviewSettings settings;
    final PreviewSkeleton skeleton;
    final YsmAnimationClip clip;
    private final JsonObject states;
    private final long seed;
    private final List<YsmAnimationEvent> eventTimeline;
    private final PreviewMesh mesh;
    private final PreviewControllerPlayer.Definition controllerDefinition;
    private final PreviewLua.Program luaProgram;
    private final JsonObject configuredControls;
    private final PreviewActionPlayer.Definition actionDefinition;
    private final String figuraClip;
    private final double particleGroundY;

    PreviewAnimation(PreviewMesh mesh, PreviewConfig config) {
        if (mesh.skeleton == null) {
            throw new UnsupportedOperationException("Animated playback currently requires a YSM skeleton");
        }
        skeleton = mesh.skeleton;
        this.mesh = mesh;
        try {
            settings = PreviewSettings.open(config, mesh.figuraParts == null);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Unable to load preview model settings", failure);
        }
        particleGroundY = config.ground.enabled ? mesh.minimumY : Double.NEGATIVE_INFINITY;
        states = config.states.deepCopy();
        seed = config.scene.seed;
        configuredControls = config.scripts.controls.deepCopy();
        figuraClip = config.animation.clip;
        if (mesh.figuraParts != null && !config.animation.mode.equals("figura")) {
            throw new IllegalArgumentException("Figura scripted model playback requires animation.mode=figura");
        }
        if (mesh.figuraParts == null && config.animation.mode.equals("figura")) {
            throw new IllegalArgumentException("animation.mode=figura requires a Figura model");
        }
        if (mesh.figuraParts != null && config.animation.actions) {
            throw new IllegalArgumentException("animation.actions is the YSM declarative action host");
        }
        actionDefinition = config.animation.actions ? new PreviewActionPlayer.Definition(mesh, config) : null;
        if (config.scripts.enabled) {
            var source = config.configFile.getParent().resolve(config.scene.models.get(0)).normalize();
            try {
                luaProgram = PreviewLua.Program.load(source);
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException("Unable to load model Lua resources: " + source, failure);
            }
        } else {
            luaProgram = null;
        }
        if (mesh.figuraParts != null) {
            controllerDefinition = null;
            clip = null;
            eventTimeline = List.of();
            return;
        }
        if (config.animation.mode.equals("controller")) {
            controllerDefinition = new PreviewControllerPlayer.Definition(mesh, config);
            clip = null;
            eventTimeline = List.of();
            return;
        }
        controllerDefinition = null;
        var compiler = new PreviewMolang(states, seed);
        YsmAnimationClip selected = null;
        for (String source : mesh.animationSources) {
            JsonObject root = JsonParser.parseString(source).getAsJsonObject();
            var animations = root.getAsJsonObject("animations");
            if (animations != null && animations.has(config.animation.clip)) {
                // Only the explicitly selected clip is activated, as with a named player request.
                JsonObject input = new JsonObject();
                JsonObject selection = new JsonObject();
                selection.add(config.animation.clip, animations.get(config.animation.clip));
                input.add("animations", selection);
                selected = YsmAnimationParser.parse(input.toString(), compiler.engine()).get(config.animation.clip);
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException(
                "Animation clip not found: " + config.animation.clip + " in " + mesh.label);
        }
        for (var event : selected.events) {
            if (!event.type().equals("timeline") && !event.type().equals("sound")) {
                throw new UnsupportedOperationException("Animation " + selected.name + " requests unsupported "
                    + event.type() + " event: " + event.value());
            }
        }
        clip = selected;
        eventTimeline = selected.events.stream().sorted(Comparator.comparingDouble(YsmAnimationEvent::time)).toList();
    }

    State newState() {
        return new State();
    }

    @Override
    public void close() {
        audio.close();
    }

    String description() {
        String playback = mesh.figuraParts != null
            ? "Figura scripted parts=" + skeleton.names.length + "; clips=" + mesh.figuraParts.animations.size()
            : controllerDefinition == null ? "YSM clip + Molang: " + clip.name
                                           : "YSM controllers + Molang: " + controllerDefinition.controllers.size();
        return playback + (actionDefinition == null ? "" : "; YSM actions=" + actionDefinition.metadata.size())
            + (luaProgram == null ? "; Lua scripts disabled" : "; Figura LuaJ scripts=" + luaProgram.sources().size());
    }

    private PreviewLua createLua(PreviewMolang host) {
        if (luaProgram == null && actionDefinition == null && mesh.figuraParts == null) {
            return null;
        }
        try {
            PreviewLua lua =
                new PreviewLua(luaProgram == null ? new PreviewLua.Program(java.util.Map.of(), List.of()) : luaProgram,
                    states, seed, host, audio, settings);
            lua.particles.ground(particleGroundY);
            if (host.effects != null)
                host.effects.attachLua(lua);
            if (actionDefinition != null) {
                lua.attachYsm(
                    actionDefinition, mesh, states.has("language") ? states.get("language").getAsString() : "en_us");
            }
            if (mesh.figuraParts != null) {
                lua.attachFigura(mesh.figuraParts);
            }
            if (mesh.vanilla != null) {
                lua.attachVanilla(mesh.vanilla);
            }
            lua.initialize();
            lua.applyControls(configuredControls);
            if (lua.ysm() != null) {
                lua.ysm().installWheel();
            }
            if (mesh.figuraParts != null && !figuraClip.isBlank()) {
                var matches = lua.animations.values()
                                  .stream()
                                  .filter(animation
                                      -> figuraClip.equals(animation.modelName + "." + animation.name)
                                          || figuraClip.equals(animation.name))
                                  .toList();
                if (matches.size() != 1) {
                    throw new IllegalArgumentException(
                        "Figura animation.clip must select exactly one clip: " + figuraClip);
                }
                matches.get(0).play();
            }
            return lua;
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Unable to initialize Lua host", failure);
        }
    }

    final class State {
        PreviewParticles particles() {
            return effects == null ? (lua == null ? null : lua.particles) : effects.particles.particles;
        }

        PreviewMolang molang() {
            return host;
        }
        private PreviewMolang host = new PreviewMolang(states, seed);
        private PreviewYsmEffects effects = createEffects();
        private PreviewLua lua = createLua(host);
        private final YsmBonePose[] sampled = new YsmBonePose[skeleton.names.length];
        private final YsmBonePose[] lastFinite = new YsmBonePose[skeleton.names.length];
        private final Matrix4f[] world = new Matrix4f[skeleton.names.length];
        final Matrix4f[] palette = new Matrix4f[skeleton.names.length];
        private FloatBuffer publishedPalette;
        private boolean publishedPaletteReady;
        private boolean paletteObjectsCurrent = true;
        private final boolean[] defaults = mesh.figuraParts == null
            ? YsmVisibilityDefaults.visibility(mesh, host, false)
            : skeleton.defaultVisible.clone();
        private final boolean[] visible = defaults.clone();
        private final boolean[] effectiveVisible = new boolean[skeleton.names.length];
        private float previousElapsed = Float.NEGATIVE_INFINITY;
        private PreviewControllerPlayer controllerPlayer = controllerDefinition == null
            ? null
            : new PreviewControllerPlayer(controllerDefinition, host, skeleton, defaults);
        private java.util.Map<String, YsmBonePose> controllerPose = java.util.Map.of();

        private State() {
            for (int index = 0; index < world.length; index++) {
                world[index] = new Matrix4f();
                palette[index] = new Matrix4f();
            }
            seedActionBase();
        }

        private PreviewYsmEffects createEffects() {
            if (mesh.figuraParts != null)
                return null;
            var result = new PreviewYsmEffects(audio, null, host, mesh.ysmResources, states, seed);
            result.particles.particles.ground(particleGroundY);
            host.effects = result;
            host.bones = new PreviewYsmBones(
                skeleton, mesh.vanilla == null ? skeleton.names.length : mesh.vanilla.modelBoneCount());
            host.functions = new PreviewYsmFunctions(host, mesh.ysmResources.functions());
            return result;
        }

        private void seedActionBase() {
            if (lua != null && lua.ysm() != null && clip != null) {
                lua.ysm().animations().play(clip.name, clip.loop, 1);
            }
        }

        void sample(long frame, float requestedTime) {
            float elapsed = Math.max(0, requestedTime);
            if (elapsed < previousElapsed) {
                if (effects != null)
                    effects.close();
                if (lua != null)
                    lua.close();
                host = new PreviewMolang(states, seed);
                effects = createEffects();
                lua = createLua(host);
                if (controllerDefinition != null) {
                    controllerPlayer = new PreviewControllerPlayer(controllerDefinition, host, skeleton, defaults);
                    controllerPose = java.util.Map.of();
                }
                Arrays.fill(lastFinite, null);
                previousElapsed = Float.NEGATIVE_INFINITY;
                seedActionBase();
            }
            if (host.bones == null) {
                sampleFrame(frame, elapsed);
                return;
            }
            host.bones.beginFrame(frame);
            try (var scope = host.bones.beginEvaluation()) {
                sampleFrame(frame, elapsed);
                host.bones.captureOutput(sampled);
            }
        }

        private void sampleFrame(long frame, float elapsed) {
            if (lua != null) {
                lua.inputTime(elapsed);
            }
            try (var scope = host.executionBudget().beginFrame(frame)) {
                host.filters.update(top.aihmc.omnimorph.model.ysm.YsmFilterRuntime.Domain.PREVIEW, elapsed * 20, frame,
                    host.executionBudget());
            }
            if (effects != null) {
                effects.tick();
                if (lua == null && controllerPlayer == null)
                    effects.particles.advance(elapsed);
            }
            if (mesh.figuraParts != null) {
                if (lua != null) {
                    lua.frame(elapsed);
                }
                previousElapsed = elapsed;
                return;
            }
            if (lua != null && lua.ysm() != null && controllerPlayer == null) {
                sampleActions(frame, elapsed);
                previousElapsed = elapsed;
                return;
            }
            if (lua != null && controllerPlayer == null) {
                lua.frame(elapsed);
            }
            if (controllerPlayer != null) {
                sampleControllers(frame, elapsed);
                previousElapsed = elapsed;
                return;
            }
            float time =
                clip.length > 0 ? (clip.loop ? elapsed % clip.length : Math.min(elapsed, clip.length)) : elapsed;
            float delta = Float.isFinite(previousElapsed) ? elapsed - previousElapsed : 0;
            try (var scope = host.beginFrame(frame, time, delta)) {
                host.lifeTime(elapsed);
                events(previousElapsed, elapsed);
                float weight = clip.evaluateBlendWeight(host.evaluator());
                boolean ended =
                    clip.length > 0 && elapsed > clip.length && clip.loopMode == YsmAnimationClip.LoopMode.ONCE;
                System.arraycopy(defaults, 0, visible, 0, visible.length);
                for (int index = 0; index < sampled.length; index++) {
                    var bone = clip.boneAnimations.get(skeleton.names[index]);
                    if (bone == null || ended || weight == 0) {
                        sampled[index] = null;
                        continue;
                    }
                    YsmBonePose pose = YsmAnimationSampler.sampleBone(bone, time, host.evaluator());
                    pose = YsmAnimationSampler.holdFinite(pose, lastFinite[index]);
                    lastFinite[index] = pose;
                    sampled[index] = weighted(pose, weight);
                    float[] scale = sampled[index].scale();
                    if (scale != null) {
                        float magnitude =
                            Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2])));
                        if (magnitude < 0.0001f) {
                            visible[index] = false;
                        } else if (!YsmVisibilityDefaults.isBaseAnimation(clip.name)) {
                            visible[index] = true;
                        }
                    }
                }
            }
            previousElapsed = elapsed;
        }

        private void sampleControllers(long frame, float elapsed) {
            float delta = Float.isFinite(previousElapsed) ? elapsed - previousElapsed : elapsed;
            try (var scope = host.beginFrame(frame, elapsed, delta)) {
                if (!Float.isFinite(previousElapsed)) {
                    host.stepClock(0, 0);
                    controllerPose = advanceController(0);
                }
                double clock = Float.isFinite(previousElapsed) ? previousElapsed : 0;
                // Replay fixed steps on first seek as well, so a fixed-time capture runs real transitions.
                while (elapsed - clock > 0.0000001) {
                    float step = (float) Math.min(1.0 / 60, elapsed - clock);
                    clock += step;
                    if (lua != null) {
                        lua.advanceTicks((float) clock);
                    } else {
                        effects.particles.advance((float) clock);
                    }
                    host.stepClock((float) clock, step);
                    controllerPose = advanceController(step);
                }
                if (lua != null) {
                    lua.render(elapsed);
                    lua.controls.hydrateLiveValues(lua);
                    controllerPose = lua.ysm() == null
                        ? controllerPlayer.sampleCurrent()
                        : controllerPlayer.sampleCurrent(lua.ysm().animations().sample(defaults));
                }
                System.arraycopy(defaults, 0, visible, 0, visible.length);
                for (int index = 0; index < sampled.length; index++) {
                    YsmBonePose pose = controllerPose.get(skeleton.names[index]);
                    sampled[index] = pose;
                    float[] scale = pose == null ? null : pose.scale();
                    if (scale != null) {
                        visible[index] =
                            Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2]))) >= 0.0001f;
                    }
                }
                if (lua != null && lua.ysm() != null) {
                    lua.ysm().animations().capture(controllerPose);
                }
            }
        }

        private java.util.Map<String, YsmBonePose> advanceController(float step) {
            if (lua == null || lua.ysm() == null) {
                return controllerPlayer.update(step);
            }
            controllerPlayer.advanceOnly(step);
            lua.ysm().animations().advance(step);
            return controllerPlayer.sampleCurrent(lua.ysm().animations().sample(defaults));
        }

        private void sampleActions(long frame, float elapsed) {
            float delta = Float.isFinite(previousElapsed) ? elapsed - previousElapsed : elapsed;
            try (var scope = host.beginFrame(frame, elapsed, delta)) {
                lua.frame(elapsed);
                lua.controls.hydrateLiveValues(lua);
                var player = lua.ysm().animations();
                player.advance(delta);
                var poses = player.sample(defaults);
                player.capture(poses);
                System.arraycopy(defaults, 0, visible, 0, visible.length);
                for (int index = 0; index < sampled.length; index++) {
                    var pose = poses.get(skeleton.names[index]);
                    sampled[index] = pose;
                    float[] scale = pose == null ? null : pose.scale();
                    if (scale != null) {
                        visible[index] =
                            Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2]))) >= 0.0001f;
                    }
                }
            }
        }

        String controllerState(String name) {
            if (controllerPlayer == null) {
                throw new IllegalStateException("Not in controller playback mode");
            }
            return controllerPlayer.runtime.currentState(name);
        }

        void postRender(float time) {
            if (lua != null) {
                lua.postFrame(time);
            }
        }

        PreviewLua lua() {
            return lua;
        }

        private void events(float previous, float elapsed) {
            if (eventTimeline.isEmpty()) {
                return;
            }
            effects.events(clip, previous, elapsed, clip.loop, null, this);
        }

        void updateBones() {
            updateBones(null);
        }

        void updateBones(PreviewNativeVertices nativeVertices) {
            updateBones(nativeVertices, true);
        }

        void updateBonesForRender(PreviewNativeVertices nativeVertices) {
            updateBones(nativeVertices, false);
        }

        private void updateBones(PreviewNativeVertices nativeVertices, boolean readPaletteObjects) {
            publishedPaletteReady = false;
            paletteObjectsCurrent = true;
            if (nativeVertices == null) {
                updateBoneMatrices(null);
                return;
            }
            if (publishedPalette == null) {
                // JVM-owned storage belongs to this animation state, not the shared model renderer.
                publishedPalette = ByteBuffer.allocateDirect(Math.multiplyExact(palette.length, 64))
                                       .order(ByteOrder.nativeOrder()).asFloatBuffer();
                writePalette(publishedPalette);
            }
            nativeVertices.beginPublication(publishedPalette, readPaletteObjects);
            boolean written;
            try {
                updateBoneMatrices(nativeVertices);
            } finally {
                written = nativeVertices.endPublication();
            }
            if (!written) {
                writePalette(publishedPalette);
            }
            publishedPaletteReady = true;
            paletteObjectsCurrent = !written || readPaletteObjects;
        }

        private void updateBoneMatrices(PreviewNativeVertices nativeVertices) {
            if (mesh.figuraParts != null) {
                if (lua != null) {
                    lua.figura().update(world, palette, nativeVertices, 0);
                    lua.updateVanilla(palette, nativeVertices);
                } else {
                    for (int index = 0; index < palette.length; index++) {
                        palette[index].identity();
                        if (!skeleton.restVisible[index]) {
                            palette[index].scaling(0);
                        }
                    }
                }
                return;
            }
            for (int index : skeleton.order) {
                int parent = skeleton.parents[index];
                Matrix4f matrix = world[index];
                if (nativeVertices != null || parent < 0) {
                    matrix.identity();
                    if (parent < 0 && lua != null && lua.ysm() != null) {
                        matrix.scale(lua.ysm().widthScale(), lua.ysm().heightScale(), lua.ysm().widthScale());
                    }
                } else {
                    matrix.set(world[parent]);
                }
                Vector3f pivot = skeleton.pivots[index], rest = skeleton.rotations[index];
                YsmBonePose pose = sampled[index];
                float[] position = pose == null ? null : pose.position();
                float[] rotation = pose == null ? null : pose.rotation();
                float[] scale = pose == null ? null : pose.scale();
                matrix.translate(pivot);
                if (position != null) {
                    // YsmRenderer.applyBoneTransform: animation position flips X only.
                    matrix.translate(-position[0] / 16, position[1] / 16, position[2] / 16);
                }
                matrix.rotateZ(radians(rest.z + component(rotation, 2)))
                    .rotateY(radians(rest.y - component(rotation, 1)))
                    .rotateX(radians(rest.x - component(rotation, 0)));
                if (scale != null) {
                    matrix.scale(scale[0], scale[1], scale[2]);
                }
                matrix.translate(-pivot.x, -pivot.y, -pivot.z);
                if (nativeVertices == null) {
                    palette[index].set(matrix).mul(skeleton.inverseBindWorld[index]);
                    effectiveVisible[index] = visible[index] && (parent < 0 || effectiveVisible[parent]);
                    if (!effectiveVisible[index])
                        palette[index].scaling(0);
                } else {
                    effectiveVisible[index] = visible[index];
                }
            }
            if (nativeVertices != null)
                nativeVertices.compose(0, world.length, world, palette, effectiveVisible);
            if (lua != null)
                lua.updateVanilla(palette, nativeVertices);
        }

        void recordRenderedPivots() {
            if (host.bones != null) {
                float width = lua != null && lua.ysm() != null ? lua.ysm().widthScale() : 1;
                float height = lua != null && lua.ysm() != null ? lua.ysm().heightScale() : 1;
                host.bones.recordRenderedPivots(world, sampled, effectiveVisible, width, height);
            }
        }

        void writePalette(FloatBuffer output) {
            paletteMatrices();
            output.clear();
            for (Matrix4f matrix : palette) {
                // Use NIO writes: JOML's Unsafe FloatBuffer writer assumes native-address storage.
                output.put(matrix.m00()).put(matrix.m01()).put(matrix.m02()).put(matrix.m03());
                output.put(matrix.m10()).put(matrix.m11()).put(matrix.m12()).put(matrix.m13());
                output.put(matrix.m20()).put(matrix.m21()).put(matrix.m22()).put(matrix.m23());
                output.put(matrix.m30()).put(matrix.m31()).put(matrix.m32()).put(matrix.m33());
            }
            output.flip();
        }

        FloatBuffer paletteBuffer(FloatBuffer scratch) {
            if (publishedPaletteReady) {
                return publishedPalette;
            }
            writePalette(scratch);
            return scratch;
        }

        Matrix4f[] paletteMatrices() {
            if (!paletteObjectsCurrent) {
                for (int bone = 0; bone < palette.length; bone++) {
                    palette[bone].set(bone * 16, publishedPalette);
                }
                paletteObjectsCurrent = true;
            }
            return palette;
        }

        private YsmBonePose weighted(YsmBonePose pose, float weight) {
            float[] position = pose.position(), rotation = pose.rotation(), scale = pose.scale();
            for (int axis = 0; axis < 3; axis++) {
                if (position != null) {
                    position[axis] *= weight;
                }
                if (rotation != null) {
                    rotation[axis] *= weight;
                }
                if (scale != null) {
                    scale[axis] = 1 + (scale[axis] - 1) * weight;
                }
            }
            return new YsmBonePose(position, rotation, scale);
        }
    }

    private static float component(float[] values, int axis) {
        return values == null ? 0 : values[axis];
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
