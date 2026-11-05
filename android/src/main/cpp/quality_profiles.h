#pragma once
// OE graphics quality profiles. This module is platform-independent and
// deliberately has no Android/GLES dependencies so CI can test policy.
#include <cstddef>
namespace oe {
enum QualityTier:int{Economy=0,Low=1,Balanced=2,High=3};
struct QualityProfile{
 const char* name;
 float resolutionScale; // Android GLSurfaceView's EGL buffer scale
 int terrainTileRadius; // 64m tile range around editor camera
 float viewRangeMetres; // object/paint culling
 float lodDistanceFactor; // <1 = switch to lower LOD sooner
 int maxVisiblePaintStamps; // bound per-redraw GPU draw calls
 int glbTextureCap; // applies on newly imported GLB (already decoded textures unchanged)
};
inline constexpr QualityProfile kProfiles[]={
 {"Экономия",.55f,3,130.f,.70f,12,1024},
 {"Низкое",.70f,4,180.f,.85f,24,1024},
 {"Баланс",.85f,5,230.f,1.0f,36,1024},
 {"Высокое",1.0f,6,300.f,1.25f,48,2048}
};
inline bool validTier(int tier){return tier>=Economy&&tier<=High;}
inline const QualityProfile& profile(int tier){
 return kProfiles[validTier(tier)?tier:Low];
}
// Conservative heuristic — do not infer GPU speed from GL_MAX_TEXTURE_SIZE alone.
// An unknown GPU never auto-selects High. User can override any recommendation.
inline int recommendedTier(int ramMiB,int glMaxTextureSize,bool softwareRenderer){
 if(softwareRenderer||ramMiB<=2048||glMaxTextureSize<2048)return Economy;
 if(ramMiB<=4096||glMaxTextureSize<4096)return Low;
 return Balanced;
}
inline bool validScale(float scale){return scale>=.50f&&scale<=1.0f;}
}
