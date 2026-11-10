#include "../android/src/main/cpp/water/lake_surface.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
using namespace oe::water;
int main(){
 const auto bowl=[](double x,double z)->double{
  const double r=std::hypot(x,z);
  return -14.*std::max(0.,1.-r/70.);
 };
 LakeSurface result;
 const Lake water{0,0,64,-6};
 assert(validLake(water,512));
 assert(buildLakeSurface({water},512,bowl,result));
 assert(result.lakeCount==1&&!result.triangles.empty());
 assert(result.triangles.size()%3==0);
 assert(result.triangles.size()/3<=kMaxLakeTriangles);
 for(std::size_t i=0;i<result.triangles.size();i+=3){
  for(int v=0;v<3;++v){
   const auto&p=result.triangles[i+v];
   assert(std::abs(p.y-(water.level+.025f))<.0001f);
   assert(std::hypot(p.x,p.z)<=water.radius+.01f);
   // All water triangles lie in the depression, not above its hilltops.
   assert(bowl(p.x,p.z)<=water.level+.04f);
  }
  const auto&a=result.triangles[i],&b=result.triangles[i+1],
            &c=result.triangles[i+2];
  assert((b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x)<-.000001f);
 }
 const auto before=result.triangles.size();
 assert(buildLakeSurface({Lake{0,0,64,-20}},512,bowl,result));
 assert(result.triangles.empty()); // lake is below bottom: water invisible
 assert(buildLakeSurface({Lake{0,0,64,-6}},512,bowl,result));
 assert(result.triangles.size()==before);
 assert(buildLakeSurface({water},512,[](double,double){return 100.;},result));
 assert(result.triangles.empty());
 // Bad heightfields / invalid boundaries never replace a working surface.
 assert(buildLakeSurface({water},512,bowl,result));
 assert(!buildLakeSurface({Lake{0,0,200,-6}},512,bowl,result));
 assert(result.triangles.size()==before);
 assert(!buildLakeSurface({water},512,[](double,double){
  return std::numeric_limits<double>::quiet_NaN();
 },result));
 assert(result.triangles.size()==before);
 assert(!validLake({505,0,64,-6},512));
 assert(!validLake({0,0,64,200},512));
 assert(!validLake({0,0,64,std::numeric_limits<float>::infinity()},512));
 std::vector<Lake> many(9,water);
 assert(!buildLakeSurface(many,512,bowl,result));
 assert(result.triangles.size()==before);
 const Lake small{5,3,8,-1};
 assert(buildLakeSurface({small},512,[](double,double){return -10.;},result));
 assert(!result.triangles.empty());
 std::cout<<"OE_LAKE_SURFACE_OK: terrain-clipped shoreline, depth, bounded mesh, atomic failure\n";
}
