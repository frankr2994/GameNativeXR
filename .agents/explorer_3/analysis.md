# Technical Analysis & Architecture Mapping Report: Pillars 2 & 4

**Author:** Explorer 3 (`teamwork_preview_explorer`)  
**Target Architecture:** GameNativeXR Dev-Update Baseline  
**Date:** 2026-08-03  
**Working Directory:** `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_3`

---

## 1. Executive Summary & Tooling Disclosure

### 1.1 Objective
This report details the baseline investigation of existing XR controller input, keyboard, renderer, tracking, and mod installation in the QuestVR codebase, and defines the target architectural contracts, class boundaries, and interface contracts for **Pillar 2 (Unified VR Controller Navigation & Input Router)** and **Pillar 4 (Tracking Modes, VR Mod Resolver & Hook Pipeline)** as specified in Sections 7 and 9 of `GameNativeXR_Architecture_Plan.md`.

### 1.2 Rider MCP Availability Disclosure
In accordance with `AGENTS.md`, Rider MCP interactive tools are **unavailable** in this agent execution environment because tool execution is restricted to standard filesystem tools (`view_file`, `write_to_file`, `grep_search`, `find_by_name`, `list_dir`). File inspection, dependency tracing, and code analysis were performed using canonical workspace search and direct file analysis. Full Rider inspection checks remain pending for execution validation.

---

## 2. Baseline Codebase Analysis

### 2.1 Focus Area 1 — Input & Overlays Baseline

| File Path | Baseline Responsibility | Limitations / Deficiencies Identified |
| --- | --- | --- |
| `app/src/main/java/com/winlator/xr/XrActivity.java` | Main XR Activity (inherits `MainActivity`). Loads `libxr.so`. Fetches raw OpenXR axes and buttons via JNI (`getAxes()`, `getButtons()`). Periodically calls `updateFrame()`. | Unstructured frame input pipeline. Input processing directly calls `XrController`, `XrAPI`, and `XServer` without an explicit input routing state machine or transition cleanup. |
| `app/src/main/java/com/winlator/xr/XrRenderer.java` | Extends `GLRenderer`. Renders X-Server windows into native OpenXR framebuffers (`initFrame()`, `bindFBO()`, `endFrame()`). Draws frontmost `XrContentDialog`. | Lacks an absolute ray-to-screen reticle or cursor overlay in 3D scene space. The reticle is completely absent from the OpenGL rendering loop. |
| `app/src/main/java/com/winlator/xr/XrController.java` | Maps XR controller buttons and thumbstick axes to `XServer.pointer` (mouse) and `XServer.keyboard`. Handles DPAD navigation for `XrContentDialog`. | Delivers relative delta-mouse positioning or head-tilt pointer mapping. Lacks release-held button guarantees when switching between UI overlay navigation and game input. |
| `app/src/main/java/com/winlator/xr/XrKeyboard.java` | Wraps a hidden Android `EditText` (`text`) and calls system Android IME via `AppUtils.showKeyboard()`. Converts text edits to X-Server key events using `KeyCharacterMap`. | Relies on system Android IME pop-up which is invisible or non-functional in 3D VR mode. No controller-operable 3D keyboard UI overlay exists. |
| `app/src/main/java/com/winlator/xr/ui/XrContentDialog.java` | Extends Android `Dialog`. Captures dialog view into offscreen `Bitmap` / `Drawable` rendered into 3D XR frame. Receives `onKeyAction()` DPAD events. | Proves that 2D Android layout views can be rendered into 3D XR space as texturable drawables, serving as the UI engine for `XrKeyboardOverlay`. |

### 2.2 Focus Area 2 — Existing Mod Architecture

| File Path | Baseline Responsibility | Architectural Assessment |
| --- | --- | --- |
| `app/src/main/java/app/gamenative/data/ModInstall.kt` | Room database entities: `ModProfile`, `ModInstall`, `ModProfileInstallState`, `ModPlacementRecipe`, `ModOverwriteManifest`. | Comprehensive storage model for mod files, placement modes (`SYMLINK`, `COPY`, `OVERWRITE_COPY`), target roots, hashes, and backup tracking. Fully ready to reuse for VR mod file materialization. |
| `app/src/main/java/app/gamenative/mods/ModProfileManager.kt` | Manages per-app mod profiles, activation states, and priority ordering. | Provides active profile resolution and priority-ordered install states for Steam titles. |
| `app/src/main/java/app/gamenative/mods/ModMaterializer.kt` | Robust file placement engine. Performs `scanConflicts()`, `apply()`, `repairMissingTargets()`, `restoreBackups()`, and `removeAppliedFiles()`. | Exceptional file safety with canonical containment checks, backup creation, and atomic hash verification. Serves as the materialization backend for `VrHookPlanExecutor`. |
| `app/src/main/java/app/gamenative/mods/ModTargetResolver.kt` | Resolves target roots (`GAME_DIR`, `WINE_C`, `DOCUMENTS`, `MY_GAMES`, `APPDATA_ROAMING`, `APPDATA_LOCAL`, `APPDATA_LOCALLOW`). | Strict canonical containment checks prevent mod paths from escaping allowed root directories. |
| `app/src/main/java/com/winlator/xr/ModdingUtils.java` | Legacy special-case mod helpers for TrackIR (`opentrack_wxr.tzst`) and ReShade (`reshade-directx.tzst`, `reshade-plugins.tzst`). | Hardcoded extraction logic directly modifying filesystem before container launch. Needs replacement by generic `VrHookPlanExecutor` adapters. |

### 2.3 Focus Area 3 — Existing XR Bridge

| File Path | Baseline Responsibility | Wire Protocol & Transport Findings |
| --- | --- | --- |
| `app/src/main/java/com/winlator/xr/api/XrAPI.java` | Host bridge controller. Scans `/tmp/xr/version`, instantiates `XrVersion01` through `04`, writes `/tmp/xr/system`, and manages UDP sockets. | Simple ASCII file/socket protocol. Host reads version from `/tmp/xr/version` and spawns UDP receiver threads on configured local ports. |
| `app/src/main/java/com/winlator/xr/api/XrInterface.java` | Interface defining `AppInput`, `ControllerAxis`, `ControllerButton`, `PortIntent`, `encode()`, `consumeInputs()`, `getValue()`, `setValue()`. | Clean interface contract. Enums match native layout expected by `libxr.so`. |
| `app/src/main/java/com/winlator/xr/api/XrVersion01.java` | Version 0.1 wire format. Port 7278 (In) / 7872 (Out). Formats ASCII string containing client index, HMD/controller quaternions, thumbstick positions, IPD, FOV, and button booleans (`T`/`F`). | Reads `/tmp/xr/sbs` and `/tmp/xr/vr` flag files for mode state. |
| `app/src/main/java/com/winlator/xr/api/XrVersion02.java` | Version 0.2 wire format. Reads mode values directly from incoming UDP message rather than filesystem flag files. | Improved responsiveness over 0.1. |
| `app/src/main/java/com/winlator/xr/api/XrVersion03.java` | Version 0.3 wire format. Appends flag string `" T F"` for immersive and SBS state. | Backward-compatible extension of 0.2 format. |
| `app/src/main/java/com/winlator/xr/api/XrVersion04.java` | Version 0.4 wire format. Sends UDP data to dual output ports (7872 and 7873). | Current highest wire protocol version. |

---

## 3. Pillar 2 Specification — Unified VR Controller Navigation & Input Router

```
                      [Raw OpenXR Poses & Buttons]
                                   │
                                   ▼
                      ┌──────────────────────────┐
                      │    PreGameInputRouter    │
                      └────────────┬─────────────┘
                                   │ (Active Mode)
     ┌──────────────────┬──────────┼───────────┬──────────────────┐
     ▼                  ▼          ▼           ▼                  ▼
ANDROID_OVERLAY   GUEST_POINTER GUEST_TEXT GUEST_NAVIGATION   GAME_INPUT
 (XrContentDialog/  (X11 Pointer  (X11 Key  (X11 Navigation  (Game Mapped
 XrKeyboardOverlay)  & Reticle)    Events)    Keys: Tab/Enter) Controller/Keys)
```

### 3.1 `PreGameInputRouter` Requirements & State Machine

#### 3.1.1 Target Modes (`PreGameInputRouter.Mode`)
1. `ANDROID_OVERLAY`: Input routed to GameNative/XR dialogs (`XrContentDialog`) and `XrKeyboardOverlay`.
2. `GUEST_POINTER`: Absolute ray-to-screen reticle mapping to X-Server cursor coordinates and mouse click events.
3. `GUEST_TEXT`: Text character key events routed to focused guest window.
4. `GUEST_NAVIGATION`: Structural navigation key events (`Enter`, `Tab`, `Shift+Tab`, `Escape`, DPAD arrows, scroll wheel) routed to active guest window.
5. `GAME_INPUT`: Gameplay controller axis/button mapping routed to game input subsystem.
6. `SYSTEM_MENU`: GameNativeXR quick menu overlay, accessible at all times via Menu / primary thumbstick press.

#### 3.1.2 Release-Held Button Guarantee
- **Invariant:** Transitioning between any two modes `M_src -> M_dst` MUST release all currently pressed buttons, held keys, and active mouse drag states associated with `M_src` before enabling inputs in `M_dst`.
- **Implementation Mechanism:** The router maintains an active state bitmap `activePressedButtons` and `activePressedKeys`. Upon mode change:
  1. Emit `Pointer.setButton(button, false)` for all held mouse buttons.
  2. Emit `Keyboard.setKeyRelease(keycode)` for all held keyboard keys.
  3. Reset internal smooth cursor accumulators.
  4. Transition internal mode variable `currentMode`.

### 3.2 Absolute Ray-to-Screen Reticle
- **Virtual Screen Raycast:** Computes ray origin $\vec{O}$ and direction $\vec{D}$ from dominant controller pose in 3D world space. Intersects ray with virtual display plane defined by screen center position $\vec{P}_s$, plane normal $\vec{N}_s$, width $W_s$, and height $H_s$.
- **Screen Coordinate Mapping:** Maps 3D intersection point $(x_i, y_i)$ on the plane to absolute X-Server screen space $[0..X_{max}, 0..Y_{max}]$.
- **Cursor Rendering:** `XrRenderer` renders a distinct circular reticle icon at the mapped coordinate on top of the rendered frame buffer during `postFrame()`, even if the guest OS/game hides the Windows cursor.
- **Single-Controller Guarantee:** Fully operational with either right-controller-only or left-controller-only layout.

### 3.3 Visible Controller-Operable `XrKeyboardOverlay`
- **Surface Engine:** Extends `XrContentDialog`, rendering a custom Android XML virtual keyboard view into the offscreen bitmap/drawable.
- **Key Matrix:** QWERTY layout + Shift / CapsLock, Space, Backspace, Delete, Enter, Tab, Shift+Tab, Escape, Arrow keys, Home, End.
- **Password Masking:** Support for password input mode with hidden overlay preview.
- **Special Actions:** Dedicated "Done" and "Close" buttons. Closing restores focus back to the target guest X11 window.
- **Trigger Policy:** Auto-opens upon receipt of a confirmed guest text-focus signal; manually openable via XR Quick Menu.
- **Security Policy:** Typed characters and credentials are NEVER logged or saved to diagnostic sessions.

### 3.4 Guest Text-Focus Signal
- **X11 / Wine Integration:** Listens to X-Server window manager focus events (`XFocusInEvent`, `WM_TAKE_FOCUS`, edit control focus notifications).
- **Signal States:** `TEXT_FIELD_FOCUSED`, `TEXT_FIELD_UNFOCUSED`, `UNKNOWN`.
- **Debounce Policy:** 250ms debounce window prevents keyboard popup flickering during quick control navigation.

---

## 4. Pillar 4 Specification — Tracking Modes, VR Mod Resolver & Hook Pipeline

```
                     [Launch Request + Executable Identity]
                                       │
                                       ▼
                          ┌──────────────────────────┐
                          │   TrackingModeResolver   │
                          └────────────┬─────────────┘
                                       │
         ┌─────────────────────────────┼─────────────────────────────┐
         ▼                             ▼                             ▼
    [FLAT_3DOF]              [NATIVE_OPENXR_6DOF]              [MODDED_6DOF]
 (Default Flat Game;         (Native OpenXR/OpenVR;           (External VR Mod;
  HMD Orientation Only)       Full 6DoF Pose/Actions)          VrModResolver + 
                                                                VrHookPlanExecutor)
```

### 4.1 `TrackingModeResolver` Contract

The `TrackingModeResolver` evaluates the `LaunchRequest`, PE inspection facts (`ExecutableIdentity`), title compatibility database, and active VR mod manifests to assign exactly one `TrackingMode`:

1. `FLAT_3DOF` (Default Baseline):
   - Injected Guest Tracking: HMD orientation only (Pitch/Yaw/Roll). Zero positional offset ($X=0, Y=0, Z=0$) injected into guest contract.
   - Renderer Presentation: Quad virtual screen presentation via `XrRenderer`.
   - Selection Rule: Selected for all flat titles without a validated native VR runtime adapter or compatible active VR mod.
2. `NATIVE_OPENXR_6DOF`:
   - Injected Guest Tracking: Full 6DoF HMD pose (position + orientation), controller poses, and action sets.
   - Selection Rule: Selected when executable imports OpenXR / OpenVR native APIs and matches a validated native runtime adapter.
3. `MODDED_6DOF`:
   - Injected Guest Tracking: Full 6DoF pose/action contract negotiated via matched external VR mod manifest + versioned XR host bridge.
   - Selection Rule: Selected ONLY after exact VR mod manifest validation, target PE hash match, file materialization, and successful process handshake.

### 4.2 `VrModResolver` & Versioned Manifest Contract

#### 4.2.1 VR Mod Manifest Schema (`vr_mod_manifest.json`)
```json
{
  "schemaVersion": 1,
  "modId": "uevr_mod_v1",
  "name": "Universal Unreal Engine VR Mod",
  "version": "1.2.0",
  "supportedAppIds": ["108600"],
  "targetExecutable": {
    "relativePath": "ProjectName/Binaries/Win64/Game-Win64-Shipping.exe",
    "architecture": "x64",
    "sha256Hashes": ["a1b2c3d4e5f6..."]
  },
  "requiredTrackingMode": "MODDED_6DOF",
  "xrBridgeVersionRange": "0.1-0.4",
  "hookStrategy": "PROXY_DLL",
  "proxyDllName": "dxgi.dll",
  "dllOverrides": "dxgi=n,b",
  "handshakeTimeoutMs": 10000
}
```

#### 4.2.2 Resolver Matching Contract
- **Architecture Enforcement:** Target executable architecture (`x86` vs `x64`) MUST match mod DLL/payload architecture exactly.
- **Hash Matching:** Target executable SHA-256 hash MUST match one of the manifest's `sha256Hashes` (or bounded version rules).
- **Safety Gate:** Rejects missing manifests, path traversal attempts (`..`), hash mismatches, or conflicting multi-mod activations.

### 4.3 `VrHookPlanExecutor` & 5 Hook Strategies

#### 4.3.1 Hook Strategies (`VrHookStrategy`)
1. `LAUNCHER_EXECUTABLE`: Replaces default executable command line with mod-provided launcher binary.
2. `PROXY_DLL`: Places proxy DLL (`dxgi.dll`, `d3d11.dll`, `openxr_loader.dll`) alongside target executable and sets precise Wine `WINEDLLOVERRIDES`.
3. `RUNTIME_REGISTRATION`: Registers GameNativeXR guest OpenXR runtime in Wine registry and OpenXR loader manifest directory.
4. `OPENVR_ADAPTER`: Deploys OpenComposite `openvr_api.dll` adapter redirecting OpenVR calls to guest OpenXR.
5. `LINUX_WRAPPER`: Packaged Linux-side preload or wrapper script with allow-listed environment variables.

#### 4.3.2 Execution Lifecycle & Rollback Guarantee
1. **Plan Generation:** Produces immutable `VrHookPlan` containing planned file entries, DLL overrides, and environment variables.
2. **Materialization:** Calls `ModMaterializer.apply()` to place files and create `ModOverwriteManifest` backups.
3. **Launch Execution:** Sets launch-local environment variables (e.g. `WINEDLLOVERRIDES`) without corrupting persistent container settings.
4. **Handshake Monitoring:** Spawns handshake listener thread and waits up to `handshakeTimeoutMs` for guest process connection.
5. **Atomic Rollback:** If handshake fails or process crashes, `VrHookPlanExecutor` terminates process, restores environment, and executes `ModMaterializer.restoreBackups()`.

### 4.4 `GuestVrRuntimeAdapter` Contract

```kotlin
interface GuestVrRuntimeAdapter {
    val adapterId: String
    val supportedArchitecture: ExecutableArchitecture
    
    fun initialize(context: Context, launchPlan: LaunchPlan): Boolean
    fun onFrameTimingPredict(): FrameTiming
    fun pollPoses(): VrPoseData
    fun pollActions(): VrActionData
    fun submitFrame(eyeIndex: Int, textureId: Int, layerMetadata: LayerMetadata): Boolean
    fun shutdown()
}
```
Implementations:
- `WindowsOpenXrAdapter`: Manages Windows guest OpenXR runtime IPC bridging to host Android OpenXR session.
- `OpenVrAdapter`: Packaged OpenComposite wrapper translating OpenVR API calls to OpenXR guest adapter.

### 4.5 `XrHostBridge` Modernization Plan
- **Legacy Compatibility:** Preserves ASCII UDP wire formats `0.1` through `0.4` for existing mods (e.g. standard WinlatorXR VR mods).
- **Modern 6DoF Channels:**
  1. *Control & Handshake Channel:* TCP/Unix socket for launch token authentication, version negotiation, and capability exchange.
  2. *High-Rate Pose & Input Channel:* Shared memory / UDP binary socket for sub-millisecond pose updates with monotonic timestamps.
  3. *Frame & Swapchain Transport Channel:* Native HardwareBuffer / EGL image sharing between Wine guest DirectX/Vulkan context and Android OpenXR layer.
  4. *Diagnostic Channel:* Bi-directional heartbeat, error reporting, and clean session teardown.

---

## 5. Interface & Class Contracts (Kotlin Source Specification)

### 5.1 Pillar 2 Contracts

```kotlin
package app.gamenative.input

import com.winlator.xr.ui.XrContentDialog
import com.winlator.xserver.XServer

enum class InputRouterMode {
    ANDROID_OVERLAY,
    GUEST_POINTER,
    GUEST_TEXT,
    GUEST_NAVIGATION,
    GAME_INPUT,
    SYSTEM_MENU,
}

data class ControllerPoseState(
    val axes: FloatArray,
    val buttons: BooleanArray,
    val dominantHandIsRight: Boolean = true,
)

interface PreGameInputRouter {
    val currentMode: InputRouterMode
    
    fun setMode(targetMode: InputRouterMode)
    fun processFrameInput(poseState: ControllerPoseState, xServer: XServer)
    fun releaseAllHeldInputs(xServer: XServer)
    fun resetState(xServer: XServer)
}

interface AbsoluteRayReticleCalculator {
    fun calculateScreenCoordinates(
        controllerAxes: FloatArray,
        screenWidth: Int,
        screenHeight: Int,
        virtualScreenDistance: Float,
    ): Pair<Int, Int>?
}

interface XrKeyboardOverlayContract {
    fun showOverlay(dialog: XrContentDialog, initialText: String = "", isPassword: Boolean = false)
    fun hideOverlay()
    fun onKeyTyped(character: Char)
    fun onActionKey(keycode: Int)
}

enum class GuestTextFocusState {
    TEXT_FIELD_FOCUSED,
    TEXT_FIELD_UNFOCUSED,
    UNKNOWN,
}

interface GuestTextFocusListener {
    fun onFocusStateChanged(state: GuestTextFocusState, windowId: Long)
}
```

### 5.2 Pillar 4 Contracts

```kotlin
package app.gamenative.vr

import app.gamenative.data.ModInstall
import app.gamenative.data.ModPlacementRecipe
import java.io.File

enum class TrackingMode {
    FLAT_3DOF,
    NATIVE_OPENXR_6DOF,
    MODDED_6DOF,
}

enum class VrHookStrategy {
    LAUNCHER_EXECUTABLE,
    PROXY_DLL,
    RUNTIME_REGISTRATION,
    OPENVR_ADAPTER,
    LINUX_WRAPPER,
}

data class VrModManifest(
    val schemaVersion: Int,
    val modId: String,
    val name: String,
    val version: String,
    val supportedAppIds: List<String>,
    val targetRelativePath: String,
    val targetArchitecture: String,
    val acceptedSha256Hashes: List<String>,
    val requiredTrackingMode: TrackingMode,
    val xrBridgeVersionRange: String,
    val hookStrategy: VrHookStrategy,
    val proxyDllName: String?,
    val dllOverrides: String?,
    val handshakeTimeoutMs: Long = 10000L,
)

data class VrHookPlan(
    val manifest: VrModManifest,
    val targetExecutableFile: File,
    val install: ModInstall,
    val recipes: List<ModPlacementRecipe>,
    val environmentOverrides: Map<String, String>,
)

interface TrackingModeResolver {
    fun resolveTrackingMode(
        appId: String,
        executableFile: File,
        manifest: VrModManifest?,
        userOverrideMode: TrackingMode?,
    ): TrackingMode
}

interface VrModResolver {
    fun parseManifest(manifestFile: File): Result<VrModManifest>
    fun matchMod(
        appId: String,
        executableFile: File,
        executableHash: String,
        executableArch: String,
        installedMods: List<ModInstall>,
    ): VrModManifest?
    fun createHookPlan(manifest: VrModManifest, install: ModInstall, gameDir: File, winePrefix: String): VrHookPlan
}

interface VrHookPlanExecutor {
    suspend fun executeHookPlan(plan: VrHookPlan, backupRoot: File): Result<Unit>
    suspend fun waitForHandshake(plan: VrHookPlan): Boolean
    suspend fun rollbackHookPlan(plan: VrHookPlan): Result<Unit>
}

interface XrHostBridgeContract {
    fun initializeBridge(version: String): Boolean
    fun sendPoseUpdate(axes: FloatArray, buttons: BooleanArray)
    fun receiveGuestMessage(): String?
    fun shutdownBridge()
}
```

---

## 6. Verification & Test Plan

1. **Unit Verification (`:app:testModernXrDebugUnitTest`):**
   - Verify `PreGameInputRouterTest` transitions through all 6 modes and confirms zero held buttons after mode switch.
   - Verify `AbsoluteRayReticleTest` math calculations for edge/corner ray intersections.
   - Verify `VrModResolverTest` manifest parsing, architecture rejection (`x86` mod vs `x64` binary), and SHA-256 hash matching.
2. **Build Verification (`./gradlew :app:assembleModernXrDebug`):**
   - Ensure clean compilation with zero type mismatches or syntax errors.
3. **Execution Verification:**
   - Verify `ModMaterializer.restoreBackups()` cleanly reverts materialized VR mod files upon simulated hook failure.

---
*Report compiled by Explorer 3 (`teamwork_preview_explorer`). Ready for handoff.*
