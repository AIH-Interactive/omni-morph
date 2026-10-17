package top.aihmc.omnimorph.model.ysm;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import java.lang.ref.WeakReference;

/** Physical entity identities own their clocks and effects; definitions retain shared model resources. */
public final class YsmSubEntityInstances implements AutoCloseable {
    // Entity.equals/hashCode use only its numeric ID, which is reused across worlds.
    private static final Map<UUID, VehicleAssignment> VEHICLE_OWNERS = new WeakHashMap<>();
    private final YsmModelRuntime parent;
    private final Map<Key, Instance> instances = new LinkedHashMap<>();
    private boolean closed;

    private record VehicleAssignment(WeakReference<Entity> entity, WeakReference<YsmModelRuntime> runtime) {
    }

    private record Key(Level level, int entityId) {
    }

    private record Instance(Entity entity, YsmSubEntity definition, YsmModelRuntime runtime) {
    }

    public YsmSubEntityInstances(YsmModelRuntime parent) {
        this.parent = parent;
    }

    /** Retains the locally observed vehicle assignment while it is unoccupied, without keeping an Avatar alive. */
    public static YsmModelRuntime vehicleOwner(Entity entity) {
        synchronized (VEHICLE_OWNERS) {
            VehicleAssignment assignment = VEHICLE_OWNERS.get(entity.getUUID());
            if (assignment == null) {
                return null;
            }
            YsmModelRuntime runtime = assignment.runtime().get();
            Entity assignedEntity = assignment.entity().get();
            if (runtime == null || runtime.isClosed() || assignedEntity == null) {
                VEHICLE_OWNERS.remove(entity.getUUID());
                return null;
            }
            return assignedEntity == entity ? runtime : null;
        }
    }

    private void forgetVehicle(Entity entity) {
        synchronized (VEHICLE_OWNERS) {
            VehicleAssignment assignment = VEHICLE_OWNERS.get(entity.getUUID());
            if (assignment != null && assignment.entity().get() == entity && assignment.runtime().get() == parent) {
                VEHICLE_OWNERS.remove(entity.getUUID());
            }
        }
    }

    public synchronized YsmModelRuntime acquire(Entity entity, String kind, String typeId) {
        if (closed || parent.isClosed() || entity == null || entity.isRemoved()
                || !parent.ownsSubEntity(entity, kind)) {
            return null;
        }
        YsmSubEntity definition = parent.getSubEntity(kind, typeId);
        if (definition == null) {
            definition = parent.getSubEntityForEntity(kind, entity);
        }
        Key key = new Key(entity.level(), entity.getId());
        Instance previous = instances.get(key);
        if (previous != null && (previous.entity() != entity || previous.definition() != definition)) {
            instances.remove(key);
            previous.runtime().close();
            forgetVehicle(previous.entity());
            previous = null;
        }
        if (definition == null) {
            return null;
        }
        if ("vehicle".equals(kind)) {
            synchronized (VEHICLE_OWNERS) {
                UUID id = entity.getUUID();
                // WeakHashMap keeps the original key object on equal-key replacement.
                // Retain the UUID held by this physical entity, rather than an earlier equal UUID object.
                VEHICLE_OWNERS.remove(id);
                VEHICLE_OWNERS.put(id, new VehicleAssignment(new WeakReference<>(entity), new WeakReference<>(parent)));
            }
        }
        if (previous == null) {
            YsmModelRuntime runtime = parent.createSubEntityRuntime(definition, entity);
            previous = new Instance(entity, definition, runtime);
            instances.put(key, previous);
        }
        return previous.runtime();
    }

    /** Called by the parent Avatar tick even when no child is visible. */
    public synchronized void tick(Level currentLevel) {
        var iterator = instances.values().iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            if (instance.entity().isRemoved() || instance.entity().level() != currentLevel
                    || !parent.ownsSubEntity(instance.entity(), instance.definition().kind())) {
                iterator.remove();
                instance.runtime().close();
                forgetVehicle(instance.entity());
            } else {
                instance.runtime().sounds().tick();
            }
        }
    }

    public synchronized int size() {
        return instances.size();
    }

    @Override
    public synchronized void close() {
        closed = true;
        instances.values().forEach(instance -> {
            instance.runtime().close();
            forgetVehicle(instance.entity());
        });
        instances.clear();
    }
}
