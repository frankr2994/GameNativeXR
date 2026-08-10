package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.install.ResolvedGameInstall
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
        resolvedInstall: ResolvedGameInstall,
        exeIdentity: ExecutableIdentity,
        hardwareInput: QuestHardwareProfileInput,
        compatibilityInput: CompatibilityResolutionInput?,
        modInput: VrModRequirementInput?,
        userContainer: ContainerData?
    ): LaunchPlan

    /** Compatibility overload for isolated planning callers that predate authoritative resolution. */
    fun resolvePlan(
        request: LaunchRequest,
        exeIdentity: ExecutableIdentity,
        hardwareInput: QuestHardwareProfileInput,
        compatibilityInput: CompatibilityResolutionInput?,
        modInput: VrModRequirementInput?,
        userContainer: ContainerData?
    ): LaunchPlan {
        val legacyRequest = request.requireInstallResolution()
        val candidate = legacyRequest.candidates.first()
        val root = candidate.root.absoluteFile
        val mount = app.gamenative.launch.install.GuestMountedRoot(candidate.guestDriveLetter!!, root)
        val relative = legacyRequest.selectedLaunchOption.executableRelativePath.replace('\\', '/')
        val install = ResolvedGameInstall(
            appId = request.appId,
            gameSource = request.gameSource,
            hostInstallRoot = app.gamenative.launch.install.HostInstallRoot(root),
            guestMountedRoot = mount,
            selectedExecutable = java.io.File(root, relative),
            executableRelativePath = relative,
            wineExecutablePath = app.gamenative.launch.install.WineExecutablePath(mount.resolveWinePath(relative)),
            wineWorkingDirectoryPath = app.gamenative.launch.install.WineWorkingDirectoryPath(
                mount.resolveWinePath(relative.substringBeforeLast('/', missingDelimiterValue = ".")),
            ),
            selectedLaunchOption = legacyRequest.selectedLaunchOption,
            evidence = emptyList()
        )
        return resolvePlan(request, install, exeIdentity, hardwareInput, compatibilityInput, modInput, userContainer)
    }
}

class LaunchPrecedenceResolverImpl : LaunchPrecedenceResolver {

    override fun resolvePlan(
        request: LaunchRequest,
        resolvedInstall: ResolvedGameInstall,
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
        val explicitContainerOverrideFields = request.explicitContainerOverrideFields
        fun <T> userOverride(field: String, value: T?): T? =
            if (explicitContainerOverrideFields == null || field in explicitContainerOverrideFields) value else null

        val variant = resolveField(
            "containerVariant", baseVariant, hardwareInput.containerVariant,
            compatibilityInput?.recommendedVariant, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.CONTAINER_VARIANT, userContainer?.containerVariant),
        )

        val wine = resolveField(
            "wineVersion", baseWine, hardwareInput.wineVersion,
            compatibilityInput?.recommendedWineVersion, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.WINE_VERSION, userContainer?.wineVersion),
        )

        val wow64 = resolveField(
            "wow64Mode", baseWow64, hardwareInput.wow64Mode,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.WOW64_MODE, userContainer?.wow64Mode),
        )

        val emu = resolveField(
            "emulator", baseEmulator, hardwareInput.emulator,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.EMULATOR, userContainer?.emulator),
        )

        val box64 = resolveField(
            "box64Version", baseBox64, hardwareInput.box64Version,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.BOX64_VERSION, userContainer?.box64Version),
        )

        val box86 = resolveField(
            "box86Version", "0.3.2", hardwareInput.box86Version,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.BOX86_VERSION, userContainer?.box86Version),
        )
        val box86Preset = resolveField(
            "box86Preset", "COMPATIBILITY", hardwareInput.box86Preset,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.BOX86_PRESET, userContainer?.box86Preset),
        )
        val box64Preset = resolveField(
            "box64Preset", "COMPATIBILITY", hardwareInput.box64Preset,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.BOX64_PRESET, userContainer?.box64Preset),
        )

        val fex = resolveField(
            "fexcoreVersion", baseFexcore, hardwareInput.fexcoreVersion,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.FEXCORE_VERSION, userContainer?.fexcoreVersion),
        )
        val fexTso = resolveField(
            "fexcoreTSOMode", "Fast", hardwareInput.fexcoreTSOMode,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.FEXCORE_TSO_MODE, userContainer?.fexcoreTSOMode),
        )
        val fexX87 = resolveField(
            "fexcoreX87Mode", "Fast", hardwareInput.fexcoreX87Mode,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.FEXCORE_X87_MODE, userContainer?.fexcoreX87Mode),
        )
        val fexMultiBlock = resolveField(
            "fexcoreMultiBlock", "Disabled", hardwareInput.fexcoreMultiBlock,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.FEXCORE_MULTI_BLOCK, userContainer?.fexcoreMultiBlock),
        )
        val fexPreset = resolveField(
            "fexcorePreset", "INTERMEDIATE", hardwareInput.fexcorePreset,
            null, isExact, false, null,
            userOverride(ContainerExecutionOverrideDetector.FEXCORE_PRESET, userContainer?.fexcorePreset),
        )

        val driver = resolveField(
            "graphicsDriver", baseDriver, hardwareInput.graphicsDriver,
            compatibilityInput?.recommendedGraphicsDriver, isExact, true, null,
            userOverride(ContainerExecutionOverrideDetector.GRAPHICS_DRIVER, userContainer?.graphicsDriver),
        )
        val driverVersion = resolveField(
            "graphicsDriverVersion", "", hardwareInput.graphicsDriverVersion,
            null, isExact, true, null,
            userOverride(ContainerExecutionOverrideDetector.GRAPHICS_DRIVER_VERSION, userContainer?.graphicsDriverVersion),
        )

        val driverConfig = resolveField(
            "graphicsDriverConfig", baseDriverConfig, hardwareInput.graphicsDriverConfig,
            compatibilityInput?.recommendedGraphicsDriverConfig, isExact, true, null,
            userOverride(ContainerExecutionOverrideDetector.GRAPHICS_DRIVER_CONFIG, userContainer?.graphicsDriverConfig),
        )

        val dxwrap = resolveField(
            "dxwrapper", baseDxwrapper, hardwareInput.dxwrapper,
            compatibilityInput?.recommendedDxwrapper, isExact, true, null,
            userOverride(ContainerExecutionOverrideDetector.DXWRAPPER, userContainer?.dxwrapper),
        )

        val dxwrapConfig = resolveField(
            "dxwrapperConfig", baseDxwrapperConfig, hardwareInput.dxwrapperConfig,
            compatibilityInput?.recommendedDxwrapperConfig, isExact, true, null,
            userOverride(ContainerExecutionOverrideDetector.DXWRAPPER_CONFIG, userContainer?.dxwrapperConfig),
        )

        // Resolve tracking mode
        val trackingResolver = app.gamenative.launch.vr.TrackingModeResolverImpl()
        val trackingDecision = trackingResolver.resolveTrackingMode(
            request = request,
            executableIdentity = exeIdentity,
            hasVrModManifest = modInput != null,
            isNativeVrSupported = exeIdentity.hasOpenXRImport || exeIdentity.hasOpenVRImport
        )
        val resolvedTracking = trackingDecision.mode
        val isModSupported = resolvedTracking == app.gamenative.launch.vr.TrackingMode.MODDED_6DOF
        val effectiveModInput = if (isModSupported) modInput else null

        // Merge Environment Variables (Level 1..5 order: Level 3 Compat -> Level 4 VR Mod -> Level 5 User Container)
        val mergedEnv = mutableMapOf<String, String>()
        compatibilityInput?.recommendedEnvVars?.let { mergedEnv.putAll(it) }
        effectiveModInput?.requiredEnvVars?.let { mergedEnv.putAll(it) }
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
        effectiveModInput?.requiredDllOverrides?.let { mergedDlls.putAll(it) }

        val requiredComponents = hardwareInput.requiredComponentIds.filterTo(linkedSetOf()) { componentId ->
            when {
                componentId.startsWith("wine_") -> wine.source == SettingSource.HARDWARE_PROFILE
                componentId.startsWith("turnip_") ->
                    driver.source == SettingSource.HARDWARE_PROFILE ||
                        driverVersion.source == SettingSource.HARDWARE_PROFILE ||
                        driverConfig.source == SettingSource.HARDWARE_PROFILE
                componentId.startsWith("fexcore_") ->
                    emu.source == SettingSource.HARDWARE_PROFILE ||
                        fex.source == SettingSource.HARDWARE_PROFILE ||
                        fexPreset.source == SettingSource.HARDWARE_PROFILE
                else -> true
            }
        }

        val executionConfig = ResolvedContainerExecutionConfig(
            containerVariant = variant.value,
            wineVersion = wine.value,
            wow64Mode = wow64.value,
            emulator = emu.value,
            fexcoreVersion = fex.value,
            fexcoreTSOMode = fexTso.value,
            fexcoreX87Mode = fexX87.value,
            fexcoreMultiBlock = fexMultiBlock.value,
            fexcorePreset = fexPreset.value,
            box86Version = box86.value,
            box64Version = box64.value,
            box86Preset = box86Preset.value,
            box64Preset = box64Preset.value,
            graphicsDriver = driver.value,
            graphicsDriverVersion = driverVersion.value,
            graphicsDriverConfig = driverConfig.value,
            dxwrapper = dxwrap.value,
            dxwrapperConfig = dxwrapConfig.value,
            environmentVariables = mergedEnv.toMap(),
            dllOverrides = mergedDlls.toMap(),
            requiredPackagedComponentIds = requiredComponents,
            trackingMode = resolvedTracking,
            activeModId = effectiveModInput?.modId
        )

        return LaunchPlan(
            launchId = request.launchId,
            request = request,
            resolvedInstall = resolvedInstall,
            executableIdentity = exeIdentity,
            detectedDeviceDescriptor = hardwareInput.deviceDescriptor,
            containerVariant = variant,
            wineVersion = wine,
            wow64Mode = wow64,
            emulator = emu,
            box64Version = box64,
            box86Version = box86,
            box86Preset = box86Preset,
            box64Preset = box64Preset,
            fexcoreVersion = fex,
            fexcoreTSOMode = fexTso,
            fexcoreX87Mode = fexX87,
            fexcoreMultiBlock = fexMultiBlock,
            fexcorePreset = fexPreset,
            graphicsDriver = driver,
            graphicsDriverVersion = driverVersion,
            graphicsDriverConfig = driverConfig,
            dxwrapper = dxwrap,
            dxwrapperConfig = dxwrapConfig,
            resolvedEnvVars = mergedEnv,
            resolvedDllOverrides = mergedDlls,
            trackingMode = resolvedTracking,
            executionConfig = executionConfig,
            activeModId = effectiveModInput?.modId,
            resolvedCommandArgs = request.customExecArgs ?: ""
        )
    }
}
