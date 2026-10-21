package top.aihmc.omnimorph.model.outfit;

import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.permissions.Permissions;
import top.aihmc.omnimorph.utils.RefilledNumber;

import java.util.function.Supplier;

/** One rate bucket per wearer, in addition to each author's existing permission bucket. */
public final class OutfitEffectBudget {
    private final Supplier<Avatar> base;
    private final Avatar permissionOwner;
    private final RefilledNumber particles;
    private final RefilledNumber sounds;

    OutfitEffectBudget(Supplier<Avatar> base, Avatar permissionOwner) {
        this.base = base;
        this.permissionOwner = permissionOwner;
        particles = new RefilledNumber(permissionOwner.permissions.get(Permissions.PARTICLES));
        sounds = new RefilledNumber(permissionOwner.permissions.get(Permissions.SOUNDS));
    }

    void tick() {
        // AvatarManager / the preview session already refills the borrowed base once per tick.
        if (base.get() == null) {
            particles.set(permissionOwner.permissions.get(Permissions.PARTICLES));
            sounds.set(permissionOwner.permissions.get(Permissions.SOUNDS));
            particles.tick();
            sounds.tick();
        }
    }

    public boolean use(Avatar owner, boolean particle) {
        RefilledNumber individual = individual(owner, particle);
        RefilledNumber shared = shared(particle);
        if (individual == shared) {
            return individual.use();
        }
        if (!individual.check() || !shared.use()) {
            return false;
        }
        return individual.use();
    }

    public int remaining(Avatar owner, boolean particle) {
        return Math.min(individual(owner, particle).peek(), shared(particle).peek());
    }

    private RefilledNumber shared(boolean particle) {
        Avatar avatar = base.get();
        if (avatar != null) {
            return individual(avatar, particle);
        }
        return particle ? particles : sounds;
    }

    private static RefilledNumber individual(Avatar owner, boolean particle) {
        return particle ? owner.particlesRemaining : owner.soundsRemaining;
    }
}
