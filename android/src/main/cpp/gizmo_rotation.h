#pragma once
// Lightweight world-space quaternion rotations for touch-friendly construction
// editor gizmos. Renderer-independent so Android CI can test exact behavior.
#include <cmath>
namespace oe { namespace gizmo {
constexpr float kPi=3.14159265358979323846f;
struct Quaternion {float x=0.f,y=0.f,z=0.f,w=1.f;};
inline Quaternion normalized(Quaternion q){
 const float length2=q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w;
 if(!std::isfinite(length2)||length2<1.e-8f)return {};
 const float k=1.f/std::sqrt(length2);
 return {q.x*k,q.y*k,q.z*k,q.w*k};
}
inline Quaternion multiply(Quaternion a,Quaternion b){
 return normalized({
  a.w*b.x+a.x*b.w+a.y*b.z-a.z*b.y,
  a.w*b.y-a.x*b.z+a.y*b.w+a.z*b.x,
  a.w*b.z+a.x*b.y-a.y*b.x+a.z*b.w,
  a.w*b.w-a.x*b.x-a.y*b.y-a.z*b.z
 });
}
inline Quaternion axisAngle(int axis,float degrees){
 if(axis<0||axis>2||!std::isfinite(degrees))return {};
 const float half=degrees*(kPi/360.f),s=std::sin(half),c=std::cos(half);
 return normalized({axis==0?s:0.f,axis==1?s:0.f,axis==2?s:0.f,c});
}
// A world-axis delta PRE-multiplies the object orientation. This avoids the
// Euler gimbal lock and keeps 3 independent X/Y/Z rings responsive at any pose.
inline Quaternion rotatedWorld(Quaternion original,int axis,float degrees){
 return multiply(axisAngle(axis,degrees),original);
}
inline void rotateVector(Quaternion q,float x,float y,float z,float&rx,float&ry,float&rz){
 q=normalized(q);
 const float xx=q.x*q.x,yy=q.y*q.y,zz=q.z*q.z;
 const float xy=q.x*q.y,xz=q.x*q.z,yz=q.y*q.z;
 const float wx=q.w*q.x,wy=q.w*q.y,wz=q.w*q.z;
 rx=(1-2*(yy+zz))*x+2*(xy-wz)*y+2*(xz+wy)*z;
 ry=2*(xy+wz)*x+(1-2*(xx+zz))*y+2*(yz-wx)*z;
 rz=2*(xz-wy)*x+2*(yz+wx)*y+(1-2*(xx+yy))*z;
}
inline float signedPlaneDegrees(float ux,float uy,float uz,float vx,float vy,float vz,
 int axis){
 const float dot=ux*vx+uy*vy+uz*vz;
 const float cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;
 const float cross=axis==0?cx:axis==1?cy:cz;
 return std::atan2(cross,dot)*(180.f/kPi);
}
} }
