#include "../android/src/main/cpp/roads/road_network.h"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <limits>
#include <iostream>

using namespace oe::roads;
int main(){
    Network roads;
    auto id=roads.create("Main street","builtin:cobblestone",6.0,{{0,0},{10,0}});
    assert(id!=0&&roads.all().size()==1&&roads.find(id)!=nullptr);
    assert(roads.find(id)->widthMetres==6.0);
    SurfaceMesh mesh;
    const TerrainHeight level=[](double,double){return 2.0;};
    assert(buildSurfaceMesh(*roads.find(id),level,mesh,2.0));
    assert(mesh.triangles.size()==30); // 5 segments, 2 triangles each
    for(const auto& v:mesh.triangles){
        assert(std::abs(v.y-2.03f)<1.e-5f);
        assert(std::abs(v.z)<=3.01f);
    }
    assert(std::abs(mesh.triangles.front().v)<1.e-6f);
    float maxV=0.0f;
    for(const auto& vertex:mesh.triangles)maxV=std::max(maxV,vertex.v);
    assert(maxV>4.9f);
    const auto before=mesh.triangles.size();
    assert(!buildSurfaceMesh(*roads.find(id),
        [](double,double){return std::numeric_limits<double>::quiet_NaN();},
        mesh,2.0));
    assert(mesh.triangles.size()==before); // failure is atomic
    Road change=*roads.find(id);
    change.widthMetres=-1.0;
    assert(!roads.update(change));
    assert(roads.find(id)->widthMetres==6.0);
    change.widthMetres=4.0;
    change.controlPoints={{0,0},{5,1},{10,0}};
    assert(roads.update(change));
    assert(buildSurfaceMesh(*roads.find(id),level,mesh,1.0));
    assert(!mesh.triangles.empty());
    auto copy=roads.all();
    copy.push_back(copy.front()); // duplicate ID
    assert(!roads.replaceAll(copy));
    assert(roads.all().size()==1);
    auto original=roads.all();
    roads.clear();
    assert(roads.all().empty());
    assert(roads.replaceAll(original));
    assert(roads.find(id)!=nullptr);
    auto second=roads.create("Crossroad","builtin:dirt",2.0,{{0,10},{10,10}});
    assert(second!=0&&second!=id);
    assert(roads.remove(id)&&!roads.remove(id));
    assert(!roads.replaceAll({Road{0,"bad","builtin:dirt",1.0,{{0,0},{1,1}}}}));
    assert(roads.find(second)!=nullptr);
    assert(roads.create("bad","builtin:stone",6.0,{{0,0},{0,0}})==0);
    assert(roads.create("bad","builtin:stone",6.0,
           {{0,0},{std::numeric_limits<double>::infinity(),0}})==0);
    std::cout<<"OE_ROAD_NETWORK_OK: stable IDs, validation, atomic replacement, terrain mesh\n";
    return 0;
}
