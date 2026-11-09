#include "structures.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>
namespace oe { namespace medieval {
namespace {
void box(std::vector<Part>& parts,float x,float y,float z,float sx,float sy,float sz,
         float r,float g,float b){
    parts.push_back({x,y,z,sx,sy,sz,r,g,b});
}
}
bool validModule(int type) noexcept {
    return type>=kStoneBridge&&type<=kLastModuleType;
}
std::vector<Part> moduleParts(int type){
    std::vector<Part> p;
    if(!validModule(type))return p;
    p.reserve(30);
    if(type==kStoneBridge){
        // 8 m x 3.6 m old stone bridge, one lightweight open central span.
        // Compact stone arch silhouette with a traversable flat deck. This
        // is a PROTOTYPE, not a physical arched collision mesh.
        for(float x:{-3.43f,3.43f}){
            box(p,x,.40f,0,1.15f,.80f,3.45f,.48f,.46f,.41f); // bank abutment
            box(p,x,1.05f,0,.95f,.68f,3.45f,.57f,.54f,.48f);
        }
        for(float x:{-2.55f,2.55f}){
            box(p,x,1.15f,0,.88f,.50f,3.40f,.54f,.50f,.44f);
        }
        for(float x:{-1.65f,1.65f}){
            box(p,x,1.35f,0,.95f,.30f,3.40f,.52f,.49f,.43f);
        }
        // Constant top across the gap; joints suggest separate stone slabs.
        box(p,0,1.58f,0,8.45f,.38f,3.65f,.57f,.54f,.48f);
        for(int i=0;i<5;++i){
            const float x=-3.45f+i*1.72f;
            box(p,x,1.795f,0,1.64f,.075f,3.58f,
                .60f-(i%2)*.04f,.58f-(i%2)*.04f,.52f-(i%2)*.03f);
        }
        // Medieval low parapet, no modern footpath curb.
        for(float z:{-1.67f,1.67f}){
            box(p,0,2.05f,z,8.35f,.45f,.27f,.48f,.45f,.40f);
            for(float x:{-3.6f,0.f,3.6f})
                box(p,x,2.32f,z,.44f,.18f,.40f,.51f,.49f,.43f);
        }
    }else if(type==kTimberBridge){
        // 8 m timber foot/packhorse span. Individual planks and beams are
        // merged into ONE static draw, not dozens of runtime entities.
        for(float z:{-1.18f,1.18f}){
            box(p,0,1.28f,z,8.30f,.30f,.20f,.35f,.23f,.14f);
            box(p,0,2.18f,z,8.1f,.16f,.12f,.39f,.25f,.14f);
            for(float x:{-3.25f,0.f,3.25f}){
                box(p,x,1.68f,z,.20f,1.20f,.19f,.29f,.21f,.14f);
            }
            for(float x:{-3.55f,3.55f})
                box(p,x,.64f,z,.30f,1.28f,.35f,.29f,.21f,.14f);
        }
        for(int i=0;i<12;++i){
            const float x=-3.74f+i*.68f;
            box(p,x,1.50f,0,.65f,.14f,2.83f,
                .43f-.03f*(i%3),.30f-.02f*(i%3),.17f);
        }
        box(p,0,1.34f,0,8.35f,.12f,2.80f,.28f,.20f,.12f);
    }else if(type==kStoneFoundation){
        // Low foundation pad 7.2 x 6.1 m for existing 6 x 5 m buildings.
        // Top y=.48 m. Bottom y=-.49 m (partly embedded in ground).
        box(p,0,0,0,7.2f,.96f,6.1f,.46f,.44f,.40f);
        box(p,0,.43f,0,7.35f,.10f,6.25f,.57f,.54f,.48f);
        for(float z:{-2.83f,2.83f}){
            for(int i=0;i<4;++i){
                const float x=-2.72f+i*1.81f;
                box(p,x,.035f,z,1.78f,.74f,.30f,
                    .49f-(i%2)*.04f,.47f-(i%2)*.04f,.43f-(i%2)*.04f);
            }
        }
        for(float x:{-3.36f,3.36f}){
            for(int i=0;i<3;++i){
                const float z=-1.85f+i*1.85f;
                box(p,x,.035f,z,.30f,.74f,1.82f,
                    .49f-(i%2)*.04f,.47f-(i%2)*.04f,.43f-(i%2)*.04f);
            }
        }
    }
    if(p.size()>kMaxPartsPerModule)return {};
    return p;
}
Bounds moduleBounds(int type){
    Bounds b{
      std::numeric_limits<float>::infinity(),-std::numeric_limits<float>::infinity(),
      std::numeric_limits<float>::infinity(),-std::numeric_limits<float>::infinity(),
      std::numeric_limits<float>::infinity(),-std::numeric_limits<float>::infinity()
    };
    const auto parts=moduleParts(type);
    for(const auto&part:parts){
        b.minX=std::min(b.minX,part.x-part.sx*.5f);
        b.maxX=std::max(b.maxX,part.x+part.sx*.5f);
        b.minY=std::min(b.minY,part.y-part.sy*.5f);
        b.maxY=std::max(b.maxY,part.y+part.sy*.5f);
        b.minZ=std::min(b.minZ,part.z-part.sz*.5f);
        b.maxZ=std::max(b.maxZ,part.z+part.sz*.5f);
    }
    return b;
}
}} // namespace oe::medieval
