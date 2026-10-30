#pragma once

#include <cstddef>
#include <cstdint>

namespace omnimorph {

constexpr std::int32_t abi_version = 2;
constexpr std::int32_t vertex_floats = 9;
constexpr std::int32_t vertex_bytes = vertex_floats * sizeof(float);
constexpr std::int32_t matrix_floats = 40;
constexpr std::int32_t max_vertices = 65536;
constexpr std::int32_t normalize_normals = 1;
constexpr std::int32_t affine_output_w = 2;

enum class Kernel : std::int32_t { automatic = -1, scalar = 0, sse2 = 1, avx2 = 2, neon = 3 };
enum class Status : std::int32_t { ok = 0, invalid_argument = 1, unsupported_kernel = 2, numerical_failure = 3, exhausted = 4 };

struct CpuFeatures {
    bool sse2 = false;
    bool avx = false;
    bool avx2 = false;
    bool fma = false;
    bool osxsave = false;
    bool xmm_ymm_state = false;
    bool neon = false;
};

struct Buffer {
    std::byte* data;
    std::size_t size;
    bool writable;
};

struct alignas(32) Transform {
    float position[16];
    float normal[12];
    float uv[12];
    std::int32_t flags;
};

using TransformKernel = void (*)(const std::byte*, std::int32_t, std::byte*, std::int32_t,
                                 std::int32_t, const Transform&);

CpuFeatures detect_cpu();
bool available(const CpuFeatures& features, Kernel kernel);
Kernel select_kernel(const CpuFeatures& features);
std::int32_t capability_bits(const CpuFeatures& features);

Status execute(Buffer input, std::int32_t input_offset, std::int32_t input_stride,
               Buffer matrices, std::int32_t matrix_offset, Buffer output,
               std::int32_t output_offset, std::int32_t output_stride,
               std::int32_t count, Kernel requested, std::int32_t flags);

void scalar_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                   std::int32_t output_stride, std::int32_t count, const Transform& transform);
void sse2_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                 std::int32_t output_stride, std::int32_t count, const Transform& transform);
void avx2_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                 std::int32_t output_stride, std::int32_t count, const Transform& transform);
void neon_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                 std::int32_t output_stride, std::int32_t count, const Transform& transform);

void finish_vertex(float* values, const Transform& transform);

// ABI 2: pose packing converts double values to float32 with round-to-nearest, matching a Java (float) cast.
constexpr std::int32_t max_pack_values = 4096 * matrix_floats;

using PackKernel = void (*)(const double*, float*, std::int32_t);

Status pack(Buffer input, std::int32_t input_offset, Buffer output, std::int32_t output_offset,
            std::int32_t count, Kernel requested);

void scalar_pack(const double* input, float* output, std::int32_t count);
void sse2_pack(const double* input, float* output, std::int32_t count);
void avx2_pack(const double* input, float* output, std::int32_t count);
void neon_pack(const double* input, float* output, std::int32_t count);

} // namespace omnimorph
