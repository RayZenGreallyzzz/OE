#pragma once
// OE R5.2: portable terrain-conforming, stitched medieval street surfaces.
// The centreline topology and original scene Road IDs remain authoritative.
#include "road_topology.h"
#include <cstddef>
#include <cstdint>
#include <vector>
namespace oe { namespace roads {

struct MedievalSurfacePart {
    std::uint64_t roadId=0; // 0 only for non-selectable junction fill
    std::uint64_t materialRoadId=0; // source of preview material
    SurfaceMesh mesh;
};
struct MedievalSurface {
    std::vector<MedievalSurfacePart> roads;     // road ribbon faces, cut at junction caps
    std::vector<MedievalSurfacePart> junctions; // fan faces share EXACT cap vertices
    std::size_t stitchedNodes=0;
    std::size_t triangles=0;
};
// Full atomic result; rejects unsafe narrow angles, too-short approaches,
// non-finite heights or over-complex streets rather than emitting cracked mesh.
// Caller may fall back to R5 ribbons on rejected exotic geometry.
bool buildMedievalSurface(const RoadTopology& topology,const TerrainHeight& height,
                          MedievalSurface& output,double stepMetres=2.0);
}} // namespace oe::roads
