#include "../android/src/main/cpp/roads/road_materials.h"
#include "../android/src/main/cpp/roads/road_model.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <string>
using namespace oe::roads;
static bool near(float a,float b){return std::abs(a-b)<1e-7f;}

int main(){
    assert(!validSurface(-1)&&!validSurface(5));
    assert(validSurface(kAsphalt)&&validSurface(kCobblestone));
    assert(validSurface(kDirt)&&validSurface(kGravel)&&validSurface(kAncientSlate));
    const Road legacy{1,6.f,true,{{0,0},{10,0}}};
    assert(legacy.material==kAsphalt); // 4-field old aggregate remains valid
    assert(valid(legacy,512.f));
    Road street{2,4.f,false,{{0,0},{10,0}},kCobblestone};
    assert(valid(street,512.f));
    assert(street.material==kCobblestone);
    street.material=kAncientSlate;
    assert(valid(street,512.f));
    street.material=5;
    assert(!valid(street,512.f));
    street.material=-1;
    assert(!valid(street,512.f));

    const auto asphalt=surfacePalette(kAsphalt);
    const auto cobble=surfacePalette(kCobblestone);
    const auto dirt=surfacePalette(kDirt);
    const auto gravel=surfacePalette(kGravel);
    const auto slate=surfacePalette(kAncientSlate);
    assert(!asphalt.hasPavers&&!asphalt.hasRuts);
    assert(cobble.hasPavers&&!cobble.hasRuts);
    assert(dirt.hasRuts&&!dirt.hasPavers);
    assert(!gravel.hasRuts&&!gravel.hasPavers);
    assert(slate.hasPavers&&!slate.hasRuts);
    assert(slate.base.r<cobble.base.r);
    assert(asphalt.base.b<gravel.base.b);
    assert(std::string(surfaceName(kCobblestone))!="");
    assert(std::string(surfaceName(kAncientSlate))!="");
    for(int material=0;material<=4;++material){
        const auto rgb=tileColor(material,3,5,7);
        assert(rgb.r>=.02f&&rgb.r<=.95f);
        assert(rgb.g>=.02f&&rgb.g<=.95f);
        assert(rgb.b>=.02f&&rgb.b<=.95f);
        const auto twice=tileColor(material,3,5,7);
        assert(near(rgb.r,twice.r)&&near(rgb.g,twice.g)&&near(rgb.b,twice.b));
    }
    assert(tileHash(2,3,4)==tileHash(2,3,4));
    assert(tileHash(2,3,4)!=tileHash(2,3,5));
    assert(tileHash(2,3,4)!=tileHash(2,4,4));
    assert(detailColumns(2.f)>=1);
    assert(detailColumns(18.f)<=15);
    assert(detailColumns(6.f)>detailColumns(3.f));
    assert(detailColumns(0.f)==0);
    assert(detailColumns(std::nanf(""))==0);
    const int columns=detailColumns(18.f);
    for(std::size_t rows:{1u,4u,100u,2000u,12000u}){
        const std::size_t budget=kDetailTilesTotal/kMaxRoads;
        const int stride=detailStride(rows,columns,budget);
        assert(stride>=1);
        const auto drawnRows=(rows+static_cast<std::size_t>(stride)-1)/static_cast<std::size_t>(stride);
        assert(drawnRows*static_cast<std::size_t>(columns)
                 <=budget+static_cast<std::size_t>(columns)); // bounded rounding
    }
    assert(detailStride(1000,0,100)==0);
    assert(detailStride(1000,5,0)==0);
    std::cout<<"OE medieval road materials: five styles, deterministic palette, budget, legacy IDs OK\n";
}
