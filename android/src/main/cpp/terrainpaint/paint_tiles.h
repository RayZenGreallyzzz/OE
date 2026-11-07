#pragma once
// OE R4: portable bounded tile-based terrain colour overlay.
// No OpenGL, Android, Java, or Runtime Core dependency.
#include <array>
#include <cstdint>
#include <vector>

namespace oe { namespace terrainpaint {
constexpr int kTileMetres=64;
constexpr int kResolution=128; // 0.5 metre / pixel, fixed authoring grid
constexpr int kBytesPerTile=kResolution*kResolution*4;
constexpr std::size_t kMaxTiles=32; // <= 2 MiB colour data on 4 GiB tablets
struct Tile {
 int x=0,z=0;  // signed 64m tile coordinates
 std::vector<std::uint8_t> pixels; // RGBA composited image, row 0 at +Z
 std::uint32_t revision=1; // never serialized, for incremental GPU refresh
};
struct Image {
 int width=0,height=0;
 const std::uint8_t* rgba=nullptr; // decoded source, borrowed by caller
};
class Canvas final {
public:
 bool paint(double x0,double z0,double x1,double z1,double radius,
            double opacity,bool erase,const Image& source,int worldSize);
 bool replaceAll(std::vector<Tile> incoming,int worldSize);
 const std::vector<Tile>& tiles() const noexcept { return tiles_; }
 void clear() noexcept { tiles_.clear(); }
private:
 std::vector<Tile> tiles_;
};
}} // namespace oe::terrainpaint
