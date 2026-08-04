package app.gamenative.hardware

import com.winlator.container.ContainerData
import org.json.JSONArray
import org.json.JSONObject

class HardwareExecutionProfileResolverImpl(
    private val assetProvider: ProfileAssetProvider
) : HardwareExecutionProfileResolver {

    override fun resolveProfile(descriptor: QuestDeviceDescriptor): HardwareProfileResolutionResult {
        val (targetProfileId, resolutionSource) = when (descriptor.classifiedDevice) {
            ClassifiedQuestDevice.QUEST_2 -> "quest_2" to ProfileResolutionSource.EXACT_MATCH
            ClassifiedQuestDevice.QUEST_3 -> "quest_3" to ProfileResolutionSource.EXACT_MATCH
            ClassifiedQuestDevice.UNKNOWN_META -> "default_baseline" to ProfileResolutionSource.UNQUALIFIED_METADATA_FALLBACK
            ClassifiedQuestDevice.NOT_QUEST -> "default_baseline" to ProfileResolutionSource.DEFAULT_BASELINE_FALLBACK
        }

        val jsonString = assetProvider.getProfileJson(targetProfileId)
            ?: throw IllegalStateException("Failed to load profile asset '$targetProfileId'")

        val profile = parseProfileJson(jsonString)
        val validationErrors = profile.validate()
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid profile '$targetProfileId': ${validationErrors.joinToString()}")
        }
        require(profile.profileId == targetProfileId) {
            "Profile asset '$targetProfileId' declared profileId '${profile.profileId}'"
        }
        require(descriptor.matchedRuleId in profile.acceptedRuleIds) {
            "Profile '$targetProfileId' does not accept detected rule '${descriptor.matchedRuleId}'"
        }

        val fieldDecisions = buildFieldDecisions(profile, descriptor, resolutionSource)

        return HardwareProfileResolutionResult(
            descriptor = descriptor,
            profile = profile,
            resolutionSource = resolutionSource,
            fieldDecisions = fieldDecisions
        )
    }

    override fun applyToContainerData(base: ContainerData, result: HardwareProfileResolutionResult): ContainerData {
        val p = result.profile
        return base.copy(
            containerVariant = p.containerVariant,
            wineVersion = p.wineVersion,
            wow64Mode = p.wow64Mode,
            emulator = p.emulator,
            fexcoreVersion = p.fexcoreVersion,
            fexcoreTSOMode = p.fexcoreTSOMode,
            fexcoreX87Mode = p.fexcoreX87Mode,
            fexcoreMultiBlock = p.fexcoreMultiBlock,
            fexcorePreset = p.fexcorePreset,
            box86Version = p.box86Version,
            box64Version = p.box64Version,
            box86Preset = p.box86Preset,
            box64Preset = p.box64Preset,
            graphicsDriver = p.graphicsDriver,
            graphicsDriverVersion = p.graphicsDriverVersion,
            graphicsDriverConfig = p.graphicsDriverConfig,
            dxwrapper = p.dxwrapper,
            dxwrapperConfig = p.dxwrapperConfig
        )
    }

    private fun parseProfileJson(jsonString: String): HardwareExecutionProfile {
        val obj = JSONObject(jsonString)
        val profileId = obj.getString("profileId")
        val schemaVersion = obj.getInt("schemaVersion")

        val acceptedRuleIdsArray = obj.optJSONArray("acceptedRuleIds") ?: JSONArray()
        val acceptedRuleIds = mutableListOf<String>()
        for (i in 0 until acceptedRuleIdsArray.length()) {
            acceptedRuleIds.add(acceptedRuleIdsArray.getString(i))
        }

        val componentsArray = obj.optJSONArray("requiredPackagedComponents") ?: JSONArray()
        val components = mutableListOf<RequiredComponentSpec>()
        for (i in 0 until componentsArray.length()) {
            val cObj = componentsArray.getJSONObject(i)
            components.add(
                RequiredComponentSpec(
                    componentId = cObj.getString("componentId"),
                    expectedHashSha256 = if (cObj.has("expectedHashSha256") && !cObj.isNull("expectedHashSha256")) cObj.getString("expectedHashSha256") else null,
                    minVersion = if (cObj.has("minVersion") && !cObj.isNull("minVersion")) cObj.getString("minVersion") else null
                )
            )
        }

        return HardwareExecutionProfile(
            profileId = profileId,
            schemaVersion = schemaVersion,
            acceptedRuleIds = acceptedRuleIds,
            containerVariant = obj.getString("containerVariant"),
            wineVersion = obj.getString("wineVersion"),
            wow64Mode = obj.getBoolean("wow64Mode"),
            emulator = obj.getString("emulator"),
            fexcoreVersion = obj.getString("fexcoreVersion"),
            fexcoreTSOMode = obj.optString("fexcoreTSOMode", "Fast"),
            fexcoreX87Mode = obj.optString("fexcoreX87Mode", "Fast"),
            fexcoreMultiBlock = obj.optString("fexcoreMultiBlock", "Disabled"),
            fexcorePreset = obj.optString("fexcorePreset", "INTERMEDIATE"),
            box86Version = obj.optString("box86Version", "0.3.2"),
            box64Version = obj.optString("box64Version", "0.4.2"),
            box86Preset = obj.optString("box86Preset", "COMPATIBILITY"),
            box64Preset = obj.optString("box64Preset", "COMPATIBILITY"),
            graphicsDriver = obj.getString("graphicsDriver"),
            graphicsDriverVersion = obj.getString("graphicsDriverVersion"),
            graphicsDriverConfig = obj.optString("graphicsDriverConfig", ""),
            dxwrapper = obj.getString("dxwrapper"),
            dxwrapperConfig = obj.getString("dxwrapperConfig"),
            requiredPackagedComponents = components
        )
    }

    private fun buildFieldDecisions(
        profile: HardwareExecutionProfile,
        descriptor: QuestDeviceDescriptor,
        source: ProfileResolutionSource
    ): Map<String, ProfileFieldDecision> {
        val rationalePrefix = "Resolved from profile '${profile.profileId}' via rule '${descriptor.matchedRuleId}' ($source)"
        val fields = mapOf(
            "containerVariant" to profile.containerVariant,
            "wineVersion" to profile.wineVersion,
            "wow64Mode" to profile.wow64Mode.toString(),
            "emulator" to profile.emulator,
            "fexcoreVersion" to profile.fexcoreVersion,
            "fexcoreTSOMode" to profile.fexcoreTSOMode,
            "fexcoreX87Mode" to profile.fexcoreX87Mode,
            "fexcoreMultiBlock" to profile.fexcoreMultiBlock,
            "fexcorePreset" to profile.fexcorePreset,
            "box86Version" to profile.box86Version,
            "box64Version" to profile.box64Version,
            "box86Preset" to profile.box86Preset,
            "box64Preset" to profile.box64Preset,
            "graphicsDriver" to profile.graphicsDriver,
            "graphicsDriverVersion" to profile.graphicsDriverVersion,
            "graphicsDriverConfig" to profile.graphicsDriverConfig,
            "dxwrapper" to profile.dxwrapper,
            "dxwrapperConfig" to profile.dxwrapperConfig
        )
        return fields.mapValues { (fieldName, value) ->
            ProfileFieldDecision(
                fieldName = fieldName,
                value = value,
                sourceProfileId = profile.profileId,
                rationale = "$rationalePrefix for field $fieldName"
            )
        }
    }
}
