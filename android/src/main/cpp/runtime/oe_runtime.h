#pragma once
// OE Game Runtime Core 0.7.0 — portable C++17, no Android/Java/GLES/editor API.
#include <cstddef>
#include <cstdint>
#include <limits>
#include <optional>
#include <string>
#include <vector>

namespace oe { namespace runtime {

constexpr double kFixedTimeStep = 1.0 / 60.0; // simulation time in seconds
constexpr std::size_t kMaxRuntimeEntities = 4096;
constexpr std::size_t kMaxStepsPerFrame = 8;

struct EntityId {
    std::uint32_t slot = std::numeric_limits<std::uint32_t>::max();
    std::uint32_t generation = 0;
    bool operator==(const EntityId& other) const noexcept {
        return slot == other.slot && generation == other.generation;
    }
    bool operator!=(const EntityId& other) const noexcept { return !(*this == other); }
};

struct Vec3 { double x=0.0, y=0.0, z=0.0; };
struct Quaternion { double x=0.0, y=0.0, z=0.0, w=1.0; };
struct Transform {
    Vec3 position{};
    Quaternion orientation{};
    Vec3 scale{1.0, 1.0, 1.0};
};
struct MeshRenderer {
    std::string assetId; // lookup key only; no GPU allocation in runtime core
    bool visible=true;
};
struct Velocity { Vec3 metresPerSecond{}; };
struct Camera {
    double verticalFovDegrees=60.0;
    double nearClip=0.05;
    double farClip=1000.0;
};
// Data-only contract. Collision detection/resolution belongs to a future system.
struct Collider {
    enum class Shape { Box, Sphere } shape=Shape::Box;
    Vec3 halfExtents{0.5, 0.5, 0.5};
    double radius=0.5;
    bool isTrigger=false;
};
struct EntitySnapshot {
    EntityId id{};
    std::string name;
    Transform transform{};
    std::optional<MeshRenderer> renderer;
    std::optional<Velocity> velocity;
    std::optional<Camera> camera;
    std::optional<Collider> collider;
};
// Portable scene description supplied by an editor, game loader or network tool.
struct SceneItem {
    std::string name;
    Transform transform{};
    std::string meshAsset; // Empty means object has no renderer.
};
struct StepResult {
    std::size_t steps=0;
    std::uint64_t totalTicks=0;
    double interpolationAlpha=0.0;
    double droppedSeconds=0.0;
};

class World final {
public:
    explicit World(std::size_t capacity=kMaxRuntimeEntities);
    World(const World&)=delete;
    World& operator=(const World&)=delete;

    EntityId create(const std::string& name={});
    // Validates first; either all scene entities are replaced or none are.
    // Loading resets the simulation clock and invalidates all previous IDs.
    bool loadStaticScene(const std::vector<SceneItem>& items);
    bool alive(EntityId id) const noexcept;
    bool destroy(EntityId id) noexcept;
    bool setName(EntityId id,const std::string& name);
    const std::string* name(EntityId id) const noexcept;

    bool setTransform(EntityId id,const Transform& transform) noexcept;
    const Transform* transform(EntityId id) const noexcept;
    bool setRenderer(EntityId id,const MeshRenderer& renderer);
    const MeshRenderer* renderer(EntityId id) const noexcept;
    bool setVelocity(EntityId id,const Velocity& velocity) noexcept;
    const Velocity* velocity(EntityId id) const noexcept;
    bool setCamera(EntityId id,const Camera& camera) noexcept;
    const Camera* camera(EntityId id) const noexcept;
    bool setCollider(EntityId id,const Collider& collider) noexcept;
    const Collider* collider(EntityId id) const noexcept;
    bool removeRenderer(EntityId id) noexcept;
    bool removeVelocity(EntityId id) noexcept;
    bool removeCamera(EntityId id) noexcept;
    bool removeCollider(EntityId id) noexcept;

    // One deterministic simulation tick moves velocity components; no physics.
    // Advance clamps frame delta to 250 ms and avoids a backlog/death spiral.
    StepResult advance(double frameSeconds) noexcept;
    // Explicit fixed ticks useful for deterministic headless unit tests/replay.
    bool stepTicks(std::size_t count) noexcept;
    void setPaused(bool paused) noexcept;
    bool paused() const noexcept {return paused_;}
    std::uint64_t ticks() const noexcept {return ticks_;}
    double elapsedSeconds() const noexcept {return static_cast<double>(ticks_)*kFixedTimeStep;}
    std::size_t entityCount() const noexcept {return liveCount_;}
    std::size_t capacity() const noexcept {return capacity_;}
    std::vector<EntitySnapshot> snapshot() const;
    void clear() noexcept; // invalidates all existing IDs

private:
    struct Slot {
        std::uint32_t generation=1;
        bool alive=false;
        std::string name;
        Transform transform{};
        std::optional<MeshRenderer> renderer;
        std::optional<Velocity> velocity;
        std::optional<Camera> camera;
        std::optional<Collider> collider;
    };
    Slot* find(EntityId id) noexcept;
    const Slot* find(EntityId id) const noexcept;
    void singleTick() noexcept;

    std::vector<Slot> slots_;
    std::vector<std::uint32_t> freeSlots_;
    std::size_t capacity_;
    std::size_t liveCount_=0;
    std::uint64_t ticks_=0;
    double accumulator_=0.0;
    bool paused_=false;
    std::uint32_t generationSerial_=1;
};

}} // namespace oe::runtime
