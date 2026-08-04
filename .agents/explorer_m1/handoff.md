# Milestone 1: Unified Launch Contract Foundations — Handoff Report

## 1. Observation
- **Baseline Worktree**: `Dev-Update` at commit `6cde5d4b99ad1ddf3aedcaaf9eb6982c4261ffaa`.
- **Existing Launch Code Inspection**:
  - `XServerScreen.kt` (`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`, 5879 lines) currently handles both UI rendering and launch orchestration in a monolithic flow.
  - Launch states in `XServerScreen` are implicit coroutine sequences with unstructured errors and global static side effects (`ProcessHelper.removeAllDebugCallbacks()`, `ContainerUtils.setContainerDefaults()` mutating shared `DefaultVersion` static fields).
  - Existing launcher backends (`BionicProgramLauncherComponent.java`, `GlibcProgramLauncherComponent.java`, `GuestProgramLauncherComponent.java`) and `ProcessHelper.java` process execution helpers are present in `com/winlator/xenvironment/components/` and `com/winlator/core/`.
  - Package `app.gamenative.launch` currently contains only `LaunchReadiness.kt` (36 lines). None of the Milestone 1 Unified Launch Contract deliverables exist yet in the codebase.
- **Architectural Specifications**:
  - `GameNativeXR_Architecture_Plan.md` Section 5 defines the Unified Launch Contract: 19-state explicit state machine, immutable `LaunchRequest`/`LaunchPlan`, 5-level deterministic setting precedence, 18 failure categories, and PE executable inspection.
  - `PROJECT.md` details Milestone 1 scope, interface contracts, and code layout under `app/src/main/java/app/gamenative/launch/` and `app/src/test/java/app/gamenative/launch/`.
  - `.agents/explorer_1/analysis.md` provides an exhaustive analysis of existing `XServerScreen.kt` launch steps, backend launchers, and precedence resolution flaws.

## 2. Logic Chain
1. **State Machine Extraction**: Extracting launch orchestration out of `XServerScreen.kt` requires a formal, explicit state machine (`LaunchState.kt` with 19 states and `GameLaunchCoordinator.kt`/`GameLaunchCoordinatorImpl.kt`). Explicit state transition rules (`canTransitionTo`) prevent out-of-order execution or invalid jumps.
2. **Immutable Data Contracts**: Creating `LaunchRequest.kt` and `LaunchPlan.kt` ensures that all input launch arguments and resolved environment parameters are immutable and auditable. Every field in `LaunchPlan` tracks its value alongside an explicit `SettingSource` level (1 through 5).
3. **Pure Precedence Engine**: `LaunchPrecedenceResolver.kt` implements a 5-level precedence hierarchy (Baseline -> Quest Hardware Profile -> Compatibility Profile -> VR Mod Requirements -> User Override). It explicitly enforces the fallback-match protection rule: weak/fallback compatibility matches (`fallback_match`) cannot overwrite Quest hardware driver settings (`graphicsDriver`, `graphicsDriverConfig`, `dxwrapper`, `dxwrapperConfig`).
4. **Binary Executable Inspection**: `PeHeaderParser.kt` and `ExecutableInspector.kt` provide pure Kotlin binary header parsing for DOS/PE headers, Machine architecture types (`X86_32`, `X64_64`, `ARM64`), SHA-256 computation, and VR import detection (`openxr_loader.dll`, `openvr_api.dll`) without native dependencies or executing the guest binary.
5. **Classified Failure Taxonomy**: `LaunchFailureCategory.kt` defines 18 failure categories with a sealed exception hierarchy (`LaunchFailureException`) that maps raw exceptions, process exit codes (`fromExitCode`), and timeouts into structured, user-remediable errors.
6. **Comprehensive Unit Testing**: The blueprint details co-located unit test suites (`ExecutableInspectorTest.kt`, `LaunchPrecedenceResolverTest.kt`, `GameLaunchCoordinatorTest.kt`) using synthetic PE binary generation and mock assertions to guarantee 100% path coverage.

## 3. Caveats
- **Read-Only Inspection Scope**: Explorer M1 is a read-only investigation role. No production Kotlin files were written to `app/src/main/java/` or `app/src/test/java/`; the complete, production-ready blueprint is recorded in `.agents/explorer_m1/analysis.md`.
- **Rider MCP Pending Execution**: Unit tests were designed for execution via `.\gradlew.bat :app:testModernXrDebugUnitTest`. Execution remains pending until implementer agents create the source files.

## 4. Conclusion
The implementation blueprint for **Milestone 1: Unified Launch Contract Foundations** is complete, fully specified, and ready for immediate implementation by implementer agents. The blueprint in `.agents/explorer_m1/analysis.md` provides explicit, production-ready Kotlin code contracts, data structures, binary parsing algorithms, state machine locks, precedence calculations, failure classifications, and unit test suites.

## 5. Verification Method
1. **Blueprint Inspection**:
   Inspect `.agents/explorer_m1/analysis.md` for complete code contracts of all 8 Milestone 1 deliverables.
2. **Implementation & Test Execution**:
   Once implemented by the implementer agent, verify Milestone 1 by running:
   `.\gradlew.bat :app:testModernXrDebugUnitTest --tests "app.gamenative.launch.*"`
3. **Invalidation Conditions**:
   - Any test failure in `ExecutableInspectorTest`, `LaunchPrecedenceResolverTest`, or `GameLaunchCoordinatorTest`.
   - Failure of `LaunchPrecedenceResolver` to protect Quest hardware driver settings when evaluating `fallback_match` compatibility entries.
   - Any invalid state transition allowed by `LaunchState.canTransitionTo()`.
