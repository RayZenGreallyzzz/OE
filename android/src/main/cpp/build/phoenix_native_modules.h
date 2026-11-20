#pragma once
// Native geometry plans for the 24 built-in Phoenix UV44 construction catalog
// entries. Independent implementations; no original/proprietary UV44 assets.
// ALL items are editable native OE entities sharing terrain, camera, undo,
// and .oezip. Parity is incomplete: see docs/PHOENIX_UV44_NATIVE_KIT.md.
#include <cmath>
#include <initializer_list>
namespace oe { namespace phoenix {
constexpr int kFirst=29,kLast=52,kCount=kLast-kFirst+1;
inline bool valid(int kind){return kind>=kFirst&&kind<=kLast;}
inline const char* name(int kind){
 static const char*const names[kCount]={
 "UV44 Пол 2x2","UV44 Лестница 13 ступеней","UV44 Прямая стена 2м",
 "UV44 Угол 90","UV44 Дуга 90","UV44 Дуга 180","UV44 Секция крыши",
 "UV44 Дверь классическая","UV44 Дверь округлая","UV44 Дверь готическая",
 "UV44 Дверь руническая","UV44 Дверь двустворчатая",
 "UV44 Рама прямая","UV44 Рама полукруглая","UV44 Рама готическая",
 "UV44 Магический портал","UV44 Двускатная крыша","UV44 Вальмовая 3 ската",
 "UV44 Вальмовая 4 ската","UV44 Крыша шатёр","UV44 Мансардная крыша",
 "UV44 Фэнтези шпиль","UV44 Ведьмина крыша","UV44 Крыша башни"};
 return valid(kind)?names[kind-kFirst]:"Неизвестная деталь";
}
struct Part {int mesh;float x,y,z,w,h,d,yaw,r,g,b;};
struct Socket {float x,z,dx,dz;};
inline int sockets(int kind,Socket out[2]){
 switch(kind){
 case 31:out[0]={-1,0,-1,0};out[1]={1,0,1,0};return 2;
 case 32:out[0]={-1.9f,0,-1,0};out[1]={0,-1.9f,0,-1};return 2;
 case 33:out[0]={2,0,0,-1};out[1]={0,2,-1,0};return 2;
 case 34:out[0]={2,0,0,-1};out[1]={-2,0,0,-1};return 2;
 default:return 0;
 }
}
template<class Emit> bool parts(int kind,Emit emit){
 if(!valid(kind))return false;
 const float stone[3]={.72f,.67f,.59f},wood[3]={.43f,.28f,.18f},
   metal[3]={.32f,.34f,.39f},gold[3]={.79f,.61f,.32f},
   roof[3]={.55f,.26f,.22f};
 auto box=[&](float x,float y,float z,float w,float h,float d,
              const float *rgb=stone,float yaw=0.f,int mesh=0){
   emit(Part{mesh,x,y,z,w,h,d,yaw,rgb[0],rgb[1],rgb[2]});
 };
 if(kind==29){box(0,.09f,0,2,.18f,2);return true;}
 if(kind==30){
  for(int i=0;i<13;i++){float h=(i+1)*.2f;
   box(0,h*.5f,-1.95f+(i+.5f)*.3f,1.2f,h,.3f);}
  return true;
 }
 if(kind==31){box(0,1.3f,0,2,2.6f,.22f);return true;}
 if(kind==32){
  box(-.95f,1.3f,0,1.9f,2.6f,.22f);
  box(0,1.3f,-.95f,.22f,2.6f,1.9f);return true;
 }
 if(kind==33||kind==34){
  const float theta=kind==33?1.57079632679f:3.14159265359f;
  const int steps=kind==33?18:36;
  for(int i=0;i<steps;i++){
   const float a=(i+.5f)*theta/steps,arc=2.f*theta/steps;
   box(2.f*std::cos(a),1.3f,2.f*std::sin(a),
       arc+.012f,2.6f,.22f,stone,90.f-a*57.2957795f);
  }return true;
 }
 if(kind==35){box(0,.78f,0,2.5f,1.35f,2.f,roof,0,1);return true;}
 if(kind>=36&&kind<=40){
  box(-.94f,1.28f,0,.13f,2.56f,.27f,wood);
  box(.94f,1.28f,0,.13f,2.56f,.27f,wood);
  box(0,2.55f,0,2.01f,.16f,.28f,wood);
  if(kind==40){
   box(-.45f,1.18f,.04f,.85f,2.25f,.1f,wood);
   box(.45f,1.18f,.04f,.85f,2.25f,.1f,wood);
   box(0,1.22f,.14f,.09f,.15f,.07f,gold);
  }else{
   box(0,1.18f,.04f,1.76f,2.26f,.1f,wood);
   box(.64f,1.19f,.16f,.1f,.12f,.07f,gold);
  }
  if(kind==37)box(0,2.35f,.03f,1.66f,.24f,.28f,stone,0,2);
  if(kind==38)box(0,2.53f,.02f,1.62f,.36f,.28f,stone,0,1);
  if(kind==39)for(int i=0;i<5;i++)
    box(0,.47f+i*.4f,.12f,.9f,.035f,.035f,gold);
  return true;
 }
 if(kind>=41&&kind<=44){
  const float*trim=kind==44?gold:(kind==43?metal:wood);
  box(-.94f,1.3f,0,.15f,2.6f,.24f,trim);
  box(.94f,1.3f,0,.15f,2.6f,.24f,trim);
  box(0,2.57f,0,2.f,.15f,.24f,trim);
  if(kind==42)box(0,2.49f,0,1.78f,.3f,.28f,trim,0,2);
  if(kind==43)box(0,2.65f,0,1.68f,.35f,.24f,trim,0,1);
  if(kind==44)for(float y:{.42f,2.05f})
    box(0,y,.14f,1.9f,.055f,.055f,gold);
  return true;
 }
 const float*shade=(kind>=50)?metal:roof;
 if(kind==45)box(0,1.02f,0,3.25f,1.7f,3.f,shade,0,1);
 if(kind==46){box(0,.85f,0,3.2f,1.5f,2.7f,shade,0,1);
              box(.45f,1.36f,0,1.7f,.45f,1.8f,shade);}
 if(kind==47)box(0,1.f,0,3.1f,1.8f,3.1f,shade,0,1);
 if(kind==48)box(0,1.36f,0,3.3f,2.6f,3.3f,shade,0,1);
 if(kind==49){box(0,.62f,0,3.3f,1.2f,3.3f,shade,0,1);
              box(0,1.45f,0,2.2f,1.f,2.2f,shade,0,1);}
 if(kind==50){box(0,1.75f,0,2.8f,3.45f,2.8f,shade,0,1);
              box(0,3.55f,0,.22f,.65f,.22f,gold);}
 if(kind==51){box(0,.78f,0,3.1f,1.5f,3.1f,shade,0,1);
              box(.6f,2.08f,-.15f,1.8f,2.25f,1.9f,shade,0,1);}
 if(kind==52){box(0,1.4f,0,3.5f,2.7f,3.5f,shade,0,1);
              box(0,2.87f,0,.42f,.48f,.42f,gold,0,2);}
 return true;
}
}} // oe::phoenix
