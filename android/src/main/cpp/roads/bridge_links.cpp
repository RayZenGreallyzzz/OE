#include "bridge_links.h"
#include "medieval_surface.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>
#include <set>
#include <utility>
#include <vector>
namespace oe { namespace roads {
namespace {
constexpr double kPi=3.14159265358979323846;
constexpr double kLift=.14;
constexpr double kDirectionCos=.76; // avoid side-on false links
constexpr double kMaxWidthOverflow=.65;
constexpr double kCandidateMidpointDistance=6.;
constexpr double kFittedSpanTolerance=1.4;
bool finite(double n){return std::isfinite(n);}
double length2(double x,double z){return std::hypot(x,z);}
double distance(Point a,Point b){return length2(a.x-b.x,a.z-b.z);}
bool bridgeType(int t){
 return t==medieval::kStoneBridge||t==medieval::kTimberBridge||
        t==medieval::kNarrowFootbridge||t==medieval::kRopeBridge||
        t==medieval::kSteppingStoneFord||t==medieval::kWideCartBridge||
        t==medieval::kSwampCauseway;
}
bool continuous(int type){return type!=medieval::kSteppingStoneFord;}
bool footOnly(int type){return type==medieval::kNarrowFootbridge||
                               type==medieval::kRopeBridge||
                               type==medieval::kSwampCauseway;}
double deckHeight(int type){
 switch(type){
  case medieval::kStoneBridge: return 1.8325;
  case medieval::kTimberBridge: return 1.57;
  case medieval::kNarrowFootbridge: return 1.07;
  case medieval::kRopeBridge: return 1.385;
  case medieval::kSteppingStoneFord: return .28;
  case medieval::kWideCartBridge: return 1.69;
  case medieval::kSwampCauseway: return 1.125;
  default: return std::numeric_limits<double>::quiet_NaN();
 }
}
double deckWidth(int type){
 switch(type){
  case medieval::kStoneBridge: return 3.58;
  case medieval::kTimberBridge: return 2.80;
  case medieval::kNarrowFootbridge: return 1.69;
  case medieval::kRopeBridge: return 2.00;
  case medieval::kSteppingStoneFord: return 1.50;
  case medieval::kWideCartBridge: return 4.55;
  case medieval::kSwampCauseway: return 3.04;
  default: return 0;
 }
}
double deckSpan(int type){
 switch(type){
  case medieval::kStoneBridge: return 8.45;
  case medieval::kTimberBridge: return 8.35;
  case medieval::kNarrowFootbridge: return 6.35;
  case medieval::kRopeBridge: return 9.00;
  case medieval::kSteppingStoneFord: return 8.5;
  case medieval::kWideCartBridge: return 10.18;
  case medieval::kSwampCauseway: return 8.55;
  default: return 0;
 }
}
struct DeadEnd {
 std::uint64_t node=0,road=0;
 Point pos;
 double outwardX=0,outwardZ=0,width=0;
};
bool deadEnds(const RoadTopology&graph,std::vector<DeadEnd>&ends){
 ends.clear();
 // Only degree-1 centreline endpoints; no accidental T junction links.
 for(const auto&node:graph.nodes){
  if(node.degree!=1)continue;
  for(const auto&road:graph.roads){
   const auto&points=road.controlPoints;
   if(points.size()<2)continue;
   bool start=distance(points.front(),node.position)<.0142;
   bool end=distance(points.back(),node.position)<.0142;
   if(!start&&!end)continue;
   const Point adjacent=start?points[1]:points[points.size()-2];
   double ox=node.position.x-adjacent.x,oz=node.position.z-adjacent.z;
   double len=length2(ox,oz);
   if(len<.01)return false;
   ends.push_back({node.id,road.id,node.position,ox/len,oz/len,
                   road.widthMetres});
   if(ends.size()>kMaxTopologyNodes)return false;
   break;
  }
 }
 return true;
}
bool acceptableEnd(const DeadEnd&road,WorldPoint cap,WorldPoint other,
                   double bridgeWidth,const TerrainHeight&height,double maxGap){
 const double horizontal=length2(road.pos.x-cap.x,road.pos.z-cap.z);
 if(horizontal>maxGap)return false;
 if(road.width>bridgeWidth+kMaxWidthOverflow)return false;
 const double roadY=height(road.pos.x,road.pos.z);
 if(!finite(roadY)||std::abs(roadY+kLift-cap.y)>kBridgeMaxVerticalGap)return false;
 // outward road tangent must point INTO the bridge, not parallel sideways.
 const double bx=other.x-cap.x,bz=other.z-cap.z,bd=length2(bx,bz);
 if(bd<=.01)return false;
 return road.outwardX*bx/bd+road.outwardZ*bz/bd>=kDirectionCos;
}
bool appendCollision(const SurfaceMesh&mesh,std::uint64_t id,
                     std::vector<CollisionTriangle>&colliders){
 if(mesh.triangles.size()%3!=0||
    mesh.triangles.size()/3>kMaxTotalCollisionTriangles-colliders.size())
  return false;
 for(std::size_t i=0;i<mesh.triangles.size();i+=3){
  const auto& a=mesh.triangles[i],&b=mesh.triangles[i+1],
              &c=mesh.triangles[i+2];
  colliders.push_back({{a.x,a.y,a.z},{b.x,b.y,b.z},{c.x,c.y,c.z},0,id});
 }
 return true;
}
bool appendDeck(const BridgeDeck&deck,std::vector<CollisionTriangle>&colliders){
 if(colliders.size()>kMaxTotalCollisionTriangles-2)return false;
 const double ux=(deck.end.x-deck.start.x)/deck.length,
              uz=(deck.end.z-deck.start.z)/deck.length;
 const double leftX=-uz*deck.width*.5,leftZ=ux*deck.width*.5;
 const WorldPoint a{deck.start.x+leftX,deck.start.y,deck.start.z+leftZ};
 const WorldPoint b{deck.start.x-leftX,deck.start.y,deck.start.z-leftZ};
 const WorldPoint c{deck.end.x+leftX,deck.end.y,deck.end.z+leftZ};
 const WorldPoint d{deck.end.x-leftX,deck.end.y,deck.end.z-leftZ};
 // Down/up winding is not used for collision yet; triangles include Y.
 colliders.push_back({a,b,c,deck.entityId,0});
 colliders.push_back({c,b,d,deck.entityId,0});
 return true;
}
} // namespace

bool describeBridge(const BridgeInstance&o,BridgeDeck&out) noexcept{
 if(o.entityId<=0||!bridgeType(o.type)||!finite(o.x)||!finite(o.y)||
    !finite(o.z)||!finite(o.yawDegrees)||!finite(o.uniformScale)||
    !finite(o.sx)||!finite(o.sy)||!finite(o.sz)||
    std::abs(o.x)>8192||std::abs(o.z)>8192||
    o.y< -24||o.y>160||std::abs(o.yawDegrees)>36000||
    o.uniformScale<.2||o.uniformScale>8||
    o.sx<.1||o.sx>8||o.sy<.1||o.sy>8||o.sz<.1||o.sz>8)return false;
 const double span=deckSpan(o.type)*o.uniformScale*o.sx;
 const double width=deckWidth(o.type)*o.uniformScale*o.sz;
 const double level=o.y+deckHeight(o.type)*o.uniformScale*o.sy;
 if(!finite(span)||!finite(width)||!finite(level)||span<.5||span>600||
    width<.3||width>150)return false;
 const double angle=o.yawDegrees*kPi/180.;
 const double dx=std::cos(angle)*span*.5;
 const double dz=-std::sin(angle)*span*.5; // matches GLES rotateY
 BridgeDeck draft;
 draft.entityId=o.entityId;draft.type=o.type;
 draft.start={o.x-dx,level,o.z-dz};
 draft.end={o.x+dx,level,o.z+dz};
 draft.width=width;draft.length=span;
 draft.supportsContinuousCrossing=continuous(o.type);
 draft.footOnly=footOnly(o.type);
 out=draft;return true;
}
bool buildBridgeNetwork(const RoadTopology&graph,
                        const std::vector<BridgeInstance>&bridges,
                        const TerrainHeight&height,BridgeNetwork&output){
 if(!height||bridges.size()>kMaxBridges||
    graph.edges.size()>kMaxTopologyEdges||
    graph.nodes.size()>kMaxTopologyNodes||
    graph.roads.size()>kMaxRoads)return false;
 BridgeNetwork result;
 std::set<int> ids;
 std::set<std::uint64_t> usedNodes;
 std::vector<DeadEnd> ends;
 if(!deadEnds(graph,ends))return false;
 // The candidate collider mesh is kept independent from the visual renderer.
 // Prefer stitched X/T/Y surfaces, fall back to R1 ribbons for sharp joins.
 MedievalSurface surfaces;
 if(buildMedievalSurface(graph,height,surfaces)){
  result.usedStitchedJunctions=true;
  for(const auto&road:surfaces.roads)
   if(!appendCollision(road.mesh,road.roadId,result.collision))return false;
  for(const auto&patch:surfaces.junctions)
   if(!appendCollision(patch.mesh,0,result.collision))return false;
 }else{
  for(const auto&road:graph.roads){
   SurfaceMesh mesh;
   if(!buildSurfaceMesh(road,height,mesh))return false;
   if(!appendCollision(mesh,road.id,result.collision))return false;
  }
 }
 result.roadCollisionTriangles=result.collision.size();
 if(result.roadCollisionTriangles>kMaxRoadCollisionTriangles)return false;
 for(const auto&object:bridges){
  BridgeDeck deck;
  if(!ids.insert(object.entityId).second||!describeBridge(object,deck))return false;
  result.decks.push_back(deck);
  if(!deck.supportsContinuousCrossing){++result.unconnectedBridges;continue;}
  // Deck floor collision is useful even on a temporarily unconnected bridge.
  if(!appendDeck(deck,result.collision))return false;
  const DeadEnd* match[2]={nullptr,nullptr};
  const WorldPoint cap[2]={deck.start,deck.end};
  for(int side=0;side<2;++side){
   double closest=kBridgeMaxHorizontalGap;
   bool ambiguous=false;
   for(const auto&road:ends){
    const WorldPoint other=cap[1-side];
    if(!acceptableEnd(road,cap[side],other,deck.width,height,
                      kBridgeMaxHorizontalGap))continue;
    const double dist=length2(road.pos.x-cap[side].x,road.pos.z-cap[side].z);
    if(dist<closest-.10){closest=dist;match[side]=&road;ambiguous=false;}
    else if(std::abs(dist-closest)<=.10){ambiguous=true;}
   }
   if(ambiguous)match[side]=nullptr;
  }
  if(match[0]&&match[1]&&match[0]->node!=match[1]->node&&
     usedNodes.count(match[0]->node)==0&&
     usedNodes.count(match[1]->node)==0){
   result.links.push_back({object.entityId,match[0]->node,match[1]->node,
       match[0]->road,match[1]->road,deck.length,deck.width,deck.footOnly});
   usedNodes.insert(match[0]->node);usedNodes.insert(match[1]->node);
  }else ++result.unconnectedBridges;
 }
 output=std::move(result);return true;
}
bool proposeBridgeRoadFit(const RoadTopology&graph,const BridgeInstance&current,
                          const TerrainHeight&height,BridgeInstance&fitted){
 if(!height)return false;
 BridgeDeck deck;
 if(!describeBridge(current,deck)||!deck.supportsContinuousCrossing)return false;
 std::vector<DeadEnd> ends;
 if(!deadEnds(graph,ends))return false;
 const double cx=current.x,cz=current.z;
 double best=std::numeric_limits<double>::infinity();
 BridgeInstance candidate=current;
 for(std::size_t i=0;i<ends.size();++i)for(std::size_t j=i+1;j<ends.size();++j){
  const auto&a=ends[i],&b=ends[j];
  if(a.node==b.node||a.width>deck.width+kMaxWidthOverflow||
     b.width>deck.width+kMaxWidthOverflow)continue;
  const double span=distance(a.pos,b.pos);
  if(std::abs(span-deck.length)>kFittedSpanTolerance)continue;
  const double midX=(a.pos.x+b.pos.x)*.5,midZ=(a.pos.z+b.pos.z)*.5;
  const double midpointGap=length2(midX-cx,midZ-cz);
  if(midpointGap>kCandidateMidpointDistance)continue;
  const double ay=height(a.pos.x,a.pos.z),by=height(b.pos.x,b.pos.z);
  if(!finite(ay)||!finite(by)||std::abs(ay-by)>.65)continue;
  const double dx=b.pos.x-a.pos.x,dz=b.pos.z-a.pos.z;
  const double ux=dx/span,uz=dz/span;
  const double alignmentA=a.outwardX*ux+a.outwardZ*uz;
  const double alignmentB=-(b.outwardX*ux+b.outwardZ*uz);
  if(alignmentA<kDirectionCos||alignmentB<kDirectionCos)continue;
  const double score=midpointGap+std::abs(span-deck.length)*.8+
                     std::abs(ay-by)*.5;
  if(score>=best)continue;
  const double angle=-std::atan2(dz,dx)*180./kPi;
  const double surfaceY=(ay+by)*.5+kLift;
  const double originY=surfaceY-
                       deckHeight(current.type)*current.uniformScale*current.sy;
  if(originY< -24||originY>160)continue;
  candidate.x=midX;candidate.z=midZ;candidate.yawDegrees=angle;
  candidate.y=originY;best=score;
 }
 if(!finite(best))return false;
 BridgeDeck finalDeck;
 if(!describeBridge(candidate,finalDeck))return false;
 // Verify both caps against nearby ends in their NEW actual orientation.
 int connected=0;
 for(const auto&side:{finalDeck.start,finalDeck.end}){
  const WorldPoint other=side.x==finalDeck.start.x&&
       side.z==finalDeck.start.z?finalDeck.end:finalDeck.start;
  bool found=false;
  for(const auto&road:ends)
   if(acceptableEnd(road,side,other,finalDeck.width,height,
                    kBridgeMaxHorizontalGap)){found=true;break;}
  if(found)++connected;
 }
 if(connected!=2)return false;
 fitted=candidate;return true;
}
}} // namespace oe::roads
