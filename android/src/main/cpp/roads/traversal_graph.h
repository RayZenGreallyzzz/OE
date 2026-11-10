#pragma once
// OE R5.4: road/bridge traversal and walkable collision SURFACE DATA.
// Portable C++17, no GLES, Android, game physics or scene format dependency.
#include "road_topology.h"
#include "../medieval/structures.h"
#include <cstddef>
#include <cstdint>
#include <vector>

namespace oe { namespace roads {
constexpr std::size_t kMaxTraversalBridges=120;
constexpr std::size_t kMaxTraversalSurfaces=3000;
constexpr double kBridgeRoadReach=2.5; // metres from a bridge mouth
constexpr double kBridgeStepTolerance=.65; // y metres; no fake vertical teleport

struct BridgeInstance {
 std::uint64_t id=0; int type=0;
 Point center{};
 double y=0,yaw=0,size=1,sx=1,sy=1,sz=1;
};
struct WalkPoint {double x=0,y=0,z=0;};
struct WalkSurface {
 std::uint64_t ownerId=0;
 bool bridge=false; // a simplified 4-corner WALKABLE deck, not railing collider
 bool approach=false; // tiny grade-checked bank-to-bridge transition
 WalkPoint a,b,c,d; // quad in physical XYZ metres
};
enum class WalkEdgeKind:unsigned char{ Road,Bridge,Approach };
struct WalkEdge {
 std::uint64_t from=0,to=0,objectId=0;
 WalkEdgeKind kind=WalkEdgeKind::Road;
 double length=0;
};
struct BridgeStatus {
 std::uint64_t id=0;
 int endsConnected=0;
 bool walkable=false; // stepping-stone fords are NOT continuous walkable decks
};
struct TraversalGraph {
 std::vector<WalkEdge> edges;
 std::vector<WalkSurface> surfaces;
 std::vector<BridgeStatus> bridges;
 std::size_t fullBridges=0,oneEndedBridges=0,detachedBridges=0;
 std::size_t roadSurfaces=0,bridgeSurfaces=0,approachSurfaces=0;
};

// A road approaches a bridge ONLY via a real road DEAD END close to the
// bridge's longitudinal mouth and at nearly the same walking height.
// One-sided and misaligned bridges remain disconnected.
// Does not mutate roads, bridges, scene, terrain or out on failure.
// Uses deterministic tie-breaking and bounded CPU/GPU-independent data.
bool buildTraversalGraph(const RoadTopology& roads,
                         const std::vector<BridgeInstance>& bridges,
                         const TerrainHeight& ground,TraversalGraph& out);

// Proposes ONE Y elevation for the selected existing bridge, aligning its
// deck with TWO road dead ends already near its mouths in XZ. This must be
// explicitly approved by the user/editor and committed through Undo.
// Never changes bridge X/Z/yaw/size or any authored street.
bool suggestBridgeDeckHeight(const RoadTopology& roads,
                             const BridgeInstance& bridge,
                             const TerrainHeight& ground,
                             double& suggestedEntityY);
struct BridgeAlignment {Point center{};double y=0,yaw=0;};
// A one-click authoring helper: align the selected bridge's position,
// orientation and deck elevation to two nearby APPROACHING street dead ends.
// Requires distinct ends, reasonable gap, grade and good post-alignment
// contacts. Does not resize, edit roads or mutate the source bridge.
bool suggestBridgeRoadAlignment(const RoadTopology& roads,
                                const BridgeInstance& bridge,
                                const TerrainHeight& ground,
                                BridgeAlignment& suggested);
}} // namespace oe::roads
