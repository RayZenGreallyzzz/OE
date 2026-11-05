#include "../android/src/main/cpp/quality_profiles.h"
#include <cassert>
#include <iostream>
int main(){
 using namespace oe;
 assert(recommendedTier(1536,2048,false)==Economy);
 assert(recommendedTier(4096,8192,false)==Low);
 assert(recommendedTier(6144,4096,false)==Balanced);
 assert(recommendedTier(16384,16384,false)==Balanced);
 assert(recommendedTier(8192,8192,true)==Economy);
 assert(recommendedTier(3072,1024,false)==Economy);
 for(int i=Economy;i<=High;i++){
  const auto&p=profile(i);
  assert(validTier(i));
  assert(validScale(p.resolutionScale));
  assert(p.terrainTileRadius>=3&&p.terrainTileRadius<=6);
  assert(p.viewRangeMetres>=100.f);
  assert(p.lodDistanceFactor>0.f);
  assert(p.maxVisiblePaintStamps>0&&p.maxVisiblePaintStamps<=48);
  assert(p.glbTextureCap==1024||p.glbTextureCap==2048);
 }
 assert(!validTier(-1)&&!validTier(4));
 assert(profile(-1).resolutionScale==profile(Low).resolutionScale);
 std::cout<<"OE_DEVICE_QUALITY_PROFILES_OK\n";
}
