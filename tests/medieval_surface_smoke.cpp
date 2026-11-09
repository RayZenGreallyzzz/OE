#include "../android/src/main/cpp/roads/medieval_surface.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <set>
#include <utility>
#include <vector>
using namespace oe::roads;
static Road lane(std::uint64_t id,double width,std::initializer_list<Point> points){
 return {id,"Medieval street","builtin:cobblestone",width,std::vector<Point>(points)};
}
static bool approximately(const Vertex&a,const Vertex&b){
 return std::abs(a.x-b.x)<.00005f&&std::abs(a.y-b.y)<.00005f&&
        std::abs(a.z-b.z)<.00005f;
}
static int matchingJunctionBoundaryVertices(const MedievalSurface&surface){
 int matches=0;
 if(surface.junctions.empty())return 0;
 const auto&tri=surface.junctions.front().mesh.triangles;
 for(std::size_t i=0;i<tri.size();i+=3){
  const Vertex&cap=tri[i+2]; // pivot,next,current; all current are cap points
  bool found=false;
  for(const auto&road:surface.roads){
   for(const auto&v:road.mesh.triangles)if(approximately(v,cap)){
    found=true;break;
   }
   if(found)break;
  }
  if(found)++matches;
 }
 return matches;
}
int main(){
 const TerrainHeight rolling=[](double x,double z){return .04*x+.02*z;};
 RoadTopology topology;
 assert(buildRoadTopology({lane(11,6,{{-24,0},{24,0}}),
                           lane(22,6,{{0,-24},{0,24}})},topology));
 MedievalSurface surface;
 assert(buildMedievalSurface(topology,rolling,surface));
 assert(surface.roads.size()==2&&surface.junctions.size()==1);
 assert(surface.stitchedNodes==1);
 assert(surface.junctions[0].mesh.triangles.size()==24); // 8 edges, 8 triangles
 assert(matchingJunctionBoundaryVertices(surface)==8);
 assert(surface.triangles>surface.junctions[0].mesh.triangles.size()/3);
 for(const auto&part:surface.roads)for(const auto&v:part.mesh.triangles)
  assert(std::abs(v.y-(rolling(v.x,v.z)+.14))<1.e-4);
 for(const auto&part:surface.junctions){
  for(std::size_t i=0;i<part.mesh.triangles.size();i+=3){
   const auto&a=part.mesh.triangles[i],&b=part.mesh.triangles[i+1],
              &c=part.mesh.triangles[i+2];
   const float signedXZ=(b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x);
   assert(signedXZ<-.00001f); // upward normal
  }
 }
 // A T junction has a seamless hexagonal 3-cap polygon.
 RoadTopology tee;
 assert(buildRoadTopology({lane(31,4,{{-18,0},{18,0}}),
                           lane(32,4,{{0,18},{0,.5}})},tee));
 MedievalSurface tmesh;
 assert(buildMedievalSurface(tee,rolling,tmesh));
 assert(tmesh.stitchedNodes==1&&tmesh.junctions[0].mesh.triangles.size()==18);
 assert(matchingJunctionBoundaryVertices(tmesh)==6);
 // Three winding paths form a Y junction.
 RoadTopology y;
 assert(buildRoadTopology({lane(41,3,{{-18,-18},{0,0}}),
                           lane(42,3,{{18,-18},{0,0}}),
                           lane(43,3,{{0,0},{0,20}})},y));
 MedievalSurface ymesh;
 assert(buildMedievalSurface(y,rolling,ymesh));
 assert(ymesh.stitchedNodes==1&&matchingJunctionBoundaryVertices(ymesh)==6);
 // Simple winding road without junctions must retain original R5 appearance.
 RoadTopology single;
 assert(buildRoadTopology({lane(50,3,{{0,0},{20,0},{20,10}})},single));
 MedievalSurface simple;
 SurfaceMesh prior;
 assert(buildMedievalSurface(single,rolling,simple));
 assert(buildSurfaceMesh(single.roads.front(),rolling,prior));
 assert(simple.stitchedNodes==0&&simple.junctions.empty());
 assert(simple.roads.front().mesh.triangles.size()==prior.triangles.size());
 // An X with very short approach cannot create a patched hole.
 RoadTopology tooShort;
 assert(buildRoadTopology({lane(60,8,{{-3,0},{3,0}}),
                           lane(61,8,{{0,-3},{0,3}})},tooShort));
 const auto before=surface;
 assert(!buildMedievalSurface(tooShort,rolling,surface));
 assert(surface.roads.size()==before.roads.size()); // atomic failure
 assert(surface.triangles==before.triangles);
 assert(!buildMedievalSurface(topology,
        [](double,double){return std::numeric_limits<double>::quiet_NaN();},
        surface));
 assert(surface.triangles==before.triangles);
 assert(!buildMedievalSurface(topology,rolling,surface,.1));
 assert(surface.triangles==before.triangles);
 std::cout<<"OE_MEDIEVAL_SURFACES_OK: no gaps at X/T/Y, shared caps, terrain, atomic fallback\n";
}
