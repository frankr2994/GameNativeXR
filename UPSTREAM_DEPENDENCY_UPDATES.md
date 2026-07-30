# GameNativeXR Upstream Dependencies & Update Analysis (Corrected)

This document catalogs the third-party dependencies used by GameNativeXR, identifying their status, distinguishing between source and shipped binaries, and noting what genuinely requires pulling over versus what is already present or deferred.

## 1. Emulation & Translation Cores

### FEX-Emu
* **Status:** Already integrated but FEX 2607 is missing.
* **Current Version Used:** `fexcore-2605` (Default) is shipped as a prebuilt binary containing custom Windows-facing DLLs.
* **Missing/Updates to Pull:** FEX 2607 is genuinely missing. It requires a reproducible GameNative-compatible packaging recipe because an upstream FEX archive cannot simply be renamed.

### Proton & Wine
* **Status:** Already integrated but not default for all paths.
* **Current Version Used:** 
  * `proton-10.0-arm64ec-2` is the actual default for modern Bionic containers.
  * Bundled Proton 9 container patterns are fallback/legacy assets.
* **Deferred:** Stock Proton or Wine container replacement is a deferred architecture project. Do not replace the custom container pattern with stock Proton.

### Box64 / Box86 & WoWBox64
* **Status:** Already integrated.
* **Current Version Used:** `box64-0.4.2` and `wowbox64-0.4.2` packages already exist and are used. UI defaults and per-container defaults are not identical.

---

## 2. Graphics Translation (DirectX & Vulkan)

### DXVK
* **Status:** Already integrated but not default.
* **Current Version Used:** DXVK 2.7.1 is already present, selectable, and downloadable. However, the default remains `2.6.1-gplasync` (or `2.4.1-gplasync` for Turnip) because DXVK 2.7+ requires Vulkan 1.3 and additional features.

### VKD3D-Proton
* **Status:** Already integrated but not default.
* **Current Version Used:** VKD3D-Proton 3.0b is already present in the integration layers. The default remains `2.14.1` because 3.0b is not needed for the first D3D11 target (Halo 3).

### Turnip / Mesa Drivers
* **Status:** Already integrated, device-dependent.
* **Current Version Used:** Turnip 26.0.0 R8 is already selectable and downloadable. It is not a universal Quest default. Drivers must be qualified per device/runtime path.
* **Deferred:** New Turnip build/default migration is a deferred project.

### Zink & VirGL
* **Status:** Already integrated.
* **Deferred:** Zink/VirGL/Mesa rebuild is deferred.

---

## 3. Platform & Framework Dependencies

### Winlator Core
* **Status:** Deferred architecture work.
* **Action:** A broad Winlator merge is an architecture project, not a dependency bump, and is excluded from the immediate refresh.

### JavaSteam / SteamBootstrap
* **Status:** Custom snapshot used.
* **Current Version Used:** `1.8.0.1-24-SNAPSHOT` (GameNative-specific or locally built).
* **Missing/Updates to Pull:** Update it only from the GameNative-compatible branch (`gamenative-latest`) and only with login/library/download regression evidence.

### Submodules: libadrenotools & lsfg-vk-android
* **Status:** Deferred native rebuilds.
* **Current State:** 
  * Source submodule pointers (`libadrenotools`, `lsfg-vk-android`) exist.
  * Shipped prebuilt binaries (like `liblsfg-vk-layer.so`) are distinct artifacts.
* **Deferred:** Updating the source pointers plus executing native rebuilds is deferred until NDK 27.3.13750724 is installed.
