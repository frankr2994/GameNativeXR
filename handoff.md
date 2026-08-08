# GameNativeXR Architecture Update - Handoff

## Summary of Completed Work
The goal was to implement M4 (Controller Input & UI Binding) from the architecture plan. Specifically, we needed to route inputs properly depending on whether an Android UI dialog is open, we're in desktop mode, or we are running a VR game.

1. **PreGameInputRouter & RayPointerMapper:**
   - Implemented a Kotlin-based state machine (`PreGameInputRouterImpl`) with different modes (`ANDROID_OVERLAY`, `GUEST_POINTER`, `GUEST_TEXT`, `GAME_INPUT`).
   - Implemented the `RayPointerMapperImpl` to map OpenXR absolute controller rotations (yaw/pitch) into a 2D virtual screen surface, projecting it to XServer cursor bounds.

2. **Wiring into XrController.java:**
   - Replaced the hardcoded OpenXR-to-Android-KeyEvent bindings in `XrController.updateAndroidInput`.
   - Injected the `PreGameInputRouter` and defined a Mode Selection Policy that determines the current state:
     - `ANDROID_OVERLAY` if `XrContentDialog.getFrontInstance() != null`.
     - `GAME_INPUT` if `XrActivity.isVR` or `isImmersive`.
     - `GUEST_POINTER` otherwise.
   - Wired the XServer pointer to the absolute ray hit coordinates from `RayPointerMapper`.
   - Wired `PreGameInputRouter` output actions (`RoutedInputAction`) to execute XServer pointer clicks, Android KeyEvents, and UI navigation actions.
   - `updateAndroidInput` now returns a boolean indicating whether legacy XServer inputs in `XrActivity` should proceed (`FORWARD_TO_GAME_INPUT`).

3. **Wiring into XrActivity.java:**
   - Modified the `updateAndroidInput(buttons)` call to `updateAndroidInput(buttons, axes)` to allow the controller to receive rotational data for the ray mapper.

4. **Testing and Verification:**
   - Project successfully compiles (`./gradlew compileModernXrDebugJavaWithJavac`).

# Round 2 Execution & Results

## Rider Limitations & Handoff Clarification
- **Rider Status**: Rider MCP was loaded, but the project model only exposed "Miscellaneous Files" with unloadable configurations, as warned in the Round 2 corrections. Therefore, authoritative tests and builds were strictly executed via the shell using `./gradlew` rather than Rider's internal test runner.

## Corrections Applied
- **R2-01 (Pre-game controller availability)**: Extracted connection state into a `ControllerConnectionState` tri-state model. Removed the fabricated boolean connection signals. Implemented determinism for hand selection during unknown availability states. Tested in `PreGameInputRouterTest.kt`.
- **R2-02 (Explicit Guest UI Mode)**: Replaced the unreliable window-class heuristic (`progman`, `#32770`, etc.) with an explicit guest UI mode toggled by pressing `L_THUMBSTICK_PRESS + L_X` or `R_THUMBSTICK_PRESS + R_A`.
- **R2-03 (Renderer State Protection)**: Removed the generic `nativeSetUseVR(false)` calls from the input routing logic. Consuming UI input (like Android overlays or Guest Navigation) no longer disables the active VR renderer state.
- **R2-04 (Pointer Lock Safety)**: Replaced unlocked and direct mutations of the `Pointer` object with `injectPointerMove`, `injectPointerButtonPress`, and `injectPointerButtonRelease` via the `XServer`.
- **R2-05 (Guest Navigation Keys)**: Implemented all previously dead key injections for `GUEST_KEY_ENTER`, `GUEST_KEY_TAB`, `GUEST_KEY_SHIFT_TAB`, `GUEST_KEY_ESCAPE`, and Arrow Keys. Handled correct modifier key chaining for Shift+Tab.
- **R2-06 (Live Policy Tests)**: Extracted `XrLivePolicy` out of `XrController` to provide a pure policy/sink boundary. Authored `XrLivePolicyTest.kt` verifying all transitions, release-all behavior, and renderer state preservation.
- **R2-07 (Diagnostics Redaction)**: Verified that `ProcessOutputBusTest.kt` correctly uses `SecretRedactor.REDACTED` and verified `ProcessHelper.java` respects the legacy draining implementation without reintroducing idle executors.
- **R2-08 (Scoped Logging Subscription)**: Updated `XServerScreen.kt` to only spawn and subscribe `debugCaptureSubscriber` when `captureLogs` is enabled, and gracefully clean it up through the `EnvironmentComponent.stop` block.
- **R2-09 (Tracking Fallback Integrity)**: Modified `LaunchPrecedenceResolverImpl` to resolve tracking first. VR Mod specific DLL overrides, Environment variables, and active Mod IDs are now ignored if the tracking resolves to the safe default `FLAT_3DOF`. Updated `LaunchPrecedenceResolverMatrixTest.kt` to assert these safe flat fallbacks.

## Testing & Validation
- **Focused Test Gate**: Ran 15 tests (including the 13 from prior runs) across `PreGameInputRouterTest`, `ProcessOutputBusTest`, `TrackingModeResolverTest`, `LaunchPrecedenceResolverMatrixTest`, and `XrLivePolicyTest`. All tests passed.
- **Full Suite & Build**: Ran full `:app:testModernXrDebugUnitTest` and `:app:assembleModernXrDebug` successfully via Gradle shell.
- **APK Verification**: Ran `verify-quest-apk.ps1` to validate the assembly.

## Remaining Risks & Deferred Work
- **Guest OpenXR/OpenVR Runtime Bridge**: True 6DOF mod injection remains deferred and falls back safely to flat 3DOF until a native runtime adapter is available.
- **On-Device Testing**: Has not been performed as it was pending authorization. It is now safe for Quest smoke testing.
* **Guest Text-Focus Detection:** The `detectGuestTextFocus()` method in `XrController.java` currently returns `false`. Reliably detecting when a guest Windows application is prompting for text requires reading X11 cursor changes (e.g. finding the I-beam cursor ID) or intercepting XIM requests, neither of which was safely exposed out-of-the-box in the Winlator XServer. This heuristic needs further research and implementation.
* **Doom 3 DRM:** As discovered previously, SteamStub IPC prevents native modification. Any fallback to manual implementation for Doom 3 VR will require a different approach.

## Next Steps
1. The user can build and deploy the application to test the absolute controller pointer in 2D mode, the Android UI navigation, and verify that game inputs still forward correctly in VR.
2. Address the unimplemented text-focus detection if automatic keyboard opening is strictly required.
3. Move onto Pillars 2-4 of the `GameNativeXR_Architecture_Plan.md`.
