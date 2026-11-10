#include "../android/src/main/cpp/roads/bridge_approach_visual.h"
#include "../android/src/main/cpp/roads/traversal_graph.h"
#include <cassert>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <vector>
using namespace oe::roads;
static Road road(std::uint64_t id,std::initializer_list<Point> points){
 return {id,"Historic road","builtin:cobblestone",3.2,
         std::vector<Point>(points)};
}
static double upNormalY(const Vertex&a,const Vertex&b,const Vertex&c){
 return (c.x-a.x)*(b.z-a.z)-(b.x-a.x)*(c.z-a.z);
}
int main(){
 RoadTopology topo;
 assert(buildRoadTopology({road(5,{{-16,0},{-5.25,0}}),
                           road(6,{{5.25,0},{16,0}})},topo,0.));
 BridgeInstance bridge;bridge.id=99;
 bridge.type=oe::medieval::kStoneBridge;bridge.y=-1.70;
 const TerrainHeight river=[](double x,double){
  return std::abs(x)<5.0?-4.:0.;
 };
 TraversalGraph graph;
 assert(buildTraversalGraph(topo,{bridge},river,graph));
 assert(graph.fullBridges==1&&graph.approachSurfaces==2);
 std::vector<BankVisualPart> visual;
 assert(buildBankVisualSeams(graph,visual));
 assert(visual.size()==2);
 for(const auto&part:visual){
  assert(part.materialRoadId==5||part.materialRoadId==6);
  assert(part.mesh.triangles.size()==6); // two triangles, bounded
  const auto&v=part.mesh.triangles;
  assert(upNormalY(v[0],v[1],v[2])>0.);
  assert(upNormalY(v[3],v[4],v[5])>0.);
  for(const auto&p:v){
   assert(std::abs(p.y-.156f)<.001f);
   assert(p.u==p.x&&p.v==p.z); // world planar stone mosaic
  }
 }
 // No fake walkable visible ramp if either road bank is detached.
 RoadTopology partial;
 assert(buildRoadTopology({road(5,{{-16,0},{-5.25,0}})},partial,0.));
 assert(buildTraversalGraph(partial,{bridge},river,graph));
 assert(buildBankVisualSeams(graph,visual)&&visual.empty());
 // If unexpected malformed approach data appears, keep prior mesh atomically.
 assert(buildTraversalGraph(topo,{bridge},river,graph));
 assert(buildBankVisualSeams(graph,visual));
 const auto old=visual;
 for(auto&surface:graph.surfaces)if(surface.approach){
  surface.c=surface.b;break;
 }
 assert(!buildBankVisualSeams(graph,visual));
 assert(visual.size()==old.size()&&visual[0].materialRoadId==old[0].materialRoadId);
 graph.approachSurfaces=graph.surfaces.size()+1;
 assert(!buildBankVisualSeams(graph,visual));
 assert(visual.size()==old.size());
 std::cout<<"OE_BANK_VISUAL_OK: 2 seam meshes, positive winding, road textures, no half bridge\n";
}
