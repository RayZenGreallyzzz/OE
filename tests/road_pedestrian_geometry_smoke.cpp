#include "../android/src/main/cpp/roads/road_pedestrian_geometry.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>
using namespace oe::roads;

static bool nearly(float a,float b,float tolerance=.05f){
    return std::abs(a-b)<tolerance;
}
static void inspectCrossings(const std::vector<JunctionSurface>&surfaces,
                             const std::vector<Crosswalk>&crossings,float halfWorld){
    for(const Crosswalk&walk:crossings){
        assert(walk.roadId>0&&!walk.bars.empty());
        assert(finitePoint(walk.centre));
        assert(nearly(distanceSquared(walk.direction,{0,0}),1.f));
        for(const PedestrianQuad&bar:walk.bars){
            for(Point p:{bar.a,bar.b,bar.c,bar.d}){
                assert(validFootPoint(p,halfWorld));
                assert(!insideAnyJunction(p,surfaces));
            }
            const Point span=subtract(bar.b,bar.a);
            const Point along=subtract(bar.d,bar.a);
            assert(nearly(std::sqrt(distanceSquared(bar.a,bar.b)),.42f));
            assert(nearly(std::sqrt(distanceSquared(bar.a,bar.d)),kCrosswalkDepth));
            assert(std::abs(dot2(span,along))<.002f);
        }
    }
}
int main(){
    const Road avenue{1,6.f,true,{{-25,0},{25,0}}};
    const Road street{2,8.f,true,{{0,-25},{0,25}}};
    const auto roads=std::vector<Road>{avenue,street};
    const auto shapes=makeJunctionSurfaces(roads,buildNetwork(roads),512.f);
    assert(shapes.size()==1);
    const auto&x=shapes[0];
    assert(x.approaches==4&&x.mouths.size()==4);
    const auto corners=junctionCornerSidewalks(x,512.f);
    assert(corners.size()>=3);
    for(const auto&apron:corners){
        assert(!mouthEdge(x,apron.a,apron.b));
        assert(validFootPoint(apron.a,512.f));
        assert(validFootPoint(apron.b,512.f));
        assert(validFootPoint(apron.c,512.f));
        assert(validFootPoint(apron.d,512.f));
        const Point outerMid=mix2(apron.c,apron.d,.5f);
        assert(!insideJunction(outerMid,x));
    }
    const auto xWalks=makeCrosswalks(shapes,512.f);
    assert(xWalks.size()==4);
    inspectCrossings(shapes,xWalks,512.f);
    for(const Crosswalk&walk:xWalks){
        assert(walk.bars.size()>=3);
        assert(!insideJunction(walk.centre,x));
        assert(dot2(walk.direction,
                    subtract(walk.centre,x.centre))>0.f);
    }

    const Road arm{3,4.f,true,{{8,-22},{8,0}}};
    const auto tRoads=std::vector<Road>{avenue,arm};
    const auto tShapes=makeJunctionSurfaces(tRoads,buildNetwork(tRoads),512.f);
    assert(tShapes.size()==1&&tShapes.front().approaches==3);
    const auto tWalks=makeCrosswalks(tShapes,512.f);
    assert(tWalks.size()==3);
    inspectCrossings(tShapes,tWalks,512.f);

    // A short dead-end arm must not receive a zebra on nonexistent asphalt.
    const Road shortArm{4,4.f,true,{{12,-3},{12,0}}};
    const auto shortRoads=std::vector<Road>{avenue,shortArm};
    const auto shortShapes=makeJunctionSurfaces(shortRoads,
                                               buildNetwork(shortRoads),512.f);
    if(!shortShapes.empty()){
        const auto shortWalks=makeCrosswalks(shortShapes,512.f);
        for(const Crosswalk&w:shortWalks)assert(w.roadId!=4);
    }

    // A straight shared endpoint is not an intersection and gets no crossings.
    const Road straightA{5,6.f,true,{{0,35},{12,35}}};
    const Road straightB{6,6.f,true,{{12,35},{24,35}}};
    const auto straight=makeJunctionSurfaces({straightA,straightB},
                                  buildNetwork({straightA,straightB}),512.f);
    assert(straight.empty());
    assert(makeCrosswalks(straight,512.f).empty());

    // Disabled/junction-free geometry must not produce floating traffic marks.
    assert(junctionCornerSidewalks(JunctionSurface{},512.f).empty());
    assert(makeCrosswalks(shapes,0.f).empty());
    assert(makeCrosswalks({},512.f).empty());

    const Road nearBoundary{7,6.f,true,{{492,0},{511,0}}};
    const Road throughEdge{8,6.f,true,{{504,-10},{504,10}}};
    const auto edgeShapes=makeJunctionSurfaces(
        {nearBoundary,throughEdge},buildNetwork({nearBoundary,throughEdge}),512.f);
    const auto edgeWalks=makeCrosswalks(edgeShapes,512.f);
    inspectCrossings(edgeShapes,edgeWalks,512.f);
    for(const auto&surface:edgeShapes){
        for(const auto&quad:junctionCornerSidewalks(surface,512.f))
            for(const Point p:{quad.a,quad.b,quad.c,quad.d})
                assert(validFootPoint(p,512.f));
    }
    std::cout<<"Road pedestrians: X/T curbs, aprons, zebra safety and map edges OK\n";
}
