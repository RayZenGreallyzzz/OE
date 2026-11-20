// Host-only executable for native Phoenix construction and original 2m rooms.
// Tests actual geometry apertures rather than merely searching for UI strings.
#include "../android/src/main/cpp/build/phoenix_native_modules.h"
#include "../android/src/main/cpp/build/phoenix_room_workflow.h"
#include <cassert>
#include <cmath>
#include <vector>
#include <iostream>
using oe::phoenix::Part;
using oe::phoenix::RoomPiece;
static std::vector<Part> geometry(int type){
 std::vector<Part> out;
 assert(oe::phoenix::parts(type,[&](const Part&p){out.push_back(p);}));
 return out;
}
static bool filledAt(const std::vector<Part>&boxes,float x,float y,float z){
 for(const Part&p:boxes){
  if(p.mesh!=0||std::abs(p.yaw)>.0001f)continue;
  if(std::abs(x-p.x)<p.w*.5f-.0001f &&
     std::abs(y-p.y)<p.h*.5f-.0001f &&
     std::abs(z-p.z)<p.d*.5f-.0001f)return true;
 }
 return false;
}
int main(){
 using namespace oe::phoenix;
 static_assert(kFirst==29 && kLast==56 && kCount==28);
 for(int t=kFirst;t<=kLast;t++){
  const auto ps=geometry(t);
  assert(!ps.empty());
  for(const auto&p:ps){
   assert(p.mesh>=0&&p.mesh<=2);
   assert(p.w>.001f&&p.h>.001f&&p.d>.001f);
   assert(std::isfinite(p.x)&&std::isfinite(p.y)&&
          std::isfinite(p.z)&&std::isfinite(p.yaw));
  }
 }
 // Actual void in the wall mesh: no box covers the door/window centre.
 assert(!filledAt(geometry(53),0,1.2f,0)); // through door
 assert(!filledAt(geometry(54),0,1.2f,0)); // through window
 assert(!filledAt(geometry(55),0,1.2f,0)); // arched passage
 assert(filledAt(geometry(56),0,1.2f,0));  // reversible solid wall
 assert(filledAt(geometry(54),0,.4f,0));  // window has solid sill
 assert(filledAt(geometry(53),0,2.4f,0)); // door has a lintel
 Socket sockets[2]{};
 for(int t:{31,53,54,55,56}){
  assert(oe::phoenix::sockets(t,sockets)==2);
  assert(sockets[0].x==-1.f&&sockets[1].x==1.f);
 }
 for(int size:{4,6,8,12})for(bool ceiling:{false,true}){
  std::vector<RoomPiece> room;
  int count=roomPlan(size,ceiling,14.f,5.f,20.f,
                     [&](const RoomPiece&p){room.push_back(p);});
  assert(count==roomCount(size,ceiling));
  assert(count==static_cast<int>(room.size()));
  int door=0,window=0,floor=0;
  for(const auto&p:room){
   if(p.kind==53)++door;
   if(p.kind==54)++window;
   if(p.kind==29)++floor;
   assert(std::isfinite(p.x)&&std::isfinite(p.y)&&std::isfinite(p.z));
  }
  assert(door==1&&window==1);
  assert(floor==(size/2)*(size/2)*(ceiling?2:1));
 }
 assert(roomCount(3,false)==0);
 assert(roomCount(26,true)==0);
 assert(roomPlan(4,false,NAN,1,0,[](const RoomPiece&){})==0);
 std::cout<<"OE_PHOENIX_NATIVE_WORKFLOW_OK: 28 shapes, true wall apertures, "
          <<"UV44 2m rooms and ceiling grids\n";
}
