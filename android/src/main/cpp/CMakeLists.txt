cmake_minimum_required(VERSION 3.22.1)
project(orvetra_engine LANGUAGES CXX)
# Vendored MIT meshoptimizer codecs; no network access needed at runtime.
add_library(oe_native SHARED
    native_engine.cpp
    vendor/meshoptimizer/vertexcodec.cpp
    vendor/meshoptimizer/indexcodec.cpp
    vendor/meshoptimizer/vertexfilter.cpp
    vendor/meshoptimizer/allocator.cpp)
target_compile_features(oe_native PRIVATE cxx_std_17)
target_link_libraries(oe_native PRIVATE GLESv3 log android)
