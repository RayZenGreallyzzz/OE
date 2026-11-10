#include "walk_collision.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <set>
#include <utility>
#include <vector>

namespace oe { namespace runtime {
namespace {
constexpr double kDt=kFixedTimeStep;
constexpr double kGroundMargin=.01;
constexpr double kMaxHeight=10000.;
constexpr double kFloorTolerance=.002;
bool finite(double x){return std::isfinite(x);}
bool finitePoint(const roads::WalkPoint&p){
 return finite(p.x)&&finite(p.y)&&finite(p.z)&&
        std::abs(p.x)<8400.&&std::abs(p.y)<kMaxHeight&&
        std::abs(p.z)<8400.;
}
double planarArea(const roads::WalkPoint&a,const roads::WalkPoint&b,
                  const roads::WalkPoint&c){
 return (b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);
}
// Triangle barycentric in XZ, accepted on both sides of winding.
bool triangleHeight(double x,double z,const roads::WalkPoint&a,
                    const roads::WalkPoint&b,const roads::WalkPoint&c,
                    double&y,double&slope){
 const double cross=planarArea(a,b,c);
 if(!finite(cross)||std::abs(cross)<1.e-6)return false;
 const double u=((b.x-x)*(c.z-z)-(b.z-z)*(c.x-x))/cross;
 const double v=((c.x-x)*(a.z-z)-(c.z-z)*(a.x-x))/cross;
 const double w=1.-u-v;
 if(u< -kFloorTolerance||v< -kFloorTolerance||w< -kFloorTolerance)return false;
 y=u*a.y+v*b.y+w*c.y;
 const double dx1=b.x-a.x,dz1=b.z-a.z;
 const double dx2=c.x-a.x,dz2=c.z-a.z;
 const double dh1=b.y-a.y,dh2=c.y-a.y;
 const double gradientX=(dh1*dz2-dh2*dz1)/cross;
 const double gradientZ=(dx1*dh2-dx2*dh1)/cross;
 slope=std::hypot(gradientX,gradientZ);
 return finite(y)&&finite(slope);
}
bool quadHeight(double x,double z,const roads::WalkSurface&q,
                double&y,double&slope){
 // Shared diagonal across road/bridge quad. Do not interpolate across
 // the wrong side: that would make an invisible bridge outside its deck.
 return triangleHeight(x,z,q.a,q.b,q.c,y,slope)||
        triangleHeight(x,z,q.a,q.c,q.d,y,slope);
}
bool validSurface(const roads::WalkSurface&s){
 if(!s.ownerId||!finitePoint(s.a)||!finitePoint(s.b)||
    !finitePoint(s.c)||!finitePoint(s.d))return false;
 const double a=planarArea(s.a,s.b,s.c);
 const double b=planarArea(s.a,s.c,s.d);
 return finite(a)&&finite(b)&&std::abs(a)>.001&&std::abs(b)>.001&&
        a*b>0.;
}
bool validTerrain(roads::TerrainHeight&terrain,double x,double z,double&h){
 if(!terrain)return false;
 h=terrain(x,z);
 return finite(h)&&std::abs(h)<kMaxHeight;
}
} // namespace
bool WalkController::inBounds(double x,double z) const noexcept{
 return finite(x)&&finite(z)&&std::abs(x)<=worldHalf_-1.0&&
        std::abs(z)<=worldHalf_-1.0;
}
bool WalkController::configure(const roads::TraversalGraph&g,
                              roads::TerrainHeight terrain,double worldHalf){
 if(!terrain||!finite(worldHalf)||worldHalf<32.||worldHalf>8192.||
    g.surfaces.size()>kWalkMaxSurfaces||g.bridges.size()>roads::kMaxTraversalBridges)
  return false;
 double sample=0.;
 if(!validTerrain(terrain,0.,0.,sample))return false;
 std::set<std::uint64_t> connected;
 for(const auto&b:g.bridges)
  if(b.walkable&&b.endsConnected==2)connected.insert(b.id);
 std::vector<roads::WalkSurface> next;
 next.reserve(g.surfaces.size());
 for(const auto&s:g.surfaces){
  if(!validSurface(s))return false;
  if(s.bridge&&!connected.count(s.ownerId))continue;
  next.push_back(s);
 }
 // All potentially failing work above finishes before any old session
 // can be replaced. The previous session survives a malformed graph.
 terrain_=std::move(terrain);surfaces_=std::move(next);
 worldHalf_=worldHalf;state_={};active_=false;
 return true;
}
bool WalkController::query(double x,double z,double referenceFeet,
                           double maxStepUp,Floor&floor) const{
 if(!inBounds(x,z)||!terrain_||!finite(referenceFeet))return false;
 double terrainY=0.;
 // Terrain geometry is always present even when a custom road exists.
 // A lake is VISUAL ONLY here; no false buoyancy/contact at water level.
 if(!validTerrain(const_cast<roads::TerrainHeight&>(terrain_),x,z,terrainY))
  return false;
 const auto height=[&](double px,double pz,double&h)->bool{
  return validTerrain(const_cast<roads::TerrainHeight&>(terrain_),px,pz,h);
 };
 const double d=.25;
 double right=0,left=0,forward=0,back=0;
 if(!height(x+d,z,right)||!height(x-d,z,left)||
    !height(x,z+d,forward)||!height(x,z-d,back))return false;
 const double slope=std::hypot((right-left)/(2*d),(forward-back)/(2*d));
 Floor best{{terrainY,GroundKind::Terrain,0},slope};
 // Do not teleport the player from underneath a bridge onto its floor:
 // only a support at or below the current walkable step ceiling is eligible.
 // But preserve the highest VALID walk surface (road above terrain).
 for(const auto&s:surfaces_){
  double y=0,grade=0;
  if(!quadHeight(x,z,s,y,grade))continue;
  if(y>referenceFeet+maxStepUp+kGroundMargin)continue;
  if(grade>kWalkMaxSlope)continue;
  if(y>best.hit.height+.0001){
   best={{y,s.bridge?GroundKind::Bridge:GroundKind::Road,s.ownerId},grade};
  }
 }
 floor=best;
 return true;
}
bool WalkController::spawn(double x,double z){
 if(!terrain_||!inBounds(x,z))return false;
 double h=0;
 if(!validTerrain(terrain_,x,z,h))return false;
 Floor sample{};
 if(!query(x,z,h,kWalkStepHeight,sample))return false;
 if(sample.slope>kWalkMaxSlope)return false;
 state_={};
 state_.feet={x,sample.hit.height,z};
 state_.grounded=true;state_.groundKind=sample.hit.kind;
 state_.groundId=sample.hit.ownerId;active_=true;
 return true;
}
bool WalkController::advance(double dx,double dz,bool jump,std::size_t ticks){
 if(!active_||!finite(dx)||!finite(dz)||std::abs(dx)>1.||std::abs(dz)>1.||
    ticks==0||ticks>180)return false;
 // Simulate on a local copy: an unexpectedly invalid terrain sample cannot
 // partially move the player/editor ghost. No unbounded catch-up.
 WalkState next=state_;
 const double speed=std::hypot(dx,dz);
 if(speed>1.){dx/=speed;dz/=speed;}
 for(std::size_t tick=0;tick<ticks;++tick){
  if(tick==0&&jump&&next.grounded){
   next.verticalVelocity=kJumpSpeed;
   next.grounded=false;next.groundKind=GroundKind::None;next.groundId=0;
  }
  const double desiredX=next.feet.x+dx*kWalkSpeedMetresPerSecond*kDt;
  const double desiredZ=next.feet.z+dz*kWalkSpeedMetresPerSecond*kDt;
  if(!inBounds(desiredX,desiredZ))return false;
  Floor target{};
  if(!query(desiredX,desiredZ,next.feet.y,kWalkStepHeight,target))
   return false;
  const double elevation=target.hit.height-next.feet.y;
  const bool steep=target.slope>kWalkMaxSlope;
  const bool blocked=steep||
      (next.grounded&&elevation>kWalkStepHeight);
  if(!blocked){
   next.feet.x=desiredX;next.feet.z=desiredZ;
  }else{
   // Movement is blocked by steep mountain sides/vertical steps, but
   // gravity still acts on the actor while airborne.
   if(!query(next.feet.x,next.feet.z,next.feet.y,kWalkStepHeight,target))
    return false;
  }
  if(next.grounded){
   const double below=target.hit.height-next.feet.y;
   if(!blocked&&below>=-kWalkStepHeight&&below<=kWalkStepHeight){
    next.feet.y=target.hit.height;
    next.verticalVelocity=0.;
    next.groundKind=target.hit.kind;next.groundId=target.hit.ownerId;
   }else if(below< -kWalkStepHeight){
    next.grounded=false;
    next.groundKind=GroundKind::None;next.groundId=0;
   }
  }
  if(!next.grounded){
   const double previousY=next.feet.y;
   next.verticalVelocity+=kWalkGravity*kDt;
   const double proposedY=previousY+next.verticalVelocity*kDt;
   if(next.verticalVelocity<=0.&&
      previousY>=target.hit.height-kGroundMargin&&
      proposedY<=target.hit.height+kGroundMargin&&!steep){
    next.feet.y=target.hit.height;next.verticalVelocity=0.;
    next.grounded=true;next.groundKind=target.hit.kind;
    next.groundId=target.hit.ownerId;
   }else{
    next.feet.y=proposedY;
   }
  }
  if(!finite(next.feet.y)||std::abs(next.feet.y)>kMaxHeight)return false;
  ++next.fixedTicks;
 }
 state_=next;
 return true;
}
void WalkController::clear() noexcept{
 terrain_={};surfaces_.clear();state_={};active_=false;
}
bool WalkController::applyToWorld(World&world,EntityId actor) const noexcept{
 if(!active_||!world.alive(actor))return false;
 const Transform*source=world.transform(actor);
 if(!source)return false;
 Transform t=*source;t.position=state_.feet;
 return world.setTransform(actor,t);
}
}} // namespace oe::runtime
