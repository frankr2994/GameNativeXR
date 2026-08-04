# Handoff Report — Explorer 2: Hardware Profiles (Pillar 1) & Diagnostic Pipeline (Pillar 3)

## 1. Observation

Direct observations from source code in repository `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update`:

1. **Hardware Utilities & GPU Detection**:
   * File: `app/src/main/java/app/gamenative/utils/HardwareUtils.kt`
     * Line 21-29: `getMachineName()` formats `Build.MODEL` and `Build.MANUFACTURER`.
     * Line 36-47: `getSOCName()` reads `Build.SOC_MODEL` on API level 31+.
     * Line 52-100: `getGPUInfo(context)` uses a temporary `GLSurfaceView` to query `GLES20.glGetString(GLES20.GL_RENDERER)`.
   * File: `app/src/main/java/com/winlator/core/GPUInformation.java`
     * Lines 76-128: `loadGPUInformation()` uses off-screen EGL PBuffer context creation on a background thread with blocking `synchronized` `wait()` / `notify()`.
     * Lines 158, 183, 194, 199: `isAdreno6xx()`, `isTurnipCapable()`, `isAdreno710_720_732()`, `isAdreno740()` perform regex matches on renderer strings.
2. **Container Defaults & Global Mutations**:
   * File: `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`
     * Lines 48-96: `setContainerDefaults(context)` directly mutates `DefaultVersion.VARIANT`, `DefaultVersion.WINE_VERSION`, `DefaultVersion.DEFAULT_GRAPHICS_DRIVER`, `DefaultVersion.DXVK`, `DefaultVersion.VKD3D`, `DefaultVersion.WRAPPER`, `DefaultVersion.STEAM_TYPE`, and `DefaultVersion.ASYNC_CACHE`.
   * File: `app/src/main/java/com/winlator/container/ContainerData.kt`
     * Lines 12-127: Data class containing container execution parameters (`containerVariant`, `wineVersion`, `wow64Mode`, `emulator`, `fexcoreVersion`, `graphicsDriver`, `dxwrapper`, etc.).
3. **Application Startup & Crash Handling**:
   * File: `app/src/main/java/app/gamenative/PluviaApp.kt`
     * Lines 55-74: `preloadSystemLibraries()` is called before `Timber.plant()` and `CrashHandler.initialize(this)`.
   * File: `app/src/main/java/app/gamenative/CrashHandler.kt`
     * Line 25: `CRASH_FILE_HISTORY_COUNT = 1` limits retained crash reports to a single file.
     * Line 33: `logcatCommand` uses `logcat -d -t 256 --pid=${android.os.Process.myPid()}`. Uncaught JVM exceptions dump raw logcat output into `pluvia_crash_$timestamp.txt` without secret redaction.
4. **Process Output & Callbacks**:
   * File: `app/src/main/java/com/winlator/core/ProcessHelper.java`
     * Line 29: `private static final ArrayList<Callback<String>> debugCallbacks = new ArrayList<>();`
     * Lines 484-496: `removeAllDebugCallbacks()` clears all output callbacks globally, preventing multi-subscriber log observation.

---

## 2. Logic Chain

1. **Observation 1 & 2** show that device identity and execution defaults are currently determined by checking GPU renderer regex strings (`GPUInformation.isTurnipCapable()`, `isAdreno6xx()`) and mutating shared static state (`DefaultVersion.*`).
   * *Inference*: Mutating shared static defaults creates hidden side-effects across container operations. Explicit device detection (`QuestDeviceDetector`) and pure profile resolution (`HardwareExecutionProfileResolver`) are needed to map hardware facts into immutable `ContainerData` without global state mutation.
2. **Observation 3** shows that `PluviaApp.kt` loads system libraries before initializing Timber logging or the `CrashHandler`.
   * *Inference*: Early native library load failures or pre-initialization crashes bypass diagnostic logging. Initializing `DiagnosticSession` immediately after `super.onCreate()` ensures complete diagnostic coverage across main and `:vr_process` processes.
3. **Observation 3 & 4** show that `CrashHandler.kt` retains only 1 crash file, lacks multi-process session/launch IDs, lacks secret redaction, and `ProcessHelper.java` uses destructive global callbacks.
   * *Inference*: `ProcessOutputBus` must replace single global callbacks to support fan-out stdout/stderr streaming to Logcat, rotating JSONL files, and live UI sinks. `SecretRedactor` must sanitize all logs prior to sink emission, and `FailureTaxonomy` must categorize exit causes.

---

## 3. Caveats

1. **Physical Quest 3 Hardware**: Quest 3 signature fixtures and GPU family rules (Adreno 740) are modeled based on device specifications and repository analysis. Physical Quest 3 hardware validation remains required before promoting the Quest 3 profile from `candidate` status to `supported` (Plan Section 6.1).
2. **Read-Only Scope**: This task is a read-only investigation. Implementation of the specified classes (`QuestDeviceDetector`, `HardwareExecutionProfileResolver`, `DiagnosticSession`, `ProcessOutputBus`, etc.) must be executed in subsequent implementation phases.

---

## 4. Conclusion

The current codebase contains all raw building blocks for GPU probing and container execution, but suffers from global state mutation, missing device classification, late diagnostic initialization, destructive output callbacks, and unredacted single-file crash logging. 

`analysis.md` defines the complete class boundaries, interfaces, data contracts, and detection rules to implement Pillar 1 (`QuestDeviceDetector`, `HardwareExecutionProfileResolver`) and Pillar 3 (`DiagnosticSession`, `ProcessOutputBus`, Logcat/JSONL Sinks, `SecretRedactor`, `FailureTaxonomy`).

---

## 5. Verification Method

To verify these findings independently:

1. **Inspect Code Analysis Report**:
   * View `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_2/analysis.md`.
2. **Run Authoritative Repository Build & Test Commands**:
   * Test command: `.\gradlew.bat :app:testModernXrDebugUnitTest`
   * Build command: `.\gradlew.bat :app:assembleModernXrDebug`
3. **Code Inspection Verification**:
   * Inspect `HardwareUtils.kt`, `GPUInformation.java`, `ContainerUtils.kt`, `PluviaApp.kt`, `CrashHandler.kt`, and `ProcessHelper.java` to verify reported line numbers and logic paths.

---

## Rider MCP Tools and Actions Summary
* **Rider MCP Availability**: Standard repository shell and file analysis tools were used for file inspection. Rider symbol navigation rules and requirements were referenced in accordance with `F:\QuestVR\AGENTS.md`.
* **Rider Checks Status**: Pending code implementation phases will execute Rider inspections (`get_file_problems`, `search_symbol`) during implementation.
