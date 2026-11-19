// Owner scenario: a native collision ridge blocks movement, then a gap opens
// when the author erases its middle. No WebView and no separate game world.
#include "../android/src/main/cpp/runtime/walk_collision.h"
#include <cassert>
#include <cmath>
#include <iostream>
using namespace oe::runtime;
int main(){
 WalkBarrier wall{1,0,-15,0,15,.8,4.};
 assert(validWalkBarrier(wall,512.));
 assert(walkBarrierStops(wall,-2,0,2,0,0,0));
 assert(!walkBarrierStops(wall,-2,25,2,25,0,0));
 assert(!walkBarrierStops(wall,-2,0,2,0,6,0));
 std::vector<WalkBarrier> pieces;
 assert(cutWalkBarrier(wall,0,0,4,pieces));
 assert(pieces.size()==2);
 assert(std::abs(pieces[0].bz+4.)<.001);
 assert(std::abs(pieces[1].az-4.)<.001);
 pieces[1].id=2;
 assert(!walkBarrierStops(pieces[0],-2,0,2,0,0,0));
 assert(!walkBarrierStops(pieces[1],-2,0,2,0,0,0));
 assert(walkBarrierStops(pieces[0],-2,-10,2,-10,0,0));
 assert(!cutWalkBarrier(wall,50,50,4,pieces));
 assert(cutWalkBarrier(wall,0,0,4,pieces));pieces[1].id=2;
 assert(!validWalkBarrier(WalkBarrier{3,0,0,900,0,.8,4},512));
 oe::roads::TraversalGraph empty;
 auto terrain=[](double,double){return 0.;};
 WalkController walker;
 assert(walker.configure(empty,terrain,512.,{wall}));
 assert(walker.barrierCount()==1);
 assert(walker.spawn(-2,0));
 assert(walker.advance(1,0,false,90));
 assert(walker.state().feet.x<-.70); // walls block the SAME 60Hz controller
 assert(walker.configure(empty,terrain,512.,{}));
 assert(walker.spawn(-2,0));
 assert(walker.advance(1,0,false,90));
 assert(walker.state().feet.x>0.5);  // erase/remove allows movement
 assert(walker.configure(empty,terrain,512.,pieces.size()==2?pieces:
        std::vector<WalkBarrier>{})); // no partial graph mutation
 assert(walker.spawn(-2,0));
 assert(walker.advance(1,0,false,90));
 assert(walker.state().feet.x>0.5);  // a gap works
 // On an ascending hillside, compare actor Y with wall LOCAL foot height,
 // NOT wall midpoint Y: old code let players walk through uphill wall ends.
 const oe::roads::TerrainHeight hillside=[](double,double z){return .5*z;};
 WalkBarrier ridge{5,0,-15,0,15,.8,4.};
 assert(walker.configure(empty,hillside,512.,{ridge}));
 assert(walker.spawn(-2.,8.));
 assert(walker.advance(1.,0.,false,90));
 assert(walker.state().feet.x<-.70); // remains blocked at +4m terrain
 std::vector<WalkBarrier> ridgeGap;
 assert(cutWalkBarrier(ridge,0.,8.,4.,ridgeGap));
 assert(ridgeGap.size()==2);ridgeGap[1].id=6;
 assert(walker.configure(empty,hillside,512.,ridgeGap));
 assert(walker.spawn(-2.,8.));
 assert(walker.advance(1.,0.,false,90));
 assert(walker.state().feet.x>.50); // opening is truly passable
 // Stage9 two-tap authoring: a DIAGONAL chain is as physical as old
 // pre-sized walls. Cutting its middle must open the authored gap.
 WalkBarrier diagonal{12,-12,-12,12,12,.8,4.};
 assert(validWalkBarrier(diagonal,512.));
 assert(walker.configure(empty,terrain,512.,{diagonal}));
 assert(walker.spawn(-3.,3.));
 assert(walker.advance(1.,0.,false,140));
 assert(walker.state().feet.x<2.5);
 std::vector<WalkBarrier> diagonalGap;
 assert(cutWalkBarrier(diagonal,3.,3.,4.,diagonalGap));
 assert(diagonalGap.size()==2);
 diagonalGap[1].id=13;
 assert(walker.configure(empty,terrain,512.,diagonalGap));
 assert(walker.spawn(-3.,3.));
 assert(walker.advance(1.,0.,false,140));
 assert(walker.state().feet.x>4.);
 walker.clear();assert(walker.barrierCount()==0);
 std::cout<<"OE_STAGE8_REAL_WALK_COLLISIONS_OK\n";
}
