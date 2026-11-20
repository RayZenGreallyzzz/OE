#include "../android/src/main/cpp/terrainpaint/paint_tiles.h"
#include <cassert>
#include <cmath>
#include <iostream>
using namespace oe::terrainpaint;
int main(){
 Canvas world;
 const std::uint8_t sample[]={210,90,30,255};
 const Image paint{1,1,sample};
 assert(world.paint(0,0,10,0,1.0,1.0,false,paint,1024));
 assert(world.tiles().size()>=2); // passes across tile boundaries at X=0,Z=0
 std::size_t painted=0;
 for(const auto& tile:world.tiles())for(std::size_t i=3;i<tile.pixels.size();i+=4)
   if(tile.pixels[i])painted++;
 assert(painted>25);
 const auto previous=world.tiles();
 assert(!world.paint(0,0,200,0,2,1,false,paint,1024));
 assert(world.tiles().size()==previous.size());
 assert(world.paint(0,0,10,0,1.0,1.0,true,paint,1024));
 assert(world.tiles().size()==previous.size()); // erase does not add tiles
 std::vector<Tile> loaded=previous;
 assert(world.replaceAll(loaded,1024));
 assert(world.tiles().front().pixels==previous.front().pixels);
 loaded.push_back(loaded.front());
 assert(!world.replaceAll(loaded,1024)); // duplicate coords rejected atomically
 assert(world.tiles().size()==previous.size());
 assert(!world.paint(0,0,1,1,0.3,1,false,paint,1024));
 assert(!world.paint(NAN,0,1,1,1,1,false,paint,1024));
 // Regression: painting more than 32 DISTINCT map tiles must not silently stop.
 Canvas wide;
 for(int i=0;i<50;i++){
  const int tx=i%10,tz=i/10;
  const double x=-480+tx*64+10,z=-480+tz*64+10;
  assert(wide.paint(x,z,x+1,z,1,1,false,paint,1024));
 }
 assert(wide.tiles().size()==50);
 const auto wideCopy=wide.tiles();
 Canvas restored;
 assert(restored.replaceAll(wideCopy,1024));
 assert(restored.tiles().size()==50);
 Canvas limit;
 assert(!limit.paint(-400,-400,400,400,16,1,false,paint,1024));
 assert(limit.tiles().empty());
 std::cout<<"OE_TERRAIN_TILES_OK: continuous strokes, 50 painted areas, atomic restore\n";
}
