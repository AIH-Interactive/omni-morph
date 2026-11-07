#include "preview_pose_avx.hpp"

#include <immintrin.h>
#include <limits>
#include <stdexcept>

namespace omm::preview {
namespace {

model::Matrix4 multiply(const model::Matrix4 &left, const float *right) {
    model::Matrix4 result;
    const __m256d a = _mm256_loadu_pd(left.data());
    const __m256d b = _mm256_loadu_pd(left.data() + 4);
    const __m256d c = _mm256_loadu_pd(left.data() + 8);
    const __m256d d = _mm256_loadu_pd(left.data() + 12);
    for (size_t column = 0; column < 4; ++column) {
        __m256d value = _mm256_mul_pd(a, _mm256_set1_pd(right[column * 4]));
        value = _mm256_add_pd(value, _mm256_mul_pd(b, _mm256_set1_pd(right[column * 4 + 1])));
        value = _mm256_add_pd(value, _mm256_mul_pd(c, _mm256_set1_pd(right[column * 4 + 2])));
        value = _mm256_add_pd(value, _mm256_mul_pd(d, _mm256_set1_pd(right[column * 4 + 3])));
        _mm256_storeu_pd(result.data() + column * 4, value);
    }
    return result;
}

} // namespace

void compose_avx2(std::vector<model::PartPose> &pose, int32_t offset, int32_t count,
    const int32_t *parents, const int32_t *order, const float *inverse, const float *local,
    uint8_t *visible, float *world_output, float *palette_output) {
    const __m256 absolute_mask = _mm256_castsi256_ps(_mm256_set1_epi32(0x7fffffff));
    const __m256 maximum = _mm256_set1_ps(std::numeric_limits<float>::max());
    for (size_t position = 0; position < pose.size(); ++position) {
        const int32_t bone = order[position];
        if (bone < offset || bone >= offset + count) {
            continue;
        }
        const size_t index = static_cast<size_t>(bone - offset);
        const float *input = local + index * 16;
        for (size_t value = 0; value < 16; value += 8) {
            const __m256 values = _mm256_and_ps(_mm256_loadu_ps(input + value), absolute_mask);
            if (_mm256_movemask_ps(_mm256_cmp_ps(values, maximum, _CMP_LE_OQ)) != 255) {
                throw std::invalid_argument("preview local pose is not finite affine");
            }
        }
        if (input[3] != 0 || input[7] != 0 || input[11] != 0 || input[15] != 1) {
            throw std::invalid_argument("preview local pose is not finite affine");
        }
        const int32_t parent = parents[bone];
        auto &world = pose[bone].world;
        if (parent < 0) {
            for (size_t column = 0; column < 4; ++column) {
                _mm256_storeu_pd(world.data() + column * 4, _mm256_cvtps_pd(_mm_loadu_ps(input + column * 4)));
            }
        } else {
            world = multiply(pose[parent].world, input);
        }
        visible[index] = visible[index] != 0 && (parent < 0 || visible[parent - offset] != 0);
        auto palette = multiply(world, inverse + static_cast<size_t>(bone) * 16);
        if (!visible[index]) {
            palette = {};
            palette[15] = 1;
        }
        for (size_t column = 0; column < 4; ++column) {
            _mm_storeu_ps(world_output + index * 16 + column * 4,
                _mm256_cvtpd_ps(_mm256_loadu_pd(world.data() + column * 4)));
            _mm_storeu_ps(palette_output + index * 16 + column * 4,
                _mm256_cvtpd_ps(_mm256_loadu_pd(palette.data() + column * 4)));
        }
    }
}

} // namespace omm::preview
