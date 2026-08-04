## 2026-08-03T04:12:44Z
<USER_REQUEST>
You are Worker M1 (teamwork_preview_worker).
Your working directory is F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/worker_m1.
Master Project Plan location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/PROJECT.md
Original Request location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/ORIGINAL_REQUEST.md
Architecture Blueprint location: F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/explorer_m1/analysis.md

MANDATORY: You MUST read ORIGINAL_REQUEST.md, PROJECT.md, and .agents/explorer_m1/analysis.md before writing any code.

DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Mission:
Implement Milestone 1: Unified Launch Contract Foundations exactly as specified in .agents/explorer_m1/analysis.md.

Exclusive Write Ownership:
- `app/src/main/java/app/gamenative/launch/**`
- `app/src/test/java/app/gamenative/launch/**`

Files to Implement:
1. `app/src/main/java/app/gamenative/launch/LaunchState.kt`
2. `app/src/main/java/app/gamenative/launch/LaunchRequest.kt`
3. `app/src/main/java/app/gamenative/launch/LaunchPlan.kt`
4. `app/src/main/java/app/gamenative/launch/LaunchPrecedenceResolver.kt`
5. `app/src/main/java/app/gamenative/launch/LaunchFailureCategory.kt`
6. `app/src/main/java/app/gamenative/launch/inspect/ExecutableInspector.kt`
7. `app/src/main/java/app/gamenative/launch/inspect/PeHeaderParser.kt`
8. `app/src/main/java/app/gamenative/launch/GameLaunchCoordinator.kt`
9. `app/src/main/java/app/gamenative/launch/GameLaunchCoordinatorImpl.kt`

Unit Test Files to Implement:
1. `app/src/test/java/app/gamenative/launch/ExecutableInspectorTest.kt`
2. `app/src/test/java/app/gamenative/launch/LaunchPrecedenceResolverTest.kt`
3. `app/src/test/java/app/gamenative/launch/GameLaunchCoordinatorTest.kt`

Verification Requirements:
You MUST run and verify the following commands in powershell:
1. `.\gradlew.bat :app:testModernXrDebugUnitTest` (must pass with 0 failures)
2. `.\gradlew.bat :app:assembleModernXrDebug` (must compile with 0 syntax/type errors)

Rider MCP Rules:
Prefer Rider MCP tools (or canonical shell tools) for navigation, code inspection, edits, compilation, and test execution.

Output Requirements:
Write your implementation summary, test commands, exact output logs, and handoff report to F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/worker_m1/handoff.md. Update F:/QuestVR/_worktrees/GameNativeXR-Dev-Update/.agents/worker_m1/progress.md regularly.

When finished, send a message to parent with your handoff report summary.
</USER_REQUEST>
