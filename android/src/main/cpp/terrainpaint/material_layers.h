#pragma once
// OE R4.1: editor-only, independent material-weight masks per terrain tile.
// Portable C++17. No OpenGL/Android/Game Runtime dependencies.
#include "paint_tiles.h"
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace oe { namespace terrainpaint {

constexpr std::size_t kMaxMaterialTiles=32;
constexpr std::size_t kMaxMaterialsPerTile=4;
constexpr std::size_t kMaterialMaskBytes=kResolution*kResolution; // 128x128 alpha
constexpr std::size_t kMaxMaterialMaskBytes=
    kMaxMaterialTiles*kMaxMaterialsPerTile*kMaterialMaskBytes; // <= 2 MiB

struct MaterialMask {
    std::string material; // canonical tx_<sha24>.png / .jpg
    std::vector<std::uint8_t> weights; // opaque weight at each 0.5-metre texel
    std::uint32_t revision=1; // disposable GL upload marker, never serialized
};

struct MaterialTile {
    int x=0,z=0; // signed 64-metre terrain tiles
    std::vector<MaterialMask> materials; // max 4 per tile
};

bool validMaterialKey(const std::string& material) noexcept;

class MaterialCanvas final {
public:
    // Interpolates one stroke segment into one material mask. When erase is
    // true ONLY the named material is affected. No partial changes on failure.
    bool stroke(double x0,double z0,double x1,double z1,
                double radius,double opacity,const std::string& material,
                bool erase,int worldSize);
    // Atomic replacement, rejects duplicate tiles/materials and invalid masks.
    bool replaceAll(std::vector<MaterialTile> incoming,int worldSize);
    const std::vector<MaterialTile>& tiles() const noexcept{return tiles_;}
    void clear() noexcept{tiles_.clear();}
private:
    std::vector<MaterialTile> tiles_;
};

}} // namespace oe::terrainpaint
