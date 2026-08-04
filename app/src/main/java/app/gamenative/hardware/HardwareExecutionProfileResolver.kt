package app.gamenative.hardware

import com.winlator.container.ContainerData

enum class ProfileResolutionSource {
    EXACT_MATCH,
    UNQUALIFIED_METADATA_FALLBACK,
    DEFAULT_BASELINE_FALLBACK
}

data class ProfileFieldDecision(
    val fieldName: String,
    val value: String,
    val sourceProfileId: String,
    val rationale: String
)

data class HardwareProfileResolutionResult(
    val descriptor: QuestDeviceDescriptor,
    val profile: HardwareExecutionProfile,
    val resolutionSource: ProfileResolutionSource,
    val fieldDecisions: Map<String, ProfileFieldDecision>
)

interface ProfileAssetProvider {
    fun getProfileJson(profileId: String): String?
}

interface HardwareExecutionProfileResolver {
    fun resolveProfile(descriptor: QuestDeviceDescriptor): HardwareProfileResolutionResult
    fun applyToContainerData(base: ContainerData, result: HardwareProfileResolutionResult): ContainerData
}
