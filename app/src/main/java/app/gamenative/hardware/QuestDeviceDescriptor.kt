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
