# GameNativeXR Technical Analysis: Pillar 1 (Hardware Profiles) & Pillar 3 (Diagnostic Pipeline)

## Executive Summary

This report presents a comprehensive investigation of the current hardware detection mechanisms, container defaults, application startup sequence, crash handling, and process output management within the `Dev-Update` worktree. Findings are mapped directly against **Pillar 1 (Quest 2 and Quest 3 Hardware Execution Profiles)** and **Pillar 3 (High-Verbosity Diagnostic Pipeline)** of the `GameNativeXR Architecture Plan` (Sections 6 and 8).

Key findings:
1. **Global Mutable Defaults**: `ContainerUtils.setContainerDefaults()` currently mutates shared global static state in `DefaultVersion.*` based on `GPUInformation` probes. This creates implicit side-effects during launch.
2. **Device Identity vs GPU Probes**: The current codebase relies on `GPUInformation` regex checks (e.g. `isAdreno6xx`, `isTurnipCapable`, `isAdreno710_720_732`) rather than an explicit device classifier. Quest 2 and Quest 3 are not explicitly identified as distinct device profiles.
3. **Diagnostic Late Initialization**: `PluviaApp.kt` calls `preloadSystemLibraries()` *before* Timber or `CrashHandler.initialize()`. Any failure during system library preloading or in early multi-process (`:vr_process`) startup bypasses structured logging and uncaught exception capture.
4. **Destructive Output Callbacks**: `ProcessHelper.java` manages process stdout/stderr using a single global list of callbacks (`debugCallbacks`) cleared and re-added destructively by `XServerScreen.kt`. Simultaneous multi-subscriber log observation is impossible.
5. **Crash Log Retention & Redaction**: `CrashHandler.kt` retains only 1 crash file (`CRASH_FILE_HISTORY_COUNT = 1`), relies on unparsed logcat dumps, lacks multi-process session/launch IDs, lacks Android `ApplicationExitInfo` integration, and performs no secret redaction on tokens or credentials.

---

## 1. Baseline Technical Breakdown

### 1.1 Hardware Detection & Execution Defaults

#### Existing Source Inventory
* `app/src/main/java/app/gamenative/utils/HardwareUtils.kt`:
  * `getMachineName()`: Formats `Build.MODEL` and `Build.MANUFACTURER`.
  * `getSOCName()`: Accesses `Build.SOC_MODEL` on API level 31+.
  * `getGPUInfo(context)`: Asynchronously instantiates a `GLSurfaceView` to query `GLES20.glGetString(GLES20.GL_RENDERER)` and `GL_VENDOR`.
* `app/src/main/java/com/winlator/core/GPUInformation.java`:
  * Uses an off-screen EGL PBuffer surface on a background thread with blocking `synchronized` `wait()` / `notify()` to retrieve OpenGL ES2 `GL_RENDERER`, `GL_VENDOR`, `GL_VERSION`.
  * Caches renderer string in `PrefManager` under key `gpu_renderer2`.
  * Provides helper methods: `isAdreno6xx()`, `isTurnipCapable()`, `isAdreno710_720_732()`, `isAdreno740()`, `isAdreno8Elite()`, `isAdrenoA12()`.
  * Exposes native JNI methods: `getVulkanVersion()`, `getRenderer()`, `enumerateExtensions()`, `getVendorID()`.
* `app/src/main/java/com/winlator/container/ContainerData.kt`:
  * Immutable Kotlin data class storing execution parameters (`containerVariant`, `wineVersion`, `wow64Mode`, `emulator`, `fexcoreVersion`, `fexcorePreset`, `box86Version`, `box64Version`, `graphicsDriver`, `dxwrapper`, `dxwrapperConfig`, etc.) as well as UI and XR controller mappings.
* `app/src/main/java/com/winlator/container/Container.java`:
  * Stateful container object responsible for persisting settings into `.container` JSON files (`saveData()`, `loadData()`).
* `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`:
  * `setContainerDefaults(context)`: Mutates static fields on `DefaultVersion` (`VARIANT`, `WINE_VERSION`, `DEFAULT_GRAPHICS_DRIVER`, `DXVK`, `VKD3D`, `WRAPPER`, `STEAM_TYPE`, `ASYNC_CACHE`).
  * `toContainerData(container)`: Reads registry settings and extra fields into `ContainerData`.
  * `applyToContainer()`: Writes `ContainerData` into `Container` instance and Wine registry (`user.reg`).
* `app/src/main/java/app/gamenative/utils/BestConfigService.kt`:
  * `fetchBestConfig()`: Queries remote API for game/GPU configuration recommendations.
  * `applyGpuFamilyOverrides()`: Enforces GPU-specific overrides (e.g. DXVK 1.11.1-sarek for Adreno 6xx).
  * `validateComponentVersions()`: Validates requested Wine, DXVK, VKD3D, Box64, FEXCore versions against locally installed resources and content manifests.

#### Key Architectural Gaps in Hardware Handling
1. **Lack of Quest Device Classification**: There is no dedicated Quest device detector. Hardware capability is inferred entirely through GPU string matching (`isTurnipCapable`, `isAdreno6xx`).
2. **Global State Mutation**: `ContainerUtils.setContainerDefaults()` mutates `DefaultVersion` globally. This violates the goal of deterministic, immutable launch plan resolution.
3. **No Versioned Hardware Profile Schema**: Container execution settings are built via ad-hoc default assignments rather than mapping a versioned hardware profile asset into `ContainerData`.

---

### 1.2 Application Startup & Diagnostic Pipeline

#### Existing Source Inventory
* `app/src/main/java/app/gamenative/PluviaApp.kt`:
  * `onCreate()` sequence:
    1. `super.onCreate()`
    2. `preloadSystemLibraries()` (loads system `libjpeg.so`)
    3. `StrictMode` configuration
    4. `Timber.plant(Timber.DebugTree())` or `ReleaseTree()`
    5. `NetworkMonitor.init(this)`
    6. `CrashHandler.initialize(this)`
    7. `PrefManager.init(this)`
    8. Background tasks (Container migrations, downloader)
    9. PostHog analytics init
* `app/src/main/java/app/gamenative/CrashHandler.kt`:
  * Uncaught exception handler registered via `Thread.setDefaultUncaughtExceptionHandler()`.
  * `CRASH_FILE_HISTORY_COUNT = 1`: Deletes all older crash files, retaining only the most recent crash.
  * Captures last 256 lines of process logcat (`logcat -d -t 256 --pid=PID`).
  * Writes plain text report `pluvia_crash_$timestamp.txt` in `getExternalFilesDir(null)/crash_logs/`.
* `app/src/main/java/com/winlator/core/ProcessHelper.java`:
  * Manages native sub-process execution (`exec`, `execWithOutput`, `startProcess`).
  * Process management: `suspendProcess`, `resumeProcess`, `terminateProcess`, `killProcess`, `pauseAllWineProcesses`, `resumeAllWineProcesses`.
  * Output callbacks: `private static final ArrayList<Callback<String>> debugCallbacks = new ArrayList<>()`. Methods `addDebugCallback()`, `removeDebugCallback()`, `removeAllDebugCallbacks()`.

#### Key Architectural Gaps in Diagnostic Pipeline
1. **Late Diagnostic Initialization**: `preloadSystemLibraries()` runs *before* Timber or `CrashHandler` are installed. If native library loading fails, the error is uncaptured by the app's diagnostic system.
2. **Single-Process Focus**: `PluviaApp` and `CrashHandler` assume single-process execution. When `:vr_process` runs, session IDs and launch correlation are lost.
3. **Destructive Output Callbacks**: `ProcessHelper.debugCallbacks` allows only global registration. Callers such as `XServerScreen` call `removeAllDebugCallbacks()`, destroying any existing subscribers.
4. **Log Retention & Formatting Gaps**:
   * No machine-readable JSON Lines (JSONL) session sink.
   * Crash log retention is capped at 1 file, destroying diagnostic history.
   * No integration with Android `ApplicationExitInfo` to diagnose ANRs, low-memory kills, or OS process terminations.
   * No secret redaction rules for tokens, cookies, TOTP keys, or user paths.

---

## 2. Technical Requirements & Contract Specifications

### 2.1 Pillar 1: Quest Hardware Execution Profiles

#### Component 1: `QuestDeviceDetector`
* **Package**: `app.gamenative.hardware`
* **File Path**: `app/src/main/java/app/gamenative/hardware/QuestDeviceDetector.kt`
* **Responsibility**: Collect a process-cached `QuestDeviceDescriptor` and classify the execution target into `QUEST_2`, `QUEST_3`, or `UNKNOWN_META`.

##### Data Contracts
```kotlin
package app.gamenative.hardware

enum class QuestDeviceType {
    QUEST_2,
    QUEST_3,
    UNKNOWN_META
}

data class QuestDeviceDescriptor(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val socManufacturer: String?,
    val socModel: String?,
    val glVendor: String,
    val glRenderer: String,
    val glVersion: String,
    val supportedAbis: List<String>,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val isMetaQuestRuntime: Boolean
)

data class QuestDeviceClassificationResult(
    val deviceType: QuestDeviceType,
    val matchedRuleId: String,
    val confidenceScore: Float, // 0.0f to 1.0f
    val rejectedRuleIds: List<String>,
    val descriptor: QuestDeviceDescriptor
)
```

##### Class Boundary & Interface
```kotlin
interface QuestDeviceDetector {
    /**
     * Obtains the process-cached hardware descriptor.
     */
    fun getDescriptor(context: android.content.Context): QuestDeviceDescriptor

    /**
     * Classifies the current device with confidence score and evidence.
     */
    fun detectDevice(context: android.content.Context): QuestDeviceClassificationResult
}
```

##### Detection Rules & Signature Rules
1. **Rule 1: Manufacturer & XR Runtime Evidence Gate** (`RULE_META_EVIDENCE`)
   * Requires `descriptor.manufacturer` contains `"Meta"` or `"Oculus"` (case-insensitive) OR `isMetaQuestRuntime == true`.
   * Failure returns `UNKNOWN_META` with confidence `0.0f`.
2. **Rule 2: Signature Token Matching** (`RULE_QUEST2_SIGNATURE` / `RULE_QUEST3_SIGNATURE`)
   * **Quest 2**: Model/Device/Product contains `"Hollywood"`, `"Quest 2"`, `"Oculus Quest 2"`, or `"miramar"`.
   * **Quest 3**: Model/Device/Product contains `"Eureka"`, `"Quest 3"`, `"Oculus Quest 3"`, or `"eureka"`.
3. **Rule 3: GPU Family Consistency Verification** (`RULE_GPU_FAMILY_CONSISTENCY`)
   * **Quest 2 Requirement**: `glRenderer` must match Adreno 6xx (`GPUInformation.isAdreno6xx()`, e.g., `Adreno (TM) 650`).
   * **Quest 3 Requirement**: `glRenderer` must match Adreno 7xx (`GPUInformation.isTurnipCapable()` && `isAdreno740()`, e.g., `Adreno (TM) 740`).
   * If model signature and GPU family disagree, classification FAILS and returns `UNKNOWN_META`.
4. **Rule 4: Confidence Scoring Scale**
   * `1.0f`: Full match across Manufacturer + Model Signature + SoC + GPU Family + XR Runtime.
   * `0.8f`: Match across Manufacturer + Model Signature + GPU Family (SoC model unavailable).
   * `0.5f`: Partial heuristic match.
   * `0.0f`: Missing Meta evidence or conflicting GPU/Model signatures.

---

#### Component 2: `HardwareExecutionProfileResolver`
* **Package**: `app.gamenative.hardware`
* **File Path**: `app/src/main/java/app/gamenative/hardware/HardwareExecutionProfileResolver.kt`
* **Responsibility**: Pure, side-effect-free resolver mapping a `QuestDeviceClassificationResult` to a versioned `HardwareExecutionProfile` and merging it into `ContainerData`.

##### Data Contracts
```kotlin
package app.gamenative.hardware

import com.winlator.container.ContainerData

data class ComponentIdHash(
    val componentId: String,
    val expectedHashSha256: String? = null
)

data class HardwareExecutionProfile(
    val profileId: String,
    val schemaVersion: Int,
    val acceptedRuleIds: List<String>,
    val containerVariant: String,
    val wineVersion: String,
    val wow64Mode: Boolean,
    val emulator: String,
    val fexcoreVersion: String,
    val fexcoreTSOMode: String,
    val fexcoreX87Mode: String,
    val fexcoreMultiBlock: String,
    val fexcorePreset: String,
    val box86Version: String,
    val box64Version: String,
    val box86Preset: String,
    val box64Preset: String,
    val graphicsDriver: String,
    val graphicsDriverVersion: String,
    val graphicsDriverConfig: String,
    val dxwrapper: String,
    val dxwrapperConfig: String,
    val requiredPackagedComponents: List<ComponentIdHash>
)

data class HardwareProfileResolutionReport(
    val selectedProfile: HardwareExecutionProfile,
    val classification: QuestDeviceClassificationResult,
    val fieldDecisions: Map<String, String> // Field -> Decision Rationale
)
```

##### Class Boundary & Interface
```kotlin
interface HardwareExecutionProfileResolver {
    /**
     * Resolves an immutable HardwareExecutionProfile for the detected headset.
     */
    fun resolveProfile(classification: QuestDeviceClassificationResult): HardwareProfileResolutionReport

    /**
     * Maps the resolved HardwareExecutionProfile into ContainerData without mutating DefaultVersion.
     */
    fun applyProfileToContainerData(
        profile: HardwareExecutionProfile,
        baseContainerData: ContainerData
    ): ContainerData
}
```

##### Initial Candidates Mapped to ContainerData
* **Quest 2 Candidate Profile (`quest2_v1`)**:
  * `containerVariant` = `"bionic"`
  * `wineVersion` = `"proton-10.0-arm64ec-2"`
  * `wow64Mode` = `true`
  * `emulator` = `"FEXCore"`, `fexcoreVersion` = `"2605"`
  * `graphicsDriver` = `"Wrapper"`, `graphicsDriverVersion` = `"Turnip v26.2.0 R4"`
  * `dxwrapper` = `"dxvk"`, `dxwrapperConfig` = `"version=1.11.1-sarek,vkd3dVersion=2.14.1"`
* **Quest 3 Candidate Profile (`quest3_candidate_v1`)**:
  * `containerVariant` = `"bionic"`
  * `wineVersion` = `"proton-10.0-arm64ec-2"`
  * `wow64Mode` = `true`
  * `emulator` = `"FEXCore"`, `fexcoreVersion` = `"2605"`
  * `graphicsDriver` = `"Wrapper"`, `graphicsDriverVersion` = `"Turnip v26.2.0 R4"`
  * `dxwrapper` = `"dxvk"`, `dxwrapperConfig` = `"version=2.4.1-gplasync,vkd3dVersion=2.14.1"`
* **Constraint**: Hardware profiles MUST NOT contain screen resolution, refresh rate, XR CPU/GPU level, foveation, upscaling, or performance policy fields.

---

### 2.2 Pillar 3: High-Verbosity Diagnostic Pipeline

#### Component 1: `DiagnosticSession`
* **Package**: `app.gamenative.diagnostics`
* **File Path**: `app/src/main/java/app/gamenative/diagnostics/DiagnosticSession.kt`
* **Responsibility**: Process-safe structured log session manager, installed early in `PluviaApp.onCreate()`.

##### Data Contracts
```kotlin
package app.gamenative.diagnostics

enum class LogSeverity {
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL
}

enum class LogSubsystem {
    BOOT, DEVICE, CONTAINER, ENVIRONMENT, LAUNCH, INPUT, XR, MOD, CRASH, PROCESS
}

data class DiagnosticEvent(
    val timestampIso: String,
    val timestampNanos: Long,
    val sessionId: String,
    val launchId: String?,
    val processName: String,
    val pid: Int,
    val threadName: String,
    val severity: LogSeverity,
    val subsystem: LogSubsystem,
    val eventName: String,
    val launchState: String?,
    val durationMs: Long?,
    val fields: Map<String, String>,
    val parentOpId: String?
)
```

##### Class Boundary & Interface
```kotlin
interface DiagnosticSessionManager {
    val sessionId: String
    var activeLaunchId: String?

    /**
     * Must be invoked in Application.onCreate() immediately after super.onCreate(),
     * prior to system library preloading.
     */
    fun initializeEarly(application: android.app.Application)

    /**
     * Emits a structured diagnostic event across all active sinks.
     */
    fun logEvent(event: DiagnosticEvent)

    /**
     * Binds a new launch transaction ID across main and :vr_process processes.
     */
    fun startLaunchSession(launchId: String)

    /**
     * Queries recent in-memory ring buffer events for inclusion in crash reports.
     */
    fun getRecentEvents(count: Int = 100): List<DiagnosticEvent>
}
```

---

#### Component 2: `ProcessOutputBus`
* **Package**: `app.gamenative.diagnostics`
* **File Path**: `app/src/main/java/app/gamenative/diagnostics/ProcessOutputBus.kt`
* **Responsibility**: Multi-subscriber stdout/stderr bus replacing single global `ProcessHelper.debugCallbacks`.

##### Data Contracts
```kotlin
package app.gamenative.diagnostics

enum class ProcessOutputStreamType {
    STDOUT, STDERR
}

data class ProcessOutputChunk(
    val launchId: String,
    val pid: Int,
    val subsystem: String,
    val streamType: ProcessOutputStreamType,
    val timestampNanos: Long,
    val line: String
)

fun interface ProcessOutputSubscriber {
    fun onOutputLine(chunk: ProcessOutputChunk)
}
```

##### Class Boundary & Interface
```kotlin
interface ProcessOutputBus {
    /**
     * Registers a non-destructive subscriber for process output.
     */
    fun subscribe(subscriber: ProcessOutputSubscriber)

    /**
     * Unregisters a subscriber.
     */
    fun unsubscribe(subscriber: ProcessOutputSubscriber)

    /**
     * Attaches stdout and stderr stream drainers for a spawned process.
     */
    fun attachProcessPipes(
        launchId: String,
        pid: Int,
        subsystem: String,
        stdoutStream: java.io.InputStream,
        stderrStream: java.io.InputStream
    )

    /**
     * Broadcasts a line to all registered subscribers.
     */
    fun publish(chunk: ProcessOutputChunk)
}
```

---

#### Component 3: Logcat & JSONL Sinks
* **Package**: `app.gamenative.diagnostics.sinks`
* **File Paths**:
  * `app/src/main/java/app/gamenative/diagnostics/sinks/LogcatSink.kt`
  * `app/src/main/java/app/gamenative/diagnostics/sinks/JsonLinesSink.kt`

##### Requirements
* **LogcatSink**: Converts `DiagnosticEvent` to threadtime-formatted logcat messages.
* **JsonLinesSink**: Appends serialized JSON Lines (`.jsonl`) to files located in `context.getExternalFilesDir("diagnostics")`.
  * Multi-process file lock / atomic append.
  * Retention Policy: Maximum 10 session files, 5 MB max file size per session. Older files purged automatically.

---

#### Component 4: Secret Redaction Rules (Section 8.5)
* **Package**: `app.gamenative.diagnostics.redaction`
* **File Path**: `app/src/main/java/app/gamenative/diagnostics/redaction/SecretRedactor.kt`

##### Redaction Patterns & Targets
```kotlin
object SecretRedactor {
    private val PATTERNS = listOf(
        Regex("(?i)(access_token|refresh_token|sessionid|totp|password|secret|authorization)=\\s*[^&\\s]+"),
        Regex("(?i)Bearer\\s+[A-Za-z0-9\\-\\._~\\+\\/]+=*"),
        Regex("(?:\\/data\\/user\\/\\d+\\/|\\/home\\/)[a-zA-Z0-9_\\-]+")
    )

    fun redact(input: String): String {
        var sanitized = input
        for (pattern in PATTERNS) {
            sanitized = pattern.replace(sanitized, "[REDACTED_SECRET]")
        }
        return sanitized
    }
}
```

---

#### Component 5: Failure Taxonomy Logging (Section 5.5)
* **Package**: `app.gamenative.diagnostics`
* **File Path**: `app/src/main/java/app/gamenative/diagnostics/FailureTaxonomy.kt`

##### Classified Failure Categories
```kotlin
enum class FailureClass {
    INSTALL_OR_EXECUTABLE_NOT_FOUND,
    UNSUPPORTED_OR_UNKNOWN_EXEC_ARCH,
    MISSING_TRANSLATION_COMPONENT,
    INCOMPATIBLE_HARDWARE_PROFILE,
    MISSING_GRAPHICS_DRIVER_OR_DXWRAPPER,
    STEAM_AUTH_FAILURE,
    CONTAINER_PREPARATION_FAILURE,
    GUEST_PROCESS_START_FAILURE,
    GUEST_PROCESS_EARLY_EXIT,
    GUEST_WINDOW_UNRESPONSIVE,
    NATIVE_XR_HOST_INIT_FAILURE,
    VR_RUNTIME_HANDSHAKE_TIMEOUT,
    MOD_MANIFEST_OR_HASH_MISMATCH,
    HOOK_PLACEMENT_OR_ROLLBACK_FAILURE,
    JAVA_UNCAUGHT_EXCEPTION,
    NATIVE_CRASH,
    ANDROID_PROCESS_TERMINATION // ANR, LowMemory, OS SIGKILL
}

data class ClassifiedFailure(
    val failureClass: FailureClass,
    val launchId: String,
    val lastLaunchState: String,
    val exitCode: Int?,
    val userFacingMessage: String,
    val technicalDetails: Map<String, String>,
    val cause: Throwable? = null
)
```

---

## 3. Comparison Matrix: Existing vs. Planned Diagnostic Architecture

| Aspect | Baseline Implementation (`Dev-Update`) | Planned Architecture (`GameNativeXR Plan`) |
| :--- | :--- | :--- |
| **Diagnostic Init** | Timber planted at line 66 of `PluviaApp.kt` *after* `preloadSystemLibraries()`. | Immediate init in `PluviaApp.onCreate()` *before* `preloadSystemLibraries()`. |
| **Process Identity** | Single process focus; no session tracking across `:vr_process`. | Dual-process `sessionId` + per-launch `launchId` passed via Intent extras. |
| **Process Output** | Global mutable list `debugCallbacks` in `ProcessHelper.java` cleared destructively. | Multi-subscriber `ProcessOutputBus` with non-blocking stream drainers and rate limits. |
| **Crash Storage** | Single crash file (`CRASH_FILE_HISTORY_COUNT = 1`) overwriting history. | Rotating JSONL files + structured crash summary + 100-event ring buffer. |
| **Redaction** | No secret redaction on logcat, credentials, tokens, or paths. | Mandatory `SecretRedactor` applied before all sinks (Logcat, JSONL, Summary). |
| **Exit Reason Analysis**| Only Java uncaught exceptions captured. | JVM exceptions + native crashes + `ApplicationExitInfo` query on app restart. |

---

## 4. Implementation Verification Plan

1. **Rider MCP Code Inspections**:
   * Navigate symbol usages for `GPUInformation`, `HardwareUtils`, `ContainerUtils`, `ProcessHelper`, `CrashHandler`.
   * Verify nullability, concurrency annotations, and thread safety across multi-process boundaries.
2. **Authoritative Shell Tests**:
   * Unit test execution: `.\gradlew.bat :app:testModernXrDebugUnitTest`
   * APK build verification: `.\gradlew.bat :app:assembleModernXrDebug`
