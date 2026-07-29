<img width="2400" height="1368" alt="GameNativeXR" src="https://github.com/user-attachments/assets/970ebab4-2be4-4c9f-accf-bdca603a8282" />

### Introduction

GameNativeXR is an active-development fork of GameNative for standalone XR headsets. It combines GameNative's local PC-game compatibility stack and library support with an XR host, stereoscopic presentation, and XR controller integration. The goal is to make supported games playable on the headset without PC streaming while keeping the project aligned with the current GameNative platform baseline.

### Goal

The fork is deliberately upstream-oriented: GameNative updates are merged as reviewable history, and XR-specific changes stay isolated where possible. Current work focuses on a dependable Quest XR runtime path, input, presentation, compatibility profiles, and repeatable validation. Compatibility varies by game, device, graphics driver, and container configuration; this is development software, not a guarantee that every PC title will run.

### Status
`Dev-Update` is the active integration branch. It contains the GameNative upstream merge at `9e9bcdc5` and preserves the Quest XR build variants. The validated build target is `modernXrDebug`.

Current renderer note: XR sessions are intentionally routed through the OpenGL X-server view because the XR renderer consumes its shared EGL context. Non-XR containers can continue to use the upstream Vulkan renderer.

---

# Original README

<div align="center">

# GameNative

**Play the PC games you already own — from Steam, Epic and GOG — on your Android device, with cloud saves.**

<a href="https://trendshift.io/repositories/14497" target="_blank"><img src="https://trendshift.io/api/badge/repositories/14497" alt="utkarshdalal%2FGameNative | Trendshift" style="width: 250px; height: 55px;" width="250" height="55"/></a>

<a href="https://www.star-history.com/utkarshdalal/gamenative">
 <picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/badge?repo=utkarshdalal/GameNative&theme=dark" />
  <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/badge?repo=utkarshdalal/GameNative" />
  <img alt="Star History Rank" src="https://api.star-history.com/badge?repo=utkarshdalal/GameNative" />
 </picture>
</a>

[![GitHub Release](https://img.shields.io/github/v/release/utkarshdalal/GameNative?style=flat-square&logo=github&label=latest)](https://github.com/utkarshdalal/GameNative/releases/latest)
[![GitHub stars](https://img.shields.io/github/stars/utkarshdalal/GameNative?style=flat-square&logo=github&color=ffd700)](https://github.com/utkarshdalal/GameNative/stargazers)
[![Discord](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fdiscord.com%2Fapi%2Fv9%2Finvites%2F2hKv4VfZfE%3Fwith_counts%3Dtrue&query=%24.approximate_member_count&style=flat-square&logo=discord&logoColor=white&label=discord&color=5865F2&suffix=%20members)](https://discord.gg/2hKv4VfZfE)
[![License](https://img.shields.io/badge/license-GPL%203.0-blue?style=flat-square)](https://github.com/utkarshdalal/GameNative/blob/master/LICENSE)
[![Ko-fi](https://img.shields.io/badge/ko--fi-support-FF5E5B?style=flat-square&logo=ko-fi&logoColor=white)](https://ko-fi.com/gamenative)

[**Download**](https://downloads.gamenative.app/releases/1.1.1/gamenative-v1.1.1.apk) · [**Discord**](https://discord.gg/2hKv4VfZfE) · [**Support on Ko-fi**](https://ko-fi.com/gamenative)

<video src="https://github.com/user-attachments/assets/95b5397b-908a-44ef-a10a-dac7723580b0" autoplay loop muted playsinline width="100%"></video>

</div>

---

GameNative lets you run the PC games in your Steam, Epic and GOG libraries directly on Android — no streaming required. Your saves sync to the cloud, so you can stop on your PC and keep going on your phone.

It's still early. Not every game runs yet, and some need tweaking to play well, but the community is constantly finding and sharing configs that work — and these get applied automatically. You can see if anyone has tried running your game successfully at https://gamenative.app/compatibility.

## What you get

- Play games you actually own on Steam, Epic, GOG and Amazon
- Cloud saves that carry over between your PC and your phone
- Automatically applied known configs, so many games just work out of the box with no tweaking required
- Controller and touch support, with a custom control editor and on-screen HUD
- Steam DLC, workshop and branch support
- Active support over Discord if you need help getting a game running

## Demo

[TechDweeb](https://www.youtube.com/@TechDweeb) walks through setting up GameNative on an Android handheld in a couple of minutes:

<div align="center">

<a href="https://youtu.be/QqIChmAu2_A?si=Ha6xzTQXZA2H8HUN&t=53" target="_blank"><img src="https://github.com/user-attachments/assets/6957e3a1-34ac-41f5-b558-0f1868dbf3d4" alt="Youtube Video" /></a>

</div>

## How to use

1. Download the latest release [here](https://downloads.gamenative.app/releases//gamenative-v.apk)
2. Install the APK on your Android device
3. Log in to your Steam account
4. Install your game
5. Hit play and enjoy

## Support

The fastest way to get help is the [Discord server](https://discord.gg/2hKv4VfZfE) — we're 35k+ strong and someone's usually around.

Please **don't** open issues on GitHub; they're closed automatically. Bring it to Discord instead.

If you'd like to chip in, you can support the project on [Ko-fi](https://ko-fi.com/gamenative).

## Contributing

Want to help out? Message us to get into the **#development** channel on [Discord](https://discord.gg/2hKv4VfZfE), or open a thread there. Things we're currently looking for help with live on our [Trello board](https://trello.com/b/vGRkFoAM/open-source-board).

## Building
### IF YOU JUST WANT TO USE THE APP, PLEASE SEE THE HOW TO USE SECTION ABOVE. THIS IS ONLY NEEDED IF YOU WANT TO CONTRIBUTE FOR DEVELOPMENT.
1. Install the Android SDK/NDK versions pinned by the project and set `sdk.dir` in `local.properties` if Android Studio has not already done so.
2. **JavaSteam checkout:** The historic JavaSteam snapshot coordinates are no longer consistently available. Build the GameNative-compatible branch before building this app:
   ```
   git clone --branch gamenative-latest https://github.com/joshuatam/JavaSteam.git ../JavaSteamGameNative
   cd ../JavaSteamGameNative
   ./gradlew jar :javasteam-depotdownloader:jar
   ```
   GameNativeXR automatically uses those sibling artifacts. To use another checkout location, pass `-PjavasteamDir=/path/to/JavaSteam` to Gradle.
3. Build the Quest XR debug APK with `./gradlew assembleModernXrDebug`. On constrained or sandboxed Windows environments where the Kotlin daemon cache is unavailable, add `-Pkotlin.compiler.execution.strategy=in-process`.
4. Verify the generated Quest APK with `./tools/verify-quest-apk.ps1`. See [Quest APK verification](docs/QUEST_APK_VALIDATION.md) for optional ADB install/launch commands.
5. **SteamGridDB API Key (Optional):** To enable automatic fetching of game images for Custom Games, add your SteamGridDB API key to `local.properties`:
   ```properties
   STEAMGRIDDB_API_KEY=your_api_key_here
   ```
   You can get one from your [SteamGridDB preferences](https://www.steamgriddb.com/profile/preferences). Without it everything still works — it just won't fetch images.

## Analytics & privacy

GameNative uses [PostHog](https://posthog.com) for anonymous analytics. No personal information is ever collected — no names, emails, IPs or device identifiers.

**Always collected**, to improve game compatibility:
- Game launch, close and exit events (game name, store, session length, average FPS, container config)
- Game install, cancel and uninstall events

This is how we figure out which games work, how well they run, and which configs to apply automatically for the next person. It can't identify you.

**Optional**, and switchable under *Settings → Info → Usage Analytics*:
- Feature usage (on-screen keyboard, controller, HUD, control editor)
- Login success/failure events
- Recommendation interactions
- App lifecycle events (foreground/background)
- Cloud sync events

The full [Privacy Policy](PrivacyPolicy/README.md) has the details.

## Supporters

Thanks to our [Ko-fi sponsors](https://ko-fi.com/gamenative) and [GitHub sponsors](https://github.com/sponsors/utkarshdalal?preview=true), including [CodeRabbit](https://coderabbit.link/gnative).

[![Star History Chart](https://api.star-history.com/svg?repos=utkarshdalal/GameNative&type=Date&theme=dark)](https://www.star-history.com/#utkarshdalal/GameNative&Date)

## License

[GPL 3.0](https://github.com/utkarshdalal/GameNative/blob/master/LICENSE).

See [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) for attributions, copyleft source offers, and notices about third-party and proprietary components bundled with the app.

---

**Disclaimer:** This software is meant for playing games that you legally own. Don't use it for piracy or anything else illegal. The maintainer takes no responsibility for misuse.
