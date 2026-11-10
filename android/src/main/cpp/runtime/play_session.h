#pragma once
// OE R5.8b: isolated, testable C++17 game preview session.
// Owns its own ECS scene and physically simulated player; never references
// the mutable editor scene, GPU buffers, undo stack, or Android lifecycle.
#include "oe_runtime.h"
#include "walk_collision.h"
#include "solid_collision.h"
#include "../roads/traversal_graph.h"
#include <cstddef>
#include <vector>

namespace oe { namespace runtime {
class PlaySession final {
 public:
  explicit PlaySession(std::size_t capacity=512):world_(capacity){}
  PlaySession(const PlaySession&)=delete;
  PlaySession& operator=(const PlaySession&)=delete;
  bool start(const std::vector<SceneItem>&scene,
             const roads::TraversalGraph&walkSurfaces,
             roads::TerrainHeight terrain,double worldHalf,
             double spawnX,double spawnZ,double yaw,
             const std::vector<SolidBox>&staticSolids={});
  bool step(double axisX,double axisZ,bool jump,std::size_t fixedTicks=2);
  void orbit(double dx,double dy) noexcept;
  void stop() noexcept;
  bool active() const noexcept{return active_;}
  const World& world() const noexcept{return world_;}
  const WalkState& player() const noexcept{return walker_.state();}
  EntityId playerId() const noexcept{return playerId_;}
  double yaw() const noexcept{return cameraYaw_;}
  double pitch() const noexcept{return cameraPitch_;}
 private:
  World world_;
  WalkController walker_;
  EntityId playerId_{};
  bool active_=false;
  double cameraYaw_=0.;
  double cameraPitch_=.24;
};
}} // namespace oe::runtime
