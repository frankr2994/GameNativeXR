# Native XR Provenance Audit

**Status:** BLOCKED — the tracked native XR bridge is identifiable, but not reproducible from this worktree.

**Audited binary:** `app/src/main/jniLibs/arm64-v8a/libxr.so`  
**Audit date:** 2026-08-03  
**Scope:** evidence only; no native source, CMake, JNI, tracking, passthrough, or frame behavior was changed.

## Evidence

| Item | Result |
|---|---|
| File size | 42,864 bytes |
| SHA-256 | `FDA0AD73C060E3230383C7C8C75EA1CB6899B4C24E59C1180DF18C19AFC03CE4` |
| Git blob ID | `0320cbbf11ccad99420409b53d5dfb8a44a2c76a` |
| Binary format | ELF64, little-endian, AArch64 shared object |
| SONAME | `libxr.so` |
| Direct dependencies | `libopenxr_loader.so`, `libEGL.so`, `libGLESv3.so`, `liblog.so`, `libm.so`, `libdl.so`, `libc.so` |
| Initial import history | `fd95d3d222798c09502526200f17d97d65866424` — `OpenXR - Basic hookup` |
| Latest tracked update | `8137e988e6f324ed3aab9406c6dbc5779f2aa2de` — `XR module from winlatorxr_cats_11` |

`llvm-readelf` confirms that the binary exports the JNI methods expected by
`com.winlator.xr.XrActivity`, including `init`, `initFrame`, `getAxes`,
`getButtons`, `nativeSetUsePT`, `nativeSetUseVR`, and `nativeSetFramesync`.
It imports OpenXR lifecycle calls such as `xrCreateInstance`, `xrWaitFrame`,
`xrBeginFrame`, and `xrEndFrame` from `libopenxr_loader.so`.

## What is present in this worktree

- `XrActivity.java` loads the prebuilt library and declares the Java/JNI ABI.
- `XrInterface.java` documents ABI-sensitive axis and button enumeration order.
- `app/src/main/cpp/CMakeLists.txt` builds `winlator` and `vulkan_renderer`; it
  does not define an `xr` library target.
- The repository contains the Khronos loader binary, but not the source or
  build recipe for this `libxr.so` bridge.

The workspace-wide native-source search found no C or C++ implementation of
the `Java_com_winlator_xr_XrActivity_*` bridge functions. The separate
`tools/xr-visual-harness` is intentionally a Windows OpenXR harness and its
own README explicitly excludes validation of Android `libxr.so` behavior.

## Gate decision

The source-provenance gate in `GameNativeXR_Architecture_Plan.md` remains
**blocked**. It is unsafe to alter native tracking, passthrough, frame timing,
or the JNI ABI until all of the following are available:

1. The exact source revision that produced the tracked binary, including the
   meaning of `winlatorxr_cats_11`.
2. A reproducible Android build recipe with the selected NDK and all required
   headers/libraries.
3. A license/provenance record suitable for redistribution of that source and
   its binary output.
4. A symbol and behavior-parity check against the known-good tracked binary.

Until that gate passes, retain the existing binary unchanged. Work that does
not change native behavior—such as launch contracts, diagnostics, controller
navigation, and manifest-only planning—can continue independently.
