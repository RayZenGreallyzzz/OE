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
float foundationTop(int type) noexcept {
    switch(type){
        case kStoneFoundation: return kFoundationTop;
        case kTimberPileFoundation: return 1.28f;
        case kRuinFoundation: return .46f;
        case kSmallHutFoundation: return .40f;
        case kTowerFoundation: return .65f;
        default: return std::numeric_limits<float>::quiet_NaN();
    }
}
bool fitsStockBuildingFoundation(int type) noexcept {
    return type==kStoneFoundation||type==kTimberPileFoundation||
           type==kRuinFoundation||type==kTowerFoundation;
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
    }else if(type==kNarrowFootbridge){
        // A 6 m unrailed footbridge for a little stream, one-piece mesh.
        for(float z:{-0.64f,0.64f})
            box(p,0,.81f,z,6.4f,.21f,.19f,.39f,.26f,.17f);
        for(int i=0;i<10;++i){
            const float x=-2.78f+i*.61f;
            box(p,x,.99f,0,.55f,.16f,1.69f,
                .49f-(i%3)*.025f,.33f-(i%3)*.018f,.20f);
        }
        for(float x:{-2.85f,2.85f})for(float z:{-0.75f,0.75f})
            box(p,x,.37f,z,.28f,.74f,.28f,.32f,.24f,.17f);
    }else if(type==kRopeBridge){
        // Rustic rope-and-plank footbridge: rectangular rail approximations
        // instead of expensive dynamic cables or simulated cloth physics.
        for(float z:{-0.87f,0.87f}){
            box(p,0,1.13f,z,9.1f,.13f,.14f,.34f,.22f,.15f);
            box(p,0,2.14f,z,9.0f,.10f,.12f,.44f,.33f,.24f);
            for(float x:{-3.95f,0.f,3.95f})
                box(p,x,1.67f,z,.14f,1.15f,.15f,.37f,.28f,.19f);
            for(float x:{-4.35f,4.35f})
                box(p,x,.67f,z,.29f,1.35f,.26f,.30f,.20f,.12f);
        }
        for(int i=0;i<15;++i){
            float x=-4.24f+i*.605f;
            box(p,x,1.31f,0,.56f,.15f,2.00f,
                .49f-.025f*(i%4),.34f-.015f*(i%4),.19f);
        }
    }else if(type==kSteppingStoneFord){
        // Shallow irregular stepping stones. River/water placement is manual.
        for(int i=0;i<10;++i){
            const float x=-3.95f+i*.87f;
            const float z=(i%3-1)*.29f;
            const float top=.24f+.035f*(i%2);
            box(p,x,top*.5f,z,.83f,.36f+.035f*(i%2),
                1.50f+.10f*(i%3),.53f-.04f*(i%3),.51f-.04f*(i%3),.47f-.035f*(i%3));
        }
        for(float x:{-4.45f,4.45f})
            box(p,x,.11f,0,.93f,.22f,2.4f,.48f,.46f,.40f);
    }else if(type==kTimberPileFoundation){
        // Elevated timber floor for marshland and stilt villages, top 1.28 m.
        for(float x:{-3.15f,0.f,3.15f})for(float z:{-2.65f,0.f,2.65f}){
            box(p,x,.56f,z,.34f,1.22f,.34f,.34f,.23f,.15f);
        }
        for(float z:{-2.74f,2.74f})
            box(p,0,1.05f,z,7.35f,.30f,.25f,.37f,.25f,.16f);
        for(float x:{-3.20f,3.20f})
            box(p,x,1.05f,0,.30f,.30f,6.05f,.36f,.25f,.16f);
        box(p,0,1.20f,0,7.48f,.16f,6.18f,.47f,.32f,.18f);
        for(int i=0;i<4;++i)
            box(p,-2.79f+i*1.86f,1.292f,0,1.80f,.025f,6.00f,
                .52f-(i%2)*.035f,.36f-(i%2)*.024f,.21f);
    }else if(type==kRuinFoundation){
        // Weathered 7 x 6 m plinth and low broken perimeter, top .46 m.
        box(p,0,.02f,0,7.1f,.86f,6.00f,.39f,.39f,.37f);
        box(p,0,.427f,0,7.20f,.066f,6.09f,.49f,.47f,.42f);
        for(int i=0;i<6;++i){
            const float x=-2.89f+i*1.15f;
            const float h=.30f+.10f*(i%3);
            box(p,x,.46f+h*.5f,2.87f,1.10f,h,.40f,.45f,.44f,.39f);
        }
        for(int i=0;i<4;++i){
            const float z=-1.94f+i*1.27f;
            const float h=.22f+.13f*(i%3);
            box(p,3.29f,.46f+h*.5f,z,.39f,h,1.17f,.47f,.44f,.40f);
        }
        for(float x:{-3.15f,3.15f})
            box(p,x,.13f,-2.78f,.38f,.33f,.48f,.42f,.40f,.35f);
    }else if(type==kSmallHutFoundation){
        // Low pad for huts (4 x 4 m); not suitable as an auto-fitted
        // foundation beneath the 6 x 5 m stock tavern or forge.
        box(p,0,0,0,4.20f,.76f,4.20f,.49f,.46f,.40f);
        box(p,0,.34f,0,4.35f,.12f,4.35f,.59f,.56f,.49f);
        for(float z:{-1.9f,1.9f})
            for(float x:{-1.40f,0.f,1.40f})
                box(p,x,-.02f,z,1.34f,.45f,.28f,.48f,.44f,.40f);
    }else if(type==kTowerFoundation){
        // Heavy 6.8 m square keep/tower base, upper face y=.65.
        box(p,0,0,0,6.83f,1.30f,6.83f,.38f,.38f,.36f);
        box(p,0,.584f,0,6.98f,.132f,6.98f,.54f,.52f,.47f);
        for(float x:{-3.23f,3.23f})for(float z:{-3.23f,3.23f})
            box(p,x,.35f,z,.75f,.85f,.75f,.43f,.42f,.38f);
        for(float z:{-3.35f,3.35f})
            box(p,0,.17f,z,6.42f,.30f,.29f,.47f,.46f,.41f);
    }else if(type==kWideCartBridge){
        // About ten metres long, wider old stone cart crossing.
        for(float x:{-4.45f,4.45f})
            box(p,x,.46f,0,1.12f,.92f,4.45f,.49f,.47f,.41f);
        for(float x:{-3.45f,3.45f})
            box(p,x,1.07f,0,1.30f,.59f,4.45f,.50f,.49f,.43f);
        box(p,0,1.40f,0,10.20f,.38f,4.70f,.56f,.53f,.47f);
        for(int i=0;i<7;++i){
            const float x=-4.32f+i*1.44f;
            box(p,x,1.64f,0,1.35f,.10f,4.55f,
                .56f-.02f*(i%3),.54f-.02f*(i%3),.49f);
        }
        for(float z:{-2.16f,2.16f}){
            box(p,0,1.96f,z,10.1f,.44f,.27f,.44f,.43f,.39f);
            for(float x:{-4.1f,0.f,4.1f})
                box(p,x,2.27f,z,.40f,.19f,.36f,.48f,.46f,.42f);
        }
    }else if(type==kSwampCauseway){
        // Boardwalk on driven piles, one static chunk, can tile lengthwise.
        for(float z:{-1.19f,1.19f})
            box(p,0,.81f,z,8.60f,.23f,.21f,.34f,.23f,.15f);
        for(int i=0;i<12;++i){
            const float x=-3.96f+i*.72f;
            box(p,x,1.05f,0,.67f,.15f,3.04f,
                .44f-.018f*(i%3),.30f-.015f*(i%3),.18f);
        }
        for(float x:{-3.80f,0.f,3.80f})for(float z:{-1.18f,1.18f}){
            box(p,x,.39f,z,.33f,.85f,.32f,.28f,.21f,.14f);
        }
        box(p,0,.91f,0,8.55f,.09f,2.85f,.31f,.22f,.15f);
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
