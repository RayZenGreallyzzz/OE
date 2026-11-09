#include "material_layers.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <set>
#include <utility>

namespace oe { namespace terrainpaint {
namespace {
constexpr double kMaxStrokeMetres=128.0;
constexpr double kPixelMetres=double(kTileMetres)/kResolution;
bool finite(double x) noexcept{return std::isfinite(x);}
double clamp(double x,double a,double b){return std::max(a,std::min(x,b));}
int tileOf(double p){return static_cast<int>(std::floor(p/kTileMetres));}
bool validWorld(int world){return world==1024||world==2048||world==4096||
                                  world==8192||world==16384;}
bool tileInside(int x,int z,int world){
 const double half=world*.5;
 return x*kTileMetres<half&&(x+1)*kTileMetres> -half &&
        z*kTileMetres<half&&(z+1)*kTileMetres> -half;
}
MaterialTile* findTile(std::vector<MaterialTile>&tiles,int x,int z){
 for(auto&tile:tiles)if(tile.x==x&&tile.z==z)return &tile;
 return nullptr;
}
MaterialMask* findMask(MaterialTile&tile,const std::string&key){
 for(auto&mask:tile.materials)if(mask.material==key)return &mask;
 return nullptr;
}
void stamp(std::vector<MaterialTile>&tiles,double x,double z,double radius,
           double opacity,const std::string& key,bool erase,int world){
 const double half=world*.5;
 const int minTX=tileOf(std::max(-half,x-radius));
 const int maxTX=tileOf(std::min(std::nextafter(half,-INFINITY),x+radius));
 const int minTZ=tileOf(std::max(-half,z-radius));
 const int maxTZ=tileOf(std::min(std::nextafter(half,-INFINITY),z+radius));
 for(int tz=minTZ;tz<=maxTZ;++tz)for(int tx=minTX;tx<=maxTX;++tx){
  MaterialTile*tile=findTile(tiles,tx,tz);
  if(!tile)continue;
  MaterialMask*mask=findMask(*tile,key);
  if(!mask)continue;
  const double originX=tx*kTileMetres,originZ=tz*kTileMetres;
  const int minX=std::max(0,int(std::floor((x-radius-originX)/kPixelMetres)));
  const int maxX=std::min(kResolution-1,int(std::ceil((x+radius-originX)/kPixelMetres)));
  const int minZ=std::max(0,int(std::floor((z-radius-originZ)/kPixelMetres)));
  const int maxZ=std::min(kResolution-1,int(std::ceil((z+radius-originZ)/kPixelMetres)));
  bool changed=false;
  for(int iz=minZ;iz<=maxZ;++iz)for(int ix=minX;ix<=maxX;++ix){
   const double worldX=originX+(ix+.5)*kPixelMetres;
   const double worldZ=originZ+(iz+.5)*kPixelMetres;
   if(worldX< -half||worldX>=half||worldZ< -half||worldZ>=half)continue;
   const double distance=std::hypot(worldX-x,worldZ-z);
   if(distance>=radius)continue;
   const double taper=clamp((radius-distance)/std::max(.25,radius*.24),0.,1.);
   const double alpha=opacity*taper;
   if(alpha<=0.)continue;
   std::uint8_t&old=mask->weights[static_cast<std::size_t>(iz)*kResolution+ix];
   const double target=erase?(old*(1.-alpha)):(old+(255.-old)*alpha);
   const auto fresh=static_cast<std::uint8_t>(clamp(std::round(target),0.,255.));
   if(fresh!=old){old=fresh;changed=true;}
  }
  if(changed)++mask->revision;
 }
}
} // namespace

bool validMaterialKey(const std::string& key) noexcept {
 if(key.size()!=31||key.compare(0,3,"tx_")!=0)return false;
 for(std::size_t i=3;i<27;++i)
  if(!((key[i]>='0'&&key[i]<='9')||(key[i]>='a'&&key[i]<='f')))return false;
 return key.compare(27,4,".png")==0||key.compare(27,4,".jpg")==0;
}

bool MaterialCanvas::stroke(double x0,double z0,double x1,double z1,
                            double radius,double opacity,const std::string& material,
                            bool erase,int worldSize){
 if(!finite(x0)||!finite(z0)||!finite(x1)||!finite(z1)||!finite(radius)||
    !finite(opacity)||!validMaterialKey(material)||!validWorld(worldSize)||
    radius<.5||radius>16.||opacity<.05||opacity>1.)return false;
 const double half=worldSize*.5;
 if(std::max({std::abs(x0),std::abs(z0),std::abs(x1),std::abs(z1)})>=half)
    return false;
 const double distance=std::hypot(x1-x0,z1-z0);
 if(!finite(distance)||distance>kMaxStrokeMetres)return false;
 const int minX=tileOf(std::max(-half,std::min(x0,x1)-radius));
 const int minZ=tileOf(std::max(-half,std::min(z0,z1)-radius));
 const int maxX=tileOf(std::min(std::nextafter(half,-INFINITY),std::max(x0,x1)+radius));
 const int maxZ=tileOf(std::min(std::nextafter(half,-INFINITY),std::max(z0,z1)+radius));
 std::vector<std::pair<int,int>> missingTiles;
 std::vector<std::pair<int,int>> missingMasks;
 for(int z=minZ;z<=maxZ;++z)for(int x=minX;x<=maxX;++x){
  if(!tileInside(x,z,worldSize))continue;
  MaterialTile* tile=findTile(tiles_,x,z);
  if(erase)continue; // erase only existing material masks
  if(!tile){missingTiles.emplace_back(x,z);continue;}
  if(!findMask(*tile,material)){
   if(tile->materials.size()>=kMaxMaterialsPerTile)return false;
   missingMasks.emplace_back(x,z);
  }
 }
 if(tiles_.size()+missingTiles.size()>kMaxMaterialTiles)return false;
 for(const auto&position:missingTiles){
  MaterialTile tile;tile.x=position.first;tile.z=position.second;
  tile.materials.push_back({material,std::vector<std::uint8_t>(kMaterialMaskBytes,0),1});
  tiles_.push_back(std::move(tile));
 }
 for(const auto&position:missingMasks){
  MaterialTile*tile=findTile(tiles_,position.first,position.second);
  if(tile)tile->materials.push_back({material,std::vector<std::uint8_t>(kMaterialMaskBytes,0),1});
 }
 const double step=std::max(.25,radius*.28);
 const int count=std::max(1,int(std::ceil(distance/step)));
 for(int i=0;i<=count;++i){
  const double t=double(i)/count;
  stamp(tiles_,x0+(x1-x0)*t,z0+(z1-z0)*t,radius,opacity,material,erase,worldSize);
 }
 // Reclaim erased material slots/empty tiles to keep the 2 MiB cap meaningful.
 if(erase){
  for(auto&tile:tiles_){
   auto& layers=tile.materials;
   layers.erase(std::remove_if(layers.begin(),layers.end(),[](const MaterialMask&mask){
    return std::none_of(mask.weights.begin(),mask.weights.end(),
                        [](std::uint8_t value){return value!=0;});
   }),layers.end());
  }
  tiles_.erase(std::remove_if(tiles_.begin(),tiles_.end(),
              [](const MaterialTile&tile){return tile.materials.empty();}),tiles_.end());
 }
 return true;
}

bool MaterialCanvas::replaceAll(std::vector<MaterialTile> incoming,int worldSize){
 if(!validWorld(worldSize)||incoming.size()>kMaxMaterialTiles)return false;
 std::set<std::pair<int,int>> positions;
 for(auto&tile:incoming){
  if(!tileInside(tile.x,tile.z,worldSize)||!positions.insert({tile.x,tile.z}).second||
     tile.materials.empty()||tile.materials.size()>kMaxMaterialsPerTile)return false;
  std::set<std::string> keys;
  for(auto&mask:tile.materials){
   if(!validMaterialKey(mask.material)||!keys.insert(mask.material).second||
      mask.weights.size()!=kMaterialMaskBytes)return false;
   mask.revision=1;
  }
 }
 tiles_.swap(incoming);
 return true;
}

}} // namespace oe::terrainpaint
