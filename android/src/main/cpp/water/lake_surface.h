#pragma once
// R5.3: bounded water surfaces over sculpted heightfield. C++17, no GLES.
#include <cstddef>
#include <functional>
#include <vector>
namespace oe { namespace water {
constexpr std::size_t kMaxLakes=8;
constexpr float kMinLakeRadius=8.f,kMaxLakeRadius=160.f;
constexpr float kMinLakeLevel=-24.f,kMaxLakeLevel=160.f;
constexpr std::size_t kMaxLakeTriangles=50000;
struct Lake { float x=0,z=0,radius=64,level=0; };
struct WaterVertex { float x=0,y=0,z=0,r=0,g=0,b=0; };
struct LakeSurface {std::vector<WaterVertex> triangles;std::size_t lakeCount=0;};
// Caller input is validated atomically, and result is unchanged on error.
// Shoreline triangulation clips against the sampled terrain height and the
// authored radial boundary. No water is drawn where terrain is above the level.
using TerrainHeight=std::function<double(double,double)>;
bool validLake(const Lake&lake,float worldHalf) noexcept;
bool buildLakeSurface(const std::vector<Lake>&lakes,float worldHalf,
                      const TerrainHeight& terrain,LakeSurface&output);
}} // namespace oe::water
