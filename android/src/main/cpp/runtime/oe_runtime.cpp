#include "oe_runtime.h"
#include <algorithm>
#include <cmath>
#include <utility>

namespace oe { namespace runtime {
namespace {
bool finite(double x) noexcept {return std::isfinite(x);}
bool validVec(const Vec3& v) noexcept {
    return finite(v.x)&&finite(v.y)&&finite(v.z)&&
        std::abs(v.x)<=1.0e9&&std::abs(v.y)<=1.0e9&&std::abs(v.z)<=1.0e9;
}
bool validTransform(const Transform& t) noexcept {
    const Quaternion& q=t.orientation;
    const double length2=q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w;
    return validVec(t.position)&&validVec(t.scale)&&
        t.scale.x>0.0&&t.scale.y>0.0&&t.scale.z>0.0&&
        t.scale.x<=1.0e6&&t.scale.y<=1.0e6&&t.scale.z<=1.0e6&&
        finite(q.x)&&finite(q.y)&&finite(q.z)&&finite(q.w)&&
        finite(length2)&&length2>1.0e-12&&length2<1.0e12;
}
bool validCamera(const Camera& c) noexcept {
    return finite(c.verticalFovDegrees)&&c.verticalFovDegrees>=1.0&&
        c.verticalFovDegrees<=179.0&&finite(c.nearClip)&&
        finite(c.farClip)&&c.nearClip>=0.001&&c.farClip>c.nearClip&&
        c.farClip<=1.0e7;
}
bool validCollider(const Collider& c) noexcept {
    return validVec(c.halfExtents)&&finite(c.radius)&&
        c.halfExtents.x>0&&c.halfExtents.y>0&&c.halfExtents.z>0&&
        c.radius>0&&c.radius<=1.0e6;
}
constexpr std::size_t kMaxEntityNameBytes=128;
constexpr std::size_t kMaxAssetIdBytes=256;
constexpr double kMaximumFrameSeconds=0.25;
}
World::World(std::size_t capacity)
    :capacity_(std::min<std::size_t>(capacity,kMaxRuntimeEntities)){
    slots_.reserve(capacity_);
    freeSlots_.reserve(capacity_);
}
World::Slot* World::find(EntityId id) noexcept {
    if(id.slot>=slots_.size())return nullptr;
    Slot& slot=slots_[id.slot];
    return slot.alive&&slot.generation==id.generation?&slot:nullptr;
}
const World::Slot* World::find(EntityId id) const noexcept {
    if(id.slot>=slots_.size())return nullptr;
    const Slot& slot=slots_[id.slot];
    return slot.alive&&slot.generation==id.generation?&slot:nullptr;
}
EntityId World::create(const std::string& name){
    if(liveCount_>=capacity_||name.size()>kMaxEntityNameBytes)return {};
    std::uint32_t index;
    if(!freeSlots_.empty()){
        index=freeSlots_.back();freeSlots_.pop_back();
    }else{
        index=static_cast<std::uint32_t>(slots_.size());
        slots_.emplace_back();
    }
    Slot& slot=slots_[index];
    slot.generation=generationSerial_++;
    if(generationSerial_==0)generationSerial_=1;
    slot.alive=true;
    slot.name=name;
    slot.transform=Transform{};
    slot.renderer.reset();
    slot.velocity.reset();
    slot.camera.reset();
    slot.collider.reset();
    ++liveCount_;
    return {index,slot.generation};
}
bool World::loadStaticScene(const std::vector<SceneItem>& items){
    if(items.size()>capacity_)return false;
    // All validation and allocations happen in temporary state.
    std::vector<Slot> nextSlots;
    nextSlots.reserve(items.size());
    std::uint32_t nextGeneration=generationSerial_;
    for(const SceneItem& item:items){
        if(item.name.size()>kMaxEntityNameBytes||
           item.meshAsset.size()>kMaxAssetIdBytes||
           !validTransform(item.transform))return false;
        Slot slot;
        slot.alive=true;
        slot.generation=nextGeneration++;
        if(nextGeneration==0)nextGeneration=1;
        slot.name=item.name;
        slot.transform=item.transform;
        if(!item.meshAsset.empty())slot.renderer=MeshRenderer{item.meshAsset,true};
        nextSlots.push_back(std::move(slot));
    }
    slots_.swap(nextSlots);
    freeSlots_.clear();
    generationSerial_=nextGeneration;
    liveCount_=slots_.size();
    ticks_=0;accumulator_=0.0;paused_=false;
    return true;
}
bool World::alive(EntityId id) const noexcept{return find(id)!=nullptr;}
bool World::destroy(EntityId id) noexcept{
    Slot* slot=find(id);if(!slot)return false;
    slot->alive=false;slot->name.clear();
    slot->renderer.reset();slot->velocity.reset();slot->camera.reset();
    slot->collider.reset();
    // Handle ID reuse without accepting handles from the destroyed generation.
    ++slot->generation;
    if(slot->generation==0)slot->generation=1;
    freeSlots_.push_back(id.slot);
    --liveCount_;
    return true;
}
bool World::setName(EntityId id,const std::string& name){
    Slot* slot=find(id);
    if(!slot||name.size()>kMaxEntityNameBytes)return false;
    slot->name=name;return true;
}
const std::string* World::name(EntityId id) const noexcept{
    const Slot* slot=find(id);return slot?&slot->name:nullptr;
}
bool World::setTransform(EntityId id,const Transform& t) noexcept{
    Slot* slot=find(id);
    if(!slot||!validTransform(t))return false;
    slot->transform=t;return true;
}
const Transform* World::transform(EntityId id) const noexcept{
    const Slot* slot=find(id);return slot?&slot->transform:nullptr;
}
bool World::setRenderer(EntityId id,const MeshRenderer& renderer){
    Slot* slot=find(id);
    if(!slot||renderer.assetId.empty()||renderer.assetId.size()>kMaxAssetIdBytes)
        return false;
    slot->renderer=renderer;return true;
}
const MeshRenderer* World::renderer(EntityId id) const noexcept{
    const Slot* slot=find(id);
    return slot&&slot->renderer?&*slot->renderer:nullptr;
}
bool World::setVelocity(EntityId id,const Velocity& velocity) noexcept{
    Slot* slot=find(id);
    if(!slot||!validVec(velocity.metresPerSecond))return false;
    slot->velocity=velocity;return true;
}
const Velocity* World::velocity(EntityId id) const noexcept{
    const Slot* slot=find(id);
    return slot&&slot->velocity?&*slot->velocity:nullptr;
}
bool World::setCamera(EntityId id,const Camera& camera) noexcept{
    Slot* slot=find(id);
    if(!slot||!validCamera(camera))return false;
    slot->camera=camera;return true;
}
const Camera* World::camera(EntityId id) const noexcept{
    const Slot* slot=find(id);
    return slot&&slot->camera?&*slot->camera:nullptr;
}
bool World::setCollider(EntityId id,const Collider& collider) noexcept{
    Slot* slot=find(id);
    if(!slot||!validCollider(collider))return false;
    slot->collider=collider;return true;
}
const Collider* World::collider(EntityId id) const noexcept{
    const Slot* slot=find(id);
    return slot&&slot->collider?&*slot->collider:nullptr;
}
bool World::removeRenderer(EntityId id) noexcept{
    Slot* slot=find(id);if(!slot)return false;slot->renderer.reset();return true;
}
bool World::removeVelocity(EntityId id) noexcept{
    Slot* slot=find(id);if(!slot)return false;slot->velocity.reset();return true;
}
bool World::removeCamera(EntityId id) noexcept{
    Slot* slot=find(id);if(!slot)return false;slot->camera.reset();return true;
}
bool World::removeCollider(EntityId id) noexcept{
    Slot* slot=find(id);if(!slot)return false;slot->collider.reset();return true;
}
void World::singleTick() noexcept{
    for(Slot& slot:slots_){
        if(!slot.alive||!slot.velocity)continue;
        const Vec3& v=slot.velocity->metresPerSecond;
        Transform next=slot.transform;
        next.position.x+=v.x*kFixedTimeStep;
        next.position.y+=v.y*kFixedTimeStep;
        next.position.z+=v.z*kFixedTimeStep;
        if(validTransform(next))slot.transform=next;
        // Invalid position is never propagated to the renderer.
    }
    ++ticks_;
}
bool World::stepTicks(std::size_t count) noexcept{
    if(paused_||count>600)return false;
    for(std::size_t n=0;n<count;n++)singleTick();
    return true;
}
StepResult World::advance(double frameSeconds) noexcept{
    StepResult result{};
    result.totalTicks=ticks_;
    if(paused_||!finite(frameSeconds)||frameSeconds<0.0)return result;
    const double elapsed=std::min(frameSeconds,kMaximumFrameSeconds);
    result.droppedSeconds=std::max(0.0,frameSeconds-elapsed);
    accumulator_+=elapsed;
    constexpr double eps=1.0e-12;
    while(result.steps<kMaxStepsPerFrame&&accumulator_+eps>=kFixedTimeStep){
        singleTick();
        accumulator_-=kFixedTimeStep;
        if(accumulator_<0.0)accumulator_=0.0;
        ++result.steps;
    }
    if(accumulator_+eps>=kFixedTimeStep){
        // Skip old frames, never fast-forward gameplay by unbounded CPU work.
        const double remainder=std::fmod(accumulator_,kFixedTimeStep);
        result.droppedSeconds+=accumulator_-remainder;
        accumulator_=remainder;
    }
    result.interpolationAlpha=std::max(0.0,std::min(1.0,accumulator_/kFixedTimeStep));
    result.totalTicks=ticks_;
    return result;
}
void World::setPaused(bool paused) noexcept{
    paused_=paused;
    if(paused)accumulator_=0.0; // resume must not catch up paused wall time
}
std::vector<EntitySnapshot> World::snapshot() const{
    std::vector<EntitySnapshot> out;
    out.reserve(liveCount_);
    for(std::size_t i=0;i<slots_.size();i++){
        const Slot& slot=slots_[i];if(!slot.alive)continue;
        EntitySnapshot copy;
        copy.id={static_cast<std::uint32_t>(i),slot.generation};
        copy.name=slot.name;
        copy.transform=slot.transform;
        copy.renderer=slot.renderer;
        copy.velocity=slot.velocity;
        copy.camera=slot.camera;
        copy.collider=slot.collider;
        out.push_back(std::move(copy));
    }
    return out;
}
void World::clear() noexcept{
    for(std::size_t i=0;i<slots_.size();i++){
        Slot& slot=slots_[i];
        slot.alive=false;slot.name.clear();
        slot.renderer.reset();slot.velocity.reset();slot.camera.reset();
        slot.collider.reset();slot.transform=Transform{};
        ++slot.generation;
        if(slot.generation==0)slot.generation=1;
    }
    freeSlots_.clear();
    for(std::size_t i=slots_.size();i>0;i--)
        freeSlots_.push_back(static_cast<std::uint32_t>(i-1));
    liveCount_=0;ticks_=0;accumulator_=0.0;paused_=false;
}
}} // namespace oe::runtime
