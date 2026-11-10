#include "bridge_approach_visual.h"
#include <cmath>
#include <cstdint>
#include <set>
#include <utility>
#include <vector>

namespace oe { namespace roads {
namespace {
constexpr double kVisualLift=.016; // prevent z-fighting with bridge/terrain
bool good(const WalkPoint&p){
 return std::isfinite(p.x)&&std::isfinite(p.y)&&std::isfinite(p.z)&&
        std::abs(p.x)<8400.&&std::abs(p.z)<8400.&&std::abs(p.y)<10000.;
}
double area(const WalkPoint&a,const WalkPoint&b,const WalkPoint&c){
 return (b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);
}
Vertex makeVertex(const WalkPoint&p){
 // World-XZ texture projection uses the same material atlas as junction fills.
 return {static_cast<float>(p.x),static_cast<float>(p.y+kVisualLift),
         static_cast<float>(p.z),static_cast<float>(p.x),
         static_cast<float>(p.z)};
}
void triangle(SurfaceMesh&mesh,const WalkPoint&a,const WalkPoint&b,
              const WalkPoint&c){
 mesh.triangles.push_back(makeVertex(a));
 mesh.triangles.push_back(makeVertex(b));
 mesh.triangles.push_back(makeVertex(c));
}
} // namespace
bool buildBankVisualSeams(const TraversalGraph&graph,
                          std::vector<BankVisualPart>&out){
 if(graph.surfaces.size()>kMaxTraversalSurfaces||
    graph.bridges.size()>kMaxTraversalBridges)return false;
 std::set<std::uint64_t> full;
 for(const auto&b:graph.bridges)
  if(b.walkable&&b.endsConnected==2)full.insert(b.id);
 std::vector<BankVisualPart> draft;
 draft.reserve(graph.approachSurfaces);
 for(const auto&s:graph.surfaces){
  if(!s.approach||!s.bridge||!full.count(s.ownerId))continue;
  if(!s.ownerId||!s.materialRoadId||!good(s.a)||!good(s.b)||
     !good(s.c)||!good(s.d))return false;
  const double a=area(s.a,s.b,s.c),b=area(s.a,s.c,s.d);
  // Invalid/projected-to-zero surfaces are not silently presented as roads.
  if(!std::isfinite(a)||!std::isfinite(b)||
     std::abs(a)<=.001||std::abs(b)<=.001||a*b<=0.)return false;
  BankVisualPart part;part.materialRoadId=s.materialRoadId;
  part.mesh.triangles.reserve(6);
  // A positive XZ area yields a DOWN-facing 3D normal: reverse winding.
  if(a>0.){
   triangle(part.mesh,s.a,s.c,s.b);
   triangle(part.mesh,s.a,s.d,s.c);
  }else{
   triangle(part.mesh,s.a,s.b,s.c);
   triangle(part.mesh,s.a,s.c,s.d);
  }
  draft.push_back(std::move(part));
 }
 out=std::move(draft);
 return true;
}
}} // namespace oe::roads
