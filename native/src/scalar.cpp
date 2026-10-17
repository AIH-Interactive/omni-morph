#include "acceleration.hpp"

#include <cmath>
#include <cstring>

namespace omnimorph {

void finish_vertex(float* values, const Transform& transform) {
    if ((transform.flags & affine_output_w) != 0) {
        values[3] = 1;
    }
    if ((transform.flags & normalize_normals) != 0) {
        const float length = std::sqrt(values[4] * values[4] + values[5] * values[5] + values[6] * values[6]);
        const float inverse = 1.0f / length;
        values[4] *= inverse;
        values[5] *= inverse;
        values[6] *= inverse;
    }
}

void scalar_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                   std::int32_t output_stride, std::int32_t count, const Transform& transform) {
    for (std::int32_t vertex = 0; vertex < count; ++vertex) {
        float source[vertex_floats];
        float result[vertex_floats];
        std::memcpy(source, input + static_cast<std::size_t>(vertex) * input_stride, vertex_bytes);
        for (int row = 0; row < 4; ++row) {
            result[row] = transform.position[row] * source[0] + transform.position[4 + row] * source[1]
                          + transform.position[8 + row] * source[2] + transform.position[12 + row] * source[3];
        }
        for (int row = 0; row < 3; ++row) {
            result[4 + row] = transform.normal[row] * source[4] + transform.normal[4 + row] * source[5]
                              + transform.normal[8 + row] * source[6];
        }
        for (int row = 0; row < 2; ++row) {
            result[7 + row] = transform.uv[row] * source[7] + transform.uv[4 + row] * source[8]
                              + transform.uv[8 + row];
        }
        finish_vertex(result, transform);
        std::memcpy(output + static_cast<std::size_t>(vertex) * output_stride, result, vertex_bytes);
    }
}

} // namespace omnimorph
