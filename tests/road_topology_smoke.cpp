#include "../android/src/main/cpp/roads/road_topology.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <vector>
using namespace oe::roads;
static Road road(std::uint64_t id,std::initializer_list<Point> p){
 return {id,"Stone lane","builtin:cobblestone",4,std::vector<Point>(p)};
}
static const RoadNode* junction(const RoadTopology&topo,double x,double z){
 for(const auto&node:topo.nodes)
  if(node.connectedRoads.size()>1&&std::hypot(node.position.x-x,node.position.z-z)<.02)
   return &node;
 return nullptr;
}
int main(){
 RoadTopology topo;
 // X intersection splits both 2-point centerlines into linked vertices.
 assert(buildRoadTopology({road(11,{{-10,0},{10,0}}),
                          road(22,{{0,-10},{0,10}})},topo));
 assert(topo.roads.size()==2&&topo.junctionCount==1);
 assert(topo.roads[0].controlPoints.size()==3);
 assert(topo.roads[1].controlPoints.size()==3);
 assert(topo.edges.size()==4);
 const auto*cross=junction(topo,0,0);
 assert(cross&&cross->degree==4&&cross->connectedRoads.size()==2);
 const auto stableKey=cross->id;
 RoadTopology roundtrip;
 assert(buildRoadTopology(topo.roads,roundtrip,0.));
 assert(junction(roundtrip,0,0)->id==stableKey);
 assert(roundtrip.roads[0].controlPoints.size()==3); // idempotent
 assert(roundtrip.roads[0].id==11&&roundtrip.roads[1].id==22);
 assert(roundtrip.roads[0].material=="builtin:cobblestone");
 // T junction: one existing segment must split; endpoint remains an endpoint.
 RoadTopology tee;
 assert(buildRoadTopology({road(30,{{-10,0},{10,0}}),
                          road(31,{{0,8},{0,.8}})},tee));
 auto joint=junction(tee,0,0);
 assert(joint&&joint->degree==3&&tee.roads[0].controlPoints.size()==3);
 assert(std::abs(tee.roads[1].controlPoints.back().z)<1.e-6);
 assert(tee.edges.size()==3);
 // Y: three distinct streets meet at common endpoint.
 RoadTopology y;
 assert(buildRoadTopology({road(1,{{-10,-10},{0,0}}),
                          road(2,{{10,-10},{0,0}}),
                          road(3,{{0,0},{0,10}})},y));
 assert(y.junctionCount==1);
 assert(junction(y,0,0)->degree==3);
 // Snapping farther than 2 metres forbidden. User remains in control.
 RoadTopology separate;
 assert(buildRoadTopology({road(5,{{-10,0},{10,0}}),
                          road(6,{{0,10},{0,2.1}})},separate));
 assert(separate.junctionCount==0);
 // Two narrow parallel alleys within two metres must remain separate.
 RoadTopology alleys;
 assert(buildRoadTopology({road(14,{{-10,0},{10,0}}),
                          road(15,{{-10,1.5},{10,1.5}})},alleys));
 assert(alleys.junctionCount==0&&alleys.edges.size()==2);
 assert(std::abs(alleys.roads[1].controlPoints.front().z-1.5)<1e-6);
 // Shared point exact (no false duplicate edges).
 RoadTopology endpoint;
 assert(buildRoadTopology({road(7,{{0,0},{5,0}}),
                          road(8,{{0,0},{0,5}})},endpoint));
 assert(junction(endpoint,0,0)->degree==2);
 // Collinear overlapping roads must not pretend to connect.
 RoadTopology retained=topo;
 assert(!buildRoadTopology({road(9,{{-10,0},{10,0}}),
                           road(10,{{0,0},{12,0}})},topo));
 assert(topo.roads.size()==retained.roads.size());
 assert(junction(topo,0,0)->id==stableKey);
 assert(!buildRoadTopology({road(11,{{0,0},{1,0}}),
                           road(11,{{0,1},{1,1}})},topo));
 assert(!buildRoadTopology({road(3,{{0,0},{10,0}})},topo,-1));
 assert(!buildRoadTopology({road(3,{{0,0},{10,0}})},topo,9));
 Road huge=road(111,{{0,0},{1,0}});
 huge.controlPoints.clear();
 for(int i=0;i<1210;++i)huge.controlPoints.push_back({double(i),0});
 assert(!buildRoadTopology({huge},topo));
 // Cannot produce a connected graph with invalid source control points.
 assert(!buildRoadTopology({road(77,{{0,0},{0,0}})},topo));
 std::cout<<"OE_ROAD_TOPOLOGY_OK: X/T/Y shared nodes, snap segments, stable IDs, atomic bounds\n";
}
