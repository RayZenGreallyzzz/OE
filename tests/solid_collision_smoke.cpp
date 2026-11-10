#include "../android/src/main/cpp/runtime/solid_collision.h"
#include "../android/src/main/cpp/runtime/play_session.h"
#include "../android/src/main/cpp/roads/traversal_graph.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <vector>
using namespace oe::runtime;
using namespace oe::roads;

int main(){
 const SolidBox wall{{0.,1.4,0.},{.13,1.4,1.6},0.};
 assert(validSolidBox(wall));
 assert(!solidOverlapsBody({wall},{-2.,0.,0.}));
 assert(solidOverlapsBody({wall},{-.2,0.,0.}));
 assert(solidBlocksStep({wall},{-2.,0.,0.},{2.,0.,0.}));
 assert(!solidBlocksStep({wall},{-2.,0.,2.5},{2.,0.,2.5}));
 assert(!solidBlocksStep({wall},{-2.,4.,0.},{2.,4.,0.}));
 // Rotation exactly matches authored OpenGL Y convention.
 const SolidBox turned{{0.,1.4,0.},{1.6,1.4,.13},1.5707963267948966};
 assert(solidBlocksStep({turned},{-2.,0.,0.},{2.,0.,0.}));
 assert(!solidBlocksStep({turned},{-2.,0.,2.4},{2.,0.,2.4}));
 SolidBox invalid=wall;invalid.yawRadians=std::numeric_limits<double>::quiet_NaN();
 assert(!validSolidBox(invalid));

 const TerrainHeight ground=[](double,double){return 0.;};
 Road road{1,"Stone path","builtin:cobblestone",3.2,{{-10,0},{10,0}}};
 RoadTopology topology;
 assert(buildRoadTopology({road},topology,0.));
 TraversalGraph paths;
 assert(buildTraversalGraph(topology,{},ground,paths));
 PlaySession play;
 assert(!play.start({},paths,ground,512.,0.,0.,0.,{wall}));
 assert(!play.active()&&play.world().entityCount()==0);
 assert(!play.start({},paths,ground,512.,-2.,0.,0.,{invalid}));
 assert(!play.active()&&play.world().entityCount()==0);
 assert(play.start({},paths,ground,512.,-2.,0.,0.,{wall}));
 const auto oldWorld=play.player().feet.x;
 assert(play.world().entityCount()==1);
 // 4 metres of requested movement but upright player never crosses the box.
 for(int t=0;t<48;t++)assert(play.step(1.,0.,t==0,2));
 assert(play.player().feet.x>oldWorld);
 assert(play.player().feet.x<-.40);
 assert(play.player().fixedTicks==96);
 assert(play.world().ticks()==96);
 const auto actor=play.playerId();
 assert(play.world().alive(actor));
 // Holding a diagonal input at a box edge allows sideways sliding.
 const auto oldZ=play.player().feet.z;
 for(int t=0;t<8;t++)assert(play.step(1.,1.,false,2));
 assert(play.player().feet.x<-.40);
 assert(play.player().feet.z>oldZ+.2);
 play.stop();
 assert(!play.active()&&!play.world().alive(actor));
 // A new session can start without stale collision boxes.
 assert(play.start({},paths,ground,512.,-2.,0.,0.));
 for(int t=0;t<45;t++)assert(play.step(1.,0.,false,2));
 assert(play.player().feet.x>0.);
 play.stop();

 WalkController ghost;
 assert(ghost.configure(paths,ground,512.));
 assert(ghost.setStaticSolids({wall}));
 assert(!ghost.setStaticSolids({invalid}));
 assert(!ghost.spawn(0.,0.)); // old valid solid still installed atomically
 assert(ghost.spawn(-2.,0.));
 assert(ghost.advance(1.,0.,false,150));
 assert(ghost.state().feet.x<-.40);
 std::cout<<"OE_SOLID_COLLISION_OK: blocking, slide, jump, rotation, vertical, rollback, Stop\n";
}
