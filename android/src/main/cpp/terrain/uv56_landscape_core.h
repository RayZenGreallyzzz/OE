#pragma once
// OE R5.7: native height-brush math ported from Phoenix UV56
// landscape-manager.js. Core data, NOT another WebView/Android app.
// No UV56 building, drone or character source is used.
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <map>
#include <optional>
#include <utility>
#include <vector>

namespace oe { namespace uv56 {
using Point=std::pair<int,int>;
struct Node {int h=0,p=0;}; // centimetres, material id
using ChangeSet=std::map<Point,std::optional<Node>>;
enum class Tool {Look,Raise,Lower,Ridge,Smooth,Flatten};

class Terrain {
public:
 static constexpr std::size_t kNodeLimit=32768; // bounded 4 GiB tablet budget
 static constexpr std::size_t kUndoLimit=16;
 bool create(int areaSqM){
  if(areaSqM<1000||areaSqM>2000000)return false;
  area_=areaSqM;points_.clear();undo_.clear();redo_.clear();stroke_.clear();
  strokeActive_=false;strokeLevel_=0;return true;
 }
 int areaSqM()const{return area_;}
 bool active()const{return area_!=0;}
 std::size_t nodeCount()const{return points_.size();}
 const std::map<Point,Node>& nodes()const{return points_;}
 bool valid(float x,float z)const{
  if(!active()||!std::isfinite(x)||!std::isfinite(z))return false;
  const double side=std::sqrt(static_cast<double>(area_));
  return x>=-side/2&&x<=side/2&&z>=-side/2&&z<=side/2;
 }
 Node at(int x,int z)const{
  auto it=points_.find({x,z});return it==points_.end()?Node{}:it->second;
 }
 float heightNode(int x,int z)const{return at(x,z).h/100.f;}
 float heightAt(float x,float z)const{
  if(!active()||!std::isfinite(x)||!std::isfinite(z))return 0.f;
  const int ax=static_cast<int>(std::floor(x)),az=static_cast<int>(std::floor(z));
  const float u=x-ax,v=z-az;
  return heightNode(ax,az)*(1-u)*(1-v)+heightNode(ax+1,az)*u*(1-v)+
         heightNode(ax,az+1)*(1-u)*v+heightNode(ax+1,az+1)*u*v;
 }
 // Original UV56 nodes are [x,z,heightCm,materialId]. Validate before swap.
 bool restore(int area,const std::vector<std::array<int,4>>&raw,int customTileCount=0){
  if(area<1000||area>2000000||customTileCount<0||customTileCount>64||
     raw.size()>kNodeLimit)return false;
  std::map<Point,Node> proposed;
  const double half=std::sqrt(static_cast<double>(area))*.5;
  for(const auto&n:raw){
   if(n[0]<-half-1||n[0]>half+1||n[1]<-half-1||n[1]>half+1||
      n[2]<-60000||n[2]>60000||n[3]<0||n[3]>5+customTileCount)
     return false;
   if(!n[2]&&!n[3])continue;
   if(!proposed.emplace(Point{n[0],n[1]},Node{n[2],n[3]}).second)return false;
  }
  area_=area;points_.swap(proposed);undo_.clear();redo_.clear();stroke_.clear();
  strokeActive_=false;strokeLevel_=0;return true;
 }
 std::vector<std::array<int,4>> snapshot()const{
  std::vector<std::array<int,4>> out;out.reserve(points_.size());
  for(const auto&kv:points_)
   out.push_back({kv.first.first,kv.first.second,kv.second.h,kv.second.p});
  return out;
 }
 void beginStroke(float x,float z){
  stroke_.clear();strokeActive_=valid(x,z);strokeLevel_=heightAt(x,z);
 }
 // UV56 raised-cosine falloff, stroke-aware delta, smooth and flatten.
 bool brushAt(float x,float z,Tool tool,float radius,float power){
  if(!valid(x,z)||tool==Tool::Look||!std::isfinite(radius)||
     !std::isfinite(power))return false;
  if(!strokeActive_)beginStroke(x,z);
  radius=(tool==Tool::Raise||tool==Tool::Lower||tool==Tool::Ridge)?
   std::max(2.75f,std::clamp(radius,.5f,24.f)):std::clamp(radius,.5f,24.f);
  power=std::clamp(power,.1f,8.f);
  const double half=std::sqrt(static_cast<double>(area_))*.5;
  const int x0=static_cast<int>(std::ceil(std::max(-half,static_cast<double>(x-radius))));
  const int x1=static_cast<int>(std::floor(std::min(half,static_cast<double>(x+radius))));
  const int z0=static_cast<int>(std::ceil(std::max(-half,static_cast<double>(z-radius))));
  const int z1=static_cast<int>(std::floor(std::min(half,static_cast<double>(z+radius))));
  // UV56 reads a consistent neighbour snapshot for smoothing.
  std::map<Point,float> smoothBase;
  if(tool==Tool::Smooth){
   for(int iz=z0-1;iz<=z1+1;++iz)for(int ix=x0-1;ix<=x1+1;++ix)
    smoothBase.emplace(Point{ix,iz},heightNode(ix,iz));
  }
  std::map<Point,Node> staged;
  for(int iz=z0;iz<=z1;++iz)for(int ix=x0;ix<=x1;++ix){
   const float d=std::hypot(ix-x,iz-z);if(d>radius)continue;
   const float falloff=std::pow(.5f+.5f*std::cos(kPi*d/radius),1.16f);
   Point key{ix,iz};Node previous=at(ix,iz),next=previous;
   if(tool==Tool::Raise||tool==Tool::Lower||tool==Tool::Ridge){
    auto original=stroke_.find(key);
    int start=original==stroke_.end()?previous.h:
       (original->second?original->second->h:0);
    const int amount=jsRound(power*100.f*.5f*falloff);
    const int desired=std::clamp(start+(tool==Tool::Lower?-amount:amount),-20000,60000);
    next.h=tool==Tool::Lower?std::min(previous.h,desired):
                                std::max(previous.h,desired);
   }else if(tool==Tool::Smooth){
    const float mean=(smoothBase.at({ix-1,iz})+smoothBase.at({ix+1,iz})+
                      smoothBase.at({ix,iz-1})+smoothBase.at({ix,iz+1}))/4;
    next.h=jsRound(previous.h+(mean*100.f-previous.h)*
                    std::min(.8f,.36f*power*falloff));
   }else if(tool==Tool::Flatten){
    next.h=jsRound(previous.h+(strokeLevel_*100.f-previous.h)*
                    std::min(1.f,.36f*power*falloff));
   }
   if(previous.h!=next.h)staged.emplace(key,next);
  }
  if(staged.empty())return false;
  std::size_t added=0,removed=0;
  for(const auto&it:staged){
   bool was=points_.count(it.first)>0;
   bool now=it.second.h||it.second.p;
   if(!was&&now)++added;
   if(was&&!now)++removed;
  }
  if(points_.size()+added-removed>kNodeLimit)return false;
  for(const auto&it:staged){
   if(!stroke_.count(it.first)){
    auto old=points_.find(it.first);
    stroke_.emplace(it.first,old==points_.end()?std::nullopt:
                    std::optional<Node>(old->second));
   }
   if(it.second.h||it.second.p)points_[it.first]=it.second;
   else points_.erase(it.first);
  }
  return true;
 }
 void endStroke(){
  if(!stroke_.empty()){
   if(undo_.size()>=kUndoLimit)undo_.erase(undo_.begin());
   undo_.push_back(std::move(stroke_));redo_.clear();
  }
  stroke_.clear();strokeActive_=false;
 }
 bool undo(){return replay(undo_,redo_);}
 bool redo(){return replay(redo_,undo_);}
private:
 static constexpr float kPi=3.14159265358979323846f;
 static int jsRound(float f){return static_cast<int>(std::floor(f+.5f));}
 bool replay(std::vector<ChangeSet>&from,std::vector<ChangeSet>&to){
  if(from.empty()||strokeActive_)return false;
  ChangeSet opposite;
  for(const auto&change:from.back()){
   auto it=points_.find(change.first);
   opposite.emplace(change.first,it==points_.end()?std::nullopt:
                    std::optional<Node>(it->second));
  }
  for(const auto&change:from.back()){
   if(change.second)points_[change.first]=*change.second;
   else points_.erase(change.first);
  }
  from.pop_back();
  if(to.size()>=kUndoLimit)to.erase(to.begin());
  to.push_back(std::move(opposite));return true;
 }
 int area_=0;float strokeLevel_=0;bool strokeActive_=false;
 std::map<Point,Node> points_;
 ChangeSet stroke_;
 std::vector<ChangeSet> undo_,redo_;
};
}} // oe::uv56
