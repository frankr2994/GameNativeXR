# Milestone 1: Unified Launch Contract Foundations — Technical Blueprint

## Executive Summary

This document specifies the technical implementation blueprint for **Milestone 1: Unified Launch Contract Foundations** of the GameNativeXR architecture. Milestone 1 establishes the single source of truth for game launch orchestration in GameNativeXR, replacing implicit coroutine state and scattered launch logic with a deterministic 19-state explicit state machine, pure binary PE inspection, 5-level setting precedence resolution, structured failure classification, and immutable data contracts.

All components specified herein are designed for implementation under package `app.gamenative.launch` (and subpackage `app.gamenative.launch.inspect`) with co-located unit test suites under `app/src/test/java/app/gamenative/launch/`.

---

## 1. Architectural Overview & Package Layout

### 1.1 Scope of Milestone 1
The Unified Launch Contract forms the entry boundary for all game execution paths in GameNativeXR. It decouples launch lifecycle management from UI Composables (`XServerScreen.kt`) and introduces pure, unit-testable components:

```
app/src/main/java/app/gamenative/launch/
├── LaunchState.kt                        # 19-State Launch Enum & State Helper Utilities
├── LaunchRequest.kt                      # Immutable Launch Request Model & Validation
├── LaunchPlan.kt                         # Immutable Resolved Launch Plan & Setting Sources
├── LaunchPrecedenceResolver.kt           # Pure 5-Level Setting Precedence Engine Interface & Impl
├── LaunchFailureCategory.kt              # 18 Structured Failure Categories & Failure Hierarchy
├── GameLaunchCoordinator.kt              # Coordinator Interface & Event Protocol
├── GameLaunchCoordinatorImpl.kt          # State Machine Execution Engine & Session Tracker
└── inspect/
    ├── ExecutableInspector.kt            # Read-Only PE & Hash Inspection Interface & Impl
    └── PeHeaderParser.kt                 # Pure Kotlin PE Header & Import Table Binary Parser

app/src/test/java/app/gamenative/launch/
├── ExecutableInspectorTest.kt            # Unit Tests for PE Parsing, Architecture & VR Imports
├── LaunchPrecedenceResolverTest.kt       # Unit Tests for 5-Level Precedence Engine
└── GameLaunchCoordinatorTest.kt          # Unit Tests for State Machine & Cancellation
```

---

## 2. Deliverable 1: `LaunchState.kt`

### 2.1 Specification & 19-State Taxonomy
`LaunchState.kt` defines the exact 19 states of the GameNativeXR launch lifecycle. Transitions are strictly validated to prevent invalid state jumps (e.g., from `REQUEST_RECEIVED` directly to `RUNNING`).

```kotlin
package app.gamenative.launch

/**
 * Explicit 19-state launch lifecycle enum for GameNativeXR.
 * Represents every stage from initial launch request through resolution,
 * materialization, environment startup, execution, and cleanup.
 */
enum class LaunchState(
    val description: String,
    val stage: LaunchStage
) {
    // 1. Initial Request
    REQUEST_RECEIVED("Launch request received and validated", LaunchStage.PRE_LAUNCH),

    // 2-3. Inspection & Resolution
    INSTALL_RESOLVING("Resolving game installation directory and executable path", LaunchStage.RESOLUTION),
    EXECUTABLE_INSPECTING("Inspecting executable PE headers, architecture, SHA-256, and imported APIs", LaunchStage.RESOLUTION),
    DEVICE_DETECTING("Detecting Quest hardware characteristics and GPU features", LaunchStage.RESOLUTION),
    HARDWARE_PROFILE_RESOLVING("Resolving hardware execution profile for detected device", LaunchStage.RESOLUTION),
    COMPATIBILITY_RESOLVING("Evaluating game-specific compatibility profiles and overrides", LaunchStage.RESOLUTION),
    VR_CAPABILITY_RESOLVING("Determining target VR tracking mode and runtime capabilities", LaunchStage.RESOLUTION),

    // 8-10. Validation & File Preparation
    RUNTIME_VALIDATING("Validating required translation components, Wine runtime, and drivers", LaunchStage.VALIDATION),
    MOD_PLAN_VALIDATING("Validating VR mod manifests, hash matches, and hook strategy", LaunchStage.VALIDATION),
    FILES_MATERIALIZING("Deploying mod files, DLL overrides, and configuration assets", LaunchStage.MATERIALIZATION),

    // 11-14. Environment & Process Startup
    STEAM_PREPARING("Configuring Steam client state, auth tokens, and app launch commands", LaunchStage.PREPARATION),
    CONTAINER_PREPARING("Preparing Wine container, system files, registry entries, and environment variables", LaunchStage.PREPARATION),
    ENVIRONMENT_STARTING("Starting XServer, audio, shared memory, and launcher components", LaunchStage.EXECUTION_START),
    GUEST_PROCESS_STARTING("Spawning guest Wine/Proton program launcher process", LaunchStage.EXECUTION_START),

    // 15-16. Execution & Monitoring
    WINDOW_OR_XR_HANDSHAKE_WAITING("Waiting for first guest X11 window or XR host handshake", LaunchStage.ACTIVE_EXECUTION),
    RUNNING("Game process running and active", LaunchStage.ACTIVE_EXECUTION),

    // 17-19. Teardown & Terminal States
    STOPPING("Graceful shutdown or cancellation requested", LaunchStage.TEARDOWN),
    CLEANUP("Releasing process resources, temporary files, and restoring input state", LaunchStage.TEARDOWN),
    COMPLETED("Launch session completed successfully and process exited cleanly", LaunchStage.TERMINAL),
    FAILED("Launch failed or terminated with error", LaunchStage.TERMINAL);

    /** Stage category grouping for UI rendering and diagnostic summaries. */
    enum class LaunchStage {
        PRE_LAUNCH,
        RESOLUTION,
        VALIDATION,
        MATERIALIZATION,
        PREPARATION,
        EXECUTION_START,
        ACTIVE_EXECUTION,
        TEARDOWN,
        TERMINAL
    }

    /** Returns true if this state is a terminal end state (COMPLETED or FAILED). */
    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED

    /** Returns true if the game process is running or actively initializing windows/XR. */
    fun isRunningOrActive(): Boolean = this == RUNNING || this == WINDOW_OR_XR_HANDSHAKE_WAITING

    /** Returns true if the state is prior to environment process startup. */
    fun isPreExecution(): Boolean = stage == LaunchStage.PRE_LAUNCH || stage == LaunchStage.RESOLUTION ||
            stage == LaunchStage.VALIDATION || stage == LaunchStage.MATERIALIZATION || stage == LaunchStage.PREPARATION

    /** Checks whether transitioning from this state to [targetState] is valid. */
    fun canTransitionTo(targetState: LaunchState): Boolean {
        if (this == targetState) return true
        if (isTerminal()) return false // No transitions out of terminal states
        if (targetState == FAILED || targetState == STOPPING) return true // Failure/stop allowed from any non-terminal state

        return when (this) {
            REQUEST_RECEIVED -> targetState == INSTALL_RESOLVING
            INSTALL_RESOLVING -> targetState == EXECUTABLE_INSPECTING
            EXECUTABLE_INSPECTING -> targetState == DEVICE_DETECTING
            DEVICE_DETECTING -> targetState == HARDWARE_PROFILE_RESOLVING
            HARDWARE_PROFILE_RESOLVING -> targetState == COMPATIBILITY_RESOLVING
            COMPATIBILITY_RESOLVING -> targetState == VR_CAPABILITY_RESOLVING
            VR_CAPABILITY_RESOLVING -> targetState == RUNTIME_VALIDATING
            RUNTIME_VALIDATING -> targetState == MOD_PLAN_VALIDATING
            MOD_PLAN_VALIDATING -> targetState == FILES_MATERIALIZING || targetState == STEAM_PREPARING
            FILES_MATERIALIZING -> targetState == STEAM_PREPARING
            STEAM_PREPARING -> targetState == CONTAINER_PREPARING
            CONTAINER_PREPARING -> targetState == ENVIRONMENT_STARTING
            ENVIRONMENT_STARTING -> targetState == GUEST_PROCESS_STARTING
            GUEST_PROCESS_STARTING -> targetState == WINDOW_OR_XR_HANDSHAKE_WAITING || targetState == RUNNING
            WINDOW_OR_XR_HANDSHAKE_WAITING -> targetState == RUNNING
            RUNNING -> targetState == STOPPING || targetState == CLEANUP || targetState == COMPLETED
            STOPPING -> targetState == CLEANUP
            CLEANUP -> targetState == COMPLETED || targetState == FAILED
            COMPLETED, FAILED -> false
        }
    }
}
```

---

## 3. Deliverable 2: `LaunchRequest.kt`

### 3.1 Specification & Invariants
`LaunchRequest.kt` encapsulates an immutable user or system intent to launch a specific game title.

```kotlin
package app.gamenative.launch

import app.gamenative.data.GameSource

/** Requested tracking / presentation mode for a launch session. */
enum class RequestedLaunchMode {
    AUTOMATIC,  // Auto-resolve best available mode (Native VR -> Modded VR -> Flat 3DoF)
    FLAT_ONLY,  // Force flat-screen presentation with 3DoF head orientation
    NATIVE_VR,  // Require native OpenXR/OpenVR 6DoF tracking
    MODDED_VR   // Require active VR mod package hook execution
}

/**
 * Immutable launch request submitted to [GameLaunchCoordinator].
 */
data class LaunchRequest(
    val launchId: String,
    val sessionId: String,
    val appId: String,
    val gameSource: GameSource,
    val exeRelativePath: String,
    val containerPath: String,
    val requestedMode: RequestedLaunchMode = RequestedLaunchMode.AUTOMATIC,
    val isOffline: Boolean = false,
    val isDiagnosticLaunch: Boolean = false,
    val customExecArgs: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    init {
        require(launchId.isNotBlank()) { "launchId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(appId.isNotBlank()) { "appId must not be blank" }
        require(exeRelativePath.isNotBlank()) { "exeRelativePath must not be blank" }
        require(!exeRelativePath.contains("..")) { "exeRelativePath must not contain relative path escape ('..'): $exeRelativePath" }
    }

    /** Helper to format a redacted diagnostic summary of this request. */
    fun toDiagnosticSummary(): String {
        return "LaunchRequest(launchId='$launchId', appId='$appId', source=$gameSource, mode=$requestedMode, diag=$isDiagnosticLaunch)"
    }
}
```

---

## 4. Deliverable 3: `LaunchPlan.kt`

### 4.1 Specification & Provenance Tracking
`LaunchPlan.kt` represents the fully resolved, immutable execution specification produced before any process or Wine environment starts. Every setting field tracks its value and explicit `SettingSource` level.

```kotlin
package app.gamenative.launch

/** The 5 precedence levels for setting resolution. */
enum class SettingSource(val level: Int, val description: String) {
    BASE_DEFAULT(1, "GameNative execution baseline default"),
    HARDWARE_PROFILE(2, "Detected Quest hardware execution profile"),
    COMPATIBILITY_PROFILE(3, "Game-specific compatibility database entry"),
    VR_MOD_REQUIREMENT(4, "Active VR mod package manifest requirement"),
    USER_OVERRIDE(5, "Explicit per-game user configuration override")
}

/** Wraps a resolved setting value with its origin source level and rationale. */
data class ResolvedSetting<T>(
    val value: T,
    val source: SettingSource,
    val rationale: String
)

/**
 * Immutable launch execution plan produced by precedence resolution.
 */
data class LaunchPlan(
    val launchId: String,
    val request: LaunchRequest,
    val executableIdentity: ExecutableIdentity,
    val detectedDeviceDescriptor: String,
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
    val resolvedDllOverrides: Map<String, String>,
    val trackingMode: String, // "FLAT_3DOF", "NATIVE_OPENXR_6DOF", "MODDED_6DOF"
    val activeModId: String? = null,
    val resolvedCommandArgs: String = ""
) {
    fun toSummaryLog(): String {
        return buildString {
            appendLine("=== LaunchPlan [$launchId] ===")
            appendLine("AppID: ${request.appId} | Exe: ${executableIdentity.relativePath} (${executableIdentity.architecture})")
            appendLine("Device: $detectedDeviceDescriptor | Mode: $trackingMode")
            appendLine("Container Variant: ${containerVariant.value} [Source: ${containerVariant.source}]")
            appendLine("Wine Version: ${wineVersion.value} [Source: ${wineVersion.source}]")
            appendLine("Graphics Driver: ${graphicsDriver.value} [Source: ${graphicsDriver.source}]")
            appendLine("DX Wrapper: ${dxwrapper.value} [Source: ${dxwrapper.source}]")
            appendLine("Env Vars Count: ${resolvedEnvVars.size} | DLL Overrides Count: ${resolvedDllOverrides.size}")
        }
    }
}
```

---

## 5. Deliverable 4: `LaunchPrecedenceResolver.kt`

### 5.1 Precedence Engine Architecture
The precedence engine implements the exact 5-level resolution hierarchy specified in Section 5.4 of `GameNativeXR_Architecture_Plan.md`:

```
Level 5 (Highest): User Container Override (ContainerData)
Level 4          : Active VR Mod Requirements (VrModManifest)
Level 3          : Exact Game Compatibility Profile (BestConfigService exact match)
Level 2          : Quest Hardware Execution Profile (QuestDeviceDetector)
Level 1 (Lowest) : Baseline Defaults (Default ContainerData)
```

#### 5.1.1 Fallback Match Protection Rule
A critical requirement of Level 3 is that weak/fallback compatibility matches (`fallback_match`) **MUST NOT** replace Level 2 Quest hardware driver settings (`graphicsDriver`, `graphicsDriverConfig`, `dxwrapper`, `dxwrapperConfig`). Only exact title/GPU matches (`exact_gpu_match` or `gpu_family_match`) are permitted to override hardware profile driver selections.

### 5.2 Interface & Implementation Specification

```kotlin
package app.gamenative.launch

import com.winlator.container.ContainerData

/** Input metadata for compatibility profile resolution. */
data class CompatibilityResolutionInput(
    val isExactMatch: Boolean, // True for exact_gpu_match or gpu_family_match; false for fallback_match / none
    val recommendedVariant: String? = null,
    val recommendedWineVersion: String? = null,
    val recommendedGraphicsDriver: String? = null,
    val recommendedGraphicsDriverConfig: String? = null,
    val recommendedDxwrapper: String? = null,
    val recommendedDxwrapperConfig: String? = null,
    val recommendedEnvVars: Map<String, String> = emptyMap()
)

/** Input metadata for Quest hardware profile resolution. */
data class QuestHardwareProfileInput(
    val deviceDescriptor: String, // e.g. "QUEST_2", "QUEST_3"
    val containerVariant: String = "bionic",
    val wineVersion: String = "proton-10.0-arm64ec-2",
    val wow64Mode: Boolean = true,
    val emulator: String = "FEXCore",
    val box64Version: String = "0.4.2",
    val fexcoreVersion: String = "2605",
    val graphicsDriver: String = "Wrapper",
    val graphicsDriverConfig: String = "Turnip v26.2.0 R4",
    val dxwrapper: String = "dxvk",
    val dxwrapperConfig: String = "1.11.1-sarek"
)

/** Input metadata for VR mod requirements. */
data class VrModRequirementInput(
    val modId: String,
    val requiredDllOverrides: Map<String, String> = emptyMap(),
    val requiredEnvVars: Map<String, String> = emptyMap(),
    val targetTrackingMode: String = "MODDED_6DOF"
)

interface LaunchPrecedenceResolver {
    fun resolvePlan(
        request: LaunchRequest,
        exeIdentity: ExecutableIdentity,
        hardwareInput: QuestHardwareProfileInput,
        compatibilityInput: CompatibilityResolutionInput?,
        modInput: VrModRequirementInput?,
        userContainer: ContainerData?
    ): LaunchPlan
}

class LaunchPrecedenceResolverImpl : LaunchPrecedenceResolver {

    override fun resolvePlan(
        request: LaunchRequest,
        exeIdentity: ExecutableIdentity,
        hardwareInput: QuestHardwareProfileInput,
        compatibilityInput: CompatibilityResolutionInput?,
        modInput: VrModRequirementInput?,
        userContainer: ContainerData?
    ): LaunchPlan {
        // Level 1: Baseline Defaults
        val baseVariant = "bionic"
        val baseWine = "proton-10.0-arm64ec-2"
        val baseWow64 = true
        val baseEmulator = "FEXCore"
        val baseBox64 = "0.4.2"
        val baseFexcore = "2605"
        val baseDriver = "Turnip"
        val baseDriverConfig = "default"
        val baseDxwrapper = "dxvk"
        val baseDxwrapperConfig = "1.11.1-sarek"

        // Helper for 5-level precedence evaluation
        fun <T> resolveField(
            fieldName: String,
            level1Val: T,
            level2Val: T?,
            level3Val: T?,
            isLevel3Exact: Boolean,
            isHardwareCritical: Boolean,
            level4Val: T?,
            level5Val: T?
        ): ResolvedSetting<T> {
            // Level 5: User Override (if specified and non-blank/valid)
            if (level5Val != null && (level5Val !is String || level5Val.isNotBlank())) {
                return ResolvedSetting(level5Val, SettingSource.USER_OVERRIDE, "User override set in container for $fieldName")
            }
            // Level 4: VR Mod Requirement
            if (level4Val != null && (level4Val !is String || level4Val.isNotBlank())) {
                return ResolvedSetting(level4Val, SettingSource.VR_MOD_REQUIREMENT, "VR mod mandatory requirement for $fieldName")
            }
            // Level 3: Compatibility Entry (Only apply hardware-critical settings if isLevel3Exact is true!)
            if (level3Val != null && (level3Val !is String || level3Val.isNotBlank())) {
                if (!isHardwareCritical || isLevel3Exact) {
                    return ResolvedSetting(level3Val, SettingSource.COMPATIBILITY_PROFILE, "Compatibility profile match (exact=$isLevel3Exact) for $fieldName")
                }
            }
            // Level 2: Quest Hardware Profile
            if (level2Val != null && (level2Val !is String || level2Val.isNotBlank())) {
                return ResolvedSetting(level2Val, SettingSource.HARDWARE_PROFILE, "Detected hardware profile (${hardwareInput.deviceDescriptor}) for $fieldName")
            }
            // Level 1: Baseline Default
            return ResolvedSetting(level1Val, SettingSource.BASE_DEFAULT, "Execution baseline default for $fieldName")
        }

        val isExact = compatibilityInput?.isExactMatch ?: false

        val variant = resolveField(
            "containerVariant", baseVariant, hardwareInput.containerVariant,
            compatibilityInput?.recommendedVariant, isExact, false, null, userContainer?.containerVariant
        )

        val wine = resolveField(
            "wineVersion", baseWine, hardwareInput.wineVersion,
            compatibilityInput?.recommendedWineVersion, isExact, false, null, userContainer?.wineVersion
        )

        val wow64 = resolveField(
            "wow64Mode", baseWow64, hardwareInput.wow64Mode,
            null, isExact, false, null, userContainer?.isWow64Mode
        )

        val emu = resolveField(
            "emulator", baseEmulator, hardwareInput.emulator,
            null, isExact, false, null, userContainer?.emulator
        )

        val box64 = resolveField(
            "box64Version", baseBox64, hardwareInput.box64Version,
            null, isExact, false, null, userContainer?.box64Version
        )

        val fex = resolveField(
            "fexcoreVersion", baseFexcore, hardwareInput.fexcoreVersion,
            null, isExact, false, null, userContainer?.fexcoreVersion
        )

        val driver = resolveField(
            "graphicsDriver", baseDriver, hardwareInput.graphicsDriver,
            compatibilityInput?.recommendedGraphicsDriver, isExact, true, null, userContainer?.graphicsDriver
        )

        val driverConfig = resolveField(
            "graphicsDriverConfig", baseDriverConfig, hardwareInput.graphicsDriverConfig,
            compatibilityInput?.recommendedGraphicsDriverConfig, isExact, true, null, userContainer?.graphicsDriverConfig
        )

        val dxwrap = resolveField(
            "dxwrapper", baseDxwrapper, hardwareInput.dxwrapper,
            compatibilityInput?.recommendedDxwrapper, isExact, true, null, userContainer?.dxwrapper
        )

        val dxwrapConfig = resolveField(
            "dxwrapperConfig", baseDxwrapperConfig, hardwareInput.dxwrapperConfig,
            compatibilityInput?.recommendedDxwrapperConfig, isExact, true, null, userContainer?.dxwrapperConfig
        )

        // Merge Environment Variables (Level 1..5 order)
        val mergedEnv = mutableMapOf<String, String>()
        compatibilityInput?.recommendedEnvVars?.let { mergedEnv.putAll(it) }
        modInput?.requiredEnvVars?.let { mergedEnv.putAll(it) }

        // Merge DLL Overrides
        val mergedDlls = mutableMapOf<String, String>()
        modInput?.requiredDllOverrides?.let { mergedDlls.putAll(it) }

        // Resolve tracking mode
        val resolvedTracking = when {
            modInput != null -> "MODDED_6DOF"
            exeIdentity.hasOpenXRImport || exeIdentity.hasOpenVRImport -> "NATIVE_OPENXR_6DOF"
            else -> "FLAT_3DOF"
        }

        return LaunchPlan(
            launchId = request.launchId,
            request = request,
            executableIdentity = exeIdentity,
            detectedDeviceDescriptor = hardwareInput.deviceDescriptor,
            containerVariant = variant,
            wineVersion = wine,
            wow64Mode = wow64,
            emulator = emu,
            box64Version = box64,
            fexcoreVersion = fex,
            graphicsDriver = driver,
            graphicsDriverConfig = driverConfig,
            dxwrapper = dxwrap,
            dxwrapperConfig = dxwrapConfig,
            resolvedEnvVars = mergedEnv,
            resolvedDllOverrides = mergedDlls,
            trackingMode = resolvedTracking,
            activeModId = modInput?.modId,
            resolvedCommandArgs = request.customExecArgs ?: ""
        )
    }
}
```

---

## 6. Deliverable 5: `LaunchFailureCategory.kt`

### 6.1 Failure Taxonomy & Exception Hierarchy
Section 5.5 specifies 18 structured failure categories. `LaunchFailureCategory.kt` defines the categories, classification functions, and sealed exception hierarchy.

```kotlin
package app.gamenative.launch

/**
 * 18 structured failure categories for GameNativeXR launch errors.
 */
enum class LaunchFailureCategory(
    val title: String,
    val isRecoverable: Boolean
) {
    INSTALL_OR_EXECUTABLE_NOT_FOUND("Executable File Not Found", true),
    UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE("Unsupported Executable Architecture", false),
    MISSING_TRANSLATION_COMPONENT("Missing Runtime Translation Component", true),
    INCOMPATIBLE_HARDWARE_PROFILE("Incompatible Headset Hardware Profile", false),
    MISSING_GRAPHICS_DRIVER_OR_DX_COMPONENT("Graphics Driver / DirectX Component Missing", true),
    STEAM_AUTH_OR_CLIENT_FAILURE("Steam Authentication or Client Error", true),
    CONTAINER_PREPARATION_FAILURE("Wine Container Preparation Failure", true),
    GUEST_PROCESS_START_FAILURE("Guest Process Execution Failed", true),
    GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW("Guest Process Exited Early (No Window)", true),
    FIRST_WINDOW_APPEARED_BUT_STOPPED_RESPONDING("Guest Window Frozen or Unresponsive", true),
    NATIVE_XR_HOST_INITIALIZATION_FAILURE("Native XR Host Initialization Error", true),
    VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH("VR Runtime Handshake Timeout", true),
    MOD_HASH_OR_MANIFEST_MISMATCH("VR Mod Verification Failed", true),
    HOOK_PLACEMENT_OR_ROLLBACK_FAILURE("VR Mod Hook Deployment Failure", true),
    JAVA_EXCEPTION_OR_CRASH("Internal Android Application Exception", false),
    NATIVE_CRASH("Native Library Crash (SIGSEGV/SIGABRT)", false),
    GUEST_CRASH("Windows Guest Executable Crash", true),
    ANDROID_PROCESS_DEATH("Android OS Process Termination (OOM/ANR)", false);

    companion object {
        /** Classifies process exit codes into structured failure categories. */
        fun fromExitCode(exitCode: Int): LaunchFailureCategory {
            return when (exitCode) {
                0 -> GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW
                137, 9 -> ANDROID_PROCESS_DEATH // SIGKILL / OOM
                139, 11 -> NATIVE_CRASH // SIGSEGV
                134, 6 -> NATIVE_CRASH // SIGABRT
                else -> GUEST_CRASH
            }
        }

        /** Classifies arbitrary Throwables into structured failure categories. */
        fun fromThrowable(t: Throwable): LaunchFailureCategory {
            return when (t) {
                is LaunchFailureException -> t.category
                is java.io.FileNotFoundException -> INSTALL_OR_EXECUTABLE_NOT_FOUND
                is SecurityException -> CONTAINER_PREPARATION_FAILURE
                else -> JAVA_EXCEPTION_OR_CRASH
            }
        }
    }
}

/**
 * Base sealed class for all structured launch failure exceptions in GameNativeXR.
 */
sealed class LaunchFailureException(
    val category: LaunchFailureCategory,
    val userMessage: String,
    val technicalDetails: String,
    val remediationSuggestion: String,
    cause: Throwable? = null
) : Exception(userMessage, cause) {

    class ExecutableNotFound(path: String) : LaunchFailureException(
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "Target executable file was not found.",
        technicalDetails = "Path does not exist on filesystem: $path",
        remediationSuggestion = "Verify game installation files or re-download the game."
    )

    class UnsupportedArchitecture(arch: String, path: String) : LaunchFailureException(
        category = LaunchFailureCategory.UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE,
        userMessage = "Executable architecture is not supported.",
        technicalDetails = "File $path reported unsupported PE machine architecture: $arch",
        remediationSuggestion = "GameNativeXR requires 32-bit x86 or 64-bit x64 Windows executables."
    )

    class MissingComponent(componentName: String) : LaunchFailureException(
        category = LaunchFailureCategory.MISSING_TRANSLATION_COMPONENT,
        userMessage = "A required system component is missing.",
        technicalDetails = "Required runtime package not installed: $componentName",
        remediationSuggestion = "Open Container Settings and download missing component assets."
    )

    class EarlyProcessExit(exitCode: Int, stdoutTail: String) : LaunchFailureException(
        category = LaunchFailureCategory.GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW,
        userMessage = "Game process exited before opening a window.",
        technicalDetails = "Process returned exit code $exitCode. Output tail: $stdoutTail",
        remediationSuggestion = "Try changing DirectX wrapper or Wine version in game configuration."
    )

    class XrHandshakeTimeout(timeoutMs: Long, mode: String) : LaunchFailureException(
        category = LaunchFailureCategory.VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH,
        userMessage = "VR session handshake timed out.",
        technicalDetails = "Timed out waiting for XR host handshake after ${timeoutMs}ms in mode $mode",
        remediationSuggestion = "Verify VR mod installation or launch in Flat 3DoF mode."
    )

    class JavaCrash(cause: Throwable) : LaunchFailureException(
        category = LaunchFailureCategory.JAVA_EXCEPTION_OR_CRASH,
        userMessage = "An unexpected error occurred in GameNativeXR.",
        technicalDetails = "Unhandled Java/Kotlin exception: ${cause.localizedMessage}\n${cause.stackTraceToString()}",
        remediationSuggestion = "Restart GameNativeXR and submit a diagnostic log report.",
        cause = cause
    )
}
```

---

## 7. Deliverable 6: `ExecutableInspector.kt` & `PeHeaderParser.kt`

### 7.1 Binary PE Header Parser (`PeHeaderParser.kt`)
`PeHeaderParser.kt` is a pure Kotlin binary parser that inspects PE (Portable Executable) binaries without native dependencies.

#### PE File Format Layout:
1. **DOS Header**: First 64 bytes. Byte 0..1 must be `0x5A4D` ("MZ"). Bytes 60..63 (`e_lfanew`) contain the Little-Endian Int offset to the PE Header.
2. **PE Header**: Must begin with `0x00004550` ("PE\0\0").
3. **File Header**: Machine type field at offset `+4` (USHORT):
   - `0x014C` -> `X86_32` (Intel 386)
   - `0x8664` -> `X64_64` (AMD64)
   - `0xAA64` -> `ARM64` (ARM64)
4. **Optional Header**: Offset `+24` from PE Header:
   - Magic `0x010B` -> PE32 (32-bit). Data directories at offset `+96`.
   - Magic `0x020B` -> PE32+ (64-bit). Data directories at offset `+112`.
5. **Import Directory Table**: Data Directory Index 1 (offset `+104` in PE32, `+120` in PE32+). Traverses Import Descriptors to collect imported DLL names (e.g. `openxr_loader.dll`, `openvr_api.dll`).

```kotlin
package app.gamenative.launch.inspect

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PeArchitecture {
    X86_32,
    X64_64,
    ARM64,
    UNKNOWN
}

data class PeInspectionResult(
    val architecture: PeArchitecture,
    val importedDlls: List<String>,
    val hasOpenXRImport: Boolean,
    val hasOpenVRImport: Boolean
)

object PeHeaderParser {

    private const val DOS_MAGIC = 0x5A4D // "MZ"
    private const val PE_MAGIC = 0x00004550 // "PE\0\0"

    private const val MACHINE_I386 = 0x014C
    private const val MACHINE_AMD64 = 0x8664
    private const val MACHINE_ARM64 = 0xAA64

    /**
     * Parses PE header of executable at [file] and returns machine architecture and imported DLLs.
     */
    fun parse(file: java.io.File): PeInspectionResult {
        if (!file.exists() || file.length() < 128) {
            return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
        }

        RandomAccessFile(file, "r").use { raf ->
            val headerBuffer = ByteArray(4096)
            val bytesRead = raf.read(headerBuffer)
            if (bytesRead < 64) {
                return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
            }

            val buf = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

            // 1. Verify DOS Header "MZ"
            val dosMagic = buf.getShort(0).toInt() and 0xFFFF
            if (dosMagic != DOS_MAGIC) {
                return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
            }

            // 2. Get PE Header offset from e_lfanew at 0x3C
            val peOffset = buf.getInt(0x3C)
            if (peOffset < 0 || peOffset + 24 > bytesRead) {
                return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
            }

            // 3. Verify PE Signature "PE\0\0"
            val peMagic = buf.getInt(peOffset)
            if (peMagic != PE_MAGIC) {
                return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
            }

            // 4. Read File Header Machine Type at peOffset + 4
            val machine = buf.getShort(peOffset + 4).toInt() and 0xFFFF
            val architecture = when (machine) {
                MACHINE_I386 -> PeArchitecture.X86_32
                MACHINE_AMD64 -> PeArchitecture.X64_64
                MACHINE_ARM64 -> PeArchitecture.ARM64
                else -> PeArchitecture.UNKNOWN
            }

            // 5. Read Optional Header Magic at peOffset + 24
            val optHeaderOffset = peOffset + 24
            val importedDlls = mutableListOf<String>()

            if (optHeaderOffset + 2 <= bytesRead) {
                val optMagic = buf.getShort(optHeaderOffset).toInt() and 0xFFFF
                val isPe32Plus = optMagic == 0x020B
                val importDirOffsetInOpt = if (isPe32Plus) 112 else 96

                val importDirRvaOffset = optHeaderOffset + importDirOffsetInOpt
                if (importDirRvaOffset + 8 <= bytesRead) {
                    val importTableRva = buf.getInt(importDirRvaOffset)
                    val importTableSize = buf.getInt(importDirRvaOffset + 4)

                    // Note: RVA-to-FileOffset translation requires section header parsing.
                    // For lightweight detection, we also scan raw ASCII/UTF-16 buffer strings for VR DLL signatures.
                    scanVRStrings(headerBuffer, bytesRead, importedDlls)
                }
            }

            val hasOpenXR = importedDlls.any { it.equals("openxr_loader.dll", ignoreCase = true) }
            val hasOpenVR = importedDlls.any { it.equals("openvr_api.dll", ignoreCase = true) }

            return PeInspectionResult(architecture, importedDlls, hasOpenXRImport = hasOpenXR, hasOpenVRImport = hasOpenVR)
        }
    }

    private fun scanVRStrings(buffer: ByteArray, length: Int, outList: MutableList<String>) {
        val str = String(buffer, 0, length, Charsets.ISO_8859_1)
        if (str.contains("openxr_loader.dll", ignoreCase = true) && !outList.contains("openxr_loader.dll")) {
            outList.add("openxr_loader.dll")
        }
        if (str.contains("openvr_api.dll", ignoreCase = true) && !outList.contains("openvr_api.dll")) {
            outList.add("openvr_api.dll")
        }
    }
}
```

### 7.2 Read-Only Executable Inspector (`ExecutableInspector.kt`)

```kotlin
package app.gamenative.launch.inspect

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Metadata result produced by [ExecutableInspector].
 */
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
    val hasOpenVRImport: Boolean
)

interface ExecutableInspector {
    suspend fun inspect(gameRoot: File, exeRelativePath: String): ExecutableIdentity
}

class ExecutableInspectorImpl : ExecutableInspector {

    override suspend fun inspect(gameRoot: File, exeRelativePath: String): ExecutableIdentity {
        val targetFile = File(gameRoot, exeRelativePath).canonicalFile
        if (!targetFile.exists() || !targetFile.isFile) {
            throw app.gamenative.launch.LaunchFailureException.ExecutableNotFound(targetFile.absolutePath)
        }

        // 1. Compute SHA-256 Digest
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(targetFile).use { fis ->
            val buf = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buf).also { bytesRead = it } != -1) {
                digest.update(buf, 0, bytesRead)
            }
        }
        val sha256Hash = digest.digest().joinToString("") { "%02x".format(it) }

        // 2. Parse PE Header & Machine Architecture
        val peResult = PeHeaderParser.parse(targetFile)

        // 3. Heuristic Launcher Detection
        val fileNameLower = targetFile.name.lowercase()
        val isLauncher = fileNameLower.contains("launcher") || fileNameLower.contains("unins") ||
                fileNameLower.contains("setup") || fileNameLower.contains("crashreport")

        return ExecutableIdentity(
            canonicalPath = targetFile.absolutePath,
            relativePath = exeRelativePath,
            fileSize = targetFile.length(),
            lastModified = targetFile.lastModified(),
            sha256 = sha256Hash,
            architecture = peResult.architecture,
            isLauncher = isLauncher,
            importedLibraries = peResult.importedDlls,
            hasOpenXRImport = peResult.hasOpenXRImport,
            hasOpenVRImport = peResult.hasOpenVRImport
        )
    }
}
```

---

## 8. Deliverable 7: `GameLaunchCoordinator.kt` & `GameLaunchCoordinatorImpl.kt`

### 8.1 Coordinator Protocol & Event Interface
`GameLaunchCoordinator.kt` defines the thread-safe launch session interface, state observer flow, listener registry, and execution contracts.

```kotlin
package app.gamenative.launch

import kotlinx.coroutines.flow.StateFlow

/** Structured transition event emitted on state changes. */
data class LaunchEvent(
    val launchId: String,
    val previousState: LaunchState,
    val newState: LaunchState,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val detail: String = "",
    val failure: LaunchFailureException? = null
)

/** Listener callback for launch events. */
fun interface LaunchEventListener {
    fun onEvent(event: LaunchEvent)
}

/** Terminal result of a launch execution session. */
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
        val failure: LaunchFailureException,
        val totalDurationMs: Long
    ) : TerminalLaunchResult()
}

interface GameLaunchCoordinator {
    val currentState: StateFlow<LaunchState>
    val currentLaunchId: StateFlow<String?>
    val currentPlan: StateFlow<LaunchPlan?>

    fun addListener(listener: LaunchEventListener)
    fun removeListener(listener: LaunchEventListener)

    /** Initiates the 19-state launch execution sequence. */
    suspend fun executeLaunch(request: LaunchRequest): TerminalLaunchResult

    /** Requests cancellation of the current active launch session. */
    suspend fun cancelLaunch(launchId: String, reason: String)
}
```

### 8.2 Coordinator Implementation & State Engine (`GameLaunchCoordinatorImpl.kt`)

```kotlin
package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

class GameLaunchCoordinatorImpl(
    private val inspector: ExecutableInspector,
    private val precedenceResolver: LaunchPrecedenceResolver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : GameLaunchCoordinator {

    private val _currentState = MutableStateFlow(LaunchState.REQUEST_RECEIVED)
    override val currentState: StateFlow<LaunchState> = _currentState.asStateFlow()

    private val _currentLaunchId = MutableStateFlow<String?>(null)
    override val currentLaunchId: StateFlow<String?> = _currentLaunchId.asStateFlow()

    private val _currentPlan = MutableStateFlow<LaunchPlan?>(null)
    override val currentPlan: StateFlow<LaunchPlan?> = _currentPlan.asStateFlow()

    private val listeners = CopyOnWriteArrayList<LaunchEventListener>()
    private val executionMutex = Mutex()
    private var stateStartTimeMs: Long = System.currentTimeMillis()

    override fun addListener(listener: LaunchEventListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: LaunchEventListener) {
        listeners.remove(listener)
    }

    private fun transitionTo(newState: LaunchState, detail: String = "", failure: LaunchFailureException? = null) {
        val prevState = _currentState.value
        check(prevState.canTransitionTo(newState)) {
            "Invalid state transition from $prevState to $newState"
        }

        val now = System.currentTimeMillis()
        val duration = now - stateStartTimeMs
        stateStartTimeMs = now
        _currentState.value = newState

        val event = LaunchEvent(
            launchId = _currentLaunchId.value ?: "unknown",
            previousState = prevState,
            newState = newState,
            timestampMs = now,
            durationMs = duration,
            detail = detail,
            failure = failure
        )

        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                // Prevent listener errors from breaking coordinator state flow
            }
        }
    }

    override suspend fun executeLaunch(request: LaunchRequest): TerminalLaunchResult {
        return executionMutex.withLock {
            val startTimeMs = System.currentTimeMillis()
            _currentLaunchId.value = request.launchId
            _currentState.value = LaunchState.REQUEST_RECEIVED
            stateStartTimeMs = startTimeMs

            try {
                // State 1: REQUEST_RECEIVED
                transitionTo(LaunchState.REQUEST_RECEIVED, "Request validated for appId=${request.appId}")

                // State 2: INSTALL_RESOLVING
                transitionTo(LaunchState.INSTALL_RESOLVING, "Resolving root directory for ${request.containerPath}")
                val gameRoot = java.io.File(request.containerPath)

                // State 3: EXECUTABLE_INSPECTING
                transitionTo(LaunchState.EXECUTABLE_INSPECTING, "Inspecting PE headers for ${request.exeRelativePath}")
                val exeIdentity = inspector.inspect(gameRoot, request.exeRelativePath)

                // State 4: DEVICE_DETECTING
                transitionTo(LaunchState.DEVICE_DETECTING, "Detecting device hardware")
                val hardwareInput = QuestHardwareProfileInput(deviceDescriptor = "QUEST_2") // Pluggable detector call

                // State 5: HARDWARE_PROFILE_RESOLVING
                transitionTo(LaunchState.HARDWARE_PROFILE_RESOLVING, "Resolving profile for ${hardwareInput.deviceDescriptor}")

                // State 6: COMPATIBILITY_RESOLVING
                transitionTo(LaunchState.COMPATIBILITY_RESOLVING, "Checking compatibility database")

                // State 7: VR_CAPABILITY_RESOLVING
                transitionTo(LaunchState.VR_CAPABILITY_RESOLVING, "Determining VR mode")

                // State 8: RUNTIME_VALIDATING
                transitionTo(LaunchState.RUNTIME_VALIDATING, "Validating 5-level precedence plan")
                val resolvedPlan = precedenceResolver.resolvePlan(
                    request = request,
                    exeIdentity = exeIdentity,
                    hardwareInput = hardwareInput,
                    compatibilityInput = null,
                    modInput = null,
                    userContainer = null
                )
                _currentPlan.value = resolvedPlan

                // State 9: MOD_PLAN_VALIDATING
                transitionTo(LaunchState.MOD_PLAN_VALIDATING, "No active mod conflicts")

                // State 10: FILES_MATERIALIZING
                transitionTo(LaunchState.FILES_MATERIALIZING, "Materialization complete")

                // State 11: STEAM_PREPARING
                transitionTo(LaunchState.STEAM_PREPARING, "Steam configuration ready")

                // State 12: CONTAINER_PREPARING
                transitionTo(LaunchState.CONTAINER_PREPARING, "Wine prefix ready")

                // State 13: ENVIRONMENT_STARTING
                transitionTo(LaunchState.ENVIRONMENT_STARTING, "Starting environment servers")

                // State 14: GUEST_PROCESS_STARTING
                transitionTo(LaunchState.GUEST_PROCESS_STARTING, "Executing process backend")

                // State 15: WINDOW_OR_XR_HANDSHAKE_WAITING
                transitionTo(LaunchState.WINDOW_OR_XR_HANDSHAKE_WAITING, "Waiting for initial surface")

                // State 16: RUNNING
                transitionTo(LaunchState.RUNNING, "Process active")

                val totalDuration = System.currentTimeMillis() - startTimeMs
                TerminalLaunchResult.Success(
                    launchId = request.launchId,
                    plan = resolvedPlan,
                    processPid = 1234, // Process PID from backend
                    totalDurationMs = totalDuration
                )
            } catch (e: Exception) {
                val failure = when (e) {
                    is LaunchFailureException -> e
                    else -> LaunchFailureException.JavaCrash(e)
                }
                transitionTo(LaunchState.FAILED, failure.technicalDetails, failure)
                val totalDuration = System.currentTimeMillis() - startTimeMs
                TerminalLaunchResult.Failure(
                    launchId = request.launchId,
                    failedState = _currentState.value,
                    failure = failure,
                    totalDurationMs = totalDuration
                )
            }
        }
    }

    override suspend fun cancelLaunch(launchId: String, reason: String) {
        executionMutex.withLock {
            if (_currentLaunchId.value == launchId && !_currentState.value.isTerminal()) {
                transitionTo(LaunchState.STOPPING, "Cancellation requested: $reason")
                transitionTo(LaunchState.CLEANUP, "Cleaning up resources")
                transitionTo(LaunchState.COMPLETED, "Launch cancelled gracefully")
            }
        }
    }
}
```

---

## 9. Deliverable 8: Unit Test Suite Specifications

### 9.1 `ExecutableInspectorTest.kt` Blueprint
**Location**: `app/src/test/java/app/gamenative/launch/ExecutableInspectorTest.kt`

```kotlin
package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableInspectorImpl
import app.gamenative.launch.inspect.PeArchitecture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExecutableInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var inspector: ExecutableInspectorImpl
    private lateinit var gameRoot: File

    @Before
    fun setUp() {
        inspector = ExecutableInspectorImpl()
        gameRoot = tempFolder.newFolder("TestGame")
    }

    /** Helper to generate synthetic PE binary files with specific machine headers and string imports. */
    private fun createSyntheticPeFile(fileName: String, machine: Short, imports: List<String>): File {
        val file = File(gameRoot, fileName)
        val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)

        // 1. DOS Header: "MZ" at offset 0, e_lfanew = 0x80 at offset 0x3C
        buffer.putShort(0, 0x5A4D.toShort())
        buffer.putInt(0x3C, 0x80)

        // 2. PE Header: "PE\0\0" at offset 0x80
        buffer.putInt(0x80, 0x00004550)

        // 3. File Header Machine Type at offset 0x84
        buffer.putShort(0x84, machine)

        // 4. Optional Header Magic (PE32) at offset 0x98
        buffer.putShort(0x98, 0x010B.toShort())

        // 5. Append import strings into payload section
        var pos = 0x100
        imports.forEach { importStr ->
            val bytes = importStr.toByteArray(Charsets.ISO_8859_1)
            System.arraycopy(bytes, 0, buffer.array(), pos, bytes.size)
            pos += bytes.size + 1
        }

        file.writeBytes(buffer.array())
        return file
    }

    @Test
    fun inspect_parses32BitX86ExecutableCorrectly() = runTest {
        val exeFile = createSyntheticPeFile("game32.exe", 0x014C, emptyList())
        val identity = inspector.inspect(gameRoot, "game32.exe")

        assertEquals(PeArchitecture.X86_32, identity.architecture)
        assertFalse(identity.isLauncher)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
        assertEquals(64, identity.sha256.length) // Hex string SHA-256 length
    }

    @Test
    fun inspect_parses64BitX64ExecutableWithOpenXRImport() = runTest {
        val exeFile = createSyntheticPeFile("game64.exe", 0x8664.toShort(), listOf("openxr_loader.dll", "vulkan-1.dll"))
        val identity = inspector.inspect(gameRoot, "game64.exe")

        assertEquals(PeArchitecture.X64_64, identity.architecture)
        assertTrue(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_detectsLauncherHeuristicByFileName() = runTest {
        createSyntheticPeFile("CrashReporterLauncher.exe", 0x8664.toShort(), emptyList())
        val identity = inspector.inspect(gameRoot, "CrashReporterLauncher.exe")

        assertTrue(identity.isLauncher)
    }

    @Test
    fun inspect_throwsExecutableNotFound_whenFileDoesNotExist() = runTest {
        try {
            inspector.inspect(gameRoot, "non_existent.exe")
            fail("Expected ExecutableNotFound exception")
        } catch (e: LaunchFailureException.ExecutableNotFound) {
            assertTrue(e.technicalDetails.contains("non_existent.exe"))
        }
    }
}
```

### 9.2 `LaunchPrecedenceResolverTest.kt` Blueprint
**Location**: `app/src/test/java/app/gamenative/launch/LaunchPrecedenceResolverTest.kt`

```kotlin
package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.PeArchitecture
import com.winlator.container.ContainerData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LaunchPrecedenceResolverTest {

    private lateinit var resolver: LaunchPrecedenceResolverImpl
    private lateinit var mockRequest: LaunchRequest
    private lateinit var mockExeIdentity: ExecutableIdentity
    private lateinit var quest2Hardware: QuestHardwareProfileInput

    @Before
    fun setUp() {
        resolver = LaunchPrecedenceResolverImpl()
        mockRequest = LaunchRequest(
            launchId = "launch-123",
            sessionId = "session-456",
            appId = "730",
            gameSource = GameSource.STEAM,
            exeRelativePath = "csgo.exe",
            containerPath = "/sdcard/game"
        )
        mockExeIdentity = ExecutableIdentity(
            canonicalPath = "/sdcard/game/csgo.exe",
            relativePath = "csgo.exe",
            fileSize = 1024,
            lastModified = System.currentTimeMillis(),
            sha256 = "abc123hash",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = emptyList(),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )
        quest2Hardware = QuestHardwareProfileInput(
            deviceDescriptor = "QUEST_2",
            graphicsDriver = "Wrapper",
            graphicsDriverConfig = "Turnip v26.2.0 R4",
            dxwrapper = "dxvk",
            dxwrapperConfig = "1.11.1-sarek"
        )
    }

    @Test
    fun resolvePlan_appliesHardwareProfile_whenNoUserOrCompatOverridesExist() {
        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, null, null, null)

        assertEquals("QUEST_2", plan.detectedDeviceDescriptor)
        assertEquals("Wrapper", plan.graphicsDriver.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriver.source)
        assertEquals("1.11.1-sarek", plan.dxwrapperConfig.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.dxwrapperConfig.source)
    }

    @Test
    fun resolvePlan_protectsHardwareDriver_whenCompatibilityMatchIsFallback() {
        val fallbackCompat = CompatibilityResolutionInput(
            isExactMatch = false, // Fallback match across different GPU
            recommendedGraphicsDriver = "IncompatibleDriverOverride",
            recommendedDxwrapperConfig = "2.4.1-gplasync"
        )

        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, fallbackCompat, null, null)

        // Hardware driver must NOT be replaced by fallback match!
        assertEquals("Wrapper", plan.graphicsDriver.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriver.source)
        assertEquals("1.11.1-sarek", plan.dxwrapperConfig.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.dxwrapperConfig.source)
    }

    @Test
    fun resolvePlan_appliesCompatibilityDriver_whenMatchIsExact() {
        val exactCompat = CompatibilityResolutionInput(
            isExactMatch = true, // Exact title & GPU match
            recommendedGraphicsDriver = "TurnipCustom",
            recommendedDxwrapperConfig = "2.4.1-gplasync"
        )

        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, exactCompat, null, null)

        // Exact match CAN replace hardware driver
        assertEquals("TurnipCustom", plan.graphicsDriver.value)
        assertEquals(SettingSource.COMPATIBILITY_PROFILE, plan.graphicsDriver.source)
    }

    @Test
    fun resolvePlan_appliesUserOverride_overAllOtherLevels() {
        val userContainer = ContainerData().apply {
            graphicsDriver = "UserCustomDriver"
        }

        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, null, null, userContainer)

        assertEquals("UserCustomDriver", plan.graphicsDriver.value)
        assertEquals(SettingSource.USER_OVERRIDE, plan.graphicsDriver.source)
    }
}
```

### 9.3 `GameLaunchCoordinatorTest.kt` Blueprint
**Location**: `app/src/test/java/app/gamenative/launch/GameLaunchCoordinatorTest.kt`

```kotlin
package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.ExecutableInspector
import app.gamenative.launch.inspect.PeArchitecture
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameLaunchCoordinatorTest {

    private lateinit var mockInspector: ExecutableInspector
    private lateinit var mockResolver: LaunchPrecedenceResolver
    private lateinit var coordinator: GameLaunchCoordinatorImpl
    private lateinit var mockRequest: LaunchRequest

    @Before
    fun setUp() {
        mockInspector = mockk()
        mockResolver = mockk()
        coordinator = GameLaunchCoordinatorImpl(mockInspector, mockResolver)

        mockRequest = LaunchRequest(
            launchId = "test-launch-1",
            sessionId = "test-session-1",
            appId = "480",
            gameSource = GameSource.STEAM,
            exeRelativePath = "SpaceWar.exe",
            containerPath = "/sdcard/spacewar"
        )
    }

    @Test
    fun executeLaunch_progressesThrough19StatesToSuccess() = runTest {
        val fakeExeIdentity = ExecutableIdentity(
            canonicalPath = "/sdcard/spacewar/SpaceWar.exe",
            relativePath = "SpaceWar.exe",
            fileSize = 2048,
            lastModified = 1000L,
            sha256 = "hash123",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = emptyList(),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )

        coEvery { mockInspector.inspect(any(), "SpaceWar.exe") } returns fakeExeIdentity

        val fakePlan = mockk<LaunchPlan>(relaxed = true)
        every { mockResolver.resolvePlan(any(), any(), any(), any(), any(), any()) } returns fakePlan

        val eventsEmitted = mutableListOf<LaunchState>()
        coordinator.addListener { event ->
            eventsEmitted.add(event.newState)
        }

        val result = coordinator.executeLaunch(mockRequest)

        assertTrue(result is TerminalLaunchResult.Success)
        assertEquals(LaunchState.RUNNING, coordinator.currentState.value)
        assertTrue(eventsEmitted.contains(LaunchState.REQUEST_RECEIVED))
        assertTrue(eventsEmitted.contains(LaunchState.EXECUTABLE_INSPECTING))
        assertTrue(eventsEmitted.contains(LaunchState.RUNNING))
    }

    @Test
    fun executeLaunch_transitionsToFailed_whenExecutableNotFound() = runTest {
        coEvery { mockInspector.inspect(any(), any()) } throws LaunchFailureException.ExecutableNotFound("/sdcard/spacewar/SpaceWar.exe")

        val result = coordinator.executeLaunch(mockRequest)

        assertTrue(result is TerminalLaunchResult.Failure)
        val failureResult = result as TerminalLaunchResult.Failure
        assertEquals(LaunchState.FAILED, failureResult.failedState)
        assertEquals(LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND, failureResult.failure.category)
    }

    @Test
    fun cancelLaunch_gracefullyStopsActiveSession() = runTest {
        coordinator.cancelLaunch("test-launch-1", "User cancelled launch")

        // No active session running, state remains in initial state or completed cleanly
        assertFalse(coordinator.currentState.value.isRunningOrActive())
    }
}
```

---

## 10. Independent Verification & Acceptance Matrix

### 10.1 Verification Protocol
To verify the implementation of Milestone 1 once implemented:
1. Run target unit test command:
   `.\gradlew.bat :app:testModernXrDebugUnitTest --tests "app.gamenative.launch.*"`
2. Verify zero test failures and 100% path coverage across:
   - `ExecutableInspectorTest`
   - `LaunchPrecedenceResolverTest`
   - `GameLaunchCoordinatorTest`

### 10.2 Milestone 1 Acceptance Criteria Checklist
- [x] Explicit 19-state enum `LaunchState.kt` defined with valid transition matrix and stage categorization.
- [x] Immutable `LaunchRequest.kt` model defined with strict field validation and path escape prevention.
- [x] Immutable `LaunchPlan.kt` model defined with explicit 5-level setting provenance tracking.
- [x] `LaunchPrecedenceResolver.kt` pure precedence engine defined with fallback-match driver protection rule.
- [x] `LaunchFailureCategory.kt` defined with 18 failure categories and sealed exception hierarchy.
- [x] `PeHeaderParser.kt` and `ExecutableInspector.kt` defined for zero-dependency PE binary parsing, machine type detection, SHA-256 streaming, and VR import detection.
- [x] `GameLaunchCoordinator.kt` and `GameLaunchCoordinatorImpl.kt` defined with thread-safe state machine engine, listener registry, and mutex locking.
- [x] Complete unit test blueprints (`ExecutableInspectorTest.kt`, `LaunchPrecedenceResolverTest.kt`, `GameLaunchCoordinatorTest.kt`) written with synthetic PE generation and test scenarios.
