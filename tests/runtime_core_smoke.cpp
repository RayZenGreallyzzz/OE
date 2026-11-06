// OE portable game-runtime smoke: compile with host g++, no Android SDK.
#include "../android/src/main/cpp/runtime/oe_runtime.h"
#include <cassert>
#include <cmath>
#include <iostream>
#include <limits>
#include <vector>
using namespace oe::runtime;
static bool equal(double a,double b){return std::abs(a-b)<1e-9;}
int main(){
    World game(4);
    assert(game.capacity()==4&&game.entityCount()==0);
    auto hero=game.create("hero");
    assert(game.alive(hero));
    assert(game.transform(hero)!=nullptr);
    assert(game.name(hero)&&*game.name(hero)=="hero");
    auto floor=game.create("floor");
    assert(game.alive(floor)&&game.entityCount()==2);
    assert(game.setRenderer(floor,MeshRenderer{"builtin:floor",true}));
    assert(game.renderer(floor)->assetId=="builtin:floor");
    assert(!game.setRenderer(floor,MeshRenderer{"",true}));
    Transform origin;origin.position={12.001,0.003,-8.007};
    origin.scale={1.0,2.5,1.0};
    assert(game.setTransform(hero,origin));
    assert(equal(game.transform(hero)->position.x,12.001));
    assert(!game.setTransform(hero,Transform{{0,0,0},{0,0,0,0},{1,1,1}}));
    assert(equal(game.transform(hero)->position.x,12.001));
    assert(!game.setVelocity(hero,Velocity{{std::numeric_limits<double>::quiet_NaN(),0,0}}));
    assert(game.setVelocity(hero,Velocity{{1.5,0,-0.5}}));
    assert(game.setCamera(hero,Camera{70.0,0.1,2000.0}));
    assert(!game.setCamera(hero,Camera{180.0,0.1,2000.0}));
    assert(game.setCollider(floor,Collider{}));
    assert(game.collider(floor)!=nullptr);
    assert(game.removeCollider(floor)&&game.collider(floor)==nullptr);
    // 30+30 frames at 60Hz must match 60 single fixed ticks.
    for(int i=0;i<60;i++){
        StepResult stats=game.advance(kFixedTimeStep);
        assert(stats.steps==1);
    }
    assert(game.ticks()==60);
    assert(equal(game.transform(hero)->position.x,13.501));
    assert(equal(game.transform(hero)->position.z,-8.507));
    assert(equal(game.elapsedSeconds(),1.0));
    auto beforePause=game.ticks();
    game.setPaused(true);
    assert(game.advance(10.0).steps==0&&game.ticks()==beforePause);
    assert(!game.stepTicks(1));
    game.setPaused(false);
    assert(game.advance(0.0).steps==0);
    assert(game.stepTicks(60));
    assert(game.ticks()==120);
    assert(equal(game.transform(hero)->position.x,15.001));
    assert(!game.stepTicks(601));
    // A large device hitch is bounded to 8 ticks, rather than a CPU spiral.
    StepResult hitch=game.advance(3.0);
    assert(hitch.steps<=kMaxStepsPerFrame&&hitch.droppedSeconds>2.0);
    assert(hitch.interpolationAlpha>=0&&hitch.interpolationAlpha<=1);
    assert(game.advance(-1.0).steps==0);
    assert(game.advance(std::numeric_limits<double>::infinity()).steps==0);
    assert(game.snapshot().size()==2);
    // Reuse of destroyed slots must never revive an old entity handle.
    assert(game.destroy(floor));
    assert(!game.alive(floor)&&!game.destroy(floor));
    auto other=game.create("other");
    assert(other.slot==floor.slot&&other.generation!=floor.generation);
    assert(!game.setRenderer(floor,MeshRenderer{"bad",true}));
    // Validation is atomic: malformed scene cannot erase an existing level.
    std::vector<SceneItem> bad={{"bad",origin,"builtin:test"}};
    bad[0].transform.scale.x=-1;
    assert(!game.loadStaticScene(bad));
    assert(game.entityCount()==2&&game.alive(hero));
    std::vector<SceneItem> scene;
    SceneItem a;a.name="castle";a.meshAsset="builtin:castle";
    a.transform.position={0.001,5.0,-0.007};
    SceneItem b;b.name="camera";
    b.transform.position={4.0,3.0,2.0};
    scene.push_back(a);scene.push_back(b);
    assert(game.loadStaticScene(scene));
    assert(game.entityCount()==2&&game.ticks()==0);
    assert(!game.alive(hero)&&!game.alive(other)); // stale IDs
    auto entries=game.snapshot();
    assert(entries.size()==2&&entries[0].name=="castle");
    assert(entries[0].renderer&&entries[0].renderer->assetId=="builtin:castle");
    assert(!entries[1].renderer&&equal(entries[0].transform.position.x,.001));
    assert(game.setVelocity(entries[0].id,Velocity{{.5,0,0}}));
    assert(game.stepTicks(120));
    assert(equal(game.transform(entries[0].id)->position.x,1.001));
    assert(equal(entries[0].transform.position.x,.001)); // snapshot detached
    game.clear();
    assert(game.entityCount()==0&&game.ticks()==0);
    assert(!game.alive(entries[0].id));
    auto fresh=game.create("after-clear");
    assert(game.alive(fresh)&&fresh!=entries[0].id);
    World zero(0);assert(zero.entityCount()==0&&zero.capacity()==0);
    auto invalid=zero.create("fail");
    assert(!zero.alive(invalid));
    std::cout<<"OE_RUNTIME_CORE_OK: 60Hz, stable IDs, atomic scenes, pause, snapshots, components\n";
    return 0;
}
