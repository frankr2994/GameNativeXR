package app.gamenative.hardware.quest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestProfileResolverTest {

    private fun createBaseDescriptor(
        manufacturer: String = "Oculus",
        model: String = "Quest 2",
        device: String = "hollywood",
        glRenderer: String? = "Adreno (TM) 650",
        isMetaQuestRuntime: Boolean = false
    ) = QuestDeviceDescriptor(
        manufacturer = manufacturer,
        brand = "Oculus",
        model = model,
        device = device,
        product = "hollywood",
        hardware = "qcom",
        socManufacturer = "Qualcomm",
        socModel = "XR2",
        glRenderer = glRenderer,
        glVendor = "Qualcomm",
        supportedAbis = listOf("arm64-v8a"),
        androidVersion = 29,
        securityPatch = "2023-11-01",
        isMetaQuestRuntime = isMetaQuestRuntime
    )

    @Test
    fun `test valid Quest 2 descriptor resolves to quest2 profile`() {
        val descriptor = createBaseDescriptor()
        val report = QuestProfileResolver.resolve(descriptor)
        
        assertTrue(report.isSupported)
        assertNotNull(report.profile)
        assertEquals("quest2", report.profile?.id)
        assertEquals("QUEST2_RULE_1", report.ruleId)
        assertTrue(report.rejectedRules.isEmpty())
    }

    @Test
    fun `test non-Meta manufacturer rejects resolution`() {
        val descriptor = createBaseDescriptor(manufacturer = "Samsung")
        val report = QuestProfileResolver.resolve(descriptor)
        
        assertFalse(report.isSupported)
        assertNull(report.profile)
        assertEquals("UNKNOWN_META", report.ruleId)
        assertTrue(report.rejectedRules.contains("NOT_META_MANUFACTURER"))
    }

    @Test
    fun `test valid Quest 2 descriptor but missing Adreno 6xx GPU rejects resolution`() {
        val descriptor = createBaseDescriptor(glRenderer = "Mali-G710")
        val report = QuestProfileResolver.resolve(descriptor)
        
        assertFalse(report.isSupported)
        assertNull(report.profile)
        assertEquals("UNKNOWN_META", report.ruleId)
        assertTrue(report.rejectedRules.contains("QUEST2_GPU_MISMATCH"))
    }

    @Test
    fun `test Quest 3 descriptor remains unpromoted`() {
        val descriptor = createBaseDescriptor(
            model = "Quest 3", 
            device = "eureka", 
            glRenderer = "Adreno (TM) 740"
        )
        val report = QuestProfileResolver.resolve(descriptor)
        
        assertFalse(report.isSupported)
        assertNull(report.profile)
        assertEquals("UNKNOWN_META", report.ruleId)
        assertTrue(report.rejectedRules.contains("QUEST3_UNPROMOTED"))
    }
    
    @Test
    fun `test Quest 3 descriptor with mismatched GPU`() {
        val descriptor = createBaseDescriptor(
            model = "Quest 3", 
            device = "eureka", 
            glRenderer = "Adreno (TM) 650"
        )
        val report = QuestProfileResolver.resolve(descriptor)
        
        assertFalse(report.isSupported)
        assertNull(report.profile)
        assertEquals("UNKNOWN_META", report.ruleId)
        assertTrue(report.rejectedRules.contains("QUEST3_GPU_MISMATCH"))
    }
}
