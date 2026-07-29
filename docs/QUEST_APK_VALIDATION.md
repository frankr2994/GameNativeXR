# Quest APK Verification and Deployment

Use `tools/verify-quest-apk.ps1` to create a local verification report for a
debug APK before installing it on the Quest 2. The report is written under
`build/verification/` by default and is not committed.

## Verify an existing APK

From the repository root:

```powershell
.\tools\verify-quest-apk.ps1
```

The script checks the APK hash, package/version, ARM64 native libraries,
Meta Quest VR entry activity/category, and APK signing. It does not connect to
or modify a headset unless an ADB option is supplied.

## Install and launch on Quest 2

Enable Developer Mode and USB debugging on the headset, connect it by USB, and
authorize the computer in the headset prompt. Then run:

```powershell
.\tools\verify-quest-apk.ps1 -Install -Launch
```

For more than one connected Android device, choose the Quest explicitly:

```powershell
.\tools\verify-quest-apk.ps1 -DeviceSerial <adb-serial> -Install -Launch
```

The launch target is `app.gamenative/com.winlator.xr.runtime.MetaQuest`. ADB
installation/launch confirms Android deployment only; it does not prove
OpenXR session behavior, controller input, performance, or Halo compatibility.

## Build notes

Build first with `./gradlew.bat assembleDebug`. The current project needs the
GameNative-compatible JavaSteam artifacts described in the repository README.
In constrained environments where Kotlin cannot use its normal daemon cache,
append `-Pkotlin.compiler.execution.strategy=in-process` to the Gradle command.
