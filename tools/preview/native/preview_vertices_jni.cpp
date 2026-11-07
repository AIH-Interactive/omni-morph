#include <jni.h>

#include <algorithm>
#include <cmath>
#include <exception>
#include <mutex>
#include <stdexcept>

#include "abi/cpu_executor.hpp"
#include "model/model.hpp"
#if defined(OMM_PREVIEW_AVX2)
#include "preview_pose_avx.hpp"
#endif

namespace {

// Preview inputs are float matrices. Keep double accumulation and its original
// left-to-right order, but consume the input columns without an intermediate copy.
omm::model::Matrix4 multiply_preview(const omm::model::Matrix4 &left, const float *right) noexcept {
    omm::model::Matrix4 result;
    for (size_t column = 0; column < 4; ++column) {
        const double x = right[column * 4];
        const double y = right[column * 4 + 1];
        const double z = right[column * 4 + 2];
        const double w = right[column * 4 + 3];
        for (size_t row = 0; row < 4; ++row) {
            result[column * 4 + row] =
                ((left[row] * x + left[4 + row] * y) + left[8 + row] * z) + left[12 + row] * w;
        }
    }
    return result;
}

std::vector<std::vector<size_t>> validate_order(const int32_t *parents, const int32_t *order,
    size_t total, int32_t offset, int32_t count, bool serial) {
    std::vector<size_t> depth(serial ? 0 : count);
    std::vector<bool> seen(count, false);
    std::vector<std::vector<size_t>> levels;
    size_t visited = 0;
    for (size_t position = 0; position < total; ++position) {
        const int32_t bone = order[position];
        if (bone < offset || bone >= offset + count) {
            continue;
        }
        const size_t index = static_cast<size_t>(bone - offset);
        const int32_t parent = parents[bone];
        if (seen[index] ||
            (parent >= 0 && (parent < offset || parent >= offset + count || !seen[parent - offset]))) {
            throw std::invalid_argument("preview skeleton order must visit each parent before its children");
        }
        if (!serial) {
            depth[index] = parent < 0 ? 0 : depth[parent - offset] + 1;
            if (levels.size() <= depth[index]) {
                levels.resize(depth[index] + 1);
            }
            levels[depth[index]].push_back(index);
        }
        seen[index] = true;
        ++visited;
    }
    if (visited != static_cast<size_t>(count)) {
        throw std::invalid_argument("preview skeleton order does not cover the requested range");
    }
    return levels;
}

} // namespace

// Preview-only bridge for an unlit canonical mesh. It does not alter the public
// pose API's invertible-normal contract: zero-scale visibility matrices are
// valid for positions here.
extern "C" JNIEXPORT void JNICALL Java_top_aihmc_omm_preview_PreviewNativeVertices_transform(
    JNIEnv *env, jclass, jlong handle, jobject palette_buffer, jobject output_buffer) {
    try {
        const auto instance = omm::model::instance_table().get(static_cast<uint64_t>(handle), omm_stage_jni);
        const std::lock_guard lock(instance->mutex);
        const auto &model = *instance->model;
        auto *palette = static_cast<const float *>(env->GetDirectBufferAddress(palette_buffer));
        auto *output = static_cast<float *>(env->GetDirectBufferAddress(output_buffer));
        if (palette == nullptr || output == nullptr ||
            env->GetDirectBufferCapacity(palette_buffer) < static_cast<jlong>(model.parts.size() * 16) ||
            env->GetDirectBufferCapacity(output_buffer) < static_cast<jlong>(model.vertices.size() * 5)) {
            throw std::invalid_argument("preview vertex buffers must be direct and cover the mesh/palette");
        }
        for (size_t part = 0; part < model.parts.size(); ++part) {
            omm::model::Matrix4 matrix;
            for (size_t value = 0; value < 16; ++value) {
                matrix[value] = palette[part * 16 + value];
            }
            if (!omm::model::is_finite_affine(matrix)) {
                throw std::invalid_argument("preview palette must be finite affine");
            }
        }
        // The shared preview shader consumes only positions and unchanged UVs. Match its
        // float palette math directly, without computing unused normals or allocating a
        // full production vertex array. The public model transform contract is unchanged.
        const auto &runtime = model.storage.runtime();
        constexpr size_t block_size = 256;
        const size_t blocks = (model.vertices.size() + block_size - 1) / block_size;
        runtime->cpu_executor->run(blocks, [&](size_t block) {
            const size_t end = std::min((block + 1) * block_size, model.vertices.size());
            for (size_t vertex = block * block_size; vertex < end; ++vertex) {
                const auto &source = model.vertices[vertex];
                const float *matrix = palette + source.part_id * 16;
                const float x = source.position[0], y = source.position[1], z = source.position[2];
                const float result_x = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
                const float result_y = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
                const float result_z = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
                if (!std::isfinite(result_x) || !std::isfinite(result_y) || !std::isfinite(result_z)) {
                    throw std::invalid_argument("preview transformed position exceeds finite float range");
                }
                output[vertex * 5] = result_x;
                output[vertex * 5 + 1] = result_y;
                output[vertex * 5 + 2] = result_z;
                output[vertex * 5 + 3] = source.uv[0];
                output[vertex * 5 + 4] = source.uv[1];
            }
        });
    } catch (const std::exception &failure) {
        if (!env->ExceptionCheck()) {
            if (const auto type = env->FindClass("java/lang/IllegalStateException")) {
                env->ThrowNew(type, failure.what());
                env->DeleteLocalRef(type);
            }
        }
    }
}

extern "C" JNIEXPORT void JNICALL Java_top_aihmc_omm_preview_PreviewNativeVertices_prepare(
    JNIEnv *env, jclass, jlong handle, jint offset, jint count, jobject parents_buffer, jobject order_buffer) {
    try {
        const auto instance = omm::model::instance_table().get(static_cast<uint64_t>(handle), omm_stage_jni);
        const std::lock_guard lock(instance->mutex);
        const auto total = instance->model->parts.size();
        if (offset < 0 || count < 0 || static_cast<size_t>(offset) + static_cast<size_t>(count) > total) {
            throw std::invalid_argument("preview skeleton range is invalid");
        }
        const auto *parents = static_cast<const int32_t *>(env->GetDirectBufferAddress(parents_buffer));
        const auto *order = static_cast<const int32_t *>(env->GetDirectBufferAddress(order_buffer));
        if (parents == nullptr || order == nullptr ||
            env->GetDirectBufferCapacity(parents_buffer) < static_cast<jlong>(total) ||
            env->GetDirectBufferCapacity(order_buffer) < static_cast<jlong>(total)) {
            throw std::invalid_argument("preview skeleton metadata must be direct and cover the model");
        }
        validate_order(parents, order, total, offset, count, true);
    } catch (const std::exception &failure) {
        if (!env->ExceptionCheck()) {
            if (const auto type = env->FindClass("java/lang/IllegalStateException")) {
                env->ThrowNew(type, failure.what());
                env->DeleteLocalRef(type);
            }
        }
    }
}

extern "C" JNIEXPORT void JNICALL Java_top_aihmc_omm_preview_PreviewNativeVertices_compose(
    JNIEnv *env, jclass, jlong handle, jint offset, jint count, jobject parents_buffer, jobject order_buffer,
    jobject inverse_buffer, jobject local_buffer, jobject visible_buffer, jobject world_buffer,
    jobject palette_buffer, jboolean prepared) {
    try {
        const auto instance = omm::model::instance_table().get(static_cast<uint64_t>(handle), omm_stage_jni);
        const std::lock_guard lock(instance->mutex);
        const auto total = instance->model->parts.size();
        if (offset < 0 || count < 0 || static_cast<size_t>(offset) + static_cast<size_t>(count) > total) {
            throw std::invalid_argument("preview skeleton range is invalid");
        }
        const auto records = [&](jobject buffer, size_t required) -> void * {
            auto *data = env->GetDirectBufferAddress(buffer);
            if (data == nullptr || env->GetDirectBufferCapacity(buffer) < static_cast<jlong>(required)) {
                throw std::invalid_argument(
                    "preview skeleton buffers must be direct and cover the requested range");
            }
            return data;
        };
        const auto *parents = static_cast<const int32_t *>(records(parents_buffer, total));
        const auto *order = static_cast<const int32_t *>(records(order_buffer, total));
        const auto *inverse = static_cast<const float *>(records(inverse_buffer, total * 16));
        const auto *local =
            static_cast<const float *>(records(local_buffer, static_cast<size_t>(count) * 16));
        auto *visible = static_cast<uint8_t *>(records(visible_buffer, count));
        auto *world_output = static_cast<float *>(records(world_buffer, static_cast<size_t>(count) * 16));
        auto *palette_output = static_cast<float *>(records(palette_buffer, static_cast<size_t>(count) * 16));
        const auto &runtime = instance->model->storage.runtime();
        const bool serial = runtime->worker_count == 1;
        std::vector<std::vector<size_t>> levels;
        // The private Java bridge caches only ranges validated by prepare(), and never
        // modifies its copied parents/order buffers after construction.
        if (!serial || !prepared) {
            levels = validate_order(parents, order, total, offset, count, serial);
        }
#if defined(OMM_PREVIEW_AVX2)
        if (serial && runtime->active_kernel == omm_cpu_avx2) {
            omm::preview::compose_avx2(instance->pose, offset, count, parents, order, inverse, local,
                visible, world_output, palette_output);
            return;
        }
#endif
        // This bridge owns a private instance and holds its mutex. Its existing
        // double world matrices provide per-instance scratch without a frame allocation.
        auto &pose = instance->pose;
        const auto compose_world = [&](size_t index) {
            omm::model::Matrix4 matrix;
            std::copy_n(local + index * 16, 16, matrix.begin());
            if (!omm::model::is_finite_affine(matrix)) {
                throw std::invalid_argument("preview local pose is not finite affine");
            }
            const int32_t parent = parents[index + offset];
            pose[index + offset].world =
                parent < 0 ? matrix : multiply_preview(pose[parent].world, local + index * 16);
            visible[index] = visible[index] != 0 && (parent < 0 || visible[parent - offset] != 0);
        };
        const auto write_palette = [&](size_t index) {
            const auto &world = pose[index + offset].world;
            auto palette = multiply_preview(world, inverse + (index + offset) * 16);
            if (!visible[index]) {
                palette = {};
                palette[15] = 1;
            }
            for (size_t value = 0; value < 16; ++value) {
                world_output[index * 16 + value] = static_cast<float>(world[value]);
                palette_output[index * 16 + value] = static_cast<float>(palette[value]);
            }
        };
        if (serial) {
            // The validated order already satisfies dependencies. A single participant
            // needs neither depth buckets nor per-level executor dispatch.
            for (size_t position = 0; position < total; ++position) {
                const int32_t bone = order[position];
                if (bone >= offset && bone < offset + count) {
                    const size_t index = static_cast<size_t>(bone - offset);
                    compose_world(index);
                    write_palette(index);
                }
            }
        } else {
            for (const auto &level : levels) {
                runtime->cpu_executor->run(level.size(), [&](size_t task) {
                    compose_world(level[task]);
                });
            }
            runtime->cpu_executor->run(count, write_palette);
        }
    } catch (const std::exception &failure) {
        if (!env->ExceptionCheck()) {
            if (const auto type = env->FindClass("java/lang/IllegalStateException")) {
                env->ThrowNew(type, failure.what());
                env->DeleteLocalRef(type);
            }
        }
    }
}

extern "C" JNIEXPORT jint JNICALL Java_top_aihmc_omm_preview_PreviewNativeDraw_submit(
    JNIEnv *env, jclass, jlong bind_vertex_array, jlong bind_texture, jlong uniform_1i, jlong draw_elements,
    jint vao, jint textured_uniform, jobject command_buffer, jint count) {
    using BindVertexArray = void(JNICALL *)(uint32_t);
    using BindTexture = void(JNICALL *)(uint32_t, uint32_t);
    using Uniform1i = void(JNICALL *)(int32_t, int32_t);
    using DrawElements = void(JNICALL *)(uint32_t, int32_t, uint32_t, const void *);
    const auto *commands = static_cast<const int32_t *>(env->GetDirectBufferAddress(command_buffer));
    if (count < 0 || commands == nullptr ||
        env->GetDirectBufferCapacity(command_buffer) < int64_t(count) * 4 || bind_vertex_array == 0 ||
        bind_texture == 0 || uniform_1i == 0 || draw_elements == 0) {
        if (const auto type = env->FindClass("java/lang/IllegalArgumentException")) {
            env->ThrowNew(type, "native GL submission requires valid entry points and direct draw commands");
            env->DeleteLocalRef(type);
        }
        return 0;
    }
    const auto bind_vao = reinterpret_cast<BindVertexArray>(static_cast<uintptr_t>(bind_vertex_array));
    const auto bind_tex = reinterpret_cast<BindTexture>(static_cast<uintptr_t>(bind_texture));
    const auto set_uniform = reinterpret_cast<Uniform1i>(static_cast<uintptr_t>(uniform_1i));
    const auto draw = reinterpret_cast<DrawElements>(static_cast<uintptr_t>(draw_elements));
    bind_vao(static_cast<uint32_t>(vao));
    jint submitted = 0;
    for (jint index = 0; index < count; ++index) {
        const auto *command = commands + index * 4;
        bind_tex(0x0DE1, static_cast<uint32_t>(command[2])); // GL_TEXTURE_2D
        set_uniform(textured_uniform, command[3]);
        const auto byte_offset = static_cast<uintptr_t>(command[0]) * sizeof(uint32_t);
        draw(0x0004, command[1], 0x1405,
             reinterpret_cast<const void *>(byte_offset)); // TRIANGLES, UNSIGNED_INT
        ++submitted;
    }
    return submitted;
}
