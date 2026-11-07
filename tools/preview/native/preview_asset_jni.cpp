#include <jni.h>
#include <yyjson.h>

#include <cstdlib>
#include <limits>
#include <memory>
#include <stdexcept>

#include "ysmparser/access.hpp"
#include "ysmparser/json.hpp"

// Bind the already parsed native geometry; no source JSON or encrypted payload is parsed by Java again.
extern "C" JNIEXPORT jbyteArray JNICALL Java_top_aihmc_omm_preview_PreviewNativeAsset_geometry(
    JNIEnv *env, jclass, jlong runtime_handle, jlong asset_handle, jint geometry_id) {
    try {
        const auto runtime =
            omm::abi::runtime_table().get(static_cast<uint64_t>(runtime_handle), omm_stage_jni);
        const auto asset = omm::ysm::retain_asset(static_cast<uint64_t>(asset_handle), runtime.get());
        if (geometry_id < 0 || static_cast<size_t>(geometry_id) >= asset->geometries.size()) {
            throw std::invalid_argument("preview geometry ID is outside the parsed asset");
        }
        const auto &geometry = asset->geometries[geometry_id];
        std::unique_ptr<yyjson_mut_doc, decltype(&yyjson_mut_doc_free)> document(yyjson_mut_doc_new(nullptr),
                                                                                 yyjson_mut_doc_free);
        if (!document) {
            throw std::bad_alloc();
        }
        auto *doc = document.get();
        const auto put = [&](yyjson_mut_val *object, const char *key, yyjson_mut_val *value) {
            yyjson_mut_obj_add(object, yyjson_mut_strcpy(doc, key), value);
        };
        const auto numbers = [&](const auto &values) {
            auto *array = yyjson_mut_arr(doc);
            for (float value : values) {
                const auto text = omm::ysm::json::float_text(value);
                yyjson_mut_arr_append(array, yyjson_mut_rawncpy(doc, text.data(), text.size()));
            }
            return array;
        };
        auto *root = yyjson_mut_obj(doc);
        put(root, "texture_width", yyjson_mut_sint(doc, geometry.texture_width));
        put(root, "texture_height", yyjson_mut_sint(doc, geometry.texture_height));
        auto *bones = yyjson_mut_arr(doc);
        for (const auto &bone : geometry.bones) {
            auto *item = yyjson_mut_obj(doc);
            put(item, "name", yyjson_mut_strncpy(doc, bone.name.data(), bone.name.size()));
            put(item, "parent", yyjson_mut_strncpy(doc, bone.parent.data(), bone.parent.size()));
            put(item, "pivot", numbers(bone.pivot));
            put(item, "rotation", numbers(bone.rotation));
            auto *cubes = yyjson_mut_arr(doc);
            for (const auto &cube : bone.cubes) {
                auto *quads = yyjson_mut_arr(doc);
                for (const auto &quad : cube.quads) {
                    auto *face = yyjson_mut_obj(doc);
                    put(face, "positions", numbers(quad.positions));
                    put(face, "uvs", numbers(quad.uvs));
                    put(face, "normal", numbers(quad.normal));
                    yyjson_mut_arr_append(quads, face);
                }
                yyjson_mut_arr_append(cubes, quads);
            }
            put(item, "cubes", cubes);
            yyjson_mut_arr_append(bones, item);
        }
        put(root, "bones", bones);
        yyjson_mut_doc_set_root(doc, root);
        size_t length = 0;
        std::unique_ptr<char, decltype(&std::free)> text(yyjson_mut_write(doc, 0, &length), std::free);
        if (!text) {
            throw std::bad_alloc();
        }
        if (length > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
            throw std::length_error("preview geometry binding exceeds a Java byte array");
        }
        auto result = env->NewByteArray(static_cast<jsize>(length));
        if (result != nullptr) {
            env->SetByteArrayRegion(result, 0, static_cast<jsize>(length),
                                    reinterpret_cast<const jbyte *>(text.get()));
        }
        return result;
    } catch (const std::exception &failure) {
        if (!env->ExceptionCheck()) {
            if (const auto type = env->FindClass("java/io/IOException")) {
                env->ThrowNew(type, failure.what());
                env->DeleteLocalRef(type);
            }
        }
        return nullptr;
    }
}
