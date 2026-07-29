# XR Visual Harness Validation

Date: 2026-07-28

## Scope

This record covers the Windows x64 `tools/xr-visual-harness` executable. It
does not cover the GameNativeXR Android APK, `libxr.so`, XServer surface
sampling, UDP transport, Quest hardware, or Halo-MCC-VR.

## Build baseline

- Generator: Visual Studio 17 2022, x64
- Compiler: MSVC 19.31.31107 / toolset 14.31.31103
- OpenXR headers/loader: OpenXR-SDK-Source 1.1.61 at
  `5267613edf3d937e3d77556a106a65c2f82b25c6`
- Configuration: Release

Commands:

```powershell
cmake -S tools\xr-visual-harness -B F:\QuestVR\_build\xr-visual-harness `
  -G "Visual Studio 17 2022" -A x64 `
  -DOPENXR_SDK_SOURCE_DIR=F:\QuestVR\Halo-MCC-VR\out\deps\openxr-src
cmake --build F:\QuestVR\_build\xr-visual-harness --config Release --parallel 1
ctest --test-dir F:\QuestVR\_build\xr-visual-harness -C Release --output-on-failure
```

Result: the executable and static Khronos loader built successfully; the
no-runtime CTest self-test passed.

## Meta XR Simulator runs

The runtime was selected only for each PowerShell process:

```powershell
$env:XR_RUNTIME_JSON='E:\Program Files\MetaXRSimulator\v205.0\meta_openxr_simulator.json'
```

Observed common configuration:

- runtime: Meta XR Simulator 205.0.0;
- adapter: NVIDIA GeForce RTX 2080 Ti;
- swapchain: 1440 x 1584, two array views, three images;
- format: DXGI 29 (`DXGI_FORMAT_R8G8B8A8_UNORM_SRGB`);
- normal session progression through READY, SYNCHRONIZED, VISIBLE, FOCUSED,
  STOPPING, IDLE, and EXITING.

| Pattern | Requested frames | Rendered frames | Process result |
|---|---:|---:|---|
| stereo | 120 | 120 | success |
| sbs diagnostic | 30 | 30 | success |
| aer diagnostic | 30 | 30 | success |

The SBS and AER patterns are diagnostic visualizations inside ordinary OpenXR
projection views. They do not implement or validate GameNativeXR's Android
SBS/AER image-selection behavior.

## Evidence boundary

The runs prove that the harness can create a D3D11 OpenXR session, select the
runtime-required adapter, create a two-view array swapchain, locate valid
views, acquire/render/release images, submit frames, and exit cleanly through
the Meta XR Simulator API path.

They do not yet prove that an operator saw the expected colors, crosshairs,
frame bits, eye order, or absence of visual artifacts. A captured simulator
image or witnessed visual acceptance run is still required for that claim.

## Post-upstream regression

Date: 2026-07-29

The harness was rebuilt and rerun from GameNativeXR `Dev-Update` commit
`bd4863dc` after the GameNative upstream merge and XR renderer-routing fix.
The pinned OpenXR-SDK-Source remained at
`5267613edf3d937e3d77556a106a65c2f82b25c6`.

The Release x64 build and `xr_visual_harness_self_test` passed. Meta XR
Simulator runs used the same process-local runtime manifest:

```powershell
$env:XR_RUNTIME_JSON='E:\Program Files\MetaXRSimulator\v205.0\meta_openxr_simulator.json'
```

| Pattern | Requested frames | Rendered frames | Process result |
|---|---:|---:|---|
| stereo | 120 | 120 | success |
| sbs diagnostic | 120 | 120 | success |
| aer diagnostic | 120 | 120 | success |

All three runs reported:

- Meta XR Simulator `205.0.0`;
- NVIDIA GeForce RTX 2080 Ti;
- a 1440 x 1584 swapchain with two array views and three images;
- DXGI format 29 (`DXGI_FORMAT_R8G8B8A8_UNORM_SRGB`);
- progression through IDLE, READY, SYNCHRONIZED, VISIBLE, FOCUSED, STOPPING,
  IDLE, and EXITING.

The harness must run outside the restricted Codex filesystem/process sandbox.
Inside that sandbox, the runtime reached READY but its log reported Windows
error 5 while opening the Meta XR Simulator frontend process, preventing
session synchronization. The same binary and runtime manifest completed
normally outside the sandbox.

The simulator logged an undestroyed reference space during its internal
shutdown. Its trace shows that the remaining space is a simulator-created VIEW
space; the harness-created LOCAL space is explicitly destroyed before
`xrDestroySession`. No harness cleanup change is indicated by this warning.

This regression closes the post-merge API/frame-loop requirement only. It does
not add operator visual acceptance and does not validate Android, Quest,
GameNativeXR SBS/AER sampling, or image transport.
