#include "play_session.h"
#include <algorithm>
#include <cmath>
#include <utility>

namespace oe { namespace runtime {
bool PlaySession::start(const std::vector<SceneItem>&scene,
                        const roads::TraversalGraph&walkSurfaces,
                        roads::TerrainHeight terrain,double worldHalf,
                        double spawnX,double spawnZ,double yaw,
                        const std::vector<SolidBox>&staticSolids){
 if(active_||!std::isfinite(yaw)||!std::isfinite(spawnX)||
    !std::isfinite(spawnZ)||scene.size()+1>world_.capacity())return false;
 if(!walker_.configure(walkSurfaces,std::move(terrain),worldHalf)||
    !walker_.setStaticSolids(staticSolids)||!walker_.spawn(spawnX,spawnZ)){
  walker_.clear();return false;
 }
 if(!world_.loadStaticScene(scene)){walker_.clear();return false;}
 const EntityId actor=world_.create("OE Play character");
 if(!world_.alive(actor)||!world_.setCamera(actor,Camera{})||
    !walker_.applyToWorld(world_,actor)){
  world_.clear();walker_.clear();return false;
 }
 playerId_=actor;
 cameraYaw_=yaw;
 cameraPitch_=.24;
 active_=true;
 return true;
}
bool PlaySession::step(double axisX,double axisZ,bool jump,std::size_t fixedTicks){
 if(!active_||fixedTicks==0||fixedTicks>8||
    !std::isfinite(axisX)||!std::isfinite(axisZ))return false;
 // The analog pad is CAMERA RELATIVE. Up on the pad means forward.
 const double x=axisX*std::cos(cameraYaw_)+axisZ*std::sin(cameraYaw_);
 const double z=-axisX*std::sin(cameraYaw_)+axisZ*std::cos(cameraYaw_);
 if(!walker_.advance(x,z,jump,fixedTicks))return false;
 if(!world_.stepTicks(fixedTicks))return false;
 return walker_.applyToWorld(world_,playerId_);
}
void PlaySession::orbit(double dx,double dy) noexcept{
 if(!active_||!std::isfinite(dx)||!std::isfinite(dy))return;
 cameraYaw_=std::remainder(cameraYaw_+std::clamp(dx,-.3,.3),6.283185307179586);
 cameraPitch_=std::clamp(cameraPitch_+std::clamp(dy,-.2,.2),-.12,.93);
}
void PlaySession::stop() noexcept{
 active_=false;
 walker_.clear();
 world_.clear();
 playerId_={};
 cameraYaw_=0.;
 cameraPitch_=.24;
}
}} // namespace oe::runtime
