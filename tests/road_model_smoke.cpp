#include "../android/src/main/cpp/roads/road_model.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
using namespace oe::roads;
static bool near(float a,float b){return std::abs(a-b)<1e-4f;}
int main(){
  Road r{1,6.f,true,{}};
  assert(!valid(r,512.f));
  assert(append(r,{0,0},512.f,1.f));
  assert(!append(r,{0,0},512.f,1.f));
  assert(append(r,{0,10},512.f,1.f));
  assert(append(r,{10,20},512.f,1.f));
  assert(valid(r,512.f));
  auto points=sample(r);
  assert(points.size()==(r.points.size()-1)*kCurveSubdivisions+1);
  assert(near(points.front().x,0.f)&&near(points.front().z,0.f));
  assert(near(points.back().x,10.f)&&near(points.back().z,20.f));
  assert(near(points[kCurveSubdivisions].x,0.f)&&near(points[kCurveSubdivisions].z,10.f));
  for(const auto&p:points)assert(finitePoint(p));
  assert(!append(r,{600,0},512.f,1.f));
  assert(!append(r,{std::numeric_limits<float>::quiet_NaN(),0},512.f,1.f));
  Road bad=r;bad.width=19.f;assert(!valid(bad,512.f));
  bad=r;bad.id=-2;assert(!valid(bad,512.f));
  bad=r;bad.points[1]=bad.points[0];assert(!valid(bad,512.f));
  bad=r;bad.points[0].x=513.f;assert(!valid(bad,512.f));
  Road full{4,2.f,false,{}};
  for(std::size_t i=0;i<kMaxPoints;++i)
    assert(append(full,{static_cast<float>(i)*2,0},512.f,1.f));
  assert(!append(full,{200,0},512.f,1.f));
  assert(valid(full,512.f));
  std::cout<<"Road model smoke: bounds, interpolation, validation and limits OK\n";
}
