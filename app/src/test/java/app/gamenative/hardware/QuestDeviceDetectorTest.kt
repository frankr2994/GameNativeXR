package app.gamenative.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuestDeviceDetectorTest {

    private lateinit var detector: QuestDeviceDetectorImpl

    @Before
    fun setUp() {
        detector = QuestDeviceDetectorImpl()
    }

    @Test
    fun classifyFacts_returnsQuest2_forHollywoodModelAndAdreno650() {
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
            glRenderer = "Adreno (TM) 650",
            glVersion = "OpenGL ES 3.2 V@0502.0",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "10",
            sdkInt = 29,
            securityPatch = "2021-01-01",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.QUEST_2, descriptor.classifiedDevice)
        assertEquals("RULE_QUEST_2_EXACT", descriptor.matchedRuleId)
        assertEquals(1.0, descriptor.confidence, 0.001)
        assertEquals("Oculus", descriptor.buildManufacturer)
        assertEquals("Adreno (TM) 650", descriptor.gpuRenderer)
    }

    @Test
    fun classifyFacts_returnsQuest3_forEurekaModelAndAdreno740() {
        val facts = QuestRawHardwareFacts(
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
            glVersion = "OpenGL ES 3.2 V@0700.0",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "12",
            sdkInt = 32,
            securityPatch = "2023-10-01",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.QUEST_3, descriptor.classifiedDevice)
        assertEquals("RULE_QUEST_3_EXACT", descriptor.matchedRuleId)
        assertEquals(1.0, descriptor.confidence, 0.001)
    }

    @Test
    fun classifyFacts_returnsNotQuest_forSamsungPhone() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Samsung",
            buildBrand = "samsung",
            buildModel = "SM-G998B",
            buildDevice = "p3s",
            buildProduct = "p3s",
            buildHardware = "exynos2100",
            socManufacturer = "Samsung",
            socModel = "Exynos 2100",
            glVendor = "ARM",
            glRenderer = "Mali-G78",
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "13",
            sdkInt = 33,
            securityPatch = "2023-05-01",
            isMetaXrRuntime = false
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.NOT_QUEST, descriptor.classifiedDevice)
        assertEquals("RULE_NOT_QUEST", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
        assertTrue(descriptor.rejectedRules.contains("RULE_QUEST_2_EXACT"))
        assertTrue(descriptor.rejectedRules.contains("RULE_QUEST_3_EXACT"))
    }

    @Test
    fun classifyFacts_returnsUnknownMeta_whenQuest3ModelHasAdreno6xxGPU() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Meta",
            buildBrand = "Meta",
            buildModel = "Quest 3",
            buildDevice = "eureka",
            buildProduct = "eureka",
            buildHardware = "qcom",
            socManufacturer = null,
            socModel = null,
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 650", // Conflicting GPU!
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "12",
            sdkInt = 31,
            securityPatch = "",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        // Must NOT infer Quest 3 when GPU is Adreno 6xx!
        assertEquals(ClassifiedQuestDevice.UNKNOWN_META, descriptor.classifiedDevice)
        assertEquals("RULE_UNKNOWN_META_FALLBACK", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
    }

    @Test
    fun classifyFacts_returnsUnknownMeta_forUnrecognizedMetaModel() {
        val facts = QuestRawHardwareFacts(
            buildManufacturer = "Meta",
            buildBrand = "Meta",
            buildModel = "Quest Pro Prototype",
            buildDevice = "future_device",
            buildProduct = "future_device",
            buildHardware = "qcom",
            socManufacturer = null,
            socModel = null,
            glVendor = "Qualcomm",
            glRenderer = "Adreno (TM) 830",
            glVersion = "OpenGL ES 3.2",
            supportedAbis = listOf("arm64-v8a"),
            androidRelease = "14",
            sdkInt = 34,
            securityPatch = "",
            isMetaXrRuntime = true
        )

        val descriptor = detector.classifyFacts(facts)

        assertEquals(ClassifiedQuestDevice.UNKNOWN_META, descriptor.classifiedDevice)
        assertEquals("RULE_UNKNOWN_META_FALLBACK", descriptor.matchedRuleId)
        assertEquals(0.0, descriptor.confidence, 0.001)
    }
}
