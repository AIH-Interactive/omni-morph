#include "acceleration.hpp"

#include <cmath>
#include <cstring>
#include <limits>
#include <new>
#include <vector>

namespace omnimorph {
namespace {

bool range_valid(Buffer buffer, std::int32_t offset, std::int32_t stride, std::int32_t count,
                 std::size_t width, std::size_t& length) {
    if (offset < 0 || stride < static_cast<std::int32_t>(width) || count < 0 || count > max_vertices) {
        return false;
    }
    length = count == 0 ? 0 : static_cast<std::size_t>(count - 1) * stride + width;
    return static_cast<std::size_t>(offset) <= buffer.size
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

bool finite_values(const std::byte* data, std::size_t count) {
    for (std::size_t index = 0; index < count; ++index) {
        float value;
        std::memcpy(&value, data + index * sizeof(float), sizeof(value));
        if (!std::isfinite(value)) {
            return false;
        }
    }
    return true;
}

} // namespace

Status execute(Buffer input, std::int32_t input_offset, std::int32_t input_stride,
               Buffer matrices, std::int32_t matrix_offset, Buffer output,
               std::int32_t output_offset, std::int32_t output_stride,
               std::int32_t count, Kernel requested, std::int32_t flags) {
    std::size_t input_length, output_length, matrix_length;
    if (!output.writable || (flags & ~(normalize_normals | affine_output_w)) != 0
        || !range_valid(input, input_offset, input_stride, count, vertex_bytes, input_length)
        || !range_valid(output, output_offset, output_stride, count, vertex_bytes, output_length)
        || !range_valid(matrices, matrix_offset, matrix_floats * sizeof(float), 1,
                        matrix_floats * sizeof(float), matrix_length)) {
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
    const auto* source = input.data + input_offset;
    auto* destination = output.data + output_offset;
    const auto* matrix_data = matrices.data + matrix_offset;
    if (overlaps(source, input_length, destination, output_length)
        || overlaps(matrix_data, matrix_length, destination, output_length)) {
        return Status::invalid_argument;
    }
    if (!finite_values(matrix_data, matrix_floats)) {
        return Status::numerical_failure;
    }
    for (std::int32_t vertex = 0; vertex < count; ++vertex) {
        if (!finite_values(source + static_cast<std::size_t>(vertex) * input_stride, vertex_floats)) {
            return Status::numerical_failure;
        }
    }
    Transform transform{};
    static_assert(offsetof(Transform, normal) == 16 * sizeof(float));
    static_assert(offsetof(Transform, uv) == 28 * sizeof(float));
    std::memcpy(&transform, matrix_data, matrix_floats * sizeof(float));
    transform.flags = flags;
    try {
        // Publish only a complete finite result. A rejected batch never touches the caller's output.
        std::vector<std::byte> result(static_cast<std::size_t>(count) * vertex_bytes);
        TransformKernel kernel = selected == Kernel::avx2 ? avx2_kernel
                                 : selected == Kernel::sse2 ? sse2_kernel : scalar_kernel;
        kernel(source, input_stride, result.data(), vertex_bytes, count, transform);
        if (!finite_values(result.data(), static_cast<std::size_t>(count) * vertex_floats)) {
            return Status::numerical_failure;
        }
        for (std::int32_t vertex = 0; vertex < count; ++vertex) {
            std::memcpy(destination + static_cast<std::size_t>(vertex) * output_stride,
                        result.data() + static_cast<std::size_t>(vertex) * vertex_bytes, vertex_bytes);
        }
        return Status::ok;
    } catch (const std::bad_alloc&) {
        return Status::exhausted;
    }
}

} // namespace omnimorph
