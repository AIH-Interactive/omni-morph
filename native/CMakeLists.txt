if(NOT WIN32 OR NOT MSVC OR NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "H2 supports native MSVC Windows x64 builds only")
endif()

find_package(JNI REQUIRED)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

add_library(accel_core STATIC src/dispatch.cpp src/transform.cpp src/scalar.cpp src/sse2.cpp src/avx2.cpp)
target_include_directories(accel_core PUBLIC include)
target_compile_options(accel_core PRIVATE /W4 /fp:strict)
# Keep CPUID, JNI_OnLoad, validation and scalar/SSE2 code at baseline ISA.
set_source_files_properties(src/avx2.cpp PROPERTIES COMPILE_OPTIONS "/arch:AVX2")

add_library(omnimorph_accel SHARED src/jni.cpp)
target_link_libraries(omnimorph_accel PRIVATE accel_core)
target_include_directories(omnimorph_accel PRIVATE ${JNI_INCLUDE_DIRS})
target_compile_options(omnimorph_accel PRIVATE /W4 /fp:strict)
set_target_properties(omnimorph_accel PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")

add_executable(accel_tests tests/accel_tests.cpp)
target_link_libraries(accel_tests PRIVATE accel_core)
target_compile_options(accel_tests PRIVATE /W4 /fp:strict)
add_test(NAME acceleration_native COMMAND accel_tests)
