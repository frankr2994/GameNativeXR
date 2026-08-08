package app.gamenative.hardware.quest

data class QuestDeviceDescriptor(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val socManufacturer: String?,
    val socModel: String?,
    val glRenderer: String?,
    val glVendor: String?,
    val supportedAbis: List<String>,
    val androidVersion: Int,
    val securityPatch: String,
    val isMetaQuestRuntime: Boolean
)
