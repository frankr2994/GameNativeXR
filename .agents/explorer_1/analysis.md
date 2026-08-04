# GameNativeXR Launch Pipeline Analysis & Unified Launch Contract Specification

## Executive Summary

This document provides a comprehensive technical investigation of the existing launch pipeline in the GameNativeXR codebase (`Dev-Update` worktree at commit `6cde5d4b99ad1ddf3aedcaaf9eb6982c4261ffaa`) and specifies the architecture, interface contracts, class boundaries, and state machine for the target **Unified Launch Contract** as detailed in Section 5 of `GameNativeXR_Architecture_Plan.md`.

---

## 1. Existing Launch Orchestration Analysis (`XServerScreen.kt`)

### 1.1 Overview & Responsibilities

`XServerScreen.kt` (`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`, 5879 lines) currently acts as a monolithic controller that owns both UI presentation (Compose) and low-level launch orchestration.

When a user launches a game, `XServerScreen` executes the following sequence:

```
[XServerScreen Composable]
       │
       ├── 1. Acquire Container (ContainerUtils.getContainer)
       ├── 2. Initialize UI State & Suspension Policy
       ├── 3. setupXEnvironment(...)
       │      ├── ProcessHelper.hardKillStaleWineProcesses()
       │      ├── ImageFs & ContentsManager Sync
       │      ├── Environment Variables Construction (EnvVars)
       │      ├── TrackIR & ReShade Mod Setup (ModdingUtils)
       │      ├── Select Launcher Backend (BionicProgramLauncherComponent vs GlibcProgramLauncherComponent)
       │      ├── GameFixesRegistry.applyFor(...)
       │      ├── Command Resolution (getWineStartCommand)
       │      ├── Pre-Install Steps Resolution (PreInstallSteps.getPreInstallCommands)
       │      ├── Component Extraction / Unpack Callback setup
       │      ├── Instantiate XEnvironment & Components:
       │      │     ├─ SysVSharedMemoryComponent
       │      │     ├─ XServerComponent
       │      │     ├─ NetworkInfoUpdateComponent
       │      │     ├─ SteamClientComponent
       │      │     ├─ Audio: ALSAServerComponent or PulseAudioComponent
       │      │     ├─ Graphics: VirGLRendererComponent or VortekRendererComponent
       │      │     ├─ GuestProgramLauncherComponent (Bionic / Glibc)
       │      │     └─ WineRequestComponent
       │      ├── Setup Wine System Files & DXVK/VKD3D Extraction
       │      ├── Steam Credentials Setup (SteamTokenLogin if isLaunchRealSteam)
       │      └── environment.startEnvironmentComponents()
       ├── 4. Async Steam Ticket Request (SteamService.getEncryptedAppTicket)
       ├── 5. Achievement Watcher Setup (AchievementWatcher)
       └── 6. Start WinHandler IPC (xServer.winHandler.start())
```

### 1.2 Step-by-Step Breakdown of `XServerScreen` Launch Flow

#### Step 1: Pre-Launch State Reset & Stale Process Cleanup
- `ProcessHelper.hardKillStaleWineProcesses()`: Scans `/proc` for existing `wine` / `exe` processes and issues `killProcess()` (SIGKILL). Waits up to 5 seconds for complete termination.
- `ProcessHelper.removeAllDebugCallbacks()`: Clears all global debug log listeners.

#### Step 2: Environment Variable Preparation (`EnvVars`)
- Sets system locale (`LC_ALL`), Mesa debug flags (`MESA_DEBUG=silent`, `MESA_NO_ERROR=1`), `WINEPREFIX`.
- Sets SDL controller env vars (`SDL_XINPUT_ENABLED`, `SDL_DIRECTINPUT_ENABLED`, `SDL_JOYSTICK_HIDAPI`, etc.) based on `container.isSdlControllerAPI` and `container.inputType`.
- Evaluates debug/diagnostic logging preferences:
  - If `diagnostics == true`: sets `WRAPPER_DIAG=1`, `WRAPPER_LOG_LEVEL=info`, `VKD3D_DEBUG=warn`, `DXVK_LOG_LEVEL=info`, `WINEDEBUG=+vulkan`.
  - Else: sets `WINEDEBUG` to `+<channels>` or `-all`.
- Registers a debug log callback on `ProcessHelper` to append log output to `wine_debug.log`.

#### Step 3: Mod Pre-Unpack (TrackIR & ReShade)
- `ModdingUtils.unpackTrackIR(context)`: Unpacks TrackIR DLLs and launches `getRuntimeForTrackIR()` if `container.isXrUseTrackIR()`.
- `ModdingUtils.updateReshade(...)`: Copies/updates ReShade DLLs and DXGI proxy if `container.isXrUseReshade()`.

#### Step 4: Launcher Backend Selection & Game Fixes
- Queries `container.getContainerVariant()`:
  - If `"glibc"` -> instantiates `GlibcProgramLauncherComponent`.
  - Else (`"bionic"`) -> instantiates `BionicProgramLauncherComponent`.
- Applies game-specific overrides via `GameFixesRegistry.applyFor(context, appId, container)`.
- Sets WoW64 mode, container instance, `WineInfo`, and `SteamAppId` on `guestProgramLauncherComponent`.

#### Step 5: Command Resolution (`getWineStartCommand`)
Resolves the launch command string based on `gameSource`:
- **Steam**:
  - Sets `container.executablePath` if empty using `SteamService.getInstalledExe(gameId)`.
  - Writes `ColdClientLoader.ini` via `SteamUtils.writeColdClientIni` if not using legacy DRM.
  - Support modes: BionicSteam (`C:\Program Files (x86)\Steam\steamapps\common\...`), RealSteam (`steam.exe -applaunch ...`), or LegacyDRM.
- **Epic**:
  - Queries `EpicService`, resolves installed executable path.
  - Builds launch arguments with auth parameters (`EpicService.buildLaunchParameters`).
  - Sets `guestProgramLauncherComponent.workingDir`.
- **Amazon**:
  - Parses `fuel.json` manifest if present for command, working directory, and args.
  - Fallback: heuristic executable choice via `ExecutableSelectionUtils.choosePrimaryExeFromDisk`.
  - Sets FuelPump environment variables (`FUEL_DIR`, `AMAZON_GAMES_SDK_PATH`, `AMAZON_GAMES_FUEL_ENTITLEMENT_ID`, `AMAZON_GAMES_FUEL_PRODUCT_SKU`, `AMAZON_GAMES_FUEL_DISPLAY_NAME`).
  - Deploys SDK DLLs to prefix via `AmazonSdkManager.deploySdkToPrefix`.
- **GOG**:
  - Delegates to `GOGService.getGogWineStartCommand(...)`.
- **Custom Game**:
  - Maps A: drive to custom game folder, resolves relative path via `CustomGameScanner.findUniqueExeRelativeToFolder`.
- **Final Command Format**:
  `wine explorer /desktop=shell,<screenInfo> winhandler.exe <resolved_command> <execArgs>`

#### Step 6: Pre-Install Steps & Chain Resolution
- If `preInstallCommands` exist (e.g., DirectX/VCRedist installers), chains them using recursive termination callbacks:
  ```kotlin
  fun chainPreInstallSteps(remaining: List<PreInstallSteps.PreInstallCommand>) {
      // Sets current pre-install executable as guestExecutable
      // On termination, marks step done, issues wineserver -k, advances to next step
  }
  ```

#### Step 7: Environment Components Setup & Execution
- Adds environment components to `XEnvironment`:
  1. `SysVSharedMemoryComponent` (Unix socket `/tmp/sysvshm_server`)
  2. `XServerComponent` (Unix socket `/tmp/.X11-unix/X0`)
  3. `NetworkInfoUpdateComponent`
  4. `SteamClientComponent` (local emulated Steam client socket, if not RealSteam/BionicSteam)
  5. Audio: `ALSAServerComponent` or `PulseAudioComponent`
  6. Graphics: `VirGLRendererComponent` or `VortekRendererComponent`
  7. `GuestProgramLauncherComponent` (Bionic or Glibc backend)
  8. `WineRequestComponent`
- Calls `environment.startEnvironmentComponents()` which starts all servers and executes the guest launcher backend.
- Starts `xServer.winHandler.start()` on `Dispatchers.IO` for window & process management IPC.

### 1.3 Architectural Deficiencies in Existing Launch Flow

1. **Monolithic UI & Orchestration Coupling**: `XServerScreen.kt` combines Compose layout logic, brightness management, controller UI overlays, LSFG settings, performance HUD, and complex multi-step backend launch orchestration in a single 5879-line file.
2. **Implicit & Unobservable State Machine**: Launch states are implicit (driven by coroutines, callbacks, and inline boolean flags). There is no centralized state machine, transition event bus, or execution state model.
3. **Global Static State Pollution**:
   - `ProcessHelper.removeAllDebugCallbacks()` clears debug callbacks globally across all processes/launches.
   - `ContainerUtils.setContainerDefaults()` mutates global static fields in `DefaultVersion` (`DefaultVersion.VARIANT`, `DefaultVersion.DXVK`, etc.), causing race conditions if container configurations differ.
   - `ProcessHelper.pauseAllWineProcesses()` uses static process listing.
4. **Unstructured Error Handling**: Failures during Wine prep, command resolution, driver loading, or process startup throw raw exceptions or emit generic strings via `onGameLaunchError`, causing black screens without structured classification.
5. **No Executable Identity Verification**: Executables are launched without PE header parsing, architecture validation (x86 vs x64), SHA-256 calculation, or imported VR API detection.

---

## 2. Existing Launch Backends Analysis

The project contains three launcher backend classes and one process management helper:

```
                  [EnvironmentComponent]
                            │
              [GuestProgramLauncherComponent]
                            ├── BionicProgramLauncherComponent
                            └── GlibcProgramLauncherComponent

              [ProcessHelper] (Static utility)
```

### 2.1 `GuestProgramLauncherComponent` (Base Backend)
- **File**: `com/winlator/xenvironment/components/GuestProgramLauncherComponent.java` (389 lines)
- **Role**: Base class for guest launchers. Stores execution context (`guestExecutable`, `workingDir`, `bindingPaths`, `envVars`, `box86Version`, `box64Version`, `box86Preset`, `box64Preset`, `wow64Mode`, `wineInfo`, `container`, `terminationCallback`).
- **Core Mechanism**:
  - `start()`: Extracts box86/box64 files if needed, calls `execGuestProgram()`.
  - `exec(...)`: Constructs PRoot command:
    `libproot.so --kill-on-exit --rootfs=<rootDir> --cwd=/home/xuser --bind=/dev --bind=/tmp/shm:/dev/shm --bind=/proc --bind=/sys --bind=<user_drives> /usr/bin/env <envVars> box64 <guestExecutable>`
  - Uses JNI / `ProcessHelper.exec(...)` to start the PRoot process.
  - `stop()`: Kills process PID (`Process.killProcess(pid)`) and scans sub-processes (`ProcessHelper.listSubProcesses()`).

### 2.2 `BionicProgramLauncherComponent` (Android Bionic Backend)
- **File**: `com/winlator/xenvironment/components/BionicProgramLauncherComponent.java` (698 lines)
- **Role**: Native launcher backend for Android Bionic libc. Used when `containerVariant == "bionic"` (the default and required path for modern Android / Quest builds).
- **Core Mechanism**:
  - Handles WoW64 and Arm64EC execution:
    - If `wineInfo.isArm64EC()`: extracts FEXCore / WoWbox64 DLLs into `system32` and sets `HODLL=libwow64fex.dll` or `wowbox64.dll`.
    - Else: extracts `box64-<version>-bionic.tzst` and executes `bin/box64 <guestExecutable>`.
  - Bionic Environment Preloads:
    - `LD_PRELOAD`: Preloads `libandroid-sysvshm.so`, `libevshim.so` (controller input shim), `libpreload-bionic.so`, and `libkgslshim.so` (if `XR_BUILD` is true).
    - `LsfgVkManager`: Configures LSFG Vulkan frame generation layer if enabled.
  - **BionicSteam Native Bridge Integration**:
    - If `container.isLaunchBionicSteam`:
      1. Calls `addRealSteamEnvVars(...)`: sets `WINESTEAMCLIENTPATH64`, `WINESTEAMCLIENTPATH`, `_STEAM_SETENV_MANAGER=1`, `STEAM_BASE_FOLDER`, `SteamUser`, `SteamGameId`, `SteamAppId`.
      2. Calls `bootstrapNativeSteamClient(...)`: invokes `app.gamenative.SteamBootstrap.INSTANCE.start(...)`, which loads `libsteamclient.so` into the Android process via JNI and connects to Steam network/IPC before Wine starts.

### 2.3 `GlibcProgramLauncherComponent` (Glibc Linux Rootfs Backend)
- **File**: `com/winlator/xenvironment/components/GlibcProgramLauncherComponent.java` (343 lines)
- **Role**: Backend for container variant `"glibc"`. Uses Glibc rootfs and Box64.
- **Core Mechanism**:
  - Extracts Box64 for Glibc (`box64-<version>.tzst`).
  - Sets Glibc-specific `LD_PRELOAD`: `libredirect.so` and `libandroid-sysvshm.so` from `glibc64Dir`.
  - Executes `usr/local/bin/box64 <guestExecutable>`.
  - Note: Glibc containers are explicitly blocked on modern Android builds (`BuildConfig.MODERN_ANDROID`).

### 2.4 `ProcessHelper.java` (Process Lifecycle & Output Management)
- **File**: `com/winlator/core/ProcessHelper.java` (614 lines)
- **Role**: Low-level process execution, signal delivery, sub-process enumeration, and stdout/stderr logging.
- **Key Methods**:
  - Process Signals: `suspendProcess(pid)` (SIGSTOP), `resumeProcess(pid)` (SIGCONT), `terminateProcess(pid)` (SIGTERM), `killProcess(pid)` (SIGKILL).
  - Bulk Operations: `terminateAllWineProcesses()`, `killAllWineProcesses()`, `hardKillStaleWineProcesses()`, `pauseAllWineProcesses()`, `resumeAllWineProcesses()`.
  - Process Enumeration:
    - `listSubProcesses()`: Runs `ps -A -o USER,PID,PPID,VSZ,RSS,WCHAN,ADDR,S,NAME` and filters by process UID.
    - `listRunningWineProcesses()`: Scans `/proc/<pid>/cmdline` for `"wine"` or `"exe"`.
  - Execution:
    - `exec(command, envp, workingDir, terminationCallback)`: Calls `Runtime.getRuntime().exec()`, uses reflection on `process.getClass().getDeclaredField("pid")` to extract native PID. Spawns `waitFor` thread.
    - `execWithOutput(command, envp, workingDir, includeStderr, timeoutSeconds)`: Uses `ProcessBuilder`, spawns `stdout-drainer` and `stderr-drainer` threads to prevent pipe deadlock.
  - Logging Callback Flaw: Uses a single static `ArrayList<Callback<String>> debugCallbacks`. `removeAllDebugCallbacks()` wipes all subscribers globally.

---

## 3. Setting Overrides & Precedence Analysis

### 3.1 Existing Component Inventory

#### 1. `BestConfigService.kt`
- **Location**: `app/src/main/java/app/gamenative/utils/BestConfigService.kt` (940 lines)
- **Role**: Fetches recommended container configurations from online API (`https://api.gamenative.app/api/best-config`).
- **Match Types**:
  - `"exact_gpu_match"`: Matches exact game title & exact GPU model. Applies all config fields.
  - `"gpu_family_match"`: Matches game title & GPU family. Applies all config fields.
  - `"fallback_match"`: Matches game title across different GPU families. **Filters out hardware-critical fields** (`graphicsDriver`, `graphicsDriverVersion`, `graphicsDriverConfig`, `dxwrapper`, `dxwrapperConfig`).
  - `"no_match"`: Returns empty config.
- **Mandatory GPU Family Overrides (`applyGpuFamilyOverrides`)**:
  - Adreno 6xx: Forces DXVK `1.11.1-sarek` (DXVK 2.x is incompatible).
  - Adreno 8 Elite Gen 5: Forces `Turnip Adreno Driver T26 (@Mr_Purple_666)`.
  - Adreno A12: Forces `Turnip v26.1.0 A12 Fix`.
- **Component Validation (`validateComponentVersions`)**: Validates that DXVK, VKD3D, Box64, WoWBox64, FEXCore, Wine, and graphics driver versions in the JSON response exist in packaged resources or installed manifests.

#### 2. `ContainerUtils.kt`
- **Location**: `app/src/main/java/app/gamenative/utils/ContainerUtils.kt` (1481 lines)
- **Role**: Container creation, defaults initialization, settings application, and wine registry modification.
- **Key Methods**:
  - `setContainerDefaults(context)`: Evaluates GPU probes (`GPUInformation.isTurnipCapable`, `isAdrenoA12`, `isAdreno8EliteGen5`, etc.) and mutates static fields on `DefaultVersion`.
  - `getDefaultContainerData()`: Loads base defaults from `PrefManager`.
  - `applyToContainer(context, container, containerData)`: Writes settings to container instance and updates `.wine/user.reg` Direct3D registry entries (`renderer`, `csmt`, `VideoPciDeviceID`, `VideoPciVendorID`, `OffScreenRenderingMode`, `strict_shader_math`, `VideoMemorySize`, `MouseWarpOverride`).

#### 3. `ContainerData.kt`
- **Location**: `com/winlator/container/ContainerData.kt` (305 lines)
- **Role**: Immutable data model representing container configuration (65+ fields including `containerVariant`, `wineVersion`, `emulator`, `fexcoreVersion`, `box64Version`, `graphicsDriver`, `dxwrapper`, `cpuList`, `xrButtonA..Y`, `xrRefreshRate`, `lsfgEnabled`).

### 3.2 Target 5-Level Deterministic Precedence Model

Section 5.4 of the Architecture Plan defines a 5-level precedence hierarchy for setting resolution:

```
Priority 5 (Highest): Explicit Per-Game User Overrides (ContainerData / ContainerExtra)
Priority 4          : Active External VR-Mod Requirements (VrModManifest)
Priority 3          : Exact Game Compatibility Profile (BestConfigService exact_gpu_match / gpu_family_match)
Priority 2          : Quest Hardware Execution Profile (QuestDeviceDetector -> QUEST_2 / QUEST_3 profile)
Priority 1 (Lowest) : Versioned GameNative Execution Baseline (Default ContainerData)
```

#### Precedence Resolution Rules:
1. Every field in the final `LaunchPlan` records its resolved value **and its source level** (1 through 5).
2. Level 2 (Hardware Profile) overrides Level 1 Baseline for headset-critical settings (`containerVariant`, `wineVersion`, `wow64Mode`, `emulator`, `fexCoreVersion`, `box64Version`, `graphicsDriver`, `dxwrapper`, `dxwrapperConfig`).
3. Level 3 (Compatibility Profile) overrides Level 2 for exact game/GPU matches. However, a **fallback match** (`fallback_match`) from Level 3 **MUST NOT** replace Level 2 hardware-critical graphics driver or DXVK settings.
4. Level 4 (VR Mod Requirements) can declare mandatory DLL overrides or environment variables required for VR hooks. If Level 4 conflicts with Level 5 (User Override), the system must flag a conflict and require deliberate user resolution rather than silently overwriting persistent settings.
5. Level 5 (User Override) represents persistent user edits on a container.

---

## 4. Interface Contracts & Class Boundaries for Unified Launch Contract

The Unified Launch Contract (Section 5) will be implemented under `app/src/main/java/app/gamenative/launch/`.

```
app/src/main/java/app/gamenative/launch/
├── GameLaunchCoordinator.kt
├── LaunchState.kt
├── LaunchEvent.kt
├── LaunchEventListener.kt
├── LaunchRequest.kt
├── LaunchPlan.kt
├── LaunchPlanResolver.kt
├── ExecutableInspector.kt
├── ExecutableIdentity.kt
├── PeArchitecture.kt
├── PeHeaderParser.kt
├── SettingPrecedenceResolver.kt
├── LaunchFailure.kt
└── LaunchFailureTaxonomy.kt
```

### 4.1 Launch State Machine (19 States)

```kotlin
package app.gamenative.launch

/**
 * 19 explicit states of the GameNativeXR Unified Launch Pipeline.
 */
enum class LaunchState {
    REQUEST_RECEIVED,
    INSTALL_RESOLVING,
    EXECUTABLE_INSPECTING,
    DEVICE_DETECTING,
    HARDWARE_PROFILE_RESOLVING,
    COMPATIBILITY_RESOLVING,
    VR_CAPABILITY_RESOLVING,
    RUNTIME_VALIDATING,
    MOD_PLAN_VALIDATING,
    FILES_MATERIALIZING,
    STEAM_PREPARING,
    CONTAINER_PREPARING,
    ENVIRONMENT_STARTING,
    GUEST_PROCESS_STARTING,
    WINDOW_OR_XR_HANDSHAKE_WAITING,
    RUNNING,
    STOPPING,
    CLEANUP,
    COMPLETED,
    FAILED;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED
    fun isRunningOrActive(): Boolean = this == RUNNING || this == WINDOW_OR_XR_HANDSHAKE_WAITING
}
```

### 4.2 Failure Taxonomy (Section 5.5)

```kotlin
package app.gamenative.launch

/**
 * Classified failure taxonomy for GameNativeXR launch errors.
 */
enum class LaunchFailureCategory {
    INSTALL_OR_EXECUTABLE_NOT_FOUND,
    UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE,
    MISSING_TRANSLATION_COMPONENT,
    INCOMPATIBLE_HARDWARE_PROFILE,
    MISSING_GRAPHICS_DRIVER_OR_DX_COMPONENT,
    STEAM_AUTH_OR_CLIENT_FAILURE,
    CONTAINER_PREPARATION_FAILURE,
    GUEST_PROCESS_START_FAILURE,
    GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW,
    FIRST_WINDOW_APPEARED_BUT_STOPPED_RESPONDING,
    NATIVE_XR_HOST_INITIALIZATION_FAILURE,
    VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH,
    MOD_HASH_OR_MANIFEST_MISMATCH,
    HOOK_PLACEMENT_OR_ROLLBACK_FAILURE,
    JAVA_EXCEPTION_OR_CRASH,
    NATIVE_CRASH,
    GUEST_CRASH,
    ANDROID_PROCESS_DEATH
}

sealed class LaunchFailure(
    val category: LaunchFailureCategory,
    val userMessage: String,
    val technicalDetails: String,
    val cause: Throwable? = null
) : Exception(userMessage, cause) {

    class ExecutableNotFound(path: String) : LaunchFailure(
        LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        "Executable not found: $path",
        "Target executable path does not exist on filesystem: $path"
    )

    class UnsupportedArchitecture(arch: String, path: String) : LaunchFailure(
        LaunchFailureCategory.UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE,
        "Unsupported executable architecture ($arch)",
        "Executable $path reports unsupported machine architecture: $arch"
    )

    class MissingComponent(componentName: String) : LaunchFailure(
        LaunchFailureCategory.MISSING_TRANSLATION_COMPONENT,
        "Required component missing: $componentName",
        "Translation or runtime component not found: $componentName"
    )

    class SteamFailure(details: String) : LaunchFailure(
        LaunchFailureCategory.STEAM_AUTH_OR_CLIENT_FAILURE,
        "Steam client / authentication error",
        details
    )

    class ProcessStartFailure(command: String, details: String) : LaunchFailure(
        LaunchFailureCategory.GUEST_PROCESS_START_FAILURE,
        "Failed to start game process",
        "Command: '$command'. Details: $details"
    )

    class ProcessEarlyExit(exitCode: Int, stdoutStderr: String) : LaunchFailure(
        LaunchFailureCategory.GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW,
        "Game process exited prematurely (code $exitCode)",
        "Exit code: $exitCode. Output log tail: $stdoutStderr"
    )

    class XrHandshakeTimeout(timeoutMs: Long, mode: String) : LaunchFailure(
        LaunchFailureCategory.VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH,
        "VR runtime handshake timed out",
        "Timed out waiting for XR handshake after ${timeoutMs}ms in mode $mode"
    )

    class ModMismatch(modId: String, expectedHash: String, actualHash: String) : LaunchFailure(
        LaunchFailureCategory.MOD_HASH_OR_MANIFEST_MISMATCH,
        "VR Mod hash verification failed",
        "Mod $modId expected executable SHA-256 $expectedHash, got $actualHash"
    )

    class SystemException(cause: Throwable) : LaunchFailure(
        LaunchFailureCategory.JAVA_EXCEPTION_OR_CRASH,
        "Internal launch error: ${cause.localizedMessage}",
        "Unhandled exception in launch coordinator: ${cause.stackTraceToString()}",
        cause
    )
}
```

### 4.3 Immutable Launch Request & Launch Plan Models

```kotlin
package app.gamenative.launch

import app.gamenative.data.GameSource

/**
 * Immutable launch request submitted by the caller (Library / UI / Steam).
 */
data class LaunchRequest(
    val launchId: String,
    val sessionId: String,
    val appId: String,
    val gameSource: GameSource,
    val requestedMode: RequestedLaunchMode = RequestedLaunchMode.AUTOMATIC,
    val isOffline: Boolean = false,
    val isDiagnosticLaunch: Boolean = false,
    val customExecPathOverride: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class RequestedLaunchMode {
    AUTOMATIC,
    FLAT_ONLY,
    NATIVE_VR,
    MODDED_VR
}

/**
 * Source of a resolved setting field in the precedence hierarchy.
 */
enum class SettingSource {
    BASE_DEFAULT,           // Level 1
    HARDWARE_PROFILE,       // Level 2
    COMPATIBILITY_PROFILE,  // Level 3
    VR_MOD_REQUIREMENT,     // Level 4
    USER_OVERRIDE           // Level 5
}

data class ResolvedSetting<T>(
    val value: T,
    val source: SettingSource,
    val description: String
)

/**
 * Immutable resolved launch plan produced before Wine / environment starts.
 */
data class LaunchPlan(
    val launchId: String,
    val request: LaunchRequest,
    val executableIdentity: ExecutableIdentity,
    val deviceDescriptor: String, // e.g. "QUEST_2" / "QUEST_3"
    val containerVariant: ResolvedSetting<String>,
    val wineVersion: ResolvedSetting<String>,
    val wow64Mode: ResolvedSetting<Boolean>,
    val emulator: ResolvedSetting<String>,
    val box64Version: ResolvedSetting<String>,
    val fexcoreVersion: ResolvedSetting<String>,
    val graphicsDriver: ResolvedSetting<String>,
    val graphicsDriverConfig: ResolvedSetting<String>,
    val dxwrapper: ResolvedSetting<String>,
    val dxwrapperConfig: ResolvedSetting<String>,
    val resolvedEnvVars: Map<String, String>,
    val trackingMode: String, // "FLAT_3DOF", "NATIVE_OPENXR_6DOF", "MODDED_6DOF"
    val activeModId: String? = null
)
```

### 4.4 Executable Inspector Interface & PE Parser Design

```kotlin
package app.gamenative.launch

import java.io.File

enum class PeArchitecture {
    X86_32,   // IMAGE_FILE_MACHINE_I386 (0x014c)
    X64_64,   // IMAGE_FILE_MACHINE_AMD64 (0x8664)
    ARM64,    // IMAGE_FILE_MACHINE_ARM64 (0xaa64)
    UNKNOWN
}

data class ExecutableIdentity(
    val canonicalPath: String,
    val relativePath: String,
    val fileSize: Long,
    val lastModified: Long,
    val sha256: String,
    val architecture: PeArchitecture,
    val isLauncher: Boolean,
    val importedLibraries: List<String>,
    val hasOpenXRImport: Boolean,
    val hasOpenVRImport: Boolean,
    val hasDirectXImport: Boolean
)

interface ExecutableInspector {
    /**
     * Read-only inspection of target executable PE headers, hash, and imports.
     */
    suspend fun inspectExecutable(exeFile: File, relativePath: String): ExecutableIdentity
}
```

#### PE Header Parsing Specification (`PeHeaderParser.kt`):
- Reads the first 4KB of the executable file using Java `RandomAccessFile` / `ByteBuffer` (Little Endian).
- Reads `IMAGE_DOS_HEADER`: Checks e_magic == `0x5A4D` ("MZ"). Reads `e_lfanew` offset to NT headers.
- Reads `IMAGE_NT_HEADERS`: Checks Signature == `0x00004550` ("PE\0\0").
- Reads `IMAGE_FILE_HEADER.Machine`:
  - `0x014c` -> `X86_32` (x86 32-bit)
  - `0x8664` -> `X64_64` (x64 64-bit)
  - `0xaa64` -> `ARM64`
- Reads `IMAGE_OPTIONAL_HEADER`:
  - Determines 32-bit (`0x10b` magic) vs 64-bit (`0x20b` magic) optional header layout.
  - Locates Data Directory #1 (Import Table RVA & Size).
  - Traverses Import Directory Descriptors to extract names of imported DLLs (e.g. `openxr_loader.dll`, `openvr_api.dll`, `d3d11.dll`, `d3d12.dll`, `vulkan-1.dll`, `dxgi.dll`).
- Calculates SHA-256 digest using standard Java `MessageDigest.getInstance("SHA-256")`.

### 4.5 Game Launch Coordinator Interface & State Machine

```kotlin
package app.gamenative.launch

import kotlinx.coroutines.flow.StateFlow

data class LaunchEvent(
    val launchId: String,
    val state: LaunchState,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val detail: String = "",
    val failure: LaunchFailure? = null
)

interface LaunchEventListener {
    fun onStateChanged(event: LaunchEvent)
}

interface GameLaunchCoordinator {
    val currentState: StateFlow<LaunchState>
    val currentLaunchId: StateFlow<String?>

    fun addListener(listener: LaunchEventListener)
    fun removeListener(listener: LaunchEventListener)

    /**
     * Initiates the 19-state launch sequence asynchronously.
     * Returns a TerminalLaunchResult (Success or Failure).
     */
    suspend fun executeLaunch(request: LaunchRequest): TerminalLaunchResult

    /**
     * Requests graceful cancellation / teardown of an ongoing launch.
     */
    suspend fun cancelLaunch(launchId: String, reason: String)
}

sealed class TerminalLaunchResult {
    data class Success(
        val launchId: String,
        val plan: LaunchPlan,
        val processPid: Int,
        val totalDurationMs: Long
    ) : TerminalLaunchResult()

    data class Failure(
        val launchId: String,
        val failedState: LaunchState,
        val failure: LaunchFailure,
        val totalDurationMs: Long
    ) : TerminalLaunchResult()
}
```

---

## 5. Summary of Key Architectural Decisions & Recommendations

1. **Decouple Orchestration from `XServerScreen`**:
   `XServerScreen.kt` should delegate launch state transitions to `GameLaunchCoordinator`. `XServerScreen` retains ownership of Compose UI renderables, viewports, and overlays, observing `GameLaunchCoordinator.currentState` to display splash screens and progress dialogs.

2. **Eliminate Global Static Side Effects**:
   - Replace static `ProcessHelper.debugCallbacks` with session-scoped subscriber streams (`ProcessOutputBus`).
   - Replace `ContainerUtils.setContainerDefaults()` global `DefaultVersion` mutations with immutable `LaunchPlan` decisions per launch instance.

3. **Enforce Read-Only PE Executable Inspection**:
   Execute `ExecutableInspector` before container startup to determine whether the target is 32-bit, 64-bit, or mixed WoW64, and detect OpenXR/OpenVR imports for VR mode selection.

4. **Implement Deterministic 5-Level Setting Precedence**:
   Ensure `SettingPrecedenceResolver` strictly enforces that weak online fallback recommendations (`fallback_match`) cannot overwrite hardware driver defaults.

5. **Classified Failure Diagnostics**:
   Every failure in the state machine must produce a `LaunchFailure` with a classified `LaunchFailureCategory`, powering user-facing recovery dialogs and diagnostic session JSON Lines.

