package app.gamenative.hardware

import com.winlator.container.ContainerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestHardwareChallengerTest {

    private lateinit var detector: QuestDeviceDetectorImpl
    private lateinit var resolver: HardwareExecutionProfileResolverImpl

    private val sampleQuest2Json = """
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

    private val sampleQuest3Json = """
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

    private val sampleDefaultBaselineJson = """
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
        detector = QuestDeviceDetectorImpl()
        val assetProvider = object : ProfileAssetProvider {
            override fun getProfileJson(profileId: String): String? {
                return when (profileId) {
                    "quest_2" -> sampleQuest2Json
                    "quest_3" -> sampleQuest3Json
                    "default_baseline" -> sampleDefaultBaselineJson
                    else -> null
                }
            }
        }
        resolver = HardwareExecutionProfileResolverImpl(assetProvider)
    }

    @Test
    fun stressTest_quest2TokensAndAdreno6xxGpus() {
        val q2Models = listOf("hollywood", "Quest 2", "oculus quest 2", "miramar", "quest2")
        val adreno6xxGpus = listOf("Adreno (TM) 650", "Adreno (TM) 640", "Adreno 660", "Adreno 690")

        for (model in q2Models) {
            for (gpu in adreno6xxGpus) {
                val facts = QuestRawHardwareFacts(
                    buildManufacturer = "Oculus",
                    buildBrand = "Oculus",
                    buildModel = model,
                    buildDevice = model,
                    buildProduct = model,
                    buildHardware = "qcom",
                    socManufacturer = "Qualcomm",
                    socModel = "SM8250",
                    glVendor = "Qualcomm",
                    glRenderer = gpu,
                    glVersion = "OpenGL ES 3.2",
                    supportedAbis = listOf("arm64-v8a"),
                    androidRelease = "10",
                    sdkInt = 29,
                    securityPatch = "2021-01-01",
                    isMetaXrRuntime = true
                )

                val descriptor = detector.classifyFacts(facts)
                assertEquals("Failed for model $model and gpu $gpu", ClassifiedQuestDevice.QUEST_2, descriptor.classifiedDevice)
                assertEquals("RULE_QUEST_2_EXACT", descriptor.matchedRuleId)
                assertEquals(1.0, descriptor.confidence, 0.001)
            }
        }
    }

    @Test
    fun stressTest_quest3TokensAndAdreno7xxGpus() {
        val q3Models = listOf("eureka", "Quest 3", "oculus quest 3", "quest3", "Meta Quest 3S")
        val adreno7xxGpus = listOf("Adreno (TM) 740", "Adreno (TM) 730", "Adreno 750")

        for (model in q3Models) {
            for (gpu in adreno7xxGpus) {
                val facts = QuestRawHardwareFacts(
                    buildManufacturer = "Meta",
                    buildBrand = "Meta",
                    buildModel = model,
                    buildDevice = model,
                    buildProduct = model,
                    buildHardware = "qcom",
                    socManufacturer = "Qualcomm",
                    socModel = "SM8550",
                    glVendor = "Qualcomm",
                    glRenderer = gpu,
                    glVersion = "OpenGL ES 3.2",
                    supportedAbis = listOf("arm64-v8a"),
                    androidRelease = "12",
                    sdkInt = 32,
                    securityPatch = "2023-10-01",
                    isMetaXrRuntime = true
                )

                val descriptor = detector.classifyFacts(facts)
                assertEquals("Failed for model $model and gpu $gpu", ClassifiedQuestDevice.QUEST_3, descriptor.classifiedDevice)
                assertEquals("RULE_QUEST_3_EXACT", descriptor.matchedRuleId)
                assertEquals(1.0, descriptor.confidence, 0.001)
            }
        }
    }

    @Test
    fun stressTest_conflictingFacts_quest2ModelWithAdreno7xx_returnsUnknownMeta() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Oculus",
            buildBrand = "Oculus",
            buildModel = "Quest 2",
            buildDevice = "hollywood",
            buildProduct = "hollywood",
            buildHardware = "qcom",
            socManufacturer = "Qualcomm",
            socModel = "SM8250",
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 740", // Conflicting GPU!
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "10",
            sdkInt = 29,
            securityPatch = "2021-01-01",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)
        assertEquals(ClassifiedQuestDevice.UNKNOWN_META, descriptor.classifiedDevice)
        assertEquals("RULE_UNKNOWN_META_FALLBACK", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
    }

    @Test
    fun stressTest_nonQuestDevices_returnsNotQuest() {
        val nonQuestFactsList = listOf(
            QuestRawHardwareFacts("Samsung", "samsung", "SM-S918B", "dm3q", "dm3q", "qcom", "Qualcomm", "Snapdragon 8 Gen 2", "Qualcomm", "Adreno (TM) 740", "OpenGL ES 3.2", listOf("arm64-v8a"), "14", 34, "2024-01-01", false),
            QuestRawHardwareFacts("Google", "google", "Pixel 8 Pro", "husky", "husky", "tensor", "Google", "Tensor G3", "ARM", "Mali-G715", "OpenGL ES 3.2", listOf("arm64-v8a"), "14", 34, "2024-01-01", false),
            QuestRawHardwareFacts("Xiaomi", "xiaomi", "2304FPN6DC", "ishtar", "ishtar", "qcom", "Qualcomm", "SM8550", "Qualcomm", "Adreno (TM) 740", "OpenGL ES 3.2", listOf("arm64-v8a"), "13", 33, "2023-08-01", false)
        )

        for (facts in nonQuestFactsList) {
            val descriptor = detector.classifyFacts(facts)
            assertEquals("Failed for ${facts.buildManufacturer} ${facts.buildModel}", ClassifiedQuestDevice.NOT_QUEST, descriptor.classifiedDevice)
            assertEquals("RULE_NOT_QUEST", descriptor.matchedRuleId)
            assertEquals(0.0, descriptor.confidence, 0.001)
        }
    }

    @Test
    fun stressTest_unknownMetaDevices_returnsUnknownMeta() {
        val unknownMetaFactsList = listOf(
            QuestRawHardwareFacts("Meta", "Meta", "Quest Pro", "seacliff", "seacliff", "qcom", "Qualcomm", "SM8350", "Qualcomm", "Adreno (TM) 685", "OpenGL ES 3.2", listOf("arm64-v8a"), "12", 31, "2022-10-01", true),
            QuestRawHardwareFacts("Oculus", "Oculus", "Pacific", "pacific", "pacific", "qcom", "Qualcomm", "SDM670", "Qualcomm", "Adreno (TM) 615", "OpenGL ES 3.2", listOf("arm64-v8a"), "7.1", 25, "2018-05-01", false)
        )

        for (facts in unknownMetaFactsList) {
            val descriptor = detector.classifyFacts(facts)
            assertEquals("Failed for ${facts.buildModel}", ClassifiedQuestDevice.UNKNOWN_META, descriptor.classifiedDevice)
            assertEquals("RULE_UNKNOWN_META_FALLBACK", descriptor.matchedRuleId)
            assertEquals(0.0, descriptor.confidence, 0.001)
        }
    }

    @Test
    fun stressTest_profileResolutionAndApplyToContainerData_preservesAllPerformanceAndCustomFields() {
        val descriptor = detector.classifyFacts(
            QuestRawHardwareFacts(
                buildManufacturer = "Meta",
                buildBrand = "Meta",
                buildModel = "Quest 3",
                buildDevice = "eureka",
                buildProduct = "eureka",
                buildHardware = "qcom",
                socManufacturer = "Qualcomm",
                socModel = "SM8550",
                glVendor = "Qualcomm",
                glRenderer = "Adreno (TM) 740",
                glVersion = "OpenGL ES 3.2",
                supportedAbis = listOf("arm64-v8a"),
                androidRelease = "12",
                sdkInt = 32,
                securityPatch = "2023-10-01",
                isMetaXrRuntime = true
            )
        )

        val resolution = resolver.resolveProfile(descriptor)
        assertEquals("quest_3", resolution.profile.profileId)
        assertEquals(ProfileResolutionSource.EXACT_MATCH, resolution.resolutionSource)

        val baseContainer = ContainerData(
            name = "MyCustomGameContainer",
            screenSize = "1920x1080",
            envVars = "MESA_GL_VERSION_OVERRIDE=4.5",
            graphicsDriver = "OldDriver",
            graphicsDriverVersion = "1.0",
            graphicsDriverConfig = "OldConfig",
            rendererPresentMode = "mailbox",
            displayRenderer = "virgl",
            sfCompatMode = false,
            dxwrapper = "wined3d",
            dxwrapperConfig = "",
            audioDriver = "alsa",
            pulseaudioLowLatency = true,
            wincomponents = "directx",
            drives = "D: /sdcard",
            execArgs = "-vulkan",
            executablePath = "C:\\game.exe",
            installPath = "/sdcard/games",
            showFPS = true,
            launchRealSteam = false,
            launchBionicSteam = true,
            allowSteamUpdates = false,
            steamType = "bionic",
            cpuList = "0,1,2,3",
            cpuListWoW64 = "4,5,6,7",
            wow64Mode = false,
            startupSelection = 1,
            box86Version = "0.2.8",
            box64Version = "0.3.0",
            box86Preset = "SAFE",
            box64Preset = "SAFE",
            desktopTheme = "DARK",
            containerVariant = "glibc",
            wineVersion = "wine-8.0",
            emulator = "box64",
            fexcoreVersion = "",
            fexcoreTSOMode = "Disabled",
            fexcoreX87Mode = "Disabled",
            fexcoreMultiBlock = "Disabled",
            fexcorePreset = "SAFE",
            xrCPULevel = 95,
            xrGPULevel = 90,
            xrRefreshRate = 120
        )

        val updatedContainer = resolver.applyToContainerData(baseContainer, resolution)

        // 1. Check profile fields OVERWRITTEN correctly
        assertEquals("bionic", updatedContainer.containerVariant)
        assertEquals("proton-10.0-arm64ec-2", updatedContainer.wineVersion)
        assertEquals(true, updatedContainer.wow64Mode)
        assertEquals("FEXCore", updatedContainer.emulator)
        assertEquals("2605", updatedContainer.fexcoreVersion)
        assertEquals("Wrapper", updatedContainer.graphicsDriver)
        assertEquals("Turnip v26.2.0 R4", updatedContainer.graphicsDriverVersion)
        assertEquals("dxvk", updatedContainer.dxwrapper)
        assertEquals("version=2.4.1-gplasync,vkd3dVersion=2.14.1", updatedContainer.dxwrapperConfig)

        // 2. Check custom/performance fields UNTOUCHED
        assertEquals("MyCustomGameContainer", updatedContainer.name)
        assertEquals("1920x1080", updatedContainer.screenSize)
        assertEquals("MESA_GL_VERSION_OVERRIDE=4.5", updatedContainer.envVars)
        assertEquals("mailbox", updatedContainer.rendererPresentMode)
        assertEquals("virgl", updatedContainer.displayRenderer)
        assertEquals(false, updatedContainer.sfCompatMode)
        assertEquals("alsa", updatedContainer.audioDriver)
        assertEquals(true, updatedContainer.pulseaudioLowLatency)
        assertEquals("directx", updatedContainer.wincomponents)
        assertEquals("D: /sdcard", updatedContainer.drives)
        assertEquals("-vulkan", updatedContainer.execArgs)
        assertEquals("C:\\game.exe", updatedContainer.executablePath)
        assertEquals("/sdcard/games", updatedContainer.installPath)
        assertEquals(true, updatedContainer.showFPS)
        assertEquals(true, updatedContainer.launchBionicSteam)
        assertEquals("0,1,2,3", updatedContainer.cpuList)
        assertEquals("4,5,6,7", updatedContainer.cpuListWoW64)
        assertEquals("DARK", updatedContainer.desktopTheme)

        // Performance policy fields
        assertEquals(95, updatedContainer.xrCPULevel)
        assertEquals(90, updatedContainer.xrGPULevel)
        assertEquals(120, updatedContainer.xrRefreshRate)
    }
}
