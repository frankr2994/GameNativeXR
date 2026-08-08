package app.gamenative.hardware.quest

data class QuestProfile(
    val id: String,
    val schemaVersion: Int,
    val acceptedRuleIds: List<String>,
    
    val containerVariant: String,
    val wineVersion: String,
    val wow64Mode: Boolean,
    val emulator: String,
    val fexCoreVersion: String,
    val box86Version: String,
    val box64Version: String,
    
    val graphicsDriver: String,
    val graphicsDriverVersion: String,
    val graphicsDriverConfig: String,
    val dxwrapper: String,
    val dxwrapperConfig: String,
    
    val requiredComponentHashes: Map<String, String>
)

data class QuestProfileDecisionReport(
    val isSupported: Boolean,
    val profile: QuestProfile?,
    val ruleId: String,
    val confidence: Float,
    val rejectedRules: List<String>
)
