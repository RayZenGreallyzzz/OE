cmake_minimum_required(VERSION 3.22.1)
project(orvetra_engine LANGUAGES C CXX)
# Vendored MIT meshoptimizer codecs; no network access needed at runtime.
add_library(oe_native SHARED
    native_engine.cpp
    vendor/meshoptimizer/vertexcodec.cpp
    vendor/meshoptimizer/indexcodec.cpp
    vendor/meshoptimizer/vertexfilter.cpp
    vendor/meshoptimizer/allocator.cpp
    vendor/meshoptimizer/indexgenerator.cpp
    vendor/meshoptimizer/simplifier.cpp
    vendor/basisu/transcoder/basisu_transcoder.cpp
    vendor/basisu/zstd/zstddeclib.c)
target_compile_features(oe_native PRIVATE cxx_std_17)
target_link_libraries(oe_native PRIVATE GLESv3 log android)

# BasisU upstream requires non-strict aliasing for its transcoder tables.
set_source_files_properties(vendor/basisu/transcoder/basisu_transcoder.cpp PROPERTIES COMPILE_OPTIONS "-fno-strict-aliasing")
