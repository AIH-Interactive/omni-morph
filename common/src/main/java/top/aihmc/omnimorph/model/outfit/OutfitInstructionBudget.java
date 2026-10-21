package top.aihmc.omnimorph.model.outfit;

import org.luaj.vm2.LuaError;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.api.TextureAPI;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.permissions.Permissions;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Outfit owners share existing instruction, face and custom texture limits; the controller is excluded. */
public final class OutfitInstructionBudget {
    private static final ThreadLocal<OutfitInstructionBudget> CURRENT = new ThreadLocal<>();

    private static final class Usage {
        long epoch = Long.MIN_VALUE;
        long instructions;
        long observedBase;
    }

    private final Supplier<Avatar> base;
    private final Map<Permissions, Usage> usage = new HashMap<>();
    private long tick;
    private List<Avatar> instances = List.of();

    OutfitInstructionBudget(Supplier<Avatar> base) {
        this.base = base;
    }

    void bindInstances(Collection<Avatar> avatars) {
        instances = List.copyOf(avatars);
    }

    public void checkTextureRegistration(Avatar owner, String name) {
        int count = customTextureCount(base.get());
        for (Avatar instance : instances) {
            count += customTextureCount(instance);
        }
        if (owner.renderer.customTextures.containsKey(name)) {
            count--;
        }
        if (count >= TextureAPI.TEXTURE_LIMIT) {
            throw new LuaError("Outfit maximum amount of custom textures reached!");
        }
    }

    private static int customTextureCount(Avatar avatar) {
        return avatar == null || avatar.renderer == null ? 0 : avatar.renderer.customTextures.size();
    }

    public static OutfitInstructionBudget current(Avatar avatar) {
        if (avatar.cosmeticContext != null) {
            return avatar.cosmeticContext.instructionBudget();
        }
        var scope = CURRENT.get();
        return scope != null && scope.base.get() == avatar ? scope : null;
    }

    void beginTick() {
        tick++;
    }

    Scope enter() {
        return new Scope();
    }

    public int allowance(Avatar avatar, Avatar.Instructions instructions) {
        Permissions permission = channel(avatar, instructions);
        Avatar baseAvatar = base.get();
        int maximum = avatar.permissions.get(permission);
        Usage value = usage(permission);
        if (baseAvatar != null) {
            maximum = Math.min(maximum, baseAvatar.permissions.get(permission));
            var baseInstructions = instructions(baseAvatar, permission);
            long baseUsed = Math.max(0L, (long) baseInstructions.max - baseInstructions.remaining);
            // Import work performed before this scope; counter resets never refund shared work.
            value.instructions += Math.max(0, baseUsed - value.observedBase);
            value.observedBase = baseUsed;
        }
        long available = (long) maximum - value.instructions;
        int remaining = (int) Math.max(0, Math.min(instructions.remaining, available));
        if (remaining == 0) {
            avatar.noPermissions.add(permission);
        } else {
            avatar.noPermissions.remove(permission);
        }
        return remaining;
    }

    public void charge(Avatar avatar, Avatar.Instructions instructions, int amount) {
        Usage value = usage(channel(avatar, instructions));
        int consumed = Math.max(0, amount);
        value.instructions += consumed;
        if (avatar == base.get()) {
            // Called immediately before the VM updates the base's own counter.
            value.observedBase = Math.max(0L, (long) instructions.max - instructions.remaining) + consumed;
        }
    }

    private Usage usage(Permissions permission) {
        long epoch = YsmRenderFrameClock.currentFrame();
        if (permission == Permissions.INIT_INST) {
            epoch = 0;
        } else if (permission == Permissions.TICK_INST || permission == Permissions.WORLD_TICK_INST) {
            epoch = tick;
        }
        Usage value = usage.computeIfAbsent(permission, ignored -> new Usage());
        if (value.epoch != epoch) {
            value.epoch = epoch;
            value.instructions = 0;
            value.observedBase = 0;
        }
        return value;
    }

    private static Permissions channel(Avatar avatar, Avatar.Instructions instructions) {
        if (instructions == avatar.complexity) {
            return Permissions.COMPLEXITY;
        }
        if (instructions == avatar.init) {
            return Permissions.INIT_INST;
        }
        if (instructions == avatar.worldTick) {
            return Permissions.WORLD_TICK_INST;
        }
        if (instructions == avatar.render) {
            return Permissions.RENDER_INST;
        }
        if (instructions == avatar.worldRender) {
            return Permissions.WORLD_RENDER_INST;
        }
        if (instructions == avatar.animation) {
            return Permissions.ANIMATION_INST;
        }
        return Permissions.TICK_INST;
    }

    private static Avatar.Instructions instructions(Avatar avatar, Permissions permission) {
        if (permission == Permissions.COMPLEXITY) {
            return avatar.complexity;
        }
        if (permission == Permissions.INIT_INST) {
            return avatar.init;
        }
        if (permission == Permissions.WORLD_TICK_INST) {
            return avatar.worldTick;
        }
        if (permission == Permissions.RENDER_INST) {
            return avatar.render;
        }
        if (permission == Permissions.WORLD_RENDER_INST) {
            return avatar.worldRender;
        }
        if (permission == Permissions.ANIMATION_INST) {
            return avatar.animation;
        }
        return avatar.tick;
    }

    final class Scope implements AutoCloseable {
        private final OutfitInstructionBudget previous = CURRENT.get();

        private Scope() {
            CURRENT.set(OutfitInstructionBudget.this);
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
