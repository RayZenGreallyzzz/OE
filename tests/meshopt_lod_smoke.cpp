// Real static-mesh LOD smoke: weld flat triangle soup, keep UV/colors,
// simplify while ensuring valid in-range indices and saved source bytes.
#include "../android/src/main/cpp/vendor/meshoptimizer/meshoptimizer.h"
#include <cassert>
#include <cstdio>
#include <vector>
#include <cstring>
struct Vertex{
 float px,py,pz,nx,ny,nz,r,g,b,u,v;
};
int main(){
 std::vector<Vertex> soup;
 constexpr int steps=24;
 auto push=[&](float x,float z){
  soup.push_back({x,0.f,z,0.f,1.f,0.f,.8f,.85f,.9f,x/24.f,z/24.f});
 };
 for(int z=0;z<steps;z++)for(int x=0;x<steps;x++){
  push(x,z);push(x+1,z);push(x+1,z+1);
  push(x,z);push(x+1,z+1);push(x,z+1);
 }
 const std::vector<Vertex> original=soup;
 const size_t n=soup.size();
 std::vector<unsigned int> remap(n),indices(n);
 size_t unique=meshopt_generateVertexRemap(
   remap.data(),nullptr,n,soup.data(),n,sizeof(Vertex));
 assert(unique>10&&unique<n);
 std::vector<Vertex> welded(unique);
 meshopt_remapVertexBuffer(welded.data(),soup.data(),n,sizeof(Vertex),remap.data());
 meshopt_remapIndexBuffer(indices.data(),nullptr,n,remap.data());
 const float weights[8]={1,1,1,.25f,.25f,.25f,2,2};
 for(int stage=1;stage<=2;stage++){
  std::vector<unsigned int> smaller(n);
  float error=0;
  size_t result=meshopt_simplifyWithAttributes(smaller.data(),indices.data(),n,
    &welded[0].px,unique,sizeof(Vertex),&welded[0].nx,sizeof(Vertex),
    weights,8,nullptr,
    (n*(stage==1?55:28)/100/3)*3,
    stage==1?.012f:.046f,meshopt_SimplifyPermissive,&error);
  assert(result>=3&&result%3==0&&result<=n);
  for(size_t i=0;i<result;i++)assert(smaller[i]<unique);
  std::printf("LOD %d: %zu -> %zu triangles (error %f)\n",
    stage,n/3,result/3,error);
 }
 assert(std::memcmp(original.data(),soup.data(),n*sizeof(Vertex))==0);
 std::puts("OE_MESHOPT_LOD_OK");
 return 0;
}
