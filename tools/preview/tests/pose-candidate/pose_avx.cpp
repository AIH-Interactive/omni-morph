#include <array>
#include <algorithm>
#include <cstdint>
#include <immintrin.h>
#include <limits>
#include <stdexcept>

// Isolated translation unit: the baseline DLL code does not require AVX2.
std::array<double, 16> multiply_affine_avx(const std::array<double, 16> &left, const float *right) {
    std::array<double, 16> result;
    const __m256d a = _mm256_loadu_pd(left.data());
    const __m256d b = _mm256_loadu_pd(left.data() + 4);
    const __m256d c = _mm256_loadu_pd(left.data() + 8);
    const __m256d d = _mm256_loadu_pd(left.data() + 12);
    for (size_t column = 0; column < 4; ++column) {
        __m256d value = _mm256_mul_pd(a, _mm256_set1_pd(right[column * 4]));
        value = _mm256_add_pd(value, _mm256_mul_pd(b, _mm256_set1_pd(right[column * 4 + 1])));
        value = _mm256_add_pd(value, _mm256_mul_pd(c, _mm256_set1_pd(right[column * 4 + 2])));
        if (column == 3) {
            value = _mm256_add_pd(value, d);
        }
        _mm256_storeu_pd(result.data() + column * 4, value);
    }
    return result;
}

void publish_affine_avx(size_t count, const int32_t *order, const int32_t *parents, const float *inverse,
    const float *local_values, std::array<double, 16> *world_values, float *world_output,
    float *palette_output, uint8_t *visible) {
    const __m256 absolute_mask = _mm256_castsi256_ps(_mm256_set1_epi32(0x7fffffff));
    const __m256 maximum = _mm256_set1_ps(std::numeric_limits<float>::max());
    for (size_t position = 0; position < count; ++position) {
        const int32_t bone = order[position];
        const int32_t parent = parents[bone];
        const float *local = local_values + bone * 16;
        for (size_t offset = 0; offset < 16; offset += 8) {
            const __m256 values = _mm256_and_ps(_mm256_loadu_ps(local + offset), absolute_mask);
            if (_mm256_movemask_ps(_mm256_cmp_ps(values, maximum, _CMP_LE_OQ)) != 255) {
                throw std::invalid_argument("Candidate local pose must be finite affine");
            }
        }
        if (local[3] != 0 || local[7] != 0 || local[11] != 0 || local[15] != 1) {
            throw std::invalid_argument("Candidate local pose must be finite affine");
        }
        auto &world = world_values[bone];
        if (parent < 0) {
            std::copy_n(local, 16, world.begin());
        } else {
            world = multiply_affine_avx(world_values[parent], local);
        }
        visible[bone] = visible[bone] != 0 && (parent < 0 || visible[parent] != 0);
        auto palette = multiply_affine_avx(world, inverse + bone * 16);
        if (!visible[bone]) {
            palette = {};
            palette[15] = 1;
        }
        for (size_t column = 0; column < 4; ++column) {
            _mm_storeu_ps(world_output + bone * 16 + column * 4,
                _mm256_cvtpd_ps(_mm256_loadu_pd(world.data() + column * 4)));
            _mm_storeu_ps(palette_output + bone * 16 + column * 4,
                _mm256_cvtpd_ps(_mm256_loadu_pd(palette.data() + column * 4)));
        }
    }
}
