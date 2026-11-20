// Stage11: native linked collision node atomicity, T-junctions and Undo.
// Run with g++ -std=c++17 -O2 -Wall -Wextra -Werror -pedantic.
#include "../android/src/main/cpp/runtime/walk_barrier_nodes.h"
#include <cassert>
#include <iostream>
#include <vector>
using namespace oe::runtime;
int main(){
 std::vector<WalkBarrier> w={
  {1,0,0,10,0,.8,4},
  {2,10,0,20,0,.8,4},
  {3,10,.02,10,10,.8,4}, // near junction within 5cm
  {4,40,0,50,0,.8,4}, // unrelated
 };
 assert(walkNodeDegree(w,10,0)==3);
 assert(walkNodeDegree(w,0,0)==1);
 assert(walkNodeDegree(w,50,0)==1);
 const auto original=w; // mirrors OE SceneSnapshot for Undo
 WalkBarrier desired=w[0];desired.bx=12;desired.bz=2;
 assert(editLinkedWalkBarrier(w,1,desired,512.)==3);
 assert(w[0].bx==12&&w[0].bz==2);
 assert(w[1].ax==12&&w[1].az==2);
 assert(w[2].ax==12&&w[2].az==2); // T junction follows
 assert(w[3].ax==40&&w[3].bx==50); // unrelated stays intact
 assert(walkNodeDegree(w,12,2)==3);
 for(const auto&barrier:w)assert(validWalkBarrier(barrier,512.));
 // Any invalid *neighbor* rejects all 3 without a partial wall migration:
 const auto beforeFail=w;
 desired=w[0];desired.bx=19.5;desired.bz=0;
 assert(validWalkBarrier(desired,512.)); // selected would be legal alone
 assert(editLinkedWalkBarrier(w,1,desired,512.)==0);
 assert(w[0].bx==beforeFail[0].bx&&w[0].bz==beforeFail[0].bz);
 assert(w[1].ax==beforeFail[1].ax&&w[1].az==beforeFail[1].az);
 assert(w[2].ax==beforeFail[2].ax&&w[2].az==beforeFail[2].az);
 // A change to wall thickness does not propagate height/width to neighbors.
 desired=w[0];desired.thickness=1.5;
 assert(editLinkedWalkBarrier(w,1,desired,512.)==1);
 assert(w[0].thickness==1.5&&w[1].thickness==.8);
 // Moving the whole selected wall moves joined endpoints of other segments.
 desired=w[0];desired.ax=2;desired.az=1;desired.bx=14;desired.bz=3;
 assert(editLinkedWalkBarrier(w,1,desired,512.)==3);
 assert(w[1].ax==14&&w[1].az==3);
 assert(w[2].ax==14&&w[2].az==3);
 assert(w[3].ax==40);
 // A disconnected near miss >5cm stays disconnected:
 auto extra=w;
 extra.push_back({5,14.09,3,30,3,.8,4});
 desired=extra[0];desired.bx=15;desired.bz=4;
 assert(editLinkedWalkBarrier(extra,1,desired,512.)==3);
 assert(extra.back().ax==14.09&&extra.back().az==3);
 // A stale reference or nonsense geometry must never change the city.
 desired=extra[0];desired.id=999;
 assert(editLinkedWalkBarrier(extra,999,desired,512.)==0);
 assert(editLinkedWalkBarrier(extra,1,desired,512.)==0);
 // Undo = restore the single pre-change vector snapshot; joins also restore.
 w=original;
 assert(w[0].bx==10&&w[1].ax==10&&w[2].ax==10);
 assert(walkNodeDegree(w,10,0)==3);
 assert(walkBarrierStops(w[0],5,-2,5,2,0,0));
 std::cout<<"OE_STAGE11_LINKED_COLLISION_NODES_OK\n";
}
