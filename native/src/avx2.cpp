#include "acceleration.hpp"

#include <immintrin.h>
#include <cstring>

namespace omnimorph {
namespace {

__m256 column(const float* values) {
    return _mm256_broadcast_ps(reinterpret_cast<const __m128*>(values));
}

__m256 transform3(__m256 value, const float* matrix) {
    const __m256 x = _mm256_mul_ps(column(matrix), _mm256_permute_ps(value, _MM_SHUFFLE(0, 0, 0, 0)));
    const __m256 y = _mm256_mul_ps(column(matrix + 4), _mm256_permute_ps(value, _MM_SHUFFLE(1, 1, 1, 1)));
    const __m256 z = _mm256_mul_ps(column(matrix + 8), _mm256_permute_ps(value, _MM_SHUFFLE(2, 2, 2, 2)));
    return _mm256_add_ps(_mm256_add_ps(x, y), z);
}

} // namespace

void avx2_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                 std::int32_t output_stride, std::int32_t count, const Transform& transform) {
    std::int32_t vertex = 0;
    for (; vertex + 1 < count; vertex += 2) {
        const std::byte* first = input + static_cast<std::size_t>(vertex) * input_stride;
        const std::byte* second = first + input_stride;
        const __m256 points = _mm256_set_m128(_mm_loadu_ps(reinterpret_cast<const float*>(second)),
                                             _mm_loadu_ps(reinterpret_cast<const float*>(first)));
        const __m256 positions = _mm256_add_ps(transform3(points, transform.position),
                _mm256_mul_ps(column(transform.position + 12),
                              _mm256_permute_ps(points, _MM_SHUFFLE(3, 3, 3, 3))));
        const __m256 normals = transform3(_mm256_set_m128(
                _mm_loadu_ps(reinterpret_cast<const float*>(second + 16)),
                _mm_loadu_ps(reinterpret_cast<const float*>(first + 16))), transform.normal);
        float first_uv[2];
        float second_uv[2];
        std::memcpy(first_uv, first + 28, sizeof(first_uv));
        std::memcpy(second_uv, second + 28, sizeof(second_uv));
        const __m256 uvs = transform3(_mm256_set_m128(_mm_set_ps(0, 1, second_uv[1], second_uv[0]),
                                                     _mm_set_ps(0, 1, first_uv[1], first_uv[0])), transform.uv);
        float position_values[8];
        float normal_values[8];
        float uv_values[8];
        _mm256_storeu_ps(position_values, positions);
        _mm256_storeu_ps(normal_values, normals);
        _mm256_storeu_ps(uv_values, uvs);
        for (int half = 0; half < 2; ++half) {
            float result[vertex_floats];
            std::memcpy(result, position_values + half * 4, 4 * sizeof(float));
            std::memcpy(result + 4, normal_values + half * 4, 3 * sizeof(float));
            std::memcpy(result + 7, uv_values + half * 4, 2 * sizeof(float));
            finish_vertex(result, transform);
            std::memcpy(output + static_cast<std::size_t>(vertex + half) * output_stride, result, vertex_bytes);
        }
    }
    _mm256_zeroupper();
    if (vertex < count) {
        sse2_kernel(input + static_cast<std::size_t>(vertex) * input_stride, input_stride,
                    output + static_cast<std::size_t>(vertex) * output_stride, output_stride, 1, transform);
    }
}

} // namespace omnimorph

namespace omnimorph {

void avx2_pack(const double* input, float* output, std::int32_t count) {
    std::int32_t index = 0;
    for (; index + 8 <= count; index += 8) {
        const __m128 low = _mm256_cvtpd_ps(_mm256_loadu_pd(input + index));
        const __m128 high = _mm256_cvtpd_ps(_mm256_loadu_pd(input + index + 4));
        _mm256_storeu_ps(output + index, _mm256_set_m128(high, low));
    }
    _mm256_zeroupper();
    scalar_pack(input + index, output + index, count - index);
}

} // namespace omnimorph
