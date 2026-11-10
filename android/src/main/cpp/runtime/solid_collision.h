#pragma once
// OE R5.9a: bounded CPU-only solid boxes for the Play-only character.
// OBB footprints rotate with authoring entities; no editing or GPU dependency.
#include "oe_runtime.h"
#include <cstddef>
#include <vector>

namespace oe { namespace runtime {
constexpr std::size_t kMaxPlaySolids=300;
constexpr double kPlayCharacterRadius=.28;
constexpr double kPlayCharacterHeight=1.7;

struct SolidBox {
 Vec3 center{};
 Vec3 halfExtents{.5,.5,.5};
 double yawRadians=0.; // positive = GLES renderer rotateY convention
};

bool validSolidBox(const SolidBox&box) noexcept;
bool solidOverlapsBody(const std::vector<SolidBox>&solids,
                       Vec3 feet,double radius=kPlayCharacterRadius,
                       double height=kPlayCharacterHeight) noexcept;
// Conservative swept horizontal circle against oriented boxes; vertical
// overlap is based on the body segment above the player's foot Y.
// Does not attempt stairs, dynamic rigid bodies, triangle walls or triggers.
bool solidBlocksStep(const std::vector<SolidBox>&solids,
                     Vec3 fromFeet,Vec3 toFeet,
                     double radius=kPlayCharacterRadius,
                     double height=kPlayCharacterHeight) noexcept;
}} // namespace oe::runtime
