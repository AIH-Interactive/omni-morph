#include "acceleration.hpp"

#include <intrin.h>

namespace omnimorph {

CpuFeatures detect_cpu() {
    CpuFeatures features;
    int registers[4]{};
    __cpuid(registers, 0);
    const int highest_leaf = registers[0];
    if (highest_leaf < 1) {
        return features;
    }
    __cpuidex(registers, 1, 0);
    features.sse2 = (registers[3] & (1 << 26)) != 0;
    features.avx = (registers[2] & (1 << 28)) != 0;
    features.osxsave = (registers[2] & (1 << 27)) != 0;
    features.fma = (registers[2] & (1 << 12)) != 0;
    // XGETBV is itself guarded; unsupported OS state never reaches AVX code.
    if (features.avx && features.osxsave) {
        features.xmm_ymm_state = (_xgetbv(0) & 6) == 6;
    }
    if (highest_leaf >= 7) {
        __cpuidex(registers, 7, 0);
        features.avx2 = (registers[1] & (1 << 5)) != 0;
    }
    return features;
}

bool available(const CpuFeatures& features, Kernel kernel) {
    switch (kernel) {
    case Kernel::scalar:
        return true;
    case Kernel::sse2:
        return features.sse2;
    case Kernel::avx2:
        return features.sse2 && features.avx && features.avx2 && features.osxsave && features.xmm_ymm_state;
    default:
        return false;
    }
}

Kernel select_kernel(const CpuFeatures& features) {
    if (available(features, Kernel::avx2)) {
        return Kernel::avx2;
    }
    return features.sse2 ? Kernel::sse2 : Kernel::scalar;
}

std::int32_t capability_bits(const CpuFeatures& features) {
    return 1 | (features.sse2 ? 2 : 0) | (available(features, Kernel::avx2) ? 4 : 0)
           | (features.fma ? 8 : 0) | (features.osxsave ? 16 : 0) | (features.xmm_ymm_state ? 32 : 0);
}

} // namespace omnimorph
