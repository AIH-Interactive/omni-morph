#include "acceleration.hpp"

#if defined(OMNIMORPH_X64) && defined(_MSC_VER)
#include <intrin.h>
#elif defined(OMNIMORPH_X64)
#include <cpuid.h>
#endif

namespace omnimorph {

CpuFeatures detect_cpu() {
    CpuFeatures features;
#if defined(OMNIMORPH_ARM64)
    // AArch64 Advanced SIMD is part of the macOS arm64 execution environment.
    features.neon = true;
#else
    int registers[4]{};
#if defined(_MSC_VER)
    __cpuid(registers, 0);
#else
    __cpuid_count(0, 0, registers[0], registers[1], registers[2], registers[3]);
#endif
    const int highest_leaf = registers[0];
    if (highest_leaf < 1) {
        return features;
    }
#if defined(_MSC_VER)
    __cpuidex(registers, 1, 0);
#else
    __cpuid_count(1, 0, registers[0], registers[1], registers[2], registers[3]);
#endif
    features.sse2 = (registers[3] & (1 << 26)) != 0;
    features.avx = (registers[2] & (1 << 28)) != 0;
    features.osxsave = (registers[2] & (1 << 27)) != 0;
    features.fma = (registers[2] & (1 << 12)) != 0;
    // XGETBV is itself guarded; unsupported OS state never reaches AVX code.
    if (features.avx && features.osxsave) {
#if defined(_MSC_VER)
        features.xmm_ymm_state = (_xgetbv(0) & 6) == 6;
#else
        unsigned int low, high;
        __asm__ volatile("xgetbv" : "=a"(low), "=d"(high) : "c"(0));
        features.xmm_ymm_state = (low & 6) == 6;
#endif
    }
    if (highest_leaf >= 7) {
#if defined(_MSC_VER)
        __cpuidex(registers, 7, 0);
#else
        __cpuid_count(7, 0, registers[0], registers[1], registers[2], registers[3]);
#endif
        features.avx2 = (registers[1] & (1 << 5)) != 0;
    }
#endif
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
    case Kernel::neon:
        return features.neon;
    default:
        return false;
    }
}

Kernel select_kernel(const CpuFeatures& features) {
    if (features.neon) {
        return Kernel::neon;
    }
    if (available(features, Kernel::avx2)) {
        return Kernel::avx2;
    }
    return features.sse2 ? Kernel::sse2 : Kernel::scalar;
}

std::int32_t capability_bits(const CpuFeatures& features) {
    return 1 | (features.sse2 ? 2 : 0) | (available(features, Kernel::avx2) ? 4 : 0)
           | (features.fma ? 8 : 0) | (features.osxsave ? 16 : 0) | (features.xmm_ymm_state ? 32 : 0)
           | (features.neon ? 64 : 0);
}

} // namespace omnimorph
