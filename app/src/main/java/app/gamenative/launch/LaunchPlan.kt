package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.install.ResolvedGameInstall

/** The 5 precedence levels for setting resolution. */
enum class SettingSource(val level: Int, val description: String) {
    BASE_DEFAULT(1, "GameNative execution baseline default"),
    HARDWARE_PROFILE(2, "Detected Quest hardware execution profile"),
    COMPATIBILITY_PROFILE(3, "Game-specific compatibility database entry"),
    VR_MOD_REQUIREMENT(4, "Active VR mod package manifest requirement"),
    USER_OVERRIDE(5, "Explicit per-game user configuration override")
}

/** Wraps a resolved setting value with its origin source level and rationale. */
data class ResolvedSetting<T>(
    val value: T,
    val source: SettingSource,
    val rationale: String
)

/**
 * Immutable launch execution plan produced by precedence resolution.
 */
data class LaunchPlan(
    val launchId: String,
    val request: LaunchRequest,
    val resolvedInstall: ResolvedGameInstall,
    val executableIdentity: ExecutableIdentity,
    val detectedDeviceDescriptor: String,
    val containerVariant: ResolvedSetting<String>,
    val wineVersion: ResolvedSetting<String>,
    val wow64Mode: ResolvedSetting<Boolean>,
    val emulator: ResolvedSetting<String>,
    val box64Version: ResolvedSetting<String>,
    val box86Version: ResolvedSetting<String>,
    val box86Preset: ResolvedSetting<String>,
    val box64Preset: ResolvedSetting<String>,
    val fexcoreVersion: ResolvedSetting<String>,
    val fexcoreTSOMode: ResolvedSetting<String>,
    val fexcoreX87Mode: ResolvedSetting<String>,
    val fexcoreMultiBlock: ResolvedSetting<String>,
    val fexcorePreset: ResolvedSetting<String>,
    val graphicsDriver: ResolvedSetting<String>,
    val graphicsDriverVersion: ResolvedSetting<String>,
    val graphicsDriverConfig: ResolvedSetting<String>,
    val dxwrapper: ResolvedSetting<String>,
    val dxwrapperConfig: ResolvedSetting<String>,
    val resolvedEnvVars: Map<String, String>,
    val resolvedDllOverrides: Map<String, String>,
    val trackingMode: app.gamenative.launch.vr.TrackingMode,
    val executionConfig: ResolvedContainerExecutionConfig,
    val activeModId: String? = null,
    val resolvedCommandArgs: String = ""
) {
    fun toSummaryLog(): String {
        return buildString {
            appendLine("=== LaunchPlan [$launchId] ===")
            appendLine("AppID: ${request.appId} | Exe: ${executableIdentity.relativePath} (${executableIdentity.architecture})")
            appendLine("Device: $detectedDeviceDescriptor | Mode: $trackingMode")
            appendLine("Container Variant: ${containerVariant.value} [Source: ${containerVariant.source}]")
            appendLine("Wine Version: ${wineVersion.value} [Source: ${wineVersion.source}]")
            appendLine("Graphics Driver: ${graphicsDriver.value} [Source: ${graphicsDriver.source}]")
            appendLine("DX Wrapper: ${dxwrapper.value} [Source: ${dxwrapper.source}]")
            appendLine("Env Vars Count: ${resolvedEnvVars.size} | DLL Overrides Count: ${resolvedDllOverrides.size}")
        }
    }
}
