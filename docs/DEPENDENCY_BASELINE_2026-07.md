# GameNativeXR Dependency Baseline (July 2026)

| Component | Selectable Versions | Bundled Versions | Downloadable Versions | Actual Default by GPU/Container Path | Package Source | SHA-256 | License | Upstream Tag/Commit | First Quest Target Impact |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FEX-Emu** | 2507, 2508, 2511, 2512, 2601, 2603, 2604, 2605 | fexcore-2507 to 2605 (all bundled) | None | 2605 (Global Default) | `app/src/main/assets/fexcore/fexcore-2605.tzst` | UNKNOWN | MIT | FEX-2605 | Yes (Required for x86/x64 execution) |
| **DXVK** | async-1.10.3, 1.10.1, 1.10.3, 1.10.9-sarek, 1.11.1-sarek, 1.9.2, 2.4.1, 2.4.1-gplasync, 2.6.1-gplasync, 2.7.1 | None | All selectable (via `dxwrapper_download.json`) | 2.6.1-gplasync (Standard), 2.4.1-gplasync (Turnip), 1.11.1-sarek (Adreno6xx) | GameNative CDN | UNKNOWN | zlib/libpng | v2.6.1 | Yes (D3D11 to Vulkan for Halo 3) |
| **VKD3D-Proton** | 2.6, 2.12, 2.13, 2.14.1, 3.0b | None | 2.14.1, 3.0b (via `dxwrapper_download.json`) | 2.14.1 (Global Default) | GameNative CDN | UNKNOWN | LGPL-2.1 | v2.14.1 | No (Halo 3 is D3D11) |
| **Turnip (Mesa)** | 24.1.0, 25.0.0, 25.1.0, 25.2.0, 25.3.0, 26.0.0_R4, 26.0.0_R8 | None | All selectable (via `graphics_driver_download.json`) | 25.2.0 (Global Default) | GameNative CDN | UNKNOWN | MIT | Mesa 24/25 | Yes (Vulkan driver for Quest Adreno) |
| **Proton/Wine** | proton-9.0-arm64ec, proton-9.0-x86_64, wine-9.2-x86_64 | None | proton-9.0, proton-10.0-arm64ec-2 (via container utils/downloads) | proton-10.0-arm64ec-2 (Turnip/Bionic Default) | GameNative CDN | UNKNOWN | LGPL-2.1 | Proton 10.0 | Yes (Core Windows API Translation) |
| **Box64** | 0.3.2, 0.3.6, 0.3.7, 0.3.8, 0.4.0, 0.4.2 | wowbox64-0.3.4 to 0.4.2 | None | 0.4.2 (Global Default) | `app/src/main/assets/wowbox64/wowbox64-0.4.2.tzst` | UNKNOWN | MIT | v0.4.2 | Yes (Alternative to FEX) |
| **JavaSteam** | 1.8.0.1-24-SNAPSHOT | Built locally / Maven dependency | None | 1.8.0.1-24-SNAPSHOT | `joshuatam/JavaSteam` branch `gamenative-latest` | UNKNOWN | MIT | `gamenative-latest` | Yes (Steam validation and launch) |

*Note: For SHA-256 hashes, refer to `app/src/main/assets/runtime-component-provenance.json`.*

### Usage: Runtime Verifier
To verify runtime component consistency, run:
```powershell
.\tools\verify-runtime-components.ps1
```
This script will parse `arrays.xml` and manifest JSONs, verifying that all selectable IDs are present in either bundled assets or download manifests, and validate archive integrity where possible without network access.
