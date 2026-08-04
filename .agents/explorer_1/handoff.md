# Handoff Report — Explorer 1

## 1. Observation

### Codebase Components Examined
1. **Launch Orchestrator**:
   - `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` (5879 lines)
   - Key functions inspected: `XServerScreen` (line 343), `setupXEnvironment` (line 3517), `getWineStartCommand` (line 3999), `chainPreInstallSteps` (line 3819).
2. **Launch Backends**:
   - `app/src/main/java/com/winlator/xenvironment/components/GuestProgramLauncherComponent.java` (389 lines): Base class, PRoot process creation.
   - `app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java` (698 lines): Bionic variant, Arm64EC, FEXCore/Box64 DLLs, `addRealSteamEnvVars`, `bootstrapNativeSteamClient` (JNI call to `SteamBootstrap.INSTANCE.start`).
   - `app/src/main/java/com/winlator/xenvironment/components/GlibcProgramLauncherComponent.java` (343 lines): Glibc variant, `LD_PRELOAD` for `libredirect.so` and `libandroid-sysvshm.so`.
   - `app/src/main/java/com/winlator/core/ProcessHelper.java` (614 lines): Low-level process execution, process signal methods (`suspendProcess`, `resumeProcess`, `killProcess`), `listSubProcesses`, `listRunningWineProcesses`, static `debugCallbacks` list.
3. **Setting Overrides & Precedence**:
   - `app/src/main/java/app/gamenative/utils/BestConfigService.kt` (940 lines): Online best config API client, match types (`exact_gpu_match`, `gpu_family_match`, `fallback_match`), mandatory GPU family overrides in `applyGpuFamilyOverrides` for Adreno 6xx, Adreno 8 Elite Gen 5, Adreno A12, component validation in `validateComponentVersions`.
   - `app/src/main/java/app/gamenative/utils/ContainerUtils.kt` (1481 lines): `setContainerDefaults` mutating global `DefaultVersion` statics, `toContainerData`, `applyToContainer` updating Wine Direct3D registry settings.
   - `com/winlator/container/ContainerData.kt` (305 lines): Container configuration data class (65+ fields).

### Verified Unit Test Suite
Ran command:
`.\gradlew.bat :app:testModernXrDebugUnitTest`
Result:
```
BUILD SUCCESSFUL in 8s
52 actionable tasks: 13 executed, 39 up-to-date
```
Zero test failures on baseline.

---

## 2. Logic Chain

1. **Observation**: `XServerScreen.kt` currently performs UI presentation and low-level launch orchestration in a single 5879-line file (`setupXEnvironment` at line 3517, `getWineStartCommand` at line 3999).
   **Inference**: To satisfy Section 5 of the Architecture Plan (Unified Launch Contract), launch orchestration must be extracted from `XServerScreen.kt` into a standalone state-machine coordinator (`GameLaunchCoordinator`). `XServerScreen` should observe coordinator state via coroutine flows to update Compose UI.

2. **Observation**: Process execution logging currently uses `ProcessHelper.debugCallbacks` (a static `ArrayList<Callback<String>>` in `ProcessHelper.java:29`). `ProcessHelper.removeAllDebugCallbacks()` clears callbacks globally across the process.
   **Inference**: Concurrent or sequential launches wipe global debug callbacks. The architecture must replace static callbacks with a session-scoped `ProcessOutputBus` subscriber model tied to a `launchId`.

3. **Observation**: `ContainerUtils.setContainerDefaults()` (line 48) directly mutates global static variables in `DefaultVersion` (`DefaultVersion.VARIANT`, `DefaultVersion.DXVK`, etc.) based on hardware probes.
   **Inference**: Mutating global static defaults at launch time causes race conditions and non-deterministic behavior. The 5-level precedence model (`SettingPrecedenceResolver`) must compute an immutable `LaunchPlan` per launch request without altering global application-wide defaults.

4. **Observation**: Executable paths are currently resolved by string heuristics (`getInstalledExe`, `choosePrimaryExeFromDisk`) and launched directly without examining PE headers or binaries.
   **Inference**: `ExecutableInspector` must inspect binary headers before Wine/Proton launch to detect architecture (x86 32-bit vs x64 64-bit), compute SHA-256 digests, and inspect import tables for VR APIs (`openxr_loader.dll`, `openvr_api.dll`) to validate WoW64 support and select tracking modes cleanly.

5. **Observation**: Failures currently throw raw Java exceptions or emit generic strings via `onGameLaunchError`, offering no classification.
   **Inference**: A structured failure taxonomy (`LaunchFailureCategory` with 18+ categories defined in Section 5.5) is required to categorize launch errors, present actionable recovery options to users, and log machine-readable diagnostic telemetry.

---

## 3. Caveats

1. **Read-Only Inspection Scope**: As an explorer subagent, no main source code files were modified. Detailed technical findings and contract specifications were written to `.agents/explorer_1/analysis.md`.
2. **Native Source Revision**: The source code for `libxr.so` is not present in this worktree (tracked prebuilt binaries only). Phase 0 native XR source recovery remains pending before Phase 6 native OpenXR guest runtime implementation.
3. **Physical Quest 3 Validation**: Quest 3 hardware profiles remain unpromoted until physical device descriptors and qualification test evidence are collected on physical Quest 3 hardware.

---

## 4. Conclusion

The existing launch pipeline in GameNativeXR has been mapped against Section 5 of `GameNativeXR_Architecture_Plan.md`.
- `XServerScreen.kt` launch orchestration can be extracted incrementally behind the `GameLaunchCoordinator` 19-state state machine interface.
- Existing backends (`BionicProgramLauncherComponent`, `GlibcProgramLauncherComponent`, `GuestProgramLauncherComponent`) are ready to be wrapped as execution backends without disrupting working Wine/Proton environment setup.
- The 5-level precedence model and immutable `LaunchPlan` design resolve global state mutation flaws in `ContainerUtils` and `BestConfigService`.
- Exact requirements, class boundaries, Kotlin interface contracts, and failure taxonomies have been specified in `analysis.md`.

---

## 5. Verification Method

To verify these findings and contract definitions:

1. **Unit Test Verification**:
   Execute the project's authoritative unit test suite:
   ```powershell
   .\gradlew.bat :app:testModernXrDebugUnitTest
   ```
   *Expected Result*: Build completes with zero failures.

2. **Inspect Technical Analysis & Contracts**:
   Read `F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_1/analysis.md` for full technical specifications of:
   - `GameLaunchCoordinator` (19-state state machine & event system)
   - `ExecutableInspector` (PE header parsing, x86/x64 detection, SHA-256, API imports)
   - Immutable `LaunchRequest`, `LaunchPlan`, and 5-level `SettingPrecedenceResolver`
   - Structured `LaunchFailureCategory` taxonomy

3. **Codebase Cross-Check**:
   - Inspect `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` lines 3517-4400.
   - Inspect `app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java` lines 99-380.
   - Inspect `app/src/main/java/app/gamenative/utils/BestConfigService.kt` lines 220-300.
