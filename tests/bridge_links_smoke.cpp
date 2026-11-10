#include "../android/src/main/cpp/roads/bridge_links.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
using namespace oe::roads;
static Road road(std::uint64_t id,double width,std::initializer_list<Point> pts){
 return {id,"Approach","builtin:cobblestone",width,std::vector<Point>(pts)};
}
static BridgeInstance bridge(int entityId,int kind,double x=0,double y=-1.6925,
                             double z=0,double yaw=0){
 return {entityId,kind,x,y,z,yaw,1,1,1,1};
}
static const TerrainHeight flat=[](double,double){return 0.;};
static RoadTopology twoBanks(double width=3.){
 RoadTopology g;
 assert(buildRoadTopology({
  road(11,width,{{-17,0},{-4.2,0}}),
  road(12,width,{{4.2,0},{17,0}})},g,0));
 return g;
}
int main(){
 RoadTopology g=twoBanks();
 BridgeDeck cap{};
 const BridgeInstance stone=bridge(41,17);
 assert(describeBridge(stone,cap));
 assert(cap.supportsContinuousCrossing&&!cap.footOnly);
 assert(std::abs(cap.length-8.45)<.001);
 assert(std::abs(cap.start.x+4.225)<.001);
 assert(std::abs(cap.end.x-4.225)<.001);
 assert(std::abs(cap.start.y-.14)<.001);
 BridgeNetwork network;
 assert(buildBridgeNetwork(g,{stone},flat,network));
 assert(network.decks.size()==1);
 assert(network.links.size()==1&&network.unconnectedBridges==0);
 assert(network.links.front().fromRoad==11&&network.links.front().toRoad==12);
 assert(network.links.front().fromNode!=network.links.front().toNode);
 assert(network.links.front().bridgeId==41);
 assert(network.roadCollisionTriangles>0);
 assert(network.collision.size()==network.roadCollisionTriangles+2);
 assert(network.collision.back().ownerId==41);
 assert(network.links.front().width>3.5);
 // An arbitrarily raised bridge stays visible but is NOT connected.
 BridgeNetwork floating;
 assert(buildBridgeNetwork(g,{bridge(42,17,0,1,0)},flat,floating));
 assert(floating.links.empty()&&floating.unconnectedBridges==1);
 assert(floating.collision.size()==floating.roadCollisionTriangles+2);
 // A bridge sitting SIDEWAYS over two parallel roads is not connected.
 BridgeNetwork sideways;
 assert(buildBridgeNetwork(g,{bridge(43,17,0,-1.6925,0,90)},flat,sideways));
 assert(sideways.links.empty());
 // Narrow bridge cannot fit a 6-metre road.
 auto wideRoads=twoBanks(6.);
 BridgeNetwork narrow;
 assert(buildBridgeNetwork(wideRoads,{stone},flat,narrow));
 assert(narrow.links.empty());
 // The ford is visibly present but it is a series of separated stones,
 // not a continuous guaranteed crossing or a solid floor collider.
 auto ford=bridge(44,22,0,-.14,0);
 BridgeNetwork stepping;
 assert(buildBridgeNetwork(g,{ford},flat,stepping));
 assert(stepping.links.empty()&&stepping.unconnectedBridges==1);
 assert(stepping.collision.size()==stepping.roadCollisionTriangles);
 // Explicit fit can align bridge to bank-road endpoints: no road changes.
 BridgeInstance proposed{};
 auto moved=bridge(45,17,1.,0.,2.,25.);
 assert(proposeBridgeRoadFit(g,moved,flat,proposed));
 assert(std::abs(proposed.x)<.01&&std::abs(proposed.z)<.01);
 assert(std::abs(proposed.y+1.6925)<.01);
 assert(std::abs(proposed.yawDegrees)<.01);
 assert(std::abs(moved.x-1.)<.01); // pure function / no scene mutation
 BridgeNetwork fitted;
 assert(buildBridgeNetwork(g,{proposed},flat,fitted)&&fitted.links.size()==1);
 // Fit must refuse decks near roads on different heights; no teleportation.
 auto stepped=[](double x,double){return x>0?4.:0.;};
 BridgeInstance unchanged=proposed;
 assert(!proposeBridgeRoadFit(g,moved,stepped,unchanged));
 assert(std::abs(unchanged.y-proposed.y)<1e-6);
 // Multiple bridges competing for same dead-end nodes are not both linked.
 BridgeNetwork duplicate;
 assert(buildBridgeNetwork(g,{stone,bridge(47,17)},flat,duplicate));
 assert(duplicate.links.size()==1&&duplicate.unconnectedBridges==1);
 // Rotated placement and scaled modules retain correct end geometry.
 BridgeDeck turned;
 assert(describeBridge(bridge(46,17,0,-1.6925,0,90),turned));
 assert(std::abs(turned.start.z-4.225)<.01);
 // Failed build must preserve previous valid output (atomic failure).
 const auto previous=network.links.size(),triangles=network.collision.size();
 assert(!buildBridgeNetwork(g,{stone,stone},flat,network)); // duplicate IDs
 assert(network.links.size()==previous&&network.collision.size()==triangles);
 assert(!buildBridgeNetwork(g,{bridge(55,19)},flat,network)); // foundation is not a bridge
 assert(!buildBridgeNetwork(g,{stone},[](double,double){
   return std::numeric_limits<double>::quiet_NaN();
 },network));
 assert(network.links.size()==previous);
 std::cout<<"OE_BRIDGE_LINKS_OK: road banks, deck fit, valid Y/width, collision, fallback, atomicity\n";
}
