#pragma once
// OE R5.8a: bounded, GLES-independent visible mesh for physically safe
// bank-to-deck seams. Never mutates authoring data or scene archives.
#include "traversal_graph.h"
#include <cstdint>
#include <vector>

namespace oe { namespace roads {
struct BankVisualPart {
 std::uint64_t materialRoadId=0; // road supplying PNG/JPG or builtin material
 SurfaceMesh mesh; // two triangles, already lifted above collision geometry
};

// Only full two-bank bridge approach surfaces become rendered mesh. Output is
// atomic on failure; no new scene format, physics surface or fake stone bridge.
bool buildBankVisualSeams(const TraversalGraph&graph,
                          std::vector<BankVisualPart>&out);
}} // namespace oe::roads
