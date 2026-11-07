#include "paint_tiles.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <set>
#include <utility>

namespace oe { namespace terrainpaint {
namespace {
constexpr double kMaxStroke=128.0;
bool finite(double d){return std::isfinite(d);}
double clamp(double x,double a,double b){return std::max(a,std::min(x,b));}
int tileOf(double x){return static_cast<int>(std::floor(x/kTileMetres));}
Tile* find(std::vector<Tile>&tiles,int tx,int tz){
 for(auto& tile:tiles)if(tile.x==tx&&tile.z==tz)return &tile;
 return nullptr;
}
bool tileInside(int x,int z,int worldSize){
 const double half=worldSize*.5;
 return x*kTileMetres<half&&(x+1)*kTileMetres> -half &&
        z*kTileMetres<half&&(z+1)*kTileMetres> -half;
}
void stamp(std::vector<Tile>& tiles,double x,double z,double radius,
           double opacity,bool erase,const Image& image,int worldSize){
 const double half=worldSize*.5;
 const int minTX=tileOf(std::max(-half,x-radius));
 const int maxTX=tileOf(std::min(std::nextafter(half,-INFINITY),x+radius));
 const int minTZ=tileOf(std::max(-half,z-radius));
 const int maxTZ=tileOf(std::min(std::nextafter(half,-INFINITY),z+radius));
 constexpr double step=double(kTileMetres)/kResolution;
 for(int tz=minTZ;tz<=maxTZ;tz++)for(int tx=minTX;tx<=maxTX;tx++){
  Tile* tile=find(tiles,tx,tz);
  if(!tile)continue;
  const double originX=tx*kTileMetres,originZ=tz*kTileMetres;
  int x0=std::max(0,int(std::floor((x-radius-originX)/step)));
  int x1=std::min(kResolution-1,int(std::ceil((x+radius-originX)/step)));
  int z0=std::max(0,int(std::floor((z-radius-originZ)/step)));
  int z1=std::min(kResolution-1,int(std::ceil((z+radius-originZ)/step)));
  bool changed=false;
  for(int iz=z0;iz<=z1;iz++)for(int ix=x0;ix<=x1;ix++){
   const double wx=originX+(ix+.5)*step,wz=originZ+(iz+.5)*step;
   if(wx < -half || wx >= half || wz < -half || wz >= half)continue;
   const double d=std::hypot(wx-x,wz-z);
   if(d>=radius)continue;
   const double taper=clamp((radius-d)/std::max(.25,radius*.24),0.,1.);
   const double coverage=clamp(opacity*taper,0.,1.);
   if(coverage<=0.)continue;
   auto* pixel=tile->pixels.data()+(iz*kResolution+ix)*4;
   const double dstAlpha=pixel[3]/255.;
   double alpha=coverage;
   int sx=0,sz=0;
   if(!erase){
    // Image repeats at 6m and is sampled in world coordinates for no seams.
    const auto wrap=[](double v){double u=std::fmod(v/6.,1.);return u<0?u+1:u;};
    sx=std::min(image.width-1,int(wrap(wx)*image.width));
    sz=std::min(image.height-1,int(wrap(wz)*image.height));
    const auto* sample=image.rgba+(sz*image.width+sx)*4;
    alpha*=sample[3]/255.;
    if(alpha<=0.)continue;
    const double outA=alpha+dstAlpha*(1.-alpha);
    for(int c=0;c<3;c++){
     const double rgb=(sample[c]*alpha+pixel[c]*dstAlpha*(1.-alpha))/std::max(outA,1e-9);
     pixel[c]=static_cast<std::uint8_t>(clamp(std::round(rgb),0,255));
    }
    pixel[3]=static_cast<std::uint8_t>(clamp(std::round(outA*255.),0,255));
   }else{
    const double outA=dstAlpha*(1.-alpha);
    pixel[3]=static_cast<std::uint8_t>(clamp(std::round(outA*255.),0,255));
    if(pixel[3]==0)pixel[0]=pixel[1]=pixel[2]=0;
   }
   changed=true;
  }
  if(changed)++tile->revision;
 }
}
} // namespace

bool Canvas::paint(double x0,double z0,double x1,double z1,double radius,
                   double opacity,bool erase,const Image& image,int worldSize){
 if(!finite(x0)||!finite(z0)||!finite(x1)||!finite(z1)||!finite(radius)||
    !finite(opacity)||radius<.5||radius>16.||opacity<.05||opacity>1.||
    (worldSize!=1024&&worldSize!=2048&&worldSize!=4096&&
     worldSize!=8192&&worldSize!=16384))return false;
 const double half=worldSize*.5;
 if(std::max({std::abs(x0),std::abs(z0),std::abs(x1),std::abs(z1)})>=half)
    return false;
 if(!erase&&(image.width<1||image.height<1||image.width>2048||
             image.height>2048||!image.rgba))return false;
 const double dist=std::hypot(x1-x0,z1-z0);
 if(dist>kMaxStroke)return false;
 const int minTX=tileOf(std::max(-half,std::min(x0,x1)-radius));
 const int minTZ=tileOf(std::max(-half,std::min(z0,z1)-radius));
 const int maxTX=tileOf(std::min(std::nextafter(half,-INFINITY),std::max(x0,x1)+radius));
 const int maxTZ=tileOf(std::min(std::nextafter(half,-INFINITY),std::max(z0,z1)+radius));
 std::vector<std::pair<int,int>> needed;
 for(int tz=minTZ;tz<=maxTZ;tz++)for(int tx=minTX;tx<=maxTX;tx++){
  if(!tileInside(tx,tz,worldSize)||find(tiles_,tx,tz))continue;
  // Erasing an unpainted tile should NOT allocate memory.
  if(erase)continue;
  needed.emplace_back(tx,tz);
 }
 if(tiles_.size()+needed.size()>kMaxTiles)return false;
 for(auto [x,z]:needed)tiles_.push_back({x,z,std::vector<std::uint8_t>(kBytesPerTile,0),1});
 const double stride=std::max(.25,radius*.28);
 const int count=std::max(1,static_cast<int>(std::ceil(dist/stride)));
 for(int i=0;i<=count;i++){
  const double t=static_cast<double>(i)/count;
  stamp(tiles_,x0+(x1-x0)*t,z0+(z1-z0)*t,radius,opacity,erase,image,worldSize);
 }
 return true;
}
bool Canvas::replaceAll(std::vector<Tile> incoming,int worldSize){
 if(incoming.size()>kMaxTiles)return false;
 std::set<std::pair<int,int>> ids;
 for(auto& tile:incoming){
  if(!tileInside(tile.x,tile.z,worldSize)||
     tile.pixels.size()!=kBytesPerTile||!ids.insert({tile.x,tile.z}).second)return false;
  tile.revision=1;
 }
 tiles_.swap(incoming);
 return true;
}
}} // namespace oe::terrainpaint
