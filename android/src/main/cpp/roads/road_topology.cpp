#include "road_topology.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <map>
#include <set>
#include <utility>

namespace oe { namespace roads {
namespace {
constexpr double kMinimumSegment=.01;
constexpr double kNumericalEpsilon=1.e-10;
constexpr double kCloseToEndpoint=.005;
constexpr std::int64_t kGridOffset=820000;
constexpr std::int64_t kGridMax=2097152;

struct Segment {
 std::size_t road=0,part=0;
 Point a,b;
};
struct Cut {double t;Point pos;};
using Cuts=std::vector<std::vector<std::vector<Cut>>>;
Point sub(Point a,Point b){return {a.x-b.x,a.z-b.z};}
Point add(Point a,Point b){return {a.x+b.x,a.z+b.z};}
Point mul(Point a,double k){return {a.x*k,a.z*k};}
double dot(Point a,Point b){return a.x*b.x+a.z*b.z;}
double cross(Point a,Point b){return a.x*b.z-a.z*b.x;}
double length(Point a){return std::hypot(a.x,a.z);}
double dist(Point a,Point b){return length(sub(a,b));}
bool finite(Point p){return std::isfinite(p.x)&&std::isfinite(p.z);}
double clamp01(double x){return std::max(0.,std::min(1.,x));}
bool boxesTouch(const Segment&a,const Segment&b){
 return std::max(a.a.x,a.b.x)+kNumericalEpsilon>=std::min(b.a.x,b.b.x)&&
        std::max(b.a.x,b.b.x)+kNumericalEpsilon>=std::min(a.a.x,a.b.x)&&
        std::max(a.a.z,a.b.z)+kNumericalEpsilon>=std::min(b.a.z,b.b.z)&&
        std::max(b.a.z,b.b.z)+kNumericalEpsilon>=std::min(a.a.z,a.b.z);
}
std::uint64_t nodeKey(Point point){
 if(!finite(point))return 0;
 const auto x=static_cast<std::int64_t>(std::llround(point.x*100.))+kGridOffset;
 const auto z=static_cast<std::int64_t>(std::llround(point.z*100.))+kGridOffset;
 if(x<0||z<0||x>=kGridMax||z>=kGridMax)return 0;
 return 1ULL+(static_cast<std::uint64_t>(x)<<21)+static_cast<std::uint64_t>(z);
}
void addCut(Cuts& cuts,std::size_t road,std::size_t segment,
            double t,Point point){
 cuts[road][segment].push_back({clamp01(t),point});
}
bool validIdList(const std::vector<Road>& roads){
 if(roads.size()>kMaxRoads)return false;
 std::set<std::uint64_t> keys;
 for(const auto&road:roads)
  if(!validRoad(road)||!keys.insert(road.id).second)return false;
 return true;
}
bool snapEndpoints(std::vector<Road>& roads,double radius){
 if(radius<=0)return true;
 const auto original=roads;
 for(std::size_t roadIndex=0;roadIndex<roads.size();++roadIndex){
  for(int endpoint=0;endpoint<2;++endpoint){
   const auto pos=endpoint==0?std::size_t(0):roads[roadIndex].controlPoints.size()-1;
   const Point originalPoint=original[roadIndex].controlPoints[pos];
   Point nearest=originalPoint;
   double best=radius;
   bool found=false;
   // Prefer exact endpoints on ties, then projection to a centreline segment.
   for(std::size_t other=0;other<original.size();++other){
    if(roadIndex==other)continue;
    const auto& points=original[other].controlPoints;
    for(std::size_t i=1;i<points.size();++i){
     const Point a=points[i-1],b=points[i],delta=sub(b,a);
     const double sq=dot(delta,delta);
     if(sq<kMinimumSegment*kMinimumSegment)continue;
     const double t=clamp01(dot(sub(originalPoint,a),delta)/sq);
     const Point projected=add(a,mul(delta,t));
     const double separation=dist(projected,originalPoint);
     // A nearby parallel alley must NOT merge even if its endpoints line up.
     // Permit collinear end-to-end joining but not a sideways lane jump.
     if(separation>.20){
      const auto& own=original[roadIndex].controlPoints;
      const Point inward=endpoint==0?sub(own[1],originalPoint):
                                 sub(own[own.size()-2],originalPoint);
      const double alignment=std::abs(dot(inward,delta))/(length(inward)*length(delta));
      const double lateral=std::abs(cross(sub(projected,originalPoint),delta))/length(delta);
      if(alignment>.94&&lateral>.20)continue;
     }
     if(separation<best-(found?1.e-8:0.)){
      nearest=projected;best=separation;found=true;
     }
    }
   }
   if(found)roads[roadIndex].controlPoints[pos]=nearest;
  }
 }
 for(const auto&road:roads)if(!validRoad(road))return false;
 return true;
}
bool intersectSegments(const Segment&a,const Segment&b,
                       double& t,double& u,Point& location,
                       bool& overlap){
 overlap=false;
 const Point r=sub(a.b,a.a),s=sub(b.b,b.a),delta=sub(b.a,a.a);
 const double denominator=cross(r,s);
 const double tolerance=kNumericalEpsilon*length(r)*length(s);
 if(std::abs(denominator)<=tolerance){
  if(std::abs(cross(delta,r))<=kNumericalEpsilon*length(r)*std::max(1.,length(delta))){
   const double l2=dot(r,r);
   if(l2<=0)return false;
   const double start=dot(sub(b.a,a.a),r)/l2;
   const double finish=dot(sub(b.b,a.a),r)/l2;
   const double begin=std::max(0.,std::min(start,finish));
   const double end=std::min(1.,std::max(start,finish));
   overlap=(end-begin)*length(r)>kMinimumSegment;
   if(overlap)return false;
   // Collinear point touch. Represent by a shared node at the endpoint.
   if(end>=begin-kNumericalEpsilon){
    t=clamp01((begin+end)*.5);
    location=add(a.a,mul(r,t));
    u=clamp01(dot(sub(location,b.a),s)/dot(s,s));
    return true;
   }
  }
  return false;
 }
 t=cross(delta,s)/denominator;
 u=cross(delta,r)/denominator;
 if(t< -1.e-9||t>1.+1.e-9||u< -1.e-9||u>1.+1.e-9)return false;
 t=clamp01(t);u=clamp01(u);
 location=add(a.a,mul(r,t));
 // Exactly snap to the canonical authored endpoint within <=0.5cm.
 if(t*length(r)<=kCloseToEndpoint){t=0;location=a.a;}
 else if((1-t)*length(r)<=kCloseToEndpoint){t=1;location=a.b;}
 else if(u*length(s)<=kCloseToEndpoint){u=0;location=b.a;}
 else if((1-u)*length(s)<=kCloseToEndpoint){u=1;location=b.b;}
 return true;
}
} // namespace

bool buildRoadTopology(const std::vector<Road>& input,RoadTopology& output,
                       double endpointSnapMetres){
 if(!std::isfinite(endpointSnapMetres)||endpointSnapMetres<0||
    endpointSnapMetres>kTopologySnapRadius||!validIdList(input))return false;
 RoadTopology draft;
 draft.roads=input;
 if(!snapEndpoints(draft.roads,endpointSnapMetres))return false;
 std::vector<Segment> segments;
 Cuts cuts(draft.roads.size());
 for(std::size_t ri=0;ri<draft.roads.size();++ri){
  const auto& points=draft.roads[ri].controlPoints;
  cuts[ri].resize(points.size()-1);
  for(std::size_t i=1;i<points.size();++i){
   if(segments.size()>=kMaxTopologySegments)return false;
   segments.push_back({ri,i-1,points[i-1],points[i]});
  }
 }
 std::set<std::pair<std::uint64_t,std::uint64_t>> intersections;
 for(std::size_t i=0;i<segments.size();++i){
  const Segment&a=segments[i];
  for(std::size_t j=i+1;j<segments.size();++j){
   const Segment&b=segments[j];
   if(a.road==b.road||!boxesTouch(a,b))continue;
   double t=0,u=0;Point point{};bool overlap=false;
   const bool hit=intersectSegments(a,b,t,u,point,overlap);
   if(overlap)return false;
   if(!hit||!finite(point))continue;
   const auto idA=draft.roads[a.road].id,idB=draft.roads[b.road].id;
   const auto pair=std::minmax(idA,idB);
   const std::uint64_t key=nodeKey(point);
   if(!key)return false;
   // Every road-pair crossing is baked; a crossing can share other road pairs.
   // Checking this bound on unique pair *and location* happens below.
   intersections.insert(pair);
   addCut(cuts,a.road,a.part,t,point);
   addCut(cuts,b.road,b.part,u,point);
  }
 }
 // Rebuild each polyline with the same point on both intersecting roads.
 for(std::size_t ri=0;ri<draft.roads.size();++ri){
  auto&road=draft.roads[ri];
  const auto source=road.controlPoints;
  std::vector<Point> result;
  result.reserve(std::min<std::size_t>(kMaxControlPoints,source.size()+64));
  result.push_back(source.front());
  for(std::size_t part=0;part+1<source.size();++part){
   auto& lineCuts=cuts[ri][part];
   std::sort(lineCuts.begin(),lineCuts.end(),[](const Cut&a,const Cut&b){
    return a.t<b.t;
   });
   for(const auto& cut:lineCuts){
    if(dist(cut.pos,source[part])<kMinimumSegment||
       dist(cut.pos,source[part+1])<kMinimumSegment)continue;
    if(dist(cut.pos,result.back())<kMinimumSegment)continue;
    result.push_back(cut.pos);
    if(result.size()>kMaxControlPoints)return false;
   }
   if(dist(source[part+1],result.back())>=kMinimumSegment)
    result.push_back(source[part+1]);
   else if(part+2<source.size())return false;
   if(result.size()>kMaxControlPoints)return false;
  }
  road.controlPoints=std::move(result);
  if(!validRoad(road))return false;
 }
 std::map<std::uint64_t,std::size_t> keyIndex;
 std::vector<std::set<std::uint64_t>> incidentRoads;
 for(const auto& road:draft.roads){
  std::uint64_t previous=0;
  for(const Point position:road.controlPoints){
   const std::uint64_t key=nodeKey(position);
   if(!key)return false;
   const auto it=keyIndex.find(key);
   std::size_t index=0;
   if(it==keyIndex.end()){
    if(draft.nodes.size()>=kMaxTopologyNodes)return false;
    index=draft.nodes.size();
    draft.nodes.push_back({key,position,{},0});
    incidentRoads.emplace_back();
    keyIndex.emplace(key,index);
   }else{
    index=it->second;
    if(dist(draft.nodes[index].position,position)>.0142)return false;
   }
   incidentRoads[index].insert(road.id);
   if(previous&&previous!=key){
    if(draft.edges.size()>=kMaxTopologyEdges)return false;
    draft.edges.push_back({road.id,previous,key});
    ++draft.nodes[index].degree;
    ++draft.nodes[keyIndex[previous]].degree;
   }else if(previous==key)return false;
   previous=key;
  }
 }
 for(std::size_t i=0;i<draft.nodes.size();++i){
  const auto& connected=incidentRoads[i];
  draft.nodes[i].connectedRoads.assign(connected.begin(),connected.end());
  if(connected.size()>1)++draft.junctionCount;
 }
 if(draft.junctionCount>kMaxTopologyCrossings)return false;
 draft.crossingCount=draft.junctionCount;
 output=std::move(draft);
 return true;
}
}} // namespace oe::roads
