package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableIdentity
import com.winlator.container.ContainerData

/** Input metadata for compatibility profile resolution. */
data class CompatibilityResolutionInput(
    val isExactMatch: Boolean, // True for exact_gpu_match or gpu_family_match; false for fallback_match / none
    val recommendedVariant: String? = null,
    val recommendedWineVersion: String? = null,
    val recommendedGraphicsDriver: String? = null,
    val recommendedGraphicsDriverConfig: String? = null,
    val recommendedDxwrapper: String? = null,
    val recommendedDxwrapperConfig: String? = null,
    val recommendedEnvVars: Map<String, String> = emptyMap()
)

/** Input metadata for Quest hardware profile resolution. */
data class QuestHardwareProfileInput(
    val profileId: String = "unresolved",
    val deviceDescriptor: String, // e.g. "QUEST_2", "QUEST_3"
    val matchedRuleId: String = "unresolved",
    val containerVariant: String = "bionic",
    val wineVersion: String = "proton-10.0-arm64ec-2",
    val wow64Mode: Boolean = true,
    val emulator: String = "FEXCore",
    val fexcoreTSOMode: String = "Fast",
    val fexcoreX87Mode: String = "Fast",
    val fexcoreMultiBlock: String = "Disabled",
    val fexcorePreset: String = "INTERMEDIATE",
    val box86Version: String = "0.3.2",
    val box64Version: String = "0.4.2",
    val box86Preset: String = "COMPATIBILITY",
    val box64Preset: String = "COMPATIBILITY",
    val fexcoreVersion: String = "2605",
    val graphicsDriver: String = "Wrapper",
    val graphicsDriverVersion: String = "",
    val graphicsDriverConfig: String = "Turnip v26.2.0 R4",
    val dxwrapper: String = "dxvk",
    val dxwrapperConfig: String = "1.11.1-sarek",
    val requiredComponentIds: List<String> = emptyList()
)

/** Input metadata for VR mod requirements. */
data class VrModRequirementInput(
    val modId: String,
    val requiredDllOverrides: Map<String, String> = emptyMap(),
    val requiredEnvVars: Map<String, String> = emptyMap(),
    val targetTrackingMode: String = "MODDED_6DOF"
)

interface LaunchPrecedenceResolver {
    fun resolvePlan(
        request: LaunchRequest,
        exeIdentity: ExecutableIdentity,
        hardwareInput: QuestHardwareProfileInput,
        compatibilityInput: CompatibilityResolutionInput?,
        modInput: VrModRequirementInput?,
        userContainer: ContainerData?
    ): LaunchPlan
}

class LaunchPrecedenceResolverImpl : LaunchPrecedenceResolver {

    override fun resolvePlan(
        request: LaunchRequest,
        exeIdentity: ExecutableIdentity,
        hardwareInput: QuestHardwareProfileInput,
        compatibilityInput: CompatibilityResolutionInput?,
        modInput: VrModRequirementInput?,
        userContainer: ContainerData?
    ): LaunchPlan {
        // Level 1: Baseline Defaults
        val baseVariant = "bionic"
        val baseWine = "proton-10.0-arm64ec-2"
        val baseWow64 = true
        val baseEmulator = "FEXCore"
        val baseBox64 = "0.4.2"
        val baseFexcore = "2605"
        val baseDriver = "Turnip"
        val baseDriverConfig = "default"
        val baseDxwrapper = "dxvk"
        val baseDxwrapperConfig = "1.11.1-sarek"

        // Helper for 5-level precedence evaluation
        fun <T> resolveField(
            fieldName: String,
            level1Val: T,
            level2Val: T?,
            level3Val: T?,
            isLevel3Exact: Boolean,
            isHardwareCritical: Boolean,
            level4Val: T?,
            level5Val: T?
        ): ResolvedSetting<T> {
            // Level 5: User Override (if specified and non-blank/valid)
            if (level5Val != null && (level5Val !is String || level5Val.isNotBlank())) {
                return ResolvedSetting(level5Val, SettingSource.USER_OVERRIDE, "User override set in container for $fieldName")
            }
            // Level 4: VR Mod Requirement
            if (level4Val != null && (level4Val !is String || level4Val.isNotBlank())) {
                return ResolvedSetting(level4Val, SettingSource.VR_MOD_REQUIREMENT, "VR mod mandatory requirement for $fieldName")
            }
            // Level 3: Compatibility Entry (Only apply hardware-critical settings if isLevel3Exact is true!)
            if (level3Val != null && (level3Val !is String || level3Val.isNotBlank())) {
                if (!isHardwareCritical || isLevel3Exact) {
                    return ResolvedSetting(level3Val, SettingSource.COMPATIBILITY_PROFILE, "Compatibility profile match (exact=$isLevel3Exact) for $fieldName")
                }
            }
            // Level 2: Quest Hardware Profile
            if (level2Val != null && (level2Val !is String || level2Val.isNotBlank())) {
                return ResolvedSetting(level2Val, SettingSource.HARDWARE_PROFILE, "Detected hardware profile (${hardwareInput.deviceDescriptor}) for $fieldName")
            }
            // Level 1: Baseline Default
            return ResolvedSetting(level1Val, SettingSource.BASE_DEFAULT, "Execution baseline default for $fieldName")
        }

        val isExact = compatibilityInput?.isExactMatch ?: false

        val variant = resolveField(
            "containerVariant", baseVariant, hardwareInput.containerVariant,
            compatibilityInput?.recommendedVariant, isExact, false, null, userContainer?.containerVariant
        )

        val wine = resolveField(
            "wineVersion", baseWine, hardwareInput.wineVersion,
            compatibilityInput?.recommendedWineVersion, isExact, false, null, userContainer?.wineVersion
        )

        val wow64 = resolveField(
            "wow64Mode", baseWow64, hardwareInput.wow64Mode,
            null, isExact, false, null, userContainer?.wow64Mode
        )

        val emu = resolveField(
            "emulator", baseEmulator, hardwareInput.emulator,
            null, isExact, false, null, userContainer?.emulator
        )

        val box64 = resolveField(
            "box64Version", baseBox64, hardwareInput.box64Version,
            null, isExact, false, null, userContainer?.box64Version
        )

        val fex = resolveField(
            "fexcoreVersion", baseFexcore, hardwareInput.fexcoreVersion,
            null, isExact, false, null, userContainer?.fexcoreVersion
        )

        val driver = resolveField(
            "graphicsDriver", baseDriver, hardwareInput.graphicsDriver,
            compatibilityInput?.recommendedGraphicsDriver, isExact, true, null, userContainer?.graphicsDriver
        )

        val driverConfig = resolveField(
            "graphicsDriverConfig", baseDriverConfig, hardwareInput.graphicsDriverConfig,
            compatibilityInput?.recommendedGraphicsDriverConfig, isExact, true, null, userContainer?.graphicsDriverConfig
        )

        val dxwrap = resolveField(
            "dxwrapper", baseDxwrapper, hardwareInput.dxwrapper,
            compatibilityInput?.recommendedDxwrapper, isExact, true, null, userContainer?.dxwrapper
        )

        val dxwrapConfig = resolveField(
            "dxwrapperConfig", baseDxwrapperConfig, hardwareInput.dxwrapperConfig,
            compatibilityInput?.recommendedDxwrapperConfig, isExact, true, null, userContainer?.dxwrapperConfig
        )

        // Merge Environment Variables (Level 1..5 order: Level 3 Compat -> Level 4 VR Mod -> Level 5 User Container)
        val mergedEnv = mutableMapOf<String, String>()
        compatibilityInput?.recommendedEnvVars?.let { mergedEnv.putAll(it) }
        modInput?.requiredEnvVars?.let { mergedEnv.putAll(it) }
        userContainer?.envVars?.let { envStr ->
            if (envStr.isNotBlank()) {
                val userEnv = com.winlator.core.envvars.EnvVars(envStr)
                for (key in userEnv) {
                    mergedEnv[key] = userEnv.get(key)
                }
            }
        }

        // Merge DLL Overrides
        val mergedDlls = mutableMapOf<String, String>()
        modInput?.requiredDllOverrides?.let { mergedDlls.putAll(it) }

        // Resolve tracking mode
        val resolvedTracking = when {
            modInput != null -> "MODDED_6DOF"
            exeIdentity.hasOpenXRImport || exeIdentity.hasOpenVRImport -> "NATIVE_OPENXR_6DOF"
            else -> "FLAT_3DOF"
        }

        return LaunchPlan(
            launchId = request.launchId,
            request = request,
            executableIdentity = exeIdentity,
            detectedDeviceDescriptor = hardwareInput.deviceDescriptor,
            containerVariant = variant,
            wineVersion = wine,
            wow64Mode = wow64,
            emulator = emu,
            box64Version = box64,
            fexcoreVersion = fex,
            graphicsDriver = driver,
            graphicsDriverConfig = driverConfig,
            dxwrapper = dxwrap,
            dxwrapperConfig = dxwrapConfig,
            resolvedEnvVars = mergedEnv,
            resolvedDllOverrides = mergedDlls,
            trackingMode = resolvedTracking,
            activeModId = modInput?.modId,
            resolvedCommandArgs = request.customExecArgs ?: ""
        )
    }
}
