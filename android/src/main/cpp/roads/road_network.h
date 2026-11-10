#pragma once
// OE Road & Terrain Foundation: portable road data + terrain-conforming mesh.
// No Android, Java, GLES, editor, or game-runtime dependency.
#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace oe { namespace roads {

constexpr std::size_t kMaxRoads=256;
constexpr std::size_t kMaxControlPoints=4096;
constexpr std::size_t kMaxMeshSamples=16000;
constexpr double kMinRoadWidth=0.25;
constexpr double kMaxRoadWidth=32.0;
constexpr double kDefaultMeshStep=2.0;

struct Point { double x=0.0, z=0.0; };

struct Road {
    std::uint64_t id=0; // durable scene ID, never a runtime entity handle
    std::string name;
    std::string material; // logical asset reference; no GPU resource ownership
    double widthMetres=6.0;
    std::vector<Point> controlPoints; // editable XZ centerline, metres
    double tileMetres=4.0; // texture repeat every 4m along/across road; scene v8
};

struct Vertex {
    float x=0.0f, y=0.0f, z=0.0f;
    float u=0.0f, v=0.0f; // metre-based repeat coordinates for a road material
};

struct SurfaceMesh { std::vector<Vertex> triangles; };
using TerrainHeight = std::function<double(double x,double z)>;

bool validRoad(const Road& road) noexcept;

// Builds a ribbon from editable control points; output unchanged on failure.
// Each pair of edges samples the same terrain height provider used by the editor.
// Road junctions, collision meshes and navigation graphs are NOT generated here.
bool buildSurfaceMesh(const Road& road,const TerrainHeight& height,
                      SurfaceMesh& output,double samplingStepMetres=kDefaultMeshStep);

class Network final {
public:
    // 0 is invalid. IDs are preserved across replaceAll/save/load.
    std::uint64_t create(std::string name,std::string material,
                         double widthMetres,std::vector<Point> points);
    bool update(const Road& updated);
    bool remove(std::uint64_t id) noexcept;
    const Road* find(std::uint64_t id) const noexcept;
    const std::vector<Road>& all() const noexcept { return roads_; }
    // Atomic scene replacement: invalid input does not change live roads.
    bool replaceAll(const std::vector<Road>& incoming);
    void clear() noexcept { roads_.clear(); }
private:
    std::vector<Road> roads_;
    std::uint64_t nextId_=1;
};

}} // namespace oe::roads
