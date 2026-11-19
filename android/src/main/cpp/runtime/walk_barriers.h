#pragma once
// OE R5.7 Stage 8: finite native collision barriers, shared by editor preview
// and the portable character WalkController. No WebView/JS handlers.
// The editor may show authoring guides; runtime receives ONLY collision data.
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace oe { namespace runtime {
constexpr std::size_t kMaxWalkBarriers=64;
struct WalkBarrier {
 std::uint64_t id=0;
 double ax=0,az=0,bx=0,bz=0;
 double thickness=.8; // full thickness in metres (horizontal)
 double height=3.;    // height over the terrain, metres
};
inline bool validWalkBarrier(const WalkBarrier&w,double halfWorld) {
 const double len=std::hypot(w.bx-w.ax,w.bz-w.az);
 return w.id>0&&w.id<=1000000&&std::isfinite(halfWorld)&&halfWorld>=32.&&
  std::isfinite(w.ax)&&std::isfinite(w.az)&&
  std::isfinite(w.bx)&&std::isfinite(w.bz)&&
  std::isfinite(w.thickness)&&std::isfinite(w.height)&&
  std::abs(w.ax)<=halfWorld-1.&&std::abs(w.bx)<=halfWorld-1.&&
  std::abs(w.az)<=halfWorld-1.&&std::abs(w.bz)<=halfWorld-1.&&
  len>=1.&&len<=512.&&w.thickness>=.3&&w.thickness<=4.&&
  w.height>=1.&&w.height<=30.;
}
inline double walkPointSegmentDistance(double px,double pz,
        double ax,double az,double bx,double bz){
 const double vx=bx-ax,vz=bz-az,len2=vx*vx+vz*vz;
 if(len2<1.e-12)return std::hypot(px-ax,pz-az);
 const double t=std::clamp(((px-ax)*vx+(pz-az)*vz)/len2,0.,1.);
 return std::hypot(px-(ax+t*vx),pz-(az+t*vz));
}
inline double walkCross(double ax,double az,double bx,double bz){
 return ax*bz-az*bx;
}
inline bool walkSegmentsIntersect(double ax,double az,double bx,double bz,
                                  double cx,double cz,double dx,double dz) {
 const double rx=bx-ax,rz=bz-az,sx=dx-cx,sz=dz-cz;
 const double den=walkCross(rx,rz,sx,sz);
 if(std::abs(den)<1.e-10)return false;
 const double qx=cx-ax,qz=cz-az;
 const double t=walkCross(qx,qz,sx,sz)/den;
 const double u=walkCross(qx,qz,rx,rz)/den;
 return t>=0.&&t<=1.&&u>=0.&&u<=1.;
}
inline double walkSegmentDistance(double ax,double az,double bx,double bz,
                                  double cx,double cz,double dx,double dz){
 if(walkSegmentsIntersect(ax,az,bx,bz,cx,cz,dx,dz))return 0.;
 return std::min({walkPointSegmentDistance(ax,az,cx,cz,dx,dz),
   walkPointSegmentDistance(bx,bz,cx,cz,dx,dz),
   walkPointSegmentDistance(cx,cz,ax,az,bx,bz),
   walkPointSegmentDistance(dx,dz,ax,az,bx,bz)});
}
inline bool walkBarrierStops(const WalkBarrier&w,
       double fromX,double fromZ,double toX,double toZ,
       double feetY,double floorHeight,double bodyRadius=.36){
 if(feetY>=floorHeight+w.height||feetY+1.6<floorHeight)return false;
 const double clearance=w.thickness*.5+bodyRadius;
 const double dist=walkSegmentDistance(fromX,fromZ,toX,toZ,
                                      w.ax,w.az,w.bx,w.bz);
 if(dist>=clearance)return false;
 // A player already intersecting a wall can move OUT, never get stuck inside.
 const double before=walkPointSegmentDistance(fromX,fromZ,w.ax,w.az,w.bx,w.bz);
 const double after=walkPointSegmentDistance(toX,toZ,w.ax,w.az,w.bx,w.bz);
 if(before<clearance&&after>before+1.e-6)return false;
 return true;
}
// One cut removes the requested central section and preserves two remaining
// segments (each remains a REAL collider). A removed middle gap stays open.
inline bool cutWalkBarrier(const WalkBarrier&source,double x,double z,
                           double radius,std::vector<WalkBarrier>&pieces){
 pieces.clear();
 if(!std::isfinite(x)||!std::isfinite(z)||!std::isfinite(radius)||
    radius<.5||radius>32.)return false;
 const double vx=source.bx-source.ax,vz=source.bz-source.az;
 const double len=std::hypot(vx,vz);
 if(len<1.)return false;
 const double t=std::clamp(((x-source.ax)*vx+(z-source.az)*vz)/(len*len),0.,1.);
 const double px=source.ax+t*vx,pz=source.az+t*vz;
 if(std::hypot(x-px,z-pz)>source.thickness*.5+radius)return false;
 const double start=std::clamp(t*len-radius,0.,len);
 const double end=std::clamp(t*len+radius,0.,len);
 if(end-start<.5)return false;
 if(start>=1.) {
  WalkBarrier left=source;
  left.bx=source.ax+vx*start/len;
  left.bz=source.az+vz*start/len;
  pieces.push_back(left);
 }
 if(len-end>=1.) {
  WalkBarrier right=source;
  right.ax=source.ax+vx*end/len;
  right.az=source.az+vz*end/len;
  pieces.push_back(right);
 }
 return true;
}
}} // namespace oe::runtime
