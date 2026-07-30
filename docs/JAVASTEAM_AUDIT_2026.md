# JavaSteam Dependency Audit (2026)

## Current configuration

- Dependency: io.github.joshuatam:javasteam:1.8.0.1-24-SNAPSHOT
- Intended source branch: gamenative-latest in joshuatam/JavaSteam
- Resolution source: configured Maven snapshot repository or a local build cache

## Decision: retain pending a compatible candidate

No newer GameNative-compatible JavaSteam artifact, provenance record, or regression result was established in this refresh. The current dependency is therefore retained without inferring an XR-specific compatibility claim.

Any future update must document the exact resolved artifact and validate Steam authentication, library synchronization, offline launch, and a repeat launch on the Quest target before adoption.
