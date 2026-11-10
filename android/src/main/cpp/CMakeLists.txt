cmake_minimum_required(VERSION 3.22.1)
project(orvetra_engine LANGUAGES C CXX)
# Vendored MIT meshoptimizer codecs; no network access needed at runtime.
# Portable scene/time/component core (no Android headers or GLES calls).
add_library(oe_runtime_core STATIC runtime/oe_runtime.cpp runtime/walk_collision.cpp runtime/play_session.cpp runtime/solid_collision.cpp)
set_target_properties(oe_runtime_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(oe_runtime_core PUBLIC cxx_std_17)
target_include_directories(oe_runtime_core PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/runtime")

add_library(oe_road_network STATIC roads/road_network.cpp roads/road_junctions.cpp roads/road_topology.cpp roads/medieval_surface.cpp roads/traversal_graph.cpp roads/bridge_approach_visual.cpp)
set_target_properties(oe_road_network PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(oe_road_network PUBLIC cxx_std_17)
target_include_directories(oe_road_network PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/roads")

add_library(oe_terrain_paint STATIC terrainpaint/paint_tiles.cpp terrainpaint/material_layers.cpp)
set_target_properties(oe_terrain_paint PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(oe_terrain_paint PUBLIC cxx_std_17)
target_include_directories(oe_terrain_paint PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/terrainpaint")

add_library(oe_native SHARED
    native_engine.cpp
    medieval/structures.cpp
    water/lake_surface.cpp
    vendor/meshoptimizer/vertexcodec.cpp
    vendor/meshoptimizer/indexcodec.cpp
    vendor/meshoptimizer/vertexfilter.cpp
    vendor/meshoptimizer/allocator.cpp
    vendor/meshoptimizer/indexgenerator.cpp
    vendor/meshoptimizer/simplifier.cpp
    vendor/basisu/transcoder/basisu_transcoder.cpp
    vendor/basisu/zstd/zstddeclib.c)
target_compile_features(oe_native PRIVATE cxx_std_17)
target_link_libraries(oe_native PRIVATE oe_runtime_core oe_road_network oe_terrain_paint GLESv3 log android)

# Upstream BasisU requires non-strict aliasing for transcoder lookup tables.
set_source_files_properties(vendor/basisu/transcoder/basisu_transcoder.cpp PROPERTIES COMPILE_OPTIONS "-fno-strict-aliasing")
