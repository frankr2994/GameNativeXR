package app.gamenative.launch

import app.gamenative.launch.vr.TrackingMode

/** Immutable launch-only execution view. It is never written back to a saved Container. */
data class ResolvedContainerExecutionConfig(
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
    val environmentVariables: Map<String, String>,
    val dllOverrides: Map<String, String>,
    val requiredPackagedComponentIds: Set<String>,
    val trackingMode: TrackingMode,
    val activeModId: String?
)
