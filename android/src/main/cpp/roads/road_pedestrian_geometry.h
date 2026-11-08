#pragma once
// OE 0.7.7 pedestrian geometry: portable, deterministic 2D layout.
// Shader/EGL/terrain are handled by the editor's cached road renderer.
#include "road_junction_geometry.h"
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <vector>

namespace oe { namespace roads {
constexpr float kSidewalkWidth=1.8f;
constexpr float kCurbRise=.145f; // sidewalk surface over asphalt
constexpr float kCrosswalkGap=1.0f; // metres outside the road-mouth boundary
constexpr float kCrosswalkDepth=1.35f;
constexpr std::size_t kMaxCrosswalks=256;

struct PedestrianQuad {Point a{},b{},c{},d{};};
struct Crosswalk {
    int roadId=0;
    Point centre{};
    Point direction{}; // away from intersection
    std::vector<PedestrianQuad> bars;
};
inline Point outwardNormal(Point a,Point b) noexcept {
    const Point edge=unit2(subtract(b,a));
    return {edge.z,-edge.x}; // convex polygon has CCW winding
}
inline float dot2(Point a,Point b) noexcept {return a.x*b.x+a.z*b.z;}
inline bool insideJunction(Point point,const JunctionSurface&surface,
                           float epsilon=.01f) noexcept {
    if(surface.outline.size()<3)return false;
    for(std::size_t i=0;i<surface.outline.size();++i){
        if(winding(surface.outline[i],
                   surface.outline[(i+1)%surface.outline.size()],point)<-epsilon)
            return false;
    }
    return true;
}
inline bool mouthEdge(const JunctionSurface&surface,Point a,Point b) noexcept {
    for(const JunctionMouth&mouth:surface.mouths){
        const bool same=distanceSquared(a,mouth.left)<.02f &&
                        distanceSquared(b,mouth.right)<.02f;
        const bool reverse=distanceSquared(a,mouth.right)<.02f &&
                           distanceSquared(b,mouth.left)<.02f;
        if(same||reverse)return true;
    }
    return false;
}
// Convex asphalt junctions get sidewalk aprons only on the *corner* edges,
// never across a live driving approach. Miter corners are length-limited so
// nearly-parallel/acute edges cannot create huge self-overlapping geometry.
inline std::vector<PedestrianQuad> junctionCornerSidewalks(
    const JunctionSurface&surface,float worldHalf,
    float sidewalkWidth=kSidewalkWidth) {
    const auto&poly=surface.outline;
    std::vector<PedestrianQuad> result;
    const std::size_t n=poly.size();
    if(n<3||!std::isfinite(worldHalf)||worldHalf<=0.f||
       !std::isfinite(sidewalkWidth)||sidewalkWidth<=0.f)return result;
    std::vector<Point> shifted(n);
    for(std::size_t i=0;i<n;++i){
        const Point prev=outwardNormal(poly[(i+n-1)%n],poly[i]);
        const Point next=outwardNormal(poly[i],poly[(i+1)%n]);
        Point sum=add2(prev,next);
        if(distanceSquared(sum,{0.f,0.f})<1e-6f)sum=next;
        const Point bisector=unit2(sum);
        const float cosTheta=std::max(.45f,dot2(bisector,next));
        const float length=std::min(2.2f*sidewalkWidth,sidewalkWidth/cosTheta);
        shifted[i]=add2(poly[i],scale2(bisector,length));
        shifted[i].x=std::max(-worldHalf,std::min(worldHalf,shifted[i].x));
        shifted[i].z=std::max(-worldHalf,std::min(worldHalf,shifted[i].z));
    }
    for(std::size_t i=0;i<n;++i){
        const std::size_t next=(i+1)%n;
        if(mouthEdge(surface,poly[i],poly[next]))continue;
        if(distanceSquared(poly[i],poly[next])<.04f)continue;
        if(distanceSquared(shifted[i],shifted[next])<.04f)continue;
        result.push_back({poly[i],poly[next],shifted[next],shifted[i]});
    }
    return result;
}
inline bool insideAnyJunction(Point point,
    const std::vector<JunctionSurface>&surfaces) noexcept {
    for(const auto&surface:surfaces)
        if(insideJunction(point,surface))return true;
    return false;
}
inline bool validFootPoint(Point point,float halfWorld) noexcept {
    return finitePoint(point)&&
           std::abs(point.x)<=halfWorld&&std::abs(point.z)<=halfWorld;
}
// Bars are oriented across each approach road, *outside* the intersection.
// If they would land on another intersection or outside the map, omit that
// whole crossing rather than render misleading floating/partial stripes.
inline std::vector<Crosswalk> makeCrosswalks(
    const std::vector<JunctionSurface>&surfaces,float halfWorld) {
    std::vector<Crosswalk> result;
    if(!std::isfinite(halfWorld)||halfWorld<=0.f)return result;
    for(const auto&surface:surfaces){
        for(const JunctionMouth&mouth:surface.mouths){
            if(result.size()>=kMaxCrosswalks)return result;
            if(mouth.width<kMinWidth||mouth.width>kMaxWidth||
               mouth.remaining<kCrosswalkGap+kCrosswalkDepth+.3f)
                continue;
            const Point direction=unit2(mouth.outward);
            const Point normal{-direction.z,direction.x};
            const float available=mouth.width-.8f;
            constexpr float stripe=.42f,spacing=.32f;
            const int count=std::max(0,std::min(30,
                   static_cast<int>((available+spacing)/(stripe+spacing))));
            if(count<2)continue;
            const float span=count*stripe+(count-1)*spacing;
            const Point centre=add2(mouth.centre,scale2(direction,
                           kCrosswalkGap+kCrosswalkDepth*.5f));
            Crosswalk crossing;
            crossing.roadId=mouth.roadId;
            crossing.centre=centre;
            crossing.direction=direction;
            crossing.bars.reserve(static_cast<std::size_t>(count));
            bool validCrossing=true;
            for(int i=0;i<count;++i){
                const float start=-span*.5f+i*(stripe+spacing);
                const Point c0=add2(centre,scale2(normal,start));
                const Point c1=add2(c0,scale2(normal,stripe));
                const Point depth=scale2(direction,kCrosswalkDepth*.5f);
                const PedestrianQuad bar{
                    subtract(c0,depth),subtract(c1,depth),
                    add2(c1,depth),add2(c0,depth)
                };
                for(const Point p:{bar.a,bar.b,bar.c,bar.d}){
                    if(!validFootPoint(p,halfWorld)||
                       insideAnyJunction(p,surfaces)){
                        validCrossing=false;break;
                    }
                }
                if(!validCrossing)break;
                crossing.bars.push_back(bar);
            }
            if(validCrossing&&crossing.bars.size()==static_cast<std::size_t>(count))
                result.push_back(std::move(crossing));
        }
    }
    return result;
}
}} // namespace oe::roads
