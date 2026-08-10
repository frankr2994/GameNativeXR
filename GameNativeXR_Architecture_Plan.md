# GameNativeXR Continuation Architecture Plan

## Document status

- **Authoritative baseline:** `Dev-Update` at `1a98caca4bff21cd0f242cd05fbf18e82dbcd274`
- **Remote baseline:** synchronized with `origin/Dev-Update`
- **Continuation authorized:** 2026-08-08
- **Implementation resumed:** 2026-08-09 (`Proceed`)
- **Primary qualified hardware:** physical Quest 2; right controller available, left controller broken
- **Quest 3 status:** candidate data only; physical qualification is pending
- **Native XR status:** changes blocked until the tracked `libxr.so` source and provenance are recovered

This document supersedes the implementation sequence and phase-status claims in the pre-`1a98caca` version of `GameNativeXR_Architecture_Plan.md`. Those claims are historical and must not be used to infer current completion. The existing GameNative environment setup remains the execution source of truth until it is wrapped and validated incrementally.

## Baseline evidence

Before this continuation was written, Rider MCP was confirmed attached to `F:\QuestVR\_worktrees\GameNativeXR-Dev-Update`. Rider file navigation and inspection are available, but the IDE project model exposes only `Miscellaneous Files`; Gradle is authoritative for Android builds and tests.

The clean baseline was confirmed with:

```powershell
git status --short --branch
git rev-parse HEAD
```

It reported `Dev-Update...origin/Dev-Update`, no working-tree changes, and commit `1a98caca4bff21cd0f242cd05fbf18e82dbcd274`.

The historical baseline remains clean at that commit. The resumed 2026-08-09 worktree is intentionally dirty with the continuation implementation and its tests already in progress. Those changes are preserved and reviewed in place; no reset, checkout, or broad cleanup is permitted while this continuation is active.

Commit `1a98caca` retains debug-only Meta XR Operator assets under `app/src/modernXrDebug` and extends `tools/verify-quest-apk.ps1` with explicit `Require` and `Forbid` policies. The baseline validation evidence is:

```powershell
.\gradlew.bat :app:assembleModernXrDebug
powershell -ExecutionPolicy Bypass -File .\tools\verify-quest-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\modernXr\debug\app-modernXr-debug.apk `
  -OperatorLayerPolicy Require
.\gradlew.bat :app:assembleModernXrRelease
powershell -ExecutionPolicy Bypass -File .\tools\verify-quest-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\modernXr\release\app-modernXr-release.apk `
  -OperatorLayerPolicy Forbid
```

The continuation reran these gates on 2026-08-08. The debug APK contains the required Operator layer (SHA-256 `53724FB29C6AF2D2837A9937E6BF4C024B7AB2E1A834DFB59135B8C5B5C82FF6`); the release APK excludes it (SHA-256 `3ABF8460F60E43AC0CCDD3FD628E19B277711AE5E6C9EA4C9FDDF4D49A05512D`). The original `GOGDownloadManagerTest.setUp` hang was removed with a test-only parallelism seam and Robolectric startup isolation. A clean Windows full-suite run now terminates: 942 tests execute, with 13 remaining failures confined to POSIX Wine drive/registry fixtures, a privileged symlink fixture, and a live manifest-content download returning non-XZ data. The focused launch/GOG suite passes.

Physical Quest 2 evidence on 2026-08-09 is equally authoritative:

- MQDH observes the connected Quest 2, the debug GameNative package, a connected right controller with position tracking, and a searching/untracked left controller.
- The debug-only Meta XR Operator API layer is live in the `:vr_process`; it observes a real Winlator OpenXR session, valid HMD tracking, live 72 Hz cylinder-layer frames, and the expected Quest limitation that composited-image capture is unavailable.
- The session briefly reaches `FOCUSED` but ordinarily returns to `VISIBLE` when the system focus placeholder is foregrounded. This remains diagnostic evidence, not a basis for injected input.
- The physical right controller does not produce a Winlator pointer, including after the documented right-thumbstick-plus-A pointer-mode toggle. Operator reports no active right-hand interaction profile and an inactive, untracked aim action even while MQDH reports the physical controller connected.
- Therefore the Java routing and X-server injection layer is present but lacks a live native controller frame. Do not substitute ADB, scrcpy, or synthetic Operator input for the controller-only acceptance gate.

## Current implementation inventory

### Completed and retained

- Early diagnostics, secret redaction, process-output fanout, and crash integration.
- Canonical Quest hardware detection and versioned Quest 2/Quest 3 execution profiles.
- PE executable inspection and launch-plan/precedence types.
- Safe `FLAT_3DOF` tracking resolution when native or modded VR capability is unavailable.
- Controller ray pointer, explicit pointer/navigation modes, one-controller fallback, X-server-safe input injection, and system-dialog routing.
- Debug-only Meta XR Operator packaging with release-exclusion verification.

### Implemented by this continuation

- Authoritative typed install/executable resolution with traversal, canonical containment, ambiguity, stale-candidate, and missing-file failures before environment startup.
- An immutable `ResolvedContainerExecutionConfig`, stable failure records, a nullable real-PID `RunningLaunchHandle`, and an event-driven `GameLaunchCoordinatorImpl`; fabricated PID `1234` is removed.
- A narrow `XServerLaunchExecutionBackend` adapter around the existing environment setup, with real preinstall, component, command, PID, first-window, exit, cancellation, and cleanup observations.
- A live coordinator caller for normal Steam XR launches. Boot-to-container, graphics-test, and non-Steam paths intentionally retain their existing flow.
- First-window gating for flat `RUNNING`, XR-handshake gating for VR `RUNNING`, idempotent cancellation, duplicate-session rejection across recreation, and exactly-once terminal cleanup.
- Runtime component validation and redacted setting/source logging before Wine starts.
- Robolectric production-startup isolation and a focused GOG parallelism provider that preserve production download behavior while removing the former setup deadlock.
- A launch-only adapter (`ResolvedContainerExecutionOverlay`) that applies the resolved configuration during execution and restores the mutable `Container` state, protecting persisted data from temporary overrides.

### Partially live and requiring device qualification

- A full saved `ContainerData` snapshot currently wins normal precedence fields as explicit user values. The live caller therefore preserves user configuration but does not yet prove hardware-profile defaults are applied field-by-field.
- The Quest profile requests `Turnip v26.2.0 R4`, while the packaged manifest currently exposes `adrenotools-turnip26.0.0_R4`; validation truthfully rejects a hardware-derived request for the missing component.
- Existing controller/keyboard routing is retained, but the first physical Quest 2 run proves it is not receiving a usable native right-controller frame. Reliable guest text-focus evidence, automatic XR keyboard opening, and right-controller-only physical acceptance remain pending.
- Flat `FLAT_3DOF` is the planning default, but x86/x64 first-window and rendering/input qualification still require a physical Quest 2 run with an explicit ADB serial.

### Blocked

- Native XR host changes, guest runtime work, and protocol expansion are blocked on exact `libxr.so` source, revision, license, and reproducible build evidence.
- The physical right-controller failure is blocked at the same native host boundary: `XrActivity.getAxes()` and `getButtons()` are JNI calls into the tracked `libxr.so`, and the Java enum ordering is explicitly part of that binary contract. Do not alter JNI/controller behavior until Phase 8 evidence exists.
- Quest 3 profile promotion and release qualification are blocked on physical Quest 3 hardware.
- Automatic keyboard opening is blocked until a real X-server/Wine text-focus signal is observed and defined.
- End-to-end physical flat-launch qualification and device-correlated collection require a connected Quest 2 and explicit ADB serial.
- Manifest-driven external VR runtime/mod hooks remain gated behind native XR source recovery and the guest runtime bridge.

## Immediate architectural objective

Connect the existing planning, hardware, tracking, diagnostics, and controller work to the real Steam/Wine/X-server launch path without replacing the working GameNative environment setup wholesale. A real launch must resolve the game install and executable before Wine starts, construct an immutable launch-only execution configuration, adapt the existing backend, and expose truthful lifecycle evidence through one coordinator.

## Public interfaces and architecture

### GameInstallResolver

Input:

- Steam app ID;
- selected launch option;
- game source; and
- current container.

Output: `ResolvedGameInstall`, containing the verified host install root, guest-mounted root, selected executable, and resolution evidence. The resolver must distinguish the game install directory from the Wine container/prefix directory. Conflicting Steam metadata, stale saved paths, path traversal, containment failures, and missing files fail before Wine starts.

### LaunchPlanResolver

Input: resolved install, executable identity, Quest hardware profile, compatibility data, user overrides, and requested tracking mode.

Output: immutable `LaunchPlan`. Planning must not mutate the persistent container.

### LaunchExecutionBackend

An adapter around the existing `XServerScreen.setupXEnvironment` flow. It accepts the immutable plan, keeps the existing Bionic/Glibc guest launch components, and reports actual lifecycle events.

### RunningLaunchHandle

Replaces the required fabricated PID. It contains launch ID, backend identity, nullable root PID, observed child PIDs, cancellation capability, and termination result. A missing PID is represented as unavailable.

### ResolvedContainerExecutionConfig

An immutable launch-only view containing:

- container variant and Wine version;
- WoW64 mode;
- FEXCore/Box86/Box64 versions and presets;
- graphics driver and configuration;
- DX wrapper and configuration;
- environment variables and DLL overrides;
- required packaged component IDs;
- tracking mode and active mod ID.

The existing container remains persisted user configuration. The resolved view is applied only while constructing the environment and command, then discarded during cleanup. Hardware profiles must never rewrite saved containers or global defaults.

## State and failure model

Coordinator state is driven by backend evidence:

1. install resolution completed;
2. executable inspection completed;
3. profile and precedence resolution completed;
4. Wine prefix preparation started/completed;
5. environment components started;
6. preinstall command started/completed;
7. guest command submitted;
8. guest PID observed, when available;
9. first X-server window or XR handshake observed;
10. running;
11. guest exited, crashed, timed out, or was cancelled; and
12. cleanup completed or failed.

`RUNNING` is forbidden merely because setup functions returned. Flat mode requires first-window evidence; VR mode requires a valid runtime handshake.

Failure records include a stable failure code, user-facing explanation, technical details, failed state, backend, executable identity when available, and recovery guidance.

## Continuation sequence

### Phase 0 — update the roadmap and freeze the baseline

Deliverables:

- Replace obsolete phase/status claims with this continuation roadmap.
- Confirm Rider project, Git branch, commit, remote tracking, and cleanliness.
- Record debug/release APK validation evidence from `1a98caca`.
- Record the `GOGDownloadManagerTest.setUp` full-suite hang as pre-existing infrastructure debt.

Exit gate: one authoritative roadmap separates completed, scaffolded, blocked, and unstarted work. This phase changes no feature code.

### Phase 1 — map the live launch path

Perform read-only call-path analysis before restructuring:

- Trace Steam library launch requests through `XServerScreen`.
- Locate selection of install root, executable, launch option, container, Wine command, guest mount, and backend.
- Trace preinstall commands, Steam modes, `unpackExecutableFile`, environment startup, first-window events, process callbacks, and cleanup.
- Record every conversion among Android host paths, container drives, and Wine guest paths.
- Identify the smallest adapter boundary around the existing environment setup.

Exit gate: a checked-in launch-path map identifies inputs, outputs, callbacks, ownership, and the agreed adapter boundary. No launch logic is moved.

### Phase 2 — authoritative install and executable resolution

Implement `GameInstallResolver`:

- Resolve the real Steam install directory from current GameNative/Steam metadata.
- Validate the selected launch option and relative executable under that directory.
- Preserve typed host, mounted, and Wine-visible paths.
- Reject traversal and filesystem-link escape.
- Run the existing PE inspector only after install resolution succeeds.
- Record architecture, imports, hash, and launcher classification.
- Do not add DRM removal, executable patching, or title-specific bypasses.

Tests cover valid/missing/stale/conflicting installs, host-to-guest conversion, x86/x64 fixtures, traversal and symlink/junction containment, and launcher-versus-final executable selection.

Exit gate: the launcher can explain exactly which executable it intends to run and why; mismatches are controller-visible before environment startup.

### Phase 3 — connect the coordinator to the existing backend

- Construct one coordinator per launch session from live UI/container/Steam state.
- Let the coordinator perform resolution and planning.
- Pass the immutable plan into an adapter around `setupXEnvironment`.
- Convert actual callbacks into coordinator transitions.
- Replace PID `1234` with `RunningLaunchHandle`.
- Preserve preinstall chaining, Steam preparation, audio, X-server, renderer, Bionic/Glibc launchers, and cleanup behavior.
- Make cancellation idempotent and prevent duplicate launches after activity recreation.

Tests cover callback/state mapping, the first-window gate, early exit, preinstall failure, setup exceptions, cancellation, duplicate rejection, listener isolation, and exactly-once cleanup.

Exit gate: a real flat-game launch produces a truthful timeline with no fabricated PID, completion, or step.

### Phase 4 — activate the canonical Quest 2 execution profile

- Detect the headset once per launch.
- Resolve and validate the packaged profile and all required components before setup.
- Apply it through `ResolvedContainerExecutionConfig`.
- Preserve explicit per-game overrides through the existing precedence contract.
- Log each field and source after redaction.
- Fail recoverably on unknown or conflicting hardware classification.

Quest 2 is the only physically qualified profile. Quest 3 assets remain candidate data. This phase adds no refresh-rate, foveation, resolution, performance-level, upscaling, or other tuning.

Exit gate: Quest 2 selects one validated x86/x64 stack without mutating saved container state; missing components fail before Wine.

### Phase 5 — controller-only pre-game interaction

- Preserve pointer, navigation, game-input, keyboard, and system-menu modes.
- Keep a manual keyboard command available.
- Add a visible XR keyboard operable with the right controller alone.
- Route text and special keys through lock-owning X-server APIs.
- Add a real guest text-focus signal from observed X-server/Wine behavior.
- Never infer text focus from titles, executable names, or game classes.
- Auto-open only on reliable evidence; preserve manual recovery.
- Reset held buttons, modifiers, pointer, and routing state at mode/lifecycle transitions.

Quest acceptance covers Steam credentials, TOTP, launchers, setup/error dialogs, Enter, Tab, Shift+Tab, arrows, Escape, Backspace, and pointer clicks using only the right controller.

Exit gate: pre-game flows require no ADB/scrcpy input, keyboard, mouse, or left controller; diagnostics contain no credentials.

### Phase 6 — qualify flat 3DoF launches

- Default every unqualified game to `FLAT_3DOF`.
- Render on the existing XR surface.
- Apply HMD orientation to presentation only; do not inject positional head movement into the guest.
- Keep pointer/navigation available until explicit game-input mode.
- Record first-window time, lifetime, display/frame state, and terminal outcome.

The Quest 2 matrix covers x86, x64, mixed WoW64 when available, clean exit, guest crash, process-start black screen, first-window hang, missing executable, and missing component.

Exit gate: physical Quest 2 x86 and x64 titles reach usable first windows and every failure yields a correlated bundle.

### Phase 7 — diagnostics and test infrastructure

- Start diagnostics before install resolution.
- Correlate app, XR process, backend, environment, guest output, X-server windows, and termination by launch ID.
- Add redacted plan summaries, Android exit reasons, and tombstone references.
- Provide one host-side collection command for manifest, structured logs, relevant logcat, and Operator observations.
- Keep Operator payloads confined to `modernXrDebug`.

Investigate `GOGDownloadManagerTest.setUp` with a focused timeout and evidence. Fix only demonstrated lifecycle/test-infrastructure defects; do not alter production download behavior solely to force completion.

Exit gate: the full unit suite terminates and passes, forced launch failures produce complete redacted bundles, and release verification excludes Operator artifacts.

### Phase 8 — recover native XR source and provenance

Blocked until all evidence exists:

- exact source repository/revision for the tracked `libxr.so`;
- redistribution-compatible license record;
- reproducible NDK/CMake/Ninja build;
- JNI symbol parity;
- Quest 2 baseline behavior parity; and
- documented rollback to the known binary.

Do not alter JNI, tracking, passthrough, frame timing, or swapchain behavior before this gate passes.

### Phase 9 — guest native VR runtime bridge

After Phase 8, define a version-negotiated host/guest protocol, architecture-matched Windows OpenXR runtime pieces, an approved OpenComposite-compatible OpenVR adapter, 6DoF poses/actions/haptics/lifecycle/frame submission/passthrough transport, legacy UDP compatibility, and safe flat fallback.

Exit gate: synthetic x86/x64 OpenXR and OpenVR references pass on Quest 2; runtime failures classify or fall back safely.

### Phase 10 — manifest-driven external VR hooks

After the runtime bridge, extend the existing mod materializer with a versioned manifest covering exact app/executable matching, architecture and hashes, runtime protocol, files, environment, DLL overrides, hook strategy, handshake, and rollback. Validate completely before mutation and prove the path with a synthetic fixture before title profiles.

Exit gate: synthetic activation, handshake, launch, cleanup, and rollback pass; real games require profile/manifest data and mod-side adaptation, not hard-coded launcher changes.

### Phase 11 — Quest 3 and release qualification

When physical hardware is available, capture the descriptor, validate components, and repeat the x86/x64, flat/native/modded, navigation, failure, and lifecycle matrix before promotion.

Final release requires no Operator library/manifest/service/callback/debug payload, no credential leakage, no DRM or anti-cheat bypass, deterministic cleanup/rollback, and a documented failure taxonomy.

## Settings precedence

Resolve and log every field source in this order:

1. GameNative execution baseline;
2. detected Quest hardware profile;
3. exact executable/game compatibility profile;
4. active external VR-mod requirement; and
5. explicit per-game user override.

A conflict that cannot safely honor a later source must fail visibly; it must never silently rewrite persisted state.

## Required validation workflow

Every phase begins by confirming the Rider project, branch, commit, and cleanliness. Rider MCP is preferred for file/symbol discovery, usages and call hierarchy, inspections, refactors, formatting, focused configurations, and affected-scope review.

Until Rider loads the Android/Gradle model correctly, every handoff must list:

- Rider tools/actions used;
- Rider inspection results;
- actions unavailable because only `Miscellaneous Files` is exposed;
- shell fallbacks; and
- Rider-assisted checks still pending.

Authoritative shell gates:

```powershell
git status --short --branch
git diff --check
.\gradlew.bat :app:testModernXrDebugUnitTest
.\gradlew.bat :app:assembleModernXrDebug
powershell -ExecutionPolicy Bypass -File .\tools\verify-quest-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\modernXr\debug\app-modernXr-debug.apk `
  -OperatorLayerPolicy Require
.\gradlew.bat :app:assembleModernXrRelease
powershell -ExecutionPolicy Bypass -File .\tools\verify-quest-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\modernXr\release\app-modernXr-release.apk `
  -OperatorLayerPolicy Forbid
```

Device tests require an explicit ADB serial. Meta XR Operator/Quest MCP is restricted to device and OpenXR observation, display-state inspection, debug correlation, and repeatable smoke-test execution. It is never a production input, runtime dependency, or release component.

## Assumptions and non-negotiable defaults

- Existing GameNative environment setup remains authoritative until incremental extraction is validated.
- Quest 2 is the only qualified device.
- The right controller alone must complete every pre-game flow.
- `FLAT_3DOF` is the mandatory safe default.
- Native/modded 6DoF is selected only after a validated handshake.
- Hardware profiles contain compatibility settings only, never performance tuning.
- Steam authentication and supported DRM use legitimate Steam/runtime paths.
- Title-specific fixes require a generic boundary, failure model, tests, and rollback before entering shared architecture.

## Change-control and handoff

Each phase records its before-state and boundary, Rider usage/results, focused tests, authoritative shell validation, required Quest evidence, changed files, rollback instructions, and whether the exit gate passed or remains blocked. Hardware- or source-gated phases stay explicitly blocked rather than being simulated.
