# Halo 3 on Standalone Quest: Engineering Roadmap

Status: G0 build baselines complete; Phase 1 flat-game feasibility next; Phase 2 desktop protocol/simulator probes are runnable and physical Quest validation remains
Target branch: `Dev`
First game: Steam Halo: The Master Chief Collection, Halo 3 campaign
Available development device: Meta Quest 2
Additional validation target: Meta Quest 3 when hardware is available

Current evidence snapshot (2026-07-28): GameNativeXR `Dev` builds a debug APK
from source using JDK 17, Android SDK 35, and NDK `22.1.7171670`. Because the
historic JavaSteam snapshot artifacts are not reliably available, the build
uses locally built artifacts from the `joshuatam/JavaSteam` `gamenative-latest`
branch by default; this is documented in the repository README and committed
in `67d5e742`. Halo-MCC-VR commit `ba1407ae5e0fee09f16fa8b52e3c2f2740344ba6`
also configured, built the Release x64 DLL/launcher, and passed its core test
on this machine with CMake 4.1.2, MSVC 14.31.31103, and the repository-pinned
OpenXR-SDK, MinHook, and Dear ImGui revisions. Use
`cmake --build --preset release --parallel 1` here: the default parallel
aggregate build reported a silent MSBuild project-reference failure, while the
single-job aggregate build succeeded. Physical Quest 2 runs remain G1 evidence;
the supported PCVR runtime reference has not been revalidated in this session.

## 1. Objective

Run the user's legitimately owned Steam copy of Halo 3 from MCC directly on a
standalone Quest headset, without a gaming PC or streaming, while preserving the
existing Halo-MCC-VR PCVR path.

The first deliverable is a repeatable Halo 3 vertical slice. The long-term
deliverable is a reusable compatibility platform for other Windows games that
already have PCVR mods. "Full Steam library" is a direction, not a literal
compatibility guarantee: games vary by CPU features, graphics API, middleware,
anti-cheat, DRM, launcher, memory, and mod architecture.

This project will not:

- distribute MCC, Steam credentials, proprietary Halo data, or patched game
  binaries;
- bypass ownership checks, DRM, or anti-cheat;
- make online anti-cheat modes an initial target;
- treat simulator success, a successful build, or menu rendering as proof of
  headset playability.

## 2. Evidence from the Current Repositories

### GameNativeXR

- GameNativeXR already starts Windows software through Wine with Box64 or FEX
  and contains Steam integration paths.
- Its XR host is an Android OpenXR activity. `XrRenderer` can submit a flat
  window, side-by-side (SBS) stereo, or alternate-eye rendering (AER).
- The host already sends headset and controller state to a guest through an XR
  protocol with versions 0.1 through 0.4. It receives VR/3D mode, FOV, and
  haptic state from the guest.
- The native host library, `libxr.so`, owns the OpenXR instance, session,
  swapchains, poses, timing, haptics, and OpenGL ES submission. Only its ARM
  binary is present; its source is not in this repository.

### Halo-MCC-VR

- The mod is an injected Windows x64 DLL and currently creates its own Win32
  OpenXR session through `XR_KHR_D3D11_enable`.
- It already hooks MCC's D3D11 rendering and Halo 3 camera/input behavior.
- It already has headset-confirmed Halo 3 behavior that must remain the PCVR
  reference.
- The launcher starts MCC without anti-cheat, sets Steam app ID `976730`, and
  injects the DLL with `CreateRemoteThread` and `LoadLibraryW`.

### Consequence

A Windows D3D11 OpenXR session cannot be passed directly to Quest's Android
OpenXR runtime. The lowest-risk first architecture is therefore a host/guest
bridge:

```text
Quest OpenXR runtime
        |
Android GameNativeXR XR host
  - OpenXR session and compositor submission
  - Quest poses, controls, haptics, predicted timing
        |
        | versioned XR bridge
        v
Wine + Box64 guest
  Halo-MCC-VR GameNative backend
  - Halo camera and input hooks
  - stereo render control
        |
        v
MCC/Halo 3 D3D11 -> DXVK/Vulkan -> GameNativeXR display surface
        |
        +---------------------------> SBS or AER eye images to XR host
```

The existing SteamVR/OpenXR backend remains intact for regression testing.

### Verified bridge facts and evidence limits

The current Android host/guest bridge uses ASCII UDP packets. Guest-to-host
state arrives on UDP port `7278`; host-to-guest state uses `7872`, with
protocol 0.4 also using `7873`. The version/system files and debug override
path are documented in [XR_BRIDGE_PROTOCOL.md](XR_BRIDGE_PROTOCOL.md).
Protocol 0.4 carries headset and controller pose, thumbstick, button, mode,
FOV, IPD, sync, and haptic fields. Antigravity's deterministic vector corpus
is available under the workspace handoff directory. It is a proposed contract
for a new guest parser/serializer; the current Android host only parses
guest-to-host packets. Exact position/quaternion units, handedness, and some
ranges still require a physical Quest acceptance run.

The native host implementation is still only the prebuilt ARM64
`libxr.so`; its source and provenance are unresolved. The installed Meta XR
Simulator is a Windows OpenXR runtime, so it cannot install or execute the
Android GameNativeXR APK directly. It can validate an independent Windows
OpenXR/D3D11 visual harness, but it cannot prove the Android host's XServer
frame-sync/image path. APK lifecycle, performance, thermals, and headset
acceptance require the physical Quest 2.

### Batch 2 handoff review

The five Antigravity Batch 2 handoffs are complete and reviewed. Their
artifacts remain in `F:\QuestVR\ANTIGRAVITY_HANDOFFS\BATCH-02-*`:

- The `libxr.so` inventory confirms the recorded SHA-256 and identifies the
  historical `winlatorxr_cats_11` attribution, OpenXR-related strings, and a
  missing source/license trail. This is a release and redistribution blocker,
  but the handoff's copyleft conclusion is a risk requiring exact upstream
  license/source confirmation, not a final legal determination.
- The Meta XR Simulator matrix confirms the useful boundary: Windows
  OpenXR/D3D11 presentation and protocol mocks are testable there; Android
  JNI, Quest haptics, device timing, and Wi-Fi behavior are not.
- The Halo seam map identifies `src/dll/vr.h` as the likely future backend
  boundary. It is design input only; the PCVR backend remains untouched until
  the roadmap gates authorize refactoring.
- The GameNative profile draft is non-binding. MCC AppID, executable path,
  launch arguments, save paths, and performance values still require evidence
  from the user's owned installation and Quest runs.
- The Quest 2 runbook is an operator draft. Its suggested ADB commands,
  refresh-rate criteria, network thresholds, and storage mappings require
  Codex review before execution.

## 3. Program Gates

Work must stop at a failed gate until the failure is understood. Later XR work
must not hide a basic game-emulation failure.

| Gate | Required proof | Stop condition |
|---|---|---|
| G0: Reproducible baselines | GameNativeXR APK and Halo-MCC-VR Windows build are reproducible from pinned commits | Missing source, toolchain, or undocumented binary prevents reproduction |
| G1: Flat Halo 3 | The available Quest 2 launches owned MCC/Halo 3 without anti-cheat and completes 30 minutes of campaign in a flat window | OOM, unsupported instruction, DRM/auth failure, or unusable sustained frame rate; a measured Quest 2 hardware ceiling requires Quest 3 access before work can resume |
| G2: XR bridge probe | A protocol mock validates UDP parsing/serialization, and an independent Windows x64 OpenXR/D3D11 harness validates simulator presentation; physical Quest then validates the Android host/image path | Protocol is unstable, host/image-path behavior differs, or eye images require CPU readback |
| G3: Halo stereo | Halo 3 renders geometrically correct left/right eyes in-headset while the PCVR backend still works | Per-eye hooks fail under Wine/Box64 or image transport is too expensive |
| G4: Playable controls | Campaign, menus, vehicles, weapons, recenter, pause, and haptics are usable | Input latency or coordinate conversion makes sustained play impractical |
| G5: Quest 2 timing and stability | Correct predicted pose/timing, no systematic eye reversal or judder, and a 45-minute Quest 2 thermal soak | Native XR host cannot be source-owned or timing cannot be made deterministic |
| G6: Quest 3 validation | A measured physical Quest 3 run confirms compatibility and establishes its quality/performance preset | Quest 3 support remains unclaimed until hardware is available |
| G7: Release candidate | Clean install, owned-game setup, rollback, diagnostics bundle, notices, and physical-device acceptance | Game files/DRM are modified or release cannot be reproduced |

## 4. AI Model Policy

Use the cheapest model that can reliably complete a bounded task. Escalate only
after a failed attempt or when the task crosses multiple runtime boundaries.

### Codex CLI

| Label | Model and reasoning | Use |
|---|---|---|
| C-L | GPT-5.6 Luna, low | Mechanical extraction, formatting, deterministic transformations |
| C-M | GPT-5.6 Terra, medium | Normal implementation, build fixes, tests, integration in one subsystem |
| C-H | GPT-5.6 Sol, high | Cross-repository design, difficult debugging, OpenXR lifecycle, JNI, Wine/Box64, frame timing |
| C-XH | GPT-5.6 Sol, extra high | Rare escalation for unresolved concurrency, frame-pacing, or reverse-engineering problems |

Codex is the integrator because it owns the working tree, build/test loop, Git
history, and pushes to `Dev`.

### Antigravity CLI

| Label | Model and reasoning | Use |
|---|---|---|
| AG-FL | Gemini 3.6 Flash, low | Inventory, log classification, checklists, test reports, repetitive documentation |
| AG-FM | Gemini 3.6 Flash, medium | Bounded tests, serializers, parsers, Gradle/CMake cleanup, simple UI work |
| AG-FH | Gemini 3.6 Flash, high | Isolated utilities or probe implementations with a frozen interface |
| AG-PL | Gemini 3.1 Pro, low | Focused code review, design-doc review, license/dependency review |
| AG-PH | Gemini 3.1 Pro, high | Independent architecture critique, graphics/timing analysis, difficult postmortems |

Antigravity should usually produce a report, patch, or isolated commit. It
should not integrate directly into `Dev`.

### Parallel-work rules

1. Codex owns `Dev` and final integration.
2. Each Antigravity coding task gets a separate worktree and branch named
   `agent/ag-<task>`.
3. Two agents never edit the same files concurrently.
4. Every handoff names the base commit, allowed files, invariants, build/test
   commands, and definition of done.
5. Read-heavy inventory, log analysis, and test execution may run in parallel.
   Cross-cutting writes are serialized.
6. Codex reviews diffs and reruns acceptance checks before cherry-picking.
7. High-reasoning models consume summarized evidence, not unfiltered logs.

## 5. Execution Roadmap

### Phase 0 — Reproducible Baselines and Governance

Goal: establish known-good PCVR and Android baselines before changing behavior.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Record commit, submodule, binary, SDK, NDK, JDK, CMake, compiler, Wine, Box64/FEX, DXVK, and device versions | Antigravity | AG-FL | **Complete:** environment manifest and setup checklist; use Codex's successful-build logs as authoritative where tool-version snapshots differ |
| Add project-specific agent/build guidance without overriding Halo-MCC-VR's existing safety rules | Codex | C-M | Concise `AGENTS.md`/build documentation |
| Build the fork's unmodified `Dev` APK | Codex | C-M | **Complete:** source-accountable debug APK; local GameNative JavaSteam fallback documented in `67d5e742` |
| Verify APK identity and prepare an explicit Quest deployment path | Codex | C-M | **Complete:** `tools/verify-quest-apk.ps1` verifies package, ABI, Quest manifest entries, signing, hash, and optional ADB install/launch |
| Build unmodified Halo-MCC-VR on Windows x64 | Codex + Rider | C-M | **Complete:** commit `ba1407a`; `cmake --preset release`, `cmake --build --preset release --parallel 1`, and `ctest --preset release` pass |
| Verify Halo 3 PCVR behavior on the existing supported PC path | User + Codex | C-H for failures only | Reference logs/config and headset acceptance notes |
| Audit licenses and provenance of packaged native binaries, especially `libxr.so` | Antigravity review, Codex decision | AG-PL / C-H | **Evidence inventory complete:** SHA-256 and historical attribution recorded; exact source/license confirmation remains a release blocker |

Gate: G0.

### Phase 1 — Flat Halo 3 Feasibility

Goal: prove that MCC/Halo 3 itself is viable on Quest before doing XR
integration.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Create a Halo 3-only GameNative profile with conservative memory, graphics, and CPU settings | Codex | C-H | **Prepared:** non-binding schema/inventory in the Batch 2 handoff; production values wait for owned MCC install evidence |
| Install only user-owned MCC components required for Halo 3 and launch without anti-cheat | User provides files/auth; Codex automates profile | C-M | Repeatable launch recipe |
| Test the existing launcher/injection path under Wine/Box64 | Codex | C-H | Capability matrix for process creation, remote injection, hooks, and Steam presence |
| If remote injection fails, test Wine-supported DLL loading or a proxy/bootstrap DLL | Codex | C-H | Reversible injection path preserving PCVR launcher |
| Collect RSS/PSS, GPU, CPU, thermal, frame-time, Wine, Box64, DXVK, and logcat data | Antigravity parses; Codex diagnoses | AG-FL / C-H | One compact run report per experiment |
| Compare Box64 and FEX only after a stable Box64 baseline | Antigravity report; Codex experiment | AG-FL / C-M | Evidence-based translator choice |

Acceptance:

- Quest 2 reaches a Halo 3 campaign mission, saves/loads, and remains stable for
  30 minutes in a flat GameNativeXR window.
- The run has enough memory and GPU margin to justify adding two-eye rendering.
- A failure conclusively caused by Quest 2's hardware ceiling pauses this gate
  until a physical Quest 3 is available; it does not disprove the architecture.

Gate: G1.

### Phase 2 — Standalone XR Bridge Probe

Goal: validate the existing GameNativeXR XR protocol and zero-copy stereo path
without MCC, Steam, game hooks, or signature scanning.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Write a normative version 0.4 protocol specification from host code and packet captures | Codex | C-H | **Initial specification complete:** `docs/XR_BRIDGE_PROTOCOL.md`; units/handedness remain measured-validation items |
| Create golden packet vectors and parser/serializer tests | Antigravity | AG-FM | **Design complete:** corpus in `ANTIGRAVITY_HANDOFFS/TASK-01-protocol-vectors`; Codex must integrate strict tests before it is treated as executable proof |
| Implement a protocol-only mock host and x64 guest parser/serializer | Codex | C-M | **Complete:** `tools/xr-bridge-protocol` has a strict parser/serializer, vector-backed CTest coverage, and a loopback UDP fixture; malformed packets are rejected atomically |
| Implement an independent x64 Windows OpenXR/D3D11 visual harness | Codex | C-H | **Complete:** `tools/xr-visual-harness` renders deterministic stereo, SBS, and AER diagnostic patterns through a two-view D3D11 OpenXR swapchain |
| Add host diagnostics for session state, frame ID, eye selection, packets, and dropped frames | Codex | C-M | Structured, rate-limited logs, after a source-owned host seam exists |
| Exercise the visual harness in Meta XR Simulator | Codex | C-H | **API/frame-loop scope complete:** Meta XR Simulator 205 passed stereo 120-frame and SBS/AER 30-frame runs using process-local `XR_RUNTIME_JSON`; captured/human visual acceptance and Android transport validation remain |
| Confirm physical-device eye order, scale, FOV, pose direction, controls, and haptics | User + Codex | C-H for failures | Quest 2 acceptance capture, repeated later on Quest 3 |
| Prove the Android-host image path avoids CPU readback | Codex + graphics profiler | C-H | Physical-device GPU trace and frame-time evidence |

Start with SBS for correctness. Evaluate AER after SBS works; AER may reduce
peak surface requirements but sacrifices simultaneous eye frames and complicates
timing.

Gate: G2.

### Phase 3 — Decouple Halo-MCC-VR from Its OpenXR Runtime

Goal: preserve current PCVR behavior while allowing GameNativeXR to provide XR
state.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Define an internal backend contract for lifecycle, frame timing, views, actions, haptics, and eye targets | Codex | C-H | Architecture decision and interface |
| Independently review the seam against OpenXR ordering and Halo timing invariants | Antigravity | AG-PH | **Complete as design input:** Batch 2 seam map identifies `src/dll/vr.h`; refactor remains gated on G1/G2 evidence |
| Move current OpenXR behavior behind `SteamOpenXRBackend` with no functional change | Codex + Rider | C-H | PCVR regression-safe refactor |
| Implement `GameNativeXrBackend` using the proven bridge library | Codex | C-H | Runtime-selectable backend |
| Keep Halo 3 as the only standalone target while preserving dormant ODST/Reach code | Codex | C-M | Build/config isolation, no destructive cleanup |
| Add math, packet, state-machine, and backend-selection tests | Antigravity writes bounded tests; Codex integrates | AG-FM / C-M | Automated regression suite |

Required invariants:

- The existing PCVR backend remains the behavioral reference.
- Failure in optional input/IK/UI behavior cannot tear down a frame or session.
- No guessed or copied Halo offsets are introduced.
- Existing Halo-MCC-VR prohibited hook/patch rules remain in force.

### Phase 4 — Halo Stereo Image Transport

Goal: render correct Halo 3 eye images into a surface that GameNativeXR can
submit.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Map the mod's per-eye D3D11 flow onto one SBS output surface | Codex | C-H | First correct stereo image |
| Emit GameNativeXR frame-sync and eye/mode markers without breaking normal mirroring | Codex | C-H | Deterministic host eye selection |
| Add deterministic stereo grids, depth markers, and frame counters | Antigravity | AG-FH | Visual validation mode |
| Profile D3D11 → DXVK/Vulkan → display surface → GLES/OpenXR composition | Codex | C-H | Per-stage timing and copy analysis |
| Review Vulkan external-memory/direct-image alternatives only if measured copies are unacceptable | Antigravity analysis; Codex decision | AG-PH / C-XH | Evidence-based fallback design |
| Add AER as an optional low-memory experiment | Codex | C-H | Configurable AER path with comparison report |

Acceptance:

- world scale, IPD, FOV, eye order, depth, menus, cutscenes, scopes, and
  first-person models are visually correct on Quest 2;
- there is no per-frame CPU readback;
- PCVR remains functional.

Gate: G3.

### Phase 5 — Quest Input, Tracking, and Haptics

Goal: make Halo 3 campaign fully operable with Quest controllers.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Specify Quest-to-mod coordinate systems, units, handedness, dead zones, and button semantics | Codex | C-H | Versioned mapping specification |
| Implement mapping tables and golden tests after the specification freezes | Antigravity | AG-FH | Tested input translation module |
| Feed headset/controller state into existing Halo camera, weapon, IK, and `VrPadState` behavior | Codex + Rider | C-H | Playable controls |
| Return haptic commands to the Android host | Codex | C-M | Left/right haptic feedback |
| Add recenter, pause, menu navigation, seated/standing origin, controller loss, and focus handling | Codex | C-H | Complete lifecycle behavior |
| Run scripted input traces and summarize failures | Antigravity | AG-FM | Reproducible trace reports |

Gate: G4.

### Phase 6 — Source-owned XR Host and Protocol 0.5

Goal: remove the opaque native-host dependency and expose the timing required
for a polished direct-headset experience.

This phase may start earlier if `libxr.so` provenance or behavior blocks G0–G3.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Obtain the matching `libxr.so` source from an upstream project, or decide to replace it | User outreach + Codex audit | C-H | Documented source/provenance decision |
| Build a source-owned Android OpenXR host using the Khronos loader and Meta-supported Android lifecycle | Codex | C-H | Reproducible ARM64 native library |
| Preserve flat, SBS, AER, controller, haptic, passthrough, refresh, and performance-level behavior | Codex | C-H | Compatibility tests against current host |
| Design protocol 0.5 with capabilities, explicit sizes, sequence IDs, monotonic timestamps, predicted display time, per-eye views/FOV, refresh rate, focus/session state, and error counters | Codex with independent review | C-H / AG-PH | Normative protocol specification |
| Implement version negotiation and 0.4 compatibility | Codex | C-H | Backward-compatible host and guest |
| Evaluate shared memory or Unix-domain transport only after profiling UDP | Antigravity analysis; Codex implementation if justified | AG-PH / C-XH | Measured transport decision |
| Add fuzz, malformed-packet, disconnect, restart, and sequence-wrap tests | Antigravity | AG-FH | Resilience suite |

Gate: the source-owned host matches current behavior and protocol 0.5 improves
measured prediction/frame alignment without regressing 0.4.

### Phase 7 — Performance, Memory, and Thermals

Goal: reach a stable, documented operating envelope.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Automate collection and normalization of frame-time, memory, thermal, clock, translator, DXVK, and XR metrics | Antigravity | AG-FM | Benchmark bundle and compact reports |
| Establish Quest 2 presets for resolution, refresh, graphics, CPU affinity, translator, and background services | Codex | C-H | Quality/performance presets |
| Optimize only the largest measured bottleneck per experiment | Codex | C-H; C-XH only after repeated failure | Reviewable, evidence-backed changes |
| Use Pro-high for independent postmortems at major plateaus, not routine runs | Antigravity | AG-PH | Ranked hypotheses tied to traces |
| Run 45-minute campaign and thermal-soak scenarios | User/device automation; Antigravity summarizes | AG-FL | Stability report |
| Repeat the full matrix on Quest 3 when hardware becomes available | Codex | C-H | Quest 3 preset and support decision |

Quest 2 is the immediate development and stress-test target because it is the
available physical device. Quest 3 must still be tested before the project
claims Quest 3 support; simulator results cannot substitute for its performance,
thermal, or device-lifecycle validation.

Gates: G5 and G6.

### Phase 8 — Product Integration

Goal: turn the prototype into a reversible GameNativeXR workflow.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Add an MCC app profile that detects app ID/version and exposes Halo 3 XR settings | Codex | C-M | Game profile |
| Build isolated Compose UI/settings components after schemas freeze | Antigravity | AG-FM | Reviewable UI commit |
| Add mod package validation, hashes, version compatibility, install/update/rollback, and backup behavior | Codex | C-H | Safe mod manager using user-provided package |
| Add an anti-cheat-off launch action and clear user warnings | Codex | C-M | Explicit, compliant launch flow |
| Add one-click diagnostics export with redaction | Antigravity utility; Codex security review | AG-FH / C-H | Support bundle |
| Keep generic XR bridge settings separate from Halo-specific defaults | Codex | C-H | Reusable architecture for later games |

### Phase 9 — QA and Release

Goal: produce a reproducible Halo 3 standalone release candidate.

Test matrix:

- Quest 2 physical device: required for the initial vertical slice;
- Quest 3 physical device: required before claiming Quest 3 support;
- Meta XR Simulator: deterministic automation only;
- PC OpenXR/SteamVR: regression reference;
- clean install, update, rollback, app restart, headset sleep/wake;
- main menu, campaign load, cutscenes, checkpoints, death/reload, vehicles,
  turrets, dual-wielding, scopes, grenades, pause, saves, and level transitions;
- controller disconnect/reconnect, tracking loss, recenter, boundary/focus
  changes;
- 45-minute thermal run and low-storage/low-memory behavior.

| Task | Owner | Model | Deliverable |
|---|---|---|---|
| Maintain deterministic test cases and classify results | Antigravity | AG-FL | Test dashboard/report |
| Diagnose and fix failures one evidence-backed behavior at a time | Codex | C-M or C-H | Focused commits |
| Review licenses, notices, source-offer obligations, and bundled binary provenance | Antigravity review; Codex final decision | AG-PL / C-H | Release compliance checklist |
| Rebuild from a clean checkout and create signed artifacts | Codex | C-M | Reproducible release candidate |
| Perform final physical-headset acceptance | User | N/A | G7 sign-off |

Gate: G7.

### Phase 10 — Generalize Beyond Halo 3

Only begin after G7.

1. Extract a documented GameNativeXR guest SDK:
   lifecycle, timing, views, input, haptics, SBS/AER, diagnostics, and examples.
2. Separate reusable runtime code from Halo-specific hooks.
3. Classify candidate games by mod architecture:
   OpenXR/OpenVR API, D3D11/D3D12/Vulkan, injection mechanism, anti-cheat/DRM,
   memory, and CPU requirements.
4. Prefer games whose mods already control per-eye rendering and camera state.
5. Add one game at a time with its own G1–G7 evidence.
6. Investigate a Windows OpenXR runtime shim only if multiple games cannot use
   the guest SDK and the measured benefit justifies its much higher complexity.

## 6. Fallback Decision Tree

| Failure | First response | Escalation |
|---|---|---|
| MCC cannot reach gameplay on Quest 2 | Diagnose CPU instruction, memory, Steam/auth, DXVK, or translator separately | Stop XR work until G1 passes on Quest 2 or evidence shows that Quest 3 hardware is required |
| `CreateRemoteThread` injection fails under Wine/Box64 | Use Wine DLL override, bootstrap/proxy DLL, or supported preload path | Use Halo tools only if module/version behavior also changed |
| Halo signatures fail | Record exact MCC/Halo DLL hashes and scan results | Request matching Halo tools/symbol evidence; never guess offsets |
| SBS requires CPU copies | Trace the graphics path and remove the copy | Prototype Vulkan external-memory sharing |
| AER causes judder or eye mismatch | Keep SBS as the correctness path | Use AER only as an explicit experimental preset |
| Current `libxr.so` cannot be sourced | Reimplement the required host behavior from official Android OpenXR samples | Do not ship a growing feature set on an unmaintainable opaque binary |
| UDP causes measurable stale poses/loss | Add sequence/timestamp telemetry | Move the data plane to shared memory while retaining a control socket |
| Quest 2 runs out of memory | Remove unnecessary MCC content/services and lower eye surface cost | If the limit is conclusively hardware-bound, pause G1 until Quest 3 can be tested |
| Steam client/auth is the limiting factor | Use supported GameNative Steam paths and legitimate login/offline behavior | Do not bypass ownership or DRM |

## 7. Tooling

### Required before Phase 1

- JDK 17.
- Android SDK platform/build tools 35.
- The NDK version pinned by GameNativeXR (`22.1.7171670`) installed side by
  side with a current NDK for new Meta/OpenXR native work.
- CMake 3.24+ and Ninja.
- Visual Studio 2022 Desktop C++ workload, current Windows SDK, C++20 tools, and
  FXC/D3D compiler components.
- ADB, USB debugging, and the available physical Quest 2.
- Access to a physical Quest 3 before claiming Quest 3 compatibility.
- Meta XR Simulator and Meta Quest developer tooling.
- Git LFS only if a later dependency actually requires it.

### Install before native-host or performance work

- Vulkan SDK and validation layers.
- LLVM binary tools (`llvm-readobj`, `llvm-objdump`, `llvm-symbolizer`) from the
  NDK or Visual Studio LLVM workload.
- Android GPU Inspector, Perfetto, and Meta's headset performance/metrics tools.
- DXVK debug layers/symbols and a way to retain Wine/Box64 crash logs.
- Rider with MCP for C++ call hierarchy, safe refactors, debugger integration,
  and test navigation. Rider is useful in Phases 0, 3, 4, and 5; it is not
  required for simple file edits.

### Request Halo tools when

- the exact MCC version changes signatures;
- injection succeeds but known Halo 3 hooks do not;
- a camera/render behavior cannot be established from the existing mod's
  signatures and headset-confirmed evidence;
- a Halo data-layout change requires authoritative validation.

Before requesting them, record the Steam build ID and SHA-256 hashes of the
relevant MCC/Halo modules.

## 8. Evidence and Commit Discipline

Each experiment must record:

- repository commits and dirty state;
- app/game/mod versions and binary hashes;
- device, OS, runtime, refresh, thermal, battery, and graphics settings;
- exact launch command and environment overrides, with secrets removed;
- expected result, observed result, and attached logs/traces;
- one conclusion and the next smallest experiment.

Each implementation commit should make one coherent behavior change. Generated
artifacts, MCC files, credentials, large traces, and personally identifying
device data must not enter Git.

## 9. Definition of the Halo 3 Vertical Slice

The first target is complete when:

1. A clean `Dev` checkout builds a source-accountable GameNativeXR APK.
2. The user can configure a legitimately owned Halo 3 installation without
   anti-cheat or modified game binaries.
3. Halo 3 launches directly on Quest 2 and reaches campaign gameplay.
4. Head and hand tracking, controls, menus, haptics, stereoscopic rendering,
   FOV, scale, and recenter behavior are usable.
5. The existing Halo-MCC-VR PCVR backend still passes its reference checks.
6. A 45-minute physical Quest 2 run has no crash, runaway memory, systematic
   eye mismatch, or undocumented thermal failure.
7. Install, update, rollback, logs, known limitations, licenses, and source
   instructions are documented.
8. Quest 3 is separately validated before Quest 3 support is claimed.

## 10. First Execution Package

Start in this order:

1. Complete the environment/provenance manifest and build both unmodified
   projects.
2. Verify the Halo-MCC-VR PCVR reference.
3. Attempt G1 on Quest 2 with a flat Halo 3 window.
4. Use the completed protocol corpus to implement a mock UDP fixture, then build
   the independent simulator visual harness; do not represent either as an
   Android image-path validation.
5. Do not refactor Halo-MCC-VR or replace `libxr.so` until G1 evidence exists,
   unless missing `libxr.so` source blocks a reproducible release baseline.

The first request to the user should be the owned MCC/Halo 3 install location,
Steam build ID, relevant executable/DLL hashes, Quest 2 connection, and the
output of the baseline toolchain manifest. Halo tools are not needed until a
specific hook or signature failure is observed.
