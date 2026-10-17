#include "acceleration.hpp"
#include <jni.h>
#if defined(OMNIMORPH_GENERATED_JNI)
#include "top_aihmc_omnimorph_render_acceleration_NativeBindings.h"
static_assert(omnimorph::abi_version == top_aihmc_omnimorph_render_acceleration_NativeBindings_ABI);
static_assert(omnimorph::vertex_bytes == top_aihmc_omnimorph_render_acceleration_NativeBindings_STRIDE_BYTES);
static_assert(omnimorph::matrix_floats * sizeof(float) == top_aihmc_omnimorph_render_acceleration_NativeBindings_MATRIX_BYTES);
static_assert(omnimorph::max_vertices == top_aihmc_omnimorph_render_acceleration_NativeBindings_MAX_VERTICES);
#endif

namespace {

omnimorph::Buffer buffer(JNIEnv* env, jobject object, bool output) {
    if (object == nullptr) {
        return {nullptr, 0, false};
    }
    const jlong capacity = env->GetDirectBufferCapacity(object);
    auto* address = static_cast<std::byte*>(env->GetDirectBufferAddress(object));
    bool writable = false;
    if (output && capacity >= 0) {
        jclass type = env->GetObjectClass(object);
        jmethodID read_only = env->GetMethodID(type, "isReadOnly", "()Z");
        if (read_only != nullptr) {
            writable = env->CallBooleanMethod(object, read_only) == JNI_FALSE;
        }
        env->DeleteLocalRef(type);
    }
    return {address, capacity < 0 ? 0 : static_cast<std::size_t>(capacity), writable};
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    // These entry points only need JNI 1.6, shared by desktop JVMs and Android runtimes.
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass bindings = env->FindClass("top/aihmc/omnimorph/render/acceleration/NativeBindings");
    if (bindings == nullptr) {
        return JNI_ERR;
    }
    jfieldID abi = env->GetStaticFieldID(bindings, "ABI", "I");
    const bool compatible = abi != nullptr && env->GetStaticIntField(bindings, abi) == omnimorph::abi_version;
    env->DeleteLocalRef(bindings);
    return compatible ? JNI_VERSION_1_6 : JNI_ERR;
}

extern "C" JNIEXPORT jint JNICALL
Java_top_aihmc_omnimorph_render_acceleration_NativeBindings_handshake(JNIEnv*, jclass, jint java_abi) {
    return java_abi == omnimorph::abi_version ? omnimorph::abi_version : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_top_aihmc_omnimorph_render_acceleration_NativeBindings_capabilities(JNIEnv*, jclass) {
    return omnimorph::capability_bits(omnimorph::detect_cpu());
}

extern "C" JNIEXPORT jint JNICALL
Java_top_aihmc_omnimorph_render_acceleration_NativeBindings_transform(
    JNIEnv* env, jclass, jobject input, jint input_offset, jint input_stride,
    jobject matrices, jint matrix_offset, jobject output, jint output_offset,
    jint output_stride, jint count, jint kernel, jint flags) {
    const auto source = buffer(env, input, false);
    const auto matrix = buffer(env, matrices, false);
    const auto destination = buffer(env, output, true);
    if (env->ExceptionCheck()) {
        return static_cast<jint>(omnimorph::Status::invalid_argument);
    }
    // Heap/non-buffer objects and null buffers must not masquerade as empty direct buffers.
    if (input == nullptr || matrices == nullptr || output == nullptr
        || env->GetDirectBufferCapacity(input) < 0 || env->GetDirectBufferCapacity(matrices) < 0
        || env->GetDirectBufferCapacity(output) < 0) {
        return static_cast<jint>(omnimorph::Status::invalid_argument);
    }
    return static_cast<jint>(omnimorph::execute(source, input_offset, input_stride, matrix,
        matrix_offset, destination, output_offset, output_stride, count,
        static_cast<omnimorph::Kernel>(kernel), flags));
}
