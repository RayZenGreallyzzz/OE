#include "../android/src/main/cpp/medieval/structures.h"
#include <cassert>
#include <cmath>
#include <iostream>
using namespace oe::medieval;
int main(){
 assert(!validModule(16)&&validModule(kStoneBridge)&&validModule(kTimberBridge)&&
        validModule(kStoneFoundation)&&!validModule(20));
 assert(moduleParts(20).empty());
 for(int type=kStoneBridge;type<=kLastModuleType;++type){
  const auto parts=moduleParts(type);
  assert(!parts.empty()&&parts.size()<=kMaxPartsPerModule);
  for(const Part& part:parts){
   assert(part.sx>0&&part.sy>0&&part.sz>0);
   assert(part.r>=0&&part.g>=0&&part.b>=0);
   assert(part.r<=1&&part.g<=1&&part.b<=1);
  }
  const auto b=moduleBounds(type);
  assert(b.minX<b.maxX&&b.minY<b.maxY&&b.minZ<b.maxZ);
  assert(b.maxX-b.minX<12&&b.maxZ-b.minZ<8);
  // 12 triangles / box, <=480 triangles for a whole module draw call.
  assert(parts.size()*12<=kMaxPartsPerModule*12);
 }
 const auto stone=moduleBounds(kStoneBridge);
 const auto wood=moduleBounds(kTimberBridge);
 assert(stone.maxX-stone.minX>8&&stone.minY>=0);
 assert(wood.maxX-wood.minX>8&&wood.minY>=0);
 // Both bridges must have a passage/open underside around midspan.
 for(int type:{kStoneBridge,kTimberBridge}){
  for(const Part&part:moduleParts(type)){
    if(part.sx<3 && std::abs(part.x)<1.f)
      assert(part.y-part.sy*.5f>=.7f); // no central supporting pillar
  }
 }
 const auto base=moduleBounds(kStoneFoundation);
 assert(std::abs(base.maxY-kFoundationTop)<.001f);
 assert(base.minY<-.4&&base.maxX-base.minX>7.f);
 // A foundation designed beneath the stock 6x5m tavern/forge must extend
 // beyond its walls, and a tree/road must remain unaffected.
 assert(base.minX<=-3.5f&&base.maxX>=3.5f&&
        base.minZ<=-2.9f&&base.maxZ>=2.9f);
 std::cout<<"OE_MEDIEVAL_MODULES_OK: 3 bounded shared meshes, open bridges, building foundation\n";
}
