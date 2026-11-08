#pragma once
// OE road-editor data and curve sampling. Portable C++17; independent of Android/GLES.
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <vector>

namespace oe { namespace roads {
constexpr std::size_t kMaxRoads=24;
constexpr std::size_t kMaxPoints=48;
constexpr float kMinWidth=2.f, kMaxWidth=18.f;
constexpr int kCurveSubdivisions=5;

struct Point {float x=0.f,z=0.f;};
struct Road {
    int id=0;
    float width=6.f;
    bool markings=true;
    std::vector<Point> points;
};

inline float distanceSquared(Point a,Point b) noexcept {
    const float x=a.x-b.x,z=a.z-b.z;
    return x*x+z*z;
}
inline bool finitePoint(Point p) noexcept {
    return std::isfinite(p.x)&&std::isfinite(p.z);
}
inline bool append(Road&road,Point p,float halfWorld,float minSpacing) {
    if(road.points.size()>=kMaxPoints||!finitePoint(p)||
       !std::isfinite(halfWorld)||halfWorld<=0.f||
       std::abs(p.x)>halfWorld||std::abs(p.z)>halfWorld)return false;
    const float spacing=std::max(.25f,minSpacing);
    if(!road.points.empty()&&distanceSquared(road.points.back(),p)<spacing*spacing)
        return false;
    road.points.push_back(p);
    return true;
}
inline bool valid(const Road&road,float halfWorld) noexcept {
    if(road.id<=0||!std::isfinite(road.width)||
       road.width<kMinWidth||road.width>kMaxWidth||
       road.points.size()<2||road.points.size()>kMaxPoints||
       !std::isfinite(halfWorld)||halfWorld<=0.f)return false;
    for(std::size_t i=0;i<road.points.size();++i){
        const Point p=road.points[i];
        if(!finitePoint(p)||std::abs(p.x)>halfWorld||std::abs(p.z)>halfWorld)
            return false;
        if(i&&distanceSquared(road.points[i-1],p)<.25f*.25f)return false;
    }
    return true;
}
// Cubic Hermite/Catmull-Rom interpolation passes precisely through edit handles.
inline Point interpolate(Point p0,Point p1,Point p2,Point p3,float t) noexcept {
    t=std::max(0.f,std::min(1.f,t));
    const float t2=t*t,t3=t2*t;
    const float h00=2*t3-3*t2+1,h10=t3-2*t2+t;
    const float h01=-2*t3+3*t2,h11=t3-t2;
    const float mx1=.5f*(p2.x-p0.x),mz1=.5f*(p2.z-p0.z);
    const float mx2=.5f*(p3.x-p1.x),mz2=.5f*(p3.z-p1.z);
    return {h00*p1.x+h10*mx1+h01*p2.x+h11*mx2,
            h00*p1.z+h10*mz1+h01*p2.z+h11*mz2};
}
inline std::vector<Point> sample(const Road&road) {
    std::vector<Point> result;
    if(road.points.size()<2)return result;
    result.reserve((road.points.size()-1)*kCurveSubdivisions+1);
    for(std::size_t i=0;i+1<road.points.size();++i){
        const Point a=road.points[i?i-1:i],b=road.points[i],
                    c=road.points[i+1],
                    d=road.points[i+2<road.points.size()?i+2:i+1];
        for(int step=0;step<kCurveSubdivisions;++step)
            result.push_back(interpolate(a,b,c,d,step/(float)kCurveSubdivisions));
    }
    result.push_back(road.points.back());
    return result;
}
}} // namespace oe::roads
