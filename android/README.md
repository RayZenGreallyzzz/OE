# OE Native — Android editor (0.2 Alpha)

This is the **native Android** prototype of Orvetra Engine, separate from the existing browser demo at the repository root and from the Godot-based **ORVETHRA** game.

## What actually exists

- Android Activity and touch-first editor UI written in Java (no WebView).
- The 3D engine core is **C++17 + OpenGL ES 3.0** through JNI (no Godot / Unreal / Unity / Three.js).
- Native 3D preview: tavern, forge, auction house, lanterns, fountain, trees, ground, simple sunlight shading.
- Single-finger orbit, two-finger camera zoom, tap-to-select, list of entities.
- Move in X/Z, rotate around Y, resize selected scene object.
- Add buildings and props; save and reopen scene as JSON in the app's private storage.
- **Render-on-demand** via `GLSurfaceView.RENDERMODE_WHEN_DIRTY`: stops issuing frames when the user is not editing. Intended to reduce idle GPU load, but **cannot guarantee eliminating coil whine**.
- Max 300 entities, a single arm64-v8a APK target, target API 34, min API 26.

## Installation / build

### Github Actions (no PC required)

Open **Actions → OE Android APK → latest successful run → Artifacts → OE-Android-arm64-debug**, download and unzip, then install `android-debug.apk` on Android. This is a *debug APK*, not a Play Store release, and only supports 64-bit ARM. You may need to allow installation from the files/browser app.

### Android Studio

Open the repository root in Android Studio (not just the `android` directory). Use JDK 17 and Android SDK 35 + NDK 26.3.11579264 + CMake 3.22.1; build the `android` module with Gradle 8.9.

### From command line (with Android SDK and Gradle installed)

```sh
gradle :android:assembleDebug
```

Output: `android/build/outputs/apk/debug/android-debug.apk`.

## Controls

- **One finger:** orbit the camera.
- **Two fingers:** zoom.
- **Tap a building:** select; use the bottom controls to translate, rotate, or scale the selected entity.
- **Objects:** choose any entity by name.
- **Save / Load:** scene JSON saved locally in this Android app. This does *not* change the Godot project.
- **New Scene:** restore the demonstration district. Saved JSON is not deleted.

## Limits and next work

This build is a **real native starting point**, not a full Unreal replacement yet. There is no GLB importer, PBR textures, shadows, navigation, physics, animation, audio, scripts, prefab system, native Vulkan renderer, game export, or Godot scene converter yet. The displayed architecture is procedural placeholder geometry, not the already-created ORVETHRA buildings. Direct import of Godot .tscn/.gd files is **not** supported: game logic will need an explicit compatibility/porting layer.

On a 4 GB Android tablet the goal is a stable, responsive low-power editing experience. Actual frame timing, chipset compatibility and any electrical buzzing must be tested on the physical tablet.
