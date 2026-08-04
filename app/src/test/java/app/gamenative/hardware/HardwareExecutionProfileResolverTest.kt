package app.gamenative.hardware

import com.winlator.container.ContainerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HardwareExecutionProfileResolverTest {

    private lateinit var resolver: HardwareExecutionProfileResolverImpl
    private lateinit var mockAssetProvider: ProfileAssetProvider

    private val quest2Json = """
        {
          "profileId": "quest_2",
          "schemaVersion": 1,
          "acceptedRuleIds": ["RULE_QUEST_2_EXACT"],
          "containerVariant": "bionic",
          "wineVersion": "proton-10.0-arm64ec-2",
          "wow64Mode": true,
          "emulator": "FEXCore",
          "fexcoreVersion": "2605",
          "fexcoreTSOMode": "Fast",
          "fexcoreX87Mode": "Fast",
          "fexcoreMultiBlock": "Disabled",
          "fexcorePreset": "INTERMEDIATE",
          "box86Version": "0.3.2",
          "box64Version": "0.4.2",
          "box86Preset": "COMPATIBILITY",
          "box64Preset": "COMPATIBILITY",
          "graphicsDriver": "Wrapper",
          "graphicsDriverVersion": "Turnip v26.2.0 R4",
          "graphicsDriverConfig": "",
          "dxwrapper": "dxvk",
          "dxwrapperConfig": "version=1.11.1-sarek,vkd3dVersion=2.14.1"
        }
    """.trimIndent()

    private val quest3Json = """
        {
          "profileId": "quest_3",
          "schemaVersion": 1,
          "acceptedRuleIds": ["RULE_QUEST_3_EXACT"],
          "containerVariant": "bionic",
          "wineVersion": "proton-10.0-arm64ec-2",
          "wow64Mode": true,
          "emulator": "FEXCore",
          "fexcoreVersion": "2605",
          "fexcoreTSOMode": "Fast",
          "fexcoreX87Mode": "Fast",
          "fexcoreMultiBlock": "Disabled",
          "fexcorePreset": "INTERMEDIATE",
          "box86Version": "0.3.2",
          "box64Version": "0.4.2",
          "box86Preset": "COMPATIBILITY",
          "box64Preset": "COMPATIBILITY",
          "graphicsDriver": "Wrapper",
          "graphicsDriverVersion": "Turnip v26.2.0 R4",
          "graphicsDriverConfig": "",
          "dxwrapper": "dxvk",
          "dxwrapperConfig": "version=2.4.1-gplasync,vkd3dVersion=2.14.1"
        }
    """.trimIndent()

    private val defaultBaselineJson = """
        {
          "profileId": "default_baseline",
          "schemaVersion": 1,
          "acceptedRuleIds": ["RULE_UNKNOWN_META_FALLBACK", "RULE_NOT_QUEST"],
          "containerVariant": "bionic",
          "wineVersion": "proton-10.0-arm64ec-2",
          "wow64Mode": true,
          "emulator": "FEXCore",
          "fexcoreVersion": "2605",
          "fexcoreTSOMode": "Fast",
          "fexcoreX87Mode": "Fast",
          "fexcoreMultiBlock": "Disabled",
          "fexcorePreset": "INTERMEDIATE",
          "box86Version": "0.3.2",
          "box64Version": "0.4.2",
          "box86Preset": "COMPATIBILITY",
          "box64Preset": "COMPATIBILITY",
          "graphicsDriver": "Wrapper",
          "graphicsDriverVersion": "Turnip v26.2.0 R4",
          "graphicsDriverConfig": "",
          "dxwrapper": "dxvk",
          "dxwrapperConfig": "version=1.11.1-sarek,vkd3dVersion=2.14.1"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        mockAssetProvider = object : ProfileAssetProvider {
            override fun getProfileJson(profileId: String): String? {
                return when (profileId) {
                    "quest_2" -> quest2Json
                    "quest_3" -> quest3Json
                    "default_baseline" -> defaultBaselineJson
                    else -> null
                }
            }
        }
        resolver = HardwareExecutionProfileResolverImpl(mockAssetProvider)
    }

    @Test
    fun resolveProfile_resolvesQuest2Profile_forQuest2Descriptor() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            gpuRenderer = "Adreno (TM) 650",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_2,
            matchedRuleId = "RULE_QUEST_2_EXACT",
            confidence = 1.0
        )

        val result = resolver.resolveProfile(descriptor)

        assertEquals("quest_2", result.profile.profileId)
        assertEquals(ProfileResolutionSource.EXACT_MATCH, result.resolutionSource)
        assertEquals("version=1.11.1-sarek,vkd3dVersion=2.14.1", result.profile.dxwrapperConfig)
        assertNotNull(result.fieldDecisions["graphicsDriver"])
    }

    @Test
    fun resolveProfile_resolvesQuest3Profile_forQuest3Descriptor() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Meta",
            buildModel = "Quest 3",
            buildDevice = "eureka",
            buildProduct = "eureka",
            socManufacturer = "Qualcomm",
            socModel = "SM8550",
            gpuRenderer = "Adreno (TM) 740",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_3,
            matchedRuleId = "RULE_QUEST_3_EXACT",
            confidence = 1.0
        )

        val result = resolver.resolveProfile(descriptor)

        assertEquals("quest_3", result.profile.profileId)
        assertEquals(ProfileResolutionSource.EXACT_MATCH, result.resolutionSource)
        assertEquals("version=2.4.1-gplasync,vkd3dVersion=2.14.1", result.profile.dxwrapperConfig)
    }

    @Test
    fun resolveProfile_fallsBackToDefaultBaseline_forUnknownMetaDescriptor() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Meta",
            buildModel = "Unknown Prototype",
            buildDevice = "unknown",
            buildProduct = "unknown",
            socManufacturer = null,
            socModel = null,
            gpuRenderer = "Adreno (TM) 750",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.UNKNOWN_META,
            matchedRuleId = "RULE_UNKNOWN_META_FALLBACK",
            confidence = 0.0
        )

        val result = resolver.resolveProfile(descriptor)

        assertEquals("default_baseline", result.profile.profileId)
        assertEquals(ProfileResolutionSource.UNQUALIFIED_METADATA_FALLBACK, result.resolutionSource)
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolveProfile_rejectsAProfileWhoseRulesDoNotMatchTheDetectedDevice() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            gpuRenderer = "Adreno (TM) 650",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_2,
            matchedRuleId = "RULE_UNEXPECTED",
            confidence = 1.0
        )

        resolver.resolveProfile(descriptor)
    }

    @Test
    fun applyToContainerData_createsNewInstanceWithProfileParameters_withoutMutatingOriginalOrPerformancePolicy() {
        val descriptor = QuestDeviceDescriptor(
            buildManufacturer = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            gpuRenderer = "Adreno (TM) 650",
            isMetaXrRuntime = true,
            classifiedDevice = ClassifiedQuestDevice.QUEST_2,
            matchedRuleId = "RULE_QUEST_2_EXACT",
            confidence = 1.0
        )

        val result = resolver.resolveProfile(descriptor)

        val baseContainer = ContainerData(
            name = "TestContainer",
            screenSize = "1280x720",
            envVars = "",
            graphicsDriver = "OldDriver",
            graphicsDriverVersion = "",
            graphicsDriverConfig = "",
            rendererPresentMode = "fifo",
            displayRenderer = "vulkan",
            sfCompatMode = true,
            dxwrapper = "dxvk",
            dxwrapperConfig = "OldConfig",
            audioDriver = "pulseaudio",
            pulseaudioLowLatency = false,
            wincomponents = "",
            drives = "",
            execArgs = "",
            executablePath = "",
            installPath = "",
            showFPS = false,
            launchRealSteam = false,
            launchBionicSteam = false,
            allowSteamUpdates = false,
            steamType = "normal",
            cpuList = "0,1",
            cpuListWoW64 = "0,1",
            wow64Mode = true,
            startupSelection = 0,
            box86Version = "0.3.2",
            box64Version = "0.4.2",
            box86Preset = "COMPATIBILITY",
            box64Preset = "COMPATIBILITY",
            desktopTheme = "LIGHT,IMAGE,#0277bd",
            containerVariant = "bionic",
            wineVersion = "proton-10.0",
            emulator = "FEXCore",
            fexcoreVersion = "2605",
            fexcoreTSOMode = "Fast",
            fexcoreX87Mode = "Fast",
            fexcoreMultiBlock = "Disabled",
            fexcorePreset = "INTERMEDIATE",
            xrCPULevel = 80, // Performance policy field
            xrRefreshRate = 90 // Performance policy field
        )

        val updatedContainer = resolver.applyToContainerData(baseContainer, result)

        // Verify original is untouched
        assertEquals("OldDriver", baseContainer.graphicsDriver)
        assertEquals("OldConfig", baseContainer.dxwrapperConfig)

        // Verify updated container has profile fields
        assertEquals("Wrapper", updatedContainer.graphicsDriver)
        assertEquals("version=1.11.1-sarek,vkd3dVersion=2.14.1", updatedContainer.dxwrapperConfig)

        // Verify performance policy fields REMAIN UNCHANGED
        assertEquals(80, updatedContainer.xrCPULevel)
        assertEquals(90, updatedContainer.xrRefreshRate)
    }
}
