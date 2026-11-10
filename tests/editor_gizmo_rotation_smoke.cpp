#include "../android/src/main/cpp/gizmo_rotation.h"
#include <cmath>
#include <cstdio>
using namespace oe::gizmo;
static bool close(float a,float b){return std::abs(a-b)<.002f;}
int main(){
 float x,y,z;
 Quaternion q=axisAngle(0,90.f);
 rotateVector(q,0.f,1.f,0.f,x,y,z);
 if(!close(x,0)||!close(y,0)||!close(z,1))return 1;
 // A full turn is the same orientation (quaternions q and -q equivalent).
 q=rotatedWorld({},2,360.f);
 rotateVector(q,1,0,0,x,y,z);
 if(!close(x,1)||!close(y,0)||!close(z,0))return 2;
 // World X after world Y must rotate the *world* Y vector, not object local X.
 q=rotatedWorld(rotatedWorld({},1,90.f),0,90.f);
 rotateVector(q,0,0,1,x,y,z);
 if(!close(x,1)||!close(y,0)||!close(z,0))return 3;
 q=rotatedWorld(rotatedWorld(rotatedWorld({},1,90.f),2,30.f),0,-120.f);
 const float len=q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w;
 if(!close(len,1.f))return 4;
 const float signedAngle=signedPlaneDegrees(1,0,0,0,0,-1,1);
 if(!close(signedAngle,90))return 5;
 const Quaternion bad=normalized({0,0,0,0});
 if(!close(bad.w,1))return 6;
 std::puts("OE construction gizmo: quaternion 360 / world XYZ / snap math OK");
 return 0;
}
