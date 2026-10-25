package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget;
import top.aihmc.omnimorph.model.outfit.TrailBuffer;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import org.figuramc.figura.permissions.Permissions;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Native custom particles sharing Minecraft's particle pass, texture binding and lifecycle. */
public final class CustomParticle extends SingleQuadParticle {
    private static final OutfitLimits LOCAL_LIMITS = OutfitLimits.defaults();
    private enum RenderMode {
        BILLBOARD, LAYERED, RIBBON
    }

    private final Avatar owner;
    private final PreviewParticleScope preview;
    private final FiguraTexture texture;
    private final long seed;
    private Map<FiguraTexture, FiguraTexture.Lease> textureLeases = Map.of();
    private OutfitInstructionBudget.ParticleFaceLease faceLease;
    private CustomParticleLayer baseLayer;
    private List<CustomParticleLayer> layers = List.of();
    private CustomParticleLayer drawingLayer;
    private RenderMode mode = RenderMode.BILLBOARD;
    private boolean anchorTrail;
    private CustomParticlePipelines.Blend blend = CustomParticlePipelines.Blend.ALPHA;
    private ParticleSpriteSheet sheet;
    private ParticleSpriteSheet.Uv uv = new ParticleSpriteSheet.Uv(0, 1, 0, 1);
    private boolean emissive;
    private RibbonOptions ribbonOptions = RibbonOptions.defaults();
    private TrailBuffer ribbonTrail;
    private String renderError;
    private Vec3 acceleration = Vec3.ZERO;
    private ParticleMotion motion;
    private int motionStartAge;
    private String motionError;
    private double rotationDegrees;
    private float angularVelocity;
    private boolean trajectoryTick;
    private final CustomParticleCallbacks callbacks = new CustomParticleCallbacks(this);
    private ParticleLightSettings lightSettings = ParticleLightSettings.defaults();

    public CustomParticle(Avatar owner, ClientLevel level, FiguraTexture texture, Vec3 position, Vec3 velocity) {
        super(level, position.x, position.y, position.z, null);
        if (texture == null || !texture.belongsTo(owner)) {
            throw new LuaError("Custom particle texture must belong to this avatar instance");
        }
        this.owner = owner;
        this.preview = owner.previewParticles;
        this.texture = texture;
        seed = random.nextLong();
        baseLayer = CustomParticleLayer.base(texture, blend);
        sheet = ParticleSpriteSheet.read(texture.getWidth(), texture.getHeight(), new LuaTable(), seed, 0);
        setParticleSpeed(velocity.x, velocity.y, velocity.z);
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        lifetime = 20;
        quadSize = .1f;
    }

    Avatar owner() {
        return owner;
    }

    ClientLevel world() {
        return level;
    }

    public PreviewParticleScope preview() {
        return preview;
    }

    void retainTexture() {
        textureLeases = retainLayers(currentLayers());
        faceLease = OutfitInstructionBudget.retainParticleFaces(owner);
        callbacks.updateLease();
    }

    public void setTickCallback(LuaFunction function) {
        callbacks.set(false, function);
    }

    public void setFrameCallback(LuaFunction function) {
        callbacks.set(true, function);
    }

    public String getCallbackError() {
        return callbacks.error();
    }

    boolean canActivateCallbacks() {
        return callbacks.canActivate();
    }

    ParticleLightSettings lightSettings() {
        return lightSettings;
    }

    Vec3 lightPosition(float partialTick) {
        if (ribbon() && anchorTrail) {
            var snapshot = owner.cosmeticContext.trailSnapshot(partialTick);
            if (snapshot == null || snapshot.world() != level) {
                return null;
            }
            var head = snapshot.path().getLast().sample();
            return new Vec3(head.x(), head.y(), head.z());
        }
        return new Vec3(xo + (x - xo) * partialTick, yo + (y - yo) * partialTick, zo + (z - zo) * partialTick);
    }

    void disableLightFromPolicy() {
        lightSettings = new ParticleLightSettings(false, lightSettings.level(), lightSettings.radius(), lightSettings.interval());
    }

    public void setDynamicLight(boolean enabled) {
        setLightSettings(new ParticleLightSettings(enabled, lightSettings.level(), lightSettings.radius(), lightSettings.interval()));
    }

    public boolean dynamicLight() {
        return lightSettings.enabled();
    }

    public void setLightLevel(int level) {
        setLightSettings(new ParticleLightSettings(lightSettings.enabled(), level, lightSettings.radius(), lightSettings.interval()));
    }

    public int lightLevel() {
        return lightSettings.level();
    }

    public void setLightRadius(double radius) {
        setLightSettings(new ParticleLightSettings(lightSettings.enabled(), lightSettings.level(), radius, lightSettings.interval()));
    }

    public double lightRadius() {
        return lightSettings.radius();
    }

    public void setLightInterval(int interval) {
        setLightSettings(new ParticleLightSettings(lightSettings.enabled(), lightSettings.level(),
                lightSettings.radius(), interval));
    }

    public int lightInterval() {
        return lightSettings.interval();
    }

    private void setLightSettings(ParticleLightSettings replacement) {
        replacement.validate(limits());
        try {
            if (preview != null) {
                preview.validateLight(this, replacement);
                lightSettings = replacement;
                return;
            }
            if (active()) {
                if (replacement.enabled() && level != net.minecraft.client.Minecraft.getInstance().level) {
                    throw new LuaError("PARTICLE_DYNAMIC_LIGHT_WORLD_UNAVAILABLE");
                }
                if (replacement.enabled() && !lightSettings.enabled()
                        && !ParticleDynamicLights.intervalSelects(this, replacement.interval())) {
                    lightSettings = new ParticleLightSettings(false, replacement.level(), replacement.radius(),
                            replacement.interval());
                    return;
                }
                ParticleDynamicLights.update(this, replacement);
            }
            lightSettings = replacement;
        } catch (LuaError rejected) {
            if (!replacement.enabled() || !"PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT".equals(rejected.getMessage())) {
                throw rejected;
            }
            // The wearer's light capacity is full: keep the particle and its settings, but without illumination,
            // as the per-frame policy does. A busy budget must not stop the whole accessory script.
            lightSettings = new ParticleLightSettings(false, replacement.level(), replacement.radius(), replacement.interval());
        }
    }

    void runFrameCallback(float partialTick) {
        callbacks.run(true, partialTick);
    }

    public boolean activate() {
        return CustomParticleRuntime.activate(this);
    }

    public boolean active() {
        return !textureLeases.isEmpty() && isAlive();
    }

    public int layerCount() {
        return usesBaseLayer(mode, layers) ? 1 : layers.size();
    }

    boolean ribbon() {
        return mode == RenderMode.RIBBON;
    }

    boolean independentRibbon() {
        return ribbon() && !anchorTrail;
    }

    public boolean anchorTrail() {
        return anchorTrail;
    }

    public void setAnchorTrail(boolean enabled) {
        if (enabled == anchorTrail) {
            return;
        }
        if (enabled && (owner.cosmeticContext == null || !owner.cosmeticContext.hasTrailSource())) {
            throw new LuaError("CUSTOM_PARTICLE_ANCHOR_TRAIL_UNAVAILABLE");
        }
        CustomParticleRuntime.changeRibbonMode(this, independentRibbon(), ribbon() && !enabled);
        anchorTrail = enabled;
        if (ribbonTrail != null) {
            ribbonTrail.clear();
        }
        renderError = null;
    }

    public void setRibbon(LuaTable options) {
        var replacement = ribbonOptions.update(options);
        var bright = options.rawget("emissive");
        if (!bright.isnil() && !bright.isboolean()) {
            throw new LuaError("Ribbon emissive must be a boolean");
        }
        var light = options.rawget("light");
        if (!light.isnil()) {
            setLightSettings(lightSettings.alias(light));
        }
        boolean samplingChanged = replacement.sampleDistance() != ribbonOptions.sampleDistance()
                || replacement.sampleInterval() != ribbonOptions.sampleInterval()
                || replacement.teleportDistance() != ribbonOptions.teleportDistance();
        ribbonOptions = replacement;
        if (!bright.isnil()) {
            emissive = bright.toboolean();
        }
        if (samplingChanged && ribbonTrail != null) {
            ribbonTrail.clear();
        }
    }

    public LuaTable getRibbon() {
        var result = ribbonOptions.toLua();
        result.set("emissive", org.luaj.vm2.LuaValue.valueOf(emissive));
        var light = new LuaTable();
        light.set("enabled", org.luaj.vm2.LuaValue.valueOf(lightSettings.enabled()));
        light.set("level", lightSettings.level());
        light.set("radius", org.luaj.vm2.LuaValue.valueOf(lightSettings.radius()));
        light.set("interval", lightSettings.interval());
        result.set("light", light);
        return result;
    }

    public String getRenderError() {
        return renderError;
    }

    public void setAcceleration(Vec3 value) {
        ParticleMotion.finite(value);
        acceleration = value;
    }

    public Vec3 acceleration() {
        return acceleration;
    }

    public void setMotion(LuaTable options) {
        var prepared = ParticleMotion.read(options, new Vec3(x, y, z), level);
        Vec3 initial = prepared.mode().equals("free") || prepared.mode().equals("tracking") ? null
                : prepared.position(new Vec3(x, y, z), 0);
        if (initial != null) {
            setPos(initial.x, initial.y, initial.z);
            xo = x;
            yo = y;
            zo = z;
        }
        motion = prepared.trajectory() == null ? null : prepared;
        motionStartAge = age;
        motionError = null;
        if (ribbonTrail != null) {
            ribbonTrail.clear();
        }
    }

    public String motionMode() {
        return motion == null ? "free" : motion.mode();
    }

    public String getMotionError() {
        return motionError;
    }

    public void setRotation(float degrees) {
        finite(degrees, 0, 0);
        rotationDegrees = degrees % 360.0;
        roll = oRoll = (float) Math.toRadians(rotationDegrees);
    }

    public double rotation() {
        return rotationDegrees;
    }

    public void setAngularVelocity(float degreesPerTick) {
        finite(degreesPerTick, 0, 0);
        angularVelocity = degreesPerTick;
    }

    public float angularVelocity() {
        return angularVelocity;
    }

    public String renderMode() {
        return mode.name();
    }

    public void setRenderMode(String value) {
        RenderMode requested;
        try {
            requested = RenderMode.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new LuaError("Unknown custom particle render mode");
        }
        replaceLayers(baseLayer, layers, requested);
    }

    public void setLayers(LuaTable declaration) {
        if (declaration == null) {
            throw new LuaError("Particle layers must be a table");
        }
        int count = CustomParticleLayer.arrayLength(declaration, limits().maxParticleLayers());
        if (count == 0) {
            throw new LuaError("Layered particles require at least one layer");
        }
        var prepared = new ArrayList<CustomParticleLayer>(count);
        for (int index = 0; index < count; index++) {
            var value = declaration.rawget(index + 1);
            if (!value.istable()) {
                throw new LuaError("Each particle layer must be a table");
            }
            prepared.add(CustomParticleLayer.read(owner, texture, value.checktable(), index, seed, age));
        }
        replaceLayers(baseLayer, List.copyOf(prepared), ribbon() ? RenderMode.RIBBON : RenderMode.LAYERED);
    }

    public void setBlendMode(String value) {
        CustomParticlePipelines.Blend requested = CustomParticleLayer.blend(value);
        replaceLayers(CustomParticleLayer.base(texture, requested), layers, mode);
        blend = requested;
    }

    public String blendMode() {
        return blend.name().toLowerCase(java.util.Locale.ROOT);
    }

    OutfitLimits limits() {
        if (preview != null) {
            return preview.limits();
        }
        return owner.cosmeticContext == null ? LOCAL_LIMITS : owner.cosmeticContext.limits();
    }

    List<CustomParticleLayer> currentLayers() {
        return usesBaseLayer(mode, layers) ? List.of(baseLayer) : layers;
    }

    private static boolean usesBaseLayer(RenderMode requested, List<CustomParticleLayer> prepared) {
        return requested == RenderMode.BILLBOARD || requested == RenderMode.RIBBON && prepared.isEmpty();
    }

    private void replaceLayers(CustomParticleLayer base, List<CustomParticleLayer> prepared, RenderMode requested) {
        List<CustomParticleLayer> visible = usesBaseLayer(requested, prepared) ? List.of(base) : prepared;
        if (visible.isEmpty() || visible.size() > limits().maxParticleLayers()) {
            throw new LuaError("CUSTOM_PARTICLE_LAYER_LIMIT");
        }
        Map<FiguraTexture, FiguraTexture.Lease> replacement = active() ? retainLayers(visible) : Map.of();
        try {
            CustomParticleRuntime.changeRibbonMode(this, independentRibbon(),
                    requested == RenderMode.RIBBON && !anchorTrail);
        } catch (RuntimeException | Error failure) {
            replacement.values().forEach(FiguraTexture.Lease::close);
            throw failure;
        }
        var previous = textureLeases;
        baseLayer = base;
        layers = prepared;
        mode = requested;
        if (!ribbon() && ribbonTrail != null) {
            ribbonTrail.clear();
            ribbonTrail = null;
        }
        renderError = null;
        textureLeases = replacement;
        previous.values().forEach(FiguraTexture.Lease::close);
    }

    private Map<FiguraTexture, FiguraTexture.Lease> retainLayers(List<CustomParticleLayer> visible) {
        var prepared = new IdentityHashMap<FiguraTexture, FiguraTexture.Lease>();
        try {
            for (CustomParticleLayer layer : visible) {
                if (!prepared.containsKey(layer.texture())) {
                    var existing = textureLeases.get(layer.texture());
                    prepared.put(layer.texture(), existing == null ? layer.texture().retain() : existing.retain());
                }
            }
            return prepared;
        } catch (RuntimeException | Error failure) {
            prepared.values().forEach(FiguraTexture.Lease::close);
            throw failure;
        }
    }

    public void setSpriteSheet(LuaTable options) {
        sheet = ParticleSpriteSheet.read(texture.getWidth(), texture.getHeight(), options, seed, age);
    }

    public void setFrame(int frame) {
        sheet.setFrame(frame, age);
    }

    public int frame() {
        return sheet.frameAt(age);
    }

    public void setPaused(boolean paused) {
        sheet.setPaused(paused, age);
        for (CustomParticleLayer layer : layers) {
            if (layer.sheet() != null) {
                layer.sheet().setPaused(paused, age);
            }
        }
    }

    public boolean paused() {
        return sheet.paused();
    }

    public void setEmissive(boolean value) {
        emissive = value;
    }

    public boolean emissive() {
        return emissive;
    }

    @Override
    public void setPos(double x, double y, double z) {
        finite(x, y, z);
        super.setPos(x, y, z);
    }

    @Override
    public void setParticleSpeed(double x, double y, double z) {
        finite(x, y, z);
        super.setParticleSpeed(x, y, z);
    }

    @Override
    public void setLifetime(int value) {
        if (value <= 0) {
            throw new LuaError("Custom particle lifetime must be positive");
        }
        super.setLifetime(value);
    }

    @Override
    public void tick() {
        if (!isAlive() || callbacks.running()) {
            return;
        }
        if (active() && (level != net.minecraft.client.Minecraft.getInstance().level
                || preview != null && !preview.accepts(owner, level))) {
            remove();
            return;
        }
        callbacks.run(false, 1);
        if (!isAlive()) {
            return;
        }
        oRoll = (float) Math.toRadians(rotationDegrees);
        roll = (float) Math.toRadians(rotationDegrees + angularVelocity);
        rotationDegrees = (rotationDegrees + angularVelocity) % 360;
        try {
            if (motion == null) {
                setParticleSpeed(xd + acceleration.x, yd + acceleration.y, zd + acceleration.z);
                super.tick();
            } else {
                Vec3 current = new Vec3(x, y, z);
                Vec3 next = motion.position(current, (double) age - motionStartAge + 1);
                if (next == null) {
                    motionError = "TARGET_UNAVAILABLE";
                    if (!motion.stopOnTargetLost()) {
                        remove();
                        return;
                    }
                    // Release the target immediately; a stopped particle keeps only its ordinary bounded lifetime.
                    motion = new ParticleMotion("stopped", (position, time) -> position, false);
                    next = current;
                }
                tickTrajectory(next.subtract(current));
            }
            finite(x, y, z);
        } catch (LuaError invalid) {
            motionError = "NON_FINITE_MOTION";
            remove();
            return;
        }
        if (ribbonTrail != null) {
            ribbonTrail.advance(level, Math.max(0, age - 1));
        }
    }

    private void tickTrajectory(Vec3 velocity) {
        float previousGravity = gravity;
        float previousFriction = friction;
        try {
            setParticleSpeed(velocity.x, velocity.y, velocity.z);
            gravity = 0;
            friction = 1;
            trajectoryTick = true;
            super.tick();
        } finally {
            gravity = previousGravity;
            friction = previousFriction;
            trajectoryTick = false;
        }
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (trajectoryTick || preview != null) {
            // Native move short-circuits permanently after a collision, even with hasPhysics=false.
            // A kinematic trajectory must apply its explicit position independently of that free-motion state.
            setPos(x + dx, y + dy, z + dz);
            onGround = false;
        } else {
            super.move(dx, dy, dz);
        }
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        if (!active() || ribbon()) {
            return;
        }
        // Iris shadow extraction uses the shadow camera; custom particles are camera-relative to the
        // main view and must not contribute a second, offset copy.
        if (org.figuramc.figura.utils.PlatformUtils.isModLoaded("iris")
                && top.aihmc.omnimorph.compat.IrisCompat.isShadowPass()) {
            return;
        }
        if (layerCount() > limits().maxParticleLayers()) {
            remove();
            return;
        }
        owner.prepareComplexityFrame();
        var budget = OutfitInstructionBudget.currentFaces(owner);
        int faces = layerCount();
        if (budget.allowance(owner, owner.complexity) < faces) {
            owner.noPermissions.add(Permissions.COMPLEXITY);
            return;
        }
        budget.charge(owner, owner.complexity, faces);
        owner.complexity.use(faces);
        float red = rCol;
        float green = gCol;
        float blue = bCol;
        float opacity = alpha;
        try {
            for (CustomParticleLayer layer : currentLayers()) {
                drawingLayer = layer;
                uv = layer.uvAt(age + partialTick, sheet);
                rCol = red * layer.tint().red();
                gCol = green * layer.tint().green();
                bCol = blue * layer.tint().blue();
                alpha = opacity * layer.tint().alpha();
                layer.texture().uploadIfDirty();
                CustomParticleRuntime.retainFrame(layer.texture(), textureLeases.get(layer.texture()));
                super.extract(state, camera, partialTick);
            }
        } finally {
            drawingLayer = null;
            rCol = red;
            gCol = green;
            bCol = blue;
            alpha = opacity;
        }
    }

    RibbonRenderState extractRibbon(Camera camera, Frustum frustum, float partialTick, int availableVertices) {
        if (!active() || !ribbon()) {
            return null;
        }
        if (layerCount() > limits().maxParticleLayers()) {
            remove();
            return null;
        }
        renderError = null;
        try {
            if (!Float.isFinite(partialTick)) {
                throw new IllegalArgumentException("INVALID_RIBBON_TIME");
            }
            double now;
            Vec3 position;
            List<TrailBuffer.PathSample> path;
            if (anchorTrail) {
                var snapshot = owner.cosmeticContext.trailSnapshot(partialTick);
                if (snapshot == null || snapshot.world() != level) {
                    return null;
                }
                now = snapshot.timeTicks();
                path = snapshot.path();
                var head = path.getLast().sample();
                position = new Vec3(head.x(), head.y(), head.z());
            } else {
                if (ribbonTrail == null) {
                    ribbonTrail = new TrailBuffer(limits());
                }
                now = Math.max(0, age - 1 + (double) partialTick);
                position = lightPosition(partialTick);
                ribbonTrail.sample(YsmRenderFrameClock.currentFrame(), level, this, now, position,
                        ribbonOptions.samplingPolicy());
                path = ribbonTrail.snapshotPath();
            }
            if (path.size() < 2) {
                return null;
            }
            AABB bounds = new AABB(position, position);
            for (var entry : path) {
                var sample = entry.sample();
                Vec3 point = new Vec3(sample.x(), sample.y(), sample.z());
                bounds = bounds.minmax(new AABB(point, point));
            }
            if (frustum != null && !frustum.isVisible(bounds.inflate(ribbonOptions.geometry().width() * .5))) {
                return null;
            }
            owner.prepareComplexityFrame();
            var budget = OutfitInstructionBudget.currentFaces(owner);
            int remainingFaces = budget.allowance(owner, owner.complexity);
            var right = camera.rotation().transform(new org.joml.Vector3f(1, 0, 0));
            var mesh = RibbonMesh.build(path, now, camera.position(), new Vec3(right.x, right.y, right.z),
                    ribbonOptions.geometry(), limits(), layerCount(), availableVertices, remainingFaces,
                    oRoll + (double) partialTick * (roll - oRoll));
            if (mesh.quads().isEmpty()) {
                return null;
            }
            var materials = new ArrayList<RibbonRenderState.Layer>(layerCount());
            try {
                for (CustomParticleLayer layer : currentLayers()) {
                    drawingLayer = layer;
                    materials.add(new RibbonRenderState.Layer(layer.renderLayer(), layer.uvAt(age + partialTick, sheet),
                            rCol * layer.tint().red(), gCol * layer.tint().green(), bCol * layer.tint().blue(),
                            alpha * layer.tint().alpha(), getLightCoords(partialTick)));
                }
            } finally {
                drawingLayer = null;
            }
            var state = new RibbonRenderState(mesh, materials, camera.position());
            // Both complete geometry budgets are reserved before any upload or native submission.
            CustomParticleRuntime.chargeRibbonVertices(this, mesh.submittedVertices());
            budget.charge(owner, owner.complexity, mesh.submittedFaces());
            owner.complexity.use(mesh.submittedFaces());
            for (CustomParticleLayer layer : currentLayers()) {
                layer.texture().uploadIfDirty();
                CustomParticleRuntime.retainFrame(layer.texture(), textureLeases.get(layer.texture()));
            }
            return state;
        } catch (IllegalArgumentException invalid) {
            renderError = invalid.getMessage();
            if ("RIBBON_VERTEX_LIMIT".equals(renderError) || "RIBBON_FACE_LIMIT".equals(renderError)) {
                owner.noPermissions.add(Permissions.COMPLEXITY);
            }
            return null;
        }
    }

    @Override
    public void remove() {
        super.remove();
        CustomParticleRuntime.removed(this);
        ParticleDynamicLights.remove(this);
        callbacks.close();
        motion = null;
        if (ribbonTrail != null) {
            ribbonTrail.clear();
            ribbonTrail = null;
        }
        var previous = textureLeases;
        textureLeases = Map.of();
        try {
            previous.values().forEach(FiguraTexture.Lease::close);
        } finally {
            if (faceLease != null) {
                faceLease.close();
                faceLease = null;
            }
        }
    }

    @Override
    protected int getLightCoords(float partialTick) {
        if (preview != null) {
            // GUI light is local to the panel. Never query a live-world light source for preview geometry.
            return 0xF000F0;
        }
        boolean bright = drawingLayer != null && drawingLayer.emissive() != null ? drawingLayer.emissive() : emissive;
        if (bright) {
            return 0xF000F0;
        }
        if (ribbon() && anchorTrail) {
            Vec3 position = lightPosition(partialTick);
            return position == null ? 0 : net.minecraft.client.renderer.LevelRenderer.getLightCoords(level,
                    net.minecraft.core.BlockPos.containing(position));
        }
        return super.getLightCoords(partialTick);
    }

    @Override
    protected Layer getLayer() {
        return drawingLayer == null ? baseLayer.renderLayer() : drawingLayer.renderLayer();
    }

    @Override
    protected float getU0() {
        return uv.u0();
    }

    @Override
    protected float getU1() {
        return uv.u1();
    }

    @Override
    protected float getV0() {
        return uv.v0();
    }

    @Override
    protected float getV1() {
        return uv.v1();
    }

    private static void finite(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new LuaError("Custom particle position and velocity must be finite");
        }
    }
}
