#pragma once
// OE 0.7.5 road-network detection. Portable C++17: no JNI, GLES, or Android.
// Junction graph is derived from road control points, never serialized.
#include "road_model.h"
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <vector>

namespace oe { namespace roads {

constexpr std::size_t kMaxJunctions=512;
constexpr float kSnapDistance=3.0f;
constexpr float kJunctionMergeDistance=1.25f;

struct Junction {
    Point position{};
    float radius=0.f;
    std::vector<int> roadIds;
};
struct Network {
    std::vector<Junction> junctions;
    std::size_t connectedPairs=0;
    std::size_t testedSegments=0;
    std::size_t overflow=0;
};
struct SnapResult {
    bool snapped=false;
    int targetRoad=0;
    Point position{};
};

inline Point closestPoint(Point p,Point a,Point b) noexcept {
    const float dx=b.x-a.x,dz=b.z-a.z;
    const float length2=dx*dx+dz*dz;
    if(length2<1.e-8f)return a;
    const float t=std::max(0.f,std::min(1.f,
        ((p.x-a.x)*dx+(p.z-a.z)*dz)/length2));
    return {a.x+dx*t,a.z+dz*t};
}

inline SnapResult snapEndpoint(Road&road,bool first,
    const std::vector<Road>&all,float halfWorld,
    float maxDistance=kSnapDistance) {
    SnapResult result;
    if(road.points.size()<2||!std::isfinite(halfWorld)||
       !std::isfinite(maxDistance)||maxDistance<=0.f)return result;
    const std::size_t i=first?0:road.points.size()-1;
    const std::size_t neighbor=first?1:road.points.size()-2;
    const Point source=road.points[i];
    float bestDistance=maxDistance*maxDistance;
    for(const Road&other:all){
        if(other.id==road.id||other.points.size()<2)continue;
        const auto samples=sample(other);
        for(std::size_t j=1;j<samples.size();++j){
            const Point candidate=closestPoint(source,samples[j-1],samples[j]);
            const float distance=distanceSquared(source,candidate);
            if(distance>bestDistance||!finitePoint(candidate)||
               std::abs(candidate.x)>halfWorld||std::abs(candidate.z)>halfWorld||
               distanceSquared(road.points[neighbor],candidate)<.25f*.25f)
                continue;
            bestDistance=distance;
            result={true,other.id,candidate};
        }
    }
    if(result.snapped)road.points[i]=result.position;
    return result;
}

inline float cross(Point a,Point b) noexcept {
    return a.x*b.z-a.z*b.x;
}
inline Point subtract(Point a,Point b) noexcept {
    return {a.x-b.x,a.z-b.z};
}
inline bool segmentIntersection(Point a,Point b,Point c,Point d,
                                Point&intersection) noexcept {
    const Point r=subtract(b,a),s=subtract(d,c);
    const float denom=cross(r,s);
    const float scaledEpsilon=1e-6f*
        std::sqrt(std::max(1e-8f,distanceSquared(a,b)*
                                      distanceSquared(c,d)));
    if(std::abs(denom)<=scaledEpsilon)return false; // parallel or collinear
    const Point delta=subtract(c,a);
    const float t=cross(delta,s)/denom;
    const float u=cross(delta,r)/denom;
    if(t<-.00001f||t>1.00001f||u<-.00001f||u>1.00001f)
        return false;
    const float tc=std::max(0.f,std::min(1.f,t));
    intersection={a.x+tc*r.x,a.z+tc*r.z};
    return finitePoint(intersection);
}

inline void includeRoad(Junction&junction,int roadId) {
    if(std::find(junction.roadIds.begin(),junction.roadIds.end(),roadId)
       ==junction.roadIds.end())junction.roadIds.push_back(roadId);
}

inline void addJunction(Network&net,Point point,float widthA,float widthB,
                        int roadA,int roadB) {
    const float radius=.6f*std::max(widthA,widthB)+.6f;
    for(Junction&junction:net.junctions){
        if(distanceSquared(junction.position,point)<
           kJunctionMergeDistance*kJunctionMergeDistance){
            includeRoad(junction,roadA);
            includeRoad(junction,roadB);
            junction.radius=std::max(junction.radius,radius);
            return;
        }
    }
    if(net.junctions.size()>=kMaxJunctions){++net.overflow;return;}
    Junction junction;
    junction.position=point;junction.radius=radius;
    includeRoad(junction,roadA);includeRoad(junction,roadB);
    net.junctions.push_back(std::move(junction));
}

// Fast exact crossings with x-interval sweep and z-interval reject.
// Endpoint-on-segment detection also covers collinear T-joins.
// Geometry of every junction is assembled by the GLES renderer from this graph.
inline Network buildNetwork(const std::vector<Road>&roads){
    struct Segment {
        Point a,b;
        float minX,maxX,minZ,maxZ;
        float width;
        int roadId;
    };
    Network net;
    std::vector<Segment> segments;
    for(const Road&road:roads){
        if(road.points.size()<2)continue;
        auto samples=sample(road);
        for(std::size_t i=1;i<samples.size();++i){
            const Point a=samples[i-1],b=samples[i];
            if(!finitePoint(a)||!finitePoint(b)||
               distanceSquared(a,b)<1e-6f)continue;
            segments.push_back({a,b,std::min(a.x,b.x),std::max(a.x,b.x),
                                 std::min(a.z,b.z),std::max(a.z,b.z),
                                 road.width,road.id});
        }
    }
    std::sort(segments.begin(),segments.end(),
        [](const Segment&a,const Segment&b){return a.minX<b.minX;});
    for(std::size_t i=0;i<segments.size();++i){
        const Segment&a=segments[i];
        for(std::size_t j=i+1;j<segments.size();++j){
            const Segment&b=segments[j];
            if(b.minX>a.maxX+.001f)break;
            if(a.roadId==b.roadId||
               a.maxZ<b.minZ-.001f||b.maxZ<a.minZ-.001f)continue;
            ++net.testedSegments;
            Point intersection;
            if(segmentIntersection(a.a,a.b,b.a,b.b,intersection))
                addJunction(net,intersection,a.width,b.width,a.roadId,b.roadId);
        }
    }
    // Exact endpoints along another segment can be collinear, so the
    // nonparallel intersection test alone would miss these joins.
    for(const Road&road:roads){
        if(road.points.size()<2)continue;
        for(const Point end:{road.points.front(),road.points.back()}){
            for(const Segment&seg:segments){
                if(seg.roadId==road.id)continue;
                if(end.x<seg.minX-.15f||end.x>seg.maxX+.15f||
                   end.z<seg.minZ-.15f||end.z>seg.maxZ+.15f)continue;
                const Point projected=closestPoint(end,seg.a,seg.b);
                if(distanceSquared(end,projected)<=.15f*.15f)
                    addJunction(net,projected,road.width,seg.width,
                                road.id,seg.roadId);
            }
        }
    }
    for(Junction&junction:net.junctions){
        std::sort(junction.roadIds.begin(),junction.roadIds.end());
        const std::size_t connected=junction.roadIds.size();
        if(connected>1)net.connectedPairs+=connected*(connected-1)/2;
    }
    return net;
}

}} // namespace oe::roads
