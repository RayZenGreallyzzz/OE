cmake_minimum_required(VERSION 3.22.1)
project(orvetra_engine LANGUAGES CXX)
# Vendored MIT meshoptimizer codecs; no network access needed at runtime.
# Portable scene/time/component core (no Android headers or GLES calls).
add_library(oe_runtime_core STATIC runtime/oe_runtime.cpp)
set_target_properties(oe_runtime_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(oe_runtime_core PUBLIC cxx_std_17)
target_include_directories(oe_runtime_core PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/runtime")

add_library(oe_native SHARED
    native_engine.cpp
    vendor/meshoptimizer/vertexcodec.cpp
    vendor/meshoptimizer/indexcodec.cpp
    vendor/meshoptimizer/vertexfilter.cpp
    vendor/meshoptimizer/allocator.cpp
    vendor/meshoptimizer/indexgenerator.cpp
    vendor/meshoptimizer/simplifier.cpp)
target_compile_features(oe_native PRIVATE cxx_std_17)
target_link_libraries(oe_native PRIVATE oe_runtime_core GLESv3 log android)
