#include "../android/src/main/cpp/terrainpaint/material_layers.h"
#include <cassert>
#include <iostream>
#include <limits>
#include <string>
using namespace oe::terrainpaint;
const std::string stone="tx_111111111111111111111111.png";
const std::string dirt="tx_222222222222222222222222.jpg";
const std::string grass="tx_333333333333333333333333.png";
const std::string sand="tx_444444444444444444444444.png";
const std::string brick="tx_555555555555555555555555.png";
int main(){
 assert(validMaterialKey(stone)&&validMaterialKey(dirt));
 assert(!validMaterialKey("tx_abc.png"));
 MaterialCanvas canvas;
 assert(canvas.stroke(10,10,20,10,1.0,1.0,stone,false,1024));
 assert(canvas.stroke(12,10,18,10,1.0,.75,dirt,false,1024));
 assert(canvas.tiles().size()==1);
 assert(canvas.tiles()[0].materials.size()==2);
 const auto preserved=canvas.tiles()[0].materials[1].weights;
 assert(canvas.stroke(13,10,14,10,1.0,1.0,stone,true,1024));
 assert(canvas.tiles()[0].materials[1].weights==preserved); // eraser is material-only
 assert(canvas.stroke(11,10,13,10,1.0,1.0,grass,false,1024));
 assert(canvas.stroke(11,10,13,10,1.0,1.0,sand,false,1024));
 assert(canvas.tiles()[0].materials.size()==4);
 assert(!canvas.stroke(11,10,13,10,1.0,1.0,brick,false,1024));
 assert(canvas.tiles()[0].materials.size()==4); // full-tile refused atomically
 auto original=canvas.tiles();
 assert(canvas.replaceAll(original,1024));
 assert(canvas.tiles()[0].materials[1].weights==preserved);
 auto invalid=original;
 invalid[0].materials[1].material=stone;
 assert(!canvas.replaceAll(invalid,1024));
 assert(canvas.tiles()[0].materials[1].material==dirt);
 invalid=original;invalid[0].materials[0].weights.pop_back();
 assert(!canvas.replaceAll(invalid,1024));
 assert(!canvas.stroke(0,0,200,0,1,1,stone,false,1024));
 assert(!canvas.stroke(std::numeric_limits<double>::quiet_NaN(),0,0,0,1,1,stone,false,1024));
 MaterialCanvas budget;
 for(int i=0;i<32;i++){
  int x=-480+(i%8)*64,z=-480+(i/8)*64;
  assert(budget.stroke(x+10,z+10,x+11,z+10,1,1,stone,false,1024));
 }
 assert(budget.tiles().size()==32);
 assert(!budget.stroke(400,400,401,400,1,1,stone,false,1024));
 assert(budget.tiles().size()==32); // cap 32 tiles
 assert(!budget.replaceAll({MaterialTile{0,0,{}}},1024));
 std::cout<<"OE_MATERIAL_LAYERS_OK: 4 independent masks/tile, eraser isolation, roundtrip, bounded memory\n";
}
