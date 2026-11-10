#include "../android/src/main/cpp/roads/road_network.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>
using namespace oe::roads;

static bool near(float x,float y,float tolerance=.01f) {
    return std::abs(x-y)<=tolerance;
}
static bool hasLink(const Network&network,int a,int b) {
    for(const Junction&junction:network.junctions){
        if(std::find(junction.roadIds.begin(),junction.roadIds.end(),a)
             !=junction.roadIds.end()&&
           std::find(junction.roadIds.begin(),junction.roadIds.end(),b)
             !=junction.roadIds.end())return true;
    }
    return false;
}

int main(){
    const Road eastWest{1,6.f,true,{{-20,0},{20,0}}};
    const Road northSouth{2,8.f,true,{{0,-20},{0,20}}};
    const auto cross=buildNetwork({eastWest,northSouth});
    assert(cross.junctions.size()==1);
    assert(cross.connectedPairs==1);
    assert(hasLink(cross,1,2));
    assert(near(cross.junctions[0].position.x,0));
    assert(near(cross.junctions[0].position.z,0));
    assert(cross.junctions[0].radius>4.f);

    const Road diagonal{3,4.f,false,{{-20,-20},{20,20}}};
    const auto triple=buildNetwork({eastWest,northSouth,diagonal});
    assert(triple.junctions.size()==1);
    assert(triple.connectedPairs==3);
    assert(triple.junctions[0].roadIds.size()==3);

    Road branch{4,4.f,true,{{8,-15},{8,-2.4f}}};
    auto noSnap=buildNetwork({eastWest,branch});
    assert(noSnap.junctions.empty());
    const auto snap=snapEndpoint(branch,false,{eastWest,branch},512.f);
    assert(snap.snapped&&snap.targetRoad==1);
    assert(near(snap.position.x,8.f));
    assert(near(snap.position.z,0.f));
    assert(valid(branch,512.f));
    const auto tjunction=buildNetwork({eastWest,branch});
    assert(tjunction.junctions.size()==1);
    assert(hasLink(tjunction,1,4));

    Road noJoin{5,4.f,false,{{12,-15},{12,-3.5f}}};
    assert(!snapEndpoint(noJoin,false,{eastWest},512.f).snapped);
    assert(near(noJoin.points.back().z,-3.5f));

    Road startBranch{6,4.f,false,{{-2,2.8f},{-2,17}}};
    assert(snapEndpoint(startBranch,true,{eastWest},512.f).snapped);
    assert(near(startBranch.points.front().z,0.f));
    assert(buildNetwork({eastWest,startBranch}).junctions.size()==1);

    // Collinear endpoints are a connection even though cross product is zero.
    const Road inlineA{7,4.f,true,{{0,25},{10,25}}};
    const Road inlineB{8,4.f,true,{{10,25},{20,25}}};
    const auto joined=buildNetwork({inlineA,inlineB});
    assert(joined.junctions.size()==1);
    assert(hasLink(joined,7,8));

    const Road parallel{9,4.f,true,{{-20,10},{20,10}}};
    assert(buildNetwork({eastWest,parallel}).junctions.empty());

    // Handles that would collide with neighbours are not snapped.
    Road nearTiny{10,4.f,false,{{10,1},{10,.1f}}};
    assert(!snapEndpoint(nearTiny,true,{eastWest},512.f).snapped);

    // A proper second crossing produces a separate junction, not a duplicate.
    const Road zigzag{11,5.f,true,{{-10,-10},{-10,10},{10,10},{10,-10}}};
    const auto doubleCross=buildNetwork({eastWest,zigzag});
    assert(doubleCross.junctions.size()==2);
    assert(doubleCross.connectedPairs==2);

    std::cout<<"Road crossings/joins/snap: X, T, 3-way, collinear and limits OK\n";
}
