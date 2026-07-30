# JavaSteam Dependency Audit (2026)

## Current Configuration
* **Dependency:** `io.github.joshuatam:javasteam:1.8.0.1-24-SNAPSHOT`
* **Source:** Maven Snapshots (`https://central.sonatype.com/repository/maven-snapshots/`) or locally built.
* **Target Branch:** `gamenative-latest` from `joshuatam/JavaSteam`

## Audit Decision: RETAINED
The custom snapshot was intentionally retained because of GameNativeXR XR separation dependencies.
We must avoid updating to arbitrary mainline JavaSteam snapshots that could break:
1. Offline fallback behaviors critical for XR usage.
2. Bionic compatibility specific to GameNative's container setup.

Therefore, the current `1.8.0.1-24-SNAPSHOT` version from the GameNative-compatible branch is preserved. No upstream pull from the mainline JavaSteam repository should be merged without extensive regression validation on the Quest target.
