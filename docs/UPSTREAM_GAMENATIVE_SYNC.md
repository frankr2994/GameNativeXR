# Upstream GameNative Sync

## Overview
This document outlines the synchronization of `GameNative` upstream master into `GameNativeXR`'s `Dev-Update` branch. 

## Objectives
- Bring the current GameNative platform baseline into GameNativeXR as one reviewable upstream merge.
- Preserve GameNativeXR's Quest XR host and Codex's Halo/bridge work.

## Merge Details
- **Target repository:** GameNativeXR (Branch: `Dev-Update`)
- **Upstream repository:** GameNative (Branch: `master`)
- **Strategy:** Git merge instead of cherry-picks to preserve commit history and ease future updates.

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
- **Controls & Handlers (`WinHandler.java`):**
  - Combined `WinHandler` singleton initialization required by GameNativeXR with new GameNative gamepad memory-mapped file initialization.
- **Dependencies (`Drawable.java`):**
  - Replaced usage of the deprecated `GPUImage` with the new upstream `NativeTexture`.

## Validation
- Build `modernXrDebug` tested and verified.
- `tools/verify-quest-apk.ps1` script passed all validation checks ensuring Quest compliance.
