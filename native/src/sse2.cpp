#include "acceleration.hpp"

#include <emmintrin.h>
#include <cstring>

namespace omnimorph {
namespace {

__m128 transform4(__m128 value, const float* matrix) {
    const __m128 x = _mm_mul_ps(_mm_loadu_ps(matrix), _mm_shuffle_ps(value, value, _MM_SHUFFLE(0, 0, 0, 0)));
    const __m128 y = _mm_mul_ps(_mm_loadu_ps(matrix + 4), _mm_shuffle_ps(value, value, _MM_SHUFFLE(1, 1, 1, 1)));
    const __m128 z = _mm_mul_ps(_mm_loadu_ps(matrix + 8), _mm_shuffle_ps(value, value, _MM_SHUFFLE(2, 2, 2, 2)));
    const __m128 w = _mm_mul_ps(_mm_loadu_ps(matrix + 12), _mm_shuffle_ps(value, value, _MM_SHUFFLE(3, 3, 3, 3)));
    return _mm_add_ps(_mm_add_ps(_mm_add_ps(x, y), z), w);
}

__m128 transform3(__m128 value, const float* matrix) {
    const __m128 x = _mm_mul_ps(_mm_loadu_ps(matrix), _mm_shuffle_ps(value, value, _MM_SHUFFLE(0, 0, 0, 0)));
    const __m128 y = _mm_mul_ps(_mm_loadu_ps(matrix + 4), _mm_shuffle_ps(value, value, _MM_SHUFFLE(1, 1, 1, 1)));
    const __m128 z = _mm_mul_ps(_mm_loadu_ps(matrix + 8), _mm_shuffle_ps(value, value, _MM_SHUFFLE(2, 2, 2, 2)));
    return _mm_add_ps(_mm_add_ps(x, y), z);
}

} // namespace

void sse2_kernel(const std::byte* input, std::int32_t input_stride, std::byte* output,
                 std::int32_t output_stride, std::int32_t count, const Transform& transform) {
    for (std::int32_t vertex = 0; vertex < count; ++vertex) {
        const std::byte* source = input + static_cast<std::size_t>(vertex) * input_stride;
        float result[vertex_floats];
        float temporary[4];
        _mm_storeu_ps(result, transform4(_mm_loadu_ps(reinterpret_cast<const float*>(source)), transform.position));
        _mm_storeu_ps(temporary, transform3(_mm_loadu_ps(reinterpret_cast<const float*>(source + 16)), transform.normal));
        std::memcpy(result + 4, temporary, 3 * sizeof(float));
        float uv[2];
        std::memcpy(uv, source + 7 * sizeof(float), sizeof(uv));
        _mm_storeu_ps(temporary, transform3(_mm_set_ps(0, 1, uv[1], uv[0]), transform.uv));
        std::memcpy(result + 7, temporary, 2 * sizeof(float));
        finish_vertex(result, transform);
        std::memcpy(output + static_cast<std::size_t>(vertex) * output_stride, result, vertex_bytes);
    }
}

} // namespace omnimorph

namespace omnimorph {

void sse2_pack(const double* input, float* output, std::int32_t count) {
    std::int32_t index = 0;
    for (; index + 4 <= count; index += 4) {
        const __m128 low = _mm_cvtpd_ps(_mm_loadu_pd(input + index));
        const __m128 high = _mm_cvtpd_ps(_mm_loadu_pd(input + index + 2));
        _mm_storeu_ps(output + index, _mm_movelh_ps(low, high));
    }
    scalar_pack(input + index, output + index, count - index);
}

} // namespace omnimorph
