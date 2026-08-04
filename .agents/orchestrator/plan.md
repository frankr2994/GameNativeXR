# Orchestration Plan — GameNativeXR Architecture Implementation

## Mission
Orchestrate the complete implementation of the GameNativeXR architecture and launch pipeline across all 4 pillars detailed in `GameNativeXR_Architecture_Plan.md`.

## Overview & Methodology
Following the Project Pattern:
1. **Phase 0: Survey**
   - Dispatch 3 parallel Explorer subagents to map current baseline vs target architecture in `GameNativeXR_Architecture_Plan.md`.
   - Explorer 1: Launch Orchestration & Unified Launch Contract (`XServerScreen.kt`, `GameLaunchCoordinator`, `ExecutableInspector`, launch state machine).
   - Explorer 2: Pillar 1 (Hardware Profiles & `QuestDeviceDetector`) + Pillar 3 (Diagnostic Pipeline & `DiagnosticSession`).
   - Explorer 3: Pillar 2 (Input Router & `XrKeyboardOverlay`) + Pillar 4 (Tracking Modes, `VrModResolver`, `XrHostBridge`).

2. **Phase 1: Project Plan & Milestone Decomposition**
   - Synthesize explorer findings into `PROJECT.md` (root) with complete Feature Inventory, Code Layout, Interface Contracts, and Milestone Decomposition (M1-M7).
   - Establish E2E testing framework.

3. **Phase 2: Milestone Execution Loop (Iterative)**
   - For each milestone:
     - Explorer(s) -> Fix Strategy
     - Worker -> Implementation & Test Execution
     - Reviewer(s) -> Code/Architecture Review
     - Challenger(s) -> Adversarial Verification
     - Forensic Auditor -> Integrity Audit
     - Gate Status Evaluation (`GATE_STATUS.md`)
   - Maintain `DEAD_ENDS.md` for oscillation prevention.

4. **Phase 3: Final Verification & Victory Claim**
   - Execute `./gradlew :app:testModernXrDebugUnitTest` across all test suites.
   - Verify 0 compile errors, 0 lint failures, clean architecture conformance.
   - Report final completion to parent.
