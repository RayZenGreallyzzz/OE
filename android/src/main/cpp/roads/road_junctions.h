#pragma once
// OE R5: portable road authoring geometry helpers. C++17, no Android/GLES.
#include "road_network.h"
#include <cstddef>
#include <cstdint>
#include <vector>

namespace oe { namespace roads {

constexpr std::size_t kMaxJunctions=96;
constexpr std::size_t kMaxJunctionSegments=1800;
struct Junction {
    Point center;
    double radiusMetres=1.0;
    std::uint64_t roadA=0,roadB=0;
};

// Snaps to existing ROAD ENDPOINTS only. Never changes source data;
// returns the original query when no endpoint is within maxDistance.
// Exclusion prevents self-snapping while extending an existing road.
Point snapEndpoint(const std::vector<Road>& roads,Point query,double maxDistance,
                   std::uint64_t excludeRoad=0) noexcept;

// Inserts quadratic Bezier fillets around non-collinear interior corners.
// Source control points are replaced by editable points: no format migration.
// All-or-nothing on invalid or out-of-budget paths, including sharp U-turns.
bool roundCorners(const Road& input,double cutMetres,Road& output,
                  unsigned subdivisions=4);

// Detects 2D centreline crossings between DIFFERENT roads, including endpoint
// meetings and T/X intersections. Collinear overlap is deliberately excluded.
// Returns false rather than freezing the tablet on extremely complex networks.
// This is a visual authoring preview, NOT a navgraph or watertight road mesh.
bool findJunctions(const std::vector<Road>& roads,std::vector<Junction>& output);

// Creates a terrain-conforming triangle-fan preview at the intersection.
// The source road strips are still separate; patch is NOT collision topology.
bool buildJunctionPreview(const Junction& intersection,const TerrainHeight& height,
                          SurfaceMesh& output,unsigned sides=16);

}} // namespace
