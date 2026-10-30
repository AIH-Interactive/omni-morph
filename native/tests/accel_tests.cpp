#include "acceleration.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <random>
#include <stdexcept>
#include <vector>

using namespace omnimorph;

namespace {

void require(bool condition, const char* message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

Buffer view(std::vector<std::byte>& storage, bool writable = true) {
    return {storage.data(), storage.size(), writable};
}

void put(std::vector<std::byte>& storage, std::size_t offset, float value) {
    std::memcpy(storage.data() + offset, &value, sizeof(value));
}

float get(const std::vector<std::byte>& storage, std::size_t offset) {
    float value;
    std::memcpy(&value, storage.data() + offset, sizeof(value));
    return value;
}

void dispatch_tests() {
    CpuFeatures cpu;
    require(select_kernel(cpu) == Kernel::scalar, "no ISA must select scalar");
    cpu.avx2 = true;
    cpu.fma = true;
    require(!available(cpu, Kernel::avx2), "AVX2 bit without SSE2/AVX/OS state must reject");
    cpu.sse2 = true;
    cpu.avx = true;
    cpu.osxsave = true;
    require(select_kernel(cpu) == Kernel::sse2, "missing YMM OS state must select SSE2");
    cpu.xmm_ymm_state = true;
    cpu.fma = false;
    require(select_kernel(cpu) == Kernel::avx2, "AVX2 must not require FMA");
    cpu.osxsave = false;
    require(!available(cpu, Kernel::avx2), "missing OSXSAVE must reject AVX2");
    require(!available(cpu, static_cast<Kernel>(99)), "unknown kernel must reject");
    cpu = CpuFeatures{};
    cpu.neon = true;
    require(select_kernel(cpu) == Kernel::neon && available(cpu, Kernel::neon), "arm64 selects NEON");
}

void numeric_and_boundary_tests() {
    constexpr int count = 257;
    constexpr int offset = 1;
    constexpr int stride = 41;
    std::vector<std::byte> input(offset + count * stride, std::byte{0x55});
    std::vector<std::byte> matrix(161);
    std::vector<std::byte> reference(input.size(), std::byte{0x33});
    std::mt19937 random(0x482761);
    std::uniform_real_distribution<float> values(-5, 5);
    for (int vertex = 0; vertex < count; ++vertex) {
        for (int component = 0; component < vertex_floats; ++component) {
            put(input, offset + vertex * stride + component * sizeof(float), values(random));
        }
    }
    for (int index = 0; index < matrix_floats; ++index) {
        put(matrix, offset + index * sizeof(float), values(random));
    }
    auto run = [&](std::vector<std::byte>& output, int vertices, Kernel kernel, int flags = 0) {
        return execute(view(input, false), offset, stride, view(matrix, false), offset,
                       view(output), offset, stride, vertices, kernel, flags);
    };
    require(run(reference, count, Kernel::scalar) == Status::ok, "scalar random batch");
    for (Kernel kernel : {Kernel::scalar, Kernel::sse2, Kernel::avx2, Kernel::neon, Kernel::automatic}) {
        if (kernel != Kernel::automatic && !available(detect_cpu(), kernel)) {
            continue;
        }
        for (int vertices : {0, 1, 2, 3, 16, 256, 257}) {
            std::vector<std::byte> output(input.size(), std::byte{0x33});
            require(run(output, vertices, kernel) == Status::ok, "unaligned/tail kernel");
            for (int vertex = 0; vertex < vertices; ++vertex) {
                for (int component = 0; component < vertex_floats; ++component) {
                    const auto position = offset + vertex * stride + component * sizeof(float);
                    const float expected = get(reference, position);
                    require(std::abs(get(output, position) - expected) <= 1e-4f + 1e-5f * std::abs(expected),
                            "kernel numeric mismatch");
                }
                require(output[offset + vertex * stride + vertex_bytes] == std::byte{0x33}, "padding overwritten");
            }
            require(output[0] == std::byte{0x33}, "prefix overwritten");
        }
    }
    std::vector<std::byte> output(input.size(), std::byte{0x33});
    const auto sentinel = output;
    auto rejected = [&](Status status) {
        require(status != Status::ok, "invalid request accepted");
        require(output == sentinel, "failed request mutated output");
    };
    rejected(run(output, max_vertices + 1, Kernel::automatic));
    rejected(run(output, -1, Kernel::automatic));
    rejected(run(output, count + 1, Kernel::automatic));
    rejected(run(output, count, static_cast<Kernel>(99)));
    rejected(run(output, count, Kernel::automatic, 4));
    rejected(execute(view(input), -1, stride, view(matrix), offset, view(output), offset,
                     stride, count, Kernel::automatic, 0));
    rejected(execute(view(input), offset, 35, view(matrix), offset, view(output), offset,
                     stride, count, Kernel::automatic, 0));
    rejected(execute(view(input), offset, stride, view(matrix), offset, view(output, false), offset,
                     stride, count, Kernel::automatic, 0));
    require(execute(view(input), offset, stride, view(matrix), offset, view(input), offset,
                    stride, count, Kernel::automatic, 0) == Status::invalid_argument, "alias accepted");
    put(input, offset, std::numeric_limits<float>::quiet_NaN());
    rejected(run(output, count, Kernel::automatic));
    put(input, offset, std::numeric_limits<float>::infinity());
    rejected(run(output, count, Kernel::automatic));
    put(input, offset, std::numeric_limits<float>::max());
    put(matrix, offset, std::numeric_limits<float>::max());
    rejected(run(output, count, Kernel::automatic));
    put(matrix, offset, std::numeric_limits<float>::infinity());
    rejected(run(output, count, Kernel::automatic));
}

void pack_tests() {
    constexpr int count = 163;
    constexpr int offset = 3;
    std::vector<std::byte> input(offset + count * sizeof(double));
    std::mt19937 random(0x7151);
    std::uniform_real_distribution<double> values(-1e6, 1e6);
    std::vector<double> source(count);
    for (int index = 0; index < count; ++index) {
        // Include values that round, denormal-range values and exact integers.
        source[index] = index % 7 == 0 ? 1e-40 * index : index % 5 == 0 ? index : values(random);
    }
    std::memcpy(input.data() + offset, source.data(), count * sizeof(double));
    for (Kernel kernel : {Kernel::scalar, Kernel::sse2, Kernel::avx2, Kernel::neon, Kernel::automatic}) {
        if (kernel != Kernel::automatic && !available(detect_cpu(), kernel)) {
            continue;
        }
        for (int values_count : {0, 1, 3, 4, 7, 8, 9, 40, count}) {
            std::vector<std::byte> output(offset + count * sizeof(float) + 4, std::byte{0x33});
            require(pack(view(input, false), offset, view(output), offset, values_count, kernel) == Status::ok,
                    "pack unaligned/tail");
            for (int index = 0; index < values_count; ++index) {
                const float expected = static_cast<float>(source[index]);
                float actual;
                std::memcpy(&actual, output.data() + offset + index * sizeof(float), sizeof(actual));
                require(std::memcmp(&actual, &expected, sizeof(float)) == 0, "pack differs from a float cast");
            }
            require(output[offset + values_count * sizeof(float)] == std::byte{0x33}, "pack wrote past its range");
        }
    }
    std::vector<std::byte> output(offset + count * sizeof(float), std::byte{0x33});
    const auto sentinel = output;
    auto rejected = [&](Status status) {
        require(status != Status::ok, "invalid pack accepted");
        require(output == sentinel, "failed pack mutated output");
    };
    rejected(pack(view(input), offset, view(output), offset, count + 1, Kernel::automatic));
    rejected(pack(view(input), offset, view(output), offset, -1, Kernel::automatic));
    rejected(pack(view(input), -1, view(output), offset, 1, Kernel::automatic));
    rejected(pack(view(input), offset, view(output, false), offset, 1, Kernel::automatic));
    rejected(pack(view(input), offset, view(output), offset, 1, static_cast<Kernel>(99)));
    require(pack(view(input), offset, view(input), offset, 4, Kernel::automatic) == Status::invalid_argument,
            "pack alias accepted");
    const double overflow = 1e300;
    std::memcpy(input.data() + offset + 5 * sizeof(double), &overflow, sizeof(overflow));
    rejected(pack(view(input), offset, view(output), offset, count, Kernel::automatic));
    const double nan = std::numeric_limits<double>::quiet_NaN();
    std::memcpy(input.data() + offset + 5 * sizeof(double), &nan, sizeof(nan));
    rejected(pack(view(input), offset, view(output), offset, count, Kernel::automatic));
}

} // namespace

int main() {
    try {
        dispatch_tests();
        numeric_and_boundary_tests();
        pack_tests();
        std::cout << "PASS native dispatch, scalar/available SSE2/AVX2/NEON, unaligned/tails, bounds, alias, finite publication, pose pack; capabilities="
                  << capability_bits(detect_cpu()) << '\n';
        return 0;
    } catch (const std::exception& failure) {
        std::cerr << "FAIL " << failure.what() << '\n';
        return 1;
    }
}
