#pragma once
// Native port of the core UV44 BuildingWorkflow.roomPlan 2m modular grid.
// Original source semantics: unit metres, STEP=2, floor HEIGHT=2.6.
// This is only a room layout generator, not every UV44 builder algorithm.
// Pure C++17: testable on desktop without GLES, JNI or original PWB assets.
#include <cmath>
namespace oe { namespace phoenix {
constexpr int kRoomStep=2;
constexpr float kRoomStorey=2.6f;
struct RoomPiece {
 int kind;
 float x,y,z,yaw;
};
inline int roomCount(int metres,bool ceiling){
 if(metres!=4&&metres!=6&&metres!=8&&metres!=12)return 0;
 const int n=metres/kRoomStep;
 return 4*n+n*n*(ceiling?2:1);
}
template<class Emit> int roomPlan(int metres,bool ceiling,
                                   float x,float baseY,float z,Emit emit){
 const int expected=roomCount(metres,ceiling);
 if(!expected||!std::isfinite(x)||!std::isfinite(baseY)||!std::isfinite(z))
  return 0;
 const int n=metres/kRoomStep;
 int count=0;
 auto add=[&](int kind,float px,float py,float pz,float yaw){
  emit(RoomPiece{kind,px,py,pz,yaw});++count;
 };
 for(int col=0;col<n;col++){
  const float px=x-metres*.5f+1.f+col*kRoomStep;
  add(col==n/2?53:56,px,baseY,z-metres*.5f,0.f);
  add(col==n/2?54:56,px,baseY,z+metres*.5f,0.f);
 }
 for(int row=0;row<n;row++){
  const float pz=z-metres*.5f+1.f+row*kRoomStep;
  add(56,x-metres*.5f,baseY,pz,90.f);
  add(56,x+metres*.5f,baseY,pz,90.f);
 }
 for(int col=0;col<n;col++)for(int row=0;row<n;row++){
  const float px=x-metres*.5f+1.f+col*kRoomStep;
  const float pz=z-metres*.5f+1.f+row*kRoomStep;
  add(29,px,baseY,pz,0.f);
  if(ceiling)
   add(29,px,baseY+kRoomStorey-.18f,pz,0.f);
 }
 return count==expected?count:0;
}
}} // oe::phoenix
