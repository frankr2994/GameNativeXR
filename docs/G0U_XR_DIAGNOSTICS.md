# G0U XR Process Diagnostics

This diagnostic pass adds bounded Logcat evidence for the first physical Quest launch after the GameNative upstream merge. It does not change renderer or Steam launch behavior.

## What is logged

- `XrRoute`: selected X-server renderer, requested container renderer, and whether the Steam service is available in that process.
- `XrLifecycle`: activity class, Android process name/PID, XR handoff flags, resolved container state, and Steam-service availability at create, resume, pause, destroy, and close-session.

No account identifiers, paths, game titles, or tokens are logged.

## Capture command

With the headset connected over ADB, clear old logs, launch one installed Steam title through the XR entry path, then capture only the diagnostic tags:

```powershell
adb logcat -c
adb logcat -v threadtime XrRoute:I XrLifecycle:I *:S
```

Save the output with the test date and APK commit. Record whether `MetaQuest` runs in `:vr_process`, whether `XrRenderer` is selected, and whether `steamServiceAvailable` is false in the VR process.

## Interpretation

`steamServiceAvailable=false` in `:vr_process` is expected from Android's process isolation. It becomes a blocker only if the captured launch also fails at a Steam-dependent operation. Do not introduce cross-process IPC merely from this value; use the paired launch result to scope a fix.

## Build verification status

The source changes are ready for verification. On 2026-07-29, the local Gradle
wrapper repeatedly stalled without compiler output and did not produce an APK,
including with a fresh project-local Gradle cache. Re-run
`assembleModernXrDebug` and `tools/verify-quest-apk.ps1` after that local
Gradle environment is responsive; no build pass is claimed by this document.
