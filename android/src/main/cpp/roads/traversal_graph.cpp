#include "traversal_graph.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <map>
#include <set>
#include <utility>
#include <vector>

namespace oe { namespace roads {
namespace {
constexpr double kSurfaceLift=.14;
constexpr double kPi=3.14159265358979323846;
constexpr std::uint64_t kBridgeNodeBit=1ULL<<63;
struct Deck {double length,width,height;};
struct End {
 std::uint64_t id=0;Point xz{};double y=0; // real authored road dead-end
};
struct BridgePose {WalkPoint a,b;double width=0;};
struct Snap {
 std::uint64_t node=0;
 WalkPoint road{};
 double planar=0;
};
Deck deckFor(int kind){
 switch(kind){
  case medieval::kStoneBridge: return {8.1,3.2,1.84};
  case medieval::kTimberBridge: return {8.0,2.5,1.57};
  case medieval::kNarrowFootbridge:return {5.9,1.5,1.07};
  case medieval::kRopeBridge: return {8.7,1.8,1.39};
  case medieval::kWideCartBridge: return {9.9,4.25,1.69};
  case medieval::kSwampCauseway:return {8.3,2.75,1.13};
  default:return {0,0,0}; // stepping stones are disconnected, not a deck
 }
}
bool finite(double x){return std::isfinite(x);}
bool xyz(WalkPoint p){return finite(p.x)&&finite(p.y)&&finite(p.z)&&
 std::abs(p.x)<8400&&std::abs(p.z)<8400&&std::abs(p.y)<10000;}
double xyzdistance(WalkPoint a,WalkPoint b){
 return std::hypot(std::hypot(a.x-b.x,a.z-b.z),a.y-b.y);
}
WalkPoint xyzPoint(Point p,double y){return {p.x,y,p.z};}
bool bridgeValid(const BridgeInstance& b){
 if(!medieval::validModule(b.type)||!b.id||
    !finite(b.center.x)||!finite(b.center.z)||!finite(b.y)||
    !finite(b.yaw)||!finite(b.size)||!finite(b.sx)||
    !finite(b.sy)||!finite(b.sz))return false;
 const double xscale=std::abs(b.size*b.sx),
             zscale=std::abs(b.size*b.sz),
             yscale=std::abs(b.size*b.sy);
 return b.size>.01&&b.size<=20&&xscale>=.05&&xscale<=50&&
        zscale>=.05&&zscale<=50&&yscale>=.05&&yscale<=50&&
        std::abs(b.center.x)<8192&&std::abs(b.center.z)<8192&&
        std::abs(b.y)<10000&&std::abs(b.yaw)<100000;
}
bool pose(const BridgeInstance&b,BridgePose&out){
 const Deck deck=deckFor(b.type);
 if(deck.length==0||!bridgeValid(b))return false;
 const double half=deck.length*std::abs(b.size*b.sx)*.5;
 const double y=b.y+deck.height*b.size*b.sy;
 const double dx=std::cos(b.yaw),dz=std::sin(b.yaw);
 out.a={b.center.x-dx*half,y,b.center.z-dz*half};
 out.b={b.center.x+dx*half,y,b.center.z+dz*half};
 out.width=deck.width*std::abs(b.size*b.sz);
 return xyz(out.a)&&xyz(out.b)&&finite(out.width)&&out.width>.1;
}
bool collectRoadEnds(const RoadTopology& graph,const TerrainHeight&ground,
                     std::vector<End>& ends){
 ends.clear();ends.reserve(graph.nodes.size());
 for(const auto&node:graph.nodes){
  if(node.degree!=1)continue;
  const double h=ground(node.position.x,node.position.z)+kSurfaceLift;
  if(!finite(h)||std::abs(h)>10000)return false;
  ends.push_back({node.id,node.position,h});
 }
 std::sort(ends.begin(),ends.end(),[](const End&a,const End&b){return a.id<b.id;});
 return true;
}
bool closestEnd(const WalkPoint&mouth,const WalkPoint&other,
                const std::vector<End>&ends,Snap& result,
                bool checkHeight){
 double best=std::numeric_limits<double>::infinity();
 bool found=false;
 for(const End&road:ends){
  const double dx=road.xz.x-mouth.x,dz=road.xz.z-mouth.z;
  const double planar=std::hypot(dx,dz);
  if(planar>kBridgeRoadReach)continue;
  const double step=std::abs(road.y-mouth.y);
  if(checkHeight&&step>kBridgeStepTolerance)continue;
  // Do not accept a road end near the middle of the other bridge mouth.
  const double otherDistance=std::hypot(road.xz.x-other.x,road.xz.z-other.z);
  if(otherDistance<=planar+.001)continue;
  if(planar<best-1.e-7||(std::abs(planar-best)<=1.e-7&&road.id<result.node)){
   best=planar;result={road.id,xyzPoint(road.xz,road.y),planar};found=true;
  }
 }
 return found;
}
std::uint64_t endpointId(std::uint64_t bridge,int end){
 if(bridge>=(kBridgeNodeBit>>1))return 0;
 return kBridgeNodeBit|(bridge<<1)|static_cast<std::uint64_t>(end);
}
bool addRoad(const RoadTopology&g,const TerrainHeight&terrain,
             TraversalGraph&draft){
 std::map<std::uint64_t,Point> positions;
 std::map<std::uint64_t,double> widths;
 for(const auto&node:g.nodes)positions.emplace(node.id,node.position);
 for(const auto&road:g.roads)widths.emplace(road.id,road.widthMetres);
 for(const auto&edge:g.edges){
  const auto a=positions.find(edge.from),b=positions.find(edge.to);
  const auto w=widths.find(edge.roadId);
  if(a==positions.end()||b==positions.end()||w==widths.end()||
     edge.from==edge.to)return false;
  const Point start=a->second,end=b->second;
  const double dx=end.x-start.x,dz=end.z-start.z;
  const double distance=std::hypot(dx,dz);
  if(distance<.01)return false;
  const double sideX=-dz/distance*w->second*.5;
  const double sideZ=dx/distance*w->second*.5;
  const auto corner=[&](double x,double z,WalkPoint&pt)->bool{
   const double y=terrain(x,z)+kSurfaceLift;
   pt={x,y,z};return xyz(pt);
  };
  WalkSurface quad;quad.ownerId=edge.roadId;
  if(!corner(start.x+sideX,start.z+sideZ,quad.a)||
     !corner(start.x-sideX,start.z-sideZ,quad.b)||
     !corner(end.x-sideX,end.z-sideZ,quad.c)||
     !corner(end.x+sideX,end.z+sideZ,quad.d))return false;
  if(draft.surfaces.size()>=kMaxTraversalSurfaces)return false;
  draft.surfaces.push_back(quad);++draft.roadSurfaces;
  draft.edges.push_back({edge.from,edge.to,edge.roadId,
                        WalkEdgeKind::Road,distance});
 }
 return true;
}
} // namespace

bool buildTraversalGraph(const RoadTopology& graph,
                         const std::vector<BridgeInstance>&bridges,
                         const TerrainHeight& ground,TraversalGraph&out){
 if(!ground||bridges.size()>kMaxTraversalBridges||
    graph.nodes.size()>kMaxTopologyNodes||
    graph.edges.size()>kMaxTopologyEdges)return false;
 TraversalGraph draft;
 draft.edges.reserve(graph.edges.size()+bridges.size()*3);
 draft.surfaces.reserve(graph.edges.size()+bridges.size());
 if(!addRoad(graph,ground,draft))return false;
 std::vector<End> roadEnds;
 if(!collectRoadEnds(graph,ground,roadEnds))return false;
 std::set<std::uint64_t> ids;
 for(const auto&bridge:bridges){
  if(!bridgeValid(bridge)||!ids.insert(bridge.id).second)return false;
  BridgeStatus status;status.id=bridge.id;
  const Deck deck=deckFor(bridge.type);
  if(deck.length==0){draft.bridges.push_back(status);continue;}
  BridgePose walk;
  if(!pose(bridge,walk))return false;
  const std::uint64_t keyA=endpointId(bridge.id,0);
  const std::uint64_t keyB=endpointId(bridge.id,1);
  if(!keyA||!keyB)return false;
  status.walkable=true;
  const double sideX=-std::sin(bridge.yaw)*walk.width*.5;
  const double sideZ=std::cos(bridge.yaw)*walk.width*.5;
  WalkSurface floor;floor.ownerId=bridge.id;floor.bridge=true;
  floor.a={walk.a.x+sideX,walk.a.y,walk.a.z+sideZ};
  floor.b={walk.a.x-sideX,walk.a.y,walk.a.z-sideZ};
  floor.c={walk.b.x-sideX,walk.b.y,walk.b.z-sideZ};
  floor.d={walk.b.x+sideX,walk.b.y,walk.b.z+sideZ};
  if(!xyz(floor.a)||!xyz(floor.b)||!xyz(floor.c)||!xyz(floor.d)||
     draft.surfaces.size()>=kMaxTraversalSurfaces)return false;
  draft.surfaces.push_back(floor);++draft.bridgeSurfaces;
  Snap a{},b{};
  const bool connectedA=closestEnd(walk.a,walk.b,roadEnds,a,true);
  const bool connectedB=closestEnd(walk.b,walk.a,roadEnds,b,true);
  const bool distinct=!connectedA||!connectedB||a.node!=b.node;
  if(connectedA&&distinct){
   ++status.endsConnected;
   draft.edges.push_back({a.node,keyA,bridge.id,WalkEdgeKind::Approach,
                          xyzdistance(a.road,walk.a)});
  }
  if(connectedB&&distinct){
   ++status.endsConnected;
   draft.edges.push_back({keyB,b.node,bridge.id,WalkEdgeKind::Approach,
                          xyzdistance(walk.b,b.road)});
  }
  // Always preserve the deck segment in the *metadata* graph; traversal
  // adapters must only route across it if both approaches are attached.
  draft.edges.push_back({keyA,keyB,bridge.id,WalkEdgeKind::Bridge,
                         xyzdistance(walk.a,walk.b)});
  if(status.endsConnected==2)++draft.fullBridges;
  else if(status.endsConnected==1)++draft.oneEndedBridges;
  else ++draft.detachedBridges;
  draft.bridges.push_back(status);
 }
 out=std::move(draft);
 return true;
}
bool suggestBridgeDeckHeight(const RoadTopology& roads,
                             const BridgeInstance&bridge,
                             const TerrainHeight&ground,
                             double&suggestedEntityY){
 if(!ground||!bridgeValid(bridge))return false;
 BridgePose deck;
 if(!pose(bridge,deck))return false;
 std::vector<End> ends;
 if(!collectRoadEnds(roads,ground,ends))return false;
 Snap a{},b{};
 if(!closestEnd(deck.a,deck.b,ends,a,false)||
    !closestEnd(deck.b,deck.a,ends,b,false)||a.node==b.node)return false;
 // A bridge is flat; a >1.2 m bank height discrepancy needs ramps and
 // terrain changes, not a silently teleported bridge or gameplay actor.
 if(std::abs(a.road.y-b.road.y)>1.2)return false;
 const double next=(a.road.y+b.road.y)*.5-
       deckFor(bridge.type).height*bridge.size*bridge.sy;
 if(!finite(next)||next< -24.||next>160.)return false;
 suggestedEntityY=next;
 return true;
}
}} // namespace oe::roads
