# Phase 1 — baseline audit (1.1)

Java/native Android UI; OpenGL ES 3, EGL window surfaces, no Vulkan/Compose/native libraries. minSdk 30, targetSdk 36. MainActivity and WallpaperService share RenderLoop/GlScene. The author activity uses Canvas animations and MediaPlayer only while visible.

## Confirmed code findings

- Motion registers gravity/accelerometer on the calling main thread at 33,333 µs. Accelerometer smoothing adds 180 ms; renderer adds 70 ms. No rotation-vector selection, no capability telemetry.
- Every sensor event allocates a float array and a posted closure. UI angle formatting runs approximately 6 times/sec.
- Every frame allocates a projection array, uniform-name array, and concatenated HashMap lookup keys. Uniform locations can be resolved once.
- Rendering uses Handler postDelayed(33) alongside blocking eglSwapBuffers/vsync, so its cadence is not aligned with refresh. It targets roughly 30 FPS regardless of display refresh.
- Bitmap decode and mip generation do NOT run every frame. Gaussian pyramid is prepared once per upload, one level per frame. Shader compilation runs on scene creation only.
- Surface changes destroy EGL and decode/upload the image again. Disk decode runs on the render thread during preparation, delaying orientation rendering. The main thread can wait up to two seconds during detach.
- Wallpaper visibility stops sensors and scheduling, offset notifications are disabled. Touch is disabled. Saved photo survives process death; effect configuration does not exist yet.
- No measured GPU times, GC trace, sensor timestamps or thermal adaptation. No evidence supports attributing the bug specifically to HyperOS.

## Preserve

Package/signature; safe ImageDecoder import; private atomic image storage; EGL/WallpaperService integration; original optical shader and author page. Existing settings migrate to Classic Tilt. No rewrite into another UI framework.

## Change

Dedicated sensor thread, latest-state handoff, coalesced wakeups, Choreographer frame pacing, reusable math buffers and cached uniforms. Async image/depth preparation, shared versioned config and GPU effects, quality FBO, thermal response. No ML/decode/disk I/O in doFrame.

## Evidence limits

User video: filmed screen, 400×400 ~30 FPS. It is not sensor telemetry, a Perfetto trace or a frame-time capture. Screenshot identifies POCO C85 / HyperOS 3.0.305.0.WBNRUXM.C11 / Android 16. `adb devices -l` was empty. POCO and AOSP runtime QA remain required; source-level causes can be removed but device success cannot be claimed.

API references: [Choreographer](https://developer.android.com/reference/android/view/Choreographer), [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager), [thermal status](https://developer.android.com/reference/android/os/PowerManager.OnThermalStatusChangedListener).
