#include "lake_surface.h"
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <utility>
#include <vector>
namespace oe { namespace water {
namespace {
constexpr double kPi=3.14159265358979323846;
constexpr float kShoreOffset=.025f;
struct Point {double x=0,z=0,depth=0;};
double area(const Point&a,const Point&b,const Point&c){
 return (b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);
}
bool sample(const Lake&lake,const TerrainHeight&terrain,double radius,
            double angle,Point&point){
 point.x=lake.x+radius*std::cos(angle);
 point.z=lake.z+radius*std::sin(angle);
 const double surface=terrain(point.x,point.z);
 if(!std::isfinite(surface)||std::abs(surface)>10000.)return false;
 point.depth=static_cast<double>(lake.level)-surface;
 return true;
}
bool appendClipped(const Lake&lake,const Point&a,const Point&b,const Point&c,
                   std::vector<WaterVertex>&result){
 // Clockwise/upward order after clipping an X/Z CCW triangle.
 const Point input[3]={a,b,c};
 std::vector<Point> clip;clip.reserve(4);
 for(int i=0;i<3;++i){
  const Point from=input[i],to=input[(i+1)%3];
  const bool inside=from.depth>.001,toInside=to.depth>.001;
  if(inside)clip.push_back(from);
  if(inside!=toInside){
   const double scale=from.depth/(from.depth-to.depth);
   clip.push_back({from.x+(to.x-from.x)*scale,
                   from.z+(to.z-from.z)*scale,0});
  }
 }
 if(clip.size()<3)return true;
 const auto emit=[&](const Point&p)->WaterVertex{
  const float x=static_cast<float>(p.x),z=static_cast<float>(p.z);
  const float shimmer=.015f*std::sin(x*.23f+z*.17f);
  return {x,lake.level+kShoreOffset,z,
          .12f+shimmer,.38f+shimmer,.49f+shimmer};
 };
 for(std::size_t i=1;i+1<clip.size();++i){
  if(std::abs(area(clip[0],clip[i],clip[i+1]))<.0001)continue;
  if(result.size()>kMaxLakeTriangles*3-3)return false;
  // CCW radial polygon in X/Z => reverse for Y-up normals.
  result.push_back(emit(clip[0]));
  result.push_back(emit(clip[i+1]));
  result.push_back(emit(clip[i]));
 }
 return true;
}
}
bool validLake(const Lake&lake,float worldHalf) noexcept{
 return std::isfinite(lake.x)&&std::isfinite(lake.z)&&
        std::isfinite(lake.radius)&&std::isfinite(lake.level)&&
        std::isfinite(worldHalf)&&worldHalf>=512.f&&worldHalf<=8192.f&&
        lake.radius>=kMinLakeRadius&&lake.radius<=kMaxLakeRadius&&
        lake.level>=kMinLakeLevel&&lake.level<=kMaxLakeLevel&&
        std::abs(lake.x)+lake.radius<=worldHalf&&
        std::abs(lake.z)+lake.radius<=worldHalf;
}
bool buildLakeSurface(const std::vector<Lake>&lakes,float worldHalf,
                      const TerrainHeight&terrain,LakeSurface&output){
 if(!terrain||lakes.size()>kMaxLakes)return false;
 LakeSurface draft;draft.lakeCount=lakes.size();
 for(const auto&lake:lakes){
  if(!validLake(lake,worldHalf))return false;
  const int rings=std::max(2,std::min(28,static_cast<int>(std::ceil(lake.radius/6.))));
  const int sectors=std::max(24,std::min(96,
      static_cast<int>(std::ceil(2.*kPi*lake.radius/6.))));
  for(int i=0;i<rings;++i){
   const double inner=lake.radius*i/rings,outer=lake.radius*(i+1)/rings;
   for(int k=0;k<sectors;++k){
    const double a=2.*kPi*k/sectors,b=2.*kPi*(k+1)/sectors;
    Point p0,p1,p2,p3;
    if(!sample(lake,terrain,inner,a,p0)||
       !sample(lake,terrain,outer,a,p1)||
       !sample(lake,terrain,outer,b,p2)||
       !sample(lake,terrain,inner,b,p3))return false;
    if(!appendClipped(lake,p0,p1,p2,draft.triangles)||
       (i>0&&!appendClipped(lake,p0,p2,p3,draft.triangles)))return false;
   }
  }
 }
 output=std::move(draft);
 return true;
}
}} // namespace oe::water
