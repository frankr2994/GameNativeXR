# GameNativeXR Dependency Baseline (July 2026)

This is a source-verified inventory of the packages currently selected by the application. It does not qualify a package for Quest hardware, and it does not change any runtime default.

| Component | Selectable / available versions | Committed package coverage | Effective default | Evidence |
| :--- | :--- | :--- | :--- | :--- |
| FEX-Emu | 2507, 2508, 2511, 2512, 2601, 2603, 2604, 2605 | Main assets include fexcore-2507 through fexcore-2605 | 2605 | DefaultVersion.FEXCORE and fexcore_version_entries |
| DXVK | async-1.10.3, 1.9.2, 1.10.1, 1.10.3, 1.10.9-sarek, 1.11.1-sarek, 2.4.1, 2.4.1-gplasync, 2.6.1-gplasync, 2.7.1 | Main assets include 1.11.1-sarek, async-1.10.3, 2.4.1-gplasync, and 2.6.1-gplasync; legacy assets include the remaining selections | Code baseline 2.6.1-gplasync; ContainerUtils applies device-specific overrides | dxwrapper_download.json, assets, DefaultVersion, ContainerUtils |
| VKD3D-Proton | 2.6, 2.12, 2.13, 2.14.1, 3.0b | Main assets include 2.14.1; legacy assets include the other selections | 2.14.1 | dxwrapper_download.json, assets, DefaultVersion.VKD3D |
| Turnip | 24.1.0, 25.0.0, 25.1.0, 25.2.0, 25.3.0 | Legacy assets include all five selections; wrapper-driver candidates are separately listed | 25.2.0 | graphics_driver_download.json, assets, DefaultVersion.TURNIP |
| Proton / Wine | Runtime content includes Proton 9 fallback patterns; the modern Bionic path selects Proton 10 ARM64EC | Proton 9 container-pattern archives are committed; Proton 10 is obtained by the runtime content flow | proton-10.0-arm64ec-2 for current Bionic paths | ContainerUtils.kt, container_files_download.json, BionicSteamAssetsDependency.kt |
| Box64 | UI arrays expose glibc, Bionic, and WoW64 choices | Main assets include the listed Box64 Bionic/glibc packages and WoW64 packages | Code baseline 0.4.2; UI labels preserve legacy per-variant defaults | DefaultVersion.BOX64, arrays.xml, assets |
| JavaSteam | 1.8.0.1-24-SNAPSHOT | Build dependency, not an asset archive | 1.8.0.1-24-SNAPSHOT | Gradle dependency declaration |

app/src/main/assets/runtime-component-provenance.json stores SHA-256 values for the specifically listed local archives. A local hash establishes repository integrity only; it is not an upstream release attestation.

## Verification

Run the authoritative offline check from the repository root:

    .\tools\verify-runtime-components.ps1

The check validates component-specific selectors, manifests, both main and legacy asset trees, archive readability, required runtime members, and recorded local hashes. It exits non-zero for an error.
