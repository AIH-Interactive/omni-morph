package top.aihmc.omnimorph.model.outfit;

import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;

/** Instance state belongs to an outfit session, never to the legacy per-avatar preference file. */
public final class CosmeticInstanceContext {
    public enum Scope {
        DRAFT,
        OWNER,
        OBSERVER
    }

    private final UUID accountId;
    private final UUID outfitId;
    private final UUID wearerId;
    private final UUID instanceId;
    private final UUID executionId = UUID.randomUUID();
    private final OutfitConfig.ResourceRef resource;
    private final OutfitLimits limits;
    private final Scope scope;
    private final boolean worldParticles;
    private final Map<String, Object> controls = new LinkedHashMap<>();
    private final Map<String, Object> luaState = new LinkedHashMap<>();
    private Supplier<MolangExecutionBudget> outfitMolangBudget;
    private OutfitInstructionBudget instructionBudget;
    private OutfitEffectBudget effectBudget;
    private Function<Float, TrailBuffer.Snapshot> trailSource;

    public CosmeticInstanceContext(UUID accountId, UUID outfitId, UUID wearerId,
                                   OutfitConfig.Instance instance, OutfitLimits limits, Scope scope) {
        this(accountId, outfitId, wearerId, instance, limits, scope, false);
    }

    public CosmeticInstanceContext(UUID accountId, UUID outfitId, UUID wearerId,
                                   OutfitConfig.Instance instance, OutfitLimits limits, Scope scope,
                                   boolean worldParticles) {
        this.accountId = Objects.requireNonNull(accountId);
        this.outfitId = Objects.requireNonNull(outfitId);
        this.wearerId = Objects.requireNonNull(wearerId);
        this.instanceId = instance.instanceId();
        this.resource = instance.resource();
        this.limits = Objects.requireNonNull(limits);
        this.scope = Objects.requireNonNull(scope);
        this.worldParticles = worldParticles;
        copyState(controls, instance.controls(), limits.maxControls());
        copyState(luaState, instance.luaState(), limits.maxLuaStateEntries());
    }

    public UUID accountId() { return accountId; }
    public UUID outfitId() { return outfitId; }
    public UUID wearerId() { return wearerId; }
    public UUID instanceId() { return instanceId; }
    public UUID executionId() { return executionId; }
    public OutfitConfig.ResourceRef resource() { return resource; }
    public OutfitLimits limits() { return limits; }
    public Scope scope() { return scope; }
    public boolean worldParticles() { return worldParticles; }

    void bindTrailSource(Function<Float, TrailBuffer.Snapshot> source) {
        trailSource = Objects.requireNonNull(source);
    }

    public boolean hasTrailSource() {
        return trailSource != null;
    }

    public TrailBuffer.Snapshot trailSnapshot(float partialTick) {
        return trailSource == null ? null : trailSource.apply(partialTick);
    }

    void bindInstructionBudget(OutfitInstructionBudget budget) {
        instructionBudget = Objects.requireNonNull(budget);
    }

    public OutfitInstructionBudget instructionBudget() {
        return instructionBudget;
    }

    void bindEffectBudget(OutfitEffectBudget budget) {
        effectBudget = Objects.requireNonNull(budget);
    }

    public OutfitEffectBudget effectBudget() {
        return effectBudget;
    }

    void bindMolangBudget(Supplier<MolangExecutionBudget> budget) {
        outfitMolangBudget = Objects.requireNonNull(budget);
    }

    public MolangExecutionBudget molangBudget() {
        return outfitMolangBudget == null ? null : outfitMolangBudget.get();
    }

    public synchronized Object control(String id) {
        return controls.get(id);
    }

    public synchronized Map<String, Object> controls() {
        return Map.copyOf(controls);
    }

    public synchronized Map<String, Object> luaState() {
        return Map.copyOf(luaState);
    }

    /** Snapshot hydration never invokes author callbacks or writes per-avatar preferences. */
    public synchronized void restoreState(Map<String, Object> restoredControls, Map<String, Object> restoredLua) {
        var checkedControls = new LinkedHashMap<String, Object>();
        var checkedLua = new LinkedHashMap<String, Object>();
        copyState(checkedControls, restoredControls, limits.maxControls());
        copyState(checkedLua, restoredLua, limits.maxLuaStateEntries());
        controls.clear();
        controls.putAll(checkedControls);
        luaState.clear();
        luaState.putAll(checkedLua);
    }

    public synchronized void saveControl(String id, Object value) {
        if (scope != Scope.OBSERVER) {
            putState(controls, id, value, limits.maxControls());
        }
    }

    public synchronized void saveLuaState(String id, Object value) {
        if (scope != Scope.OBSERVER) {
            putState(luaState, id, value, limits.maxLuaStateEntries());
        }
    }

    /** Validate before callbacks or Molang writes can observe a rejected persistent value. */
    public synchronized void validateControl(String id, Object value) {
        validateScalar(id, value);
        if (!controls.containsKey(id) && controls.size() >= limits.maxControls()) {
            throw new IllegalArgumentException("COSMETIC_STATE_LIMIT");
        }
    }

    private void copyState(Map<String, Object> target, Map<String, Object> source, int maximum) {
        for (var entry : source.entrySet()) {
            putState(target, entry.getKey(), entry.getValue(), maximum);
        }
    }

    private void putState(Map<String, Object> target, String id, Object value, int maximum) {
        validateScalar(id, value);
        if (!target.containsKey(id) && target.size() >= maximum) {
            throw new IllegalArgumentException("COSMETIC_STATE_LIMIT");
        }
        target.put(id, value instanceof Number number ? number.doubleValue() : value);
    }

    private void validateScalar(String id, Object value) {
        if (id == null || id.isBlank() || id.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw new IllegalArgumentException("COSMETIC_STATE_ID");
        }
        boolean valid = value instanceof Boolean
                || value instanceof Number number && Double.isFinite(number.doubleValue())
                || value instanceof String string
                && string.getBytes(StandardCharsets.UTF_8).length <= limits.maxStateStringBytes();
        if (!valid) {
            throw new IllegalArgumentException("COSMETIC_STATE_VALUE");
        }
    }
}
