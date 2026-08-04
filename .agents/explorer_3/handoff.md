# Handoff Report: Explorer 3 (Pillars 2 & 4 Architecture Mapping)

**Agent:** Explorer 3 (`teamwork_preview_explorer`)  
**Working Directory:** `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_3`  
**Target Architecture:** GameNativeXR Dev-Update Baseline  
**Handoff Type:** Hard Handoff (Task Complete)  
**Date:** 2026-08-03  

---

## 1. Observation

Direct observations from inspecting the `Dev-Update` codebase:

1. **Rider MCP Tooling Status:**
   - Interactive Rider MCP lazy tools (`call_mcp_tool`) were unavailable in this execution environment. Workspace exploration was executed using canonical filesystem inspection tools (`view_file`, `find_by_name`, `grep_search`).

2. **Focus Area 1 (Input & Overlays Baseline):**
   - `app/src/main/java/com/winlator/xr/XrActivity.java`:
     - Line 104: `System.loadLibrary("xr");`
     - Lines 331–359: `updateFrame()` directly reads OpenXR inputs via JNI (`getAxes()`, `getButtons()`), calls `updateXrAPI()`, updates `xrController`, and mutates `XServer` state inside an `XLock` block. Input handling lacks an explicit mode router or transition state cleanup.
   - `app/src/main/java/com/winlator/xr/XrRenderer.java`:
     - Lines 218–244: `renderDialog()` draws `XrContentDialog.getFrontInstance()`.
     - Observation: There is no OpenGL reticle or ray cursor rendering pass inside `XrRenderer.java`.
   - `app/src/main/java/com/winlator/xr/XrController.java`:
     - Lines 83–116: `updateAndroidInput()` passes DPAD / Enter / Back events to `XrContentDialog`.
     - Lines 191–226: `updateMouseAxes()` applies relative mouse movement (`dx`, `dy`) using `meter2px` or head orientation.
     - Observation: Mode switches do not release held mouse buttons or keys; no release-held guarantee exists.
   - `app/src/main/java/com/winlator/xr/XrKeyboard.java`:
     - Lines 31–102: Wraps an Android `EditText` (`text`) and relies on system Android IME (`AppUtils.showKeyboard()`).
     - Observation: System IME pop-up is non-functional / invisible in 3D VR mode.
   - `app/src/main/java/com/winlator/xr/ui/XrContentDialog.java`:
     - Lines 91–126: `redraw()` renders an Android `View` hierarchy into an offscreen `Bitmap` and `Drawable`, which `XrRenderer` renders in 3D space.

3. **Focus Area 2 (Existing Mod Architecture):**
   - `app/src/main/java/app/gamenative/data/ModInstall.kt`:
     - Lines 58–289: Defines Room entities `ModProfile`, `ModInstall`, `ModProfileInstallState`, `ModPlacementRecipe`, and `ModOverwriteManifest`.
   - `app/src/main/java/app/gamenative/mods/ModMaterializer.kt`:
     - Lines 53–231: `scanConflicts()`, `apply()`, and `repairMissingTargets()`.
     - Lines 233–255: `restoreBackups()` cleanly restores original file backups from `ModOverwriteManifest`.
   - `app/src/main/java/app/gamenative/mods/ModTargetResolver.kt`:
     - Lines 36–61: `resolve()` resolves target roots (`GAME_DIR`, `WINE_C`, `DOCUMENTS`, `MY_GAMES`, `APPDATA_ROAMING`, `APPDATA_LOCAL`, `APPDATA_LOCALLOW`) and enforces `isInsideOrEqual` canonical root containment.
   - `app/src/main/java/com/winlator/xr/ModdingUtils.java`:
     - Lines 69–108: `updateReshade()` hardcodes extraction of `reshade-directx.tzst` and file copies (`dxgi.dll` -> `d3d11.dll`).

4. **Focus Area 3 (Existing XR Bridge):**
   - `app/src/main/java/com/winlator/xr/api/XrAPI.java`:
     - Lines 166–184: Reads requested version string from `/data/data/app.gamenative/files/imagefs/tmp/xr/version` and instantiates `XrVersion01`..`04`.
   - `app/src/main/java/com/winlator/xr/api/XrVersion01.java` through `XrVersion04.java`:
     - Encodes HMD / controller poses and buttons into ASCII strings sent via local UDP socket packets to ports 7872 / 7873.

---

## 2. Logic Chain

1. **Pillar 2 Input Architecture Derivation:**
   - *From Observation 2.1 & 2.3:* `XrActivity` fetches raw OpenXR poses/buttons every frame, but inputs are passed directly to `XrController` without structured state routing, leading to input leakage and stuck keys on mode switch.
   - *Reasoning:* A dedicated `PreGameInputRouter` with 6 explicit interaction modes (`ANDROID_OVERLAY`, `GUEST_POINTER`, `GUEST_TEXT`, `GUEST_NAVIGATION`, `GAME_INPUT`, `SYSTEM_MENU`) must sit between JNI pose polling and `XServer`/UI dispatch.
   - *Reasoning for Release-Held Guarantee:* To prevent stuck keys/buttons during UI transitions, `PreGameInputRouter.setMode()` must track active pressed buttons and emit explicit release events (`setButton(false)`, `setKeyRelease()`) for the old mode before activating the new mode.
   - *Reasoning for Reticle & Keyboard Overlay:* Since `XrRenderer` already renders `XrContentDialog` drawables in OpenGL space (Observation 2.5), `XrKeyboardOverlay` can be implemented as an Android View layout hosted in `XrContentDialog`. `XrRenderer` will add an absolute ray-to-screen reticle calculation pass to render a visible pointer cursor on the 3D virtual screen.

2. **Pillar 4 VR Mod & Tracking Architecture Derivation:**
   - *From Observation 3.1–3.3:* `ModMaterializer`, `ModTargetResolver`, and `ModInstall` provide a complete, secure file materialization engine with hash verification, backup manifests (`ModOverwriteManifest`), and containment safety (`isInsideOrEqual`).
   - *From Observation 3.4:* `ModdingUtils.java` currently uses hardcoded `.tzst` archive unpackers for ReShade and TrackIR.
   - *Reasoning:* We can replace `ModdingUtils` hardcoded paths by integrating `VrModResolver` and `VrHookPlanExecutor` directly with `ModMaterializer`.
   - *Reasoning for Hook Execution & Rollback:* `VrModResolver` parses `vr_mod_manifest.json`, validates executable architecture (`x86` vs `x64`) and SHA-256 hashes, and generates a `VrHookPlan`. `VrHookPlanExecutor` executes file placement via `ModMaterializer.apply()`, configures launch-local Wine DLL overrides (`WINEDLLOVERRIDES`), monitors the guest handshake, and invokes `ModMaterializer.restoreBackups()` for atomic rollback on failure.
   - *Reasoning for Tracking Modes:* `TrackingModeResolver` evaluates title facts and assigns `FLAT_3DOF` (default orientation-only quad presentation), `NATIVE_OPENXR_6DOF` (native OpenXR/OpenVR games), or `MODDED_6DOF` (matched VR mod manifest + handshake).

---

## 3. Caveats

1. **Rider MCP Pending Checks:** Rider MCP interactive diagnostics were unavailable in this environment. Rider symbol inspections and Rider-based unit test runs remain pending. Authoritative shell validation (`.\gradlew.bat :app:testModernXrDebugUnitTest`) serves as the verification standard.
2. **Native `libxr.so` Provenance:** `libxr.so` is a prebuilt binary in `app/src/main/jniLibs/arm64-v8a`. `XrHostBridge` legacy UDP support (`0.1`..`0.4`) is verified via Java wrappers, but native C++ OpenXR host code changes are gated on native source recovery.
3. **Guest Text-Focus Detection:** Wine X11 window focus detection relies on observed X11 WM focus events. A 250ms debounce window is specified to handle ambiguous guest window focus changes.

---

## 4. Conclusion

Existing components (`XrActivity`, `XrRenderer`, `XrController`, `XrKeyboard`, `XrContentDialog`, `ModMaterializer`, `ModTargetResolver`, `XrAPI`) provide a strong baseline foundation for GameNativeXR Pillars 2 & 4.

1. **Pillar 2 Boundaries Defined:**
   - `PreGameInputRouter`: 6 interaction modes, explicit state machine transitions, release-held button guarantees.
   - Absolute Ray-to-Screen Reticle: Controller ray cast plane intersection, rendered in `XrRenderer`, single-controller operable.
   - `XrKeyboardOverlay`: Visible virtual QWERTY layout, special keys, password mask, hosted in `XrContentDialog`.
   - Guest Text-Focus Signal: Wine X11 focus listener with 250ms debounce.

2. **Pillar 4 Boundaries Defined:**
   - `TrackingModeResolver`: Resolves `FLAT_3DOF`, `NATIVE_OPENXR_6DOF`, and `MODDED_6DOF`.
   - `VrModResolver`: Versioned JSON manifests, target PE architecture & SHA-256 matching.
   - `VrHookPlanExecutor`: 5 hook strategies (`LAUNCHER_EXECUTABLE`, `PROXY_DLL`, `RUNTIME_REGISTRATION`, `OPENVR_ADAPTER`, `LINUX_WRAPPER`), pre-launch materialization via `ModMaterializer`, handshake timeout monitoring, atomic backup rollback.
   - `GuestVrRuntimeAdapter`: Contracts for `WindowsOpenXrAdapter` and `OpenVrAdapter`.
   - `XrHostBridge`: Dual-path host bridge (legacy UDP `0.1`..`0.4` + modernized high-rate 6DoF channels).

All technical details, data flows, and Kotlin/Java interface declarations are documented in `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_3/analysis.md`.

---

## 5. Verification Method

1. **Inspect Artifact Files:**
   - `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_3/analysis.md`
   - `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_3/handoff.md`
   - `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_3/progress.md`

2. **Execute Unit Tests:**
   ```powershell
   .\gradlew.bat :app:testModernXrDebugUnitTest
   ```

3. **Invalidation Conditions:**
   - If `./gradlew :app:testModernXrDebugUnitTest` fails due to syntax errors or missing class boundaries.
   - If any proposed contract violates `GameNativeXR_Architecture_Plan.md` Section 7 or Section 9 requirements.
