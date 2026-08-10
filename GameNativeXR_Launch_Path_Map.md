# GameNativeXR Live Launch Path Map

## Scope and baseline

This is the Phase 1 read-only map for `Dev-Update` at baseline commit `1a98caca`. It records the existing Steam/Wine/X-server launch behavior before coordinator integration. No launch logic was moved while producing this map.

Rider MCP opened and searched the target worktree. Text navigation worked, but semantic symbol search returned no project symbols because Rider exposes the checkout as `Miscellaneous Files`; the paths and line ownership below were therefore confirmed with Rider file/text navigation and repository search fallbacks.

## End-to-end ownership

```text
Library Play action
  -> PlayBridge.onClickPlay (PluviaMain)
  -> preLaunchApp
       container creation/selection
       executable presence check
       component/download readiness
       ImageFS install and container activation
       cloud/workshop preparation
  -> MainViewModel.launchApp
       play history, Steam API preparation, splash/navigation state
  -> PluviaScreen.XServer
  -> XServerScreen
       X server/render/input/window listeners
       background WineSetup-Thread
       prefix/component extraction
       setupXEnvironment
  -> XEnvironment.startEnvironmentComponents (in insertion order)
  -> BionicProgramLauncherComponent or GlibcProgramLauncherComponent
  -> ProcessHelper.exec
       process PID returned to the launcher component
       termination callback emits GuestProgramTerminated
  -> first application X window callback
  -> exit / PluviaApp.shutdownEnvironment
```

## Request entry and navigation

| Stage | Existing owner | Input | Output / side effect |
| --- | --- | --- | --- |
| Play request | `PluviaMain.kt`, `PlayBridge.onClickPlay` | container-style `appId`, `asContainer`, offline state | stores launch state and invokes `preLaunchApp` |
| Readiness | `PluviaMain.kt`, `preLaunchApp` | context, app ID, boot mode, cloud choices | resolves/creates persisted `Container`; checks an effective executable; installs/downloads dependencies; activates ImageFS; runs store/cloud preparation |
| Navigation | `MainViewModel.launchApp` | context and app ID | updates history/splash, prepares Steam API behavior, then navigates to `PluviaScreen.XServer` |
| X screen | `PluviaMain.kt` XServer route | `launchedAppId`, boot/test/diagnostic/offline flags | creates `XServerScreen` and connects mapped-window, exit, and launch-error UI callbacks |

`preLaunchApp` currently validates only that a source-specific executable string is nonblank. It does not produce a canonical install/executable record and it begins persistent container/component preparation before PE inspection.

## Live launch inputs

| Input | Current selection point | Notes |
| --- | --- | --- |
| Store/source and numeric ID | `ContainerUtils.extractGameSourceFromContainerId` / `extractGameIdFromContainerId` | the string `appId` is also the container ID |
| Persisted container | `ContainerUtils.getOrCreateContainer` in `preLaunchApp`; `ContainerUtils.getContainer` in `XServerScreen` | container root is the Wine prefix/config root, not the game install root |
| Steam install root | `SteamService.getAppDirPath(gameId)` | imported install path wins; otherwise completed install directories are preferred, then partial directories, then a preferred future path |
| Launch option | `SteamService.getWindowsLaunchInfos(gameId).firstOrNull()` in `XServerScreen` | selection is not represented in a resolved install contract |
| Executable | `container.executablePath`, else `SteamService.getInstalledExe` | several branches persist the fallback during command construction |
| Container variant/backend | `container.containerVariant` in `setupXEnvironment` | `GLIBC` selects `GlibcProgramLauncherComponent`; all other values select `BionicProgramLauncherComponent` |
| Wine version | `container.wineVersion` | resolves `WineInfo` and a content profile before environment setup |
| Environment and DLL overrides | local `EnvVars` plus `container.envVars` | merged during setup, then launcher-specific variables are added |
| Offline/Steam mode | route state plus container Steam flags | selects direct, real-Steam, Bionic-Steam, legacy, or loader command behavior |

## Prefix preparation before environment construction

`XServerScreen` creates one `WineSetup-Thread` when `PluviaApp.xEnvironment` is null. The thread currently owns:

1. container activation and WinHandler input configuration;
2. first-boot and variant/Wine/ImageFS change detection;
3. `WineInfo` and Wine-path selection;
4. diagnostic-session start;
5. `setupWineSystemFiles` (prefix patches, DX wrapper, Windows components, Steam files, theme/registry state, and persisted applied markers);
6. input DLL extraction;
7. graphics-driver extraction/configuration;
8. Wine audio driver and ImageFS variant selection; and
9. `setupXEnvironment` invocation and assignment to `PluviaApp.xEnvironment`.

An exception ends diagnostics, attempts environment cleanup, nulls the global environment, and invokes `onGameLaunchError`.

## Existing environment adapter boundary

The agreed minimum boundary is an adapter invoked on the existing `WineSetup-Thread` immediately before prefix preparation. It receives a fully resolved immutable plan/config and observes—but initially does not reorder—the following existing operations:

```text
backend.preparePrefix
  = setupWineSystemFiles
  + input/graphics/audio/ImageFS preparation

backend.createAndStartEnvironment
  = setupXEnvironment
      select Bionic/Glibc launcher
      construct Wine command and preinstall chain
      add X/audio/renderer/launcher/request components
      startEnvironmentComponents

backend.observe
  = launcher PID callback (new exposure required)
  + launcher termination callback
  + X WindowManager first application-window evidence
  + XR handshake evidence when a qualified VR path exists

backend.cleanup
  = existing environment stop / exit path, guarded exactly once
```

Planning and install/PE resolution must complete before `setupWineSystemFiles`; otherwise an invalid request can already mutate prefix/container state. Environment component construction and ordering stay in `setupXEnvironment` until parity tests pass.

## `setupXEnvironment` behavior to preserve

1. Hard-kill stale Wine processes and build base environment variables.
2. Select Bionic or Glibc from the container variant and content profile.
3. Apply current game fixes and legacy launch-local/persisted settings.
4. Construct the final Wine desktop command with `getWineStartCommand`.
5. Resolve and chain `PreInstallSteps`; `preUnpack` runs immediately before the first launcher execution.
6. Build `XEnvironment` in insertion order: diagnostic cleanup hook, SysV SHM, X server, network, optional Steam shim, audio, renderer, guest launcher, and Wine request component.
7. Prepare real-Steam token files where configured.
8. Call `XEnvironment.startEnvironmentComponents`; the guest launcher is started as an environment component.
9. Start WinHandler asynchronously, clear the temporary environment map, and return the live environment.

`XEnvironment.startEnvironmentComponents` starts components in insertion order. `stopEnvironmentComponents` stops them in the same order, not reverse order.

## Guest process and callback behavior

Both concrete launchers keep their own static PID and receive it from `ProcessHelper.exec`. That PID is currently logged but not exposed to Kotlin or the coordinator.

- `BionicProgramLauncherComponent.start`: stops an old process, extracts emulator files, runs `preUnpack`, calls `execGuestProgram`, and enables Steam keepalive.
- `GlibcProgramLauncherComponent.start`: stops an old process, extracts Box64/config, runs `preUnpack`, calls `execGuestProgram`, and enables keepalive.
- Each ProcessHelper termination callback resets its private PID and calls the configured termination callback.
- Preinstall termination callbacks currently mark a step complete regardless of exit status, kill Wine, retarget the launcher, and call `start()` again.
- The final game callback records diagnostics, ends the launch diagnostic session, invokes the UI error callback for nonzero status, and emits `AndroidEvent.GuestProgramTerminated`.

The public `GuestProgramLauncherComponent` base also has a separate static PID. Because the subclasses shadow it, adding a getter only to the base would not expose the real Bionic/Glibc PID. The backend needs an explicit PID-observer contract implemented by both concrete launchers.

## First-window evidence

`XServerScreen` installs a `WindowManager.OnWindowModificationListener` before environment setup:

- `onUpdateWindowContent` sets `xServerState.winStarted` on the first application-window content update.
- `onMapWindow` applies workarounds and forwards the existing UI callback.
- `onUnmapWindow` starts an exit watch and forwards unmap.

For flat mode, the coordinator's first-window signal should be the first mapped/renderable application window (or its first content update if mapping precedes renderability), filtered from Wine infrastructure windows. Returning from `setupXEnvironment` is not running evidence.

## Path model and conversions

| Meaning | Existing representation | Conversion / risk |
| --- | --- | --- |
| Game host install root | e.g. `SteamService.getAppDirPath(gameId)` | Android filesystem directory; can be imported, completed, partial, or merely preferred/future |
| Wine container/prefix root | `container.rootDir`, `ImageFs.wineprefix` | separate from game install; current coordinator incorrectly treats `request.containerPath` as the game root |
| Host drive binding | `container.drives` entries such as `A:/host/game` or another letter | setup passes host paths as launcher bind paths |
| Prefix dosdevice | `${imageFs.wineprefix}/dosdevices/a:/...` | used by unpack/preparation code; not equivalent to the original host root |
| Wine-visible direct path | `A:\relative\game.exe` or selected drive | used for custom/imported/legacy paths |
| Bionic-Steam Wine path | `C:\Program Files (x86)\Steam\steamapps\common\<folder>\<exe>` | Steam layout view, even though working directory is the Android install path |
| Real-Steam command | `steam.exe ... -applaunch <id>` | selected executable may be a later Steam child, not the root command |
| Generic host path through Wine | `Z:/...` | used for packaged utilities such as `TestD3D.exe`; unsuitable as an implicit game-root conversion |

The resolver must carry host root, mounted root/drive, Wine path, and relative executable independently. String removal (`removePrefix`), `drives.indexOf(path)`, and unchecked concatenation are not authoritative containment checks.

## Cleanup and recreation behavior

- `exit` uses a process-wide `AtomicBoolean` guard, stops WinHandler, calls `PluviaApp.shutdownEnvironment`, tears down Bionic Steam asynchronously, schedules trash cleanup, records the frame summary, and navigates away.
- setup failure has a separate cleanup path.
- composable disposal removes input/window listeners but does not own a launch-session object.
- `PluviaApp.xEnvironment == null` is the present duplicate-start guard. It is insufficient while setup is in progress because the global is assigned only after `setupXEnvironment` returns.

One session registry keyed by launch ID/app request is needed above the composable so activity recreation reattaches to the same coordinator/backend instead of starting another Wine setup thread.

## Integration constraints found during mapping

- Do not substitute `container.rootDir` for the game install root.
- Do not persist executable/profile fallbacks during immutable planning.
- Do not report `RUNNING` from successful environment setup.
- Do not fabricate a PID when the concrete launcher cannot expose one.
- Do not treat a launcher/root PID as the final game PID in real-Steam or multi-process titles.
- Preserve component order, preinstall chaining, Steam preparation, audio, renderer, WinHandler, and existing cleanup until callback parity is tested.
- Existing unpack/DRM behavior is outside this integration change; the continuation adds no bypass or executable patching.
