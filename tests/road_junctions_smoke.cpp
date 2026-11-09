#include "../android/src/main/cpp/roads/road_junctions.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
using namespace oe::roads;
int main(){
 const Road east={1,"East-West","builtin:cobblestone",8,{{-20,0},{20,0}}};
 const Road north={2,"North-South","builtin:cobblestone",6,{{0,-20},{0,20}}};
 const Road endpoint={3,"End point","builtin:cobblestone",4,{{20,0},{30,15}}};
 std::vector<Junction> joints;
 assert(findJunctions({east,north,endpoint},joints));
 assert(joints.size()==2);
 assert(std::hypot(joints.front().center.x,joints.front().center.z)<1e-6);
 assert(std::abs(joints.front().radiusMetres-4)<1e-6);
 assert(snapEndpoint({east,north}, {19.4,.7},2).x==20);
 assert(snapEndpoint({east,north}, {19.4,.7},.1).x==19.4);
 assert(snapEndpoint({east,north}, {19.4,.7},2,1).x==19.4);
 const TerrainHeight terrain=[](double x,double z){return .05*x+.1*z;};
 SurfaceMesh disc;
 assert(buildJunctionPreview(joints.front(),terrain,disc));
 assert(disc.triangles.size()==48);
 assert(!buildJunctionPreview(joints.front(),
      [](double,double){return std::numeric_limits<double>::quiet_NaN();},disc));
 assert(disc.triangles.size()==48); // error atomic
 Road curve={10,"Bend","builtin:grass",3,{{0,0},{10,0},{10,10}}};
 Road rounded;
 assert(roundCorners(curve,2,rounded));
 assert(rounded.id==curve.id);
 assert(rounded.controlPoints.front().x==0);
 assert(rounded.controlPoints.back().z==10);
 assert(rounded.controlPoints.size()>curve.controlPoints.size());
 assert(validRoad(rounded));
 const auto old=rounded;
 assert(!roundCorners(curve,0,rounded));
 assert(rounded.controlPoints.size()==old.controlPoints.size());
 assert(rounded.controlPoints[1].x==old.controlPoints[1].x);
 assert(!roundCorners(Road{99,"U","builtin:dirt",3,{{0,0},{10,0},{0,0}}},2,rounded));
 std::vector<Road> longRoads;
 Road many=east;
 many.controlPoints.clear();
 for(int i=0;i<1805;i++)many.controlPoints.push_back({double(i),0});
 longRoads.push_back(many);
 assert(!findJunctions(longRoads,joints)); // bounded work
 assert(joints.size()==2);
 std::cout<<"OE_ROAD_JUNCTIONS_OK: intersections, snapping, rounded corners, bounded geometry\n";
}
