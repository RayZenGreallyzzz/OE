#include "road_junctions.h"
#include <algorithm>
#include <cmath>
#include <limits>

namespace oe { namespace roads {
namespace {
constexpr double kEps=1.e-10;
constexpr double kYOffset=0.145;
bool finite(double x){return std::isfinite(x);}
Point sub(Point a,Point b){return {a.x-b.x,a.z-b.z};}
Point add(Point a,Point b){return {a.x+b.x,a.z+b.z};}
Point mul(Point a,double k){return {a.x*k,a.z*k};}
double len(Point p){return std::hypot(p.x,p.z);}
double cross(Point a,Point b){return a.x*b.z-a.z*b.x;}
double dot(Point a,Point b){return a.x*b.x+a.z*b.z;}
struct Segment {
 Point from,to;std::uint64_t id;double halfWidth;
};
bool intersection(const Segment&a,const Segment&b,Point&point){
 const Point r=sub(a.to,a.from),s=sub(b.to,b.from);
 const double denom=cross(r,s);
 if(std::abs(denom)<kEps)return false; // no parallel/collinear coincidence
 const Point diff=sub(b.from,a.from);
 const double t=cross(diff,s)/denom;
 const double u=cross(diff,r)/denom;
 if(t< -1.e-8||t>1.+1.e-8||u< -1.e-8||u>1.+1.e-8)return false;
 point=add(a.from,mul(r,std::max(0.,std::min(1.,t))));
 return finite(point.x)&&finite(point.z);
}
bool validJunction(const Junction& j){
 return finite(j.center.x)&&finite(j.center.z)&&
        std::abs(j.center.x)<=8192.&&std::abs(j.center.z)<=8192.&&
        finite(j.radiusMetres)&&j.radiusMetres>=.125&&j.radiusMetres<=32.&&
        j.roadA!=0&&j.roadB!=0&&j.roadA!=j.roadB;
}
} // namespace

Point snapEndpoint(const std::vector<Road>& roads,Point query,double distance,
                   std::uint64_t excludeRoad) noexcept{
 if(!finite(query.x)||!finite(query.z)||!finite(distance)||
    distance<=0.||distance>32.)return query;
 Point best=query;
 double nearest=distance;
 for(const auto&road:roads){
  if(road.id==excludeRoad||road.controlPoints.empty())continue;
  const Point endpoints[]={road.controlPoints.front(),road.controlPoints.back()};
  for(Point point:endpoints){
   const double d=len(sub(point,query));
   if(d<=nearest){nearest=d;best=point;}
  }
 }
 return best;
}

bool roundCorners(const Road& input,double cutMetres,Road& output,
                  unsigned subdivisions){
 if(!validRoad(input)||!finite(cutMetres)||cutMetres<.25||
    cutMetres>16.||subdivisions<2||subdivisions>8)return false;
 Road result=input;
 result.controlPoints.clear();
 const auto& p=input.controlPoints;
 if(p.size()>kMaxControlPoints/(subdivisions+1))return false;
 result.controlPoints.push_back(p.front());
 for(std::size_t i=1;i+1<p.size();++i){
  const Point in=sub(p[i],p[i-1]),out=sub(p[i+1],p[i]);
  const double a=len(in),b=len(out);
  if(a<.01||b<.01)return false;
  const double cosAngle=dot(in,out)/(a*b);
  if(cosAngle>.997){result.controlPoints.push_back(p[i]);continue;}
  if(cosAngle<-.98)return false; // no cusp loops
  const double cut=std::min({cutMetres,a*.28,b*.28});
  const Point incoming=sub(p[i],mul(in,cut/a));
  const Point outgoing=add(p[i],mul(out,cut/b));
  for(unsigned step=0;step<=subdivisions;++step){
   const double t=double(step)/subdivisions;
   const Point bezier=add(add(mul(incoming,(1-t)*(1-t)),
                               mul(p[i],2*(1-t)*t)),mul(outgoing,t*t));
   result.controlPoints.push_back(bezier);
   if(result.controlPoints.size()>kMaxControlPoints)return false;
  }
 }
 result.controlPoints.push_back(p.back());
 if(!validRoad(result))return false;
 output=std::move(result);
 return true;
}

bool findJunctions(const std::vector<Road>& roads,std::vector<Junction>& output){
 std::vector<Segment> lines;
 for(const auto&road:roads){
  if(!validRoad(road))return false;
  for(std::size_t i=1;i<road.controlPoints.size();++i){
   if(lines.size()>=kMaxJunctionSegments)return false;
   lines.push_back({road.controlPoints[i-1],road.controlPoints[i],
                    road.id,road.widthMetres*.5});
  }
 }
 std::vector<Junction> found;
 for(std::size_t i=0;i<lines.size();++i){
  const auto&a=lines[i];
  for(std::size_t j=i+1;j<lines.size();++j){
   const auto&b=lines[j];
   if(a.id==b.id)continue;
   // Cheap AABB reject before precision math; expensive only for nearby roads.
   if(std::max(a.from.x,a.to.x)<std::min(b.from.x,b.to.x)||
      std::max(b.from.x,b.to.x)<std::min(a.from.x,a.to.x)||
      std::max(a.from.z,a.to.z)<std::min(b.from.z,b.to.z)||
      std::max(b.from.z,b.to.z)<std::min(a.from.z,a.to.z))continue;
   Point hit;
   if(!intersection(a,b,hit))continue;
   const double r=std::max(a.halfWidth,b.halfWidth);
   bool duplicate=false;
   for(const auto&old:found){
    if(len(sub(old.center,hit))<.5&&
       ((old.roadA==a.id&&old.roadB==b.id)||
        (old.roadA==b.id&&old.roadB==a.id))){duplicate=true;break;}
   }
   if(duplicate)continue;
   if(found.size()>=kMaxJunctions)return false;
   found.push_back({hit,r,a.id,b.id});
  }
 }
 output=std::move(found);
 return true;
}

bool buildJunctionPreview(const Junction& j,const TerrainHeight& height,
                          SurfaceMesh& output,unsigned sides){
 if(!validJunction(j)||!height||sides<8||sides>32)return false;
 SurfaceMesh mesh;
 mesh.triangles.reserve(sides*3);
 const double centerHeight=height(j.center.x,j.center.z);
 if(!finite(centerHeight))return false;
 const Vertex center={float(j.center.x),float(centerHeight+kYOffset),
                      float(j.center.z),.5f,.5f};
 constexpr double pi=3.14159265358979323846;
 for(unsigned i=0;i<sides;++i){
  const double a=2*pi*i/sides,b=2*pi*(i+1)/sides;
  const Point p1{j.center.x+j.radiusMetres*std::cos(a),
                 j.center.z+j.radiusMetres*std::sin(a)};
  const Point p2{j.center.x+j.radiusMetres*std::cos(b),
                 j.center.z+j.radiusMetres*std::sin(b)};
  const double y1=height(p1.x,p1.z),y2=height(p2.x,p2.z);
  if(!finite(y1)||!finite(y2))return false;
  const Vertex v1={float(p1.x),float(y1+kYOffset),float(p1.z),
                   float(.5+.5*std::cos(a)),float(.5+.5*std::sin(a))};
  const Vertex v2={float(p2.x),float(y2+kYOffset),float(p2.z),
                   float(.5+.5*std::cos(b)),float(.5+.5*std::sin(b))};
  // CCW, Y-up: fan in XZ plane has inverted winding.
  mesh.triangles.push_back(center);
  mesh.triangles.push_back(v2);
  mesh.triangles.push_back(v1);
 }
 output=std::move(mesh);
 return true;
}

}} // namespace oe::roads
