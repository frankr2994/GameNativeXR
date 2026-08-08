package app.gamenative.hardware.quest

import java.util.Locale

object QuestProfileResolver {

    fun resolve(descriptor: QuestDeviceDescriptor): QuestProfileDecisionReport {
        val manufacturer = descriptor.manufacturer.lowercase(Locale.ENGLISH)
        val isMeta = manufacturer.contains("oculus") || 
                     manufacturer.contains("meta") ||
                     descriptor.isMetaQuestRuntime
                     
        if (!isMeta) {
            return QuestProfileDecisionReport(
                isSupported = false,
                profile = null,
                ruleId = "UNKNOWN_META",
                confidence = 0f,
                rejectedRules = listOf("NOT_META_MANUFACTURER")
            )
        }
        
        val rejectedRules = mutableListOf<String>()
        val device = descriptor.device.lowercase(Locale.ENGLISH)
        val model = descriptor.model.lowercase(Locale.ENGLISH)
        
        val isQuest2 = model.contains("quest 2") || device.contains("hollywood")
        val isQuest3 = model.contains("quest 3") || device.contains("eureka")
        
        val renderer = descriptor.glRenderer?.lowercase(Locale.ENGLISH) ?: ""
        
        if (isQuest2) {
            val hasAdreno6xx = renderer.contains("adreno") && renderer.matches(Regex(".*\\b6[0-9]{2}\\b.*"))
            if (!hasAdreno6xx) {
                rejectedRules.add("QUEST2_GPU_MISMATCH")
            } else {
                return QuestProfileDecisionReport(
                    isSupported = true,
                    profile = getQuest2Candidate(),
                    ruleId = "QUEST2_RULE_1",
                    confidence = 1.0f,
                    rejectedRules = rejectedRules
                )
            }
        }
        
        if (isQuest3) {
            val hasAdreno7xx = renderer.contains("adreno") && renderer.matches(Regex(".*\\b7[0-9]{2}\\b.*"))
            if (!hasAdreno7xx) {
                rejectedRules.add("QUEST3_GPU_MISMATCH")
            } else {
                // Per architecture plan: Quest 3 remains an unpromoted profile until a physical Quest 3 or a validated device report is available
                rejectedRules.add("QUEST3_UNPROMOTED")
            }
        }
        
        return QuestProfileDecisionReport(
            isSupported = false,
            profile = null,
            ruleId = "UNKNOWN_META",
            confidence = 0f,
            rejectedRules = rejectedRules
        )
    }
    
    private fun getQuest2Candidate(): QuestProfile {
        return QuestProfile(
            id = "quest2",
            schemaVersion = 1,
            acceptedRuleIds = listOf("QUEST2_RULE_1"),
            containerVariant = "Bionic",
            wineVersion = "proton-10.0-arm64ec-2",
            wow64Mode = true,
            emulator = "2605",
            fexCoreVersion = "2605",
            box86Version = "0.3.2",
            box64Version = "0.4.2",
            graphicsDriver = "Wrapper",
            graphicsDriverVersion = "Turnip v26.2.0 R4",
            graphicsDriverConfig = "Turnip v26.2.0 R4",
            dxwrapper = "DXVK",
            dxwrapperConfig = "1.11.1-sarek",
            requiredComponentHashes = emptyMap()
        )
    }
}
