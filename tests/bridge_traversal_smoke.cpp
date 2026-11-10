#include "../android/src/main/cpp/roads/traversal_graph.h"
#include <cassert>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <vector>
using namespace oe::roads;
static Road lane(std::uint64_t id,std::initializer_list<Point> route){
 return {id,"Dirt lane","builtin:dirt",4.,std::vector<Point>(route)};
}
static BridgeInstance bridge(std::uint64_t id,int kind=oe::medieval::kStoneBridge){
 BridgeInstance out;
 out.id=id;out.type=kind;
 return out;
}
int main(){
 const TerrainHeight flat=[](double,double){return 0.;};
 RoadTopology roads;
 assert(buildRoadTopology({
  lane(1,{{-18,0},{-4.05,0}}),lane(2,{{4.05,0},{18,0}})
 },roads,0.));
 assert(roads.edges.size()==2);
 BridgeInstance stone=bridge(100);
 TraversalGraph graph;
 assert(buildTraversalGraph(roads,{stone},flat,graph));
 assert(graph.bridges.size()==1&&graph.bridges[0].walkable);
 assert(graph.bridges[0].endsConnected==0); // not level with banks yet
 assert(graph.fullBridges==0&&graph.detachedBridges==1);
 assert(graph.roadSurfaces==2&&graph.bridgeSurfaces==1);
 assert(graph.surfaces.size()==3);
 assert(graph.edges.size()==3); // 2 real roads + isolated bridge deck
 assert(graph.surfaces.back().bridge&&graph.surfaces.back().ownerId==100);
 // The editor can propose all four bridge pose fields (XZ, yaw, Y) after
 // a rough touch placement; it must never silently move an authored street.
 BridgeInstance coarse=stone;
 coarse.center={.7,.4};coarse.y=.2;coarse.yaw=.03;
 BridgeAlignment alignment{};
 assert(suggestBridgeRoadAlignment(roads,coarse,flat,alignment));
 assert(std::abs(alignment.center.x)<.001);
 assert(std::abs(alignment.center.z)<.001);
 assert(std::abs(alignment.yaw)<.001);
 assert(std::abs(alignment.y+1.70)<.001);
 BridgeInstance fitted=coarse;
 fitted.center=alignment.center;fitted.yaw=alignment.yaw;fitted.y=alignment.y;
 assert(buildTraversalGraph(roads,{fitted},flat,graph));
 assert(graph.fullBridges==1&&graph.bridges[0].endsConnected==2);
 // A bridge too far from the mouths must not relocate itself unexpectedly.
 BridgeInstance remote=stone;remote.center={100,100};
 const BridgeAlignment remembered=alignment;
 assert(!suggestBridgeRoadAlignment(roads,remote,flat,alignment));
 assert(std::abs(alignment.center.x-remembered.center.x)<1.e-12);
 assert(std::abs(alignment.y-remembered.y)<1.e-12);
 double suggested=999.;
 assert(suggestBridgeDeckHeight(roads,stone,flat,suggested));
 assert(std::abs(suggested-(-1.70))<.001);
 stone.y=suggested;
 assert(buildTraversalGraph(roads,{stone},flat,graph));
 assert(graph.fullBridges==1&&graph.oneEndedBridges==0);
 assert(graph.bridges[0].endsConnected==2);
 assert(graph.edges.size()==5); // two street segments + two entrances + bridge
 int approaches=0,deck=0;
 for(const auto&e:graph.edges){
  if(e.kind==WalkEdgeKind::Approach){++approaches;assert(e.length<.01);}
  if(e.kind==WalkEdgeKind::Bridge){++deck;assert(std::abs(e.length-8.1)<.01);}
 }
 assert(approaches==2&&deck==1);
 const auto before=graph;
 // One missing street gives one disconnected mouth, never a complete crossing.
 RoadTopology half;
 assert(buildRoadTopology({lane(1,{{-18,0},{-4.05,0}})},half,0.));
 assert(buildTraversalGraph(half,{stone},flat,graph));
 assert(graph.fullBridges==0&&graph.oneEndedBridges==1);
 assert(graph.bridges[0].endsConnected==1);
 // Non-aligned dead end (touching the bridge from the side) is not traversable.
 RoadTopology side;
 assert(buildRoadTopology({lane(8,{{-4.05,-18},{-4.05,0}}),
                           lane(9,{{4.05,0},{4.05,18}})},side,0.));
 assert(buildTraversalGraph(side,{stone},flat,graph));
 assert(graph.bridges[0].endsConnected==0);
 // Rotation swaps the bridge mouths; the road positions no longer match.
 BridgeInstance rotated=stone;rotated.yaw=3.14159265358979323846/2.;
 assert(buildTraversalGraph(roads,{rotated},flat,graph));
 assert(graph.bridges[0].endsConnected==0);
 // Vertical gap is not magically connected; a steep ramp needs terrain work.
 const TerrainHeight stepped=[](double x,double){return x>0?1.8:0.;};
 assert(buildTraversalGraph(roads,{stone},stepped,graph));
 assert(graph.bridges[0].endsConnected==1);
 suggested=456.;
 assert(!suggestBridgeDeckHeight(roads,stone,stepped,suggested));
 assert(suggested==456.);
 // Unsafe isolated stepping stones must not pretend to be a continuous slab.
 BridgeInstance ford=bridge(400,oe::medieval::kSteppingStoneFord);
 assert(buildTraversalGraph(roads,{ford},flat,graph));
 assert(graph.bridges[0].endsConnected==0);
 assert(!graph.bridges[0].walkable);
 assert(graph.bridgeSurfaces==0);
 // Six other supported bridge variants each provide one collision-ready quad.
 for(int kind:{oe::medieval::kTimberBridge,oe::medieval::kNarrowFootbridge,
               oe::medieval::kRopeBridge,oe::medieval::kWideCartBridge,
               oe::medieval::kSwampCauseway}){
  BridgeInstance b=bridge(500+kind,kind);
  assert(buildTraversalGraph(roads,{b},flat,graph));
  assert(graph.bridgeSurfaces==1&&graph.bridges[0].walkable);
 }
 // Result is always atomic on any invalid input.
 graph=before;
 BridgeInstance bad=stone;bad.yaw=std::numeric_limits<double>::infinity();
 assert(!buildTraversalGraph(roads,{bad},flat,graph));
 assert(graph.edges.size()==before.edges.size());
 assert(graph.surfaces.size()==before.surfaces.size());
 assert(!buildTraversalGraph(roads,{stone,stone},flat,graph)); // duplicate ID
 assert(graph.edges.size()==before.edges.size());
 std::vector<BridgeInstance> tooMany(121,stone);
 for(std::size_t i=0;i<tooMany.size();++i)tooMany[i].id=i+1;
 assert(!buildTraversalGraph(roads,tooMany,flat,graph));
 assert(graph.edges.size()==before.edges.size());
 assert(!buildTraversalGraph(roads,{stone},[](double,double){
  return std::numeric_limits<double>::quiet_NaN();},graph));
 assert(graph.edges.size()==before.edges.size());
 std::cout<<"OE_TRAVERSAL_GRAPH_OK: 2-sided bridges, road-dead-end direction, "
             "surface quads, grade safety, atomic failure\n";
}
