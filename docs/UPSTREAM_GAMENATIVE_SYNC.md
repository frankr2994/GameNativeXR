# Upstream GameNative Sync

## Overview
This document records synchronization of GameNative upstream `master` into GameNativeXR's `Dev-Update` branch.

## Objectives
- Bring the current GameNative platform baseline into GameNativeXR as one reviewable upstream merge.
- Preserve the Quest XR host and its XR rendering contract.

## Merge Details
- **Target repository:** GameNativeXR (Branch: `Dev-Update`)
- **Upstream repository:** GameNative (Branch: `master`)
- **Strategy:** Git merge instead of cherry-picks to preserve commit history and ease future updates.
- **Merge commit:** `9e9bcdc50bba134015b3dcf7f136929de9a70959`
- **XR parent:** `a23ffac82762af9fcd225bc4f82ef63333c3d341`
- **Upstream parent:** `d8535825398afb1446d63f95ba53698cf1586847`

## Key Conflict Resolutions
- **Build Configurations (`app/build.gradle.kts`):**
  - Adopted new GameNative flavor definitions (`legacy`, `modern`).
  - Retained XR-specific flavor configuration (`legacyXr`, `modernXr`) for ABI filters and Quest deployment requirements.
  - Retained `JavaSteam` sibling artifact discovery for XR development workflow.
- **Manifest (`app/src/main/AndroidManifest.xml`):**
  - Retained XR Activities (`XrActivity`, `MetaQuest`, `Pico`) with `vr_process` configuration.
  - Retained XR-specific screen orientation logic dynamically injected from `build.gradle.kts`.
  - Added new GameNative services (`NexusModImportService` and `NotificationActionReceiver`).
- **Rendering & Display (`XServerView.java`, `XServerViewGL.java`, `GLRenderer.java`, `XrRenderer.java`):**
  - Accommodated GameNative's split of `XServerView` (for Vulkan/ASurface) and `XServerViewGL` (for GLSurfaceView).
  - Adjusted `XrRenderer` to accept the new `XServerViewGL` since it requires an OpenGL rendering context.
  - Implemented the `XrActivity` switch inside `XServerViewGL` to retain XR support.
  - Force XR sessions through `XServerViewGL` in `XServerScreen.kt`; the upstream Vulkan `XServerView` does not create `XrRenderer`.
- **Controls & Handlers (`WinHandler.java`):**
  - Combined `WinHandler` singleton initialization required by GameNativeXR with new GameNative gamepad memory-mapped file initialization.
- **Dependencies (`Drawable.java`):**
  - Replaced usage of the deprecated `GPUImage` with the new upstream `NativeTexture`.

## Validation
- `assembleModernXrDebug` builds successfully.
- `tools/verify-quest-apk.ps1` validates the Quest package, activity/category, ARM64 XR library pair, and APK signing.
- Device or simulator runtime validation remains required after renderer or native-bridge changes.
