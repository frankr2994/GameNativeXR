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

## Pending Issues / Considerations
* **Guest Text-Focus Detection:** The `detectGuestTextFocus()` method in `XrController.java` currently returns `false`. Reliably detecting when a guest Windows application is prompting for text requires reading X11 cursor changes (e.g. finding the I-beam cursor ID) or intercepting XIM requests, neither of which was safely exposed out-of-the-box in the Winlator XServer. This heuristic needs further research and implementation.
* **Doom 3 DRM:** As discovered previously, SteamStub IPC prevents native modification. Any fallback to manual implementation for Doom 3 VR will require a different approach.

## Next Steps
1. The user can build and deploy the application to test the absolute controller pointer in 2D mode, the Android UI navigation, and verify that game inputs still forward correctly in VR.
2. Address the unimplemented text-focus detection if automatic keyboard opening is strictly required.
3. Move onto Pillars 2-4 of the `GameNativeXR_Architecture_Plan.md`.
