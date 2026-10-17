#include "acceleration.hpp"

#include <arm_neon.h>
#include <cstring>

namespace omnimorph {
namespace {

float32x4_t transform3(const float* value, const float* matrix) {
    const auto x = vmulq_n_f32(vld1q_f32(matrix), value[0]);
    const auto y = vmulq_n_f32(vld1q_f32(matrix + 4), value[1]);
    const auto z = vmulq_n_f32(vld1q_f32(matrix + 8), value[2]);
    return vaddq_f32(vaddq_f32(x, y), z);
}

} // namespace

void neon_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                 std::int32_t output_stride, std::int32_t count, const Transform& transform) {
    for (std::int32_t vertex = 0; vertex < count; ++vertex) {
        // memcpy supports byte-offset and stride inputs without aligned float pointer assumptions.
        float source[vertex_floats];
        std::memcpy(source, input + static_cast<std::size_t>(vertex) * input_stride, vertex_bytes);
        float result[vertex_floats];
        float temporary[4];
        const auto point = vaddq_f32(transform3(source, transform.position),
                                    vmulq_n_f32(vld1q_f32(transform.position + 12), source[3]));
        vst1q_f32(result, point);
        vst1q_f32(temporary, transform3(source + 4, transform.normal));
        std::memcpy(result + 4, temporary, 3 * sizeof(float));
        const float uv[3] = {source[7], source[8], 1};
        vst1q_f32(temporary, transform3(uv, transform.uv));
        std::memcpy(result + 7, temporary, 2 * sizeof(float));
        finish_vertex(result, transform);
        std::memcpy(output + static_cast<std::size_t>(vertex) * output_stride, result, vertex_bytes);
    }
}

} // namespace omnimorph
