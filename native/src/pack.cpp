#include "acceleration.hpp"

#include <cmath>
#include <cstdint>
#include <cstring>
#include <new>
#include <vector>

namespace omnimorph {
namespace {

bool span_valid(Buffer buffer, std::int32_t offset, std::size_t length) {
    return offset >= 0 && static_cast<std::size_t>(offset) <= buffer.size
           && length <= buffer.size - offset && (length == 0 || buffer.data != nullptr);
}

bool overlaps(const std::byte* first, std::size_t first_size,
              const std::byte* second, std::size_t second_size) {
    if (first_size == 0 || second_size == 0) {
        return false;
    }
    const auto a = reinterpret_cast<std::uintptr_t>(first);
    const auto b = reinterpret_cast<std::uintptr_t>(second);
    return a <= b ? b - a < first_size : a - b < second_size;
}

} // namespace

void scalar_pack(const double* input, float* output, std::int32_t count) {
    for (std::int32_t index = 0; index < count; ++index) {
        output[index] = static_cast<float>(input[index]);
    }
}

Status pack(Buffer input, std::int32_t input_offset, Buffer output, std::int32_t output_offset,
            std::int32_t count, Kernel requested) {
    if (!output.writable || count < 0 || count > max_pack_values) {
        return Status::invalid_argument;
    }
    const std::size_t input_length = static_cast<std::size_t>(count) * sizeof(double);
    const std::size_t output_length = static_cast<std::size_t>(count) * sizeof(float);
    if (!span_valid(input, input_offset, input_length) || !span_valid(output, output_offset, output_length)) {
        return Status::invalid_argument;
    }
    const auto features = detect_cpu();
    const Kernel selected = requested == Kernel::automatic ? select_kernel(features) : requested;
    if (!available(features, selected)) {
        return Status::unsupported_kernel;
    }
    if (count == 0) {
        return Status::ok;
    }
    const std::byte* source = input.data + input_offset;
    std::byte* destination = output.data + output_offset;
    if (overlaps(source, input_length, destination, output_length)) {
        return Status::invalid_argument;
    }
    try {
        // Direct buffers carry no alignment guarantee; kernels read and write aligned scratch copies.
        std::vector<double> values(static_cast<std::size_t>(count));
        std::memcpy(values.data(), source, input_length);
        std::vector<float> result(static_cast<std::size_t>(count));
        PackKernel kernel = scalar_pack;
#if defined(OMNIMORPH_ARM64)
        if (selected == Kernel::neon) {
            kernel = neon_pack;
        }
#else
        if (selected == Kernel::avx2) {
            kernel = avx2_pack;
        } else if (selected == Kernel::sse2) {
            kernel = sse2_pack;
        }
#endif
        kernel(values.data(), result.data(), count);
        // Publish only a complete finite result; overflowing or non-finite inputs keep the Java path.
        for (float value : result) {
            if (!std::isfinite(value)) {
                return Status::numerical_failure;
            }
        }
        std::memcpy(destination, result.data(), output_length);
        return Status::ok;
    } catch (const std::bad_alloc&) {
        return Status::exhausted;
    }
}

} // namespace omnimorph
