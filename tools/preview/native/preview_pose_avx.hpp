#pragma once

#include "model/model.hpp"

namespace omm::preview {

// The caller holds the instance mutex and has validated the range and parent-first order.
void compose_avx2(std::vector<model::PartPose> &pose, int32_t offset, int32_t count,
    const int32_t *parents, const int32_t *order, const float *inverse, const float *local,
    uint8_t *visible, float *world_output, float *palette_output);

} // namespace omm::preview
