#include "../android/src/main/cpp/roads/road_junction_geometry.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
using namespace oe::roads;

static bool near(float a,float b,float tolerance=.001f){
    return std::abs(a-b)<=tolerance;
}
static float signedArea(const std::vector<Point>&outline){
    float area=0.f;
    for(std::size_t i=0;i<outline.size();++i){
        const auto a=outline[i],b=outline[(i+1)%outline.size()];
        area+=cross(a,b);
    }
    return area*.5f;
}
int main(){
    const Road horizontal{1,6.f,true,{{-20,0},{20,0}}};
    const Road vertical{2,8.f,true,{{0,-20},{0,20}}};
    const auto network=buildNetwork({horizontal,vertical});
    const auto surface=makeJunctionSurfaces({horizontal,vertical},network,512.f);
    assert(surface.size()==1);
    const auto&shape=surface.front();
    assert(shape.approaches==4);
    assert(shape.outline.size()>=4);
    assert(signedArea(shape.outline)>35.f);
    assert(shape.minX< -3.f&&shape.maxX>3.f);
    assert(shape.minZ< -3.f&&shape.maxZ>3.f);
    for(Point p:shape.outline){
        assert(finitePoint(p)&&std::abs(p.x)<512.f&&std::abs(p.z)<512.f);
    }
    const auto stripes=markingOutsideJunctions({-20,0},{20,0},surface);
    assert(stripes.size()==2);
    assert(near(stripes.front().begin,0.f));
    assert(near(stripes.back().end,1.f));
    assert(stripes.front().end<.5f&&stripes.back().begin>.5f);
    Interval inside;
    assert(polygonInterval({-20,0},{20,0},shape,inside));
    assert(near(stripes.front().end,inside.begin));
    assert(near(stripes.back().begin,inside.end));
    assert(markingOutsideJunctions({-20,19},{20,19},surface).size()==1);

    // T arm ends at the through-road; polygon has three open branches.
    const Road tArm{3,4.f,true,{{9,-15},{9,0}}};
    const auto t=makeJunctionSurfaces({horizontal,tArm},
                  buildNetwork({horizontal,tArm}),512.f);
    assert(t.size()==1);
    assert(t.front().approaches==3);
    assert(signedArea(t.front().outline)>15.f);
    assert(t.front().minZ< -2.f&&t.front().maxZ>1.f);
    const auto tStripe=markingOutsideJunctions({9,-15},{9,0},t);
    assert(tStripe.size()==1);
    assert(tStripe[0].end<1.f); // centre dash stops at intersection mouth

    // Straight, equal-width coincident ends do not need an asphalt blob.
    const Road inlineA{4,4.f,true,{{0,25},{10,25}}};
    const Road inlineB{5,4.f,true,{{10,25},{20,25}}};
    const auto aligned=makeJunctionSurfaces({inlineA,inlineB},
                     buildNetwork({inlineA,inlineB}),512.f);
    assert(aligned.empty());

    // A diagonal through-road also contributes its exact mouth bearing.
    const Road diagonal{6,4.f,false,{{-20,-20},{20,20}}};
    const auto multi=makeJunctionSurfaces({horizontal,vertical,diagonal},
                    buildNetwork({horizontal,vertical,diagonal}),512.f);
    assert(multi.size()==1);
    assert(multi.front().approaches==6);
    assert(signedArea(multi.front().outline)>signedArea(shape.outline)*.5f);

    // Two distinct junctions on one road clip a long centreline into 3.
    const Road a{7,4.f,true,{{-10,-15},{-10,15}}};
    const Road b{8,4.f,true,{{10,-15},{10,15}}};
    const auto doubleSurface=makeJunctionSurfaces({horizontal,a,b},
                   buildNetwork({horizontal,a,b}),512.f);
    assert(doubleSurface.size()==2);
    const auto pieces=markingOutsideJunctions({-20,0},{20,0},doubleSurface);
    assert(pieces.size()==3);
    for(const auto range:pieces)assert(range.end>range.begin);

    // Empty/invalid data is harmless; no polygon clips line markings.
    assert(makeJunctionSurfaces({},buildNetwork({}),512.f).empty());
    assert(markingOutsideJunctions({-1,0},{1,0},{}).size()==1);
    assert(!polygonInterval({-20,100},{20,100},shape,inside));
    std::cout<<"Width-aware X/T junction geometry and marking clipping OK\n";
}
