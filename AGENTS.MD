# GameNativeXR Autonomous Engineering Rules

## Scope and repository safety

- Work only in this worktree unless the user explicitly expands scope.
- At the start of work, confirm the worktree, branch, baseline commit, and dirty state. This worktree is intentionally dirty; preserve all unrelated edits and untracked files.
- Never run `git reset`, `git clean`, `git checkout --`, broad deletes, force-pushes, or commits/pushes unless the user explicitly asks.
- Treat `GameNativeXR_Architecture_Plan.md` as the current architecture source of intent and `GameNativeXR_Launch_Path_Map.md` as the current live-launch map.
- Do not modify native `libxr.so`, JNI, tracking, passthrough, swapchain, frame timing, or the guest native-VR bridge. Native source/provenance has not been recovered, so that work is blocked.
- Do not add DRM removal, executable patching, anti-cheat bypasses, title-specific shared-launcher hacks, or credential collection/logging.

## Tooling and validation

- Prefer Rider MCP for file navigation, symbol/reference lookup, changed-scope review, and per-file inspections.
- Use shell `rg`, Gradle, Git, ADB, and PowerShell as safe fallbacks whenever Rider lacks the required project-model capability. State any Rider limitation in the final handoff.
- Gradle is authoritative for Android builds and tests. Do not assume a Rider build result is sufficient.
- Use small, evidence-driven plans. For changes that affect launch state, guest processes, or persistence, first trace the actual call path and identify ownership/lifecycle boundaries.
- Run the narrowest relevant test set after each meaningful change, then run the required aggregate gates before final handoff.
- Use `apply_patch` for source/document edits when available. Do not perform broad formatting or unrelated cleanup.

## Quest, MQDH, and Meta XR Operator policy

- Device work is opt-in. Do not install an APK, launch an app/game, stop a live XR session, clear app data, or change ADB properties without fresh user approval.
- When device work is approved, enumerate devices first and use one explicit ADB serial. Preserve the existing GameNative/Steam data with `adb install -r`; never uninstall or clear data unless explicitly directed.
- MQDH and Meta XR Operator are observation/debug tools only: session state, screenshots, display state, logs, and repeatable smoke-test evidence.
- Never inject synthetic controller/head poses or button input as proof that physical controller routing works. A simulated tracked controller cannot validate a physically inactive/untracked controller.
- Do not expose credentials, tokens, device identifiers, controller serials, or private paths in diagnostics or handoffs.

## Required engineering behavior

- Maintain the existing Bionic/Glibc, Wine prefix, Steam preparation, X-server, renderer, audio, and cleanup flows unless a small, tested adapter boundary is demonstrated.
- A coordinated Flat 3DoF launch may enter `RUNNING` only after observed first-window evidence. Native/modded 6DoF may enter `RUNNING` only after a validated runtime handshake.
- Keep container execution profiles launch-only. Hardware profiles must not silently rewrite persisted container defaults.
- Make cancellation idempotent, cleanup deterministic, and listener failures isolated.
- Keep Operator/debug payloads confined to `modernXrDebug`; release artifacts must exclude them.
- Treat any regression around `GOGDownloadManagerTest.setUp` as a separate test-infrastructure investigation. Fix only demonstrated test lifecycle/underlying lifecycle defects; do not change production download behavior merely to make a test finish.

## Required final evidence

Before handing work back, run and report the applicable commands:

```powershell
git status --short --branch
git diff --check
.\gradlew.bat :app:testModernXrDebugUnitTest
.\gradlew.bat :app:assembleModernXrDebug
powershell -ExecutionPolicy Bypass -File .\tools\verify-quest-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\modernXr\debug\app-modernXr-debug.apk `
  -OperatorLayerPolicy Require
.\gradlew.bat :app:assembleModernXrRelease
powershell -ExecutionPolicy Bypass -File .\tools\verify-quest-apk.ps1 `
  -ApkPath .\app\build\outputs\apk\modernXr\release\app-modernXr-release.apk `
  -OperatorLayerPolicy Forbid
```

If a command is blocked or fails, retain its output, investigate safely, continue independent work, and report the exact blocker. Do not claim device qualification without approved physical-device evidence.

## Final handoff format

Lead with the outcome. Include changed files, behavior changes, test/build results, Rider MCP actions/results, shell fallbacks, unresolved risks, and any device work that remains intentionally pending.
