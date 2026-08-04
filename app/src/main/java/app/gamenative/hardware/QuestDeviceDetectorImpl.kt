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
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL?.takeIf { it.isNotBlank() }
        } else {
            HardwareUtils.getSOCName()?.takeIf { it.isNotBlank() }
        }
        val glRenderer = GPUInformation.getRenderer(context) ?: ""
        val glVendor = GPUInformation.getVendor(context) ?: ""
        val glVersion = GPUInformation.getVersion(context) ?: ""
        val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        val androidRelease = Build.VERSION.RELEASE ?: ""
        val sdkInt = Build.VERSION.SDK_INT
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else ""

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
