# PROJECT: GameNativeXR Architecture and Launch Pipeline Implementation

## Document Status
- **Baseline Worktree**: `Dev-Update`
- **Target OS**: Android (Meta Quest 2 / Quest 3)
- **Primary Package Roots**: `app/src/main/java/app/gamenative`, `app/src/main/java/com/winlator`
- **Verification Command**: `.\gradlew.bat :app:testModernXrDebugUnitTest`

---

## Architecture

The GameNativeXR launch architecture is structured around a **Unified Launch Contract** and **4 Core Pillars**:

```
                              ┌─────────────────────────────────────────┐
                              │            Steam Library UI             │
                              └────────────────────┬────────────────────┘
                                                   │ LaunchRequest
                                                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         GameLaunchCoordinator                                          │
│                                (19-State Explicit State Machine)                                       │
│                                                  │                                                     │
│   ┌───────────────────────────┬──────────────────┴────────────────┬───────────────────────────┐        │
│   ▼                           ▼                                   ▼                           ▼        │
│ ExecutableInspector    QuestDeviceDetector             TrackingModeResolver             VrModResolver  │
│ (PE x86/x64 parser)   (Quest 2/3 classification)      (3DoF / 6DoF selection)          (Manifests & Hooks)
│   │                           │                                   │                           │        │
│   └───────────────────────────┴──────────────────┬────────────────┴───────────────────────────┘        │
│                                                  │ Immutable LaunchPlan                                │
│                                                  ▼                                                     │
│                                PreGameInputRouter & XrKeyboard                                         │
│                                (6 Interaction Modes & XR Overlay)                                      │
│                                                  │                                                     │
│                                                  ▼                                                     │
│                                   Existing Wine/XServer Execution                                      │
│                                   (Bionic / Glibc Launch Backends)                                     │
└──────────────────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                                   │ Structured Logs / Events
                                                   ▼
                                           DiagnosticSession
                                      (Logcat + JSONL + Ring Buffer)
```

---

## Feature Inventory

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | `GameLaunchCoordinator` & State Machine | 19-state explicit launch state machine with event listener framework | M1 | Plan §5.3 |
| 2 | `ExecutableInspector` | Read-only PE header parser for machine type (x86/x64), SHA-256, imported VR APIs | M1 | Plan §5.2 |
| 3 | Immutable `LaunchRequest` & `LaunchPlan` | Immutable model capturing game ID, paths, mode, container, and resolved plan | M1 | Plan §5.1 |
| 4 | 5-Level Precedence Resolver | Deterministic precedence: Baseline -> Hardware -> Compatibility -> Mod -> User Override | M1 | Plan §5.4 |
| 5 | `LaunchFailureCategory` Taxonomy | 18 structured failure categories with classification methods | M1 | Plan §5.5 |
| 6 | `QuestDeviceDetector` | Quest 2 & Quest 3 classifier using Build, SoC, GPU renderer (Adreno 6xx/7xx), rules | M2 | Plan §6.1 |
| 7 | `HardwareExecutionProfileResolver` | Pure resolver mapping Quest device descriptors to versioned profile JSON schemas | M2 | Plan §6.2 |
| 8 | `ContainerData` Profile Integration | Non-mutating application of resolved profile parameters into `ContainerData` | M2 | Plan §6.4 |
| 9 | Early `DiagnosticSession` Init | Timber & CrashHandler init immediately after `super.onCreate()` in `PluviaApp` | M3 | Plan §8.1 |
| 10| `ProcessOutputBus` | Multi-subscriber stdout/stderr bus replacing global static callbacks in `ProcessHelper` | M3 | Plan §8.4 |
| 11| Structured Log Sinks | Multi-sink logging: Android Logcat, rotating JSONL files, in-memory ring buffer | M3 | Plan §8.2 |
| 12| `SecretRedactor` | Automated redaction of Steam tokens, passwords, TOTPs, auth headers before log sinks | M3 | Plan §8.5 |
| 13| `PreGameInputRouter` | 6 interaction modes with explicit release-held guarantees on mode transition | M4 | Plan §7.2 |
| 14| Controller Ray-to-Screen Pointer | Virtual screen plane intersection ray reticle rendered in 3D XR space | M4 | Plan §7.3 |
| 15| Controller-Operable `XrKeyboardOverlay` | Visible QWERTY layout, special keys, password masking hosted in `XrContentDialog` | M4 | Plan §7.4 |
| 16| Guest Text-Focus Event Signal | Wine X11 window focus detection listener with 250ms debounce for auto-keyboard | M4 | Plan §7.4 |
| 17| `TrackingModeResolver` | Mode selector for `FLAT_3DOF`, `NATIVE_OPENXR_6DOF`, and `MODDED_6DOF` | M5 | Plan §9.1 |
| 18| Versioned `VrModResolver` | VR mod manifest parser, executable path, PE architecture, and SHA-256 hash matcher | M5 | Plan §9.4 |
| 19| `VrHookPlanExecutor` | 5 hook strategies executor with pre-launch `ModMaterializer` placement & rollback | M5 | Plan §9.4 |
| 20| `GuestVrRuntimeAdapter` & `XrHostBridge` | Abstract runtime bridge for Windows OpenXR & OpenVR, control/pose channels | M5 | Plan §9.3 |
| 21| `XServerScreen` Refactoring | Incremental extraction of launch orchestration into `GameLaunchCoordinator` | M6 | Plan §3.1, §5 |
| 22| Comprehensive Unit Tests | Full test suite across all 4 pillars passing `:app:testModernXrDebugUnitTest` cleanly | M7 | Plan §13 |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Unified Launch Contract Foundations | `GameLaunchCoordinator`, `ExecutableInspector`, launch state machine, immutable `LaunchRequest`/`LaunchPlan`, 5-level precedence resolver, failure taxonomy | None | **IMPLEMENTED — isolated contract; real XServer launch delegation remains M6** |
| M2 | Pillar 1 — Quest Hardware Execution Profiles | `QuestDeviceDetector`, `HardwareExecutionProfileResolver`, Quest 2/3 profile JSON schemas, `ContainerData` profile integration | M1 | **IMPLEMENTED — profile resolution is non-mutating and not yet applied to a live launch** |
| M3 | Pillar 3 — Diagnostic Pipeline | `DiagnosticSession`, early `PluviaApp` init, `ProcessOutputBus`, Logcat/JSONL sinks, `SecretRedactor`, upgraded crash reporting | M1 | **IMPLEMENTED — boot, environment setup, guest termination, crash, and process-output coverage** |
| M4 | Pillar 2 — Input Router & XR Keyboard | `PreGameInputRouter` (6 modes), controller ray pointer reticle, `XrKeyboardOverlay`, guest text-focus signal, input release reset | M1 | **IN PROGRESS — router/adapter, tested ray-to-screen mapping, deterministic quick-menu navigation, and manual visible keyboard implemented; live pre-game routing, reticle rendering, and text-focus signal remain** |
| M5 | Pillar 4 — Tracking Modes & VR Mod Resolver | `TrackingModeResolver`, `VrModResolver`, `VrHookPlanExecutor` (5 hook strategies), `GuestVrRuntimeAdapter`, `XrHostBridge` | M1, M2, M3 | **PLANNED — native tracking, passthrough, and frame work remain gated by `docs/NATIVE_XR_PROVENANCE_AUDIT.md`; pure contracts may proceed independently** |
| M6 | Launch Pipeline Extraction & Integration | Refactor `XServerScreen.kt` to delegate launch lifecycle to `GameLaunchCoordinator`, integrate all pillars into execution flow | M1, M2, M3, M4, M5 | PLANNED |
| M7 | Final Verification & Test Hardening | End-to-end integration tests, unit test suite for all pillars, zero-failure verification via `./gradlew :app:testModernXrDebugUnitTest` | M1–M6 | PLANNED |

### Native XR source gate

**BLOCKED.** `libxr.so` is a tracked AArch64 OpenXR/JNI binary with identifiable
history and ABI exports, but this worktree has no matching native source or
CMake target. See `docs/NATIVE_XR_PROVENANCE_AUDIT.md`. This blocks native
tracking, passthrough, and frame-pipeline changes—not the independent launch,
diagnostic, controller-navigation, or manifest-contract work.

---

## Interface Contracts

### 1. `GameLaunchCoordinator` ↔ Launch Clients
```kotlin
package app.gamenative.launch

interface GameLaunchCoordinator {
    val currentSessionId: String
    val currentLaunchId: String
    val currentState: LaunchState

    fun launch(request: LaunchRequest, callback: LaunchCallback): LaunchJob
    fun cancelLaunch(launchId: String, reason: String)
    fun addStateChangeListener(listener: LaunchStateChangeListener)
    fun removeStateChangeListener(listener: LaunchStateChangeListener)
}

interface LaunchCallback {
    fun onStateChanged(previousState: LaunchState, newState: LaunchState, event: LaunchEvent)
    fun onSuccess(result: LaunchSuccessResult)
    fun onFailure(failure: LaunchFailureResult)
}
```

### 2. `ExecutableInspector` ↔ `LaunchPlanResolver`
```kotlin
package app.gamenative.launch.inspect

interface ExecutableInspector {
    fun inspect(gameRoot: File, exePath: String): ExecutableInspectionResult
}

data class ExecutableInspectionResult(
    val canonicalPath: String,
    val fileSizeBytes: Long,
    val sha256Hash: String,
    val peArchitecture: PeArchitecture, // X86, X64, ARM64, UNKNOWN
    val isLauncherExecutable: Boolean,
    val importedVrApis: List<VrApiImport>, // OPENXR, OPENVR, OCULUS_VR, NONE
    val readError: String? = null
)
```

### 3. `QuestDeviceDetector` ↔ `HardwareExecutionProfileResolver`
```kotlin
package app.gamenative.hardware

interface QuestDeviceDetector {
    fun detectDevice(): QuestDeviceDescriptor
}

data class QuestDeviceDescriptor(
    val buildManufacturer: String,
    val buildModel: String,
    val buildDevice: String,
    val buildProduct: String,
    val socManufacturer: String?,
    val socModel: String?,
    val gpuRenderer: String,
    val isMetaXrRuntime: Boolean,
    val classifiedDevice: ClassifiedQuestDevice, // QUEST_2, QUEST_3, UNKNOWN_META, NOT_QUEST
    val matchedRuleId: String,
    val confidence: Double
)
```

### 4. `PreGameInputRouter` ↔ Overlays and Guest
```kotlin
package com.winlator.xr.input

interface PreGameInputRouter {
    val currentMode: InputRouterMode // ANDROID_OVERLAY, GUEST_POINTER, GUEST_TEXT, GUEST_NAVIGATION, GAME_INPUT, SYSTEM_MENU

    fun setMode(newMode: InputRouterMode)
    fun processControllerInput(leftHand: ControllerPoseState?, rightHand: ControllerPoseState?): InputRouterResult
    fun resetAllHeldInputs()
}
```

### 5. `VrModResolver` & `VrHookPlanExecutor` ↔ Launcher
```kotlin
package com.winlator.xr.modding

interface VrModResolver {
    fun resolveVrMod(appId: String, exeResult: ExecutableInspectionResult, installedMods: List<ModInstall>): VrModResolutionResult
}

interface VrHookPlanExecutor {
    fun executePlan(plan: VrHookPlan): VrHookExecutionResult
    fun rollbackPlan(plan: VrHookPlan): VrRollbackResult
}
```

---

## Code Layout

```
app/src/main/java/
├── app/gamenative/
│   ├── launch/                           # Unified Launch Contract (M1 - DONE, M6)
│   │   ├── GameLaunchCoordinator.kt
│   │   ├── LaunchState.kt
│   │   ├── LaunchRequest.kt
│   │   ├── LaunchPlan.kt
│   │   ├── LaunchPrecedenceResolver.kt
│   │   ├── LaunchFailureCategory.kt
│   │   └── inspect/
│   │       ├── ExecutableInspector.kt
│   │       └── PeHeaderParser.kt
│   ├── hardware/                         # Pillar 1: Hardware Profiles (M2)
│   │   ├── QuestDeviceDetector.kt
│   │   ├── QuestDeviceDescriptor.kt
│   │   ├── HardwareExecutionProfileResolver.kt
│   │   └── HardwareProfileSchema.kt
│   └── diagnostics/                      # Pillar 3: Diagnostic Pipeline (M3)
│       ├── DiagnosticSession.kt
│       ├── ProcessOutputBus.kt
│       ├── SecretRedactor.kt
│       ├── FailureTaxonomy.kt
│       └── sinks/
│           ├── LogcatSink.kt
│           ├── JsonLineSink.kt
│           └── RingBufferSink.kt
└── com/winlator/xr/
    ├── input/                            # Pillar 2: Input Router & Keyboard (M4)
    │   ├── PreGameInputRouter.kt
    │   ├── RayPointerReticle.kt
    │   ├── XrKeyboardOverlay.kt
    │   └── GuestTextFocusListener.kt
    ├── tracking/                         # Pillar 4: Tracking Modes (M5)
    │   ├── TrackingModeResolver.kt
    │   └── TrackingMode.kt
    └── modding/                          # Pillar 4: VR Mod Resolver & Hooks (M5)
        ├── VrModResolver.kt
        ├── VrModManifest.kt
        ├── VrHookPlanExecutor.kt
        ├── GuestVrRuntimeAdapter.kt
        └── XrHostBridge.kt
```
