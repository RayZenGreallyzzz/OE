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
struct Entity {int id=0, type=0; float x=0, z=0, yaw=0, size=1; std::string asset;};
struct Color {float r,g,b;};
struct Shader {
 GLuint program=0;
 GLint locMvp=-1, locModel=-1, locColor=-1, locLight=-1;
};
struct Asset { Mesh gpu; std::vector<float> vertices; };
static Mesh gMeshes[3];
static std::unordered_map<std::string,Asset> gAssets;
static constexpr int MAX_ASSETS=12;
static constexpr int MAX_IMPORTED_TRIANGLES=90000;
static Shader gShader;
static std::vector<Entity> gEntities;
static int gNextId=1, gSelected=0, gWidth=1, gHeight=1;
static float gYaw=.55f, gPitch=.45f, gDistance=28.f;
static Vec3 gTarget{0,1.2f,1.3f};

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
 float f=1/std::tan(PI/6), near=.1f, far=140.f;Mat4 m;
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
 for(auto &kv:gAssets)uploadAsset(kv.second);
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
}
void drawImported(const Mat4&vp,const Entity&e){
 auto it=gAssets.find(e.asset);
 if(it==gAssets.end()||it->second.gpu.count==0)return;
 const Mat4 model=mul(mul(translate({e.x,0,e.z}),rotateY(e.yaw*RAD)),scale({e.size,e.size,e.size}));
 const Mat4 mvp=mul(vp,model);
 glUniformMatrix4fv(gShader.locMvp,1,GL_FALSE,mvp.m);
 glUniformMatrix4fv(gShader.locModel,1,GL_FALSE,model.m);
 glUniform3f(gShader.locColor,1,1,1);
 glUniform1f(gShader.locLight,e.id==gSelected?1.f:0.f);
 const Mesh &m=it->second.gpu;
 glBindVertexArray(m.vao);glDrawArrays(GL_TRIANGLES,0,m.count);
}
void part(const Mat4&vp,const Entity&e,int kind,float x,float y,float z,float sx,float sy,float sz,Color color){
 const float a=e.yaw*RAD,c=std::cos(a),s=std::sin(a),k=e.size;
 Vec3 p{e.x+k*(c*x+s*z),k*y,e.z+k*(c*z-s*x)};
 draw(vp,kind,p,{sx*k,sy*k,sz*k},a,color,e.id==gSelected);
}
constexpr Color stone{.51f,.50f,.49f},mortar{.75f,.64f,.51f},oak{.32f,.20f,.13f},
 roof{.55f,.22f,.18f},gold{.82f,.62f,.33f},dark{.19f,.23f,.29f},glass{.82f,.65f,.33f};
void building(const Mat4&vp,const Entity&e){
 const bool forge=e.type==1,auction=e.type==2;
 Color base=forge?stone:(auction?Color{.79f,.70f,.56f}:mortar);
 Color roofColor=forge?Color{.27f,.31f,.37f}:(auction?Color{.30f,.25f,.40f}:roof);
 part(vp,e,0,0,.18f,0,6,.36f,5,stone);
 part(vp,e,0,0,1.75f,0,5.7f,3.05f,4.75f,base);
 part(vp,e,1,0,3.25f,0,6.5f,2.25f,5.4f,roofColor);
 part(vp,e,0,0,1.03f,2.41f,1.36f,2.06f,.12f,oak);
 part(vp,e,0,.40f,1.06f,2.49f,.10f,.12f,.13f,gold);
 part(vp,e,0,0,3.1f,2.50f,5.92f,.19f,.22f,oak);
 for(float x:{-1.83f,1.83f}){
  part(vp,e,0,x,1.7f,2.47f,.19f,3.12f,.25f,oak);
  part(vp,e,0,x,2.1f,2.56f,.87f,.86f,.1f,glass);
  part(vp,e,0,x,2.1f,2.63f,.10f,.91f,.12f,oak);
  part(vp,e,0,x,2.56f,2.63f,1.06f,.12f,.15f,oak);
 }
 if(forge){
  part(vp,e,0,1.8f,4.36f,-.85f,1.12f,2.9f,1.1f,dark);
  part(vp,e,0,1.8f,5.86f,-.85f,1.36f,.22f,1.3f,stone);
 } else if(auction){
  for(float x:{-2.45f,2.45f}){
    part(vp,e,2,x,1.61f,2.62f,.47f,3.0f,.47f,stone);
    part(vp,e,0,x,3.13f,2.62f,.72f,.24f,.72f,gold);
  }
  part(vp,e,0,0,2.8f,2.63f,1.52f,.42f,.16f,gold);
 } else {
  part(vp,e,0,0,2.55f,2.83f,2.3f,.18f,.9f,oak);
  part(vp,e,0,-1.9f,4.3f,-.9f,.7f,2.0f,.75f,stone);
 }
}
void drawEntity(const Mat4&vp,const Entity&e){
 if(e.type==6){drawImported(vp,e);return;}
 switch(e.type){
 case 0:case 1:case 2:building(vp,e);break;
 case 3:
  part(vp,e,2,0,1.4f,0,.18f,2.8f,.18f,dark);
  part(vp,e,0,0,2.94f,0,.62f,.72f,.6f,gold);
  part(vp,e,1,0,3.34f,0,.86f,.58f,.82f,dark);
  break;
 case 4:
  part(vp,e,2,0,.27f,0,3.1f,.58f,3.1f,stone);
  part(vp,e,2,0,.63f,0,2.55f,.09f,2.55f,{.25f,.52f,.66f});
  part(vp,e,2,0,1.0f,0,.86f,.88f,.86f,stone);
  break;
 case 5:
  part(vp,e,2,0,1.45f,0,.62f,2.9f,.62f,oak);
  part(vp,e,2,0,3.55f,0,2.2f,2.5f,2.2f,{.20f,.39f,.27f});
  break;
 default:break;
 }
}
int add(int type,float x,float z,float yaw,float size){
 if(type<0||type>5||gEntities.size()>=MAX_ENTITIES)return 0;
 int id=gNextId++;gEntities.push_back({id,type,x,z,yaw,size,""});gSelected=id;return id;
}
void reset(bool demo){
 gEntities.clear();gNextId=1;gSelected=0;
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
}
Entity* selected(){for(auto &e:gEntities)if(e.id==gSelected)return &e;return nullptr;}
const char* nameFor(int type){
 const char* names[]={"Таверна","Кузница","Аукционный дом","Фонарь","Фонтан","Дерево","Модель GLB"};
 return type>=0&&type<7?names[type]:"Объект";
}
} // namespace

extern "C" {
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeInit(JNIEnv*,jclass){
 setupGL();if(gEntities.empty()&&gNextId==1)reset(true);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeResize(JNIEnv*,jclass,jint w,jint h){
 gWidth=std::max(1,int(w));gHeight=std::max(1,int(h));
 glViewport(0,0,gWidth,gHeight);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeDraw(JNIEnv*,jclass){
 glViewport(0,0,gWidth,gHeight);
 glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
 glUseProgram(gShader.program);
 Mat4 vp=viewProjection();
 draw(vp,0,{0,-.38f,0},{90,.6f,90},0,{.19f,.29f,.25f});
 draw(vp,0,{0,-.025f,2},{27,.12f,23},0,{.44f,.44f,.44f});
 draw(vp,0,{0,-.015f,16},{8,.13f,22},0,{.53f,.51f,.48f});
 for(const auto&e:gEntities)drawEntity(vp,e);
 glBindVertexArray(0);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeOrbit(JNIEnv*,jclass,jfloat dx,jfloat dy,jfloat zoom){
 gYaw-=dx*.006f;
 gPitch=clampf(gPitch+dy*.006f,-1.16f,1.25f);
 gDistance=clampf(gDistance*clampf(zoom,.6f,1.5f),5,85);
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
    Vec4 q=apply(vp,{e.x+(c*xx+s*zz)*k,yy*k,e.z+(c*zz-s*xx)*k,1});
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
 const size_t count=gEntities.size();
 return add(type,((int(count)%3)-1)*7.f,9.f+float(count/3)*7.f,0,1);
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeAddPlaced(JNIEnv*,jclass,jint type,jfloat x,jfloat z,jfloat yaw,jfloat size){
 if(!std::isfinite(x)||!std::isfinite(z)||!std::isfinite(yaw)||!std::isfinite(size) ||
 std::abs(x)>300 || std::abs(z)>300 || std::abs(yaw)>36000 || size<.2f || size>8)return 0;
 return add(type,x,z,yaw,size);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSelect(JNIEnv*,jclass,jint id){
 gSelected=0;for(const auto&e:gEntities)if(e.id==id){gSelected=id;break;}
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeNudge(JNIEnv*,jclass,jfloat x,jfloat z){
 if(Entity*e=selected()){e->x=clampf(e->x+x,-300,300);e->z=clampf(e->z+z,-300,300);}
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeRotate(JNIEnv*,jclass,jfloat deg){
 if(Entity*e=selected())e->yaw=std::fmod(e->yaw+deg,360.f);
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeScale(JNIEnv*,jclass,jfloat factor){
 if(Entity*e=selected())e->size=clampf(e->size*factor,.2f,8);
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeSelectedInfo(JNIEnv*env,jclass){
 Entity*e=selected();if(!e)return env->NewStringUTF("Выбери здание касанием или через список «Объекты»");
 std::ostringstream out;out<<nameFor(e->type)<<" #" <<e->id<<"  |  X "<<std::fixed<<std::setprecision(1)
 <<e->x<<"  Z "<<e->z<<"  ↻ "<<e->yaw<<"°  Размер "<<e->size;
 return env->NewStringUTF(out.str().c_str());
}
JNIEXPORT jstring JNICALL Java_game_oe_editor_EditorActivity_nativeSceneSnapshot(JNIEnv*env,jclass){
 std::ostringstream out;out<<std::fixed<<std::setprecision(4)<<"{\"version\":1,\"items\":[";
 for(size_t i=0;i<gEntities.size();i++){
  const auto&e=gEntities[i];if(i)out<<",";
  out<<"{\"type\":"<<e.type<<",\"x\":"<<e.x<<",\"z\":"<<e.z
     <<",\"yaw\":"<<e.yaw<<",\"size\":"<<e.size;
  if(e.type==6)out<<",\"asset\":\""<<e.asset<<"\"";
  out<<"}";
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
JNIEXPORT jboolean JNICALL Java_game_oe_editor_EditorActivity_nativeRegisterAsset(JNIEnv*env,jclass,jstring key,jfloatArray data){
 if(!key||!data)return JNI_FALSE;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return JNI_FALSE;
 std::string assetKey(raw);env->ReleaseStringUTFChars(key,raw);
 if(assetKey.size()!=31||assetKey.compare(0,3,"oe_")!=0||assetKey.substr(assetKey.size()-4)!=".glb")return JNI_FALSE;
 for(size_t i=3;i<assetKey.size()-4;i++)if(!((assetKey[i]>='0'&&assetKey[i]<='9')||(assetKey[i]>='a'&&assetKey[i]<='f')))return JNI_FALSE;
 jsize count=env->GetArrayLength(data);
 if(count<=0||count>MAX_IMPORTED_TRIANGLES*27||count%27!=0)return JNI_FALSE;
 if(gAssets.find(assetKey)==gAssets.end()&&gAssets.size()>=MAX_ASSETS)return JNI_FALSE;
 jfloat*ptr=env->GetFloatArrayElements(data,nullptr);
 if(!ptr)return JNI_FALSE;
 bool valid=true;
 for(jsize i=0;i<count;i++)if(!std::isfinite(ptr[i])||std::abs(ptr[i])>10000){valid=false;break;}
 if(!valid){env->ReleaseFloatArrayElements(data,ptr,JNI_ABORT);return JNI_FALSE;}
 Asset&a=gAssets[assetKey];
 if(a.gpu.vao)glDeleteVertexArrays(1,&a.gpu.vao);
 if(a.gpu.buffer)glDeleteBuffers(1,&a.gpu.buffer);
 a.vertices.assign(ptr,ptr+count);
 env->ReleaseFloatArrayElements(data,ptr,JNI_ABORT);
 a.gpu={};uploadAsset(a);
 return JNI_TRUE;
}
JNIEXPORT jint JNICALL Java_game_oe_editor_EditorActivity_nativeAddImportedPlaced(JNIEnv*env,jclass,jstring key,jfloat x,jfloat z,jfloat yaw,jfloat size){
 if(!key)return 0;
 const char*raw=env->GetStringUTFChars(key,nullptr);
 if(!raw)return 0;
 std::string asset(raw);env->ReleaseStringUTFChars(key,raw);
 if(gAssets.find(asset)==gAssets.end()||gEntities.size()>=MAX_ENTITIES||
 !std::isfinite(x)||!std::isfinite(z)||!std::isfinite(yaw)||!std::isfinite(size)||
 std::abs(x)>300||std::abs(z)>300||std::abs(yaw)>36000||size<.2f||size>8)return 0;
 int id=gNextId++;
 gEntities.push_back({id,6,x,z,yaw,size,asset});
 gSelected=id;return id;
}
JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeReset(JNIEnv*,jclass,jboolean withDemo){reset(withDemo==JNI_TRUE);}
} // extern C
