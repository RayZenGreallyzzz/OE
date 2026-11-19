#!/usr/bin/env python3
"""Static guard against losing Phoenix JSON / GLB on OE Android bridge regressions.

The Android APK compilation job separately confirms Java syntax. This smoke
protects source invariants only; it does not replace tablet QA.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
bridge = (ROOT / "android/src/main/java/game/oe/editor/PhoenixBuilderActivity.java").read_text()
editor = (ROOT / "android/src/main/java/game/oe/editor/EditorActivity.java").read_text()
gradle = (ROOT / "android/build.gradle").read_text()

def require(tokens):
    for token in tokens:
        assert token in bridge, f"missing Phoenix safety invariant: {token}"

require([
    "private static final int SAVE_HISTORY=3;",
    "projectBackup(slot)", "readValidProject", "validProjectJson",
    'doc.optInt("version",-1)==2', 'doc.opt("objects") instanceof JSONArray',
    "out.getFD().sync();", "StandardCopyOption.ATOMIC_MOVE",
    "atomicCopyFile(live,projectBackup(1))",
    "atomicWriteFile(live,data);",
    "for(int slot=1;slot<=SAVE_HISTORY;slot++)",
    'setNeutralButton("🛟 Копии"',
    "exportRecoveryJson", "confirmRestoreJson",
    "pendingQueue.offer(ready);", "showNextSave();",
    "private boolean fileCopyInProgress=false;",
    "if(pendingSave!=null||fileCopyInProgress)return;",
    "Ошибка копирования Phoenix", "Повторить",
    "if(stream!=null)abortSave();",
    "getLastSaveError()",
    "MAX_EXPORT_BYTES=1024L*1024*1024",
])
assert "if(stream!=null||pendingSave!=null)return false;" not in bridge
assert "if(available>0&&available<bytes.length+DISK_RESERVE)" in bridge
assert "if(available>0&&available<data.length*2L+DISK_RESERVE)" in bridge
assert "private File projectFile()" in bridge
assert 'startActivityForResult(new Intent(this,PhoenixBuilderActivity.class),PHOENIX_BUILDER)' in editor
assert "applicationId 'game.oe.editor.phoenix013fix1'" in gradle
assert "versionCode 36" in gradle
# Graphics settings must live in the always-visible top-right toolbar,
# use existing native quality implementation, and persist the selection.
assert 'Button settingsButton=button("⚙️",this::showEngineSettings);' in editor
assert 'top.addView(settingsButton,new LinearLayout.LayoutParams(dp(46),dp(43)));' in editor
assert 'private void showEngineSettings(){' in editor
settings = editor.split('private void showEngineSettings(){',1)[1].split(
    'private void chooseDeviceProfile(){',1)[0]
assert '.setTitle("⚙️ Настройки OE")' in settings
assert '🖼 Разрешение изображения' in settings
assert 'Детализация текстур GLB на экране' in settings
assert '.setItems(sections,' in settings
assert '.setMessage(' not in settings  # Android bug: hides the options
assert 'else if(which==1)chooseTextureLimit();' in settings
assert 'else if(which==2)chooseTextureSampling();' in settings
assert 'else if(which==3)chooseGlbLighting();' in settings
assert 'Чёткость текстур под углом' in settings
assert 'else showDeviceCapabilities();' in settings
assert 'qualityPrefs.getInt("oe_display_resolution",' in editor
assert 'qualityPrefs.getInt("oe_quality_tier",-1)' in editor  # migration
assert 'textureLimit=getPreferences(MODE_PRIVATE).getInt("oe_glb_preview_texture_limit",1024);' in editor
assert '.putInt("oe_display_resolution",selected).apply();' in editor
assert '.putInt("oe_glb_preview_texture_limit",textureLimit).apply();' in editor
assert 'surface.applyRenderScale(scale);' in editor
assert 'nativeSetQualityProfile(rendererTier)' in editor
assert 'QUALITY_MODES' in editor
resolution_change = editor.split('private void selectDeviceQuality(int selected){',1)[1].split(
    'private void showEngineSettings(){',1)[0]
# Never switch geometry LOD, culling or texture decode when merely changing
# screen resolution. GLB source bytes are never rewritten.
assert 'nativeSetQualityProfile' not in resolution_change
assert 'textureLimit=' not in resolution_change
assert 'GlbReader.decode' not in resolution_change
assert 'nativeQualityScale(displayTier)' in resolution_change
assert 'getPreferences(MODE_PRIVATE).edit()' in resolution_change
assert 'Файл проекта' not in resolution_change
print("OK: screen resolution changes only GLES buffer scale; source GLB quality untouched")

# Lighting is selectable but only changes the imported-GLB renderer.
cpp = (ROOT / "android/src/main/cpp/native_engine.cpp").read_text()
assert 'nativeSetImportedLighting(' in editor
assert 'nativeSetImportedLighting(glbSoftLighting);' in editor
assert 'glbSoftLighting=getPreferences(MODE_PRIVATE).getBoolean("oe_glb_soft_lighting",true);' in editor
assert '.putBoolean("oe_glb_soft_lighting",glbSoftLighting).apply();' in editor
assert 'private void chooseGlbLighting(){' in editor
assert "✨ Материалы GLB · не меняет оригиналы" in editor
assert 'glUniform1f(gShader.locImportedLighting,gImportedSoftLighting?1.f:0.f);' in cpp
assert 'glUniform1f(gShader.locImportedLighting,0.f); // Do not alter road/terrain/gizmos.' in cpp
assert 'JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSetImportedLighting(' in cpp
assert 'uniform float uImportedLighting;' in cpp
assert 'if(uImportedLighting>.5)' in cpp
assert 'vec3 base=srgbToLinear(texel.rgb)*' in cpp
assert 'color=linearToSrgb(lit);' in cpp
assert 'step(vec3(.04045),v)' in cpp
assert 'step(vec3(.0031308),v)' in cpp
assert 'vec3 fresnel=F0+(1.-F0)*pow(1.-VoH,5.);' in cpp
assert 'roughness*=orm.g;' in cpp
assert 'metallic*=orm.b;' in cpp
assert 'mapN.xy*=uNormalScale;' in cpp
assert 'gShader.locImportedLighting=glGetUniformLocation(gShader.program,"uImportedLighting");' in cpp
assert 'void drawWorld(' in cpp and 'void drawImported(' in cpp
# The new imported GLB material path must be complete from JSON to GLES.
glb = (ROOT / "android/src/main/java/game/oe/editor/GlbReader.java").read_text()
assert 'pbr.optJSONObject("metallicRoughnessTexture")' in glb
assert 'mat.optJSONObject("normalTexture")' in glb
assert 'attrs.has("NORMAL")' in glb
assert 'pbrTextures[batch*2]=normalMap;' in glb
assert 'pbrFactors[batch*3]=j.roughness;' in glb
assert 'pbrFactors[batch*3+1]=j.metallic;' in glb
assert 'pbrFactors[batch*3+2]=j.normalScale;' in glb
assert 'new float[]{1.f,1.f,1.f}' in glb  # glTF default baseColorFactor
assert 'float[] pbrTextures' not in glb  # slots are ints, factors are floats
assert 'int[] pbrTextures;' in glb
assert 'float[] pbrFactors;' in glb
assert 'mesh.pbrTextures,mesh.pbrFactors' in editor
assert 'model.pbrTextures,model.pbrFactors' in editor
assert 'jintArray pbrTexData,jfloatArray pbrFactorData,' in cpp
assert 'env->GetIntArrayRegion(pbrTexData' in cpp
assert 'env->GetFloatArrayRegion(pbrFactorData' in cpp
assert 'glUniform1f(gShader.locUseNormalMap,0.f);' in cpp
assert 'glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,0);' in cpp
assert 'glUniform1f(gShader.locImportedLighting,0.f); // Do not alter road/terrain/gizmos.' in cpp
# Sampling changes GPU state only: never touch decoded glTF or saved GLB.
assert 'private void chooseTextureSampling(){' in editor
assert 'nativeSetTextureSampling(which);' in editor
assert '.putInt("oe_glb_texture_sampling",which).apply();' in editor
assert 'textureSamplingMode=getPreferences(MODE_PRIVATE).getInt("oe_glb_texture_sampling",2);' in editor
assert 'nativeSetTextureSampling(textureSamplingMode);' in editor
assert 'JNIEXPORT void JNICALL Java_game_oe_editor_EditorActivity_nativeSetTextureSampling(' in cpp
assert 'glGenerateMipmap(GL_TEXTURE_2D);' in cpp
assert 'gTextureSampling>=1&&tex.format==0&&!tex.mipmapsReady' in cpp
assert 'GL_LINEAR_MIPMAP_LINEAR:GL_LINEAR' in cpp
assert 'gAnisotropicAvailable' in cpp
assert 'GL_EXT_texture_filter_anisotropic' in cpp
assert 'std::min(4.f,gMaxAnisotropy)' in cpp
assert 'for(auto&entry:gAssets)' in cpp
assert 'for(auto&entry:gPaintTextures)' in cpp
assert 'detectTextureFilterCapabilities();' in cpp
assert 'tex.id=0;tex.mipmapsReady=false;' in cpp
print("OK: GPU-only mipmaps and optional anisotropic filters persist across EGL recreation")
assert 'GL_RGBA8' in cpp
assert 'const char*fallbackVert=R"' in cpp
assert 'const char*fallbackFrag=R"' in cpp
assert '"GLB PBR shader unsupported by this GLES device: falling back to legacy"' in cpp
assert 'glGetProgramiv(gShader.program,GL_LINK_STATUS,&linked);' in cpp
assert 'vNormal=normalize(transpose(inverse(mat3(uModel)))*aNormal);' in cpp
print("OK: glTF PBR maps, smooth normals and GLES fallback preserve non-GLB rendering")


# Android AlertDialog.Builder.setMessage and setItems share the same content
# region: if both are set, the message wins and the destination list vanishes.
# The GLB export must use explicit buttons instead.
export_dialog = bridge.split('private void showNextSave(){', 1)[1].split(
    'private void startDeviceSave(){', 1)[0]
assert '.setPositiveButton("🏰 В город OE"' in export_dialog
assert '.setNeutralButton("📱 На устройство"' in export_dialog
assert '.setNegativeButton("Отмена"' in export_dialog
assert '.setItems(' not in export_dialog
# The "recovery copies" list also must not combine list content and message.
recovery_dialog = bridge.split('private void showProjectRecovery(){', 1)[1].split(
    'private void exportRecoveryJson(', 1)[0]
assert '.setItems(items,' in recovery_dialog
recovery_list_dialog = recovery_dialog.split(
    '.setTitle("🛟 Копии Phoenix — JSON без текстур")', 1
)[1].split('.setItems(items,', 1)[0]
assert '.setMessage(' not in recovery_list_dialog
print("OK: Android GLB destination buttons and JSON recovery list are visible")

print("OK: Phoenix JSON snapshots, fallback, SAF retry, city handoff, and app ID unchanged")

# glTF meters are not the same as file size. A 700m castle must not be
# normalized to six meters; verify whole Java->JNI->camera path and no
# historical 24MiB bridge guard. These are static invariants; tablet QA is
# still required for geometry rendering and large-world performance.
glb = (ROOT / "android/src/main/java/game/oe/editor/GlbReader.java").read_text()
assert 'float normalized=6.0f/extent' not in glb
assert 'float centerX=(maxX+minX)*.5f,centerZ=(maxZ+minZ)*.5f;' in glb
assert 'verts[i]-=centerX;' in glb
assert 'verts[i+1]-=minY;' in glb
assert 'verts[i+2]-=centerZ;' in glb
assert 'float dx=maxX-minX,dy=maxY-minY,dz=maxZ-minZ;' in glb
assert 'dy*.5f,sphere+.05f' in glb
assert 'radius>8192.f' in cpp
assert 'const float far=std::max(1800.f,gDistance*3.f+500.f);' in cpp
assert 'std::min(3200.f,radius*6.f)' in cpp # giant stays in view at auto-frame
assert 'gDistance=clampf(gDistance*clampf(zoom,.6f,1.5f),5,2400);' in cpp
assert 'if(t<=0||t>6200.f)return false;' in cpp
assert 'for(int i=1;i<=388;i++)' in cpp
assert 'frameLargeImported(gEntities.back());' in cpp
assert 'loX=a->second.minX;hiX=a->second.maxX;' in cpp
assert 'gTarget.y=terrainHeightAt(gTarget.x,gTarget.z)+1.2f+elevation;' in cpp
assert '<<(a.maxX-a.minX)*e->size*e->sx' in cpp
assert 'if(next.file.length()>24L*1024*1024)' not in bridge
assert 'if(next.file.length()>GlbReader.MAX_BYTES)' in bridge
assert 'Город пока может загрузить за один раз до ' in bridge
assert 'GLB больше 24 МБ' not in editor
assert 'Неправильный размер GLB для города (макс. 24 МБ)' not in editor
assert 'entry.bytes>24L*1024*1024' not in editor
assert 'MAX_BYTES = 64 * 1024 * 1024' in glb
assert 'MAX_TRIANGLES = 240000;' in glb
assert 'MAX_IMPORTED_TRIANGLES=240000;' in cpp
assert 'memoryUsed<=64*1024*1024' in glb
assert 'if(totalNew>64u*1024u*1024u)return JNI_FALSE;' in cpp
assert 'MAX_TEXTURE_CACHE_BYTES=128u*1024u*1024u' in cpp
# Integer arithmetic should preserve a real 700 x 250 x 90m bounding box.
points = [(-350.0, 4.0, -125.0),(350.0, 94.0, 125.0)]
lo = tuple(min(p[i] for p in points) for i in range(3))
hi = tuple(max(p[i] for p in points) for i in range(3))
center = ((lo[0]+hi[0])*.5,(lo[2]+hi[2])*.5)
world = [(p[0]-center[0],p[1]-lo[1],p[2]-center[1]) for p in points]
assert tuple(max(p[i] for p in world)-min(p[i] for p in world) for i in range(3))==(700.,90.,250.)
print("OK: original 700m castle dimensions preserved; camera and picking support metric bounds")
