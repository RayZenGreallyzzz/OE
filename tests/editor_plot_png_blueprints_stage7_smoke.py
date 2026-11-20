#!/usr/bin/env python3
"""Stage7 editor PNG blueprints; verify preserving OE's native single city.

Limits: 8 independent plans stored; only one shown at a time to bound GLES.
No separate Activity or WebView. Static source guard, not actual device QA.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=(root/"android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
arch=(root/"android/src/main/java/game/oe/editor/ProjectArchive.java").read_text()
cpp=(root/"android/src/main/cpp/native_engine.cpp").read_text()
gradle=(root/"android/build.gradle").read_text()
for marker in (
    "PICK_PLOT_PNG=707",
    "ConcurrentHashMap<Integer,MapGuide>",
    "MAX_PLOT_PNGS=8",
    '"🗺 PNG-план для выбранного участка"',
    "private void choosePlotForPlan()",
    "private void importPlotPlan(int id,Uri uri)",
    "private void configurePlotPlan(String record)",
    "private void showPlotPlan(int id)",
    "private void showGlobalMapReference()",
    "private void togglePlotPlanVisibility(int id)",
    "private void removePlotPlan(int id)",
    "private void syncPlotPlanCentres()",
    "nativeMapStage(img.bytes,img.width,img.height)",
    "nativeMapCommit(plan.centerX,plan.centerZ",
    "activePlotPlanId=id;",
    "plotPlans.put(id,plan);",
    "plotPlans.clear();plotPlans.putAll(parsedPlotPlans);",
    "plotPlanRecords=document.optJSONArray(\"plotPlans\")",
    "TextureReader.decode(original,\"image/png\",1024)",
    'json.put("plotPlans",plans)',
    'json.put("version",11)',
    'sceneVersion>13',
    "sourceReferences!=null",
    "!validEditorPlotName(label)",
):
    assert marker in java, ("Android editor blueprint missing",marker)
for marker in (
    "private static Set<String> referenceNames(String scene)",
    "version>=1&&version<=13",
    'version==11&&plans!=null&&plans.length()>0',
    'Set<String> references=referenceNames(scene)',
    "for(String reference:references)",
    "referenceFiles.length==references.size()",
    "reference.equals(calculatedReferenceName(png))",
    "max", # placeholder for existing budget validation below
):
    if marker=="max":continue
    assert marker in arch, ("portable PNG ZIP archiving missing",marker)
assert "MAX_UNPACKED_BYTES=384L*1024*1024" in arch
assert "((version==10||version==11)&&plots!=null&&plots.length()>0)" in arch
assert "android.webkit" not in java
assert "void drawMapGuide(const Mat4& vp)" in cpp
assert "nativePlotFocus(" in cpp and "drawEditorPlots(vp);" in cpp
assert "runtime/walk_collision.h" in cpp
assert "applicationId 'game.oe.editor.r57canvas.nativebuilder'" in gradle
print("OE_STAGE7_BLUEPRINTS_OK: PNG per village/city plot, individual SHA ZIP refs, "
      "one native GLES editor overlay at a time, old scenes v1-v10 retained")
