#pragma once
// OE R5.1: portable, deterministic road connection graph and junction baking.
// No Android/GLES/physics dependencies. Coordinates are X/Z metres.
#include "road_network.h"
#include <cstddef>
#include <cstdint>
#include <vector>

namespace oe { namespace roads {
constexpr std::size_t kMaxTopologySegments=1200;
constexpr std::size_t kMaxTopologyNodes=2400;
constexpr std::size_t kMaxTopologyEdges=2400;
constexpr std::size_t kMaxTopologyCrossings=96;
constexpr double kTopologySnapRadius=2.0;

struct RoadNode {
    std::uint64_t id=0; // deterministic centimetre-grid key (not a runtime handle)
    Point position;
    std::vector<std::uint64_t> connectedRoads; // >=2 means a shared junction
    unsigned degree=0; // graph edges touching this node
};
struct RoadEdge {
    std::uint64_t roadId=0;
    std::uint64_t from=0,to=0; // stable node keys
};
struct RoadTopology {
    std::vector<Road> roads; // original roads, plus baked shared control points
    std::vector<RoadNode> nodes;
    std::vector<RoadEdge> edges;
    std::size_t junctionCount=0;
    std::size_t crossingCount=0;
};

// Atomic; failure NEVER changes output. Auto-snaps road ENDPOINTS only to
// another road's nearest segment within the requested radius. Every actual
// X/T/Y crossing becomes a shared editable control point on BOTH polylines.
// Existing road IDs, material, name and width are unchanged.
// Collinear positive-length overlaps are rejected rather than falsely joined.
// A deterministic derived node key is the same after v5 scene roundtrip.
// This models centreline topology only: no collision, sidewalks or nav AI.
bool buildRoadTopology(const std::vector<Road>& input,RoadTopology& output,
                       double endpointSnapMetres=kTopologySnapRadius);
}} // namespace oe::roads
