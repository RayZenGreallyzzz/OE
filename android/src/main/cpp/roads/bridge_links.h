#pragma once
// OE R5.4: portable, bounded road ↔ bridge graph + collision-ready triangles.
// The editor scene remains authoritative. NO physics engine or GLES dependency.
#include "road_topology.h"
#include "../medieval/structures.h"
#include <cstddef>
#include <cstdint>
#include <vector>
namespace oe { namespace roads {
constexpr std::size_t kMaxBridges=128;
constexpr std::size_t kMaxRoadCollisionTriangles=48000;
constexpr std::size_t kMaxTotalCollisionTriangles=49000;
constexpr double kBridgeMaxHorizontalGap=2.0;
constexpr double kBridgeMaxVerticalGap=.75;
struct WorldPoint {double x=0,y=0,z=0;};
struct BridgeInstance {
 int entityId=0,type=0;
 double x=0,y=0,z=0,yawDegrees=0,uniformScale=1,sx=1,sy=1,sz=1;
};
struct BridgeDeck {
 int entityId=0,type=0;
 WorldPoint start,end;
 double width=0,length=0;
 bool supportsContinuousCrossing=false,footOnly=false;
};
struct BridgeNavLink {
 int bridgeId=0;
 std::uint64_t fromNode=0,toNode=0;
 std::uint64_t fromRoad=0,toRoad=0;
 double length=0,width=0;
 bool footOnly=false;
};
struct CollisionTriangle {
 WorldPoint a,b,c;
 int ownerId=0; // bridge: entity ID, road: 0
 std::uint64_t roadId=0; // 0 only for bridge decks/junction caps
};
struct BridgeNetwork {
 std::vector<BridgeDeck> decks; // for edit diagnostics and future runtime
 std::vector<BridgeNavLink> links; // dynamic; derived from current transforms
 std::vector<CollisionTriangle> collision; // proposed floor colliders, not active physics
 std::size_t unconnectedBridges=0; // incl. stepping-stone ford
 std::size_t roadCollisionTriangles=0;
 bool usedStitchedJunctions=false;
};
// Validates transforms, including stable entity IDs. Output unchanged on error.
bool describeBridge(const BridgeInstance& object,BridgeDeck& output) noexcept;
// Builds candidate path links and floor triangle data without mutating roads,
// editor entities, project scene format or render meshes.
bool buildBridgeNetwork(const RoadTopology& roadGraph,
                        const std::vector<BridgeInstance>& bridges,
                        const TerrainHeight& height,BridgeNetwork& output);
// Candidate snap for an EXPLICIT selected bridge. Returns its proposed
// X/Z, yaw and Y, without changing the editor; accepts only opposite road
// ENDPOINTS with compatible widths, slope and span. Caller's job to record Undo.
bool proposeBridgeRoadFit(const RoadTopology& graph,const BridgeInstance& current,
                          const TerrainHeight& height,BridgeInstance& fitted);
}} // namespace oe::roads
