# Windows OpenXR/D3D11 Visual Harness

This standalone Windows x64 utility validates ordinary OpenXR session,
swapchain, stereo-view, frame-loop, and D3D11 presentation behavior against a
selected runtime. It renders deterministic eye colors, crosshairs, and a
frame-counter marker without MCC, Wine, UDP, Android, or game hooks.

This harness does **not** validate GameNativeXR's Android `libxr.so`, XServer
surface sampling, frame-sync pixel contract, haptics, networking, performance,
or standalone Quest execution.

## Build

Use a Khronos OpenXR-SDK-Source checkout. The current workspace has a pinned
checkout used by the Halo-MCC-VR baseline:

```powershell
cmake -S . -B out -G "Visual Studio 17 2022" -A x64 `
  -DOPENXR_SDK_SOURCE_DIR=F:\QuestVR\Halo-MCC-VR\out\deps\openxr-src
cmake --build out --config Release --parallel 1
ctest --test-dir out -C Release --output-on-failure
```

The CMake project also accepts an installed `OpenXR::openxr_loader` package
when `OPENXR_SDK_SOURCE_DIR` is omitted.

## Process-local Meta XR Simulator run

PowerShell uses `XR_RUNTIME_JSON` for only the child process. It does not
activate the simulator globally or edit the registry:

```powershell
$env:XR_RUNTIME_JSON='E:\Program Files\MetaXRSimulator\v205.0\meta_openxr_simulator.json'
.\out\Release\xr_visual_harness.exe --frames 600 --pattern stereo
Remove-Item Env:XR_RUNTIME_JSON
```

Arguments:

- `--frames N`: request session exit after N rendered frames (default 600);
- `--pattern stereo`: blue left eye, red right eye, crosshair and frame bits;
- `--pattern sbs`: diagnostic side-by-side color halves in each OpenXR view;
- `--pattern aer`: diagnostic alternating-eye emphasis. This only visualizes
  the concept and does not exercise GameNativeXR AER sampling;
- `--self-test`: validate argument/pattern logic without creating OpenXR state.

The executable logs runtime/session transitions, selected adapter and format,
view validity, and rendered frame count to standard output.

The first simulator API/frame-loop results and their evidence boundary are
recorded in [XR_VISUAL_HARNESS_VALIDATION.md](../../docs/XR_VISUAL_HARNESS_VALIDATION.md).
