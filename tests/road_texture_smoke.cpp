#include "../android/src/main/cpp/roads/road_topology.h"
#include "../android/src/main/cpp/roads/medieval_surface.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
using namespace oe::roads;
int main(){
 const TerrainHeight terrain=[](double x,double z){return .02*x+.01*z;};
 Network network;
 const std::string texture="tx_0123456789abcdef01234567.png";
 const auto first=network.create("Main cobbled alley",texture,4.,
                                {{-25,0},{25,0}});
 assert(first&&network.find(first)->tileMetres==4.);
 Road road=*network.find(first);
 road.tileMetres=2.;
 assert(validRoad(road)&&network.update(road));
 assert(std::abs(network.find(first)->tileMetres-2.)<1e-8);
 const auto original=*network.find(first);
 for(double invalid:{.01,32.01,std::numeric_limits<double>::infinity(),
                     std::numeric_limits<double>::quiet_NaN()}){
  Road broken=original;broken.tileMetres=invalid;
  assert(!validRoad(broken)&&!network.update(broken));
  assert(std::abs(network.find(first)->tileMetres-2.)<1e-8);
 }
 SurfaceMesh mesh;
 assert(buildSurfaceMesh(*network.find(first),terrain,mesh));
 assert(mesh.triangles.size()>20);
 float maxU=0,maxV=0;
 for(const auto&vertex:mesh.triangles){
  assert(std::isfinite(vertex.u)&&std::isfinite(vertex.v));
  maxU=std::max(maxU,vertex.u);maxV=std::max(maxV,vertex.v);
 }
 // Native GPU conversion: legacy UV units are 0.5 per metre. New per-road
 // scale 2/tileMetres gives actual 1 repeat each tileMetres metres.
 assert(std::abs(maxU*(2./original.tileMetres)-2.)<.01);
 assert(std::abs(maxV*(2./original.tileMetres)-25.)<.01);
 Road invalid=original;invalid.tileMetres=999.;
 assert(!network.replaceAll({invalid})); // atomic failure
 assert(network.all().size()==1);
 assert(network.all().front().material==texture);
 assert(network.replaceAll({original}));
 assert(network.all().front().tileMetres==2.);
 // X intersection: both road settings and materials survive topology
 // baking. Junction fill refers to a real source road material ID.
 const auto crossing=network.create("Crossroad","builtin:dirt",4.,
                                    {{0,-25},{0,25}});
 assert(crossing);
 Road cross=*network.find(crossing);cross.tileMetres=8.;
 assert(network.update(cross));
 RoadTopology graph;
 assert(buildRoadTopology(network.all(),graph,0.));
 assert(graph.junctionCount==1&&graph.roads.size()==2);
 assert(graph.roads[0].tileMetres==2.);
 assert(graph.roads[1].tileMetres==8.);
 assert(graph.roads[0].material==texture);
 MedievalSurface surface;
 assert(buildMedievalSurface(graph,terrain,surface));
 assert(surface.junctions.size()==1);
 assert(!surface.junctions[0].mesh.triangles.empty());
 const std::uint64_t src=surface.junctions[0].materialRoadId;
 assert(src==first||src==crossing);
 const auto stored=network.all();
 Network loaded;
 assert(loaded.replaceAll(stored));
 assert(loaded.find(first)->material==texture);
 assert(loaded.find(first)->tileMetres==2.);
 assert(loaded.find(crossing)->tileMetres==8.);
 std::cout<<"OE_ROAD_TEXTURE_OK: 2/4/8m tile scales, UV continuity, junction source, atomic serialization\n";
}
