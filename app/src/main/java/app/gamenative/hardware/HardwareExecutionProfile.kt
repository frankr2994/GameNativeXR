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
        if (acceptedRuleIds.isEmpty() || acceptedRuleIds.any { it.isBlank() }) {
            errors.add("acceptedRuleIds must contain at least one non-blank rule ID")
        }
        if (containerVariant.isBlank()) {
            errors.add("containerVariant cannot be blank")
        }
        if (wineVersion.isBlank()) {
            errors.add("wineVersion cannot be blank")
        }
        if (emulator.isBlank()) {
            errors.add("emulator cannot be blank")
        }
        if (graphicsDriver.isBlank()) {
            errors.add("graphicsDriver cannot be blank")
        }
        if (dxwrapper.isBlank()) {
            errors.add("dxwrapper cannot be blank")
        }
        if (requiredPackagedComponents.any { it.componentId.isBlank() }) {
            errors.add("requiredPackagedComponents cannot contain a blank component ID")
        }
        return errors
    }
}
