#pragma once
// OE 0.7.8: bounded deterministic road-surface palette for low-memory Android.
// Surface IDs are stable project data, not GL texture handles.
#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>

namespace oe { namespace roads {
constexpr int kAsphalt=0;      // Backward-compatible default for OE 0.7.x roads
constexpr int kCobblestone=1;  // Realistic medieval town streets
constexpr int kDirt=2;         // Mud and worn cart paths
constexpr int kGravel=3;       // Village edges and fortress access
constexpr int kAncientSlate=4; // Subtle dark-fantasy stone, no emissive effects
constexpr std::size_t kDetailTilesTotal=14000;

struct RoadRgb {float r=0.f,g=0.f,b=0.f;};
struct SurfacePalette {
    RoadRgb base{},detail{},seam{};
    bool hasPavers=false,hasRuts=false;
};
inline bool validSurface(int material) noexcept {
    return material>=kAsphalt&&material<=kAncientSlate;
}
inline const char* surfaceName(int material) noexcept {
    switch(material) {
    case kAsphalt:return "Асфальт";
    case kCobblestone:return "Брусчатка";
    case kDirt:return "Грунт";
    case kGravel:return "Гравий";
    case kAncientSlate:return "Древние тёмные плиты";
    default:return "Неизвестное покрытие";
    }
}
inline SurfacePalette surfacePalette(int material) noexcept {
    switch(material) {
    case kCobblestone: return {{.29f,.29f,.27f},{.49f,.48f,.43f},
                               {.21f,.20f,.19f},true,false};
    case kDirt: return {{.31f,.225f,.145f},{.38f,.29f,.19f},
                        {.20f,.145f,.09f},false,true};
    case kGravel: return {{.40f,.375f,.33f},{.55f,.52f,.46f},
                          {.31f,.29f,.26f},false,false};
    case kAncientSlate: return {{.13f,.165f,.19f},{.29f,.335f,.36f},
                                {.09f,.11f,.14f},true,false};
    default: return {{.22f,.23f,.24f},{.22f,.23f,.24f},
                      {.22f,.23f,.24f},false,false};
    }
}
// Coordinate-independent seeded tile shades: stable across EGL/context restore,
// project re-opening and camera movement; zero random-number allocations.
inline std::uint32_t tileHash(int roadId,int segment,int column) noexcept {
    std::uint32_t x=static_cast<std::uint32_t>(roadId)*0x9e3779b9u;
    x^=static_cast<std::uint32_t>(segment)*0x85ebca6bu;
    x^=static_cast<std::uint32_t>(column)*0xc2b2ae35u;
    x^=x>>16;x*=0x7feb352du;x^=x>>15;x*=0x846ca68bu;x^=x>>16;
    return x;
}
inline RoadRgb tileColor(int material,int roadId,int segment,int column) noexcept {
    const auto palette=surfacePalette(material);
    const std::uint32_t h=tileHash(roadId,segment,column);
    const float noise=(static_cast<float>(h%101u)/100.f-.5f)*.22f;
    const float cold=material==kAncientSlate&&h%19u==0u?.075f:0.f;
    const auto bound=[](float v){return std::max(.02f,std::min(.95f,v));};
    return {bound(palette.detail.r+noise*.82f),
            bound(palette.detail.g+noise*.85f+cold*.2f),
            bound(palette.detail.b+noise*.83f+cold)};
}
inline int detailColumns(float width) noexcept {
    if(!std::isfinite(width)||width<2.f)return 0;
    return std::max(1,std::min(15,static_cast<int>((width-.35f)/1.15f)));
}
inline int detailStride(std::size_t targetRows,int columns,
                        std::size_t budget) noexcept {
    if(columns<=0||budget==0)return 0;
    const std::size_t maxRows=std::max<std::size_t>(1,budget/
                                          static_cast<std::size_t>(columns));
    const std::size_t stride=std::max<std::size_t>(
        1,(targetRows+maxRows-1)/maxRows);
    return static_cast<int>(std::min<std::size_t>(stride,1000000u));
}
}} // namespace oe::roads
