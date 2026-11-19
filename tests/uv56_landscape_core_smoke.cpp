#include "../android/src/main/cpp/terrain/uv56_landscape_core.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>
int main(){
 using namespace oe::uv56;
 Terrain terrain;
 assert(!terrain.create(999));
 assert(terrain.create(10000));
 assert(terrain.active()&&terrain.areaSqM()==10000);
 assert(!terrain.brushAt(51,0,Tool::Raise,4,2));
 terrain.beginStroke(0,0);
 assert(terrain.brushAt(0,0,Tool::Raise,1,2));
 assert(terrain.at(0,0).h==100); // original 2*100*0.5 UV56 centre
 const auto count=terrain.nodeCount();
 assert(!terrain.brushAt(0,0,Tool::Raise,1,2)); // repeated stroke must not spike
 assert(terrain.nodeCount()==count);
 terrain.endStroke();
 assert(std::fabs(terrain.heightAt(0,0)-1.f)<.0001f);
 assert(terrain.undo()&&terrain.at(0,0).h==0);
 assert(terrain.redo()&&terrain.at(0,0).h==100);
 auto before=terrain.snapshot();
 assert(!terrain.restore(10000,{{0,0,200,0},{0,0,300,0}})); // duplicate
 assert(terrain.snapshot()==before); // failure must not erase edited world
 std::vector<std::array<int,4>> src={{0,0,250,3},{1,0,300,4},{0,1,350,5},{1,1,400,0}};
 assert(terrain.restore(10000,src));
 assert(terrain.at(0,0).p==3&&terrain.at(1,0).p==4);
 assert(std::fabs(terrain.heightAt(.5f,.5f)-3.25f)<.0001f);
 terrain.beginStroke(.5f,.5f);
 assert(terrain.brushAt(0,0,Tool::Flatten,3,2));
 terrain.endStroke();
 assert(terrain.undo()&&terrain.at(0,0).h==250&&terrain.at(1,1).h==400);
 terrain.beginStroke(.5f,.5f);
 assert(terrain.brushAt(0,0,Tool::Smooth,3,2));
 terrain.endStroke();
 assert(terrain.undo()&&terrain.at(0,0).h==250&&terrain.at(1,1).h==400);
 assert(!terrain.restore(10000,{{0,0,0,7}},0)); // no unapproved material id
 assert(!terrain.restore(10000,{{0,0,60001,0}},0));
 Terrain drag;
 assert(drag.create(10000));
 drag.beginStroke(0,0);
 assert(drag.brushAt(0,0,Tool::Raise,4,2));
 assert(drag.brushAt(5,0,Tool::Raise,4,2));
 drag.endStroke();
 assert(drag.at(0,0).h>0&&drag.at(5,0).h>0);
 assert(drag.undo()&&drag.at(0,0).h==0&&drag.at(5,0).h==0);
 assert(drag.redo()&&drag.at(0,0).h>0&&drag.at(5,0).h>0);

 std::cout<<"OK: native UV56 heights: cm precision, falloff, no spike, "
             "undo, redo, flatten, smooth and atomic restore\n";
}
