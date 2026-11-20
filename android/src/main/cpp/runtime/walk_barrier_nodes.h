#pragma once
// OE Stage11: linked editor nodes for native physical WalkBarrier segments.
// Junctions are inferred from saved v12 endpoints; no new scene format or
// separate physics world. Changes validate atomically before authoring commit.
#include "walk_barriers.h"

namespace oe { namespace runtime {
constexpr double kLinkedWalkNodeToleranceMetres=.05; // 5 cm
inline bool sameWalkNode(double x,double z,double nx,double nz,
                         double tolerance=kLinkedWalkNodeToleranceMetres) {
 return std::isfinite(x)&&std::isfinite(z)&&
        std::isfinite(nx)&&std::isfinite(nz)&&
        std::hypot(x-nx,z-nz)<=tolerance;
}
// Count existing segments containing this endpoint. Degree 2 means two
// connected wall segments, not a duplicated independent collision entity.
inline std::size_t walkNodeDegree(const std::vector<WalkBarrier>&walls,
                                 double x,double z) {
 std::size_t count=0;
 for(const WalkBarrier&w:walls)
  if(sameWalkNode(w.ax,w.az,x,z)||sameWalkNode(w.bx,w.bz,x,z))
   ++count;
 return count;
}
// desired specifies the selected barrier's new endpoints and own thickness.
// Every nearby old endpoint is snapped to the SAME new coordinate, including
// branches and T-junction endpoints. Other barriers keep their physical
// thickness/height/IDs. If ANY affected segment becomes too short, too long,
// or leaves the world, reject THE WHOLE edit without side effects.
// Return number of changed segments, 0 on rejection. Caller handles history.
inline int editLinkedWalkBarrier(std::vector<WalkBarrier>&walls,
                                std::uint64_t id,const WalkBarrier&desired,
                                double halfWorld) {
 if(!validWalkBarrier(desired,halfWorld)||desired.id!=id||
    walls.empty()||walls.size()>kMaxWalkBarriers)return 0;
 const WalkBarrier*selected=nullptr;
 for(const auto&w:walls)if(w.id==id){selected=&w;break;}
 if(!selected)return 0;
 const WalkBarrier previous=*selected;
 const bool moveA=!sameWalkNode(previous.ax,previous.az,
                            desired.ax,desired.az,1.e-9);
 const bool moveB=!sameWalkNode(previous.bx,previous.bz,
                            desired.bx,desired.bz,1.e-9);
 std::vector<WalkBarrier> staged=walls;
 int changedCount=0;
 for(WalkBarrier&wall:staged) {
  bool changed=false;
  auto shift=[&](double&x,double&z){
   // Compare to the OLD node positions, never partially edited values.
   const bool matchA=moveA&&sameWalkNode(
       x,z,previous.ax,previous.az);
   const bool matchB=moveB&&sameWalkNode(
       x,z,previous.bx,previous.bz);
   if(matchA){if(x!=desired.ax||z!=desired.az)changed=true;
      x=desired.ax;z=desired.az;}
   else if(matchB){if(x!=desired.bx||z!=desired.bz)changed=true;
      x=desired.bx;z=desired.bz;}
  };
  shift(wall.ax,wall.az);shift(wall.bx,wall.bz);
  if(wall.id==id) {
   if(wall.thickness!=desired.thickness||
      wall.height!=desired.height)changed=true;
   wall.thickness=desired.thickness;
   wall.height=desired.height;
  }
  if(changed) {
   if(!validWalkBarrier(wall,halfWorld))return 0;
   ++changedCount;
  }
 }
 // A no-op is a successful edit, but does not create undo history.
 if(changedCount>0)walls.swap(staged);
 return changedCount>0?changedCount:1;
}
}} // namespace oe::runtime
