#pragma once
// OE Medieval Modules R5.2a: tiny, shared, static primitives.
// Pure C++17; no Android, rendering API, terrain mutation or physics.
#include <cstddef>
#include <vector>
namespace oe { namespace medieval {
constexpr int kStoneBridge=17;
constexpr int kTimberBridge=18;
constexpr int kStoneFoundation=19;
constexpr int kNarrowFootbridge=20;
constexpr int kRopeBridge=21;
constexpr int kSteppingStoneFord=22;
constexpr int kTimberPileFoundation=23;
constexpr int kRuinFoundation=24;
constexpr int kSmallHutFoundation=25;
constexpr int kTowerFoundation=26;
constexpr int kWideCartBridge=27;
constexpr int kSwampCauseway=28;
constexpr int kLastModuleType=kSwampCauseway;
constexpr std::size_t kMaxPartsPerModule=40;
// Foundation top surface (local y, before object scale), for automatic placement.
constexpr float kFoundationTop=.48f;
// World-space local top of each foundation variant, used to align underneath houses.
float foundationTop(int type) noexcept;
bool fitsStockBuildingFoundation(int type) noexcept;
struct Part {
    float x,y,z,sx,sy,sz; // box center / full dimensions in metres
    float r,g,b; // neutral vertex colour (no textures required)
};
struct Bounds { float minX,maxX,minY,maxY,minZ,maxZ; };
// 12 stable module kinds 17–28. Geometry is reused for every instance;
// one pre-baked GPU mesh per kind, then one draw call per visible instance.
bool validModule(int type) noexcept;
std::vector<Part> moduleParts(int type);
Bounds moduleBounds(int type);
}} // namespace oe::medieval
