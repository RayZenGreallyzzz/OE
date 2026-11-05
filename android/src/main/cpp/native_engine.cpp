// OE Native 0.2. GLES 3.0/C++ scene and renderer; no Godot dependency.
#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <string>
#include <vector>
#include <unordered_map>
#include <cstdint>

namespace {
constexpr float PI = 3.14159265358979323846f;
constexpr float RAD = PI / 180.0f;
constexpr int MAX_ENTITIES = 300;
struct Vec3 {float x, y, z;};
struct Vec4 {float x, y, z, w;};
struct Mat4 {float m[16]{};};
struct Mesh {GLuint vao=0, buffer=0; GLsizei count=0;};
struct Entity {int id=0, type=0; float x=0, z=0, yaw=0, size=1; std::string asset; float y=0.f;};
struct Color {float r,g,b;};
struct Shader {
 GLuint program=0;
 GLint locMvp=-1, locModel=-1, locColor=-1, locLight=-1;
};
struct Asset {
 Mesh gpu;
 std::vector<float> vertices; // Retained for GPU context recreation.
 float centerY=0.f, radius=0.f; // Conservative normalized object-space sphere.
};
static Mesh gMeshes[3];
static Mesh gStaticMeshes[10]; // Every decorative object is a single combined draw.
void bakeStaticMesh(int type);
// Cached, combined flat terrain: one GLES draw call per frame instead of one per tile.
struct TerrainGpu {
 GLuint vao=0, buffer=0;
 GLsizei vertices=0;
 int gridX=2147483647,gridZ=2147483647,worldSize=0,tiles=0,revision=-1;
};
static TerrainGpu gTerrain;
static int gDrawCalls=0,gTriangles=0;
static std::unordered_map<std::string,Asset> gAssets;
static constexpr int MAX_ASSETS=12;
static constexpr int MAX_IMPORTED_TRIANGLES=90000;
static Shader gShader;
static std::vector<Entity> gEntities;
static int gNextId=1, gSelected=0, gWidth=1, gHeight=1;
static float gYaw=.55f, gPitch=.45f, gDistance=28.f;
static Vec3 gTarget{0,1.2f,1.3f};
static bool gSceneInitialized=false, gDemoRoads=false;
constexpr float WORLD_TILE=64.0f;
static int gWorldSize=1024; // Set in app to 1024/2048/4096/8192/16384 meters.
struct TerrainBrush {float x,z,radius,strength;};
static std::vector<TerrainBrush> gBrushes;
static int gTerrainRevision=0;
static constexpr size_t MAX_TERRAIN_BRUSHES=64;
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
};
static std::vector<SceneSnapshot> gUndo,gRedo;
static bool gSuspendHistory=false;
static constexpr size_t HISTORY_LIMIT=40;
SceneSnapshot captureScene(){return {gEntities,gNextId,gSelected,gWorldSize,gDemoRoads,gTarget,gDistance,gBrushes};}
void restoreScene(const SceneSnapshot&s){
 gEntities=s.entities;gNextId=s.nextId;gSelected=s.selected;gWorldSize=s.worldSize;
 gDemoRoads=s.demoRoads;gTarget=s.target;gDistance=s.cameraDistance;
 gBrushes=s.terrain;gTerrainRevision++;gSceneInitialized=true;
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
void uploadAsset(Asset&asset){
 const auto &v=asset.vertices;
 if(v.empty())return;
 Mesh &m=asset.gpu;
 m.count=static_cast<GLsizei>(v.size()/9);
 glGenVertexArrays(1,&m.vao);glBindVertexArray(m.vao);
 glGenBuffers(1,&m.buffer);glBindBuffer(GL_ARRAY_BUFFER,m.buffer);
 glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(v.size()*sizeof(float)),v.data(),GL_STATIC_DRAW);
 const GLsizei stride=9*sizeof(float);
 for(int attrib=0;attrib<3;attrib++){
   glEnableVertexAttribArray(attrib);
   glVertexAttribPointer(attrib,3,GL_FLOAT,GL_FALSE,stride,reinterpret_cast<void*>(static_cast<uintptr_t>(attrib*3*sizeof(float))));
 }
 glBindVertexArray(0);
}
GLuint shader(GLenum kind,const char*src){
 GLuint s=glCreateShader(kind);glShaderSource(s,1,&src,nullptr);glCompileShader(s);
 GLint ok=GL_FALSE;glGetShaderiv(s,GL_COMPILE_STATUS,&ok);
 if(!ok){char log[512]={};glGetShaderInfoLog(s,sizeof(log),nullptr,log);__android_log_print(ANDROID_LOG_ERROR,"OE","Shader: %s",log);}
 return s;
}
void setupGL(){
 const char*vert=R"(#version 300 es
 layout(location=0) in vec3 aPos;
 layout(location=1) in vec3 aNormal;
 layout(location=2) in vec3 aColor;
 uniform mat4 uMvp;
 uniform mat4 uModel;
 out vec3 vNormal;
 out vec3 vColor;
 void main(){
   gl_Position=uMvp*vec4(aPos,1.0);
   vNormal=normalize(mat3(uModel)*aNormal);
   vColor=aColor;
 })";
 const char*frag=R"(#version 300 es
 precision mediump float;
 in vec3 vNormal;
 in vec3 vColor;
 uniform vec3 uColor;
 uniform float uHighlight;
 out vec4 fragColor;
 void main(){
   float sun=max(dot(normalize(vNormal),normalize(vec3(-.55,1.,.5))),0.);
   vec3 color=uColor*vColor*(.52+.48*sun);
   color=mix(color,vec3(.99,.72,.30),uHighlight*.30);
   fragColor=vec4(color,1.);
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
 for(int kind=0;kind<3;kind++){
  std::vector<float> d=geometry(kind);Mesh &m=gMeshes[kind];m.count=(GLsizei)(d.size()/6);
  glGenVertexArrays(1,&m.vao);glBindVertexArray(m.vao);
  glGenBuffers(1,&m.buffer);glBindBuffer(GL_ARRAY_BUFFER,m.buffer);
  glBufferData(GL_ARRAY_BUFFER,(GLsizeiptr)(d.size()*sizeof(float)),d.data(),GL_STATIC_DRAW);
  glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,6*sizeof(float),nullptr);
  glEnableVertexAttribArray(1);glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(3*sizeof(float)));
  glDisableVertexAttribArray(2);
 }
 for(int kind=0;kind<10;kind++)if(kind!=6)bakeStaticMesh(kind);
 for(auto &kv:gAssets)uploadAsset(kv.second);
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
 const float radius=asset.radius*e.size;
 const Vec3 center{e.x,e.y+asset.centerY*e.size,e.z};
 const Vec3 delta=sub(center,cameraEye());
 const float maxDistance=230.f+radius;
 if(dot(delta,delta)>maxDistance*maxDistance)return false;
 return visibleSphere(frustum,center,radius);
}
void drawImported(const Mat4&vp,const Entity&e){
 auto it=gAssets.find(e.asset);
 if(it==gAssets.end()||it->second.gpu.count==0)return;
 const Mat4 model=mul(mul(translate({e.x,e.y,e.z}),rotateY(e.yaw*RAD)),scale({e.size,e.size,e.size}));
 const Mat4 mvp=mul(vp,model);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,mvp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1,1,1);
 glUniform1f(gShader.locLight,e.id==gSelected?1.f:0.f);
 const Mesh &m=it->second.gpu;
 glBindVertexArray(m.vao);glDrawArrays(GL_TRIANGLES,0,m.count);
 ++gDrawCalls;gTriangles+=m.count/3;
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
 }
}
void bakeStaticMesh(int type){
 Asset baked;
 // Build once on GL-context creation. Later only transform one mesh per entity.
 forEachStaticPart(type,[&](int kind,float x,float y,float z,float sx,float sy,float sz,Color col){
  const std::vector<float> source=geometry(kind);
  baked.vertices.reserve(baked.vertices.size()+source.size()/6*9);
  for(size_t i=0;i+5<source.size();i+=6){
   baked.vertices.insert(baked.vertices.end(),{
      x+source[i]*sx,y+source[i+1]*sy,z+source[i+2]*sz,
      source[i+3]*sx,source[i+4]*sy,source[i+5]*sz,
      col.r,col.g,col.b
   });
  }
 });
 uploadAsset(baked);
 gStaticMeshes[type]=baked.gpu;
}
void drawStatic(const Mat4&vp,const Entity&e){
 if(e.type<0||e.type>=10||e.type==6)return;
 const Mesh&m=gStaticMeshes[e.type];
 if(m.count<=0)return;
 const Mat4 model=mul(mul(translate({e.x,e.y,e.z}),rotateY(e.yaw*RAD)),
  scale({e.size,e.size,e.size}));
 const Mat4 mvp=mul(vp,model);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,mvp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1.f,1.f,1.f);
 glUniform1f(gShader.locLight,e.id==gSelected?1.f:0.f);
 glBindVertexArray(m.vao);
 glDrawArrays(GL_TRIANGLES,0,m.count);
 ++gDrawCalls;gTriangles+=m.count/3;
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
 for(int tz=std::max(-half,gz-5);tz<=std::min(half-1,gz+5);tz++)
 for(int tx=std::max(-half,gx-5);tx<=std::min(half-1,gx+5);tx++){
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
  glBindVertexArray(gTerrain.vao);
  glDrawArrays(GL_TRIANGLES,0,gTerrain.vertices);
  ++gDrawCalls;gTriangles+=gTerrain.vertices/3;
 }
 if(gDemoRoads&&std::abs(gTarget.x)<150.f&&std::abs(gTarget.z)<150.f){
  draw(vp,0,{0,-.025f,2},{27,.12f,23},0,{.44f,.44f,.44f});
  draw(vp,0,{0,-.015f,16},{8,.13f,22},0,{.53f,.51f,.48f});
 }
}
bool groundPoint(float px,float py,float &x,float &z){
 const Vec3 eye=cameraEye(),forward=unit(sub(gTarget,eye));
 const Vec3 right=unit(cross(forward,{0,1,0})),up=cross(right,forward);
 const float half=std::tan(PI/6.f),aspect=float(gWidth)/float(std::max(gHeight,1));
 const float u=(px*2.f/gWidth-1.f)*half*aspect,v=(1.f-py*2.f/gHeight)*half;
 Vec3 ray=unit({forward.x+right.x*u+up.x*v,forward.y+right.y*u+up.y*v,forward.z+right.z*u+up.z*v});
 if(ray.y>-.0001f)return false;
 float t=-eye.y/ray.y;
 if(t<=0||t>1500.f)return false;
 x=eye.x+ray.x*t;z=eye.z+ray.z*t;
 return x>=-worldHalf()&&x<=worldHalf()&&z>=-worldHalf()&&z<=worldHalf();
}
bool blocked(float x,float z,int type){
 const float radius=(type==0||type==1||type==2||type==6)?6.3f:1.5f;
 for(const Entity&e:gEntities){
  float er=(e.type==0||e.type==1||e.type==2||e.type==6)?6.3f*e.size:1.5f*e.size;
  float dx=e.x-x,dz=e.z-z;
  if(dx*dx+dz*dz<(radius+er)*(radius+er))return true;
 }
 return false;
}
bool staticInView(const Mat4&vp,const Entity&e){
 if(std::abs(e.x-gTarget.x)>230.f||std::abs(e.z-gTarget.z)>230.f)return false;
 const Vec4 clip=apply(vp,{e.x,e.y+2.8f*e.size,e.z,1.f});
 const float radius=(e.type<=2?7.5f:(e.type==5?4.5f:3.5f))*e.size;
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
 if(type<0||type>9||type==6||gEntities.size()>=MAX_ENTITIES)return 0;
 recordEdit();
 int id=gNextId++;gEntities.push_back({id,type,x,z,yaw,size,"",terrainHeightAt(x,z)});gSelected=id;return id;
}
void reset(bool demo){
 recordEdit(); // demo generation is a single undoable change
 const bool wasMuted=gSuspendHistory;
 gSuspendHistory=true;
 gEntities.clear();gBrushes.clear();gTerrainRevision++;gNextId=1;gSelected=0;
 gSceneInitialized=true;gDemoRoads=demo;
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
 const char* names[]={"Таверна","Кузница","Аукционный дом","Фонарь","Фонтан","Дерево","Модель GLB","Камни","Куст","Лавка"};
 return type>=0&&type<10?names[type]:"Объект";
}
} // namespace

extern "C" {
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeInit(JNIEnv*,jclass){
 setupGL();if(!gSceneInitialized)reset(true);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeResize(JNIEnv*,jclass,jint w,jint h){
 gWidth=std::max(1,int(w));gHeight=std::max(1,int(h));
 glViewport(0,0,gWidth,gHeight);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeDraw(JNIEnv*,jclass){
 glViewport(0,0,gWidth,gHeight);
 glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
 glUseProgram(gShader.program);
 gDrawCalls=0;gTriangles=0;
 Mat4 vp=viewProjection();
 const Frustum frustum=makeFrustum(vp);
 drawWorld(vp);
 for(const auto&e:gEntities)drawEntity(vp,frustum,e);
 glBindVertexArray(0);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeOrbit(JNIEnv*,jclass,jfloat dx,jfloat dy,jfloat zoom){
 gYaw-=dx*.006f;
 gPitch=clampf(gPitch+dy*.006f,-1.16f,1.25f);
 gDistance=clampf(gDistance*clampf(zoom,.6f,1.5f),5,210);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativePan(JNIEnv*,jclass,jfloat dx,jfloat dy){
 const float speed=gDistance*.0035f;
 const float c=std::cos(gYaw),s=std::sin(gYaw);
 gTarget.x=clampf(gTarget.x+(-dx*c+dy*s)*speed,-worldHalf()+4,worldHalf()-4);
 gTarget.z=clampf(gTarget.z+(dx*s+dy*c)*speed,-worldHalf()+4,worldHalf()-4);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeFocusLocation(JNIEnv*,jclass,jint index){
 if(index<0 || index>=static_cast<jint>(sizeof(LOCATIONS)/sizeof(LOCATIONS[0])))return;
 gTarget={LOCATIONS[index].x,1.2f,LOCATIONS[index].z};
 gDistance=index==0?35.f:68.f;
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeRenderStats(JNIEnv*env,jclass){
 std::ostringstream out;
 out<<"Рендер: "<<gDrawCalls<<" draw calls / "<<gTriangles<<" треугольников · земля "<<gTerrain.tiles<<" участков";
 return env->NewStringUTF(out.str().c_str());
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeGetWorldSize(JNIEnv*,jclass){return gWorldSize;}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeSetWorldSize(JNIEnv*,jclass,jint newSize){
 if(!allowedWorldSize(newSize))return JNI_FALSE;
 float half=static_cast<float>(newSize)*.5f;
 // Never silently hide buildings when shrinking the world.
 for(const auto&e:gEntities)if(std::abs(e.x)>half-1.f||std::abs(e.z)>half-1.f)return JNI_FALSE;
 if(gWorldSize==newSize)return JNI_TRUE;
 recordEdit();
 gWorldSize=newSize;
 gTarget.x=clampf(gTarget.x,-half+4.f,half-4.f);
 gTarget.z=clampf(gTarget.z,-half+4.f,half-4.f);
 return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeJumpTo(JNIEnv*,jclass,jfloat x,jfloat z){
 if(!std::isfinite(x)||!std::isfinite(z)||std::abs(x)>worldHalf()-4.f||std::abs(z)>worldHalf()-4.f)return JNI_FALSE;
 gTarget={x,1.2f,z};
 return JNI_TRUE;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativePick(JNIEnv*,jclass,jfloat x,jfloat y){
 const Mat4 vp=viewProjection();
 float depth=1e9f;int found=0;
 for(const auto&e:gEntities){
  const float range=e.type<3||e.type==6?3.5f:2.0f;
  const float h=e.type<3||e.type==6?6.f:4.9f;
  float left=1e9f,top=1e9f,right=-1e9f,bottom=-1e9f,minW=1e9f;
  int visible=0;
  for(float xx:{-range,range})for(float zz:{-range,range})for(float yy:{0.f,h}){
    float a=e.yaw*RAD,k=e.size,c=std::cos(a),s=std::sin(a);
    Vec4 q=apply(vp,{e.x+(c*xx+s*zz)*k,e.y+yy*k,e.z+(c*zz-s*xx)*k,1});
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
 if(type<0||type>9||type==6)return 0;
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
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativePlaceAtScreen(JNIEnv*,jclass,jint type,jfloat px,jfloat py){
 if(type<0||type>9||type==6)return 0;
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
 if(type<0||type>9||type==6 || !std::isfinite(x)||!std::isfinite(z)||!std::isfinite(yaw)||!std::isfinite(size) ||
 std::abs(x)>worldHalf() || std::abs(z)>worldHalf() || std::abs(yaw)>36000 || size<.2f || size>8)return 0;
 return add(type,x,z,yaw,size);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSelect(JNIEnv*,jclass,jint id){
 gSelected=0;for(const auto&e:gEntities)if(e.id==id){gSelected=id;break;}
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
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeNudge(JNIEnv*,jclass,jfloat x,jfloat z){
 if(!std::isfinite(x)||!std::isfinite(z))return;
 if(Entity*e=selected()){
  const float nx=clampf(e->x+x,-worldHalf(),worldHalf());
  const float nz=clampf(e->z+z,-worldHalf(),worldHalf());
  if(nx==e->x&&nz==e->z)return;
  recordEdit();e->x=nx;e->z=nz;
 }
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeRotate(JNIEnv*,jclass,jfloat deg){
 if(!std::isfinite(deg)||deg==0)return;
 if(Entity*e=selected()){recordEdit();e->yaw=std::fmod(e->yaw+deg,360.f);}
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
 std::ostringstream out;out<<nameFor(e->type)<<" #" <<e->id<<"  |  X "<<std::fixed<<std::setprecision(1)
 <<e->x<<"  Z "<<e->z<<"  Y "<<e->y<<"  ↻ "<<e->yaw<<"°  Размер "<<e->size;
 return env->NewStringUTF(out.str().c_str());
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeSceneSnapshot(JNIEnv*env,jclass){
 std::ostringstream out;out<<std::fixed<<std::setprecision(4)<<"{\"version\":1,\"worldSize\":"<<gWorldSize<<",\"demoRoads\":"<<(gDemoRoads?"true":"false")<<",\"items\":[";
 for(size_t i=0;i<gEntities.size();i++){
  const auto&e=gEntities[i];if(i)out<<",";
  out<<"{\"type\":"<<e.type<<",\"x\":"<<e.x<<",\"z\":"<<e.z
     <<",\"yaw\":"<<e.yaw<<",\"size\":"<<e.size<<",\"y\":"<<e.y;
  if(e.type==6)out<<",\"asset\":\""<<e.asset<<"\"";
  out<<"}";
 }
 out<<"],\"terrain\":[";
 for(size_t i=0;i<gBrushes.size();i++){
  const auto&b=gBrushes[i];if(i)out<<",";
  out<<"["<<b.x<<","<<b.z<<","<<b.radius<<","<<b.strength<<"]";
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
 if(it->second.gpu.vao)glDeleteVertexArrays(1,&it->second.gpu.vao);
 if(it->second.gpu.buffer)glDeleteBuffers(1,&it->second.gpu.buffer);
 gAssets.erase(it);
}
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRegisterAsset(
 JNIEnv*env,jclass,jstring key,jfloatArray data,jfloat centerY,jfloat radius){
 if(!key||!data||!std::isfinite(centerY)||!std::isfinite(radius)||
    radius<.05f||radius>100.f||centerY<0.f||centerY>100.f)return JNI_FALSE;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return JNI_FALSE;
 std::string assetKey(raw);env->ReleaseStringUTFChars(key,raw);
 if(assetKey.size()!=31||assetKey.compare(0,3,"oe_")!=0||
    assetKey.substr(assetKey.size()-4)!=".glb")return JNI_FALSE;
 for(size_t i=3;i<assetKey.size()-4;i++)
  if(!((assetKey[i]>='0'&&assetKey[i]<='9')||(assetKey[i]>='a'&&assetKey[i]<='f')))
   return JNI_FALSE;
 auto existing=gAssets.find(assetKey);
 // A content-addressed key is immutable; never re-upload the same model.
 if(existing!=gAssets.end()&&existing->second.gpu.count>0)return JNI_TRUE;
 jsize count=env->GetArrayLength(data);
 if(count<=0||count>MAX_IMPORTED_TRIANGLES*27||count%27!=0)return JNI_FALSE;
 if(existing==gAssets.end()&&gAssets.size()>=MAX_ASSETS)return JNI_FALSE;
 jfloat*ptr=env->GetFloatArrayElements(data,nullptr);
 if(!ptr)return JNI_FALSE;
 bool valid=true;
 for(jsize i=0;i<count;i++)if(!std::isfinite(ptr[i])||std::abs(ptr[i])>10000){valid=false;break;}
 if(!valid){env->ReleaseFloatArrayElements(data,ptr,JNI_ABORT);return JNI_FALSE;}
 Asset prepared;
 prepared.centerY=centerY;prepared.radius=radius;
 prepared.vertices.assign(ptr,ptr+count);
 env->ReleaseFloatArrayElements(data,ptr,JNI_ABORT);
 // Keep the previous GPU asset intact until replacement is ready.
 // Bounded error drain: never block indefinitely on a lost GL context.
 for(int i=0;i<8&&glGetError()!=GL_NO_ERROR;i++){}
 uploadAsset(prepared);
 const GLenum error=glGetError();
 if(error!=GL_NO_ERROR||!prepared.gpu.vao||!prepared.gpu.buffer||prepared.gpu.count<=0){
  if(prepared.gpu.vao)glDeleteVertexArrays(1,&prepared.gpu.vao);
  if(prepared.gpu.buffer)glDeleteBuffers(1,&prepared.gpu.buffer);
  __android_log_print(ANDROID_LOG_ERROR,"OE","GLB GPU upload failed: GL 0x%x",error);
  return JNI_FALSE;
 }
 if(existing!=gAssets.end()){
  if(existing->second.gpu.vao)glDeleteVertexArrays(1,&existing->second.gpu.vao);
  if(existing->second.gpu.buffer)glDeleteBuffers(1,&existing->second.gpu.buffer);
  existing->second=std::move(prepared);
 }else gAssets.emplace(assetKey,std::move(prepared));
 return JNI_TRUE;
}
// Replace a scene only if EVERY model and entity is valid. No scene mutation
// occurs until all checks finish: failed imports cannot erase the open world.
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeReplaceScene(
 JNIEnv*env,jclass,jint size,jboolean demo,jintArray kinds,jfloatArray transforms,
 jobjectArray modelKeys){
 if(!allowedWorldSize(size)||!kinds||!transforms||!modelKeys)return JNI_FALSE;
 const jsize n=env->GetArrayLength(kinds);
 if(n<0||n>MAX_ENTITIES||env->GetArrayLength(transforms)!=n*4||
    env->GetArrayLength(modelKeys)!=n)return JNI_FALSE;
 std::array<jint,MAX_ENTITIES> types{};
 std::array<jfloat,MAX_ENTITIES*4> matrix{};
 if(n){
  env->GetIntArrayRegion(kinds,0,n,types.data());
  env->GetFloatArrayRegion(transforms,0,n*4,matrix.data());
  if(env->ExceptionCheck())return JNI_FALSE;
 }
 std::vector<Entity> staged;
 staged.reserve(static_cast<size_t>(n));
 const float half=static_cast<float>(size)*.5f;
 for(jsize i=0;i<n;i++){
  const int type=types[i];
  const float x=matrix[i*4],z=matrix[i*4+1],yaw=matrix[i*4+2],scale=matrix[i*4+3];
  if(type<0||type>9||!std::isfinite(x)||!std::isfinite(z)||
    !std::isfinite(yaw)||!std::isfinite(scale)||
    std::abs(x)>half||std::abs(z)>half||std::abs(yaw)>36000||
    scale<.2f||scale>8.f)return JNI_FALSE;
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
  staged.push_back({static_cast<int>(i)+1,type,x,z,yaw,scale,key});
 }
 gEntities.swap(staged);
 gWorldSize=size;gDemoRoads=demo==JNI_TRUE;
 gNextId=static_cast<int>(n)+1;gSelected=0;
 gTarget={0,1.2f,1.3f};gDistance=28.f;
 gSceneInitialized=true;
 gUndo.clear();gRedo.clear();
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
