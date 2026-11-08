#pragma once
// OE 0.7.6: terrain-independent 2D intersection layout and marking clipping.
// Convex junction envelopes follow the actual approach bearings and widths,
// rather than a one-size-fits-all circular decal. Built only when roads change.
#include "road_network.h"
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <vector>

namespace oe { namespace roads {
struct JunctionMouth {
    int roadId=0;
    Point centre{},left{},right{},outward{};
    float width=0.f;
    float remaining=0.f; // approach length beyond the mouth
};
struct JunctionSurface {
    Point centre{};
    std::vector<Point> outline; // CCW convex polygon
    std::vector<JunctionMouth> mouths; // oriented road mouths at the boundary
    std::size_t approaches=0;
    float minX=0.f,maxX=0.f,minZ=0.f,maxZ=0.f;
};
struct Interval {float begin=0.f,end=0.f;};

inline Point add2(Point a,Point b) noexcept {return {a.x+b.x,a.z+b.z};}
inline Point scale2(Point a,float k) noexcept {return {a.x*k,a.z*k};}
inline Point mix2(Point a,Point b,float t) noexcept {
    return {a.x+(b.x-a.x)*t,a.z+(b.z-a.z)*t};
}
inline Point unit2(Point a) noexcept {
    const float inv=1.f/std::sqrt(std::max(1e-10f,a.x*a.x+a.z*a.z));
    return {a.x*inv,a.z*inv};
}
inline float winding(Point a,Point b,Point c) noexcept {
    return cross(subtract(b,a),subtract(c,a));
}
inline std::vector<Point> convexHull(std::vector<Point> pts){
    std::sort(pts.begin(),pts.end(),[](Point a,Point b){
        return a.x<b.x||(a.x==b.x&&a.z<b.z);
    });
    pts.erase(std::unique(pts.begin(),pts.end(),[](Point a,Point b){
        return distanceSquared(a,b)<1e-8f;
    }),pts.end());
    if(pts.size()<3)return {};
    std::vector<Point> hull;
    hull.reserve(pts.size()*2);
    for(Point p:pts){
        while(hull.size()>=2&&winding(hull[hull.size()-2],hull.back(),p)<=1e-4f)
            hull.pop_back();
        hull.push_back(p);
    }
    const std::size_t lower=hull.size();
    for(std::size_t i=pts.size()-1;i>0;--i){
        const Point p=pts[i-1];
        while(hull.size()>lower&&winding(hull[hull.size()-2],hull.back(),p)<=1e-4f)
            hull.pop_back();
        hull.push_back(p);
    }
    hull.pop_back();
    return hull.size()>=3?hull:std::vector<Point>{};
}
inline Point pointAtDistance(const std::vector<Point>&points,
                             const std::vector<float>&lengths,float d) {
    if(d<=0.f)return points.front();
    if(d>=lengths.back())return points.back();
    const auto it=std::lower_bound(lengths.begin(),lengths.end(),d);
    const std::size_t i=static_cast<std::size_t>(it-lengths.begin());
    const float len=lengths[i]-lengths[i-1];
    return mix2(points[i-1],points[i],
                len>1e-5f?(d-lengths[i-1])/len:0.f);
}
inline std::vector<JunctionSurface> makeJunctionSurfaces(
       const std::vector<Road>&roads,const Network&network,float worldHalf) {
    std::vector<JunctionSurface> surfaces;
    if(!std::isfinite(worldHalf)||worldHalf<=0.f)return surfaces;
    surfaces.reserve(network.junctions.size());
    for(const Junction&j:network.junctions){
        std::vector<Point> corners;
        std::vector<JunctionMouth> mouths;
        std::vector<Point> bearings;
        std::size_t approaches=0;
        for(int id:j.roadIds){
            const auto found=std::find_if(roads.begin(),roads.end(),
                [id](const Road&r){return r.id==id;});
            if(found==roads.end()||found->points.size()<2)continue;
            const auto polyline=sample(*found);
            if(polyline.size()<2)continue;
            std::vector<float> lengths(polyline.size(),0.f);
            for(std::size_t k=1;k<polyline.size();++k)
                lengths[k]=lengths[k-1]+
                     std::sqrt(distanceSquared(polyline[k-1],polyline[k]));
            float best=1e30f,at=0.f;
            for(std::size_t k=1;k<polyline.size();++k){
                const Point p=closestPoint(j.position,polyline[k-1],polyline[k]);
                const float d2=distanceSquared(j.position,p);
                if(d2<best){
                    best=d2;
                    at=lengths[k-1]+std::sqrt(distanceSquared(polyline[k-1],p));
                }
            }
            if(!std::isfinite(best)||best>4.f)continue;
            // Each side of a through-road contributes one mouth. A T-branch
            // ending at the junction contributes only the outgoing direction.
            for(int side=-1;side<=1;side+=2){
                const float delta=std::max(j.radius,found->width*.7f);
                const float dest=std::max(0.f,std::min(lengths.back(),at+side*delta));
                if(std::abs(dest-at)<.25f)continue;
                Point mouth=pointAtDistance(polyline,lengths,dest);
                Point direction=subtract(mouth,j.position);
                if(distanceSquared(direction,{0,0})<.25f)continue;
                direction=unit2(direction);
                const Point normal{-direction.z,direction.x};
                const float half=found->width*.5f+.12f;
                const Point left{mouth.x+normal.x*half,mouth.z+normal.z*half};
                const Point right{mouth.x-normal.x*half,mouth.z-normal.z*half};
                corners.push_back(left);
                corners.push_back(right);
                const float remaining=side>0?lengths.back()-dest:dest;
                mouths.push_back({id,mouth,left,right,direction,
                                  found->width,remaining});
                bearings.push_back(direction);
                ++approaches;
            }
        }
        if(approaches<2)continue;
        // Two opposite mouths of equal width are just a straight butt join;
        // leave continuous centre lines rather than manufacture a junction.
        if(approaches==2&&bearings.size()==2&&
           bearings[0].x*bearings[1].x+bearings[0].z*bearings[1].z<-.985f)
            continue;
        // Keep all rendered vertices in-bounds; roads themselves are validated.
        for(Point&point:corners){
            point.x=std::max(-worldHalf,std::min(worldHalf,point.x));
            point.z=std::max(-worldHalf,std::min(worldHalf,point.z));
        }
        auto hull=convexHull(std::move(corners));
        if(hull.size()<3)continue;
        JunctionSurface surface;
        surface.centre=j.position;
        surface.outline=std::move(hull);
        surface.mouths=std::move(mouths);
        surface.approaches=approaches;
        surface.minX=surface.maxX=surface.outline[0].x;
        surface.minZ=surface.maxZ=surface.outline[0].z;
        for(JunctionMouth&mouth:surface.mouths){
            for(Point* p:{&mouth.left,&mouth.right,&mouth.centre}){
                p->x=std::max(-worldHalf,std::min(worldHalf,p->x));
                p->z=std::max(-worldHalf,std::min(worldHalf,p->z));
            }
        }
        for(Point p:surface.outline){
            surface.minX=std::min(surface.minX,p.x);
            surface.maxX=std::max(surface.maxX,p.x);
            surface.minZ=std::min(surface.minZ,p.z);
            surface.maxZ=std::max(surface.maxZ,p.z);
        }
        surfaces.push_back(std::move(surface));
    }
    return surfaces;
}

// Convex clipping returns the interval *inside* one junction polygon.
// Rendering draws only the outside ranges of centre and side markings.
inline bool polygonInterval(Point a,Point b,const JunctionSurface&shape,
                            Interval&interval){
    if(shape.outline.size()<3||
       std::max(a.x,b.x)<shape.minX||std::min(a.x,b.x)>shape.maxX||
       std::max(a.z,b.z)<shape.minZ||std::min(a.z,b.z)>shape.maxZ)
        return false;
    float lo=0.f,hi=1.f;
    const Point step=subtract(b,a);
    const std::size_t n=shape.outline.size();
    for(std::size_t i=0;i<n;++i){
        const Point p=shape.outline[i],q=shape.outline[(i+1)%n];
        const Point edge=subtract(q,p);
        const float start=cross(edge,subtract(a,p));
        const float slope=cross(edge,step);
        if(std::abs(slope)<1e-7f){
            if(start<0.f)return false;
            continue;
        }
        const float t=-start/slope;
        if(slope>0.f)lo=std::max(lo,t);
        else hi=std::min(hi,t);
        if(hi<=lo)return false;
    }
    if(hi<=lo+1e-5f)return false;
    interval={std::max(0.f,lo),std::min(1.f,hi)};
    return interval.end>interval.begin+1e-5f;
}
inline std::vector<Interval> markingOutsideJunctions(
    Point a,Point b,const std::vector<JunctionSurface>&shapes){
    std::vector<Interval> outside{{0.f,1.f}};
    for(const JunctionSurface&shape:shapes){
        Interval inside;
        if(!polygonInterval(a,b,shape,inside))continue;
        std::vector<Interval> next;
        for(Interval range:outside){
            if(range.end<=inside.begin||range.begin>=inside.end){
                next.push_back(range);
                continue;
            }
            if(range.begin<inside.begin-1e-5f)
                next.push_back({range.begin,inside.begin});
            if(range.end>inside.end+1e-5f)
                next.push_back({inside.end,range.end});
        }
        outside.swap(next);
        if(outside.empty())break;
    }
    return outside;
}
}} // namespace oe::roads
