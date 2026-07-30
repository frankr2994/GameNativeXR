# Deferred Dependency Updates (July 2026)

The following dependency updates and architecture tasks have been explicitly deferred from the current dependency refresh cycle (`dependency-refresh-2026-07`). They will be addressed in future dedicated projects following XR hardware validation.

## 1. Stock Proton or Wine Container Replacement
Replacing the custom GameNative container pattern with stock Proton or Wine has been deferred. The current `proton-10.0-arm64ec-2` and bundled legacy containers provide specific Bionic compatibility and fallback behaviors required for GameNativeXR's isolated execution environment.

## 2. Turnip Validation and Default Migration
While newer Turnip drivers (e.g., `26.0.0_R8`) have been qualified as available candidates, device-specific validation across Quest hardware targets is required before migrating the global default away from the current baseline.

## 3. Generic Upstream Winlator Merges
A broad, unselective merge of upstream Winlator components is an architectural project, not a simple dependency update. Due to GameNativeXR's custom `XrRenderer` and `XServerViewGL` lifecycle integrations, upstream Winlator changes must be selectively audited and ported.

## 4. NDK Native Rebuilds (AdrenoTools, LSFG-VK)
Updating source submodules (`libadrenotools`, `lsfg-vk-android`) and performing native library rebuilds is deferred. These require the Android NDK 27.3.13750724 environment and will be managed in a separate native-toolchain qualification phase.
