#include "solid_collision.h"
#include <algorithm>
#include <cmath>

namespace oe { namespace runtime {
namespace {
bool finite(double x){return std::isfinite(x);}
bool validBody(double r,double h){
 return finite(r)&&finite(h)&&r>=.1&&r<=1.&&h>=.5&&h<=3.;
}
bool verticalOverlap(const SolidBox&box,double fromY,double toY,double h){
 const double low=box.center.y-box.halfExtents.y;
 const double high=box.center.y+box.halfExtents.y;
 const double actorLow=std::min(fromY,toY);
 const double actorHigh=std::max(fromY,toY)+h;
 return actorLow<high-.001&&actorHigh>low+.001;
}
struct Local {double x,z;};
Local local(const SolidBox&b,Vec3 p){
 const double dx=p.x-b.center.x,dz=p.z-b.center.z;
 const double c=std::cos(b.yawRadians),s=std::sin(b.yawRadians);
 return {c*dx-s*dz,s*dx+c*dz};
}
bool inside(const SolidBox&b,Local p,double radius){
 return std::abs(p.x)<b.halfExtents.x+radius&&
        std::abs(p.z)<b.halfExtents.z+radius;
}
bool segmentBox(const SolidBox&b,Local from,Local to,double radius){
 // Swept circle approximated conservatively by expanding a rotated box.
 // A character starting inside an obstacle can walk OUT, not get trapped
 // forever; ordinary Play spawn refuses this overlap in the first place.
 if(inside(b,from,radius))return false;
 const double dx=to.x-from.x,dz=to.z-from.z;
 double begin=0.,end=1.;
 const auto clip=[&](double p,double d,double half)->bool{
  if(std::abs(d)<1.e-12)return std::abs(p)<half;
  double enter=(-half-p)/d,leave=(half-p)/d;
  if(enter>leave)std::swap(enter,leave);
  begin=std::max(begin,enter);
  end=std::min(end,leave);
  return begin<end&&begin<1.&&end>0.;
 };
 return clip(from.x,dx,b.halfExtents.x+radius)&&
        clip(from.z,dz,b.halfExtents.z+radius);
}
}
bool validSolidBox(const SolidBox&b) noexcept{
 return finite(b.center.x)&&finite(b.center.y)&&finite(b.center.z)&&
        std::abs(b.center.x)<8400.&&std::abs(b.center.z)<8400.&&
        std::abs(b.center.y)<10000.&&
        finite(b.halfExtents.x)&&finite(b.halfExtents.y)&&
        finite(b.halfExtents.z)&&
        b.halfExtents.x>=.01&&b.halfExtents.y>=.01&&
        b.halfExtents.z>=.01&&b.halfExtents.x<=100.&&
        b.halfExtents.y<=100.&&b.halfExtents.z<=100.&&
        finite(b.yawRadians)&&std::abs(b.yawRadians)<100000.;
}
bool solidOverlapsBody(const std::vector<SolidBox>&solids,Vec3 feet,
                       double radius,double height) noexcept{
 if(!validBody(radius,height)||!finite(feet.x)||!finite(feet.y)||
    !finite(feet.z))return true;
 for(const auto&box:solids)
  if(verticalOverlap(box,feet.y,feet.y,height)&&
     inside(box,local(box,feet),radius))return true;
 return false;
}
bool solidBlocksStep(const std::vector<SolidBox>&solids,Vec3 from,Vec3 to,
                     double radius,double height) noexcept{
 if(!validBody(radius,height)||!finite(from.x)||!finite(from.y)||
    !finite(from.z)||!finite(to.x)||!finite(to.y)||!finite(to.z))return true;
 for(const auto&box:solids){
  if(!verticalOverlap(box,from.y,to.y,height))continue;
  if(segmentBox(box,local(box,from),local(box,to),radius))return true;
 }
 return false;
}
}} // namespace oe::runtime
