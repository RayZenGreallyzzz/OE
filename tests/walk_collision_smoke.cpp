// R5.6 real 60Hz walking / standing / falling collision against road and bridge quads.
#include "../android/src/main/cpp/runtime/walk_collision.h"
#include <cassert>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <vector>

using namespace oe::runtime;
using namespace oe::roads;
static bool near(double x,double y,double tolerance=.002){
 return std::abs(x-y)<tolerance;
}
static Road street(std::uint64_t id,std::vector<Point> pts){
 return {id,"Street","builtin:dirt",3.0,std::move(pts)};
}
int main(){
 const TerrainHeight valley=[](double x,double){
  return std::abs(x)<3.7?-4.:0.;
 };
 RoadTopology roads;
 assert(buildRoadTopology({
  street(1,{{-20,0},{-4.05,0}}),
  street(2,{{4.05,0},{20,0}})
 },roads,0.));
 BridgeInstance bridge;
 bridge.id=17;bridge.type=oe::medieval::kStoneBridge;
 bridge.y=-1.70;
 TraversalGraph graph;
 assert(buildTraversalGraph(roads,{bridge},valley,graph));
 assert(graph.fullBridges==1&&graph.roadSurfaces==2&&graph.bridgeSurfaces==1);
 WalkController walker;
 assert(walker.configure(graph,valley,512.));
 assert(walker.collisionQuads()==3);
 assert(walker.spawn(-5.,0.));
 assert(walker.active()&&walker.state().grounded);
 assert(walker.state().groundKind==GroundKind::Road);
 assert(near(walker.state().feet.y,.14));
 assert(walker.advance(1.,0.,false,160));
 assert(walker.state().fixedTicks==160&&walker.state().grounded);
 assert(walker.state().feet.x>4.0);
 assert(near(walker.state().feet.y,.14));
 assert(walker.state().groundKind==GroundKind::Road);
 // Even on a connected bridge, do not teleport a walker starting in
 // the river below its walkway onto a floating deck several metres up.
 assert(walker.spawn(0.,0.));
 assert(walker.state().groundKind==GroundKind::Terrain);
 assert(near(walker.state().feet.y,-4.));
 assert(walker.spawn(-2.0,0.));
 // At this site we spawn under the bridge: must be riverbed, not bridge.
 assert(walker.state().groundKind==GroundKind::Terrain);
 // The bridge crossing is detected as an actual walking platform.
 assert(walker.spawn(-5.,0.));
 assert(walker.advance(1.,0.,false,70));
 assert(walker.state().groundKind==GroundKind::Bridge);
 assert(walker.state().grounded&&near(walker.state().feet.y,.14));
 // Jump is a deterministic ballistic motion; after enough fixed ticks
 // the character lands back on the same physical road/bridge surface.
 assert(walker.advance(0.,0.,true,1));
 assert(!walker.state().grounded);
 const double inAir=walker.state().feet.y;
 assert(inAir>.14);
 assert(walker.advance(0.,0.,false,90));
 assert(walker.state().grounded&&near(walker.state().feet.y,.14));
 assert(walker.state().verticalVelocity==0.);
 assert(walker.state().fixedTicks==161);
 // Existing ECS World can accept the solved foot position on a runtime
 // entity WITHOUT adding renderer/physics dependencies to the World core.
 World world(2);
 const EntityId actor=world.create("walker");
 assert(world.alive(actor));
 assert(walker.applyToWorld(world,actor));
 assert(near(world.transform(actor)->position.x,walker.state().feet.x));
 assert(near(world.transform(actor)->position.y,walker.state().feet.y));
 // An unconnected bridge must be excluded, even though R5.4 still
 // exposes deck mesh for future editing collision.
 TraversalGraph noRoad;
 assert(buildTraversalGraph(RoadTopology{},{bridge},valley,noRoad));
 assert(noRoad.bridgeSurfaces==1&&noRoad.fullBridges==0);
 assert(walker.configure(noRoad,valley,512.));
 assert(walker.collisionQuads()==0);
 assert(walker.spawn(-5.,0.));
 assert(walker.advance(1.,0.,false,160));
 assert(walker.state().feet.y< -3.9||!walker.state().grounded);
 // A 2.5-metre wall can't be auto-climbed. No "invisible ramp".
 const TerrainHeight wall=[](double x,double){return x>=0.?2.5:0.;};
 assert(walker.configure(TraversalGraph{},wall,512.));
 assert(walker.spawn(-1.0,0.));
 assert(walker.advance(1.,0.,false,120));
 assert(walker.state().feet.x<0.0&&near(walker.state().feet.y,0.));
 // Falling from cliffs is permitted even though climbing their near-
 // vertical slope is blocked. After enough ticks player reaches lower land.
 const TerrainHeight cliff=[](double x,double){return x>=0.?-4.:0.;};
 assert(walker.configure(TraversalGraph{},cliff,512.));
 assert(walker.spawn(-1.,0.));
 assert(walker.advance(1.,0.,false,60));
 assert(walker.state().feet.x>1.&&walker.state().feet.y<0.);
 assert(walker.advance(0.,0.,false,120));
 assert(walker.state().grounded&&near(walker.state().feet.y,-4.));
 // Strong slope is blocked, not treated as flat.
 const TerrainHeight incline=[](double x,double){return 5.*x;};
 assert(walker.configure(TraversalGraph{},incline,512.));
 assert(!walker.spawn(0.,0.)); // slope 5 is beyond the 0.85 safety limit
 // All modifications are atomic on invalid parameters; prior session
 // state remains intact after rejected moves/graphs/world bounds.
 assert(walker.configure(graph,valley,512.));
 assert(walker.spawn(-5.,0.));
 const WalkState saved=walker.state();
 assert(!walker.advance(2.,0.,false,60));
 assert(!walker.advance(std::numeric_limits<double>::quiet_NaN(),0.,false,1));
 assert(!walker.advance(0.,0.,false,181));
 assert(walker.state().fixedTicks==saved.fixedTicks);
 assert(near(walker.state().feet.x,saved.feet.x));
 TraversalGraph bad=graph;
 bad.surfaces[0].a.y=std::numeric_limits<double>::quiet_NaN();
 assert(!walker.configure(bad,valley,512.));
 assert(walker.active()&&walker.collisionQuads()==3);
 assert(walker.advance(0.,0.,false,1));
 assert(!walker.spawn(9000.,0.));
 assert(!walker.applyToWorld(world,EntityId{}));
 walker.clear();
 assert(!walker.active()&&!walker.advance(0.,0.,false,1));
 std::cout<<"OE_WALK_COLLISION_OK: walking bridge, riverbed, gravity, jump, cliff, step, slope, ECS and atomic invalid input\n";
}
