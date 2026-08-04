package app.gamenative.hardware

import android.content.Context
import app.gamenative.launch.LaunchHardwareProfileProvider
import app.gamenative.launch.QuestHardwareProfileInput

/**
 * Android-facing adapter between the Quest device/profile subsystem and the platform-neutral
 * launch coordinator. It resolves facts and a packaged profile for each launch; it does not
 * persist or mutate the user's container configuration.
 */
class QuestHardwareProfileLaunchInputProvider(
    context: Context,
    private val deviceDetector: QuestDeviceDetector = QuestDeviceDetectorImpl(),
    private val profileResolver: HardwareExecutionProfileResolver =
        HardwareExecutionProfileResolverImpl(AndroidAssetProfileProvider(context.applicationContext))
) : LaunchHardwareProfileProvider {
    private val appContext = context.applicationContext

    override suspend fun resolve(): QuestHardwareProfileInput {
        val descriptor = deviceDetector.detectDevice(appContext)
        return profileResolver.resolveProfile(descriptor).toLaunchInput()
    }
}

fun HardwareProfileResolutionResult.toLaunchInput(): QuestHardwareProfileInput {
    val resolvedProfile = profile
    return QuestHardwareProfileInput(
        profileId = resolvedProfile.profileId,
        deviceDescriptor = descriptor.classifiedDevice.name,
        matchedRuleId = descriptor.matchedRuleId,
        containerVariant = resolvedProfile.containerVariant,
        wineVersion = resolvedProfile.wineVersion,
        wow64Mode = resolvedProfile.wow64Mode,
        emulator = resolvedProfile.emulator,
        fexcoreTSOMode = resolvedProfile.fexcoreTSOMode,
        fexcoreX87Mode = resolvedProfile.fexcoreX87Mode,
        fexcoreMultiBlock = resolvedProfile.fexcoreMultiBlock,
        fexcorePreset = resolvedProfile.fexcorePreset,
        box86Version = resolvedProfile.box86Version,
        box64Version = resolvedProfile.box64Version,
        box86Preset = resolvedProfile.box86Preset,
        box64Preset = resolvedProfile.box64Preset,
        fexcoreVersion = resolvedProfile.fexcoreVersion,
        graphicsDriver = resolvedProfile.graphicsDriver,
        graphicsDriverVersion = resolvedProfile.graphicsDriverVersion,
        graphicsDriverConfig = resolvedProfile.graphicsDriverConfig,
        dxwrapper = resolvedProfile.dxwrapper,
        dxwrapperConfig = resolvedProfile.dxwrapperConfig,
        requiredComponentIds = resolvedProfile.requiredPackagedComponents.map { it.componentId }
    )
}
