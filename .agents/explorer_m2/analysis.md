# Milestone 2 — Pillar 1: Quest Hardware Execution Profiles Architecture & Implementation Blueprint

## Executive Summary

This blueprint provides the complete, production-ready specification and Kotlin source code design for **Milestone 2: Pillar 1 — Quest Hardware Execution Profiles**.

In the current baseline (`Dev-Update`), hardware capability is determined by mutating global static variables in `DefaultVersion` via `ContainerUtils.setContainerDefaults()` based on unclassified `GPUInformation` probes (`isAdreno6xx`, `isTurnipCapable`). This approach introduces non-deterministic launch side-effects and fails to distinguish Meta Quest 2 from Meta Quest 3 as distinct execution profiles.

Milestone 2 replaces global static mutations with an immutable, rules-driven hardware classification engine (`QuestDeviceDetector`) and a pure hardware profile resolver (`HardwareExecutionProfileResolver`) reading versioned JSON schemas from assets (`quest_2.json`, `quest_3.json`, `default_baseline.json`). 

---

## 1. Complete Architecture Overview

```
                                  ┌────────────────────────────────┐
                                  │      Android System / GLES     │
                                  │ (Build, SOC, GLES GL_RENDERER) │
                                  └───────────────┬────────────────┘
                                                  │ QuestRawHardwareFacts
                                                  ▼
                                   ┌──────────────────────────────┐
                                   │      QuestDeviceDetector     │
                                   │   (Rules Engine: Meta Gate,  │
                                   │    Model Tokens, GPU Regex)  │
                                   └──────────────┬───────────────┘
                                                  │ QuestDeviceDescriptor
                                                  │ (QUEST_2, QUEST_3, UNKNOWN_META, NOT_QUEST)
                                                  ▼
                                   ┌──────────────────────────────┐
                                   │HardwareExecutionProfileResolver│
                                   │ (Reads profile JSON assets)  │
                                   └──────────────┬───────────────┘
                                                  │ HardwareProfileResolutionResult
                                                  ▼
                                   ┌──────────────────────────────┐
                                   │    applyToContainerData()    │
                                   │   (Non-mutating mapping to   │
                                   │   ContainerData parameters)  │
                                   └──────────────────────────────┘
```

---

## 2. Deliverable 1: `QuestDeviceDescriptor.kt`

**Target File**: `app/src/main/java/app/gamenative/hardware/QuestDeviceDescriptor.kt`

### Specification
`QuestDeviceDescriptor` captures observed device facts, classification enum, matched rule ID, confidence score, and rejected rules.

```kotlin
package app.gamenative.hardware

/**
 * Enumeration of classified Quest hardware devices.
 */
enum class ClassifiedQuestDevice {
    /** Confirmed Meta Quest 2 headset. */
    QUEST_2,

    /** Confirmed Meta Quest 3 headset. */
    QUEST_3,

    /** Meta/Oculus device evidence present, but signature or GPU facts are ambiguous/conflicting. Strict non-inference. */
    UNKNOWN_META,

    /** Non-Meta device (e.g. general Android phone/tablet or emulator). */
    NOT_QUEST
}

/**
 * Raw hardware facts queried from Android Build APIs and GLES context.
 */
data class QuestRawHardwareFacts(
    val buildManufacturer: String,
    val buildBrand: String,
    val buildModel: String,
    val buildDevice: String,
    val buildProduct: String,
    val buildHardware: String,
    val socManufacturer: String?,
    val socModel: String?,
    val glVendor: String,
    val glRenderer: String,
    val glVersion: String,
    val supportedAbis: List<String>,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val isMetaXrRuntime: Boolean
)

/**
 * Immutable descriptor representing the classified hardware identity and evidence.
 */
data class QuestDeviceDescriptor(
    val buildManufacturer: String,
    val buildModel: String,
    val buildDevice: String,
    val buildProduct: String,
    val socManufacturer: String?,
    val socModel: String?,
    val gpuRenderer: String,
    val isMetaXrRuntime: Boolean,
    val classifiedDevice: ClassifiedQuestDevice,
    val matchedRuleId: String,
    val confidence: Double,
    val rejectedRules: List<String> = emptyList(),
    val rawFacts: QuestRawHardwareFacts? = null
)
```

---

## 3. Deliverable 2: `QuestDeviceDetector.kt` & `QuestDeviceDetectorImpl.kt`

**Target Files**:
- `app/src/main/java/app/gamenative/hardware/QuestDeviceDetector.kt`
- `app/src/main/java/app/gamenative/hardware/QuestDeviceDetectorImpl.kt`

### Specification
`QuestDeviceDetector` defines the contract for device identification. `QuestDeviceDetectorImpl` executes a 4-tier rule engine:
1. **Meta Evidence Gate** (`RULE_NOT_QUEST`): Requires `Build.MANUFACTURER` or `Build.BRAND` contains `"Meta"` or `"Oculus"` (case-insensitive) OR `isMetaXrRuntime == true`. If missing, returns `NOT_QUEST` with `confidence = 0.0`.
2. **Model Token Signature Matching**: Matches checked-in hardware model tokens for Quest 2 vs Quest 3.
3. **GPU Family Consistency Check**: Quest 2 must match Adreno 6xx (`.*adreno.*\\b6[0-9]{2}\\b.*`); Quest 3 must match Adreno 7xx (`.*adreno.*\\b7[0-9]{2}\\b.*`).
4. **Strict Non-Inference Fallback** (`RULE_UNKNOWN_META_FALLBACK`): If manufacturer is Meta/Oculus but signatures conflict or are unknown, return `UNKNOWN_META` with `confidence = 0.0`. Never infer Quest 3 for unknown devices.

```kotlin
// QuestDeviceDetector.kt
package app.gamenative.hardware

import android.content.Context

interface QuestDeviceDetector {
    /**
     * Probes the system environment (Context, Build, GLES) and returns a classified QuestDeviceDescriptor.
     */
    fun detectDevice(context: Context): QuestDeviceDescriptor

    /**
     * Pure classification method operating directly on pre-assembled raw facts.
     * Enables direct, context-free unit testing.
     */
    fun classifyFacts(facts: QuestRawHardwareFacts): QuestDeviceDescriptor
}
```

```kotlin
// QuestDeviceDetectorImpl.kt
package app.gamenative.hardware

import android.content.Context
import android.os.Build
import app.gamenative.utils.HardwareUtils
import com.winlator.core.GPUInformation
import java.util.Locale

class QuestDeviceDetectorImpl : QuestDeviceDetector {

    companion object {
        const val RULE_NOT_QUEST = "RULE_NOT_QUEST"
        const val RULE_QUEST_2_EXACT = "RULE_QUEST_2_EXACT"
        const val RULE_QUEST_3_EXACT = "RULE_QUEST_3_EXACT"
        const val RULE_UNKNOWN_META_FALLBACK = "RULE_UNKNOWN_META_FALLBACK"

        private val QUEST_2_MODEL_TOKENS = listOf("hollywood", "oculus quest 2", "quest 2", "miramar", "quest2")
        private val QUEST_3_MODEL_TOKENS = listOf("eureka", "oculus quest 3", "quest 3", "quest3")

        private val ADRENO_6XX_REGEX = Regex(".*adreno.*\\b6[0-9]{2}\\b.*", RegexOption.IGNORE_CASE)
        private val ADRENO_7XX_REGEX = Regex(".*adreno.*\\b7[0-9]{2}\\b.*", RegexOption.IGNORE_CASE)
    }

    override fun detectDevice(context: Context): QuestDeviceDescriptor {
        val buildManufacturer = Build.MANUFACTURER ?: ""
        val buildBrand = Build.BRAND ?: ""
        val buildModel = Build.MODEL ?: ""
        val buildDevice = Build.DEVICE ?: ""
        val buildProduct = Build.PRODUCT ?: ""
        val buildHardware = Build.HARDWARE ?: ""
        val socManufacturer = HardwareUtils.getSOCName() // Null or SOC string on API 31+
        val socModel = HardwareUtils.getSOCName()
        val glRenderer = GPUInformation.getRenderer(context) ?: ""
        val glVendor = GPUInformation.getVendor(context) ?: ""
        val glVersion = GPUInformation.getVersion(context) ?: ""
        val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        val androidRelease = Build.VERSION.RELEASE ?: ""
        val sdkInt = Build.VERSION.SDK_INT
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else ""
        
        // Detect Meta XR runtime presence
        val isMetaXrRuntime = isMetaXrRuntimePresent()

        val facts = QuestRawHardwareFacts(
            buildManufacturer = buildManufacturer,
            buildBrand = buildBrand,
            buildModel = buildModel,
            buildDevice = buildDevice,
            buildProduct = buildProduct,
            buildHardware = buildHardware,
            socManufacturer = socManufacturer,
            socModel = socModel,
            glVendor = glVendor,
            glRenderer = glRenderer,
            glVersion = glVersion,
            supportedAbis = supportedAbis,
            androidRelease = androidRelease,
            sdkInt = sdkInt,
            securityPatch = securityPatch,
            isMetaXrRuntime = isMetaXrRuntime
        )

        return classifyFacts(facts)
    }

    override fun classifyFacts(facts: QuestRawHardwareFacts): QuestDeviceDescriptor {
        val rejectedRules = mutableListOf<String>()

        // Tier 1: Meta/Oculus Manufacturer Evidence Gate
        val isMetaManufacturer = facts.buildManufacturer.contains("Meta", ignoreCase = true) ||
                facts.buildManufacturer.contains("Oculus", ignoreCase = true) ||
                facts.buildBrand.contains("Meta", ignoreCase = true) ||
                facts.buildBrand.contains("Oculus", ignoreCase = true) ||
                facts.isMetaXrRuntime

        if (!isMetaManufacturer) {
            rejectedRules.add(RULE_QUEST_2_EXACT)
            rejectedRules.add(RULE_QUEST_3_EXACT)
            rejectedRules.add(RULE_UNKNOWN_META_FALLBACK)
            return QuestDeviceDescriptor(
                buildManufacturer = facts.buildManufacturer,
                buildModel = facts.buildModel,
                buildDevice = facts.buildDevice,
                buildProduct = facts.buildProduct,
                socManufacturer = facts.socManufacturer,
                socModel = facts.socModel,
                gpuRenderer = facts.glRenderer,
                isMetaXrRuntime = facts.isMetaXrRuntime,
                classifiedDevice = ClassifiedQuestDevice.NOT_QUEST,
                matchedRuleId = RULE_NOT_QUEST,
                confidence = 0.0,
                rejectedRules = rejectedRules,
                rawFacts = facts
            )
        }

        val combinedModelString = "${facts.buildModel} ${facts.buildDevice} ${facts.buildProduct}".lowercase(Locale.ENGLISH)
        val isAdreno6xx = ADRENO_6XX_REGEX.matches(facts.glRenderer)
        val isAdreno7xx = ADRENO_7XX_REGEX.matches(facts.glRenderer)

        val matchesQuest2Token = QUEST_2_MODEL_TOKENS.any { combinedModelString.contains(it) }
        val matchesQuest3Token = QUEST_3_MODEL_TOKENS.any { combinedModelString.contains(it) }

        // Tier 2: Quest 2 Exact Signature
        if (matchesQuest2Token && isAdreno6xx) {
            val confidence = if (!facts.socModel.isNullOrEmpty()) 1.0 else 0.9
            return QuestDeviceDescriptor(
                buildManufacturer = facts.buildManufacturer,
                buildModel = facts.buildModel,
                buildDevice = facts.buildDevice,
                buildProduct = facts.buildProduct,
                socManufacturer = facts.socManufacturer,
                socModel = facts.socModel,
                gpuRenderer = facts.glRenderer,
                isMetaXrRuntime = facts.isMetaXrRuntime,
                classifiedDevice = ClassifiedQuestDevice.QUEST_2,
                matchedRuleId = RULE_QUEST_2_EXACT,
                confidence = confidence,
                rejectedRules = rejectedRules,
                rawFacts = facts
            )
        } else {
            rejectedRules.add(RULE_QUEST_2_EXACT)
        }

        // Tier 3: Quest 3 Exact Signature
        if (matchesQuest3Token && isAdreno7xx) {
            val confidence = if (!facts.socModel.isNullOrEmpty()) 1.0 else 0.9
            return QuestDeviceDescriptor(
                buildManufacturer = facts.buildManufacturer,
                buildModel = facts.buildModel,
                buildDevice = facts.buildDevice,
                buildProduct = facts.buildProduct,
                socManufacturer = facts.socManufacturer,
                socModel = facts.socModel,
                gpuRenderer = facts.glRenderer,
                isMetaXrRuntime = facts.isMetaXrRuntime,
                classifiedDevice = ClassifiedQuestDevice.QUEST_3,
                matchedRuleId = RULE_QUEST_3_EXACT,
                confidence = confidence,
                rejectedRules = rejectedRules,
                rawFacts = facts
            )
        } else {
            rejectedRules.add(RULE_QUEST_3_EXACT)
        }

        // Tier 4: Unknown Meta Fallback (Strict Non-Inference)
        return QuestDeviceDescriptor(
            buildManufacturer = facts.buildManufacturer,
            buildModel = facts.buildModel,
            buildDevice = facts.buildDevice,
            buildProduct = facts.buildProduct,
            socManufacturer = facts.socManufacturer,
            socModel = facts.socModel,
            gpuRenderer = facts.glRenderer,
            isMetaXrRuntime = facts.isMetaXrRuntime,
            classifiedDevice = ClassifiedQuestDevice.UNKNOWN_META,
            matchedRuleId = RULE_UNKNOWN_META_FALLBACK,
            confidence = 0.0,
            rejectedRules = rejectedRules,
            rawFacts = facts
        )
    }

    private fun isMetaXrRuntimePresent(): Boolean {
        return try {
            Class.forName("com.oculus.vrshell.SystemXR")
            true
        } catch (e: ClassNotFoundException) {
            try {
                System.getProperty("com.meta.xr.runtime") != null
            } catch (e2: Exception) {
                false
            }
        }
    }
}
```

---

## 4. Deliverable 3: `HardwareExecutionProfile.kt`

**Target File**: `app/src/main/java/app/gamenative/hardware/HardwareExecutionProfile.kt`

### Specification
`HardwareExecutionProfile` models execution fields mapped directly into `ContainerData`. Performance policy fields (resolution, refresh rate, CPU/GPU levels, foveation, upscaling, frame caps) are strictly excluded.

```kotlin
package app.gamenative.hardware

/**
 * Packaged component requirement specification.
 */
data class RequiredComponentSpec(
    val componentId: String,
    val expectedHashSha256: String? = null,
    val minVersion: String? = null
)

/**
 * Versioned schema for hardware execution parameters.
 */
data class HardwareExecutionProfile(
    val profileId: String,
    val schemaVersion: Int,
    val acceptedRuleIds: List<String>,
    val containerVariant: String,
    val wineVersion: String,
    val wow64Mode: Boolean,
    val emulator: String,
    val fexcoreVersion: String,
    val fexcoreTSOMode: String,
    val fexcoreX87Mode: String,
    val fexcoreMultiBlock: String,
    val fexcorePreset: String,
    val box86Version: String,
    val box64Version: String,
    val box86Preset: String,
    val box64Preset: String,
    val graphicsDriver: String,
    val graphicsDriverVersion: String,
    val graphicsDriverConfig: String,
    val dxwrapper: String,
    val dxwrapperConfig: String,
    val requiredPackagedComponents: List<RequiredComponentSpec> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }

    /**
     * Validates profile schema constraints.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            errors.add("Unsupported schema version: $schemaVersion (expected $CURRENT_SCHEMA_VERSION)")
        }
        if (profileId.isBlank()) {
            errors.add("Profile ID cannot be blank")
        }
        if (containerVariant.isBlank()) {
            errors.add("containerVariant cannot be blank")
        }
        if (wineVersion.isBlank()) {
            errors.add("wineVersion cannot be blank")
        }
        return errors
    }
}
```

---

## 5. Deliverable 4: `HardwareExecutionProfileResolver.kt` & `HardwareExecutionProfileResolverImpl.kt`

**Target Files**:
- `app/src/main/java/app/gamenative/hardware/HardwareExecutionProfileResolver.kt`
- `app/src/main/java/app/gamenative/hardware/HardwareExecutionProfileResolverImpl.kt`

### Specification
Pure profile resolver reading profile JSONs via a `ProfileAssetProvider` interface. Produces immutable `HardwareProfileResolutionResult` and non-mutating `applyToContainerData()`.

```kotlin
// HardwareExecutionProfileResolver.kt
package app.gamenative.hardware

import com.winlator.container.ContainerData

enum class ProfileResolutionSource {
    EXACT_MATCH,
    UNQUALIFIED_METADATA_FALLBACK,
    DEFAULT_BASELINE_FALLBACK
}

data class ProfileFieldDecision(
    val fieldName: String,
    val value: String,
    val sourceProfileId: String,
    val rationale: String
)

data class HardwareProfileResolutionResult(
    val descriptor: QuestDeviceDescriptor,
    val profile: HardwareExecutionProfile,
    val resolutionSource: ProfileResolutionSource,
    val fieldDecisions: Map<String, ProfileFieldDecision>
)

interface ProfileAssetProvider {
    fun getProfileJson(profileId: String): String?
}

interface HardwareExecutionProfileResolver {
    fun resolveProfile(descriptor: QuestDeviceDescriptor): HardwareProfileResolutionResult
    fun applyToContainerData(base: ContainerData, result: HardwareProfileResolutionResult): ContainerData
}
```

```kotlin
// HardwareExecutionProfileResolverImpl.kt
package app.gamenative.hardware

import com.winlator.container.ContainerData
import org.json.JSONArray
import org.json.JSONObject

class HardwareExecutionProfileResolverImpl(
    private val assetProvider: ProfileAssetProvider
) : HardwareExecutionProfileResolver {

    override fun resolveProfile(descriptor: QuestDeviceDescriptor): HardwareProfileResolutionResult {
        val (targetProfileId, resolutionSource) = when (descriptor.classifiedDevice) {
            ClassifiedQuestDevice.QUEST_2 -> "quest_2" to ProfileResolutionSource.EXACT_MATCH
            ClassifiedQuestDevice.QUEST_3 -> "quest_3" to ProfileResolutionSource.EXACT_MATCH
            ClassifiedQuestDevice.UNKNOWN_META -> "default_baseline" to ProfileResolutionSource.UNQUALIFIED_METADATA_FALLBACK
            ClassifiedQuestDevice.NOT_QUEST -> "default_baseline" to ProfileResolutionSource.DEFAULT_BASELINE_FALLBACK
        }

        val jsonString = assetProvider.getProfileJson(targetProfileId)
            ?: assetProvider.getProfileJson("default_baseline")
            ?: throw IllegalStateException("Failed to load profile asset '$targetProfileId' or 'default_baseline'")

        val profile = parseProfileJson(jsonString)
        val validationErrors = profile.validate()
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid profile '$targetProfileId': ${validationErrors.joinToString()}")
        }

        val fieldDecisions = buildFieldDecisions(profile, descriptor, resolutionSource)

        return HardwareProfileResolutionResult(
            descriptor = descriptor,
            profile = profile,
            resolutionSource = resolutionSource,
            fieldDecisions = fieldDecisions
        )
    }

    override fun applyToContainerData(base: ContainerData, result: HardwareProfileResolutionResult): ContainerData {
        val p = result.profile
        return base.copy(
            containerVariant = p.containerVariant,
            wineVersion = p.wineVersion,
            wow64Mode = p.wow64Mode,
            emulator = p.emulator,
            fexcoreVersion = p.fexcoreVersion,
            fexcoreTSOMode = p.fexcoreTSOMode,
            fexcoreX87Mode = p.fexcoreX87Mode,
            fexcoreMultiBlock = p.fexcoreMultiBlock,
            fexcorePreset = p.fexcorePreset,
            box86Version = p.box86Version,
            box64Version = p.box64Version,
            box86Preset = p.box86Preset,
            box64Preset = p.box64Preset,
            graphicsDriver = p.graphicsDriver,
            graphicsDriverVersion = p.graphicsDriverVersion,
            graphicsDriverConfig = p.graphicsDriverConfig,
            dxwrapper = p.dxwrapper,
            dxwrapperConfig = p.dxwrapperConfig
        )
    }

    private fun parseProfileJson(jsonString: String): HardwareExecutionProfile {
        val obj = JSONObject(jsonString)
        val profileId = obj.getString("profileId")
        val schemaVersion = obj.getInt("schemaVersion")

        val acceptedRuleIdsArray = obj.optJSONArray("acceptedRuleIds") ?: JSONArray()
        val acceptedRuleIds = mutableListOf<String>()
        for (i in 0 until acceptedRuleIdsArray.length()) {
            acceptedRuleIds.add(acceptedRuleIdsArray.getString(i))
        }

        val componentsArray = obj.optJSONArray("requiredPackagedComponents") ?: JSONArray()
        val components = mutableListOf<RequiredComponentSpec>()
        for (i in 0 until componentsArray.length()) {
            val cObj = componentsArray.getJSONObject(i)
            components.add(
                RequiredComponentSpec(
                    componentId = cObj.getString("componentId"),
                    expectedHashSha256 = cObj.optString("expectedHashSha256", null),
                    minVersion = cObj.optString("minVersion", null)
                )
            )
        }

        return HardwareExecutionProfile(
            profileId = profileId,
            schemaVersion = schemaVersion,
            acceptedRuleIds = acceptedRuleIds,
            containerVariant = obj.getString("containerVariant"),
            wineVersion = obj.getString("wineVersion"),
            wow64Mode = obj.getBoolean("wow64Mode"),
            emulator = obj.getString("emulator"),
            fexcoreVersion = obj.getString("fexcoreVersion"),
            fexcoreTSOMode = obj.optString("fexcoreTSOMode", "Fast"),
            fexcoreX87Mode = obj.optString("fexcoreX87Mode", "Fast"),
            fexcoreMultiBlock = obj.optString("fexcoreMultiBlock", "Disabled"),
            fexcorePreset = obj.optString("fexcorePreset", "INTERMEDIATE"),
            box86Version = obj.optString("box86Version", "0.3.2"),
            box64Version = obj.optString("box64Version", "0.4.2"),
            box86Preset = obj.optString("box86Preset", "COMPATIBILITY"),
            box64Preset = obj.optString("box64Preset", "COMPATIBILITY"),
            graphicsDriver = obj.getString("graphicsDriver"),
            graphicsDriverVersion = obj.getString("graphicsDriverVersion"),
            graphicsDriverConfig = obj.optString("graphicsDriverConfig", ""),
            dxwrapper = obj.getString("dxwrapper"),
            dxwrapperConfig = obj.getString("dxwrapperConfig"),
            requiredPackagedComponents = components
        )
    }

    private fun buildFieldDecisions(
        profile: HardwareExecutionProfile,
        descriptor: QuestDeviceDescriptor,
        source: ProfileResolutionSource
    ): Map<String, ProfileFieldDecision> {
        val rationalePrefix = "Resolved from profile '${profile.profileId}' via rule '${descriptor.matchedRuleId}' ($source)"
        val fields = mapOf(
            "containerVariant" to profile.containerVariant,
            "wineVersion" to profile.wineVersion,
            "wow64Mode" to profile.wow64Mode.toString(),
            "emulator" to profile.emulator,
            "fexcoreVersion" to profile.fexcoreVersion,
            "graphicsDriver" to profile.graphicsDriver,
            "graphicsDriverVersion" to profile.graphicsDriverVersion,
            "dxwrapper" to profile.dxwrapper,
            "dxwrapperConfig" to profile.dxwrapperConfig
        )
        return fields.mapValues { (fieldName, value) ->
            ProfileFieldDecision(
                fieldName = fieldName,
                value = value,
                sourceProfileId = profile.profileId,
                rationale = "$rationalePrefix for field $fieldName"
            )
        }
    }
}
```

---

## 6. Deliverable 5: Asset Profile Schemas

### 1. `app/src/main/assets/profiles/quest_2.json`
```json
{
  "profileId": "quest_2",
  "schemaVersion": 1,
  "acceptedRuleIds": [
    "RULE_QUEST_2_EXACT"
  ],
  "containerVariant": "bionic",
  "wineVersion": "proton-10.0-arm64ec-2",
  "wow64Mode": true,
  "emulator": "FEXCore",
  "fexcoreVersion": "2605",
  "fexcoreTSOMode": "Fast",
  "fexcoreX87Mode": "Fast",
  "fexcoreMultiBlock": "Disabled",
  "fexcorePreset": "INTERMEDIATE",
  "box86Version": "0.3.2",
  "box64Version": "0.4.2",
  "box86Preset": "COMPATIBILITY",
  "box64Preset": "COMPATIBILITY",
  "graphicsDriver": "Wrapper",
  "graphicsDriverVersion": "Turnip v26.2.0 R4",
  "graphicsDriverConfig": "",
  "dxwrapper": "dxvk",
  "dxwrapperConfig": "version=1.11.1-sarek,vkd3dVersion=2.14.1",
  "requiredPackagedComponents": [
    {
      "componentId": "wine_proton_10_arm64ec",
      "minVersion": "10.0-arm64ec-2"
    },
    {
      "componentId": "turnip_v26_2_0_r4",
      "minVersion": "26.2.0"
    },
    {
      "componentId": "fexcore_2605",
      "minVersion": "2605"
    }
  ]
}
```

### 2. `app/src/main/assets/profiles/quest_3.json`
```json
{
  "profileId": "quest_3",
  "schemaVersion": 1,
  "acceptedRuleIds": [
    "RULE_QUEST_3_EXACT"
  ],
  "containerVariant": "bionic",
  "wineVersion": "proton-10.0-arm64ec-2",
  "wow64Mode": true,
  "emulator": "FEXCore",
  "fexcoreVersion": "2605",
  "fexcoreTSOMode": "Fast",
  "fexcoreX87Mode": "Fast",
  "fexcoreMultiBlock": "Disabled",
  "fexcorePreset": "INTERMEDIATE",
  "box86Version": "0.3.2",
  "box64Version": "0.4.2",
  "box86Preset": "COMPATIBILITY",
  "box64Preset": "COMPATIBILITY",
  "graphicsDriver": "Wrapper",
  "graphicsDriverVersion": "Turnip v26.2.0 R4",
  "graphicsDriverConfig": "",
  "dxwrapper": "dxvk",
  "dxwrapperConfig": "version=2.4.1-gplasync,vkd3dVersion=2.14.1",
  "requiredPackagedComponents": [
    {
      "componentId": "wine_proton_10_arm64ec",
      "minVersion": "10.0-arm64ec-2"
    },
    {
      "componentId": "turnip_v26_2_0_r4",
      "minVersion": "26.2.0"
    },
    {
      "componentId": "fexcore_2605",
      "minVersion": "2605"
    }
  ]
}
```

### 3. `app/src/main/assets/profiles/default_baseline.json`
```json
{
  "profileId": "default_baseline",
  "schemaVersion": 1,
  "acceptedRuleIds": [
    "RULE_UNKNOWN_META_FALLBACK",
    "RULE_NOT_QUEST"
  ],
  "containerVariant": "bionic",
  "wineVersion": "proton-10.0-arm64ec-2",
  "wow64Mode": true,
  "emulator": "FEXCore",
  "fexcoreVersion": "2605",
  "fexcoreTSOMode": "Fast",
  "fexcoreX87Mode": "Fast",
  "fexcoreMultiBlock": "Disabled",
  "fexcorePreset": "INTERMEDIATE",
  "box86Version": "0.3.2",
  "box64Version": "0.4.2",
  "box86Preset": "COMPATIBILITY",
  "box64Preset": "COMPATIBILITY",
  "graphicsDriver": "Wrapper",
  "graphicsDriverVersion": "Turnip v26.2.0 R4",
  "graphicsDriverConfig": "",
  "dxwrapper": "dxvk",
  "dxwrapperConfig": "version=1.11.1-sarek,vkd3dVersion=2.14.1",
  "requiredPackagedComponents": [
    {
      "componentId": "wine_proton_10_arm64ec",
      "minVersion": "10.0-arm64ec-2"
    }
  ]
}
```

---

## 7. Deliverable 6: Unit Test Suites

### 1. `QuestDeviceDetectorTest.kt`
**Target File**: `app/src/test/java/app/gamenative/hardware/QuestDeviceDetectorTest.kt`

```kotlin
package app.gamenative.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuestDeviceDetectorTest {

    private lateinit var detector: QuestDeviceDetectorImpl

    @Before
    fun setUp() {
        detector = QuestDeviceDetectorImpl()
    }

    @Test
    fun classifyFacts_returnsQuest2_forHollywoodModelAndAdreno650() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Oculus",
            buildBrand = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            buildHardware = "qcom",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 650",
            glVersion = "OpenGL ES 3.2 V@0502.0",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "10",
            sdkInt = 29,
            securityPatch = "2021-01-01",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.QUEST_2, descriptor.classifiedDevice)
        assertEquals("RULE_QUEST_2_EXACT", descriptor.matchedRuleId)
        assertEquals(1.0, descriptor.confidence, 0.001)
        assertEquals("Oculus", descriptor.buildManufacturer)
        assertEquals("Adreno (TM) 650", descriptor.gpuRenderer)
    }

    @Test
    fun classifyFacts_returnsQuest3_forEurekaModelAndAdreno740() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Meta",
            buildBrand = "Meta",
            buildModel = "Quest 3",
            buildDevice = "eureka",
            buildProduct = "eureka",
            buildHardware = "qcom",
            socManufacturer = "Qualcomm",
            socModel = "SM8550",
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 740",
            glVersion = "OpenGL ES 3.2 V@0700.0",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "12",
            sdkInt = 32,
            securityPatch = "2023-10-01",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.QUEST_3, descriptor.classifiedDevice)
        assertEquals("RULE_QUEST_3_EXACT", descriptor.matchedRuleId)
        assertEquals(1.0, descriptor.confidence, 0.001)
    }

    @Test
    fun classifyFacts_returnsNotQuest_forSamsungPhone() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Samsung",
            buildBrand = "samsung",
            buildModel = "SM-G998B",
            buildDevice = "p3s",
            buildProduct = "p3s",
            buildHardware = "exynos2100",
            socManufacturer = "Samsung",
            socModel = "Exynos 2100",
            glVendor = "ARM",
            glRenderer = "Mali-G78",
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "13",
            sdkInt = 33,
            securityPatch = "2023-05-01",
            isMetaXrRuntime = false
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.NOT_QUEST, descriptor.classifiedDevice)
        assertEquals("RULE_NOT_QUEST", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
        assertTrue(descriptor.rejectedRules.contains("RULE_QUEST_2_EXACT"))
        assertTrue(descriptor.rejectedRules.contains("RULE_QUEST_3_EXACT"))
    }

    @Test
    fun classifyFacts_returnsUnknownMeta_whenQuest3ModelHasAdreno6xxGPU() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Meta",
            buildBrand = "Meta",
            buildModel = "Quest 3",
            buildDevice = "eureka",
            buildProduct = "eureka",
            buildHardware = "qcom",
            socManufacturer = null,
            socModel = null,
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 650", // Conflicting GPU!
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "12",
            sdkInt = 31,
            securityPatch = "",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        // Must NOT infer Quest 3 when GPU is Adreno 6xx!
        assertEquals(ClassifiedQuestDevice.UNKNOWN_META, descriptor.classifiedDevice)
        assertEquals("RULE_UNKNOWN_META_FALLBACK", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
    }

    @Test
    fun classifyFacts_returnsUnknownMeta_forUnrecognizedMetaModel() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Meta",
            buildBrand = "Meta",
            buildModel = "Quest Pro Prototype",
            buildDevice = "future_device",
            buildProduct = "future_device",
            buildHardware = "qcom",
            socManufacturer = null,
            socModel = null,
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 830",
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "14",
            sdkInt = 34,
            securityPatch = "",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.UNKNOWN_META, descriptor.classifiedDevice)
        assertEquals("RULE_UNKNOWN_META_FALLBACK", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
    }
}
```

---

### 2. `HardwareExecutionProfileResolverTest.kt`
**Target File**: `app/src/test/java/app/gamenative/hardware/HardwareExecutionProfileResolverTest.kt`

```kotlin
package app.gamenative.hardware

import com.winlator.container.ContainerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class HardwareExecutionProfileResolverTest {

    private lateinit var resolver: HardwareExecutionProfileResolverImpl
    private lateinit var mockAssetProvider: ProfileAssetProvider

    private val quest2Json = """
        {
          "profileId": "quest_2",
          "schemaVersion": 1,
          "acceptedRuleIds": ["RULE_QUEST_2_EXACT"],
          "containerVariant": "bionic",
          "wineVersion": "proton-10.0-arm64ec-2",
          "wow64Mode": true,
          "emulator": "FEXCore",
          "fexcoreVersion": "2605",
          "fexcoreTSOMode": "Fast",
          "fexcoreX87Mode": "Fast",
          "fexcoreMultiBlock": "Disabled",
          "fexcorePreset": "INTERMEDIATE",
          "box86Version": "0.3.2",
          "box64Version": "0.4.2",
          "box86Preset": "COMPATIBILITY",
          "box64Preset": "COMPATIBILITY",
          "graphicsDriver": "Wrapper",
          "graphicsDriverVersion": "Turnip v26.2.0 R4",
          "graphicsDriverConfig": "",
          "dxwrapper": "dxvk",
          "dxwrapperConfig": "version=1.11.1-sarek,vkd3dVersion=2.14.1"
        }
    """.trimIndent()

    private val quest3Json = """
        {
          "profileId": "quest_3",
          "schemaVersion": 1,
          "acceptedRuleIds": ["RULE_QUEST_3_EXACT"],
          "containerVariant": "bionic",
          "wineVersion": "proton-10.0-arm64ec-2",
          "wow64Mode": true,
          "emulator": "FEXCore",
          "fexcoreVersion": "2605",
          "fexcoreTSOMode": "Fast",
          "fexcoreX87Mode": "Fast",
          "fexcoreMultiBlock": "Disabled",
          "fexcorePreset": "INTERMEDIATE",
          "box86Version": "0.3.2",
          "box64Version": "0.4.2",
          "box86Preset": "COMPATIBILITY",
          "box64Preset": "COMPATIBILITY",
          "graphicsDriver": "Wrapper",
          "graphicsDriverVersion": "Turnip v26.2.0 R4",
          "graphicsDriverConfig": "",
          "dxwrapper": "dxvk",
          "dxwrapperConfig": "version=2.4.1-gplasync,vkd3dVersion=2.14.1"
        }
    """.trimIndent()

    private val defaultBaselineJson = """
        {
          "profileId": "default_baseline",
          "schemaVersion": 1,
          "acceptedRuleIds": ["RULE_UNKNOWN_META_FALLBACK", "RULE_NOT_QUEST"],
          "containerVariant": "bionic",
          "wineVersion": "proton-10.0-arm64ec-2",
          "wow64Mode": true,
          "emulator": "FEXCore",
          "fexcoreVersion": "2605",
          "fexcoreTSOMode": "Fast",
          "fexcoreX87Mode": "Fast",
          "fexcoreMultiBlock": "Disabled",
          "fexcorePreset": "INTERMEDIATE",
          "box86Version": "0.3.2",
          "box64Version": "0.4.2",
          "box86Preset": "COMPATIBILITY",
          "box64Preset": "COMPATIBILITY",
          "graphicsDriver": "Wrapper",
          "graphicsDriverVersion": "Turnip v26.2.0 R4",
          "graphicsDriverConfig": "",
          "dxwrapper": "dxvk",
          "dxwrapperConfig": "version=1.11.1-sarek,vkd3dVersion=2.14.1"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockAssetProvider = object : ProfileAssetProvider {
            override fun getProfileJson(profileId: String): String? {
                return when (profileId) {
                    "quest_2" -> quest2Json
                    "quest_3" -> quest3Json
                    "default_baseline" -> defaultBaselineJson
                    else -> null
                }
            }
        }
        resolver = HardwareExecutionProfileResolverImpl(mockAssetProvider)
    }

    @Test
    fun resolveProfile_resolvesQuest2Profile_forQuest2Descriptor() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            gpuRenderer = "Adreno (TM) 650",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_2,
            matchedRuleId = "RULE_QUEST_2_EXACT",
            confidence = 1.0
        )

        val result = resolver.resolveProfile(descriptor)

        assertEquals("quest_2", result.profile.profileId)
        assertEquals(ProfileResolutionSource.EXACT_MATCH, result.resolutionSource)
        assertEquals("version=1.11.1-sarek,vkd3dVersion=2.14.1", result.profile.dxwrapperConfig)
        assertNotNull(result.fieldDecisions["graphicsDriver"])
    }

    @Test
    fun resolveProfile_resolvesQuest3Profile_forQuest3Descriptor() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Meta",
            buildModel = "Quest 3",
            buildDevice = "eureka",
            buildProduct = "eureka",
            socManufacturer = "Qualcomm",
            socModel = "SM8550",
            gpuRenderer = "Adreno (TM) 740",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_3,
            matchedRuleId = "RULE_QUEST_3_EXACT",
            confidence = 1.0
        )

        val result = resolver.resolveProfile(descriptor)

        assertEquals("quest_3", result.profile.profileId)
        assertEquals(ProfileResolutionSource.EXACT_MATCH, result.resolutionSource)
        assertEquals("version=2.4.1-gplasync,vkd3dVersion=2.14.1", result.profile.dxwrapperConfig)
    }

    @Test
    fun resolveProfile_fallsBackToDefaultBaseline_forUnknownMetaDescriptor() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Meta",
            buildModel = "Unknown Prototype",
            buildDevice = "unknown",
            buildProduct = "unknown",
            socManufacturer = null,
            socModel = null,
            gpuRenderer = "Adreno (TM) 750",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.UNKNOWN_META,
            matchedRuleId = "RULE_UNKNOWN_META_FALLBACK",
            confidence = 0.0
        )

        val result = resolver.resolveProfile(descriptor)

        assertEquals("default_baseline", result.profile.profileId)
        assertEquals(ProfileResolutionSource.UNQUALIFIED_METADATA_FALLBACK, result.resolutionSource)
    }

    @Test
    fun applyToContainerData_createsNewInstanceWithProfileParameters_withoutMutatingOriginalOrPerformancePolicy() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            gpuRenderer = "Adreno (TM) 650",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_2,
            matchedRuleId = "RULE_QUEST_2_EXACT",
            confidence = 1.0
        )

        val result = resolver.resolveProfile(descriptor)

        val baseContainer = ContainerData(
            name = "TestContainer",
            graphicsDriver = "OldDriver",
            dxwrapperConfig = "OldConfig",
            xrCPULevel = 80, // Performance policy field
            xrRefreshRate = 90 // Performance policy field
        )

        val updatedContainer = resolver.applyToContainerData(baseContainer, result)

        // Verify original is untouched
        assertEquals("OldDriver", baseContainer.graphicsDriver)
        assertEquals("OldConfig", baseContainer.dxwrapperConfig)

        // Verify updated container has profile fields
        assertEquals("Wrapper", updatedContainer.graphicsDriver)
        assertEquals("version=1.11.1-sarek,vkd3dVersion=2.14.1", updatedContainer.dxwrapperConfig)

        // Verify performance policy fields REMAIN UNCHANGED
        assertEquals(80, updatedContainer.xrCPULevel)
        assertEquals(90, updatedContainer.xrRefreshRate)
    }
}
```

---

## 8. Implementation Guidance for `ContainerUtils` Integration

To complete Pillar 1 integration without global mutable state:
1. Deprecate direct usage of `DefaultVersion` mutations in `ContainerUtils.setContainerDefaults()`.
2. Introduce `HardwareProfileManager` singleton or DI component that initializes `QuestDeviceDetector` once during application bootstrap.
3. During container creation or launch resolution, obtain `HardwareProfileResolutionResult` and invoke `resolver.applyToContainerData(containerData, result)`.

---

## 9. 5-Component Handoff Protocol Alignment

1. **Observation**: `ContainerUtils.setContainerDefaults()` mutates global static state `DefaultVersion.*` based on unclassified GPU renderer strings (`isAdreno6xx`). Existing sources (`HardwareUtils.kt`, `GPUInformation.java`, `ContainerData.kt`) lack explicit Quest 2 / Quest 3 classification models or JSON assets.
2. **Logic Chain**: Replacing global static mutations with an explicit rules engine (`QuestDeviceDetectorImpl`) + asset-backed pure resolver (`HardwareExecutionProfileResolverImpl`) achieves deterministic profile resolution, prevents accidental Quest 3 promotion without hardware evidence, and isolates performance policy from hardware execution parameters.
3. **Caveats**: Quest 3 asset profile uses `version=2.4.1-gplasync` candidate pending final physical Quest 3 hardware qualification; Quest 2 uses confirmed baseline `1.11.1-sarek`.
4. **Conclusion**: Detailed contracts, production Kotlin source code, JSON assets, unit tests, and integration steps are fully specified for Milestone 2.
5. **Verification Method**: Execute `.\gradlew.bat :app:testModernXrDebugUnitTest` to run `QuestDeviceDetectorTest` and `HardwareExecutionProfileResolverTest`.
