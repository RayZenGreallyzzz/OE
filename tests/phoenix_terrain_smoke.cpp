#include "../android/src/main/cpp/phoenix_terrain.h"
#include <cassert>
#include <cmath>
#include <vector>
#include <iostream>
using oe::phoenix::Field;
int main(){
 Field terrain;
 std::vector<int> raw={0,0,100,1,1,0,200,2,0,1,300,3,1,1,400,4};
 assert(Field::prepare(10000,1024,raw,terrain));
 assert(terrain.areaSqM==10000&&terrain.nodes.size()==4);
 assert(std::abs(terrain.heightAt(.5f,.5f)-2.5f)<.00001f);
 assert(terrain.affectsChunk(0,0)&&terrain.affectsChunk(-1,-1));
 assert(terrain.heightAt(200,200)==0.f);
 raw[2]=15000;
 assert(!Field::prepare(10000,1024,raw,terrain));
 assert(terrain.nodes.size()==4&&std::abs(terrain.heightAt(.5f,.5f)-2.5f)<.00001f);
 raw[2]=100;
 raw.insert(raw.end(),{0,0,200,2});
 assert(!Field::prepare(10000,1024,raw,terrain)); // duplicate
 assert(!Field::prepare(2000000,1024,std::vector<int>{},terrain)); // world too small
 assert(oe::phoenix::floorDiv(-65,64)==-2);
 assert(terrain.nodes.size()==4); // no scene mutation on rejection
 std::cout<<"OK: UV56 sparse 1m/cm heightfield, bounds, undo-friendly atomic reject\n";
}