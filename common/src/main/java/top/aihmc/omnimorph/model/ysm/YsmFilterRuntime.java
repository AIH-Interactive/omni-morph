package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Per-model procedural filters, advanced before sampling the current frame's parameters. */
public final class YsmFilterRuntime {
    public enum Domain {
        BODY,
        FIRST_PERSON,
        PREVIEW
    }

    private static final int MAX_FILTERS_PER_DOMAIN = 16_384;
    private final Map<Domain, Manager> managers = new EnumMap<>(Domain.class);
    private String bone;
    private long generation;

    private record Key(String name, String bone) {
    }

    public float firstOrder(Domain domain, String name, float input, float response) {
        return sample(domain, new Key(name, null), input, response, 0f, 0f, false);
    }

    public float secondOrder(Domain domain, String name, float input, float frequency, float damping, float response) {
        return sample(domain, new Key(name, bone), input, frequency, damping, response, true);
    }

    private float sample(Domain domain, Key key, float input, float first, float second, float third, boolean secondOrder) {
        if (key.name() == null || key.name().isEmpty()) {
            return 0f;
        }
        Manager manager = managers.computeIfAbsent(domain, ignored -> new Manager());
        Filter filter = manager.filters.get(key);
        if (filter == null) {
            if (manager.filters.size() >= MAX_FILTERS_PER_DOMAIN) {
                throw new IllegalStateException("YSM filter state budget exceeded");
            }
            filter = secondOrder ? new SecondOrder(input) : new FirstOrder(input);
            filter.setArguments(input, first, second, third);
            manager.filters.put(key, filter);
            return filter.value;
        }
        filter.setArguments(input, first, second, third);
        return filter.value;
    }

    public void update(Domain domain, float renderTicks, MolangExecutionBudget budget) {
        if (!Float.isFinite(renderTicks)) {
            return;
        }
        Manager manager = managers.computeIfAbsent(domain, ignored -> new Manager());
        if (manager.lastRenderTicks <= 0f) {
            manager.lastRenderTicks = renderTicks;
        } else if (renderTicks > manager.lastRenderTicks) {
            float seconds = (renderTicks - manager.lastRenderTicks) / 20f;
            manager.lastRenderTicks = renderTicks;
            for (Filter filter : manager.filters.values()) {
                if (budget != null) {
                    budget.consume();
                }
                filter.update(seconds, budget);
            }
        }
    }

    public void update(Domain domain, float renderTicks, long renderFrame, MolangExecutionBudget budget) {
        if (!Float.isFinite(renderTicks)) {
            return;
        }
        Manager manager = managers.computeIfAbsent(domain, ignored -> new Manager());
        if (manager.lastRenderFrame == renderFrame) {
            return;
        }
        manager.lastRenderFrame = renderFrame;
        update(domain, renderTicks, budget);
    }

    public BoneScope beginBone(String name) {
        return new BoneScope(name);
    }

    public final class BoneScope implements AutoCloseable {
        private final String previousBone;
        private final long scopeGeneration;
        private boolean closed;

        private BoneScope(String name) {
            previousBone = bone;
            scopeGeneration = generation;
            bone = name;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (scopeGeneration == generation) {
                    bone = previousBone;
                }
            }
        }
    }

    public void clear() {
        generation++;
        managers.clear();
        bone = null;
    }

    private static final class Manager {
        private final Map<Key, Filter> filters = new HashMap<>();
        private float lastRenderTicks;
        private long lastRenderFrame = Long.MIN_VALUE;
    }

    private abstract static class Filter {
        protected float input;
        protected float first;
        protected float second;
        protected float third;
        protected float value;

        private void setArguments(float input, float first, float second, float third) {
            this.input = input;
            this.first = first;
            this.second = second;
            this.third = third;
        }

        protected abstract void update(float seconds, MolangExecutionBudget budget);
    }

    private static final class FirstOrder extends Filter {
        private FirstOrder(float input) {
            value = finite(input);
        }

        @Override
        protected void update(float seconds, MolangExecutionBudget budget) {
            float target = finite(input);
            float response = finite(first);
            if (!Float.isFinite(seconds) || seconds <= 0f || response <= 0f) {
                value = target;
                return;
            }
            float fraction = Math.min(seconds, response) / response;
            value = (1f - fraction) * value + fraction * target;
        }
    }

    private static final class SecondOrder extends Filter {
        private float previousInput;
        private float velocity;

        private SecondOrder(float input) {
            snap(finite(input));
        }

        @Override
        protected void update(float seconds, MolangExecutionBudget budget) {
            float target = finite(input);
            if (!Float.isFinite(seconds) || seconds <= 0f) {
                previousInput = target;
                if (!Float.isFinite(value) || !Float.isFinite(velocity)) {
                    snap(target);
                }
                return;
            }
            float step = Math.min(seconds, 0.1f);
            float frequency = clamp(finite(first), 0.001f, 5f);
            float damping = clamp(finite(second), 0f, 1f);
            float response = finite(third);
            float pi = (float) Math.PI;
            float k1 = damping / pi / frequency;
            float k2 = 1f / (2f * pi * frequency) / (2f * pi * frequency);
            float k3 = response * damping / 2f / pi / frequency;
            if (!Float.isFinite(k1) || !Float.isFinite(k2) || k2 <= 0f || !Float.isFinite(k3)) {
                snap(target);
                return;
            }
            float derivative = finite((target - previousInput) / step);
            previousInput = target;
            float stableStep = (float) Math.sqrt(4f * k2 + k1 * k1) - k1;
            if (!Float.isFinite(stableStep) || stableStep <= 0f) {
                stableStep = step;
            }
            int cycles = Math.max(1, Math.min(16, (int) Math.ceil(step / stableStep)));
            step /= cycles;
            float simulated = Float.isFinite(value) ? value : target;
            float simulatedVelocity = finite(velocity);
            for (int cycle = 0; cycle < cycles; cycle++) {
                if (budget != null) {
                    budget.consume();
                }
                simulated += step * simulatedVelocity;
                simulatedVelocity += step * (k3 * derivative + target - simulated - k1 * simulatedVelocity) / k2;
                if (!Float.isFinite(simulated) || !Float.isFinite(simulatedVelocity)) {
                    snap(target);
                    return;
                }
            }
            value = simulated;
            velocity = simulatedVelocity;
        }

        private void snap(float target) {
            previousInput = target;
            value = target;
            velocity = 0f;
        }
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
