#include "../android/src/main/cpp/runtime/walk_collision.h"
#include "../android/src/main/cpp/roads/traversal_graph.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <vector>
using namespace oe::roads;
using namespace oe::runtime;
static Road road(std::uint64_t id,std::initializer_list<Point> points){
 return {id,"Village road","builtin:cobblestone",3.2,std::vector<Point>(points)};
}
int main(){
 // 1.2-metre gap between each authored road cap and a historic bridge.
 // Terrain falls away below the (imaginary) short bank-to-deck ramps.
 const TerrainHeight river=[](double x,double){
  return std::abs(x)<5.0?-4.0:0.0;
 };
 RoadTopology topo;
 assert(buildRoadTopology({
  road(1,{{-16,0},{-5.25,0}}),
  road(2,{{5.25,0},{16,0}})
 },topo,0.));
 BridgeInstance bridge;bridge.id=17;bridge.type=oe::medieval::kStoneBridge;
 bridge.y=-1.70; // stone deck Y = 0.14, same height as both roads
 TraversalGraph graph;
 assert(buildTraversalGraph(topo,{bridge},river,graph));
 assert(graph.fullBridges==1);
 assert(graph.approachSurfaces==2);
 assert(graph.roadSurfaces==2&&graph.bridgeSurfaces==1);
 assert(graph.surfaces.size()==5);
 int ramps=0;
 for(const auto&s:graph.surfaces){
  if(!s.approach)continue;
  assert(s.bridge&&s.ownerId==17);
  ++ramps;
  assert(std::abs(s.a.y-.14)<.001);
  assert(std::abs(s.c.y-.14)<.001);
 }
 assert(ramps==2);
 WalkController walker;
 assert(walker.configure(graph,river,512));
 assert(walker.collisionQuads()==5);
 assert(walker.spawn(-6.5,0));
 assert(walker.state().grounded);
 // Travel along both collision transitions without falling through the
 // hollow riverbed. Each advance is a deterministic 60Hz batch.
 assert(walker.advance(1,0,false,155));
 assert(walker.state().feet.x>2.);
 assert(walker.state().grounded);
 assert(std::abs(walker.state().feet.y-.14)<.01);
 assert(walker.state().groundKind==GroundKind::Bridge);
 assert(walker.advance(1,0,false,80));
 assert(walker.state().feet.x>6.);
 assert(walker.state().grounded);
 assert(std::abs(walker.state().feet.y-.14)<.01);
 assert(walker.state().groundKind==GroundKind::Road);
 // A one-sided approach is not a traversable bridge or fake half-ramp.
 RoadTopology half;
 assert(buildRoadTopology({road(1,{{-16,0},{-5.25,0}})},half,0.));
 assert(buildTraversalGraph(half,{bridge},river,graph));
 assert(graph.oneEndedBridges==1&&graph.fullBridges==0);
 assert(graph.approachSurfaces==0);
 assert(walker.configure(graph,river,512));
 assert(walker.collisionQuads()==1); // road only, floating bridge excluded
 // A short 0.1 m gap with 0.4 m vertical change is too steep for
 // an interpolated ramp: don't report two safe entrances.
 RoadTopology shortGap;
 assert(buildRoadTopology({road(1,{{-16,0},{-4.15,0}}),
                           road(2,{{4.15,0},{16,0}})},shortGap,0.));
 BridgeInstance steep=bridge;steep.y=-1.30; // deck .54 vs bank .14
 assert(buildTraversalGraph(shortGap,{steep},river,graph));
 assert(graph.fullBridges==0&&graph.approachSurfaces==0);
 const TerrainHeight flat=[](double,double){return 0.;};
 // A sideways road cap directly beside the bridge mouth is NOT a ramp:
 // old R5.7 declared a full bridge but generated degenerate XZ triangles.
 RoadTopology sideways;
 assert(buildRoadTopology({road(1,{{-16,1.6},{-4.05,1.6}}),
                           road(2,{{5.25,0},{16,0}})},sideways,0.));
 assert(buildTraversalGraph(sideways,{bridge},flat,graph));
 assert(graph.fullBridges==0&&graph.oneEndedBridges==1);
 assert(graph.approachSurfaces==0);
 assert(walker.configure(graph,flat,512)); // previously failed to configure

 // A 0.6 m rise over 1.46 m diagonally seems safe, but only 0.4 m
 // lies along the bridge axis. Its actual triangle grade is 1.5: reject.
 const TerrainHeight highBank=[](double x,double){
  return x< -4.20?.6:0.;
 };
 RoadTopology slanted;
 assert(buildRoadTopology({road(1,{{-16,1.4},{-4.45,1.4}}),
                           road(2,{{5.25,0},{16,0}})},slanted,0.));
 assert(buildTraversalGraph(slanted,{bridge},highBank,graph));
 assert(graph.fullBridges==0&&graph.oneEndedBridges==1);
 assert(graph.approachSurfaces==0);
 assert(walker.configure(graph,highBank,512));

 // A normally angled road is still eligible. The approach's bank edge
 // follows the REAL road terminal edge, not the bridge's perpendicular.
 RoadTopology angled;
 assert(buildRoadTopology({road(1,{{-7,1},{-5.25,0}}),
                           road(2,{{5.25,0},{16,0}})},angled,0.));
 assert(buildTraversalGraph(angled,{bridge},flat,graph));
 assert(graph.fullBridges==1&&graph.approachSurfaces==2);
 assert(walker.configure(graph,flat,512));

 // A forced bad height source cannot corrupt the previous graph.
 const auto previous=graph;
 assert(!buildTraversalGraph(topo,{bridge},
  [](double,double){return std::numeric_limits<double>::quiet_NaN();},graph));
 assert(graph.surfaces.size()==previous.surfaces.size());
 assert(graph.approachSurfaces==previous.approachSurfaces);
 std::cout<<"OE_BANK_RAMPS_OK: 1.2m bank gaps safely bridged, real walk, no half-bridge, sideways cap or steep collision ramps\n";
}
