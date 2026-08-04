# Handoff & Review Report — Milestone 1 (Unified Launch Contract Foundations)

**Verdict: REQUEST_CHANGES**

---

## 1. Observation

Direct code observations from inspecting Worker M1's deliverables:

1. **Path Traversal Boundary Validation in `LaunchRequest.kt` and `ExecutableInspectorImpl.kt`**:
   - `app/src/main/java/app/gamenative/launch/LaunchRequest.kt` line 34:
     ```kotlin
     require(!exeRelativePath.contains("..")) { "exeRelativePath must not contain relative path escape ('..'): $exeRelativePath" }
     ```
     `LaunchRequest` validates that `exeRelativePath` does not contain `".."`, but does NOT validate whether `exeRelativePath` is an absolute path (e.g. starting with `/`, `\`, or drive letter `C:`).
   - `app/src/main/java/app/gamenative/launch/inspect/ExecutableInspectorImpl.kt` line 31:
     ```kotlin
     val targetFile = File(gameRoot, exeRelativePath).canonicalFile
     ```
     In Java/Kotlin, `File(gameRoot, "/etc/passwd").canonicalFile` evaluates to `/etc/passwd` (escaping `gameRoot`). `ExecutableInspectorImpl` does not verify that `targetFile.canonicalPath.startsWith(gameRoot.canonicalFile.canonicalPath)`.

2. **PE Header Parsing Boundary Checks in `PeHeaderParser.kt`**:
   - `app/src/main/java/app/gamenative/launch/inspect/PeHeaderParser.kt` line 56-58:
     ```kotlin
     val peOffset = buf.getInt(0x3C)
     if (peOffset < 0 || peOffset + 24 > bytesRead) {
         return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
     }
     ```
     When `peOffset` is large (e.g. `0x7FFFFFF8` / `2147483640`), `peOffset + 24` overflows a 32-bit signed integer to `-2147483632`. `-2147483632 > bytesRead` evaluates to `false`, bypassing the offset validation check.
     Subsequent call at line 62 (`val peMagic = buf.getInt(peOffset)`) attempts to read byte 2147483640 in a 4096-byte buffer, throwing an uncaught `java.lang.IndexOutOfBoundsException`.
   - `app/src/main/java/app/gamenative/launch/inspect/PeHeaderParser.kt` line 40:
     ```kotlin
     val headerSize = Math.min(raf.length(), 4096L).toInt()
     ```
     `PeHeaderParser` caps inspection to 4096 bytes and performs raw ASCII string scanning. If a PE executable places imported DLL names in the `.rdata` section past offset 4096, `scanVRStrings` misses `openxr_loader.dll` / `openvr_api.dll`.

3. **Level 5 User Environment Variable Resolution in `LaunchPrecedenceResolverImpl.kt`**:
   - `app/src/main/java/app/gamenative/launch/LaunchPrecedenceResolver.kt` lines 160-163:
     ```kotlin
     val mergedEnv = mutableMapOf<String, String>()
     compatibilityInput?.recommendedEnvVars?.let { mergedEnv.putAll(it) }
     modInput?.requiredEnvVars?.let { mergedEnv.putAll(it) }
     ```
     `LaunchPrecedenceResolverImpl` merges Level 3 (Compatibility) and Level 4 (VR Mod) environment variables, but omits Level 5 (User Override) environment variables from `userContainer?.envVars`.

4. **Taxonomy & Driver Protection Verification**:
   - `app/src/main/java/app/gamenative/launch/LaunchFailureCategory.kt` defines exactly 18 failure categories conforming to `PROJECT.md` §5.5.
   - `app/src/main/java/app/gamenative/launch/LaunchPrecedenceResolver.kt` line 95 enforces Fallback Match Protection for hardware-critical fields (`graphicsDriver`, `graphicsDriverConfig`, `dxwrapper`, `dxwrapperConfig`) when `isLevel3Exact == false`.

---

## 2. Logic Chain

1. **Path Traversal Escape**:
   `LaunchRequest` checks for `".."`, but permits absolute path arguments. `File(gameRoot, "/etc/passwd").canonicalFile` returns `/etc/passwd`. Because `ExecutableInspectorImpl` lacks `targetFile.canonicalPath.startsWith(gameRoot.canonicalPath)` verification, files outside `gameRoot` can be accessed and hashed.

2. **Integer Overflow Out-Of-Bounds Crash**:
   In `PeHeaderParser.kt`, `peOffset + 24` uses 32-bit signed arithmetic. For corrupted headers where `e_lfanew` is near `Int.MAX_VALUE`, `peOffset + 24` overflows to a negative integer, causing `peOffset + 24 > bytesRead` to evaluate to `false`. Execution proceeds to `buf.getInt(peOffset)`, throwing an unhandled `IndexOutOfBoundsException` that crashes the calling thread.

3. **Level 5 Precedence Incompleteness**:
   `SettingSource.USER_OVERRIDE` (Level 5) is defined as the highest precedence level. `ContainerData` contains a user `envVars` string. Excluding `userContainer?.envVars` from `mergedEnv` breaks Level 5 precedence for environment variables.

4. **4KB Heuristic Detection Limit**:
   PE import tables often reside in `.rdata` sections past 4096 bytes. Capping `headerSize` at 4096 bytes causes string scanning to miss VR loader imports in larger executables, degrading `trackingMode` resolution to `FLAT_3DOF`.

---

## 3. Caveats

- **No Integrity Violations Found**: Worker M1's implementations contain genuine logic, state machine synchronization, dynamic SHA-256 calculation, and PE header parsing (no hardcoded outputs or facade shortcuts).
- **Driver Override Protection Logic**: Hardware-critical fields in `LaunchPrecedenceResolverImpl` are correctly protected against fallback compatibility overrides.
- **Taxonomy Completeness**: All 18 failure categories in `LaunchFailureCategory` are present and mapped.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

Worker M1 successfully implemented the 19-state launch state machine, 18-category failure taxonomy, 5-level precedence resolver structure, and PE inspector. However, 2 Critical security/crash defects and 1 Major precedence defect must be resolved:

1. **Critical**: Fix integer overflow in `PeHeaderParser.kt` boundary check (`peOffset.toLong() + 24 > bytesRead`) and wrap header parsing in defensive exception handling.
2. **Critical**: Reject absolute paths in `LaunchRequest.kt` and enforce canonical containment checking (`targetFile.canonicalPath.startsWith(...)`) in `ExecutableInspectorImpl.kt`.
3. **Major**: Merge Level 5 `userContainer?.envVars` into `resolvedEnvVars` in `LaunchPrecedenceResolverImpl.kt`.

---

## 5. Verification Method

To independently verify remediation:

1. **Run Unit Test Suite**:
   `.\gradlew.bat :app:testModernXrDebugUnitTest --tests "app.gamenative.launch.*"`

2. **Verify Specific Test Cases**:
   - **Integer Overflow**: Pass synthetic PE file with `e_lfanew = 0x7FFFFFF8` to `PeHeaderParser.parse()`; verify it returns `PeArchitecture.UNKNOWN` without throwing `IndexOutOfBoundsException`.
   - **Path Traversal**: Attempt creating `LaunchRequest` with absolute `exeRelativePath` (`/etc/passwd` or `C:\Windows\cmd.exe`) or pass absolute path to `ExecutableInspectorImpl`; verify it is rejected.
   - **User Env Overrides**: Pass `ContainerData` with `envVars` to `LaunchPrecedenceResolverImpl.resolvePlan()`; verify key-value pairs are merged into `plan.resolvedEnvVars`.

---

## Review Summary

**Verdict**: REQUEST_CHANGES

### Findings

#### [Critical] Finding 1: Integer Overflow Out-Of-Bounds Crash in `PeHeaderParser`
- **What**: `peOffset + 24` integer overflow bypasses boundary check `peOffset + 24 > bytesRead`.
- **Where**: `app/src/main/java/app/gamenative/launch/inspect/PeHeaderParser.kt:57`
- **Why**: Allows corrupted or malicious PE binaries to throw uncaught `IndexOutOfBoundsException`.
- **Suggestion**: Use `peOffset.toLong() + 24 > bytesRead` and wrap buffer access in try-catch returning `PeArchitecture.UNKNOWN`.

#### [Critical] Finding 2: Path Traversal Security Boundary Escape
- **What**: `LaunchRequest` permits absolute paths; `ExecutableInspectorImpl` does not enforce `gameRoot` containment.
- **Where**: `app/src/main/java/app/gamenative/launch/LaunchRequest.kt:34` and `app/src/main/java/app/gamenative/launch/inspect/ExecutableInspectorImpl.kt:31`
- **Why**: Allows reading files outside the designated `gameRoot` directory.
- **Suggestion**: Reject absolute paths in `LaunchRequest` init, and check `targetFile.canonicalPath.startsWith(gameRoot.canonicalFile.canonicalPath)` in `ExecutableInspectorImpl`.

#### [Major] Finding 3: Missing Level 5 User Environment Variable Override
- **What**: `LaunchPrecedenceResolverImpl` ignores `userContainer?.envVars`.
- **Where**: `app/src/main/java/app/gamenative/launch/LaunchPrecedenceResolver.kt:160`
- **Why**: User-configured environment variables in container settings are not applied to the launch plan.
- **Suggestion**: Parse key-value pairs from `userContainer.envVars` and merge into `mergedEnv`.

#### [Minor] Finding 4: VR Import Detection Truncated at 4KB
- **What**: `PeHeaderParser` scans only the first 4096 bytes.
- **Where**: `app/src/main/java/app/gamenative/launch/inspect/PeHeaderParser.kt:40`
- **Why**: VR DLL imports located in `.rdata` past 4KB are missed, causing fallback to `FLAT_3DOF`.
- **Suggestion**: Increase header scan window or parse section headers to locate the PE import table RVA.

---

## Verified Claims

- 19-State explicit state machine → verified via `LaunchState.kt` and `GameLaunchCoordinatorTest.kt` → PASS
- 18 Failure categories completeness → verified via `LaunchFailureCategory.kt` → PASS
- Fallback match driver protection rule → verified via `LaunchPrecedenceResolverTest.kt` → PASS
- Zero hardcoded test outputs / cheating → verified via code inspection → PASS

---

## Adversarial Challenge Report

### Challenge Summary
**Overall risk assessment**: HIGH

### Challenges

#### [Critical] Challenge 1: Corrupted PE Header Crash Attack
- **Assumption challenged**: PE binaries submitted to `ExecutableInspector` will have valid `e_lfanew` values within array bounds.
- **Attack scenario**: Malicious or malformed EXE with `e_lfanew = 0x7FFFFFF8` is inspected.
- **Blast radius**: Coordinator thread crashes with unhandled `IndexOutOfBoundsException`.
- **Mitigation**: `peOffset.toLong() + 24 > bytesRead` validation check and try-catch around PE parsing.

#### [Critical] Challenge 2: Arbitrary File Access via Absolute Path
- **Assumption challenged**: `exeRelativePath` is always relative to `gameRoot`.
- **Attack scenario**: Attacker passes `exeRelativePath = "/etc/passwd"` or `exeRelativePath = "C:\\Windows\\System32\\drivers\\etc\\hosts"`.
- **Blast radius**: `ExecutableInspectorImpl` computes SHA-256 and opens files outside `gameRoot`.
- **Mitigation**: Add absolute path rejection in `LaunchRequest` and canonical containment validation in `ExecutableInspectorImpl`.

### Stress Test Results

- `peOffset = 0x7FFFFFF8` → Expected: `PeArchitecture.UNKNOWN` → Actual: `IndexOutOfBoundsException` (FAIL)
- `exeRelativePath = "/etc/passwd"` → Expected: Validation Error → Actual: Accepted and inspected outside `gameRoot` (FAIL)
- `userContainer.envVars = "MY_VAR=1"` → Expected: Merged into plan → Actual: Ignored (FAIL)
- Fallback compatibility match with driver override → Expected: Driver protected → Actual: Driver protected (PASS)
