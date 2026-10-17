package top.aihmc.omnimorph.render.acceleration;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static top.aihmc.omnimorph.render.acceleration.CapabilityReport.*;

/** Frame-boundary activation and session-scoped fallback notification policy, independent of GUI/device APIs. */
final class AccelerationController {
    record Activation(AccelerationBackend backend, Effective effective, int capabilities) {
    }

    interface Probe {
        Activation detect(Mode mode) throws ProbeFailure;
    }

    static final class ProbeFailure extends Exception {
        final FallbackReason reason;

        ProbeFailure(FallbackReason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    private final AccelerationService service;
    private final Probe probe;
    private final Consumer<CapabilityReport> changed;
    private final Set<String> notified = new HashSet<>();
    private boolean enabled;
    private Mode mode = Mode.AUTO;
    private boolean pending;
    private CapabilityReport report = new CapabilityReport(Mode.AUTO, State.DISABLED, Effective.JAVA, null, 0);
    private CapabilityReport pendingChat;

    AccelerationController(AccelerationService service, Probe probe, Consumer<CapabilityReport> changed) {
        this.service = service;
        this.probe = probe;
        this.changed = changed;
    }

    synchronized void request(boolean enabled, Mode mode) {
        if (this.enabled == enabled && this.mode == mode) {
            return;
        }
        this.enabled = enabled;
        this.mode = mode;
        pending = true;
        pendingChat = null;
        publish(new CapabilityReport(mode, enabled ? State.PROBING : State.DISABLED, Effective.JAVA, null, 0));
    }

    synchronized void retryAfterResourceReload() {
        if (enabled) {
            pending = true;
            pendingChat = null;
            publish(new CapabilityReport(mode, State.PROBING, Effective.JAVA, null, 0));
        }
    }

    synchronized void beginFrame() {
        if (!pending) {
            return;
        }
        pending = false;
        service.useBackend(null);
        if (!enabled) {
            return;
        }
        try {
            Activation activation = probe.detect(mode);
            if (activation.backend() == null || activation.effective() == Effective.JAVA
                    || (mode != Mode.SIMD && activation.effective() != Effective.GPU)) {
                throw new ProbeFailure(FallbackReason.GPU_UNAVAILABLE);
            }
            service.useBackend(activation.backend(), this::runtimeFailure);
            publish(new CapabilityReport(mode, State.READY, activation.effective(), null, activation.capabilities()));
        } catch (ProbeFailure failure) {
            fallback(failure.reason);
        } catch (RuntimeException | LinkageError failure) {
            fallback(FallbackReason.RUNTIME_FAILURE);
        }
    }

    synchronized void runtimeFailure(Throwable failure) {
        if (!enabled || report.state() == State.FALLBACK_JAVA) {
            return;
        }
        fallback(failure instanceof IllegalCallerException || failure instanceof SecurityException
                ? FallbackReason.NATIVE_ACCESS_DENIED : FallbackReason.RUNTIME_FAILURE);
    }

    private void fallback(FallbackReason reason) {
        CapabilityReport fallback = new CapabilityReport(mode, State.FALLBACK_JAVA, Effective.JAVA, reason, 0);
        publish(fallback);
        String key = mode.name() + ':' + reason.name();
        if (!notified.contains(key)) {
            pendingChat = fallback;
        }
    }

    synchronized CapabilityReport report() {
        return report;
    }

    /** The caller invokes this on the client thread only when a world/player and chat exist. */
    synchronized void flushChat(boolean playerReady, Consumer<CapabilityReport> chat) {
        if (!playerReady || pendingChat == null) {
            return;
        }
        CapabilityReport message = pendingChat;
        chat.accept(message);
        notified.add(message.requested().name() + ':' + message.reason().name());
        pendingChat = null;
    }

    private void publish(CapabilityReport next) {
        if (!next.equals(report)) {
            report = next;
            changed.accept(next);
        }
    }
}
