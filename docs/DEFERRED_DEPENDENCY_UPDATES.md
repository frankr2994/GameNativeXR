# Deferred Dependency Updates (July 2026)

The following work is intentionally outside the dependency-refresh cycle. None of these items has been adopted as a runtime default.

## 1. Stock Proton or Wine container replacement

Replacing the GameNative container pattern with stock Proton or Wine requires a dedicated compatibility project. The current Bionic and legacy fallback paths remain unchanged.

## 2. Turnip validation and default migration

Newer Turnip packages are available as candidates, but have not been qualified on Quest hardware. Device-specific validation is required before any default change.

## 3. Generic upstream Winlator merges

A broad Winlator merge is architectural work. It must be selectively audited against the XR renderer and lifecycle integrations.

## 4. NDK native rebuilds

Updating libadrenotools or lsfg-vk-android and rebuilding native libraries requires the documented Android NDK environment and a separate native-toolchain qualification phase.
