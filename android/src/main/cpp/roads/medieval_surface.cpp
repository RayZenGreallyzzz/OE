#include "medieval_surface.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <map>
#include <utility>
#include <vector>

namespace oe { namespace roads {
namespace {
constexpr double kLift=.14; // same road-plane offset as R1-R5, no raised curbs
constexpr double kPi=3.14159265358979323846;
constexpr double kMinRemaining=.04;
constexpr double kMetresToUv=0.5;
constexpr std::int64_t kGridOffset=820000;
constexpr std::uint64_t kGridMax=2097152ULL;
struct Ray {
 std::uint64_t road=0, neighbour=0;
 Point direction{};
 double halfWidth=0,angle=0;
};
struct JunctionStyle {Point center;double radius=0;std::vector<Ray> rays;};
using JunctionLookup=std::map<std::uint64_t,JunctionStyle>;
Point add(Point a,Point b){return {a.x+b.x,a.z+b.z};}
Point sub(Point a,Point b){return {a.x-b.x,a.z-b.z};}
Point mul(Point a,double s){return {a.x*s,a.z*s};}
double dot(Point a,Point b){return a.x*b.x+a.z*b.z;}
double cross(Point a,Point b){return a.x*b.z-a.z*b.x;}
double length(Point a){return std::hypot(a.x,a.z);}
double distance(Point a,Point b){return length(sub(a,b));}
Point normal(Point d){return {-d.z,d.x};}
Point normalized(Point p){const double n=length(p);return n>1.e-10?mul(p,1./n):Point{};}
bool finite(Point p){return std::isfinite(p.x)&&std::isfinite(p.z);}
std::uint64_t key(Point p){
 if(!finite(p))return 0;
 const auto x=static_cast<std::int64_t>(std::llround(p.x*100.))+kGridOffset;
 const auto z=static_cast<std::int64_t>(std::llround(p.z*100.))+kGridOffset;
 if(x<0||z<0||x>=static_cast<std::int64_t>(kGridMax)||
    z>=static_cast<std::int64_t>(kGridMax))return 0;
 return 1ULL+(static_cast<std::uint64_t>(x)<<21)+static_cast<std::uint64_t>(z);
}
const Road* byId(const RoadTopology& top,std::uint64_t id){
 for(const auto&road:top.roads)if(road.id==id)return &road;
 return nullptr;
}
bool vertex(Point p,double u,double v,const TerrainHeight& height,Vertex& out){
 if(!finite(p)||std::abs(p.x)>8220||std::abs(p.z)>8220)return false;
 const double y=height(p.x,p.z);
 if(!std::isfinite(y)||std::abs(y)>10000.)return false;
 out={static_cast<float>(p.x),static_cast<float>(y+kLift),
      static_cast<float>(p.z),static_cast<float>(u),static_cast<float>(v)};
 return true;
}
bool makeJunctions(const RoadTopology& top,JunctionLookup& lookup){
 std::map<std::uint64_t,Point> positions;
 for(const auto&node:top.nodes)positions.emplace(node.id,node.position);
 for(const auto&node:top.nodes){
  if(node.connectedRoads.size()<2)continue;
  if(lookup.size()>=kMaxTopologyCrossings)return false;
  JunctionStyle joint;joint.center=node.position;
  double minimumEdge=std::numeric_limits<double>::infinity();
  double biggestHalf=0.;
  for(const auto&edge:top.edges){
   if(edge.from!=node.id&&edge.to!=node.id)continue;
   const auto other=edge.from==node.id?edge.to:edge.from;
   const auto pt=positions.find(other);
   const auto*road=byId(top,edge.roadId);
   if(pt==positions.end()||!road)return false;
   const Point diff=sub(pt->second,node.position);
   const double len=length(diff);
   if(len<.01)return false;
   minimumEdge=std::min(minimumEdge,len);
   biggestHalf=std::max(biggestHalf,road->widthMetres*.5);
   const Point dir=mul(diff,1./len);
   double angle=std::atan2(dir.z,dir.x);
   if(angle<0)angle+=2.*kPi;
   joint.rays.push_back({edge.roadId,other,dir,
                        road->widthMetres*.5,angle});
  }
  if(joint.rays.size()<2||joint.rays.size()>16)return false;
  std::sort(joint.rays.begin(),joint.rays.end(),
            [](const Ray&a,const Ray&b){return a.angle<b.angle;});
  double radius=biggestHalf*1.20;
  for(std::size_t i=0;i<joint.rays.size();++i){
   const auto&a=joint.rays[i],&b=joint.rays[(i+1)%joint.rays.size()];
   double gap=b.angle-a.angle;
   if(i+1==joint.rays.size())gap+=2.*kPi;
   if(gap<.12)return false; // almost-parallel/overlapping outgoing arms
   const double tangent=std::tan(gap*.5);
   // For >180° the two caps have ample room; no acute-corner inflation.
   if(gap<kPi-.001&&tangent>.0001)
    radius=std::max(radius,1.25*std::max(a.halfWidth,b.halfWidth)/tangent);
  }
  if(radius>24.||radius>.44*minimumEdge)return false;
  joint.radius=radius;
  lookup.emplace(node.id,std::move(joint));
 }
 // Shared edge between two junctions must retain a positive central section.
 for(const auto&edge:top.edges){
  const auto a=positions.find(edge.from),b=positions.find(edge.to);
  if(a==positions.end()||b==positions.end())return false;
  const auto ia=lookup.find(edge.from),ib=lookup.find(edge.to);
  const double start=ia==lookup.end()?0.:ia->second.radius;
  const double end=ib==lookup.end()?0.:ib->second.radius;
  if(distance(a->second,b->second)-start-end<kMinRemaining)return false;
 }
 return true;
}
Point miter(const Road&road,std::size_t point,double half){
 const auto& p=road.controlPoints;
 if(point==0)return mul(normal(normalized(sub(p[1],p[0]))),half);
 if(point+1==p.size())return mul(normal(normalized(sub(p[point],p[point-1]))),half);
 const Point nA=normal(normalized(sub(p[point],p[point-1])));
 const Point nB=normal(normalized(sub(p[point+1],p[point])));
 const Point blend=normalized(add(nA,nB));
 if(length(blend)<.0001)return mul(nB,half);
 const double divisor=std::abs(dot(blend,nB));
 const double amount=std::min(half*1.75,half/std::max(.20,divisor));
 return mul(blend,amount);
}
bool addSegment(const Road& road,std::size_t sectionIndex,
                const JunctionLookup& joins,const TerrainHeight& height,
                double step,SurfaceMesh& destination){
 const auto& p=road.controlPoints;
 const Point a=p[sectionIndex],b=p[sectionIndex+1],dir=normalized(sub(b,a));
 const double total=distance(a,b),half=road.widthMetres*.5;
 const auto start=joins.find(key(a)),end=joins.find(key(b));
 const double trimA=start!=joins.end()?start->second.radius:0.;
 const double trimB=end!=joins.end()?end->second.radius:0.;
 const double actual=total-trimA-trimB;
 if(actual<kMinRemaining)return false;
 const std::size_t segments=static_cast<std::size_t>(std::ceil(actual/step));
 if(!segments||segments>kMaxMeshSamples||
    destination.triangles.size()/6+segments>kMaxMeshSamples)return false;
 double travelled=0.;
 for(std::size_t i=0;i<sectionIndex;++i)
  travelled+=distance(p[i],p[i+1]);
 Vertex lastLeft{},lastRight{};
 for(std::size_t i=0;i<=segments;++i){
  const double along=trimA+actual*(static_cast<double>(i)/segments);
  const Point middle=i==0&&start!=joins.end()?
                         add(a,mul(dir,trimA)):
                     i==segments&&end!=joins.end()?
                         sub(b,mul(dir,trimB)):
                         add(a,mul(dir,along));
  const Point side=(i==0&&start==joins.end())?miter(road,sectionIndex,half):
                   (i==segments&&end==joins.end())?miter(road,sectionIndex+1,half):
                                                    mul(normal(dir),half);
  Vertex left{},right{};
  // miter() already includes width; the straight side does too.
  if(!vertex(add(middle,side),0.,(travelled+along)*kMetresToUv,height,left)||
     !vertex(sub(middle,side),road.widthMetres*kMetresToUv,
             (travelled+along)*kMetresToUv,height,right))return false;
  if(i){
   destination.triangles.insert(destination.triangles.end(),
        {lastLeft,left,lastRight,left,right,lastRight});
  }
  lastLeft=left;lastRight=right;
 }
 return true;
}
bool addJunction(const JunctionStyle&joint,std::uint64_t materialId,
                 const TerrainHeight&height,MedievalSurface& result){
 std::vector<Vertex> boundary;
 boundary.reserve(joint.rays.size()*2);
 for(const auto& ray:joint.rays){
  const Point cap=add(joint.center,mul(ray.direction,joint.radius));
  const Point side=mul(normal(ray.direction),ray.halfWidth);
  Vertex minus{},plus{};
  if(!vertex(sub(cap,side),0.,0.,height,minus)||
     !vertex(add(cap,side),1.,1.,height,plus))return false;
  boundary.push_back(minus);
  boundary.push_back(plus);
 }
 Vertex pivot{};
 if(!vertex(joint.center,.5,.5,height,pivot))return false;
 MedievalSurfacePart patch;patch.roadId=0;patch.materialRoadId=materialId;
 for(std::size_t i=0;i<boundary.size();++i){
  const Vertex&a=boundary[i],&b=boundary[(i+1)%boundary.size()];
  const double signedArea=cross(
   {a.x-pivot.x,a.z-pivot.z},{b.x-pivot.x,b.z-pivot.z});
  // Valid inward-oriented, non-self-intersecting star polygon required.
  if(signedArea<.0001)return false;
  patch.mesh.triangles.insert(patch.mesh.triangles.end(),{pivot,b,a});
 }
 result.triangles+=patch.mesh.triangles.size()/3;
 result.junctions.push_back(std::move(patch));
 return true;
}
} // namespace

bool buildMedievalSurface(const RoadTopology&topology,const TerrainHeight&height,
                          MedievalSurface&output,double step){
 if(!height||!std::isfinite(step)||step<.25||step>16.||
    topology.roads.size()>kMaxRoads||
    topology.nodes.size()>kMaxTopologyNodes||
    topology.edges.size()>kMaxTopologyEdges)return false;
 MedievalSurface result;
 JunctionLookup junctions;
 if(!makeJunctions(topology,junctions))return false;
 result.roads.reserve(topology.roads.size());
 result.junctions.reserve(junctions.size());
 for(const auto&road:topology.roads){
  if(!validRoad(road))return false;
  MedievalSurfacePart part;part.roadId=road.id;part.materialRoadId=road.id;
  const auto& points=road.controlPoints;
  bool needsCuts=false;
  for(const Point p:points)if(junctions.find(key(p))!=junctions.end()){
   needsCuts=true;break;
  }
  if(!needsCuts){
   // Keep the original road ribbon for isolated winding medieval paths.
   if(!buildSurfaceMesh(road,height,part.mesh,step))return false;
  }else{
   for(std::size_t i=0;i+1<points.size();++i)
    if(!addSegment(road,i,junctions,height,step,part.mesh))return false;
  }
  result.triangles+=part.mesh.triangles.size()/3;
  result.roads.push_back(std::move(part));
 }
 for(const auto&node:junctions){
  std::uint64_t material=std::numeric_limits<std::uint64_t>::max();
  for(const auto&ray:node.second.rays)material=std::min(material,ray.road);
  if(!addJunction(node.second,material,height,result))return false;
  ++result.stitchedNodes;
 }
 output=std::move(result);
 return true;
}
}} // namespace oe::roads
