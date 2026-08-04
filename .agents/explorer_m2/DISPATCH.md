## 2026-08-03T09:08:26Z
You are Explorer M2 (teamwork_preview_explorer).
Your working directory is F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_m2.
Master Project Plan location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/PROJECT.md
Original Request location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/ORIGINAL_REQUEST.md
Architecture Plan location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/GameNativeXR_Architecture_Plan.md
Explorer 2 Findings location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_2/analysis.md

MANDATORY: You MUST read ORIGINAL_REQUEST.md, PROJECT.md, GameNativeXR_Architecture_Plan.md Section 6, and .agents/explorer_2/analysis.md before proceeding.

Your Mission:
Produce an explicit, production-ready implementation blueprint for Milestone 2: Pillar 1 — Quest Hardware Execution Profiles.

Target Deliverables to Detail in your Blueprint:
1. `app/src/main/java/app/gamenative/hardware/QuestDeviceDescriptor.kt` — Data model for hardware facts (Build.MANUFACTURER, MODEL, DEVICE, PRODUCT, SOC_MODEL, GL_RENDERER, ABIs, Meta XR runtime state), `ClassifiedQuestDevice` enum (`QUEST_2`, `QUEST_3`, `UNKNOWN_META`, `NOT_QUEST`), matched rule ID, and confidence score.
2. `app/src/main/java/app/gamenative/hardware/QuestDeviceDetector.kt` & `QuestDeviceDetectorImpl.kt` — Device detector running rules engine (Oculus/Meta manufacturer check, model string matching, GPU family regex Adreno 6xx for Quest 2 vs Adreno 7xx for Quest 3, strict non-inference for unknown devices).
3. `app/src/main/java/app/gamenative/hardware/HardwareExecutionProfile.kt` — Versioned schema mapping into `ContainerData` execution parameters (`containerVariant`, `wineVersion`, `wow64Mode`, `emulator`, `fexcoreVersion`, `graphicsDriver`, `dxwrapper`, etc.) excluding performance policy fields.
4. `app/src/main/java/app/gamenative/hardware/HardwareExecutionProfileResolver.kt` & `HardwareExecutionProfileResolverImpl.kt` — Pure profile resolver reading profile JSONs from assets and outputting immutable `HardwareProfileResolutionResult`.
5. Assets: JSON profile schemas in `app/src/main/assets/profiles/quest_2.json`, `app/src/main/assets/profiles/quest_3.json`, and `app/src/main/assets/profiles/default_baseline.json`.
6. Unit Test Suites:
   - `app/src/test/java/app/gamenative/hardware/QuestDeviceDetectorTest.kt`
   - `app/src/test/java/app/gamenative/hardware/HardwareExecutionProfileResolverTest.kt`

Rider MCP Rules:
If Rider MCP is available, use it for code inspection and checking existing project structures.

Output Requirements:
Write your detailed blueprint to F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_m2/analysis.md and handoff summary to F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_m2/handoff.md.

When finished, send a message to parent with your handoff report summary.
