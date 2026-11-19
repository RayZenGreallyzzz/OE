#pragma once
// OE v40: sparse 1m Phoenix UV56 height field. Heights are stored in cm.
// Host-testable core: no Android/GLES and no private UV56 source/assets.
#include <cmath>
#include <cstdint>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include <utility>
namespace oe { namespace phoenix {
constexpr std::size_t kMaxNodes=32768;
constexpr int kMinHeightCm=-2400, kMaxHeightCm=14000;
inline uint64_t key(int x,int z){
 return (static_cast<uint64_t>(static_cast<uint32_t>(x))<<32)|
        static_cast<uint32_t>(z);
}
inline int floorDiv(int a,int b){return a>=0?a/b:-(((-a)+b-1)/b);}
struct Node {int x=0,z=0,h=0,p=0;};
struct Field {
 int areaSqM=0;
 std::vector<Node> nodes;
 std::unordered_map<uint64_t,float> cmByNode;
 std::unordered_set<uint64_t> affectedChunks;
 void clear(){areaSqM=0;nodes.clear();cmByNode.clear();affectedChunks.clear();}
 bool empty()const{return areaSqM==0;}
 float heightNode(int x,int z)const{
  const auto found=cmByNode.find(key(x,z));
  return found==cmByNode.end()?0.f:found->second*.01f;
 }
 float heightAt(float x,float z)const{
  if(empty()||!std::isfinite(x)||!std::isfinite(z))return 0.f;
  const float half=std::sqrt(static_cast<float>(areaSqM))*.5f;
  if(x< -half||x>half||z< -half||z>half)return 0.f;
  const int ix=static_cast<int>(std::floor(x)),iz=static_cast<int>(std::floor(z));
  const float a=x-ix,b=z-iz;
  return (1-a)*(1-b)*heightNode(ix,iz)+a*(1-b)*heightNode(ix+1,iz)+
         (1-a)*b*heightNode(ix,iz+1)+a*b*heightNode(ix+1,iz+1);
 }
 bool affectsChunk(int tx,int tz)const{
  return affectedChunks.find(key(tx,tz))!=affectedChunks.end();
 }
 // Fully stage validation before replacing any live city terrain.
 static bool prepare(int area,int worldSize,const std::vector<int>&raw,Field&out){
  if(area<1000||area>2000000||worldSize<1024||worldSize>16384||
     std::sqrt(static_cast<double>(area))>worldSize||
     raw.size()%4!=0||raw.size()>kMaxNodes*4)return false;
  Field stage;stage.areaSqM=area;
  stage.nodes.reserve(raw.size()/4);
  stage.cmByNode.reserve(raw.size()/4);
  const double side=std::sqrt(static_cast<double>(area));
  for(std::size_t i=0;i<raw.size();i+=4){
   const int x=raw[i],z=raw[i+1],h=raw[i+2],p=raw[i+3];
   if(std::abs(static_cast<double>(x))>side/2+1||
      std::abs(static_cast<double>(z))>side/2+1||
      std::abs(x)>worldSize/2||std::abs(z)>worldSize/2||
      h<kMinHeightCm||h>kMaxHeightCm||p<0||p>4096||
      !stage.cmByNode.emplace(key(x,z),static_cast<float>(h)).second)
     return false;
   stage.nodes.push_back({x,z,h,p});
   const int tx=floorDiv(x,64),tz=floorDiv(z,64);
   stage.affectedChunks.insert(key(tx,tz));
   if(x%64==0)stage.affectedChunks.insert(key(tx-1,tz));
   if(z%64==0)stage.affectedChunks.insert(key(tx,tz-1));
   if(x%64==0&&z%64==0)stage.affectedChunks.insert(key(tx-1,tz-1));
  }
  out=std::move(stage);return true;
 }
};
}}