package top.aihmc.omnimorph.render.acceleration;

/** Requested preference is distinct from the backend that is actually ready. */
public record CapabilityReport(Mode requested, State state, Effective effective,
                               FallbackReason reason, int nativeCapabilities) {
    public enum Mode { AUTO, SIMD, GPU }
    public enum State { DISABLED, PROBING, READY, FALLBACK_JAVA }
    public enum Effective { JAVA, SSE2, AVX2, NEON, GPU }
}
