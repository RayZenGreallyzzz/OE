// OE Native 0.2. GLES 3.0/C++ scene and renderer; no Godot dependency.
#include <jni.h>
#include "vendor/meshoptimizer/meshoptimizer.h"
#include "quality_profiles.h"
#include "runtime/oe_runtime.h"
#include "roads/road_network.h"
#include "roads/road_junctions.h"
#include "roads/road_topology.h"
#include "roads/medieval_surface.h"
#include "roads/bridge_links.h"
#include "medieval/structures.h"
#include "water/lake_surface.h"
#include "terrainpaint/paint_tiles.h"
#include "terrainpaint/material_layers.h"
#include "vendor/basisu/transcoder/basisu_transcoder.h"
#include <GLES3/gl3.h>
#include <android/log.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <chrono>
#include <limits>
#include <mutex>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>
#include <unordered_map>
#include <map>
#include <tuple>
#include <cstdint>

namespace {
constexpr float PI = 3.14159265358979323846f;
constexpr float RAD = PI / 180.0f;
constexpr int MAX_ENTITIES = 300;
struct Vec3 {float x, y, z;};
struct Vec4 {float x, y, z, w;};
struct Mat4 {float m[16]{};};
struct Mesh {GLuint vao=0, buffer=0; GLsizei count=0;};
struct Entity {int id=0, type=0; float x=0, z=0, yaw=0, size=1; std::string asset; float y=0.f, sx=1.f,sy=1.f,sz=1.f; std::string paint;};
struct Color {float r,g,b;};
struct Shader {
 GLuint program=0;
 GLint locMvp=-1, locModel=-1, locColor=-1, locLight=-1;
 GLint locTexture=-1,locTextured=-1,locPaintOverride=-1,locOverlay=-1,locPaintCenter=-1,locPaintRadius=-1;
 GLint locGuide=-1,locOpacity=-1;
 GLint locMaterialLayer=-1,locLayerMask=-1;
};
struct TextureResource {
 GLuint id=0;
 int width=0,height=0,format=0; // 0 RGBA8, 1 ETC2 RGB, 2 ETC2 RGBA
 std::vector<unsigned char> bytes; // Retained to restore an EGL context.
};
struct AssetBatch {int first=0,count=0,texture=-1;};
struct AssetLod{
 Mesh gpu;
 std::vector<float> vertices;
 std::vector<AssetBatch> batches;
};
struct Asset {
 Mesh gpu;
 int vertexStride=9;
 std::vector<float> vertices; // Retained for GPU context recreation.
 std::vector<AssetBatch> batches;
 std::vector<TextureResource> textures;
 std::array<AssetLod,2> lods; // Tier 1/2, original remains tier 0.
 float centerY=0.f, radius=0.f;
};
static Mesh gMeshes[3];
static Mesh gStaticMeshes[29];
static Mesh gPaintMesh; // Every decorative object is a single combined draw.
void bakeStaticMesh(int type);
// Cached, combined flat terrain: one GLES draw call per frame instead of one per tile.
struct TerrainGpu {
 GLuint vao=0, buffer=0;
 GLsizei vertices=0;
 int gridX=2147483647,gridZ=2147483647,worldSize=0,tiles=0,revision=-1;
};
static TerrainGpu gTerrain;
static int gDrawCalls=0,gTriangles=0;
static int gQualityTier=oe::Low;
static float gSubmitCpuMs=0.f;
const oe::QualityProfile&quality(){return oe::profile(gQualityTier);}
static std::unordered_map<std::string,Asset> gAssets;
static std::unordered_map<std::string,TextureResource> gPaintTextures;
static constexpr size_t MAX_PAINT_TEXTURES=8,MAX_PAINT_CACHE_BYTES=32u*1024u*1024u;
static constexpr size_t MAX_TEXTURE_CACHE_BYTES=96u*1024u*1024u;
static constexpr size_t MAX_LOD_CACHE_BYTES=40u*1024u*1024u;
static int gLodDrawCounts[3]={0,0,0};
size_t lodCacheBytes(){
 size_t n=0;
 for(const auto&kv:gAssets)for(const auto&lod:kv.second.lods)
   n+=lod.vertices.size()*sizeof(float);
 return n;
}
size_t paintCacheBytes(){
 size_t n=0;for(const auto&kv:gPaintTextures)n+=kv.second.bytes.size();return n;
}
size_t textureCacheBytes(){
 size_t bytes=0;
 for(const auto&item:gAssets)for(const auto&tex:item.second.textures)
  bytes+=tex.bytes.size();
 return bytes;
}
static constexpr int MAX_ASSETS=12;
static constexpr int MAX_IMPORTED_TRIANGLES=90000;
static Shader gShader;
static std::vector<Entity> gEntities;
static int gNextId=1, gSelected=0, gWidth=1, gHeight=1;
static int gGizmoMode=0; // 0=move,1=yaw,2=scale
static bool gGizmoActive=false;
static int gGizmoActiveId=0;
static float gYaw=.55f, gPitch=.45f, gDistance=28.f;
static Vec3 gTarget{0,1.2f,1.3f};
static bool gMapTopDown=false;
static bool gSceneInitialized=false, gDemoRoads=false;
constexpr float WORLD_TILE=64.0f;
static int gWorldSize=1024; // Set in app to 1024/2048/4096/8192/16384 meters.
struct TerrainBrush {float x,z,radius,strength;};
struct TerrainPaint {float x,z,radius;std::string texture;};
static std::vector<TerrainPaint> gTerrainPaint;
static constexpr size_t MAX_TERRAIN_PAINT=48;
static std::vector<TerrainBrush> gBrushes;
static int gTerrainRevision=0;
// R4 composited paint tiles: GPU cache is disposable; authoring bytes are portable.
static oe::terrainpaint::Canvas gPaintTiles;
static oe::terrainpaint::MaterialCanvas gMaterialCanvas;
struct MaterialGpu {GLuint texture=0;std::uint32_t revision=0;};
static std::map<std::tuple<int,int,std::string>,MaterialGpu> gMaterialGpu;
static bool gMaterialStrokeActive=false,gMaterialEraser=false;
static float gLayerX=0,gLayerZ=0,gLayerRadius=2.f,gLayerStrength=.85f;
static std::string gLayerTexture;
void clearMaterialGpu(){
 for(auto& entry:gMaterialGpu)if(entry.second.texture)
  glDeleteTextures(1,&entry.second.texture);
 gMaterialGpu.clear();
}
struct PaintTileGpu {GLuint texture=0;std::uint32_t revision=0;};
static std::map<std::pair<int,int>,PaintTileGpu> gPaintTileGpu;
static bool gPrecisionPaintActive=false,gPaintCursorVisible=false,gPaintEraser=false;
static float gPaintCursorX=0,gPaintCursorZ=0,gPaintCursorRadius=2.f;
static float gStrokeX=0,gStrokeZ=0,gStrokeRadius=2.f,gStrokeOpacity=.8f;
static std::string gStrokeTexture;

// Editor-only PNG guide is intentionally NOT a Runtime Core entity/material.
struct MapGuide {
 GLuint texture=0;
 Mesh mesh;
 std::vector<unsigned char> rgba; // retained across EGL context recreation
 int pixelWidth=0,pixelHeight=0;
 double centerX=0,centerZ=0,widthMetres=512,heightMetres=512;
 float opacity=.5f;
 bool visible=true;
 int revision=0,meshRevision=-1,terrainRevision=-1,gridX=2147483647,gridZ=2147483647;
};
static MapGuide gMapGuide,gMapStage;
static constexpr size_t MAX_MAP_RGBA_BYTES=2048u*2048u*4u;
bool validMapBounds(double cx,double cz,double w,double h,float opacity){
 return std::isfinite(cx)&&std::isfinite(cz)&&std::isfinite(w)&&std::isfinite(h)&&
        std::isfinite(opacity)&&std::abs(cx)<=8192.0&&std::abs(cz)<=8192.0&&
        w>=8.0&&h>=8.0&&w<=16384.0&&h<=16384.0&&
        opacity>=.05f&&opacity<=.95f;
}
static constexpr size_t MAX_TERRAIN_BRUSHES=64;

// Edit-time road network, independent from portable game runtime.
static oe::roads::Network gRoads;
static std::uint64_t gSelectedRoad=0,gActiveRoad=0;
static int gSelectedRoadPoint=-1;
static bool gRoadPending=false;
static oe::roads::Point gRoadFirstPoint{};
static double gRoadWidth=6.0;
static std::string gRoadMaterial="builtin:cobblestone";
static int gRoadRevision=1,gRoadGpuRevision=-1,gRoadGpuTerrainRevision=-1;
static Mesh gRoadGpu;
struct RoadDrawRange {GLint first=0;GLsizei count=0;std::uint64_t id=0;};
static std::vector<RoadDrawRange> gRoadRanges;
static constexpr size_t MAX_ROAD_GPU_VERTICES=120000;
// Lake data is editor-owned, saved with the scene, and rendered with a
// single cached translucent GPU mesh. Revisions also invalidate after Undo.
static std::vector<oe::water::Lake> gLakes;
static int gSelectedLake=-1,gLakeRevision=1;
struct LakeGpu {
 GLuint vao=0,buffer=0;
 GLsizei count=0;
 int lakeRevision=-1,terrainRevision=-1;
};
static LakeGpu gLakeGpu;

float terrainHeightAt(float x,float z){
 float h=0;
 for(const auto&b:gBrushes){
  const float dx=x-b.x,dz=z-b.z,d2=dx*dx+dz*dz;
  if(d2>=b.radius*b.radius)continue;
  const float t=1.f-std::sqrt(d2)/b.radius;
  h+=b.strength*t*t*(3.f-2.f*t);
 }
 return std::max(-24.f,std::min(140.f,h));
}
// Bounded snapshots keep undo predictable on RAM-constrained Android devices.
struct SceneSnapshot {
 std::vector<Entity> entities;
 int nextId=1, selected=0, worldSize=1024;
 bool demoRoads=false;
 Vec3 target{0,1.2f,1.3f};
 float cameraDistance=28.f;
 std::vector<TerrainBrush> terrain;
 std::vector<TerrainPaint> paints;
 std::vector<oe::roads::Road> roads;
 std::uint64_t selectedRoad=0;
 oe::terrainpaint::Canvas precisePaint;
 oe::terrainpaint::MaterialCanvas materialPaint;
 std::vector<oe::water::Lake> lakes;
 int selectedLake=-1;
};
static std::vector<SceneSnapshot> gUndo,gRedo;
static bool gSuspendHistory=false;
static constexpr size_t HISTORY_LIMIT=12; // bounded undo when 2x paint canvases are present
SceneSnapshot captureScene(){return {gEntities,gNextId,gSelected,gWorldSize,gDemoRoads,
    gTarget,gDistance,gBrushes,gTerrainPaint,gRoads.all(),gSelectedRoad,gPaintTiles,gMaterialCanvas,gLakes,gSelectedLake};}
void restoreScene(const SceneSnapshot&s){
 gEntities=s.entities;gNextId=s.nextId;gSelected=s.selected;gWorldSize=s.worldSize;
 gDemoRoads=s.demoRoads;gTarget=s.target;gDistance=s.cameraDistance;
 gBrushes=s.terrain;gTerrainPaint=s.paints;gTerrainRevision++;gSceneInitialized=true;
 gLakes=s.lakes;gSelectedLake=s.selectedLake;++gLakeRevision;
 gRoads.replaceAll(s.roads);gSelectedRoad=s.selectedRoad;
 gRoadPending=false;gActiveRoad=0;gSelectedRoadPoint=-1;++gRoadRevision;
 gPaintTiles=s.precisePaint;
 gMaterialCanvas=s.materialPaint;clearMaterialGpu();gMaterialStrokeActive=false;
 for(auto& gpu:gPaintTileGpu)if(gpu.second.texture)glDeleteTextures(1,&gpu.second.texture);
 gPaintTileGpu.clear();gPrecisionPaintActive=false;gPaintCursorVisible=false;
 gMaterialGpu.clear();gMaterialStrokeActive=false;
}
void pushBounded(std::vector<SceneSnapshot>&stack,SceneSnapshot value){
 if(stack.size()>=HISTORY_LIMIT)stack.erase(stack.begin());
 stack.push_back(std::move(value));
}
void recordEdit(){
 if(!gSceneInitialized||gSuspendHistory)return;
 pushBounded(gUndo,captureScene());gRedo.clear();
}
bool undoEdit(){
 if(gUndo.empty())return false;
 pushBounded(gRedo,captureScene());
 SceneSnapshot snapshot=std::move(gUndo.back());gUndo.pop_back();
 restoreScene(snapshot);return true;
}
bool redoEdit(){
 if(gRedo.empty())return false;
 pushBounded(gUndo,captureScene());
 SceneSnapshot snapshot=std::move(gRedo.back());gRedo.pop_back();
 restoreScene(snapshot);return true;
}
float worldHalf(){return static_cast<float>(gWorldSize)*.5f;}
bool roadWithinWorld(const oe::roads::Road& road,int worldSize){
 if(!oe::roads::validRoad(road))return false;
 const double bound=worldSize*.5-road.widthMetres*2.0;
 for(auto p:road.controlPoints)if(std::abs(p.x)>bound||std::abs(p.z)>bound)return false;
 return true;
}
bool roadBudget(const std::vector<oe::roads::Road>& roads,int size){
 if(roads.size()>oe::roads::kMaxRoads)return false;
 size_t verts=0;
 for(const auto& r:roads){
  if(!roadWithinWorld(r,size))return false;
  for(size_t i=1;i<r.controlPoints.size();++i){
   const auto a=r.controlPoints[i-1],b=r.controlPoints[i];
   const double len=std::hypot(a.x-b.x,a.z-b.z);
   const size_t steps=static_cast<size_t>(std::ceil(len/oe::roads::kDefaultMeshStep));
   if(steps>oe::roads::kMaxMeshSamples||
      verts>MAX_ROAD_GPU_VERTICES||
      steps>(MAX_ROAD_GPU_VERTICES-verts)/6)return false;
   verts+=steps*6;
  }
 }
 return true;
}

bool allowedWorldSize(int size){
 return size==1024||size==2048||size==4096||size==8192||size==16384;
}
struct Location {float x,z; const char* name;};
static constexpr Location LOCATIONS[]={
 {0,0,"Город"}, {256,0,"Волшебный лес"},
 {-256,-64,"Древние руины"}, {16,-256,"Катакомбы"}, {32,256,"Дикие земли"}
};

float clampf(float v,float a,float b){return std::max(a,std::min(b,v));}
Vec3 sub(Vec3 a,Vec3 b){return {a.x-b.x,a.y-b.y,a.z-b.z};}
Vec3 cross(Vec3 a,Vec3 b){return {a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x};}
float dot(Vec3 a,Vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
Vec3 unit(Vec3 v){float l=std::sqrt(dot(v,v));return l>1e-7f?Vec3{v.x/l,v.y/l,v.z/l}:Vec3{0,1,0};}
Mat4 identity(){Mat4 o;for(int i=0;i<4;i++)o.m[i*4+i]=1;return o;}
Mat4 mul(const Mat4&a,const Mat4&b){
 Mat4 r;
 for(int c=0;c<4;c++)for(int row=0;row<4;row++)
 for(int k=0;k<4;k++)r.m[c*4+row]+=a.m[k*4+row]*b.m[c*4+k];
 return r;
}
Mat4 translate(Vec3 v){auto m=identity();m.m[12]=v.x;m.m[13]=v.y;m.m[14]=v.z;return m;}
Mat4 scale(Vec3 v){Mat4 m;m.m[0]=v.x;m.m[5]=v.y;m.m[10]=v.z;m.m[15]=1;return m;}
Mat4 rotateY(float a){Mat4 m=identity();float c=std::cos(a),s=std::sin(a);m.m[0]=c;m.m[8]=s;m.m[2]=-s;m.m[10]=c;return m;}
Mat4 perspective(float aspect){
 float f=1/std::tan(PI/6), near=.1f, far=560.f;Mat4 m;
 m.m[0]=f/aspect;m.m[5]=f;
 m.m[10]=(far+near)/(near-far);m.m[11]=-1;
 m.m[14]=2*far*near/(near-far);return m;
}
Mat4 lookAt(Vec3 eye,Vec3 center){
 Vec3 forward=unit(sub(center,eye)), right=unit(cross(forward,{0,1,0})), up=cross(right,forward);
 Mat4 m=identity();
 m.m[0]=right.x;m.m[4]=right.y;m.m[8]=right.z;
 m.m[1]=up.x;m.m[5]=up.y;m.m[9]=up.z;
 m.m[2]=-forward.x;m.m[6]=-forward.y;m.m[10]=-forward.z;
 m.m[12]=-dot(right,eye);m.m[13]=-dot(up,eye);m.m[14]=dot(forward,eye);
 return m;
}
Vec4 apply(const Mat4&m,Vec4 p){
 return {
 m.m[0]*p.x+m.m[4]*p.y+m.m[8]*p.z+m.m[12]*p.w,
 m.m[1]*p.x+m.m[5]*p.y+m.m[9]*p.z+m.m[13]*p.w,
 m.m[2]*p.x+m.m[6]*p.y+m.m[10]*p.z+m.m[14]*p.w,
 m.m[3]*p.x+m.m[7]*p.y+m.m[11]*p.z+m.m[15]*p.w
 };
}
Vec3 cameraEye(){float c=std::cos(gPitch);return {gTarget.x+std::sin(gYaw)*c*gDistance,gTarget.y+std::sin(gPitch)*gDistance,gTarget.z+std::cos(gYaw)*c*gDistance};}
Mat4 viewProjection(){return mul(perspective(float(gWidth)/std::max(1,gHeight)),lookAt(cameraEye(),gTarget));}
void addVertex(std::vector<float>&d,Vec3 p,Vec3 n){d.insert(d.end(),{p.x,p.y,p.z,n.x,n.y,n.z});}
void triangle(std::vector<float>&d,Vec3 a,Vec3 b,Vec3 c){Vec3 n=unit(cross(sub(b,a),sub(c,a)));addVertex(d,a,n);addVertex(d,b,n);addVertex(d,c,n);}
void quad(std::vector<float>&d,Vec3 a,Vec3 b,Vec3 c,Vec3 e){triangle(d,a,b,c);triangle(d,a,c,e);}
std::vector<float> geometry(int kind){
 std::vector<float> d;d.reserve(16*6*12);
 if(kind==1){
  Vec3 a{-.5f,0,-.5f},b{.5f,0,-.5f},c{0,.75f,-.5f},e{-.5f,0,.5f},f{.5f,0,.5f},g{0,.75f,.5f};
  triangle(d,a,c,b);triangle(d,e,f,g);quad(d,e,g,c,a);quad(d,b,c,g,f);quad(d,a,b,f,e);
 }else if(kind==2){
  for(int i=0;i<16;i++){float a=2*PI*i/16,b=2*PI*(i+1)/16;
   Vec3 v{.5f*std::cos(a),-.5f,.5f*std::sin(a)};
   Vec3 w{.5f*std::cos(b),-.5f,.5f*std::sin(b)};
   quad(d,v,{v.x,.5f,v.z},{w.x,.5f,w.z},w);
   triangle(d,{0,.5f,0},{v.x,.5f,v.z},{w.x,.5f,w.z});
   triangle(d,{0,-.5f,0},w,v);
  }
 }else{
  const float x=.5f,y=.5f,z=.5f;
  quad(d,{-x,-y,z},{x,-y,z},{x,y,z},{-x,y,z});
  quad(d,{x,-y,-z},{-x,-y,-z},{-x,y,-z},{x,y,-z});
  quad(d,{-x,-y,-z},{-x,-y,z},{-x,y,z},{-x,y,-z});
  quad(d,{x,-y,z},{x,-y,-z},{x,y,-z},{x,y,z});
  quad(d,{-x,y,z},{x,y,z},{x,y,-z},{-x,y,-z});
  quad(d,{-x,-y,-z},{x,-y,-z},{x,-y,z},{-x,-y,z});
 }
 return d;
}
void uploadLod(AssetLod&lod);
void uploadAsset(Asset&asset){
 const auto &v=asset.vertices;
 if(v.empty())return;
 Mesh &m=asset.gpu;
 m.count=static_cast<GLsizei>(v.size()/static_cast<size_t>(asset.vertexStride));
 glGenVertexArrays(1,&m.vao);glBindVertexArray(m.vao);
 glGenBuffers(1,&m.buffer);glBindBuffer(GL_ARRAY_BUFFER,m.buffer);
 glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(v.size()*sizeof(float)),v.data(),GL_STATIC_DRAW);
 const GLsizei stride=static_cast<GLsizei>(asset.vertexStride*sizeof(float));
 for(int attrib=0;attrib<3;attrib++){
  glEnableVertexAttribArray(attrib);
  glVertexAttribPointer(attrib,3,GL_FLOAT,GL_FALSE,stride,
   reinterpret_cast<void*>(static_cast<uintptr_t>(attrib*3*sizeof(float))));
 }
 if(asset.vertexStride==11){
  glEnableVertexAttribArray(3);
  glVertexAttribPointer(3,2,GL_FLOAT,GL_FALSE,stride,
   reinterpret_cast<void*>(static_cast<uintptr_t>(9*sizeof(float))));
 }else glDisableVertexAttribArray(3);
 glBindVertexArray(0);
}
void uploadLod(AssetLod&lod){
 if(lod.vertices.empty())return;
 Asset temp;temp.vertexStride=11;
 temp.vertices=lod.vertices;
 uploadAsset(temp);
 lod.gpu=temp.gpu;
}
void buildStaticLods(Asset&asset){
 if(asset.vertexStride!=11||asset.vertices.size()<11*450||
    asset.batches.empty())return; // tiny props gain nothing
 const float weights[8]={1.0f,1.0f,1.0f,.25f,.25f,.25f,2.0f,2.0f};
 const size_t stride=11*sizeof(float);
 for(int tier=0;tier<2;tier++){
  AssetLod&lod=asset.lods[tier];
  const float reduction=tier==0?.55f:.28f;
  const float errorLimit=tier==0?.012f:.046f;
  for(const AssetBatch&batch:asset.batches){
   const size_t n=static_cast<size_t>(batch.count);
   const float*src=asset.vertices.data()+static_cast<size_t>(batch.first)*11;
   if(n<90||n%3!=0){
    const int first=static_cast<int>(lod.vertices.size()/11);
    lod.vertices.insert(lod.vertices.end(),src,src+n*11);
    lod.batches.push_back({first,static_cast<int>(n),batch.texture});continue;
   }
   std::vector<unsigned int> remap(n),indices(n);
   const size_t unique=meshopt_generateVertexRemap(
     remap.data(),nullptr,n,src,n,stride);
   if(unique<3||unique>n)continue;
   std::vector<float> welded(unique*11);
   meshopt_remapVertexBuffer(welded.data(),src,n,stride,remap.data());
   meshopt_remapIndexBuffer(indices.data(),nullptr,n,remap.data());
   std::vector<unsigned int> simplified(n);
   size_t target=static_cast<size_t>(n*reduction)/3*3;
   target=std::max<size_t>(3,target);
   float finalError=0;
   const size_t count=meshopt_simplifyWithAttributes(
     simplified.data(),indices.data(),n,welded.data(),unique,stride,
     welded.data()+3,stride,weights,8,nullptr,
     target,errorLimit,meshopt_SimplifyPermissive,&finalError);
   const bool use=count>=3&&count%3==0&&count<n&&std::isfinite(finalError);
   const int first=static_cast<int>(lod.vertices.size()/11);
   if(use){
    for(size_t i=0;i<count;i++){
     const unsigned int index=simplified[i];
     if(index>=unique){lod.vertices.clear();lod.batches.clear();return;}
     lod.vertices.insert(lod.vertices.end(),welded.begin()+index*11,
       welded.begin()+(index+1)*11);
    }
   }else lod.vertices.insert(lod.vertices.end(),src,src+n*11);
   lod.batches.push_back({first,static_cast<int>(use?count:n),batch.texture});
  }
  // A tier must measurably reduce draw geometry, or it is disabled.
  const size_t limit=static_cast<size_t>(asset.vertices.size()*
    (tier==0?.92f:.80f));
  if(lod.vertices.size()>=limit){
   lod.vertices.clear();lod.batches.clear();
  }
 }
}
bool uploadTextures(Asset&asset){
 for(auto&tex:asset.textures){
  tex.id=0;
  glGenTextures(1,&tex.id);
  if(!tex.id)return false;
  glActiveTexture(GL_TEXTURE0);
  glBindTexture(GL_TEXTURE_2D,tex.id);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);
  glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_REPEAT);
  glPixelStorei(GL_UNPACK_ALIGNMENT,1);
  if(tex.format==0){
   glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,tex.width,tex.height,0,GL_RGBA,
    GL_UNSIGNED_BYTE,tex.bytes.data());
  }else{
   const GLenum internal=tex.format==1?GL_COMPRESSED_RGB8_ETC2:GL_COMPRESSED_RGBA8_ETC2_EAC;
   glCompressedTexImage2D(GL_TEXTURE_2D,0,internal,tex.width,tex.height,0,
     static_cast<GLsizei>(tex.bytes.size()),tex.bytes.data());
  }
  glPixelStorei(GL_UNPACK_ALIGNMENT,4);
  if(glGetError()!=GL_NO_ERROR)return false;
 }
 glBindTexture(GL_TEXTURE_2D,0);
 return true;
}
bool uploadPaintTexture(TextureResource&tex){
 Asset temp;temp.textures.push_back(std::move(tex));
 const bool ok=uploadTextures(temp);
 tex=std::move(temp.textures[0]);return ok;
}
void releaseMapGpu(MapGuide& map){
 if(map.texture)glDeleteTextures(1,&map.texture);
 if(map.mesh.vao)glDeleteVertexArrays(1,&map.mesh.vao);
 if(map.mesh.buffer)glDeleteBuffers(1,&map.mesh.buffer);
 map.texture=0;map.mesh={};map.meshRevision=-1;
}
bool uploadMapTexture(MapGuide& map){
 const size_t expected=static_cast<size_t>(map.pixelWidth)*map.pixelHeight*4;
 if(map.pixelWidth<1||map.pixelHeight<1||
    map.pixelWidth>2048||map.pixelHeight>2048||
    expected!=map.rgba.size()||expected>MAX_MAP_RGBA_BYTES)return false;
 glGenTextures(1,&map.texture);
 if(!map.texture)return false;
 glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,map.texture);
 glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
 glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
 glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
 glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
 glPixelStorei(GL_UNPACK_ALIGNMENT,1);
 glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,map.pixelWidth,map.pixelHeight,
              0,GL_RGBA,GL_UNSIGNED_BYTE,map.rgba.data());
 glPixelStorei(GL_UNPACK_ALIGNMENT,4);
 glBindTexture(GL_TEXTURE_2D,0);
 const GLenum error=glGetError();
 if(error!=GL_NO_ERROR){releaseMapGpu(map);return false;}
 return true;
}
void releaseAssetGpu(Asset&asset){
 for(auto&tex:asset.textures)if(tex.id)glDeleteTextures(1,&tex.id);
 if(asset.gpu.vao)glDeleteVertexArrays(1,&asset.gpu.vao);
 if(asset.gpu.buffer)glDeleteBuffers(1,&asset.gpu.buffer);
 for(auto&lod:asset.lods){
  if(lod.gpu.vao)glDeleteVertexArrays(1,&lod.gpu.vao);
  if(lod.gpu.buffer)glDeleteBuffers(1,&lod.gpu.buffer);
 }
}
GLuint shader(GLenum kind,const char*src){
 GLuint s=glCreateShader(kind);glShaderSource(s,1,&src,nullptr);glCompileShader(s);
 GLint ok=GL_FALSE;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
 if(!ok){char log[512]={};glGetShaderInfoLog(s,sizeof(log),nullptr,log);__android_log_print(ANDROID_LOG_ERROR,"OE","Shader: %s",log);}
 return s;
}
void setupGL(){
 // GLES handles must be created again after the EGL context is lost.
 gMapGuide.texture=0;gMapGuide.mesh={};gMapGuide.meshRevision=-1;
 gMapStage={};
 gRoadGpu={};gRoadRanges.clear();gRoadGpuRevision=-1;gRoadGpuTerrainRevision=-1;
 gLakeGpu={}; // Drop stale handles on Android EGL context recreation.
 gPaintTileGpu.clear();gPrecisionPaintActive=false;gPaintCursorVisible=false;
 const char*vert=R"(#version 300 es
 layout(location=0) in vec3 aPos;
 layout(location=1) in vec3 aNormal;
 layout(location=2) in vec3 aColor;
 layout(location=3) in vec2 aUV;
 uniform mat4 uMvp;
 uniform mat4 uModel;
 out vec3 vNormal;
 out vec3 vColor;
 out vec2 vUV;
 out vec3 vWorldPos;
 void main(){
   gl_Position=uMvp*vec4(aPos,1.0);
   vNormal=normalize(mat3(uModel)*aNormal);
   vColor=aColor;
   vUV=aUV;
   vWorldPos=(uModel*vec4(aPos,1.0)).xyz;
 })";
 const char*frag=R"(#version 300 es
 precision mediump float;
 in vec3 vNormal;
 in vec3 vColor;
 in vec2 vUV;
 in vec3 vWorldPos;
 uniform vec3 uColor;
 uniform sampler2D uTexture;
 uniform float uUseTexture;
 uniform float uHighlight;
 uniform float uPaintOverride;
 uniform float uOverlay;
 uniform vec2 uPaintCenter;
 uniform float uPaintRadius;
 uniform float uGuide;
 uniform float uOpacity;
 uniform float uMaterialLayer;
 uniform sampler2D uLayerMask;
 out vec4 fragColor;
 void main(){
   float sun=max(dot(normalize(vNormal),normalize(vec3(-.55,1.,.5))),0.);
   vec4 texel=uUseTexture>.5?texture(uTexture,
       uMaterialLayer>.5?vWorldPos.xz/6.:vUV):vec4(1.);
    if(uMaterialLayer>.5)texel.a*=texture(uLayerMask,vUV).r;
   if(uOverlay>.5){
     float dist=length(vWorldPos.xz-uPaintCenter)/max(uPaintRadius,.001);
     texel.a*=1.0-smoothstep(.82,1.0,dist);
   }
   if(texel.a<(uGuide>.5?.01:(uOverlay>.5?.01:.45)))discard;
   vec3 tint=mix(vColor,vec3(1.),uPaintOverride);
   vec3 color=uColor*tint*texel.rgb*(.52+.48*sun);
   color=mix(color,vec3(.99,.72,.30),uHighlight*.30);
   fragColor=vec4(color,texel.a*uOpacity);
 })";
 GLuint v=shader(GL_VERTEX_SHADER,vert),f=shader(GL_FRAGMENT_SHADER,frag);
 gShader.program=glCreateProgram();
 glAttachShader(gShader.program,v);glAttachShader(gShader.program,f);
 glLinkProgram(gShader.program);glDeleteShader(v);glDeleteShader(f);
 GLint linked=GL_FALSE;glGetProgramiv(gShader.program,GL_LINK_STATUS,&linked);
 if(!linked)__android_log_print(ANDROID_LOG_ERROR,"OE","GL shader link failed");
 gShader.locMvp=glGetUniformLocation(gShader.program,"uMvp");
 gShader.locModel=glGetUniformLocation(gShader.program,"uModel");
 gShader.locColor=glGetUniformLocation(gShader.program,"uColor");
 gShader.locLight=glGetUniformLocation(gShader.program,"uHighlight");
 gShader.locTexture=glGetUniformLocation(gShader.program,"uTexture");
 gShader.locTextured=glGetUniformLocation(gShader.program,"uUseTexture");
 gShader.locPaintOverride=glGetUniformLocation(gShader.program,"uPaintOverride");
 gShader.locOverlay=glGetUniformLocation(gShader.program,"uOverlay");
 gShader.locPaintCenter=glGetUniformLocation(gShader.program,"uPaintCenter");
 gShader.locPaintRadius=glGetUniformLocation(gShader.program,"uPaintRadius");
 gShader.locGuide=glGetUniformLocation(gShader.program,"uGuide");
 gShader.locOpacity=glGetUniformLocation(gShader.program,"uOpacity");
 gShader.locMaterialLayer=glGetUniformLocation(gShader.program,"uMaterialLayer");
 gShader.locLayerMask=glGetUniformLocation(gShader.program,"uLayerMask");
 glUseProgram(gShader.program);
 glUniform1i(gShader.locTexture,0);
 glUniform1f(gShader.locTextured,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locGuide,0.f);
 glUniform1f(gShader.locOpacity,1.f);
 glUniform1f(gShader.locMaterialLayer,0.f);
 glUniform1i(gShader.locLayerMask,1);
 for(int kind=0;kind<3;kind++){
  std::vector<float> d=geometry(kind);Mesh &m=gMeshes[kind];m.count=(GLsizei)(d.size()/6);
  glGenVertexArrays(1,&m.vao);glBindVertexArray(m.vao);
  glGenBuffers(1,&m.buffer);glBindBuffer(GL_ARRAY_BUFFER,m.buffer);
  glBufferData(GL_ARRAY_BUFFER,(GLsizeiptr)(d.size()*sizeof(float)),d.data(),GL_STATIC_DRAW);
  glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,6*sizeof(float),nullptr);
  glEnableVertexAttribArray(1);glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(3*sizeof(float)));
  glDisableVertexAttribArray(2);
  glDisableVertexAttribArray(3);
 }
 for(int kind=0;kind<29;kind++)if(kind!=6)bakeStaticMesh(kind);
 for(auto &kv:gAssets){
  uploadAsset(kv.second);
  for(auto&lod:kv.second.lods)uploadLod(lod);
  if(!uploadTextures(kv.second))
   __android_log_print(ANDROID_LOG_ERROR,"OE","Failed to restore textures after EGL context recreation");
 }
 for(auto&kv:gPaintTextures)if(!uploadPaintTexture(kv.second))
  __android_log_print(ANDROID_LOG_ERROR,"OE","Failed to restore custom paint texture");
 if(!gMapGuide.rgba.empty()&&!uploadMapTexture(gMapGuide))
  __android_log_print(ANDROID_LOG_ERROR,"OE","Failed to restore PNG map reference");
 // A new OpenGL context needs new handles; never reuse IDs from the previous context.
 gTerrain={};
 glGenVertexArrays(1,&gTerrain.vao);
 glGenBuffers(1,&gTerrain.buffer);
 glBindVertexArray(gTerrain.vao);
 glBindBuffer(GL_ARRAY_BUFFER,gTerrain.buffer);
 const GLsizei terrainStride=9*sizeof(float);
 for(int attrib=0;attrib<3;attrib++){
  glEnableVertexAttribArray(attrib);
  glVertexAttribPointer(attrib,3,GL_FLOAT,GL_FALSE,terrainStride,
    reinterpret_cast<void*>(static_cast<uintptr_t>(attrib*3*sizeof(float))));
 }
 glBindVertexArray(0);
 glGenVertexArrays(1,&gPaintMesh.vao);
 glGenBuffers(1,&gPaintMesh.buffer);
 glBindVertexArray(gPaintMesh.vao);
 glBindBuffer(GL_ARRAY_BUFFER,gPaintMesh.buffer);
 for(int a=0;a<3;a++){
  glEnableVertexAttribArray(a);
  glVertexAttribPointer(a,3,GL_FLOAT,GL_FALSE,11*sizeof(float),
   reinterpret_cast<void*>(static_cast<uintptr_t>(a*3*sizeof(float))));
 }
 glEnableVertexAttribArray(3);
 glVertexAttribPointer(3,2,GL_FLOAT,GL_FALSE,11*sizeof(float),
  reinterpret_cast<void*>(9*sizeof(float)));
 glBindVertexArray(0);
 glVertexAttrib3f(2,1,1,1);
 glEnable(GL_DEPTH_TEST);
 glDisable(GL_CULL_FACE);
 glClearColor(.065f,.088f,.13f,1);
}
void draw(const Mat4&vp,int kind,Vec3 position,Vec3 dimensions,float yaw,Color color,bool selected=false){
 const Mat4 model=mul(mul(translate(position),rotateY(yaw)),scale(dimensions));
 const Mat4 mvp=mul(vp,model);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,mvp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,color.r,color.g,color.b);
 glUniform1f(gShader.locLight,selected?1.f:0.f);
 glUniform1f(gShader.locTextured,0.f);
 const Mesh &mesh=gMeshes[std::max(0,std::min(2,kind))];
 glBindVertexArray(mesh.vao);glDrawArrays(GL_TRIANGLES,0,mesh.count);
 ++gDrawCalls;gTriangles+=mesh.count/3;
}
struct Frustum {
 // Normalized inward-looking clip planes in world space.
 std::array<std::array<float,4>,6> planes{};
};
Frustum makeFrustum(const Mat4&vp){
 Frustum frustum;
 int index=0;
 for(int axis=0;axis<3;axis++)for(int side=-1;side<=1;side+=2){
  auto &plane=frustum.planes[index++];
  for(int col=0;col<4;col++)
   plane[col]=vp.m[col*4+3]+static_cast<float>(side)*vp.m[col*4+axis];
  const float n=std::sqrt(plane[0]*plane[0]+plane[1]*plane[1]+plane[2]*plane[2]);
  if(n>1e-8f)for(float &v:plane)v/=n;
 }
 return frustum;
}
bool visibleSphere(const Frustum&frustum,Vec3 center,float radius){
 for(const auto &p:frustum.planes){
  const float signedDistance=p[0]*center.x+p[1]*center.y+p[2]*center.z+p[3];
  if(signedDistance < -radius)return false;
 }
 return true;
}
bool importedInView(const Frustum&frustum,const Entity&e){
 const auto it=gAssets.find(e.asset);
 if(it==gAssets.end()||it->second.gpu.count<=0)return false;
 const Asset &asset=it->second;
 const float radius=asset.radius*e.size*std::max({e.sx,e.sy,e.sz});
 const Vec3 center{e.x,e.y+asset.centerY*e.size*e.sy,e.z};
 const Vec3 delta=sub(center,cameraEye());
 const float maxDistance=quality().viewRangeMetres+radius;
 if(dot(delta,delta)>maxDistance*maxDistance)return false;
 return visibleSphere(frustum,center,radius);
}
const TextureResource*paintTexture(const std::string&key);
void bindPaint(const TextureResource*paint);
void drawImported(const Mat4&vp,const Entity&e){
 auto it=gAssets.find(e.asset);
 if(it==gAssets.end()||it->second.gpu.count==0)return;
 const Mat4 model=mul(mul(translate({e.x,e.y,e.z}),rotateY(e.yaw*RAD)),
  scale({e.size*e.sx,e.size*e.sy,e.size*e.sz}));
 const Mat4 mvp=mul(vp,model);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,mvp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1,1,1);
 glUniform1f(gShader.locLight,e.id==gSelected?1.f:0.f);
 const Asset &asset=it->second;
 const TextureResource*overridePaint=paintTexture(e.paint);
 const Mesh*gpu=&asset.gpu;
 const std::vector<AssetBatch>*batches=&asset.batches;
 int tier=0;
 if(e.id!=gSelected){
  const Vec3 center{e.x,e.y+asset.centerY*e.size*e.sy,e.z};
  const Vec3 difference=sub(center,cameraEye());
  const float distance=std::sqrt(dot(difference,difference));
  const float sizeScale=std::max(.5f,e.size*std::max({e.sx,e.sy,e.sz}));
  // Larger objects retain LOD0 longer; no per-frame simplification.
  const int preferred=distance>125.f*sizeScale*quality().lodDistanceFactor?2:
   (distance>55.f*sizeScale*quality().lodDistanceFactor?1:0);
  for(int level=preferred;level>0;level--){
   const AssetLod&candidate=asset.lods[level-1];
   if(candidate.gpu.count>0&&candidate.gpu.vao!=0){
    gpu=&candidate.gpu;batches=&candidate.batches;tier=level;break;
   }
  }
 }
 ++gLodDrawCounts[tier];
 glBindVertexArray(gpu->vao);
 if(batches->empty()){
  glUniform1f(gShader.locTextured,0.f);
  glDrawArrays(GL_TRIANGLES,0,gpu->count);
  ++gDrawCalls;gTriangles+=gpu->count/3;
 }else for(const auto&batch:*batches){
  if(overridePaint&&overridePaint->id){bindPaint(overridePaint);}
  else if(batch.texture>=0&&static_cast<size_t>(batch.texture)<asset.textures.size()&&
     asset.textures[batch.texture].id){
   glActiveTexture(GL_TEXTURE0);
   glBindTexture(GL_TEXTURE_2D,asset.textures[batch.texture].id);
   glUniform1f(gShader.locTextured,1.f);
   glUniform1f(gShader.locPaintOverride,0.f);
  }else bindPaint(nullptr);
  glDrawArrays(GL_TRIANGLES,batch.first,batch.count);
  ++gDrawCalls;gTriangles+=batch.count/3;
 }
 bindPaint(nullptr);
 glBindTexture(GL_TEXTURE_2D,0);
}
void part(const Mat4&vp,const Entity&e,int kind,float x,float y,float z,float sx,float sy,float sz,Color color){
 const float a=e.yaw*RAD,c=std::cos(a),s=std::sin(a),k=e.size;
 Vec3 p{e.x+k*(c*x+s*z),k*y,e.z+k*(c*z-s*x)};
 draw(vp,kind,p,{sx*k,sy*k,sz*k},a,color,e.id==gSelected);
}
constexpr Color stone{.51f,.50f,.49f},mortar{.75f,.64f,.51f},oak{.32f,.20f,.13f},
 roof{.55f,.22f,.18f},gold{.82f,.62f,.33f},dark{.19f,.23f,.29f},glass{.82f,.65f,.33f};
template<class Part>
void forEachStaticPart(int type,Part&&part){
 // R5.2b: twelve modular medieval structures baked into shared meshes.
 // Every visible placed instance remains ONE GLES draw call.
 if(oe::medieval::validModule(type)){
  const auto boxes=oe::medieval::moduleParts(type);
  for(const auto&box:boxes)
   part(0,box.x,box.y,box.z,box.sx,box.sy,box.sz,
        {box.r,box.g,box.b});
  return;
 }
 if(type<=2){
 const bool forge=type==1,auction=type==2;
 Color base=forge?stone:(auction?Color{.79f,.70f,.56f}:mortar);
 Color roofColor=forge?Color{.27f,.31f,.37f}:(auction?Color{.30f,.25f,.40f}:roof);
 part(0,0,.18f,0,6,.36f,5,stone);
 part(0,0,1.75f,0,5.7f,3.05f,4.75f,base);
 part(1,0,3.25f,0,6.5f,2.25f,5.4f,roofColor);
 part(0,0,1.03f,2.41f,1.36f,2.06f,.12f,oak);
 part(0,.40f,1.06f,2.49f,.10f,.12f,.13f,gold);
 part(0,0,3.1f,2.50f,5.92f,.19f,.22f,oak);
 for(float x:{-1.83f,1.83f}){
  part(0,x,1.7f,2.47f,.19f,3.12f,.25f,oak);
  part(0,x,2.1f,2.56f,.87f,.86f,.1f,glass);
  part(0,x,2.1f,2.63f,.10f,.91f,.12f,oak);
  part(0,x,2.56f,2.63f,1.06f,.12f,.15f,oak);
 }
 if(forge){
  part(0,1.8f,4.36f,-.85f,1.12f,2.9f,1.1f,dark);
  part(0,1.8f,5.86f,-.85f,1.36f,.22f,1.3f,stone);
 } else if(auction){
  for(float x:{-2.45f,2.45f}){
    part(2,x,1.61f,2.62f,.47f,3.0f,.47f,stone);
    part(0,x,3.13f,2.62f,.72f,.24f,.72f,gold);
  }
  part(0,0,2.8f,2.63f,1.52f,.42f,.16f,gold);
 } else {
  part(0,0,2.55f,2.83f,2.3f,.18f,.9f,oak);
  part(0,-1.9f,4.3f,-.9f,.7f,2.0f,.75f,stone);
 }
  return;
 }
 if(type==3){
   part(2,0,1.4f,0,.18f,2.8f,.18f,dark);
   part(0,0,2.94f,0,.62f,.72f,.6f,gold);
   part(1,0,3.34f,0,.86f,.58f,.82f,dark);
 }else if(type==4){
   part(2,0,.27f,0,3.1f,.58f,3.1f,stone);
   part(2,0,.63f,0,2.55f,.09f,2.55f,{.25f,.52f,.66f});
   part(2,0,1.0f,0,.86f,.88f,.86f,stone);
 }else if(type==5){
   part(2,0,1.45f,0,.62f,2.9f,.62f,oak);
   part(2,0,3.55f,0,2.2f,2.5f,2.2f,{.20f,.39f,.27f});
 }else if(type==7){ // Stone cluster
   part(0,0,.47f,0,1.45f,.85f,1.24f,{.37f,.39f,.41f});
   part(1,.58f,.56f,-.32f,.76f,1.0f,.82f,{.45f,.44f,.42f});
 }else if(type==8){ // Shrub
   part(2,0,.24f,0,.24f,.48f,.24f,oak);
   part(1,0,.64f,0,1.4f,.94f,1.32f,{.20f,.36f,.19f});
   part(1,.43f,.49f,.25f,.74f,.70f,.80f,{.25f,.42f,.21f});
 }else if(type==9){ // Bench
   part(0,0,.75f,0,2.0f,.20f,.72f,oak);
   part(0,0,1.19f,.34f,2.0f,.79f,.17f,oak);
   for(float x:{-.73f,.73f})for(float z:{-.27f,.27f})
     part(0,x,.36f,z,.16f,.72f,.15f,dark);
 }else if(type==10){ // Wall segment
    part(0,0,1.4f,0,3.2f,2.8f,.26f,stone);
 }else if(type==11){ // Floor tile
    part(0,0,.10f,0,3.2f,.20f,3.2f,mortar);
 }else if(type==12){ // Roof segment
    part(1,0,.85f,0,3.2f,1.55f,3.2f,roof);
 }else if(type==13){ // Open door frame
    part(0,-.91f,1.22f,0,.24f,2.44f,.28f,oak);
    part(0,.91f,1.22f,0,.24f,2.44f,.28f,oak);
    part(0,0,2.50f,0,2.06f,.24f,.28f,oak);
 }else if(type==14){ // Window (frame and glass)
    part(0,0,1.50f,0,1.48f,1.16f,.12f,glass);
    part(0,0,.89f,0,1.68f,.14f,.19f,oak);
    part(0,0,2.11f,0,1.68f,.14f,.19f,oak);
    part(0,-.77f,1.50f,0,.14f,1.34f,.19f,oak);
    part(0,.77f,1.50f,0,.14f,1.34f,.19f,oak);
 }else if(type==15){ // Pillar
    part(2,0,1.45f,0,.40f,2.9f,.40f,stone);
    part(0,0,.13f,0,.77f,.25f,.77f,mortar);
    part(0,0,2.78f,0,.77f,.25f,.77f,mortar);
 }else if(type==16){ // Staircase
    for(int step=0;step<5;step++){
      const float depth=.55f;
      part(0,0,.15f*(step+1),-1.2f+step*depth,
           2.1f,.30f*(step+1),depth,stone);
    }
 }
}
void bakeStaticMesh(int type){
 Asset baked;
 baked.vertexStride=11;
 // Build once on GL-context creation. Later only transform one mesh per entity.
 forEachStaticPart(type,[&](int kind,float x,float y,float z,float sx,float sy,float sz,Color col){
  const std::vector<float> source=geometry(kind);
  baked.vertices.reserve(baked.vertices.size()+source.size()/6*11);
  for(size_t i=0;i+5<source.size();i+=6){
   const float px=x+source[i]*sx,py=y+source[i+1]*sy,pz=z+source[i+2]*sz;
   const float nx=source[i+3],ny=source[i+4],nz=source[i+5];
   // Planar mapping: wall sides use height, floor faces use XZ.
   const float u=(std::abs(nx)>.6f?pz:px)*.5f;
   const float v=(std::abs(ny)>.6f?pz:py)*.5f;
   baked.vertices.insert(baked.vertices.end(),{
      px,py,pz,nx*sx,ny*sy,nz*sz,col.r,col.g,col.b,u,v
   });
  }
 });
 uploadAsset(baked);
 gStaticMeshes[type]=baked.gpu;
}
const TextureResource*paintTexture(const std::string&key){
 auto it=gPaintTextures.find(key);
 return it==gPaintTextures.end()?nullptr:&it->second;
}
void bindPaint(const TextureResource*paint){
 if(paint&&paint->id){
  glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,paint->id);
  glUniform1f(gShader.locTextured,1.f);
  glUniform1f(gShader.locPaintOverride,1.f);
 }else{
  glUniform1f(gShader.locTextured,0.f);
  glUniform1f(gShader.locPaintOverride,0.f);
 }
}
void drawStatic(const Mat4&vp,const Entity&e){
 if(e.type<0||e.type>=29||e.type==6)return;
 const Mesh&m=gStaticMeshes[e.type];
 if(m.count<=0)return;
 const Mat4 model=mul(mul(translate({e.x,e.y,e.z}),rotateY(e.yaw*RAD)),
  scale({e.size*e.sx,e.size*e.sy,e.size*e.sz}));
 const Mat4 mvp=mul(vp,model);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,mvp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1.f,1.f,1.f);
 glUniform1f(gShader.locLight,e.id==gSelected?1.f:0.f);
 bindPaint(paintTexture(e.paint));
 glBindVertexArray(m.vao);
 glDrawArrays(GL_TRIANGLES,0,m.count);
 ++gDrawCalls;gTriangles+=m.count/3;
 bindPaint(nullptr);glBindTexture(GL_TEXTURE_2D,0);
}

// Low-power flat terrain. Combine nearby 64m squares into one vertex buffer.
// Rebuild when the camera crosses a tile border (or world size changes), not per frame.
// 11x11 tiles maximum; only 2 triangles/tile, one draw call for all terrain.
// Surface remains a single cached GLES buffer. Fine 8m tessellation is used
// ONLY on 64m tiles intersecting a deformation stroke; other tiles stay two tris.
void appendTerrainVertex(std::vector<float>&data,float x,float z,Color base,bool detailed){
 const float h=terrainHeightAt(x,z);
 Vec3 n{0,1,0};
 if(detailed){
  const float dx=(terrainHeightAt(x+1,z)-terrainHeightAt(x-1,z))*.5f;
  const float dz=(terrainHeightAt(x,z+1)-terrainHeightAt(x,z-1))*.5f;
  n=unit({-dx,1.f,-dz});
 }
 Color c=base;
 if(h>12.f){const float t=clampf((h-12.f)/36.f,0.f,1.f);
   c={base.r*(1-t)+.42f*t,base.g*(1-t)+.42f*t,base.b*(1-t)+.40f*t};}
 data.insert(data.end(),{x,h-.14f,z,n.x,n.y,n.z,c.r,c.g,c.b});
}
void terrainTriangle(std::vector<float>&v,float x0,float z0,float x1,float z1,
 float x2,float z2,Color c,bool detail){
 appendTerrainVertex(v,x0,z0,c,detail);
 appendTerrainVertex(v,x1,z1,c,detail);
 appendTerrainVertex(v,x2,z2,c,detail);
}
void updateTerrain(){
 const int gx=static_cast<int>(std::floor(gTarget.x/WORLD_TILE));
 const int gz=static_cast<int>(std::floor(gTarget.z/WORLD_TILE));
 if(gTerrain.gridX==gx&&gTerrain.gridZ==gz&&gTerrain.worldSize==gWorldSize&&
    gTerrain.revision==gTerrainRevision)return;
 std::vector<float> vertices;
 vertices.reserve(20000);
 const int half=gWorldSize/static_cast<int>(WORLD_TILE)/2;
 int tiles=0;
 const int radius=quality().terrainTileRadius;
 for(int tz=std::max(-half,gz-radius);tz<=std::min(half-1,gz+radius);tz++)
 for(int tx=std::max(-half,gx-radius);tx<=std::min(half-1,gx+radius);tx++){
  const float x=(tx+.5f)*WORLD_TILE,z=(tz+.5f)*WORLD_TILE;
  const float dxForest=x-256.f,dzForest=z,dxRuins=x+256.f,dzRuins=z+64.f;
  const float dxCrypt=x-16.f,dzCrypt=z+256.f,dxWild=x-32.f,dzWild=z-256.f;
  Color c{.18f,.29f,.23f};
  if(dxForest*dxForest+dzForest*dzForest<170.f*170.f)c={.12f,.30f,.18f};
  else if(dxRuins*dxRuins+dzRuins*dzRuins<164.f*164.f)c={.33f,.30f,.25f};
  else if(dxCrypt*dxCrypt+dzCrypt*dzCrypt<170.f*170.f)c={.23f,.24f,.29f};
  else if(dxWild*dxWild+dzWild*dzWild<160.f*160.f)c={.36f,.32f,.20f};
  if((tx+tz)%3==0){c.r*=.96f;c.g*=.96f;c.b*=.96f;}
  bool detail=false;
  const float loX=x-WORLD_TILE*.5f,loZ=z-WORLD_TILE*.5f;
  for(const auto&b:gBrushes){
    const float closestX=clampf(b.x,loX,loX+WORLD_TILE);
    const float closestZ=clampf(b.z,loZ,loZ+WORLD_TILE);
    const float dx=b.x-closestX,dz=b.z-closestZ;
    if(dx*dx+dz*dz<b.radius*b.radius){detail=true;break;}
  }
  const int divisions=detail?8:1;
  const float step=WORLD_TILE/divisions;
  for(int iz=0;iz<divisions;iz++)for(int ix=0;ix<divisions;ix++){
    const float x0=loX+ix*step,x1=x0+step,z0=loZ+iz*step,z1=z0+step;
    terrainTriangle(vertices,x0,z0,x1,z0,x1,z1,c,detail);
    terrainTriangle(vertices,x0,z0,x1,z1,x0,z1,c,detail);
  }
  ++tiles;
 }
 gTerrain.vertices=static_cast<GLsizei>(vertices.size()/9);
 gTerrain.tiles=tiles;
 glBindBuffer(GL_ARRAY_BUFFER,gTerrain.buffer);
 glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(vertices.size()*sizeof(float)),
              vertices.data(),GL_DYNAMIC_DRAW);
 gTerrain.gridX=gx;gTerrain.gridZ=gz;gTerrain.worldSize=gWorldSize;
 gTerrain.revision=gTerrainRevision;
}
void drawWorld(const Mat4&vp){
 updateTerrain();
 if(gTerrain.vertices>0){
  const Mat4 model=identity();
  glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
  glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
  glUniform3f(gShader.locColor,1,1,1);
  glUniform1f(gShader.locLight,0);
  glUniform1f(gShader.locTextured,0.f);
  glBindVertexArray(gTerrain.vao);
  glDrawArrays(GL_TRIANGLES,0,gTerrain.vertices);
  ++gDrawCalls;gTriangles+=gTerrain.vertices/3;
 }
 if(gDemoRoads&&std::abs(gTarget.x)<150.f&&std::abs(gTarget.z)<150.f){
  draw(vp,0,{0,-.025f,2},{27,.12f,23},0,{.44f,.44f,.44f});
  draw(vp,0,{0,-.015f,16},{8,.13f,22},0,{.53f,.51f,.48f});
 }
}
// Surface decals conform to sculpted heights. Built only on redraw and culled
// by camera proximity. Regular terrain remains one batched GLES draw.
void drawTerrainPaint(const Mat4&vp){
 if(gTerrainPaint.empty()||!gPaintMesh.vao)return;
 const int steps=10;
 std::vector<float> verts;verts.reserve(steps*steps*6*11);
 glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
 glDepthMask(GL_FALSE);
 glUniform1f(gShader.locOverlay,1.f);
 glUniform1f(gShader.locPaintOverride,1.f);
 glUniform1f(gShader.locTextured,1.f);
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1.f,1.f,1.f);
 glUniform1f(gShader.locLight,0.f);
 glBindVertexArray(gPaintMesh.vao);
 int visibleStamps=0;
 for(const auto&stamp:gTerrainPaint){
  if(std::abs(stamp.x-gTarget.x)>quality().viewRangeMetres+stamp.radius||
     std::abs(stamp.z-gTarget.z)>quality().viewRangeMetres+stamp.radius)continue;
  const TextureResource*t=paintTexture(stamp.texture);
  if(!t||!t->id)continue;
  if(visibleStamps>=quality().maxVisiblePaintStamps)break;
  ++visibleStamps;
  glUniform2f(gShader.locPaintCenter,stamp.x,stamp.z);
  glUniform1f(gShader.locPaintRadius,stamp.radius);
  glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,t->id);
  verts.clear();
  auto append=[&](float xx,float zz){
   float yy=terrainHeightAt(xx,zz)+.06f;
   verts.insert(verts.end(),{xx,yy,zz,0.f,1.f,0.f,1.f,1.f,1.f,xx/6.f,zz/6.f});
  };
  for(int iz=0;iz<steps;iz++)for(int ix=0;ix<steps;ix++){
   const float x0=stamp.x-stamp.radius+2.f*stamp.radius*ix/steps;
   const float x1=stamp.x-stamp.radius+2.f*stamp.radius*(ix+1)/steps;
   const float z0=stamp.z-stamp.radius+2.f*stamp.radius*iz/steps;
   const float z1=stamp.z-stamp.radius+2.f*stamp.radius*(iz+1)/steps;
   append(x0,z0);append(x1,z0);append(x1,z1);
   append(x0,z0);append(x1,z1);append(x0,z1);
  }
  glBindBuffer(GL_ARRAY_BUFFER,gPaintMesh.buffer);
  glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(verts.size()*sizeof(float)),
     verts.data(),GL_DYNAMIC_DRAW);
  glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(verts.size()/11));
  ++gDrawCalls;gTriangles+=static_cast<int>(verts.size()/33);
 }
 glBindVertexArray(0);
 glDepthMask(GL_TRUE);glDisable(GL_BLEND);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
 glUniform1f(gShader.locTextured,0.f);
 glBindTexture(GL_TEXTURE_2D,0);
}



// A single composited RGBA texture per painted 64m tile: no per-stamp draw calls.
// Source paint images are baked once on CPU when the brush moves.
void drawPrecisionPaint(const Mat4& vp){
 if(gPaintTiles.tiles().empty()||!gPaintMesh.vao)return;
 const float view=quality().viewRangeMetres+oe::terrainpaint::kTileMetres;
 glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
 glDepthMask(GL_FALSE);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1,1,1);
 glUniform1f(gShader.locLight,0.f);
 glUniform1f(gShader.locTextured,1.f);
 glUniform1f(gShader.locPaintOverride,1.f);
 glUniform1f(gShader.locGuide,1.f);
 glUniform1f(gShader.locOpacity,1.f);
 glUniform1f(gShader.locOverlay,0.f);
 glBindVertexArray(gPaintMesh.vao);
 std::vector<float> verts;verts.reserve(16*16*6*11);
 for(const auto& tile:gPaintTiles.tiles()){
  const float ox=tile.x*oe::terrainpaint::kTileMetres,
              oz=tile.z*oe::terrainpaint::kTileMetres;
  if(std::abs(ox+32.f-gTarget.x)>view||
     std::abs(oz+32.f-gTarget.z)>view)continue;
  const auto id=std::make_pair(tile.x,tile.z);
  auto& gpu=gPaintTileGpu[id];
  if(!gpu.texture){
   glGenTextures(1,&gpu.texture);
   if(!gpu.texture)continue;
   glBindTexture(GL_TEXTURE_2D,gpu.texture);
   glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
   glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
   glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
   glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
   glPixelStorei(GL_UNPACK_ALIGNMENT,1);
   glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,oe::terrainpaint::kResolution,
       oe::terrainpaint::kResolution,0,GL_RGBA,GL_UNSIGNED_BYTE,tile.pixels.data());
   glPixelStorei(GL_UNPACK_ALIGNMENT,4);
   gpu.revision=tile.revision;
  }else{
   glBindTexture(GL_TEXTURE_2D,gpu.texture);
   if(gpu.revision!=tile.revision){
    glPixelStorei(GL_UNPACK_ALIGNMENT,1);
    glTexSubImage2D(GL_TEXTURE_2D,0,0,0,oe::terrainpaint::kResolution,
       oe::terrainpaint::kResolution,GL_RGBA,GL_UNSIGNED_BYTE,tile.pixels.data());
    glPixelStorei(GL_UNPACK_ALIGNMENT,4);
    gpu.revision=tile.revision;
   }
  }
  verts.clear();
  auto append=[&](float x,float z){
   const float y=terrainHeightAt(x,z)+.075f;
   verts.insert(verts.end(),{x,y,z,0.f,1.f,0.f,1.f,1.f,1.f,
     (x-ox)/oe::terrainpaint::kTileMetres,(z-oz)/oe::terrainpaint::kTileMetres});
  };
  for(int iz=0;iz<16;iz++)for(int ix=0;ix<16;ix++){
   const float x0=ox+ix*4.f,x1=x0+4.f,z0=oz+iz*4.f,z1=z0+4.f;
   append(x0,z0);append(x0,z1);append(x1,z0);
   append(x1,z0);append(x0,z1);append(x1,z1);
  }
  glBindBuffer(GL_ARRAY_BUFFER,gPaintMesh.buffer);
  glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(verts.size()*sizeof(float)),
               verts.data(),GL_DYNAMIC_DRAW);
  glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(verts.size()/11));
  ++gDrawCalls;gTriangles+=static_cast<int>(verts.size()/33);
 }
 glBindVertexArray(0);glBindTexture(GL_TEXTURE_2D,0);
 glDepthMask(GL_TRUE);glDisable(GL_BLEND);
 glUniform1f(gShader.locGuide,0.f);glUniform1f(gShader.locTextured,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
}

// R4.1 per-material layer preview: source diffuse texture at world-space UV,
// independent 8-bit alpha mask at tile-local UV. No stamp draw calls.
void drawMaterialLayers(const Mat4& vp){
 if(gMaterialCanvas.tiles().empty()||!gPaintMesh.vao)return;
 const float range=quality().viewRangeMetres+oe::terrainpaint::kTileMetres;
 glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
 glDepthMask(GL_FALSE);
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1,1,1);
 glUniform1f(gShader.locLight,0.f);
 glUniform1f(gShader.locTextured,1.f);
 glUniform1f(gShader.locPaintOverride,1.f);
 glUniform1f(gShader.locGuide,1.f);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locOpacity,1.f);
 glUniform1f(gShader.locMaterialLayer,1.f);
 glBindVertexArray(gPaintMesh.vao);
 std::vector<float> verts;
 verts.reserve(16*16*6*11);
 for(const auto& tile:gMaterialCanvas.tiles()){
  const float ox=tile.x*oe::terrainpaint::kTileMetres,
              oz=tile.z*oe::terrainpaint::kTileMetres;
  if(std::abs(ox+32.f-gTarget.x)>range||
     std::abs(oz+32.f-gTarget.z)>range)continue;
  verts.clear();
  auto append=[&](float x,float z){
   verts.insert(verts.end(),{x,terrainHeightAt(x,z)+.084f,z,
     0.f,1.f,0.f,1.f,1.f,1.f,
     (x-ox)/oe::terrainpaint::kTileMetres,
     (z-oz)/oe::terrainpaint::kTileMetres});
  };
  for(int z=0;z<16;z++)for(int x=0;x<16;x++){
   const float x0=ox+x*4.f,x1=x0+4.f,z0=oz+z*4.f,z1=z0+4.f;
   append(x0,z0);append(x0,z1);append(x1,z0);
   append(x1,z0);append(x0,z1);append(x1,z1);
  }
  glBindBuffer(GL_ARRAY_BUFFER,gPaintMesh.buffer);
  glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(verts.size()*sizeof(float)),
               verts.data(),GL_DYNAMIC_DRAW);
  for(const auto& layer:tile.materials){
   const TextureResource* material=paintTexture(layer.material);
   if(!material||!material->id)continue;
   const auto key=std::make_tuple(tile.x,tile.z,layer.material);
   auto& gpu=gMaterialGpu[key];
   glActiveTexture(GL_TEXTURE1);
   if(!gpu.texture){
    glGenTextures(1,&gpu.texture);
    if(!gpu.texture)continue;
    glBindTexture(GL_TEXTURE_2D,gpu.texture);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT,1);
    glTexImage2D(GL_TEXTURE_2D,0,GL_R8,oe::terrainpaint::kResolution,
       oe::terrainpaint::kResolution,0,GL_RED,GL_UNSIGNED_BYTE,layer.weights.data());
    glPixelStorei(GL_UNPACK_ALIGNMENT,4);
    gpu.revision=layer.revision;
   }else{
    glBindTexture(GL_TEXTURE_2D,gpu.texture);
    if(gpu.revision!=layer.revision){
     glPixelStorei(GL_UNPACK_ALIGNMENT,1);
     glTexSubImage2D(GL_TEXTURE_2D,0,0,0,oe::terrainpaint::kResolution,
         oe::terrainpaint::kResolution,GL_RED,GL_UNSIGNED_BYTE,layer.weights.data());
     glPixelStorei(GL_UNPACK_ALIGNMENT,4);
     gpu.revision=layer.revision;
    }
   }
   glActiveTexture(GL_TEXTURE0);
   glBindTexture(GL_TEXTURE_2D,material->id);
   glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(verts.size()/11));
   ++gDrawCalls;gTriangles+=static_cast<int>(verts.size()/33);
  }
 }
 glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,0);
 glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,0);
 glBindVertexArray(0);
 glDepthMask(GL_TRUE);glDisable(GL_BLEND);
 glUniform1f(gShader.locMaterialLayer,0.f);
 glUniform1f(gShader.locGuide,0.f);
 glUniform1f(gShader.locTextured,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
}

void drawPrecisionCursor(const Mat4& vp){
 if(!gPaintCursorVisible||!gPaintMesh.vao)return;
 constexpr int segments=48;
 std::vector<float> vertices;vertices.reserve(segments*11);
 for(int i=0;i<segments;++i){
  const float angle=(2.f*PI*i)/segments;
  const float x=gPaintCursorX+std::cos(angle)*gPaintCursorRadius;
  const float z=gPaintCursorZ+std::sin(angle)*gPaintCursorRadius;
  vertices.insert(vertices.end(),{x,terrainHeightAt(x,z)+.25f,z,0.f,1.f,0.f,
    1.f,.76f,.18f,0.f,0.f});
 }
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1,1,1);
 glUniform1f(gShader.locLight,0.f);
 glUniform1f(gShader.locTextured,0.f);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
 glDisable(GL_DEPTH_TEST);
 glBindVertexArray(gPaintMesh.vao);
 glBindBuffer(GL_ARRAY_BUFFER,gPaintMesh.buffer);
 glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(vertices.size()*sizeof(float)),
              vertices.data(),GL_DYNAMIC_DRAW);
 glDrawArrays(GL_LINE_LOOP,0,segments);
 glBindVertexArray(0);
 glEnable(GL_DEPTH_TEST);
 ++gDrawCalls;
}

bool rebuildMapMesh(){
 if(!gMapGuide.texture||!gMapGuide.visible)return false;
 const int gx=static_cast<int>(std::floor(gTarget.x/16.f));
 const int gz=static_cast<int>(std::floor(gTarget.z/16.f));
 if(gMapGuide.meshRevision==gMapGuide.revision&&
    gMapGuide.terrainRevision==gTerrainRevision&&
    gMapGuide.gridX==gx&&gMapGuide.gridZ==gz)return true;
 const double halfW=gMapGuide.widthMetres*.5,halfH=gMapGuide.heightMetres*.5;
 const double xBase=gMapGuide.centerX-halfW,zBase=gMapGuide.centerZ-halfH;
 const double range=std::min<double>(quality().viewRangeMetres,
                      std::max(96.0,static_cast<double>(gDistance)*1.8));
 const double loX=std::max({-static_cast<double>(worldHalf()),xBase,
                           gx*16.0-range-16.0});
 const double hiX=std::min({ static_cast<double>(worldHalf()),xBase+gMapGuide.widthMetres,
                           gx*16.0+range+16.0});
 const double loZ=std::max({-static_cast<double>(worldHalf()),zBase,
                           gz*16.0-range-16.0});
 const double hiZ=std::min({ static_cast<double>(worldHalf()),zBase+gMapGuide.heightMetres,
                           gz*16.0+range+16.0});
 std::vector<float> verts;
 if(hiX>loX&&hiZ>loZ){
  const double step=8.0;
  const int nx=static_cast<int>(std::ceil((hiX-loX)/step));
  const int nz=static_cast<int>(std::ceil((hiZ-loZ)/step));
  if(nx<0||nz<0||static_cast<size_t>(nx)*nz>13000u)return false;
  verts.reserve(static_cast<size_t>(nx)*nz*6*11);
  auto append=[&](double x,double z){
   const float xf=static_cast<float>(x),zf=static_cast<float>(z);
   const float y=terrainHeightAt(xf,zf)+.095f;
   const float u=static_cast<float>((x-xBase)/gMapGuide.widthMetres);
   // BitmapFactory RGBA rows run top-first; GL convention has row 0 at v=0.
   const float v=static_cast<float>(1.0-(z-zBase)/gMapGuide.heightMetres);
   verts.insert(verts.end(),{xf,y,zf,0.f,1.f,0.f,1.f,1.f,1.f,u,v});
  };
  for(int iz=0;iz<nz;++iz)for(int ix=0;ix<nx;++ix){
   const double x0=loX+ix*step,x1=std::min(hiX,x0+step);
   const double z0=loZ+iz*step,z1=std::min(hiZ,z0+step);
   // CCW, Y-up.
   append(x0,z0);append(x0,z1);append(x1,z0);
   append(x1,z0);append(x0,z1);append(x1,z1);
  }
 }
 if(!gMapGuide.mesh.vao){
  glGenVertexArrays(1,&gMapGuide.mesh.vao);
  glGenBuffers(1,&gMapGuide.mesh.buffer);
 }
 if(!gMapGuide.mesh.vao||!gMapGuide.mesh.buffer)return false;
 glBindVertexArray(gMapGuide.mesh.vao);
 glBindBuffer(GL_ARRAY_BUFFER,gMapGuide.mesh.buffer);
 glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(verts.size()*sizeof(float)),
              verts.empty()?nullptr:verts.data(),GL_DYNAMIC_DRAW);
 for(int i=0;i<3;i++){
  glEnableVertexAttribArray(static_cast<GLuint>(i));
  glVertexAttribPointer(static_cast<GLuint>(i),3,GL_FLOAT,GL_FALSE,11*sizeof(float),
   reinterpret_cast<void*>(static_cast<uintptr_t>(i*3*sizeof(float))));
 }
 glEnableVertexAttribArray(3);
 glVertexAttribPointer(3,2,GL_FLOAT,GL_FALSE,11*sizeof(float),
                      reinterpret_cast<void*>(9*sizeof(float)));
 glBindVertexArray(0);
 if(glGetError()!=GL_NO_ERROR)return false;
 gMapGuide.mesh.count=static_cast<GLsizei>(verts.size()/11);
 gMapGuide.gridX=gx;gMapGuide.gridZ=gz;
 gMapGuide.meshRevision=gMapGuide.revision;
 gMapGuide.terrainRevision=gTerrainRevision;
 return true;
}
void drawMapGuide(const Mat4& vp){
 if(!gMapGuide.texture||!gMapGuide.visible||!rebuildMapMesh()||
    gMapGuide.mesh.count<=0)return;
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1.f,1.f,1.f);
 glUniform1f(gShader.locLight,0.f);
 glUniform1f(gShader.locTextured,1.f);
 glUniform1f(gShader.locPaintOverride,1.f);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locGuide,1.f);
 glUniform1f(gShader.locOpacity,gMapGuide.opacity);
 glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
 glDepthMask(GL_FALSE);
 glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,gMapGuide.texture);
 glBindVertexArray(gMapGuide.mesh.vao);
 glDrawArrays(GL_TRIANGLES,0,gMapGuide.mesh.count);
 ++gDrawCalls;gTriangles+=gMapGuide.mesh.count/3;
 glBindVertexArray(0);glBindTexture(GL_TEXTURE_2D,0);
 glDepthMask(GL_TRUE);glDisable(GL_BLEND);
 glUniform1f(gShader.locGuide,0.f);
 glUniform1f(gShader.locOpacity,1.f);
 glUniform1f(gShader.locPaintOverride,0.f);
 glUniform1f(gShader.locTextured,0.f);
}

Color roadPreviewColor(const std::string& material){
 if(material=="builtin:dirt")return {.50f,.33f,.19f};
 if(material=="builtin:sand")return {.77f,.68f,.43f};
 if(material=="builtin:grass")return {.28f,.47f,.25f};
 return {.71f,.67f,.59f}; // Placeholder until image material library is connected.
}
bool rebuildRoadGpu(){
 if(gRoadGpuRevision==gRoadRevision&&gRoadGpuTerrainRevision==gTerrainRevision)return true;
 std::vector<float> vertices;vertices.reserve(16000);
 std::vector<RoadDrawRange> draws;
 const auto height=[](double x,double z)->double{
  return terrainHeightAt(static_cast<float>(x),static_cast<float>(z));
 };
 const auto append=[&](const oe::roads::SurfaceMesh& mesh,
                       std::uint64_t roadId,std::uint64_t materialRoadId)->bool{
  if(vertices.size()/11>MAX_ROAD_GPU_VERTICES||
     mesh.triangles.size()>MAX_ROAD_GPU_VERTICES-vertices.size()/11)return false;
  const GLint first=static_cast<GLint>(vertices.size()/11);
  const auto* source=gRoads.find(materialRoadId);
  const Color color=source?roadPreviewColor(source->material):Color{.67f,.62f,.52f};
  for(const auto& point:mesh.triangles){
   const float x=point.x,z=point.z;
   const float dx=terrainHeightAt(x+0.5f,z)-terrainHeightAt(x-0.5f,z);
   const float dz=terrainHeightAt(x,z+0.5f)-terrainHeightAt(x,z-0.5f);
   const Vec3 surfaceNormal=unit({-dx,1.0f,-dz});
   // Stable worn-earth/cobblestone modulation. Evaluated only on road mesh
   // rebuild; world-space alignment also prevents shader seams at junctions.
   const float wear=.965f+.035f*std::sin(x*.77f+z*.31f)*
                               std::sin(z*.59f-x*.21f);
   vertices.insert(vertices.end(),{x,point.y,z,
      surfaceNormal.x,surfaceNormal.y,surfaceNormal.z,
      color.r*wear,color.g*wear,color.b*wear,point.u,point.v});
  }
  draws.push_back({first,static_cast<GLsizei>(mesh.triangles.size()),roadId});
  return true;
 };
 oe::roads::RoadTopology graph;
 oe::roads::MedievalSurface stitched;
 const bool hasStitchedSurfaces=
     oe::roads::buildRoadTopology(gRoads.all(),graph,0.)&&
     oe::roads::buildMedievalSurface(graph,height,stitched);
 if(hasStitchedSurfaces){
  // Never draw R5 crossing discs over R5.2 joined geometry: every R5.2 cap
  // border is shared by the road ribbon and its terrain-following junction.
  for(const auto& part:stitched.roads)
   if(!append(part.mesh,part.roadId,part.materialRoadId))return false;
  for(const auto& part:stitched.junctions)
   if(!append(part.mesh,0,part.materialRoadId))return false;
 }else{
  // Old R5/R5.1, over-short approaches and unsupported overlap geometries
  // remain editable. Fallback cannot corrupt road IDs or projects.
  for(const auto& road:gRoads.all()){
   oe::roads::SurfaceMesh strip;
   if(!oe::roads::buildSurfaceMesh(road,height,strip))return false;
   if(!append(strip,road.id,road.id))return false;
  }
  std::vector<oe::roads::Junction> crossings;
  if(oe::roads::findJunctions(gRoads.all(),crossings)){
   for(const auto& crossing:crossings){
    oe::roads::SurfaceMesh patch;
    if(!oe::roads::buildJunctionPreview(crossing,height,patch))continue;
    if(vertices.size()/11>MAX_ROAD_GPU_VERTICES||
       patch.triangles.size()>MAX_ROAD_GPU_VERTICES-vertices.size()/11)break;
    if(!append(patch,0,crossing.roadA))break;
   }
  }
 }
 if(!gRoadGpu.vao){
  glGenVertexArrays(1,&gRoadGpu.vao);
  glGenBuffers(1,&gRoadGpu.buffer);
 }
 if(!gRoadGpu.vao||!gRoadGpu.buffer)return false;
 glBindVertexArray(gRoadGpu.vao);
 glBindBuffer(GL_ARRAY_BUFFER,gRoadGpu.buffer);
 glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(vertices.size()*sizeof(float)),
              vertices.empty()?nullptr:vertices.data(),GL_DYNAMIC_DRAW);
 for(int a=0;a<3;a++){
  glEnableVertexAttribArray(static_cast<GLuint>(a));
  glVertexAttribPointer(static_cast<GLuint>(a),3,GL_FLOAT,GL_FALSE,11*sizeof(float),
   reinterpret_cast<void*>(static_cast<uintptr_t>(a*3*sizeof(float))));
 }
 glEnableVertexAttribArray(3);
 glVertexAttribPointer(3,2,GL_FLOAT,GL_FALSE,11*sizeof(float),
                      reinterpret_cast<void*>(9*sizeof(float)));
 glBindVertexArray(0);
 if(glGetError()!=GL_NO_ERROR)return false;
 gRoadGpu.count=static_cast<GLsizei>(vertices.size()/11);
 gRoadRanges.swap(draws);
 gRoadGpuRevision=gRoadRevision;gRoadGpuTerrainRevision=gTerrainRevision;
 return true;
}
void drawRoads(const Mat4& vp){
 if(gRoads.all().empty()||!rebuildRoadGpu()||!gRoadGpu.vao)return;
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1.f,1.f,1.f);
 glUniform1f(gShader.locTextured,0.f);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
 glBindVertexArray(gRoadGpu.vao);
 for(const auto& range:gRoadRanges){
  glUniform1f(gShader.locLight,range.id==gSelectedRoad?1.f:0.f);
  glDrawArrays(GL_TRIANGLES,range.first,range.count);
  ++gDrawCalls;gTriangles+=range.count/3;
 }
 glBindVertexArray(0);
 glUniform1f(gShader.locLight,0.f);
}
// R5.3 editor lake water. The terrain-clipped shoreline mesh is generated
// ONLY when lakes/terrain change and all current lakes share one GPU draw call.
void drawLakes(const Mat4& vp){
 if(gLakes.empty())return;
 if(gLakeGpu.lakeRevision!=gLakeRevision||
    gLakeGpu.terrainRevision!=gTerrainRevision){
  oe::water::LakeSurface surface;
  const auto terrain=[](double x,double z)->double{
   return terrainHeightAt(static_cast<float>(x),static_cast<float>(z));
  };
  if(!oe::water::buildLakeSurface(gLakes,worldHalf(),terrain,surface))return;
  std::vector<float> vertices;
  vertices.reserve(surface.triangles.size()*9);
  for(const auto&v:surface.triangles){
   vertices.insert(vertices.end(),{v.x,v.y,v.z,0.f,1.f,0.f,v.r,v.g,v.b});
  }
  if(!gLakeGpu.vao){
   glGenVertexArrays(1,&gLakeGpu.vao);
   glGenBuffers(1,&gLakeGpu.buffer);
  }
  if(!gLakeGpu.vao||!gLakeGpu.buffer)return;
  glBindVertexArray(gLakeGpu.vao);
  glBindBuffer(GL_ARRAY_BUFFER,gLakeGpu.buffer);
  glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(
      vertices.size()*sizeof(float)),vertices.empty()?nullptr:vertices.data(),
      GL_DYNAMIC_DRAW);
  for(int attrib=0;attrib<3;++attrib){
   glEnableVertexAttribArray(static_cast<GLuint>(attrib));
   glVertexAttribPointer(static_cast<GLuint>(attrib),3,GL_FLOAT,GL_FALSE,
      9*sizeof(float),
      reinterpret_cast<void*>(static_cast<uintptr_t>(attrib*3*sizeof(float))));
  }
  glDisableVertexAttribArray(3); // no UV needed for cheap translucent water
  glBindVertexArray(0);
  if(glGetError()!=GL_NO_ERROR)return;
  gLakeGpu.count=static_cast<GLsizei>(vertices.size()/9);
  gLakeGpu.lakeRevision=gLakeRevision;
  gLakeGpu.terrainRevision=gTerrainRevision;
 }
 if(!gLakeGpu.count)return;
 glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
 glDepthMask(GL_FALSE);
 const Mat4 model=identity();
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,vp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1.f,1.f,1.f);
 glUniform1f(gShader.locLight,0.f);
 glUniform1f(gShader.locTextured,0.f);
 glUniform1f(gShader.locOverlay,0.f);
 glUniform1f(gShader.locPaintOverride,0.f);
 glUniform1f(gShader.locMaterialLayer,0.f);
 glUniform1f(gShader.locGuide,0.f);
 glUniform1f(gShader.locOpacity,.77f);
 glBindVertexArray(gLakeGpu.vao);
 glDrawArrays(GL_TRIANGLES,0,gLakeGpu.count);
 ++gDrawCalls;gTriangles+=gLakeGpu.count/3;
 glBindVertexArray(0);
 glUniform1f(gShader.locOpacity,1.f);
 glDepthMask(GL_TRUE);
 glDisable(GL_BLEND);
}
void drawRoadHandles(const Mat4& vp){
 const auto* road=gRoads.find(gSelectedRoad);
 const float marker=clampf(gDistance*.013f,.22f,.9f);
 if(road){
  const size_t stride=std::max<size_t>(1,(road->controlPoints.size()+63)/64);
  for(size_t i=0;i<road->controlPoints.size();i+=stride){
   const bool active=static_cast<int>(i)==gSelectedRoadPoint;
   const auto& p=road->controlPoints[i];
   const float x=static_cast<float>(p.x),z=static_cast<float>(p.z);
   draw(vp,0,{x,terrainHeightAt(x,z)+.25f,z},
        {marker*(active?1.7f:1.f),marker*.6f,marker*(active?1.7f:1.f)},
        0,active?Color{.23f,.98f,.42f}:Color{.99f,.75f,.18f});
  }
 }
 if(gRoadPending){
  const float x=static_cast<float>(gRoadFirstPoint.x),z=static_cast<float>(gRoadFirstPoint.z);
  draw(vp,0,{x,terrainHeightAt(x,z)+.26f,z},
       {marker*1.5f,marker*.7f,marker*1.5f},0,{.97f,.27f,.24f});
 }
}

bool groundPoint(float px,float py,float &x,float &z){
 const Vec3 eye=cameraEye(),forward=unit(sub(gTarget,eye));
 const Vec3 right=unit(cross(forward,{0,1,0})),up=cross(right,forward);
 const float half=std::tan(PI/6.f),aspect=float(gWidth)/float(std::max(gHeight,1));
 const float u=(px*2.f/gWidth-1.f)*half*aspect,v=(1.f-py*2.f/gHeight)*half;
 Vec3 ray=unit({forward.x+right.x*u+up.x*v,forward.y+right.y*u+up.y*v,
                 forward.z+right.z*u+up.z*v});
 if(gBrushes.empty()){
  if(ray.y>-.0001f)return false;
  const float t=-eye.y/ray.y;
  if(t<=0||t>1500.f)return false;
  x=eye.x+ray.x*t;z=eye.z+ray.z*t;
  return x>=-worldHalf()&&x<=worldHalf()&&z>=-worldHalf()&&z<=worldHalf();
 }
 // Ray-heightfield intersection: select the VISIBLE hillside, not flat y=0
 // behind it. Only runs on touch; no continuous GPU/CPU work.
 float lo=0.f;
 float previous=eye.y-terrainHeightAt(eye.x,eye.z);
 if(previous<0)return false;
 for(int i=1;i<=190;i++){
  const float hi=i*8.f;
  const float pxHit=eye.x+ray.x*hi,pzHit=eye.z+ray.z*hi;
  const float cur=eye.y+ray.y*hi-terrainHeightAt(pxHit,pzHit);
  if(cur<=0&&previous>=0){
   float low=lo,high=hi;
   for(int j=0;j<12;j++){
    const float mid=(low+high)*.5f;
    const float my=eye.y+ray.y*mid;
    if(my-terrainHeightAt(eye.x+ray.x*mid,eye.z+ray.z*mid)>0.f)low=mid;
    else high=mid;
   }
   x=eye.x+ray.x*high;z=eye.z+ray.z*high;
   return x>=-worldHalf()&&x<=worldHalf()&&z>=-worldHalf()&&z<=worldHalf();
  }
  lo=hi;previous=cur;
 }
 return false;
}
bool blocked(float x,float z,int type){
 // Modular walls and floors are intended to join/overlap other pieces.
 if(type>=10)return false;
 const float radius=(type==0||type==1||type==2||type==6)?6.3f:(type>=7?.42f:1.5f);
 for(const Entity&e:gEntities){
  if(e.type>=10)continue;
  float er=(e.type==0||e.type==1||e.type==2||e.type==6)?
      6.3f*e.size:(e.type>=7?.42f:1.5f)*e.size*std::max(e.sx,e.sz);
  float dx=e.x-x,dz=e.z-z;
  if(dx*dx+dz*dz<(radius+er)*(radius+er))return true;
 }
 return false;
}
bool staticInView(const Mat4&vp,const Entity&e){
 if(std::abs(e.x-gTarget.x)>quality().viewRangeMetres||
    std::abs(e.z-gTarget.z)>quality().viewRangeMetres)return false;
 const Vec4 clip=apply(vp,{e.x,e.y+2.8f*e.size*e.sy,e.z,1.f});
 const float radius=(e.type<=2?7.5f:
    (oe::medieval::validModule(e.type)?6.5f:(e.type==5?4.5f:3.5f)))*
    e.size*std::max({e.sx,e.sy,e.sz});
 if(clip.w < -radius)return false;
 if(clip.w <=radius)return true; // Keep very close objects visible.
 const float padX=radius*vp.m[0]*1.6f;
 const float padY=radius*vp.m[5]*1.6f;
 return std::abs(clip.x)<=clip.w+padX&&std::abs(clip.y)<=clip.w+padY;
}
void drawEntity(const Mat4&vp,const Frustum&frustum,const Entity&e){
 if(e.type==6){
  if(importedInView(frustum,e))drawImported(vp,e);
  return;
 }
 if(staticInView(vp,e))drawStatic(vp,e);
}
int add(int type,float x,float z,float yaw,float size){
 if(type<0||type>28||type==6||gEntities.size()>=MAX_ENTITIES)return 0;
 recordEdit();
 int id=gNextId++;gEntities.push_back({id,type,x,z,yaw,size,"",terrainHeightAt(x,z)});gSelected=id;return id;
}
void reset(bool demo){
 recordEdit(); // demo generation is a single undoable change
 const bool wasMuted=gSuspendHistory;
 gSuspendHistory=true;
 gEntities.clear();gBrushes.clear();gTerrainPaint.clear();gTerrainRevision++;gNextId=1;gSelected=0;
 gLakes.clear();gSelectedLake=-1;++gLakeRevision;
 gRoads.clear();gSelectedRoad=0;gActiveRoad=0;gSelectedRoadPoint=-1;gRoadPending=false;++gRoadRevision;
 gPaintTiles.clear();gMaterialCanvas.clear();
 clearMaterialGpu();gMaterialStrokeActive=false;
 for(auto& gpu:gPaintTileGpu)if(gpu.second.texture)glDeleteTextures(1,&gpu.second.texture);
 gPaintTileGpu.clear();gPrecisionPaintActive=false;gPaintCursorVisible=false;
 gSceneInitialized=true;gDemoRoads=demo;gMapTopDown=false;
 gTarget={0,1.2f,1.3f};gDistance=28.f;
 if(demo){
  add(0,-8,-5,0,1);
  add(1,0,-9,0,1);
  add(2,8,-5,0,1);
  add(3,-5,4,0,1);
  add(3,5,4,0,1);
  add(4,0,4,0,1);
  add(5,-11,8,0,1.2f);
  add(5,11,8,0,1.2f);
  gSelected=0;
 }
 gSuspendHistory=wasMuted;
}
Entity* selected(){for(auto &e:gEntities)if(e.id==gSelected)return &e;return nullptr;}
const char* nameFor(int type){
 const char* names[]={"Таверна","Кузница","Аукционный дом","Фонарь","Фонтан","Дерево","Модель GLB","Камни","Куст","Лавка","Стена","Пол","Крыша","Дверная рама","Окно","Колонна","Лестница","Каменный мост","Деревянный мост","Каменный фундамент","Узкий мостик","Верёвочный мост","Каменный брод","Свайный фундамент","Руины основания","Основание хижины","Основание башни","Широкий мост для телег","Настил через болото"};
 return type>=0&&type<29?names[type]:"Объект";
}

// Touch-friendly axis gizmo; drawn using existing simple meshes (no new shaders
// or animation loop). X=red, Y=green, Z=blue. Rotation only changes yaw.
Vec3 gizmoBase(const Entity&e){
 return {e.x,e.y+clampf(e.size*1.45f,.7f,3.0f),e.z};
}
float gizmoLength(){return clampf(gDistance*.095f,2.0f,13.f);}
Vec3 gizmoEndpoint(const Entity&e,int axis){
 Vec3 p=gizmoBase(e);
 const float len=gizmoLength();
 if(axis==0)p.x+=len;
 if(axis==1)p.y+=len;
 if(axis==2)p.z+=len;
 return p;
}
bool gizmoProject(Vec3 p,float&px,float&py){
 const Vec4 q=apply(viewProjection(),{p.x,p.y,p.z,1.f});
 if(q.w<=.1f)return false;
 px=(q.x/q.w*.5f+.5f)*gWidth;
 py=(.5f-q.y/q.w*.5f)*gHeight;
 return std::isfinite(px)&&std::isfinite(py);
}
int gizmoHit(float px,float py){
 if(gGizmoMode<0||!selected()||!std::isfinite(px)||!std::isfinite(py))return -1;
 const Entity&e=*selected();
 float bx,by;
 if(!gizmoProject(gizmoBase(e),bx,by))return -1;
 int hit=-1;
 float nearest=1e8f;
 for(int axis=0;axis<3;axis++){
  if(gGizmoMode==1&&axis!=1)continue;
  float ex,ey;
  if(!gizmoProject(gizmoEndpoint(e,axis),ex,ey))continue;
  const float dx=ex-bx,dy=ey-by,len2=dx*dx+dy*dy;
  if(len2<625.f)continue;
  const float t=((px-bx)*dx+(py-by)*dy)/len2;
  if(t<.22f||t>1.30f)continue;
  const float nx=bx+t*dx-px,ny=by+t*dy-py;
  const float distance=std::sqrt(nx*nx+ny*ny);
  const float target=std::max(22.f,gWidth*.025f); // scales with tablet viewport
  if(distance<target&&distance<nearest){nearest=distance;hit=axis;}
 }
 return hit;
}
void drawGizmo(const Mat4&vp){
 const Entity*e=selected();
 if(!e)return;
 const Vec3 origin=gizmoBase(*e);
 const float len=gizmoLength(),thin=clampf(gDistance*.0035f,.07f,.48f);
 const bool depth=glIsEnabled(GL_DEPTH_TEST);
 glDisable(GL_DEPTH_TEST); // selected handles always touchable
 const Color axisColor[3]={{.96f,.26f,.29f},{.31f,.90f,.45f},{.27f,.57f,1.f}};
 for(int axis=0;axis<3;axis++){
  if(gGizmoMode==1&&axis!=1)continue;
  Vec3 midpoint=origin,dimensions{thin,thin,thin};
  if(axis==0){midpoint.x+=len*.5f;dimensions.x=len;}
  if(axis==1){midpoint.y+=len*.5f;dimensions.y=len;}
  if(axis==2){midpoint.z+=len*.5f;dimensions.z=len;}
  draw(vp,0,midpoint,dimensions,0,axisColor[axis]);
  const Vec3 tip=gizmoEndpoint(*e,axis);
  const float cap=thin*(gGizmoMode==2?3.4f:2.4f);
  draw(vp,0,tip,{cap,cap,cap},0,axisColor[axis]);
 }
 if(depth)glEnable(GL_DEPTH_TEST);
}

} // namespace

extern "C" {
// Basis Universal ETC1S/UASTC LDR -> native GLES3 ETC2 RGBA8 blocks.
// Only 2D single-face, non-array KTX2 textures are accepted. Decoding runs
// on the asset worker thread (never on the GLES render thread).
static std::once_flag gBasisInitOnce;
JNIEXPORT jbyteArray JNICALL Java_game_oe_editor_TextureReader_nativeTranscodeBasisKtx2(
 JNIEnv*env,jclass,jbyteArray source,jint qualityLimit){
 if(!source||(qualityLimit!=1024&&qualityLimit!=2048))return nullptr;
 const jsize bytes=env->GetArrayLength(source);
 if(bytes<104||bytes>24*1024*1024)return nullptr;
 std::vector<uint8_t> encoded(static_cast<size_t>(bytes));
 env->GetByteArrayRegion(source,0,bytes,
      reinterpret_cast<jbyte*>(encoded.data()));
 if(env->ExceptionCheck())return nullptr;
 std::call_once(gBasisInitOnce,[](){basist::basisu_transcoder_init();});
 basist::ktx2_transcoder transcoder;
 if(!transcoder.init(encoded.data(),static_cast<uint32_t>(encoded.size()))||
    transcoder.get_faces()!=1||transcoder.get_layers()!=0||
    transcoder.get_header().m_pixel_depth!=0||
    !(transcoder.is_etc1s()||transcoder.is_uastc()))return nullptr;
 uint32_t width=transcoder.get_width(),height=transcoder.get_height();
 if(!width||!height||width>16384||height>16384||
    transcoder.get_levels()==0||transcoder.get_levels()>16)return nullptr;
 uint32_t level=0;
 // Prefer a source mip level that fits the import budget; do NOT
 // misrepresent downscaling of non-mipmapped GPU blocks.
 while(width>static_cast<uint32_t>(qualityLimit)||
       height>static_cast<uint32_t>(qualityLimit)){
  if(level+1>=transcoder.get_levels())return nullptr;
  level++;
  width=std::max<uint32_t>(1,transcoder.get_width()>>level);
  height=std::max<uint32_t>(1,transcoder.get_height()>>level);
 }
 const size_t blocksX=(width+3)/4,blocksY=(height+3)/4;
 const size_t nblocks=blocksX*blocksY;
 const size_t outputBytes=nblocks*16;
 if(!nblocks||nblocks>static_cast<size_t>(4*1024*1024)||outputBytes>32*1024*1024)
  return nullptr;
 if(!transcoder.start_transcoding())return nullptr;
 std::vector<uint8_t> etc2(outputBytes);
 const bool okay=transcoder.transcode_image_level(level,0,0,etc2.data(),
       static_cast<uint32_t>(nblocks),basist::transcoder_texture_format::cTFETC2_RGBA);
 if(!okay)return nullptr;
 const size_t packedBytes=12+outputBytes;
 if(packedBytes>static_cast<size_t>(std::numeric_limits<jsize>::max()))return nullptr;
 std::vector<uint8_t> packed(packedBytes);
 auto u32=[&](size_t at,uint32_t n){
  packed[at]=static_cast<uint8_t>(n);
  packed[at+1]=static_cast<uint8_t>(n>>8);
  packed[at+2]=static_cast<uint8_t>(n>>16);
  packed[at+3]=static_cast<uint8_t>(n>>24);
 };
 u32(0,width);u32(4,height);u32(8,2); // TextureReader.FORMAT_ETC2_RGBA
 std::copy(etc2.begin(),etc2.end(),packed.begin()+12);
 jbyteArray output=env->NewByteArray(static_cast<jsize>(packedBytes));
 if(!output)return nullptr;
 env->SetByteArrayRegion(output,0,static_cast<jsize>(packedBytes),
                         reinterpret_cast<const jbyte*>(packed.data()));
 return output;
}
// Offline Meshopt decoder — only bounded, validated bufferViews are accepted.
// This reconstructs decoded GLB views in memory. The original GLB is immutable.
JNIEXPORT jbyteArray JNICALL Java_game_oe_editor_GlbReader_nativeDecodeMeshopt(
 JNIEnv*env,jclass,jbyteArray encoded,jint count,jint stride,jint mode,jint filter){
 if(!encoded||count<=0||stride<=0||stride>256||mode<0||mode>2||filter<0||filter>4)
  return nullptr;
 const jsize length=env->GetArrayLength(encoded);
 const size_t bytes=static_cast<size_t>(count)*static_cast<size_t>(stride);
 if(length<=0||length>24*1024*1024||bytes>32*1024*1024||
    (mode==0&&(stride%4!=0))||
    (mode==1&&(count%3!=0||!(stride==2||stride==4)))||
    (mode==2&&!(stride==2||stride==4))||
    (mode!=0&&filter!=0)||
    (filter==1&&!(stride==4||stride==8))||
    (filter==2&&stride!=8)||
    (filter==3&&stride%4!=0)||
    (filter==4&&!(stride==4||stride==8)))return nullptr;
 std::vector<unsigned char> source(static_cast<size_t>(length)),output(bytes);
 env->GetByteArrayRegion(encoded,0,length,reinterpret_cast<jbyte*>(source.data()));
 if(env->ExceptionCheck())return nullptr;
 int result=mode==0?
    meshopt_decodeVertexBuffer(output.data(),count,stride,source.data(),source.size()):
    (mode==1?meshopt_decodeIndexBuffer(output.data(),count,stride,source.data(),source.size()):
             meshopt_decodeIndexSequence(output.data(),count,stride,source.data(),source.size()));
 if(result!=0)return nullptr;
 if(filter==1)meshopt_decodeFilterOct(output.data(),count,stride);
 else if(filter==2)meshopt_decodeFilterQuat(output.data(),count,stride);
 else if(filter==3)meshopt_decodeFilterExp(output.data(),count,stride);
 else if(filter==4)meshopt_decodeFilterColor(output.data(),count,stride);
 jbyteArray decoded=env->NewByteArray(static_cast<jsize>(bytes));
 if(!decoded)return nullptr;
 env->SetByteArrayRegion(decoded,0,static_cast<jsize>(bytes),
                         reinterpret_cast<const jbyte*>(output.data()));
 return decoded;
}
// Only RGBA8 decoded on worker thread is accepted; source images remain files.
bool validPaintKey(const std::string&key){
 if(key.size()!=31||key.compare(0,3,"tx_")!=0||
    !(key.substr(27)==".png"||key.substr(27)==".jpg"))return false;
 for(size_t i=3;i<27;i++)if(!((key[i]>='0'&&key[i]<='9')||
    (key[i]>='a'&&key[i]<='f')))return false;
 return true;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRegisterPaintTexture(
 JNIEnv*env,jclass,jstring name,jbyteArray data,jint width,jint height){
 if(!name||!data||width<=0||height<=0||width>1024||height>1024)return JNI_FALSE;
 const size_t bytes=static_cast<size_t>(width)*height*4;
 if(bytes>4u*1024u*1024u||env->GetArrayLength(data)!=static_cast<jsize>(bytes))
  return JNI_FALSE;
 const char*raw=env->GetStringUTFChars(name,nullptr);
 if(!raw)return JNI_FALSE;
 const std::string key(raw);env->ReleaseStringUTFChars(name,raw);
 if(!validPaintKey(key))return JNI_FALSE;
 auto present=gPaintTextures.find(key);
 if(present!=gPaintTextures.end()&&present->second.id)return JNI_TRUE;
 if(present==gPaintTextures.end()&&gPaintTextures.size()>=MAX_PAINT_TEXTURES)return JNI_FALSE;
 const size_t current=paintCacheBytes()-
  (present==gPaintTextures.end()?0:present->second.bytes.size());
 if(current+bytes>MAX_PAINT_CACHE_BYTES)return JNI_FALSE;
 TextureResource prepared;prepared.width=width;prepared.height=height;
 prepared.format=0;prepared.bytes.resize(bytes);
 env->GetByteArrayRegion(data,0,static_cast<jsize>(bytes),
  reinterpret_cast<jbyte*>(prepared.bytes.data()));
 if(env->ExceptionCheck())return JNI_FALSE;
 for(int i=0;i<8&&glGetError()!=GL_NO_ERROR;i++){}
 if(!uploadPaintTexture(prepared)||glGetError()!=GL_NO_ERROR){
  if(prepared.id)glDeleteTextures(1,&prepared.id);
  return JNI_FALSE;
 }
 if(present==gPaintTextures.end())gPaintTextures.emplace(key,std::move(prepared));
 else {if(present->second.id)glDeleteTextures(1,&present->second.id);present->second=std::move(prepared);}
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativePaintSelected(
 JNIEnv*env,jclass,jstring name){
 if(!name||!selected())return JNI_FALSE;
 const char*raw=env->GetStringUTFChars(name,nullptr);
 if(!raw)return JNI_FALSE;
 const std::string key(raw);env->ReleaseStringUTFChars(name,raw);
 if(!key.empty()&&(!validPaintKey(key)||gPaintTextures.find(key)==gPaintTextures.end()))
  return JNI_FALSE;
 if(selected()->paint==key)return JNI_TRUE;
 recordEdit();selected()->paint=key;return JNI_TRUE;
}

// Continuous brush composited into 64m tiles, rather than one draw per stamp.

// R4.1 material-aware mask authoring JNI, all invoked on the GLES render queue.
JNIEXPORT jintArray JNICALL Java_game_oe_editor_EditorActivity_nativeLayerCoords(
 JNIEnv*env,jclass){
 std::vector<jint> coords;
 for(const auto&tile:gMaterialCanvas.tiles())
  for(const auto&layer:tile.materials){
   (void)layer;coords.push_back(tile.x);coords.push_back(tile.z);
  }
 jintArray array=env->NewIntArray(static_cast<jsize>(coords.size()));
 if(array&&!coords.empty())
  env->SetIntArrayRegion(array,0,static_cast<jsize>(coords.size()),coords.data());
 return array;
}
JNIEXPORT jobjectArray JNICALL Java_game_oe_editor_EditorActivity_nativeLayerKeys(
 JNIEnv*env,jclass){
 jclass cls=env->FindClass("java/lang/String");
 if(!cls)return nullptr;
 jint count=0;
 for(const auto&tile:gMaterialCanvas.tiles())
  count+=static_cast<jint>(tile.materials.size());
 jobjectArray array=env->NewObjectArray(count,cls,nullptr);
 if(!array)return nullptr;
 jint index=0;
 for(const auto&tile:gMaterialCanvas.tiles())for(const auto&layer:tile.materials){
  jstring key=env->NewStringUTF(layer.material.c_str());
  if(!key)return nullptr;
  env->SetObjectArrayElement(array,index++,key);
  env->DeleteLocalRef(key);
 }
 return array;
}
JNIEXPORT jbyteArray JNICALL Java_game_oe_editor_EditorActivity_nativeLayerMask(
 JNIEnv*env,jclass,jint index){
 if(index<0)return nullptr;
 for(const auto&tile:gMaterialCanvas.tiles())for(const auto&layer:tile.materials){
  if(index--!=0)continue;
  jbyteArray array=env->NewByteArray(static_cast<jsize>(layer.weights.size()));
  if(array)env->SetByteArrayRegion(array,0,static_cast<jsize>(layer.weights.size()),
     reinterpret_cast<const jbyte*>(layer.weights.data()));
  return array;
 }
 return nullptr;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeLayerStrokeStart(
 JNIEnv*env,jclass,jfloat px,jfloat py,jfloat radius,jfloat opacity,jstring key,jboolean erase){
 gMaterialStrokeActive=false;
 if(!key||!std::isfinite(radius)||radius<.5f||radius>16.f||
    !std::isfinite(opacity)||opacity<.05f||opacity>1.f)return JNI_FALSE;
 float x,z;
 if(!groundPoint(px,py,x,z))return JNI_FALSE;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return JNI_FALSE;
 const std::string material(raw);env->ReleaseStringUTFChars(key,raw);
 if(!oe::terrainpaint::validMaterialKey(material))return JNI_FALSE;
 if(erase!=JNI_TRUE&&!paintTexture(material))return JNI_FALSE;
 // Preview on a copy: a failed tile/material budget cannot touch undo/redo.
 oe::terrainpaint::MaterialCanvas candidate=gMaterialCanvas;
 if(!candidate.stroke(x,z,x,z,radius,opacity,material,erase==JNI_TRUE,gWorldSize))
  return JNI_FALSE;
 recordEdit();
 gMaterialCanvas=std::move(candidate);
 if(erase==JNI_TRUE)clearMaterialGpu();
 gMaterialStrokeActive=true;gMaterialEraser=erase==JNI_TRUE;
 gLayerX=x;gLayerZ=z;gLayerRadius=radius;gLayerStrength=opacity;
 gLayerTexture=material;
 gPaintCursorVisible=true;gPaintCursorX=x;gPaintCursorZ=z;gPaintCursorRadius=radius;
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeLayerStrokeMove(
 JNIEnv*,jclass,jfloat px,jfloat py){
 if(!gMaterialStrokeActive)return JNI_FALSE;
 float x,z;
 if(!groundPoint(px,py,x,z))return JNI_FALSE;
 if(!gMaterialCanvas.stroke(gLayerX,gLayerZ,x,z,gLayerRadius,gLayerStrength,
                            gLayerTexture,gMaterialEraser,gWorldSize))return JNI_FALSE;
 if(gMaterialEraser)clearMaterialGpu();
 gLayerX=x;gLayerZ=z;
 gPaintCursorVisible=true;gPaintCursorX=x;gPaintCursorZ=z;gPaintCursorRadius=gLayerRadius;
 return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeLayerStrokeEnd(
 JNIEnv*,jclass){
 gMaterialStrokeActive=false;gPaintCursorVisible=false;
}

JNIEXPORT jintArray JNICALL Java_game_oe_editor_EditorActivity_nativePaintTileCoords(
 JNIEnv*env,jclass){
 const auto& list=gPaintTiles.tiles();
 jintArray result=env->NewIntArray(static_cast<jsize>(list.size()*2));
 if(!result)return nullptr;
 std::vector<jint> coords;coords.reserve(list.size()*2);
 for(const auto& tile:list){coords.push_back(tile.x);coords.push_back(tile.z);}
 if(!coords.empty())env->SetIntArrayRegion(result,0,static_cast<jsize>(coords.size()),coords.data());
 return result;
}
JNIEXPORT jbyteArray JNICALL Java_game_oe_editor_EditorActivity_nativePaintTileData(
 JNIEnv*env,jclass,jint index){
 const auto& tiles=gPaintTiles.tiles();
 if(index<0||static_cast<size_t>(index)>=tiles.size())return nullptr;
 const auto& src=tiles[static_cast<size_t>(index)].pixels;
 jbyteArray out=env->NewByteArray(static_cast<jsize>(src.size()));
 if(out)env->SetByteArrayRegion(out,0,static_cast<jsize>(src.size()),
     reinterpret_cast<const jbyte*>(src.data()));
 return out;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativePaintCursor(
 JNIEnv*,jclass,jfloat x,jfloat y,jfloat radius){
 if(!std::isfinite(radius)||radius<.5f||radius>16.f)return JNI_FALSE;
 float gx,gz;
 if(!groundPoint(x,y,gx,gz)){gPaintCursorVisible=false;return JNI_FALSE;}
 gPaintCursorVisible=true;gPaintCursorX=gx;gPaintCursorZ=gz;gPaintCursorRadius=radius;
 return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativePaintStrokeEnd(
 JNIEnv*,jclass){
 gPrecisionPaintActive=false;gPaintCursorVisible=false;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativePaintStrokeBegin(
 JNIEnv*env,jclass,jfloat px,jfloat py,jfloat radius,jfloat opacity,
 jstring selectedTexture,jboolean erase){
 gPrecisionPaintActive=false;gPaintCursorVisible=false;
 if(!std::isfinite(radius)||radius<.5f||radius>16.f||
    !std::isfinite(opacity)||opacity<.05f||opacity>1.f)return JNI_FALSE;
 float x,z;
 if(!groundPoint(px,py,x,z))return JNI_FALSE;
 const bool remove=erase==JNI_TRUE;
 std::string textureKey;
 oe::terrainpaint::Image source;
 if(!remove){
  if(!selectedTexture)return JNI_FALSE;
  const char*key=env->GetStringUTFChars(selectedTexture,nullptr);
  if(!key)return JNI_FALSE;
  textureKey=key;env->ReleaseStringUTFChars(selectedTexture,key);
  const TextureResource* tex=paintTexture(textureKey);
  if(!tex||tex->format!=0||!tex->width||!tex->height||
     tex->bytes.size()!=static_cast<size_t>(tex->width)*tex->height*4)
     return JNI_FALSE;
  source={tex->width,tex->height,tex->bytes.data()};
 }
 // One undo record per uninterrupted stroke; no per-move history allocation.
 recordEdit();
 if(!gPaintTiles.paint(x,z,x,z,radius,opacity,remove,source,gWorldSize)){
  if(!gUndo.empty())gUndo.pop_back();
  return JNI_FALSE;
 }
 gPrecisionPaintActive=true;
 gStrokeX=x;gStrokeZ=z;gStrokeRadius=radius;gStrokeOpacity=opacity;
 gPaintEraser=remove;gStrokeTexture=std::move(textureKey);
 gPaintCursorVisible=true;gPaintCursorX=x;gPaintCursorZ=z;gPaintCursorRadius=radius;
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativePaintStrokeMove(
 JNIEnv*,jclass,jfloat px,jfloat py){
 if(!gPrecisionPaintActive)return JNI_FALSE;
 float x,z;
 if(!groundPoint(px,py,x,z))return JNI_FALSE;
 oe::terrainpaint::Image source;
 if(!gPaintEraser){
  const TextureResource*tex=paintTexture(gStrokeTexture);
  if(!tex||tex->format!=0||
     tex->bytes.size()!=static_cast<size_t>(tex->width)*tex->height*4)return JNI_FALSE;
  source={tex->width,tex->height,tex->bytes.data()};
 }
 if(!gPaintTiles.paint(gStrokeX,gStrokeZ,x,z,gStrokeRadius,gStrokeOpacity,
                       gPaintEraser,source,gWorldSize))return JNI_FALSE;
 gStrokeX=x;gStrokeZ=z;
 gPaintCursorVisible=true;gPaintCursorX=x;gPaintCursorZ=z;gPaintCursorRadius=gStrokeRadius;
 return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativePaintTerrainAtScreen(
 JNIEnv*env,jclass,jfloat px,jfloat py,jfloat radius,jstring name){
 if(!name||!std::isfinite(radius)||radius<4.f||radius>64.f||
    gTerrainPaint.size()>=MAX_TERRAIN_PAINT)return 0;
 const char*raw=env->GetStringUTFChars(name,nullptr);
 if(!raw)return 0;
 const std::string key(raw);env->ReleaseStringUTFChars(name,raw);
 if(!validPaintKey(key)||gPaintTextures.find(key)==gPaintTextures.end())return 0;
 float x,z;
 if(!groundPoint(px,py,x,z))return 0;
 recordEdit();gTerrainPaint.push_back({x,z,radius,key});
 return static_cast<jint>(gTerrainPaint.size());
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeInit(JNIEnv*,jclass){
 setupGL();if(!gSceneInitialized)reset(true);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeResize(JNIEnv*,jclass,jint w,jint h){
 gWidth=std::max(1,int(w));gHeight=std::max(1,int(h));
 glViewport(0,0,gWidth,gHeight);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeDraw(JNIEnv*,jclass){
 const auto begin=std::chrono::steady_clock::now();
 glViewport(0,0,gWidth,gHeight);
 glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
 glUseProgram(gShader.program);
 gDrawCalls=0;gTriangles=0;
 gLodDrawCounts[0]=gLodDrawCounts[1]=gLodDrawCounts[2]=0;
 Mat4 vp=viewProjection();
 const Frustum frustum=makeFrustum(vp);
 drawWorld(vp);
 drawTerrainPaint(vp);
 drawPrecisionPaint(vp);
 drawMaterialLayers(vp);
 drawMapGuide(vp);
 drawRoads(vp);
 for(const auto&e:gEntities)drawEntity(vp,frustum,e);
 drawLakes(vp);
 drawGizmo(vp);
 drawRoadHandles(vp);
 drawPrecisionCursor(vp);
 glBindVertexArray(0);
 const auto end=std::chrono::steady_clock::now();
 gSubmitCpuMs=std::chrono::duration<float,std::milli>(end-begin).count();
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSetGizmoMode(
 JNIEnv*,jclass,jint mode){
 if(mode>=0&&mode<=2){gGizmoMode=mode;gGizmoActive=false;}
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeGizmoHit(
 JNIEnv*,jclass,jfloat px,jfloat py){return gizmoHit(px,py);}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeGizmoBegin(
 JNIEnv*,jclass,jint axis){
 if(!selected()||axis<0||axis>2||(gGizmoMode==1&&axis!=1))
  return JNI_FALSE;
 recordEdit();gGizmoActive=true;gGizmoActiveId=gSelected;
 return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeGizmoDrag(
 JNIEnv*,jclass,jint axis,jfloat dx,jfloat dy){
 if(!gGizmoActive||gGizmoActiveId!=gSelected||!std::isfinite(dx)||!std::isfinite(dy)||
    std::abs(dx)>1000.f||std::abs(dy)>1000.f||axis<0||axis>2)return;
 Entity*e=selected();if(!e)return;
 const Vec3 origin=gizmoBase(*e),tip=gizmoEndpoint(*e,axis);
 float bx,by,ex,ey;
 if(!gizmoProject(origin,bx,by)||!gizmoProject(tip,ex,ey))return;
 const float ux=ex-bx,uy=ey-by,length=std::sqrt(ux*ux+uy*uy);
 if(length<25.f)return;
 const float pixels=(dx*ux+dy*uy)/length;
 if(gGizmoMode==0){
  const float amount=clampf(pixels/length*gizmoLength(),-1.5f,1.5f);
  if(axis==0)e->x=clampf(e->x+amount,-worldHalf(),worldHalf());
  if(axis==1)e->y=clampf(e->y+amount,-24.f,160.f);
  if(axis==2)e->z=clampf(e->z+amount,-worldHalf(),worldHalf());
 }else if(gGizmoMode==1&&axis==1){
  e->yaw=std::fmod(e->yaw+clampf(pixels*.55f,-20.f,20.f)+360.f,360.f);
 }else if(gGizmoMode==2){
  const float factor=std::exp(clampf(pixels*.009f,-.20f,.20f));
  float *value=axis==0?&e->sx:(axis==1?&e->sy:&e->sz);
  *value=clampf(*value*factor,.10f,8.f);
 }
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeGizmoEnd(JNIEnv*,jclass){
 gGizmoActive=false;gGizmoActiveId=0;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeOrbit(JNIEnv*,jclass,jfloat dx,jfloat dy,jfloat zoom){
 if(!gMapTopDown||dx!=0.f||dy!=0.f){
  gMapTopDown=false;
  gYaw-=dx*.006f;
  gPitch=clampf(gPitch+dy*.006f,-1.16f,1.25f);
 }
 gDistance=clampf(gDistance*clampf(zoom,.6f,1.5f),5,210);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativePan(JNIEnv*,jclass,jfloat dx,jfloat dy){
 const float speed=gDistance*.0035f;
 const float c=std::cos(gYaw),s=std::sin(gYaw);
 gTarget.x=clampf(gTarget.x+(-dx*c+dy*s)*speed,-worldHalf()+4,worldHalf()-4);
 gTarget.z=clampf(gTarget.z+(dx*s+dy*c)*speed,-worldHalf()+4,worldHalf()-4);
 gTarget.y=terrainHeightAt(gTarget.x,gTarget.z)+1.2f;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeFocusLocation(JNIEnv*,jclass,jint index){
 if(index<0 || index>=static_cast<jint>(sizeof(LOCATIONS)/sizeof(LOCATIONS[0])))return;
 gTarget={LOCATIONS[index].x,terrainHeightAt(LOCATIONS[index].x,LOCATIONS[index].z)+1.2f,LOCATIONS[index].z};
 gDistance=index==0?35.f:68.f;
}
// Editor-to-runtime adapter: only this JNI layer knows about editor globals.
// The portable oe_runtime_core library NEVER includes GLES, JNI or Java.
// Build a COPY of the current edited entities and run a deterministic probe.
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeRuntimeCoreReport(
 JNIEnv*env,jclass){
 std::vector<oe::runtime::SceneItem> staged;
 staged.reserve(gEntities.size());
 std::size_t importedModels=0,modules=0;
 for(const auto&entity:gEntities){
  oe::runtime::SceneItem item;
  item.name=std::string(nameFor(entity.type))+" #"+std::to_string(entity.id);
  item.transform.position={
   static_cast<double>(entity.x),
   static_cast<double>(entity.y),
   static_cast<double>(entity.z)
  };
  const double halfYaw=static_cast<double>(entity.yaw)*static_cast<double>(RAD)*.5;
  item.transform.orientation={0.0,std::sin(halfYaw),0.0,std::cos(halfYaw)};
  item.transform.scale={
   static_cast<double>(entity.size)*entity.sx,
   static_cast<double>(entity.size)*entity.sy,
   static_cast<double>(entity.size)*entity.sz
  };
  if(entity.type==6){
   item.meshAsset=entity.asset;
   ++importedModels;
  }else{
   item.meshAsset="builtin:"+std::to_string(entity.type);
   ++modules;
  }
  staged.push_back(std::move(item));
 }
 oe::runtime::World preview(MAX_ENTITIES+4);
 if(!preview.loadStaticScene(staged))
  return env->NewStringUTF("Ошибка: сцена не прошла проверку независимого игрового ядра.");
 const std::vector<oe::runtime::EntitySnapshot> before=preview.snapshot();
 const auto actor=preview.create("runtime-test-actor");
 if(!preview.alive(actor))
  return env->NewStringUTF("Ошибка: не удалось создать тестовый игровой объект.");
 oe::runtime::Transform spawn;
 if(!preview.setTransform(actor,spawn)||
    !preview.setVelocity(actor,{{1.0,0.0,0.0}})||
    !preview.stepTicks(120))
  return env->NewStringUTF("Ошибка симуляции OE Runtime Core.");
 const oe::runtime::Transform*simulated=preview.transform(actor);
 if(!simulated||std::abs(simulated->position.x-2.0)>1e-6)
  return env->NewStringUTF("Ошибка контрольной симуляции 120 тиков.");
 const std::vector<oe::runtime::EntitySnapshot> after=preview.snapshot();
 bool sceneUnchanged=after.size()==before.size()+1;
 if(sceneUnchanged)for(size_t i=0;i<before.size();i++){
  const auto&beforePos=before[i].transform.position;
  const auto&afterPos=after[i].transform.position;
  if(beforePos.x!=afterPos.x||beforePos.y!=afterPos.y||
     beforePos.z!=afterPos.z||before[i].renderer.has_value()!=after[i].renderer.has_value()){
   sceneUnchanged=false;break;
  }
 }
 std::ostringstream report;
 report<<"OE Game Runtime Core 0.7.0\n\n"
       <<"Редактор -> копия игрового мира: "<<before.size()<<" объектов\n"
       <<"  Встроенные детали: "<<modules<<"\n"
       <<"  Импортированные 3D-модели: "<<importedModels<<"\n"
       <<"Отдельный тестовый актор: 1\n"
       <<"Фиксированный шаг: 60 тиков/с\n"
       <<"Проверено: "<<preview.ticks()<<" тиков / "
       <<std::fixed<<std::setprecision(2)<<preview.elapsedSeconds()<<" с\n"
       <<"Тест движения: X = "<<simulated->position.x<<" м (ожидается 2.00)\n"
       <<"Позиции исходной копии: "<<(sceneUnchanged?"сохранены":"ОШИБКА")<<"\n\n"
       <<"Текущая сцена редактора НЕ изменена.\n"
       <<"Физика, управление, скрипты, Play и экспорт APK: ЕЩЁ НЕ ГОТОВЫ.";
 return env->NewStringUTF(report.str().c_str());
}

JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeRecommendQuality(
 JNIEnv*,jclass,jint ramMiB){
 GLint maxTexture=0;
 glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxTexture);
 const GLubyte*raw=glGetString(GL_RENDERER);
 const std::string renderer=raw?
  std::string(reinterpret_cast<const char*>(raw)):"unknown";
 const bool software=renderer.find("SwiftShader")!=std::string::npos||
  renderer.find("llvmpipe")!=std::string::npos||
  renderer.find("softpipe")!=std::string::npos;
 return static_cast<jint>(oe::recommendedTier(
   std::max(0,int(ramMiB)),std::max(0,int(maxTexture)),software));
}
JNIEXPORT jfloat JNICALL Java_game_oe_editor_EditorActivity_nativeQualityScale(
 JNIEnv*,jclass,jint tier){
 return oe::profile(tier).resolutionScale;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeSetQualityProfile(
 JNIEnv*,jclass,jint tier){
 if(!oe::validTier(tier))return JNI_FALSE;
 if(gQualityTier!=tier){
  gQualityTier=tier;
  // Culling and tile range must be rebuilt even if camera stands still.
  gTerrain.gridX=2147483647;
 }
 return JNI_TRUE;
}
JNIEXPORT jobjectArray JNICALL Java_game_oe_editor_EditorActivity_nativeGetGpuCapabilities(
 JNIEnv*env,jclass){
 jclass stringClass=env->FindClass("java/lang/String");
 if(!stringClass)return nullptr;
 jobjectArray result=env->NewObjectArray(6,stringClass,nullptr);
 if(!result)return nullptr;
 const auto get=[&](GLenum key)->std::string{
  const GLubyte*raw=glGetString(key);
  if(!raw)return "unknown";
  std::string textValue(reinterpret_cast<const char*>(raw));
  return textValue.substr(0,160);
 };
 GLint maxTexture=0,maxUnits=0;
 glGetIntegerv(GL_MAX_TEXTURE_SIZE,&maxTexture);
 glGetIntegerv(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS,&maxUnits);
 bool astc=false;
 GLint extCount=0;
 glGetIntegerv(GL_NUM_EXTENSIONS,&extCount);
 for(GLint i=0;i<extCount&&i<2048;i++){
  const GLubyte*ext=glGetStringi(GL_EXTENSIONS,static_cast<GLuint>(i));
  if(!ext)continue;
  const std::string name(reinterpret_cast<const char*>(ext));
  if(name=="GL_KHR_texture_compression_astc_ldr"||
     name=="GL_OES_texture_compression_astc")astc=true;
 }
 const std::array<std::string,6> values={
  get(GL_VENDOR),get(GL_RENDERER),get(GL_VERSION),
  std::to_string(std::max(0,maxTexture)),astc?"yes":"no",
  std::to_string(std::max(0,maxUnits))
 };
 for(jsize i=0;i<static_cast<jsize>(values.size());i++){
  jstring value=env->NewStringUTF(values[static_cast<size_t>(i)].c_str());
  if(!value)return nullptr;
  env->SetObjectArrayElement(result,i,value);
  env->DeleteLocalRef(value);
 }
 return result;
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeRenderStats(JNIEnv*env,jclass){
 std::ostringstream out;
 out<<"GPU-профиль: "<<quality().name<<" · CPU submit "<<std::fixed<<std::setprecision(2)
 <<gSubmitCpuMs<<" мс · "<<gDrawCalls<<" draw / "<<gTriangles<<" треуг."
 <<" · LOD "<<gLodDrawCounts[0]<<"/"<<gLodDrawCounts[1]<<"/"<<gLodDrawCounts[2];
 return env->NewStringUTF(out.str().c_str());
}

// Road authoring JNI: always called on GLSurfaceView's GL thread.

JNIEXPORT jdoubleArray JNICALL Java_game_oe_editor_EditorActivity_nativeMapTarget(
 JNIEnv*env,jclass){
 jdoubleArray result=env->NewDoubleArray(2);
 if(result){
  const jdouble xy[2]={static_cast<double>(gTarget.x),static_cast<double>(gTarget.z)};
  env->SetDoubleArrayRegion(result,0,2,xy);
 }
 return result;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeMapStage(
 JNIEnv*env,jclass,jbyteArray rgba,jint width,jint height){
 if(!rgba||width<=0||height<=0||width>2048||height>2048)return JNI_FALSE;
 const size_t length=static_cast<size_t>(width)*height*4;
 if(length>MAX_MAP_RGBA_BYTES||static_cast<size_t>(env->GetArrayLength(rgba))!=length)
  return JNI_FALSE;
 MapGuide incoming;
 incoming.pixelWidth=width;incoming.pixelHeight=height;
 incoming.rgba.resize(length);
 env->GetByteArrayRegion(rgba,0,static_cast<jsize>(length),
                         reinterpret_cast<jbyte*>(incoming.rgba.data()));
 if(env->ExceptionCheck()||!uploadMapTexture(incoming))return JNI_FALSE;
 releaseMapGpu(gMapStage);
 gMapStage=std::move(incoming);
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeMapCommit(
 JNIEnv*,jclass,jdouble cx,jdouble cz,jdouble width,jfloat opacity,jboolean visible){
 if(!gMapStage.texture)return JNI_FALSE;
 const double h=width*gMapStage.pixelHeight/gMapStage.pixelWidth;
 if(!validMapBounds(cx,cz,width,h,opacity)||
    std::abs(cx)>worldHalf()||std::abs(cz)>worldHalf()||
    width>gWorldSize||h>gWorldSize)return JNI_FALSE;
 releaseMapGpu(gMapGuide);
 gMapGuide=std::move(gMapStage);
 gMapStage={};
 gMapGuide.centerX=cx;gMapGuide.centerZ=cz;
 gMapGuide.widthMetres=width;gMapGuide.heightMetres=h;
 gMapGuide.opacity=opacity;gMapGuide.visible=visible==JNI_TRUE;
 ++gMapGuide.revision;gMapGuide.meshRevision=-1;
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeMapConfigure(
 JNIEnv*,jclass,jdouble cx,jdouble cz,jdouble width,jfloat opacity,jboolean visible){
 if(!gMapGuide.texture)return JNI_FALSE;
 const double h=width*gMapGuide.pixelHeight/gMapGuide.pixelWidth;
 if(!validMapBounds(cx,cz,width,h,opacity)||
    std::abs(cx)>worldHalf()||std::abs(cz)>worldHalf()||
    width>gWorldSize||h>gWorldSize)return JNI_FALSE;
 gMapGuide.centerX=cx;gMapGuide.centerZ=cz;
 gMapGuide.widthMetres=width;gMapGuide.heightMetres=h;
 gMapGuide.opacity=opacity;gMapGuide.visible=visible==JNI_TRUE;
 ++gMapGuide.revision;
 return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeMapAbort(JNIEnv*,jclass){
 releaseMapGpu(gMapStage);gMapStage={};
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeMapClear(JNIEnv*,jclass){
 releaseMapGpu(gMapStage);gMapStage={};
 releaseMapGpu(gMapGuide);gMapGuide={};
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeMapTopView(JNIEnv*,jclass){
 gMapTopDown=true;
 gYaw=0.f;gPitch=1.55f;
 if(gMapGuide.texture){
  gTarget.x=static_cast<float>(gMapGuide.centerX);
  gTarget.z=static_cast<float>(gMapGuide.centerZ);
  gTarget.y=terrainHeightAt(gTarget.x,gTarget.z)+1.2f;
  gDistance=clampf(static_cast<float>(gMapGuide.widthMetres*.55),45.f,210.f);
 }
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeMapPerspective(JNIEnv*,jclass){
 gMapTopDown=false;
 gPitch=.7f;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeRoadBegin(JNIEnv*,jclass){
 gActiveRoad=0;gSelectedRoad=0;gRoadPending=false;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeRoadFinish(JNIEnv*,jclass){
 gActiveRoad=0;gRoadPending=false;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadContinue(JNIEnv*,jclass){
 if(!gRoads.find(gSelectedRoad))return JNI_FALSE;
 gActiveRoad=gSelectedRoad;gRoadPending=false;return JNI_TRUE;
}
// Build and validate the COMPLETE graph before recording undo or touching live
// roads. Returns false for overlaps, unsafe topology, GPU/world budgets.
bool commitRoadNetwork(const std::vector<oe::roads::Road>& proposed){
 oe::roads::RoadTopology topology;
 if(!oe::roads::buildRoadTopology(proposed,topology)||
    !roadBudget(topology.roads,gWorldSize))return false;
 oe::roads::Network staging=gRoads;
 if(!staging.replaceAll(topology.roads))return false;
 recordEdit();
 gRoads=std::move(staging);
 gSelectedRoadPoint=-1;++gRoadRevision;
 return true;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeRoadTap(
 JNIEnv*,jclass,jfloat px,jfloat py){
 float x,z;
 if(!groundPoint(px,py,x,z))return 0;
 const oe::roads::Point point{static_cast<double>(x),static_cast<double>(z)};
 if(!gActiveRoad){
  if(!gRoadPending){
   if(gRoads.all().size()>=oe::roads::kMaxRoads||
      std::abs(point.x)>worldHalf()-gRoadWidth*2||
      std::abs(point.z)>worldHalf()-gRoadWidth*2)return -1;
   gRoadFirstPoint=point;gRoadPending=true;return 1;
  }
  oe::roads::Network staging=gRoads;
  const std::uint64_t id=staging.create(
      "Road "+std::to_string(gRoads.all().size()+1),gRoadMaterial,gRoadWidth,
      {gRoadFirstPoint,point});
  if(!id)return -1;
  oe::roads::RoadTopology topology;
  if(!oe::roads::buildRoadTopology(staging.all(),topology)||
     !roadBudget(topology.roads,gWorldSize)||
     !staging.replaceAll(topology.roads))return -1;
  recordEdit();
  gRoads=std::move(staging);
  gActiveRoad=gSelectedRoad=id;gRoadPending=false;
  gSelectedRoadPoint=-1;++gRoadRevision;return 2;
 }
 const auto* current=gRoads.find(gActiveRoad);
 if(!current){gActiveRoad=0;return -1;}
 auto modified=*current;
 modified.controlPoints.push_back(point);
 auto proposed=gRoads.all();
 for(auto& road:proposed)if(road.id==modified.id){road=modified;break;}
 if(!commitRoadNetwork(proposed))return -1;
 gSelectedRoad=modified.id;
 return 3;
}
JNIEXPORT jobjectArray JNICALL Java_game_oe_editor_EditorActivity_nativeRoadLabels(
 JNIEnv*env,jclass){
 jclass klass=env->FindClass("java/lang/String");
 if(!klass)return nullptr;
 const auto& roads=gRoads.all();
 jobjectArray result=env->NewObjectArray(static_cast<jsize>(roads.size()),klass,nullptr);
 if(!result)return nullptr;
 for(jsize i=0;i<static_cast<jsize>(roads.size());++i){
  const auto& road=roads[static_cast<size_t>(i)];
  std::ostringstream out;
  out<<"Road #"<<road.id<<" · "<<road.controlPoints.size()<<" pts · "
     <<std::fixed<<std::setprecision(1)<<road.widthMetres<<" m";
  jstring label=env->NewStringUTF(out.str().c_str());
  if(!label)return nullptr;
  env->SetObjectArrayElement(result,i,label);env->DeleteLocalRef(label);
 }
 return result;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadSelect(
 JNIEnv*,jclass,jint index){
 const auto& roads=gRoads.all();
 if(index<0||static_cast<size_t>(index)>=roads.size())return JNI_FALSE;
 gSelectedRoad=roads[static_cast<size_t>(index)].id;
 gActiveRoad=0;gRoadPending=false;gSelectedRoadPoint=-1;return JNI_TRUE;
}

// Editor R5: selecting, moving or smoothing an authored control point does
// not change the road ID or the .oezip scene data schema.
JNIEXPORT jdoubleArray JNICALL Java_game_oe_editor_EditorActivity_nativeRoadPointCoords(
 JNIEnv*env,jclass){
 const oe::roads::Road* road=gRoads.find(gSelectedRoad);
 const std::size_t count=road?road->controlPoints.size():0;
 jdoubleArray result=env->NewDoubleArray(static_cast<jsize>(count*2));
 if(!result)return nullptr;
 std::vector<jdouble> coords;
 coords.reserve(count*2);
 if(road)for(const auto&point:road->controlPoints){
  coords.push_back(point.x);coords.push_back(point.z);
 }
 if(!coords.empty())
  env->SetDoubleArrayRegion(result,0,static_cast<jsize>(coords.size()),coords.data());
 return result;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadPointSelect(
 JNIEnv*,jclass,jint index){
 const auto* road=gRoads.find(gSelectedRoad);
 if(!road||index<0||static_cast<size_t>(index)>=road->controlPoints.size())
  return JNI_FALSE;
 gSelectedRoadPoint=index;
 gActiveRoad=0;gRoadPending=false;
 return JNI_TRUE;
}
bool updateSelectedRoadPoint(double x,double z){
 const auto* current=gRoads.find(gSelectedRoad);
 if(!current||gSelectedRoadPoint<0||
    static_cast<std::size_t>(gSelectedRoadPoint)>=current->controlPoints.size()||
    !std::isfinite(x)||!std::isfinite(z))return false;
 auto modified=*current;
 modified.controlPoints[static_cast<std::size_t>(gSelectedRoadPoint)]={x,z};
 auto proposed=gRoads.all();
 for(auto& road:proposed)if(road.id==modified.id){road=modified;break;}
 return commitRoadNetwork(proposed);
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadPointMove(
 JNIEnv*,jclass,jdouble x,jdouble z){
 return updateSelectedRoadPoint(x,z)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadPointTapMove(
 JNIEnv*,jclass,jfloat px,jfloat py){
 float x,z;
 if(!groundPoint(px,py,x,z))return JNI_FALSE;
 return updateSelectedRoadPoint(static_cast<double>(x),static_cast<double>(z))?
        JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadSmooth(
 JNIEnv*,jclass,jdouble metres){
 const oe::roads::Road* current=gRoads.find(gSelectedRoad);
 if(!current)return JNI_FALSE;
 oe::roads::Road smooth;
 if(!oe::roads::roundCorners(*current,metres,smooth))return JNI_FALSE;
 auto candidate=gRoads.all();
 for(auto& road:candidate)if(road.id==smooth.id){road=smooth;break;}
 if(!commitRoadNetwork(candidate))return JNI_FALSE;
 gSelectedRoadPoint=-1;gActiveRoad=0;
 return JNI_TRUE;
}
// Graph statistics are derived from the same deterministic calculation
// that bakes the roads; old v1-v5 scenes require no extra graph JSON fields.
// R5.2 diagnostics expose whether stitched surfaces were accepted, their
// junction count, triangle budget, and fallback to the R5 preview.
JNIEXPORT jintArray JNICALL Java_game_oe_editor_EditorActivity_nativeMedievalSurfaceStats(
 JNIEnv*env,jclass){
 oe::roads::RoadTopology network;
 oe::roads::MedievalSurface surfaces;
 const auto height=[](double x,double z)->double{
  return terrainHeightAt(static_cast<float>(x),static_cast<float>(z));
 };
 const bool accepted=oe::roads::buildRoadTopology(gRoads.all(),network,0.)&&
                     oe::roads::buildMedievalSurface(network,height,surfaces)&&
                     surfaces.triangles*3<=MAX_ROAD_GPU_VERTICES;
 const jint stats[3]={accepted?1:0,
     accepted?static_cast<jint>(surfaces.stitchedNodes):0,
     accepted?static_cast<jint>(surfaces.triangles):0};
 jintArray array=env->NewIntArray(3);
 if(array)env->SetIntArrayRegion(array,0,3,stats);
 return array;
}
// Every lake is editor-authored water (not game physics). Existing
// terrain brush is the excavation tool; placed water has a separate level.
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeLakePlaceAtScreen(
 JNIEnv*,jclass,jfloat px,jfloat py,jfloat radius,jfloat fillDepth){
 if(gLakes.size()>=oe::water::kMaxLakes||!std::isfinite(fillDepth)||
    fillDepth<.5f||fillDepth>32.f)return 0;
 float x=0,z=0;
 if(!groundPoint(px,py,x,z))return 0;
 const oe::water::Lake candidate{x,z,radius,terrainHeightAt(x,z)+fillDepth};
 if(!oe::water::validLake(candidate,worldHalf()))return 0;
 recordEdit();
 gLakes.push_back(candidate);gSelectedLake=static_cast<int>(gLakes.size())-1;
 ++gLakeRevision;
 return static_cast<jint>(gLakes.size());
}
JNIEXPORT jobjectArray JNICALL Java_game_oe_editor_EditorActivity_nativeLakeNames(
 JNIEnv*env,jclass){
 jclass klass=env->FindClass("java/lang/String");
 jobjectArray names=env->NewObjectArray(static_cast<jsize>(gLakes.size()),klass,nullptr);
 for(jsize i=0;i<static_cast<jsize>(gLakes.size());++i){
  const auto&lake=gLakes[static_cast<std::size_t>(i)];
  std::ostringstream line;line<<std::fixed<<std::setprecision(1)
      <<"Озеро "<<i+1<<" · X "<<lake.x<<" Z "<<lake.z
      <<" · радиус "<<lake.radius<<" м · вода "<<lake.level<<" м";
  jstring item=env->NewStringUTF(line.str().c_str());
  env->SetObjectArrayElement(names,i,item);env->DeleteLocalRef(item);
 }
 return names;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeLakeSelect(
 JNIEnv*,jclass,jint index){
 if(index<0||static_cast<std::size_t>(index)>=gLakes.size())return JNI_FALSE;
 gSelectedLake=index;return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeLakeAdjust(
 JNIEnv*,jclass,jfloat levelDelta,jfloat radiusDelta){
 if(gSelectedLake<0||static_cast<std::size_t>(gSelectedLake)>=gLakes.size()||
    !std::isfinite(levelDelta)||!std::isfinite(radiusDelta)||
    std::abs(levelDelta)>10.f||std::abs(radiusDelta)>32.f||
    (levelDelta==0.f&&radiusDelta==0.f))return JNI_FALSE;
 oe::water::Lake updated=gLakes[static_cast<std::size_t>(gSelectedLake)];
 updated.radius+=radiusDelta;updated.level+=levelDelta;
 if(!oe::water::validLake(updated,worldHalf()))return JNI_FALSE;
 recordEdit();
 gLakes[static_cast<std::size_t>(gSelectedLake)]=updated;
 ++gLakeRevision;return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeLakeDelete(
 JNIEnv*,jclass){
 if(gSelectedLake<0||static_cast<std::size_t>(gSelectedLake)>=gLakes.size())
   return JNI_FALSE;
 recordEdit();
 gLakes.erase(gLakes.begin()+gSelectedLake);
 gSelectedLake=-1;++gLakeRevision;return JNI_TRUE;
}
// R5.4 is a DERIVED runtime preparation view. No hidden changes to the
// authored road points or placed bridge transforms during inspection.
std::vector<oe::roads::BridgeInstance> authoredBridges(){
 std::vector<oe::roads::BridgeInstance> bridges;
 bridges.reserve(std::min<std::size_t>(oe::roads::kMaxBridges,gEntities.size()));
 for(const auto&e:gEntities){
  oe::roads::BridgeInstance bridge{
   e.id,e.type,e.x,e.y,e.z,e.yaw,e.size,e.sx,e.sy,e.sz
  };
  oe::roads::BridgeDeck deck;
  if(oe::roads::describeBridge(bridge,deck))bridges.push_back(bridge);
 }
 return bridges;
}
JNIEXPORT jintArray JNICALL Java_game_oe_editor_EditorActivity_nativeBridgeRoadStats(
 JNIEnv*env,jclass){
 oe::roads::RoadTopology graph;
 oe::roads::BridgeNetwork network;
 const auto height=[](double x,double z)->double{
  return terrainHeightAt(static_cast<float>(x),static_cast<float>(z));
 };
 const auto bridges=authoredBridges();
 const bool good=oe::roads::buildRoadTopology(gRoads.all(),graph,0.)&&
       oe::roads::buildBridgeNetwork(graph,bridges,height,network);
 int selectedStatus=-1;
 if(const Entity*entity=selected()){
  oe::roads::BridgeInstance sel{entity->id,entity->type,entity->x,
      entity->y,entity->z,entity->yaw,entity->size,
      entity->sx,entity->sy,entity->sz};
  oe::roads::BridgeDeck description;
  if(oe::roads::describeBridge(sel,description)){
   selectedStatus=0;
   for(const auto&link:network.links)if(link.bridgeId==entity->id){
    selectedStatus=link.footOnly?2:1;break;
   }
   if(entity->type==oe::medieval::kSteppingStoneFord)selectedStatus=3;
  }
 }
 const jint values[7]={
  good?1:0,
  good?static_cast<jint>(network.decks.size()):-1,
  good?static_cast<jint>(network.links.size()):-1,
  good?static_cast<jint>(network.unconnectedBridges):-1,
  good?static_cast<jint>(network.roadCollisionTriangles):-1,
  good?static_cast<jint>(network.collision.size()):-1,
  good?selectedStatus:-1
 };
 jintArray array=env->NewIntArray(7);
 if(array)env->SetIntArrayRegion(array,0,7,values);
 return array;
}
// Automatic *proposal* turns into one undoable transform only after the
// two shore roads pass heading/width/span/height checks. No mutation on fail.
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeBridgeAlignSelected(
 JNIEnv*,jclass){
 Entity*entity=selected();
 if(!entity)return JNI_FALSE;
 const oe::roads::BridgeInstance original{
   entity->id,entity->type,entity->x,entity->y,entity->z,
   entity->yaw,entity->size,entity->sx,entity->sy,entity->sz
 };
 oe::roads::RoadTopology graph;
 if(!oe::roads::buildRoadTopology(gRoads.all(),graph,0.))return JNI_FALSE;
 const auto height=[](double x,double z)->double{
  return terrainHeightAt(static_cast<float>(x),static_cast<float>(z));
 };
 oe::roads::BridgeInstance fitted;
 if(!oe::roads::proposeBridgeRoadFit(graph,original,height,fitted)||
    std::abs(fitted.x)>worldHalf()-1.f||
    std::abs(fitted.z)>worldHalf()-1.f)return JNI_FALSE;
 if(std::abs(fitted.x-original.x)<1.e-5&&
    std::abs(fitted.z-original.z)<1.e-5&&
    std::abs(fitted.y-original.y)<1.e-5&&
    std::abs(fitted.yawDegrees-original.yawDegrees)<1.e-5)return JNI_TRUE;
 recordEdit();
 entity->x=static_cast<float>(fitted.x);
 entity->z=static_cast<float>(fitted.z);
 entity->y=static_cast<float>(fitted.y);
 entity->yaw=static_cast<float>(fitted.yawDegrees);
 return JNI_TRUE;
}
JNIEXPORT jintArray JNICALL Java_game_oe_editor_EditorActivity_nativeRoadTopologyStats(
 JNIEnv*env,jclass){
 oe::roads::RoadTopology topology;
 const bool good=oe::roads::buildRoadTopology(gRoads.all(),topology);
 const jint values[4]={
  good?static_cast<jint>(topology.junctionCount):-1,
  good?static_cast<jint>(topology.nodes.size()):-1,
  good?static_cast<jint>(topology.edges.size()):-1,
  good?static_cast<jint>(topology.crossingCount):-1
 };
 jintArray array=env->NewIntArray(4);
 if(array)env->SetIntArrayRegion(array,0,4,values);
 return array;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadBakeTopology(
 JNIEnv*,jclass){
 if(gRoads.all().empty())return JNI_FALSE;
 return commitRoadNetwork(gRoads.all())?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeRoadIntersectionCount(
 JNIEnv*,jclass){
 std::vector<oe::roads::Junction> intersections;
 return oe::roads::findJunctions(gRoads.all(),intersections)?
        static_cast<jint>(intersections.size()): -1;
}

JNIEXPORT jdouble JNICALL Java_game_oe_editor_EditorActivity_nativeRoadSelectedWidth(
 JNIEnv*,jclass){
 const auto* road=gRoads.find(gSelectedRoad);
 return road?static_cast<jdouble>(road->widthMetres):static_cast<jdouble>(gRoadWidth);
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadSetWidth(
 JNIEnv*,jclass,jdouble width){
 if(!std::isfinite(width)||width<oe::roads::kMinRoadWidth||
    width>oe::roads::kMaxRoadWidth)return JNI_FALSE;
 if(const auto* selected=gRoads.find(gSelectedRoad)){
  auto road=*selected;road.widthMetres=width;
  auto preview=gRoads.all();
  for(auto& item:preview)if(item.id==road.id){item=road;break;}
  if(!roadBudget(preview,gWorldSize))return JNI_FALSE;
  recordEdit();
  if(!gRoads.update(road))return JNI_FALSE;
  ++gRoadRevision;
 }
 gRoadWidth=width;return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadSetMaterial(
 JNIEnv*,jclass,jint index){
 static const char* keys[]={"builtin:cobblestone","builtin:dirt",
                             "builtin:sand","builtin:grass"};
 if(index<0||index>=4)return JNI_FALSE;
 if(const auto* selected=gRoads.find(gSelectedRoad)){
  auto road=*selected;road.material=keys[index];
  recordEdit();if(!gRoads.update(road))return JNI_FALSE;
  ++gRoadRevision;
 }
 gRoadMaterial=keys[index];return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadRemoveLastPoint(
 JNIEnv*,jclass){
 const auto* selected=gRoads.find(gSelectedRoad);
 if(!selected)return JNI_FALSE;
 const std::uint64_t id=selected->id;
 if(selected->controlPoints.size()<=2){
  recordEdit();gRoads.remove(id);gSelectedRoad=0;gActiveRoad=0;
 }else{
  auto road=*selected;road.controlPoints.pop_back();
  recordEdit();gRoads.update(road);
 }
 gRoadPending=false;++gRoadRevision;
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRoadDeleteSelected(
 JNIEnv*,jclass){
 if(!gRoads.find(gSelectedRoad))return JNI_FALSE;
 recordEdit();gRoads.remove(gSelectedRoad);gSelectedRoad=0;gActiveRoad=0;
 gRoadPending=false;++gRoadRevision;return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeGetWorldSize(JNIEnv*,jclass){return gWorldSize;}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeSetWorldSize(JNIEnv*,jclass,jint newSize){
 if(!allowedWorldSize(newSize))return JNI_FALSE;
 float half=static_cast<float>(newSize)*.5f;
 // Never silently hide buildings when shrinking the world.
 for(const auto&e:gEntities)if(std::abs(e.x)>half-1.f||std::abs(e.z)>half-1.f)return JNI_FALSE;
 if(!roadBudget(gRoads.all(),newSize))return JNI_FALSE;
 oe::terrainpaint::Canvas nextTiles;
 if(!nextTiles.replaceAll(gPaintTiles.tiles(),newSize))return JNI_FALSE;
 oe::terrainpaint::MaterialCanvas nextMasks;
 if(!nextMasks.replaceAll(gMaterialCanvas.tiles(),newSize))return JNI_FALSE;
 if(gWorldSize==newSize)return JNI_TRUE;
 recordEdit();
 gWorldSize=newSize;
 gTarget.x=clampf(gTarget.x,-half+4.f,half-4.f);
 gTarget.z=clampf(gTarget.z,-half+4.f,half-4.f);
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeJumpTo(JNIEnv*,jclass,jfloat x,jfloat z){
 if(!std::isfinite(x)||!std::isfinite(z)||std::abs(x)>worldHalf()-4.f||std::abs(z)>worldHalf()-4.f)return JNI_FALSE;
 gTarget={x,terrainHeightAt(x,z)+1.2f,z};
 return JNI_TRUE;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativePick(JNIEnv*,jclass,jfloat x,jfloat y){
 const Mat4 vp=viewProjection();
 float depth=1e9f;int found=0;
 for(const auto&e:gEntities){
  const float range=e.type<3||e.type==6?3.5f:
       (oe::medieval::validModule(e.type)?5.3f:2.0f);
  const float h=e.type<3||e.type==6?6.f:
       (oe::medieval::validModule(e.type)?2.8f:4.9f);
  float left=1e9f,top=1e9f,right=-1e9f,bottom=-1e9f,minW=1e9f;
  int visible=0;
  for(float xx:{-range,range})for(float zz:{-range,range})for(float yy:{0.f,h}){
    float a=e.yaw*RAD,k=e.size,c=std::cos(a),s=std::sin(a);
    Vec4 q=apply(vp,{e.x+(c*xx*e.sx+s*zz*e.sz)*k,e.y+yy*k*e.sy,e.z+(c*zz*e.sz-s*xx*e.sx)*k,1});
    if(q.w<.1f)continue;visible++;
    float sx=(q.x/q.w*.5f+.5f)*gWidth, sy=(.5f-q.y/q.w*.5f)*gHeight;
    left=std::min(left,sx);right=std::max(right,sx);
    top=std::min(top,sy);bottom=std::max(bottom,sy);minW=std::min(minW,q.w);
  }
  if(visible>0 && x>=left && x<=right && y>=top && y<=bottom && minW<depth){
    depth=minW;found=e.id;
  }
 }
 return found;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeAdd(JNIEnv*,jclass,jint type){
 if(type<0||type>28||type==6)return 0;
 float x=gTarget.x,z=gTarget.z;
 for(int radius=0;radius<=160;radius+=12){
  for(int angle=0;angle<8;angle++){
   float a=2.f*PI*angle/8.f;
   float xx=x+std::sin(a)*radius,zz=z+std::cos(a)*radius;
   if(xx>=-worldHalf()&&xx<=worldHalf()&&zz>=-worldHalf()&&zz<=worldHalf()&&!blocked(xx,zz,type))
      return add(type,xx,zz,0,1);
  }
 }
 return 0;
}
// Add a stone plinth aligned under the selected built-in building.
// Atomic single undo entry. No terrain modification or building teleport.
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeFoundationUnderSelected(
 JNIEnv*,jclass,jint foundationKind){
 const Entity*building=selected();
 if(!oe::medieval::fitsStockBuildingFoundation(foundationKind)||
    !building||(building->type<0||building->type>2)||
    gEntities.size()>=MAX_ENTITIES||gNextId<=0)return 0;
 const Entity source=*building; // copy; push_back can invalidate pointers
 const float effectiveHeight=source.size*source.sy;
 const float foundationY=source.y-
      oe::medieval::foundationTop(foundationKind)*effectiveHeight;
 if(!std::isfinite(foundationY)||foundationY<-24.f||foundationY>160.f||
    std::abs(source.x)>worldHalf()-4.f*source.size*source.sx||
    std::abs(source.z)>worldHalf()-4.f*source.size*source.sz)return 0;
 Entity foundation{};
 foundation.id=gNextId;foundation.type=foundationKind;
 foundation.x=source.x;foundation.z=source.z;foundation.y=foundationY;
 foundation.yaw=source.yaw;foundation.size=source.size;
 foundation.sx=source.sx;foundation.sy=source.sy;foundation.sz=source.sz;
 recordEdit();
 gEntities.push_back(foundation);
 ++gNextId;gSelected=foundation.id;
 return foundation.id;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativePlaceAtScreen(JNIEnv*,jclass,jint type,jfloat px,jfloat py){
 if(type<0||type>28||type==6)return 0;
 float x,z;
 if(!groundPoint(px,py,x,z)||blocked(x,z,type))return 0;
 return add(type,x,z,0,1);
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativePlaceImportedAtScreen(JNIEnv*env,jclass,jstring key,jfloat px,jfloat py){
 if(!key)return 0;
 float x,z;
 if(!groundPoint(px,py,x,z)||blocked(x,z,6))return 0;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return 0;
 std::string asset(raw);env->ReleaseStringUTFChars(key,raw);
 if(gAssets.find(asset)==gAssets.end()||gEntities.size()>=MAX_ENTITIES)return 0;
 recordEdit();
 int id=gNextId++;gEntities.push_back({id,6,x,z,0,1,asset,terrainHeightAt(x,z)});gSelected=id;return id;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeAddPlaced(JNIEnv*,jclass,jint type,jfloat x,jfloat z,jfloat yaw,jfloat size){
 if(type<0||type>28||type==6 || !std::isfinite(x)||!std::isfinite(z)||!std::isfinite(yaw)||!std::isfinite(size) ||
 std::abs(x)>worldHalf() || std::abs(z)>worldHalf() || std::abs(yaw)>36000 || size<.2f || size>8)return 0;
 return add(type,x,z,yaw,size);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSelect(JNIEnv*,jclass,jint id){
 gGizmoActive=false;gSelected=0;for(const auto&e:gEntities)if(e.id==id){gSelected=id;break;}
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeDeleteSelected(JNIEnv*,jclass){
 auto it=std::find_if(gEntities.begin(),gEntities.end(),[](const Entity&e){return e.id==gSelected;});
 if(it==gEntities.end())return JNI_FALSE;
 recordEdit();gEntities.erase(it);gSelected=0;return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSetDemoRoads(JNIEnv*,jclass,jboolean enabled){
 if(gDemoRoads!=(enabled==JNI_TRUE))recordEdit();
 gDemoRoads=(enabled==JNI_TRUE);
}
// Exact-position inspector. Positions are un-snapped world coordinates;
// quantization is limited to float32 storage / GPU precision, not a grid.
JNIEXPORT jdoubleArray JNICALL Java_game_oe_editor_EditorActivity_nativeSelectedCoordinates(
 JNIEnv*env,jclass){
 Entity*e=selected();
 if(!e)return nullptr;
 const jdouble values[3]={static_cast<jdouble>(e->x),
                          static_cast<jdouble>(e->y),
                          static_cast<jdouble>(e->z)};
 jdoubleArray coords=env->NewDoubleArray(3);
 if(coords)env->SetDoubleArrayRegion(coords,0,3,values);
 return coords;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeSetSelectedCoordinates(
 JNIEnv*,jclass,jdouble x,jdouble y,jdouble z){
 if(!std::isfinite(x)||!std::isfinite(y)||!std::isfinite(z)||
    std::abs(x)>static_cast<double>(worldHalf())||
    std::abs(z)>static_cast<double>(worldHalf())||y< -24.0||y>160.0)
   return JNI_FALSE;
 Entity*e=selected();if(!e)return JNI_FALSE;
 const float nx=static_cast<float>(x),ny=static_cast<float>(y),
             nz=static_cast<float>(z);
 if(nx!=e->x||ny!=e->y||nz!=e->z){
  recordEdit();e->x=nx;e->y=ny;e->z=nz;
 }
 return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeNudge(JNIEnv*,jclass,jfloat x,jfloat z){
 if(!std::isfinite(x)||!std::isfinite(z))return;
 if(Entity*e=selected()){
  const float nx=clampf(e->x+x,-worldHalf(),worldHalf());
  const float nz=clampf(e->z+z,-worldHalf(),worldHalf());
  if(nx==e->x&&nz==e->z)return;
  recordEdit();e->x=nx;e->z=nz;
 }
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeChangeSelectedHeight(JNIEnv*,jclass,jfloat dy){
 if(!std::isfinite(dy)||dy==0)return;
 if(Entity*e=selected()){
  const float y=clampf(e->y+dy,-24.f,160.f);
  if(y==e->y)return;
  recordEdit();e->y=y;
 }
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSnapSelectedToGround(JNIEnv*,jclass){
 if(Entity*e=selected()){
  const float y=terrainHeightAt(e->x,e->z);
  if(y!=e->y){recordEdit();e->y=y;}
 }
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeApplyTerrainBrushAtScreen(
 JNIEnv*,jclass,jfloat px,jfloat py,jfloat radius,jfloat amount){
 float x,z;
 if(!groundPoint(px,py,x,z)||!std::isfinite(radius)||!std::isfinite(amount)||
    radius<16.f||radius>256.f||std::abs(amount)>60.f||std::abs(amount)<.001f||
    gBrushes.size()>=MAX_TERRAIN_BRUSHES)return 0;
 recordEdit();gBrushes.push_back({x,z,radius,amount});gTerrainRevision++;
 gTarget.y=terrainHeightAt(gTarget.x,gTarget.z)+1.2f;
 return static_cast<jint>(gBrushes.size());
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeTerrainBrushCount(JNIEnv*,jclass){
 return static_cast<jint>(gBrushes.size());
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeRotate(JNIEnv*,jclass,jfloat deg){
 if(!std::isfinite(deg)||deg==0)return;
 if(Entity*e=selected()){recordEdit();e->yaw=std::fmod(e->yaw+deg,360.f);}
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeScaleSelectedAxis(
 JNIEnv*,jclass,jint axis,jfloat factor){
 if(axis<0||axis>2||!std::isfinite(factor)||factor<=0)return;
 if(Entity*e=selected()){
  float *target=axis==0?&e->sx:(axis==1?&e->sy:&e->sz);
  const float value=clampf(*target*factor,.10f,8.f);
  if(value==*target)return;
  recordEdit();*target=value;
 }
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeScale(JNIEnv*,jclass,jfloat factor){
 if(!std::isfinite(factor)||factor<=0)return;
 if(Entity*e=selected()){
  const float next=clampf(e->size*factor,.2f,8);
  if(next==e->size)return;
  recordEdit();e->size=next;
 }
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeSelectedInfo(JNIEnv*env,jclass){
 Entity*e=selected();if(!e)return env->NewStringUTF("Выбери здание касанием или через список «Объекты»");
 std::ostringstream out;out<<nameFor(e->type)<<" #" <<e->id<<"  |  X "<<std::fixed<<std::setprecision(3)
 <<e->x<<"  Z "<<e->z<<"  Y "<<e->y<<"  ↻ "<<e->yaw<<"°  Размер "<<e->size
 << " XYZ "<<e->sx<<"/"<<e->sy<<"/"<<e->sz;
 return env->NewStringUTF(out.str().c_str());
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeSceneSnapshot(JNIEnv*env,jclass){
 std::ostringstream out;out<<std::fixed<<std::setprecision(6)<<"{\"version\":2,\"worldSize\":"<<gWorldSize<<",\"demoRoads\":"<<(gDemoRoads?"true":"false")<<",\"items\":[";
 for(size_t i=0;i<gEntities.size();i++){
  const auto&e=gEntities[i];if(i)out<<",";
  out<<"{\"type\":"<<e.type<<",\"x\":"<<e.x<<",\"z\":"<<e.z
     <<",\"yaw\":"<<e.yaw<<",\"size\":"<<e.size<<",\"y\":"<<e.y<<",\"sx\":"<<e.sx<<",\"sy\":"<<e.sy<<",\"sz\":"<<e.sz;
  if(e.type==6)out<<",\"asset\":\""<<e.asset<<"\"";
  if(!e.paint.empty())out<<",\"paint\":\""<<e.paint<<"\"";
  out<<"}";
 }
 out<<"],\"terrain\":[";
 for(size_t i=0;i<gBrushes.size();i++){
  const auto&b=gBrushes[i];if(i)out<<",";
  out<<"["<<b.x<<","<<b.z<<","<<b.radius<<","<<b.strength<<"]";
 }
 out<<"],\"terrainPaint\":[";
 for(size_t i=0;i<gTerrainPaint.size();i++){
  const auto&paint=gTerrainPaint[i];if(i)out<<",";
  out<<"["<<paint.x<<","<<paint.z<<","<<paint.radius<<",\""<<paint.texture<<"\"]";
 }
 out<<"],\"roads\":[";
 for(size_t i=0;i<gRoads.all().size();i++){
  const auto& road=gRoads.all()[i];
  if(i)out<<",";
  out<<"{\"id\":\""<<road.id<<"\",\"name\":\""<<road.name
     <<"\",\"material\":\""<<road.material
     <<"\",\"widthMetres\":"<<road.widthMetres<<",\"controlPoints\":[";
  for(size_t j=0;j<road.controlPoints.size();j++){
   if(j)out<<",";
   out<<"["<<road.controlPoints[j].x<<","<<road.controlPoints[j].z<<"]";
  }
  out<<"]}";
 }
 out<<"],\"lakes\":[";
 for(std::size_t i=0;i<gLakes.size();++i){
  if(i)out<<",";
  const auto&lake=gLakes[i];
  out<<"["<<lake.x<<","<<lake.z<<","<<lake.radius<<","<<lake.level<<"]";
 }
 out<<"]}";return env->NewStringUTF(out.str().c_str());
}
JNIEXPORT jobjectArray JNICALL Java_game_oe_editor_EditorActivity_nativeLabels(JNIEnv*env,jclass){
 jclass stringClass=env->FindClass("java/lang/String");
 jobjectArray result=env->NewObjectArray((jsize)gEntities.size(),stringClass,nullptr);
 for(jsize i=0;i<(jsize)gEntities.size();i++){
  const auto&e=gEntities[i];std::string label=std::string(nameFor(e.type))+" #"+std::to_string(e.id);
  jstring item=env->NewStringUTF(label.c_str());env->SetObjectArrayElement(result,i,item);env->DeleteLocalRef(item);
 }
 return result;
}
JNIEXPORT jintArray JNICALL Java_game_oe_editor_EditorActivity_nativeIds(JNIEnv*env,jclass){
 jintArray arr=env->NewIntArray((jsize)gEntities.size());std::vector<jint> ids;
 ids.reserve(gEntities.size());for(const auto&e:gEntities)ids.push_back(e.id);
 if(!ids.empty())env->SetIntArrayRegion(arr,0,(jsize)ids.size(),ids.data());
 return arr;
}
// All JNI resource operations run on GLSurfaceView's GL thread.
JNIEXPORT jobjectArray JNICALL Java_game_oe_editor_EditorActivity_nativeLoadedAssetKeys(JNIEnv*env,jclass){
 jclass klass=env->FindClass("java/lang/String");
 int loaded=0;
 for(const auto&kv:gAssets)if(kv.second.gpu.count>0)loaded++;
 jobjectArray keys=env->NewObjectArray(loaded,klass,nullptr);
 int i=0;
 for(const auto&kv:gAssets)if(kv.second.gpu.count>0){
  jstring key=env->NewStringUTF(kv.first.c_str());
  env->SetObjectArrayElement(keys,i++,key);
  env->DeleteLocalRef(key);
 }
 return keys;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeCanRegisterMore(JNIEnv*,jclass,jint count){
 return count>=0 && static_cast<size_t>(count)<=MAX_ASSETS-gAssets.size()?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeDiscardUnusedAsset(JNIEnv*env,jclass,jstring key){
 if(!key)return;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return;
 std::string asset(raw);env->ReleaseStringUTFChars(key,raw);
 for(const auto&e:gEntities)if(e.type==6&&e.asset==asset)return;
 auto it=gAssets.find(asset);
 if(it==gAssets.end())return;
 releaseAssetGpu(it->second);
 gAssets.erase(it);
}
// Register a full GLB atomically: interleaved 11-float vertices, material
// draw ranges, and up to 4 embedded RGBA8/ETC2 GPU textures.
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRegisterAsset(
 JNIEnv*env,jclass,jstring key,jfloatArray vertices,jfloat centerY,jfloat radius,
 jintArray batchData,jobjectArray imageData,jintArray imageInfo,
 jboolean preserveOriginalRig){
 if(!key||!vertices||!batchData||!imageData||!imageInfo||
    !std::isfinite(centerY)||!std::isfinite(radius)||
    radius<.05f||radius>100.f||centerY<0.f||centerY>100.f)return JNI_FALSE;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return JNI_FALSE;
 std::string assetKey(raw);env->ReleaseStringUTFChars(key,raw);
 if(assetKey.size()!=31||assetKey.compare(0,3,"oe_")!=0||
    assetKey.substr(assetKey.size()-4)!=".glb")return JNI_FALSE;
 for(size_t i=3;i<assetKey.size()-4;i++)
  if(!((assetKey[i]>='0'&&assetKey[i]<='9')||
       (assetKey[i]>='a'&&assetKey[i]<='f')))return JNI_FALSE;
 auto existing=gAssets.find(assetKey);
 // Content-addressed resources are immutable within the renderer.
 if(existing!=gAssets.end()&&existing->second.gpu.count>0)return JNI_TRUE;
 if(existing==gAssets.end()&&gAssets.size()>=MAX_ASSETS)return JNI_FALSE;
 const jsize valueCount=env->GetArrayLength(vertices);
 if(valueCount<=0||valueCount>MAX_IMPORTED_TRIANGLES*33||valueCount%33!=0)
  return JNI_FALSE;
 const jsize vertexCount=valueCount/11;
 const jsize batchLength=env->GetArrayLength(batchData);
 if(batchLength<=0||batchLength>6144||batchLength%3!=0)return JNI_FALSE;
 const jsize imageCount=env->GetArrayLength(imageData);
 if(imageCount<0||imageCount>4||env->GetArrayLength(imageInfo)!=imageCount*3)
  return JNI_FALSE;
 Asset prepared;
 prepared.vertexStride=11;prepared.centerY=centerY;prepared.radius=radius;
 prepared.vertices.resize(static_cast<size_t>(valueCount));
 env->GetFloatArrayRegion(vertices,0,valueCount,prepared.vertices.data());
 if(env->ExceptionCheck())return JNI_FALSE;
 for(float value:prepared.vertices)
  if(!std::isfinite(value)||std::abs(value)>10000.f)return JNI_FALSE;
 std::vector<jint> batches(static_cast<size_t>(batchLength));
 env->GetIntArrayRegion(batchData,0,batchLength,batches.data());
 if(env->ExceptionCheck())return JNI_FALSE;
 jint consumed=0;
 for(jsize i=0;i<batchLength;i+=3){
  const jint first=batches[i],count=batches[i+1],texture=batches[i+2];
  if(first!=consumed||count<=0||count%3!=0||
     count>vertexCount-consumed||texture< -1||texture>=imageCount)return JNI_FALSE;
  prepared.batches.push_back({first,count,texture});
  consumed+=count;
 }
 if(consumed!=vertexCount)return JNI_FALSE;
 std::vector<jint> info(static_cast<size_t>(imageCount*3));
 if(imageCount){
  env->GetIntArrayRegion(imageInfo,0,imageCount*3,info.data());
  if(env->ExceptionCheck())return JNI_FALSE;
 }
 size_t totalNew=0;
 for(jsize i=0;i<imageCount;i++){
  const jint width=info[3*i],height=info[3*i+1],format=info[3*i+2];
  if(width<=0||height<=0||width>2048||height>2048||
     format<0||format>2)return JNI_FALSE;
  const size_t expected=format==0?static_cast<size_t>(width)*height*4:
    static_cast<size_t>((width+3)/4)*static_cast<size_t>((height+3)/4)*
     static_cast<size_t>(format==1?8:16);
  jbyteArray bytes=static_cast<jbyteArray>(env->GetObjectArrayElement(imageData,i));
  if(!bytes||env->ExceptionCheck())return JNI_FALSE;
  const jsize length=env->GetArrayLength(bytes);
  if(length<0||static_cast<size_t>(length)!=expected){
   env->DeleteLocalRef(bytes);return JNI_FALSE;
  }
  TextureResource tex;
  tex.width=width;tex.height=height;tex.format=format;
  tex.bytes.resize(expected);
  env->GetByteArrayRegion(bytes,0,length,reinterpret_cast<jbyte*>(tex.bytes.data()));
  env->DeleteLocalRef(bytes);
  if(env->ExceptionCheck())return JNI_FALSE;
  totalNew+=expected;
  if(totalNew>32u*1024u*1024u)return JNI_FALSE;
  prepared.textures.push_back(std::move(tex));
 }
 const size_t oldBytes=existing==gAssets.end()?0:[&](){
  size_t n=0;
  for(const auto&tex:existing->second.textures)n+=tex.bytes.size();
  return n;
 }();
 if(textureCacheBytes()-oldBytes+totalNew>MAX_TEXTURE_CACHE_BYTES)return JNI_FALSE;
 // Only entirely static GLBs are eligible for automatic LOD generation.
 // Unsafe files with skins, animations or morph targets retain full geometry.
 if(preserveOriginalRig!=JNI_TRUE){
  buildStaticLods(prepared);
  size_t newBytes=0;
  for(const auto&lod:prepared.lods)newBytes+=lod.vertices.size()*sizeof(float);
  if(lodCacheBytes()+newBytes>MAX_LOD_CACHE_BYTES)
   for(auto&lod:prepared.lods){lod.vertices.clear();lod.batches.clear();}
 }
 // Stage GPU state without touching the old scene/asset on failures.
 for(int i=0;i<8&&glGetError()!=GL_NO_ERROR;i++){}
 uploadAsset(prepared);
 for(auto&lod:prepared.lods)if(!lod.vertices.empty()){
  uploadLod(lod);
  if(glGetError()!=GL_NO_ERROR||!lod.gpu.vao||!lod.gpu.buffer){
   if(lod.gpu.vao)glDeleteVertexArrays(1,&lod.gpu.vao);
   if(lod.gpu.buffer)glDeleteBuffers(1,&lod.gpu.buffer);
   lod.gpu={};lod.vertices.clear();lod.batches.clear();
   // Keep fully detailed LOD0 asset and continue without optional LOD.
  }
 }
 bool texturesOk=prepared.gpu.count>0&&prepared.gpu.vao&&prepared.gpu.buffer;
 if(texturesOk)texturesOk=uploadTextures(prepared);
 const GLenum error=glGetError();
 if(!texturesOk||error!=GL_NO_ERROR){
  releaseAssetGpu(prepared);
  __android_log_print(ANDROID_LOG_ERROR,"OE",
   "GLB asset or texture GPU upload failed: 0x%x",error);
  return JNI_FALSE;
 }
 if(existing!=gAssets.end()){
  releaseAssetGpu(existing->second);
  existing->second=std::move(prepared);
 }else gAssets.emplace(assetKey,std::move(prepared));
 return JNI_TRUE;
}
// Replace a scene only if EVERY model and entity is valid. No scene mutation
// occurs until all checks finish: failed imports cannot erase the open world.
// Validate everything before atomic swap on the GLES thread.
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeReplaceScene(
 JNIEnv*env,jclass,jint size,jboolean demo,jintArray kinds,jfloatArray transforms,
 jobjectArray modelKeys,jfloatArray terrainData,jobjectArray objectPaintKeys,
 jfloatArray painting,jobjectArray terrainPaintKeys,jlongArray roadIds,
 jobjectArray roadNames,jobjectArray roadMaterials,jdoubleArray roadWidths,
 jobjectArray roadPoints,jintArray tileCoords,jobjectArray tileData,
 jintArray layerCoords,jobjectArray layerKeys,jobjectArray layerMasks,
 jfloatArray lakeData){
 if(!allowedWorldSize(size)||!kinds||!transforms||!modelKeys||!terrainData||
    !objectPaintKeys||!painting||!terrainPaintKeys||!roadIds||!roadNames||
    !roadMaterials||!roadWidths||!roadPoints||!tileCoords||!tileData||!layerCoords||!layerKeys||!layerMasks||!lakeData)
    return JNI_FALSE;
 const jsize n=env->GetArrayLength(kinds);
 if(n<0||n>MAX_ENTITIES||env->GetArrayLength(transforms)!=n*8||
    env->GetArrayLength(modelKeys)!=n||env->GetArrayLength(objectPaintKeys)!=n)return JNI_FALSE;
 const jsize paintFloats=env->GetArrayLength(painting);
 if(paintFloats<0||paintFloats%3!=0||
    paintFloats>static_cast<jsize>(MAX_TERRAIN_PAINT*3)||
    env->GetArrayLength(terrainPaintKeys)!=paintFloats/3)return JNI_FALSE;
 const jsize brushFloats=env->GetArrayLength(terrainData);
 if(brushFloats<0||brushFloats%4!=0||
    brushFloats>static_cast<jsize>(MAX_TERRAIN_BRUSHES*4))return JNI_FALSE;
 std::array<jint,MAX_ENTITIES> types{};
 std::array<jfloat,MAX_ENTITIES*8> matrix{};
 if(n){
  env->GetIntArrayRegion(kinds,0,n,types.data());
  env->GetFloatArrayRegion(transforms,0,n*8,matrix.data());
  if(env->ExceptionCheck())return JNI_FALSE;
 }
 std::array<jfloat,MAX_TERRAIN_BRUSHES*4> brushRaw{};
 if(brushFloats){
  env->GetFloatArrayRegion(terrainData,0,brushFloats,brushRaw.data());
  if(env->ExceptionCheck())return JNI_FALSE;
 }
 const float half=static_cast<float>(size)*.5f;
 // Preflight lake bodies before ANY scene state is replaced. Older scene
 // versions pass an empty array, which clears previously authored lakes.
 const jsize lakeFloats=env->GetArrayLength(lakeData);
 if(lakeFloats<0||lakeFloats%4!=0||
    lakeFloats>static_cast<jsize>(oe::water::kMaxLakes*4))return JNI_FALSE;
 std::array<jfloat,oe::water::kMaxLakes*4> lakeRaw{};
 if(lakeFloats){
  env->GetFloatArrayRegion(lakeData,0,lakeFloats,lakeRaw.data());
  if(env->ExceptionCheck())return JNI_FALSE;
 }
 std::vector<oe::water::Lake> stagedLakes;
 stagedLakes.reserve(static_cast<std::size_t>(lakeFloats/4));
 for(jsize i=0;i<lakeFloats;i+=4){
  const oe::water::Lake lake{lakeRaw[i],lakeRaw[i+1],
                              lakeRaw[i+2],lakeRaw[i+3]};
  if(!oe::water::validLake(lake,half))return JNI_FALSE;
  stagedLakes.push_back(lake);
 }
 std::vector<TerrainBrush> stagedBrushes;
 stagedBrushes.reserve(static_cast<size_t>(brushFloats/4));
 for(jsize i=0;i<brushFloats;i+=4){
  const float x=brushRaw[i],z=brushRaw[i+1],radius=brushRaw[i+2],strength=brushRaw[i+3];
  if(!std::isfinite(x)||!std::isfinite(z)||!std::isfinite(radius)||!std::isfinite(strength)||
     std::abs(x)>half||std::abs(z)>half||radius<16.f||radius>256.f||
     std::abs(strength)<.001f||std::abs(strength)>60.f)return JNI_FALSE;
  stagedBrushes.push_back({x,z,radius,strength});
 }
 std::vector<TerrainPaint> stagedPaint;
 stagedPaint.reserve(static_cast<size_t>(paintFloats/3));
 std::array<jfloat,MAX_TERRAIN_PAINT*3> paintData{};
 if(paintFloats)env->GetFloatArrayRegion(painting,0,paintFloats,paintData.data());
 if(env->ExceptionCheck())return JNI_FALSE;
 for(jsize i=0;i<paintFloats/3;i++){
  const float x=paintData[i*3],z=paintData[i*3+1],radius=paintData[i*3+2];
  if(!std::isfinite(x)||!std::isfinite(z)||!std::isfinite(radius)||
     std::abs(x)>half||std::abs(z)>half||radius<4.f||radius>64.f)return JNI_FALSE;
  jstring rawPaint=static_cast<jstring>(env->GetObjectArrayElement(terrainPaintKeys,i));
  if(!rawPaint||env->ExceptionCheck())return JNI_FALSE;
  const char*paintChars=env->GetStringUTFChars(rawPaint,nullptr);
  if(!paintChars){env->DeleteLocalRef(rawPaint);return JNI_FALSE;}
  const std::string key(paintChars);
  env->ReleaseStringUTFChars(rawPaint,paintChars);env->DeleteLocalRef(rawPaint);
  if(!validPaintKey(key)||gPaintTextures.find(key)==gPaintTextures.end())return JNI_FALSE;
  stagedPaint.push_back({x,z,radius,key});
 }
 std::vector<Entity> staged;
 staged.reserve(static_cast<size_t>(n));
 for(jsize i=0;i<n;i++){
  const int type=types[i];
  const float x=matrix[i*8],z=matrix[i*8+1],yaw=matrix[i*8+2],
              scl=matrix[i*8+3],y=matrix[i*8+4],
              sx=matrix[i*8+5],sy=matrix[i*8+6],sz=matrix[i*8+7];
  if(type<0||type>28||!std::isfinite(x)||!std::isfinite(z)||
    !std::isfinite(yaw)||!std::isfinite(scl)||!std::isfinite(y)||
    !std::isfinite(sx)||!std::isfinite(sy)||!std::isfinite(sz)||
    std::abs(x)>half||std::abs(z)>half||std::abs(yaw)>36000||
    scl<.2f||scl>8.f||y<-24.f||y>160.f||
    sx<.10f||sx>8.f||sy<.10f||sy>8.f||sz<.10f||sz>8.f)return JNI_FALSE;
  std::string key;
  if(type==6){
   jstring rawKey=static_cast<jstring>(env->GetObjectArrayElement(modelKeys,i));
   if(!rawKey||env->ExceptionCheck())return JNI_FALSE;
   const char*raw=env->GetStringUTFChars(rawKey,nullptr);
   if(!raw){env->DeleteLocalRef(rawKey);return JNI_FALSE;}
   key=raw;
   env->ReleaseStringUTFChars(rawKey,raw);
   env->DeleteLocalRef(rawKey);
   auto asset=gAssets.find(key);
   if(asset==gAssets.end()||asset->second.gpu.count<=0)return JNI_FALSE;
  }
  jstring paintKey=static_cast<jstring>(env->GetObjectArrayElement(objectPaintKeys,i));
  if(!paintKey||env->ExceptionCheck())return JNI_FALSE;
  const char*paintRaw=env->GetStringUTFChars(paintKey,nullptr);
  if(!paintRaw){env->DeleteLocalRef(paintKey);return JNI_FALSE;}
  const std::string paintName(paintRaw);
  env->ReleaseStringUTFChars(paintKey,paintRaw);env->DeleteLocalRef(paintKey);
  if(!paintName.empty()&&(!validPaintKey(paintName)||
     gPaintTextures.find(paintName)==gPaintTextures.end()))return JNI_FALSE;
  staged.push_back({static_cast<int>(i)+1,type,x,z,yaw,scl,key,y,sx,sy,sz,paintName});
 }

 // Validate the durable road records before touching the currently open scene.
 const jsize roadsCount=env->GetArrayLength(roadIds);
 if(roadsCount<0||roadsCount>static_cast<jsize>(oe::roads::kMaxRoads)||
    env->GetArrayLength(roadNames)!=roadsCount||
    env->GetArrayLength(roadMaterials)!=roadsCount||
    env->GetArrayLength(roadWidths)!=roadsCount||
    env->GetArrayLength(roadPoints)!=roadsCount)return JNI_FALSE;
 std::vector<jlong> ids(static_cast<size_t>(roadsCount));
 std::vector<jdouble> widths(static_cast<size_t>(roadsCount));
 if(roadsCount){
  env->GetLongArrayRegion(roadIds,0,roadsCount,ids.data());
  env->GetDoubleArrayRegion(roadWidths,0,roadsCount,widths.data());
  if(env->ExceptionCheck())return JNI_FALSE;
 }
 std::vector<oe::roads::Road> stagedRoads;
 stagedRoads.reserve(static_cast<size_t>(roadsCount));
 size_t totalPoints=0;
 for(jsize i=0;i<roadsCount;i++){
  if(ids[static_cast<size_t>(i)]<=0)return JNI_FALSE;
  jstring jname=static_cast<jstring>(env->GetObjectArrayElement(roadNames,i));
  jstring jmaterial=static_cast<jstring>(env->GetObjectArrayElement(roadMaterials,i));
  jdoubleArray jpath=static_cast<jdoubleArray>(env->GetObjectArrayElement(roadPoints,i));
  if(env->ExceptionCheck()||!jname||!jmaterial||!jpath)return JNI_FALSE;
  const char*name=env->GetStringUTFChars(jname,nullptr);
  if(!name)return JNI_FALSE;
  std::string roadName(name);env->ReleaseStringUTFChars(jname,name);
  const char*material=env->GetStringUTFChars(jmaterial,nullptr);
  if(!material)return JNI_FALSE;
  std::string roadMaterial(material);env->ReleaseStringUTFChars(jmaterial,material);
  const jsize coords=env->GetArrayLength(jpath);
  if(coords<4||coords%2!=0||coords>static_cast<jsize>(oe::roads::kMaxControlPoints*2))
   return JNI_FALSE;
  totalPoints+=static_cast<size_t>(coords)/2;
  if(totalPoints>4096)return JNI_FALSE;
  std::vector<jdouble> pairs(static_cast<size_t>(coords));
  env->GetDoubleArrayRegion(jpath,0,coords,pairs.data());
  env->DeleteLocalRef(jname);env->DeleteLocalRef(jmaterial);env->DeleteLocalRef(jpath);
  if(env->ExceptionCheck())return JNI_FALSE;
  if(roadName.empty()||roadName.size()>64||
     !std::all_of(roadName.begin(),roadName.end(),[](unsigned char c){
       return (c>='a'&&c<='z')||(c>='A'&&c<='Z')||
              (c>='0'&&c<='9')||c==' '||c=='-'||c=='_';
     }))return JNI_FALSE;
  if(roadMaterial!="builtin:cobblestone"&&roadMaterial!="builtin:dirt"&&
     roadMaterial!="builtin:sand"&&roadMaterial!="builtin:grass")return JNI_FALSE;
  oe::roads::Road road;
  road.id=static_cast<std::uint64_t>(ids[static_cast<size_t>(i)]);
  road.name=std::move(roadName);
  road.material=std::move(roadMaterial);
  road.widthMetres=widths[static_cast<size_t>(i)];
  for(jsize j=0;j<coords;j+=2)
   road.controlPoints.push_back({pairs[static_cast<size_t>(j)],
                                 pairs[static_cast<size_t>(j+1)]});
  stagedRoads.push_back(std::move(road));
 }
 oe::roads::Network nextRoads;
 if(!nextRoads.replaceAll(stagedRoads)||!roadBudget(stagedRoads,size))return JNI_FALSE;
 // Construct paint layers before committing objects or terrain.
 const jsize tilePairs=env->GetArrayLength(tileCoords);
 if(tilePairs<0||tilePairs%2!=0||
    tilePairs>static_cast<jsize>(oe::terrainpaint::kMaxTiles*2)||
    env->GetArrayLength(tileData)!=tilePairs/2)return JNI_FALSE;
 std::vector<jint> tileLocations(static_cast<size_t>(tilePairs));
 if(tilePairs)env->GetIntArrayRegion(tileCoords,0,tilePairs,tileLocations.data());
 if(env->ExceptionCheck())return JNI_FALSE;
 std::vector<oe::terrainpaint::Tile> restoredTiles;
 restoredTiles.reserve(static_cast<size_t>(tilePairs/2));
 for(jsize i=0;i<tilePairs/2;i++){
  jbyteArray pixels=static_cast<jbyteArray>(env->GetObjectArrayElement(tileData,i));
  if(!pixels||env->ExceptionCheck())return JNI_FALSE;
  const jsize sizeBytes=env->GetArrayLength(pixels);
  if(sizeBytes!=oe::terrainpaint::kBytesPerTile){env->DeleteLocalRef(pixels);return JNI_FALSE;}
  oe::terrainpaint::Tile tile;
  tile.x=tileLocations[static_cast<size_t>(i)*2];
  tile.z=tileLocations[static_cast<size_t>(i)*2+1];
  tile.pixels.resize(oe::terrainpaint::kBytesPerTile);
  env->GetByteArrayRegion(pixels,0,sizeBytes,reinterpret_cast<jbyte*>(tile.pixels.data()));
  env->DeleteLocalRef(pixels);
  if(env->ExceptionCheck())return JNI_FALSE;
  restoredTiles.push_back(std::move(tile));
 }
 oe::terrainpaint::Canvas nextPaintTiles;
 if(!nextPaintTiles.replaceAll(std::move(restoredTiles),size))return JNI_FALSE;
 // Validate independent material masks, and verify every source texture is loaded.
 const jsize layerPairs=env->GetArrayLength(layerCoords);
 if(layerPairs<0||layerPairs%2!=0||
    layerPairs>static_cast<jsize>(oe::terrainpaint::kMaxMaterialTiles*
                                 oe::terrainpaint::kMaxMaterialsPerTile*2)||
    env->GetArrayLength(layerKeys)!=layerPairs/2||
    env->GetArrayLength(layerMasks)!=layerPairs/2)return JNI_FALSE;
 std::vector<jint> materialLocations(static_cast<size_t>(layerPairs));
 if(layerPairs)env->GetIntArrayRegion(layerCoords,0,layerPairs,materialLocations.data());
 if(env->ExceptionCheck())return JNI_FALSE;
 std::map<std::pair<int,int>,oe::terrainpaint::MaterialTile> stagedLayerTiles;
 for(jsize i=0;i<layerPairs/2;++i){
  jstring jkey=static_cast<jstring>(env->GetObjectArrayElement(layerKeys,i));
  jbyteArray jmask=static_cast<jbyteArray>(env->GetObjectArrayElement(layerMasks,i));
  if(env->ExceptionCheck()||!jkey||!jmask)return JNI_FALSE;
  const char*text=env->GetStringUTFChars(jkey,nullptr);
  if(!text)return JNI_FALSE;
  const std::string name(text);
  env->ReleaseStringUTFChars(jkey,text);
  if(!oe::terrainpaint::validMaterialKey(name)||
     paintTexture(name)==nullptr||
     env->GetArrayLength(jmask)!=oe::terrainpaint::kMaterialMaskBytes)return JNI_FALSE;
  oe::terrainpaint::MaterialMask layer;
  layer.material=name;
  layer.weights.resize(oe::terrainpaint::kMaterialMaskBytes);
  env->GetByteArrayRegion(jmask,0,static_cast<jsize>(layer.weights.size()),
     reinterpret_cast<jbyte*>(layer.weights.data()));
  env->DeleteLocalRef(jmask);env->DeleteLocalRef(jkey);
  if(env->ExceptionCheck())return JNI_FALSE;
  const auto coords=std::make_pair(materialLocations[static_cast<size_t>(2*i)],
                                   materialLocations[static_cast<size_t>(2*i+1)]);
  auto&tile=stagedLayerTiles[coords];
  tile.x=coords.first;tile.z=coords.second;
  tile.materials.push_back(std::move(layer));
 }
 std::vector<oe::terrainpaint::MaterialTile> loadedLayers;
 loadedLayers.reserve(stagedLayerTiles.size());
 for(auto& entry:stagedLayerTiles)loadedLayers.push_back(std::move(entry.second));
 oe::terrainpaint::MaterialCanvas nextLayerCanvas;
 if(!nextLayerCanvas.replaceAll(std::move(loadedLayers),size))return JNI_FALSE;
 // Commit: no validation or allocation failures below this point.
 gEntities.swap(staged);gBrushes.swap(stagedBrushes);
 gLakes.swap(stagedLakes);gSelectedLake=-1;++gLakeRevision;
 gTerrainPaint.swap(stagedPaint);gTerrainRevision++;
 gRoads=std::move(nextRoads);gSelectedRoad=0;gActiveRoad=0;gRoadPending=false;
 for(auto& gpu:gPaintTileGpu)if(gpu.second.texture)glDeleteTextures(1,&gpu.second.texture);
 gPaintTileGpu.clear();
 gPaintTiles=std::move(nextPaintTiles);
 clearMaterialGpu();gMaterialCanvas=std::move(nextLayerCanvas);
 gMaterialStrokeActive=false;
 gPrecisionPaintActive=false;gPaintCursorVisible=false;
 ++gRoadRevision;
 gWorldSize=size;gDemoRoads=demo==JNI_TRUE;
 gNextId=static_cast<int>(n)+1;gSelected=0;
 gTarget={0,terrainHeightAt(0,1.3f)+1.2f,1.3f};gDistance=28.f;
 gSceneInitialized=true;gUndo.clear();gRedo.clear();
 return JNI_TRUE;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeAddImportedPlaced(JNIEnv*env,jclass,jstring key,jfloat x,jfloat z,jfloat yaw,jfloat size){
 if(!key)return 0;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return 0;
 std::string asset(raw);env->ReleaseStringUTFChars(key,raw);
 if(gAssets.find(asset)==gAssets.end()||gEntities.size()>=MAX_ENTITIES||
 !std::isfinite(x)||!std::isfinite(z)||!std::isfinite(yaw)||!std::isfinite(size)||
 std::abs(x)>worldHalf()||std::abs(z)>worldHalf()||std::abs(yaw)>36000||size<.2f||size>8)return 0;
 recordEdit();
 int id=gNextId++;
 gEntities.push_back({id,6,x,z,yaw,size,asset,terrainHeightAt(x,z)});
 gSelected=id;return id;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeUndo(JNIEnv*,jclass){
 return undoEdit()?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRedo(JNIEnv*,jclass){
 return redoEdit()?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeClearHistory(JNIEnv*,jclass){
 gUndo.clear();gRedo.clear();
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeDuplicateSelected(JNIEnv*,jclass){
 Entity*original=selected();
 if(!original||gEntities.size()>=MAX_ENTITIES)return 0;
 Entity copy=*original;
 const float offset=(copy.type<=2||copy.type==6?7.5f:2.5f)*copy.size;
 float nx=copy.x+offset;
 if(nx>worldHalf()-2.f)nx=copy.x-offset;
 if(nx<-worldHalf()+2.f)return 0;
 copy.x=nx;
 recordEdit();
 copy.id=gNextId++;
 gEntities.push_back(copy);gSelected=copy.id;
 return copy.id;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeReset(JNIEnv*,jclass,jboolean withDemo){reset(withDemo==JNI_TRUE);}
} // extern C
