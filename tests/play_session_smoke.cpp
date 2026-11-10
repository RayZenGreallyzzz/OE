#include "../android/src/main/cpp/runtime/play_session.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>
using namespace oe::runtime;
using namespace oe::roads;

int main(){
 const TerrainHeight ground=[](double,double){return 0.;};
 Road road{1,"Old cobbles","builtin:cobblestone",3.2,{{-8,0},{8,0}}};
 RoadTopology topology;
 assert(buildRoadTopology({road},topology,0.));
 TraversalGraph traversal;
 assert(buildTraversalGraph(topology,{},ground,traversal));
 std::vector<SceneItem> editable;
 SceneItem house;
 house.name="Authoring house";house.meshAsset="builtin:0";
 house.transform.position={2,0,3};
 editable.push_back(house);
 PlaySession play;
 assert(play.start(editable,traversal,ground,512.,-6.,0.,0.));
 assert(play.active()&&play.world().entityCount()==2);
 assert(play.world().alive(play.playerId()));
 assert(play.world().snapshot().size()==2);
 const auto original=editable[0].transform.position;
 const auto initial=play.player().feet;
 assert(play.step(1.,0.,false,2)); // yaw zero => world +X
 assert(play.player().feet.x>initial.x);
 assert(play.player().fixedTicks==2&&play.world().ticks()==2);
 assert(editable[0].transform.position.x==original.x);
 assert(editable[0].transform.position.z==original.z);
 const Transform* playerInWorld=play.world().transform(play.playerId());
 assert(playerInWorld!=nullptr);
 assert(std::abs(playerInWorld->position.x-play.player().feet.x)<1.e-7);
 // Camera relative input: after a quarter turn, right moves toward -Z.
 for(int turn=0;turn<6;turn++)play.orbit(1.5707963267948966/6.,0.);
 const double oldX=play.player().feet.x,oldZ=play.player().feet.z;
 assert(play.step(1.,0.,false,2));
 assert(play.player().feet.z<oldZ);
 assert(std::abs(play.player().feet.x-oldX)<.08);
 assert(!play.start(editable,traversal,ground,512.,-6.,0.,0.)); // no reentry
 assert(play.world().entityCount()==2);
 const auto beforeInvalid=play.player().feet;
 assert(!play.step(1.,0.,false,999)); // bounded fixed batch
 assert(play.player().feet.x==beforeInvalid.x&&
        play.player().feet.z==beforeInvalid.z);
 const auto player=play.playerId();
 play.stop();
 assert(!play.active()&&play.world().entityCount()==0);
 assert(!play.world().alive(player)&&!play.step(0.,0.,false,2));
 // Malformed scene fails without changing input or claiming a Play session.
 SceneItem invalid=house;invalid.transform.scale.x=-1;
 PlaySession rejected;
 assert(!rejected.start({invalid},traversal,ground,512.,0.,0.,0.));
 assert(!rejected.active()&&rejected.world().entityCount()==0);
 assert(editable.size()==1&&editable[0].meshAsset=="builtin:0");
 std::cout<<"OE_PLAY_SESSION_OK: detached ECS snapshot, camera input, ticks, stop and failed start\n";
}
