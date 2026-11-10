#pragma once
// OE R5.6 — portable 60 Hz character feet / landscape / road / bridge
// collision. Editor preview and future Game Runtime share the SAME solver.
#include "oe_runtime.h"
#include "../roads/traversal_graph.h"
#include <cstddef>
#include <cstdint>
#include <functional>
#include <vector>

namespace oe { namespace runtime {
constexpr double kWalkSpeedMetresPerSecond=3.5;
constexpr double kWalkGravity=-18.;
constexpr double kJumpSpeed=6.3;
constexpr double kWalkStepHeight=.65;
constexpr double kWalkMaxSlope=.85; // maximum terrain vertical / horizontal
constexpr std::size_t kWalkMaxSurfaces=roads::kMaxTraversalSurfaces;

enum class GroundKind:std::uint8_t {None=0,Terrain=1,Road=2,Bridge=3};
struct GroundSample {
 double height=0;
 GroundKind kind=GroundKind::None;
 std::uint64_t ownerId=0;
};
struct WalkState {
 Vec3 feet{};
 double verticalVelocity=0.;
 bool grounded=false;
 GroundKind groundKind=GroundKind::None;
 std::uint64_t groundId=0;
 std::uint64_t fixedTicks=0;
};
class WalkController final {
public:
 // Validates bounded walk data atomically. A bridge without BOTH true
 // road approaches is *not* registered as a walkable platform.
 bool configure(const roads::TraversalGraph& graph,
                roads::TerrainHeight terrain,double worldHalf=512.);
 // Place feet on the reachable ground/road in a bounded world. Cannot
 // teleport a test player from UNDER a bridge onto its roof.
 bool spawn(double x,double z);
 // Move a discrete 60 Hz simulation; controls are in world XZ axes.
 // Input vector is normalized; jump fires once on the first step.
 // High ledges, steep cliffs, and impossible platform steps block upward
 // movement. Falling down is allowed, and bridges do not inherit terrain Y.
 bool advance(double axisX,double axisZ,bool jump,std::size_t ticks=1);
 bool active() const noexcept{return active_;}
 const WalkState& state() const noexcept{return state_;}
 std::size_t collisionQuads() const noexcept{return surfaces_.size();}
 void clear() noexcept;
 // Safe adapter for OE's existing runtime ECS World: commits ONLY player
 // transform on success, never editor objects or authored scenery.
 bool applyToWorld(World&world,EntityId actor) const noexcept;
private:
 struct Floor {
  GroundSample hit{};
  double slope=0.;
 };
 bool query(double x,double z,double referenceFeet,
            double maxStepUp,Floor&floor) const;
 bool inBounds(double x,double z) const noexcept;
 roads::TerrainHeight terrain_{};
 std::vector<roads::WalkSurface> surfaces_;
 double worldHalf_=512.;
 WalkState state_{};
 bool active_=false;
};
}} // namespace oe::runtime
